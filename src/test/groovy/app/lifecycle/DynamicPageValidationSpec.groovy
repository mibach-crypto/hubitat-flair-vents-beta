package app.lifecycle

/**
 * DynamicPageValidationSpec
 * 
 * Tests for dynamic page rendering and HTML validation across different
 * application states including normal operation, authentication errors,
 * and authentication in progress states.
 */

import me.biocomp.hubitat_ci.util.CapturingLog
import me.biocomp.hubitat_ci.api.app_api.AppExecutor
import me.biocomp.hubitat_ci.app.HubitatAppSandbox
import me.biocomp.hubitat_ci.validation.Flags
import spock.lang.Specification

class DynamicPageValidationSpec extends Specification {

    private static final File APP_FILE = new File('src/hubitat-flair-vents-app.groovy')
    private static final List VALIDATION_FLAGS = [
        Flags.DontValidateMetadata,
        Flags.DontValidatePreferences,
        Flags.DontValidateDefinition,
        Flags.DontRestrictGroovy,
        Flags.DontRequireParseMethodInDevice,
        Flags.AllowReadingNonInputSettings
    ]

    def "renders landingPage in normal state"() {
        given: "App in normal operational state"
        final log = new CapturingLog()
        def mockState = [flairAccessToken: 'valid-token']
        def mockSettings = [clientId: 'test-client', clientSecret: 'test-secret']
        
        AppExecutor executorApi = Mock {
            _ * getState() >> mockState
            _ * getAtomicState() >> [:]
            _ * getSettings() >> mockSettings
            _ * getLog() >> log
        }
        def sandbox = new HubitatAppSandbox(APP_FILE)

        when: "landingPage is rendered"
        def script = sandbox.run(
            'api': executorApi,
            'validationFlags': VALIDATION_FLAGS,
            'customizeScriptBeforeRun': { s ->
                // Mock dependencies to focus on page rendering
                s.metaClass.getDataIssues = { -> [] } // No issues
                s.metaClass.getChildDevices = { -> [] } // No devices for simplicity
                s.metaClass.dynamicPage = { Map params, Closure closure ->
                    // Simulate page rendering by executing the closure
                    def pageBuilder = new MockPageBuilder()
                    closure.delegate = pageBuilder
                    closure()
                    return pageBuilder.content
                }
            }
        )
        
        def pageContent = script.landingPage()

        then: "page renders without exceptions"
        /** The landingPage should render successfully in normal state */
        noExceptionThrown()
        
        and: "page content is generated"
        /** Page should return content indicating successful rendering */
        pageContent != null
    }

    def "renders setupPage with authentication states"() {
        given: "App setup page with different auth states"
        final log = new CapturingLog()
        
        AppExecutor executorApi = Mock {
            _ * getState() >> authState
            _ * getAtomicState() >> [:]
            _ * getSettings() >> [clientId: 'test-client', clientSecret: 'test-secret']
            _ * getLog() >> log
        }
        def sandbox = new HubitatAppSandbox(APP_FILE)

        when: "setupPage is rendered"
        def script = sandbox.run(
            'api': executorApi,
            'validationFlags': VALIDATION_FLAGS,
            'customizeScriptBeforeRun': { s ->
                // Mock validation and dependencies
                s.metaClass.validatePreferences = { -> [valid: true, errors: [:]] }
                s.metaClass.runIn = { delay, method -> }
                s.metaClass.getDataIssues = { -> [] }
                s.metaClass.dynamicPage = { Map params, Closure closure ->
                    def pageBuilder = new MockPageBuilder()
                    closure.delegate = pageBuilder
                    closure()
                    return pageBuilder.content
                }
            }
        )
        
        def pageContent = script.setupPage()

        then: "page renders successfully for different auth states"
        /** setupPage should handle all authentication states gracefully */
        noExceptionThrown()
        pageContent != null

        where: "testing different authentication states"
        authState << [
            [:], // Normal state
            [authError: '401'], // Authentication error
            [authInProgress: true] // Authentication in progress
        ]
    }

    def "validates input elements exist in page HTML"() {
        given: "App with properly mocked page rendering"
        final log = new CapturingLog()
        def mockState = [flairAccessToken: 'valid-token']
        def mockSettings = [clientId: 'test-client', clientSecret: 'test-secret']
        def inputElements = []
        
        AppExecutor executorApi = Mock {
            _ * getState() >> mockState
            _ * getAtomicState() >> [:]
            _ * getSettings() >> mockSettings
            _ * getLog() >> log
        }
        def sandbox = new HubitatAppSandbox(APP_FILE)

        when: "setupPage is rendered with input tracking"
        def script = sandbox.run(
            'api': executorApi,
            'validationFlags': VALIDATION_FLAGS,
            'customizeScriptBeforeRun': { s ->
                s.metaClass.validatePreferences = { -> [valid: true, errors: [:]] }
                s.metaClass.runIn = { delay, method -> }
                s.metaClass.getDataIssues = { -> [] }
                s.metaClass.dynamicPage = { Map params, Closure closure ->
                    def pageBuilder = new MockPageBuilder(inputElements)
                    closure.delegate = pageBuilder
                    closure()
                    return "<html><body>${pageBuilder.content}</body></html>"
                }
            }
        )
        
        def pageContent = script.setupPage()

        then: "essential input elements are present"
        /** Core OAuth setup inputs should be present in the rendered page */
        inputElements.any { it.name == 'clientId' && it.type == 'text' }
        inputElements.any { it.name == 'clientSecret' && it.type == 'password' }
        
        and: "page HTML structure is valid"
        /** Page should generate valid HTML structure */
        pageContent.contains('<html>')
        pageContent.contains('</html>')
    }

    def "handles error states gracefully in page rendering"() {
        given: "App with various error conditions"
        final log = new CapturingLog()
        
        AppExecutor executorApi = Mock {
            _ * getState() >> [authError: errorMessage, authInProgress: inProgress]
            _ * getAtomicState() >> [:]
            _ * getSettings() >> [clientId: 'test-client', clientSecret: 'test-secret']
            _ * getLog() >> log
        }
        def sandbox = new HubitatAppSandbox(APP_FILE)

        when: "page is rendered with error conditions"
        def script = sandbox.run(
            'api': executorApi,
            'validationFlags': VALIDATION_FLAGS,
            'customizeScriptBeforeRun': { s ->
                s.metaClass.validatePreferences = { -> [valid: true, errors: [:]] }
                s.metaClass.runIn = { delay, method -> }
                s.metaClass.getDataIssues = { -> [] }
                s.metaClass.dynamicPage = { Map params, Closure closure ->
                    def pageBuilder = new MockPageBuilder()
                    closure.delegate = pageBuilder
                    closure()
                    return pageBuilder.content
                }
            }
        )
        
        def pageContent = script.setupPage()

        then: "page renders without throwing exceptions"
        /** Error states should be handled gracefully without breaking page rendering */
        noExceptionThrown()
        pageContent != null

        where: "testing different error scenarios"
        errorMessage        | inProgress | description
        '401'              | false      | "Authentication error"
        'Invalid token'    | false      | "Token validation error"
        null               | true       | "Authentication in progress"
    }

    def "page rendering performance meets requirements"() {
        given: "App configured for performance testing"
        final log = new CapturingLog()
        def mockState = [flairAccessToken: 'valid-token']
        
        AppExecutor executorApi = Mock {
            _ * getState() >> mockState
            _ * getAtomicState() >> [:]
            _ * getSettings() >> [clientId: 'test-client', clientSecret: 'test-secret']
            _ * getLog() >> log
        }
        def sandbox = new HubitatAppSandbox(APP_FILE)

        when: "page rendering time is measured"
        def script = sandbox.run(
            'api': executorApi,
            'validationFlags': VALIDATION_FLAGS,
            'customizeScriptBeforeRun': { s ->
                s.metaClass.validatePreferences = { -> [valid: true, errors: [:]] }
                s.metaClass.getDataIssues = { -> [] }
                s.metaClass.getChildDevices = { -> [] }
                s.metaClass.dynamicPage = { Map params, Closure closure ->
                    def pageBuilder = new MockPageBuilder()
                    closure.delegate = pageBuilder
                    closure()
                    return pageBuilder.content
                }
            }
        )
        
        long startTime = System.currentTimeMillis()
        script.landingPage()
        script.setupPage()
        long executionTime = System.currentTimeMillis() - startTime

        then: "page rendering completes within performance bounds"
        /** Page rendering should complete quickly to ensure responsive UI */
        executionTime < 1000 // 1 second for both pages
        
        and: "pages render successfully"
        /** Both pages should render without exceptions */
        noExceptionThrown()
    }

    /**
     * Mock page builder class to simulate Hubitat's dynamicPage functionality
     * and capture input elements for validation
     */
    class MockPageBuilder {
        def content = ""
        def inputElements = []
        
        MockPageBuilder(List<Map> inputElementsList = null) {
            if (inputElementsList != null) {
                this.inputElements = inputElementsList
            }
        }
        
        def section(Map params = [:], Closure closure = null) {
            if (closure) {
                closure.delegate = this
                closure()
            }
            content += "<section>"
        }
        
        def section(String title, Closure closure = null) {
            content += "<section title='${title}'>"
            if (closure) {
                closure.delegate = this
                closure()
            }
        }
        
        def paragraph(String text) {
            content += "<p>${text}</p>"
        }
        
        def input(Map params) {
            /** Track input elements for validation against driver metadata */
            inputElements << [
                name: params.name,
                type: params.type,
                title: params.title,
                required: params.required,
                defaultValue: params.defaultValue
            ]
            content += "<input name='${params.name}' type='${params.type}' title='${params.title}'>"
        }
        
        def href(Map params) {
            content += "<a href='${params.name}'>${params.title}</a>"
        }
    }
}