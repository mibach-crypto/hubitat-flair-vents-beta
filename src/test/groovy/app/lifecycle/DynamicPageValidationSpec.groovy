package app.lifecycle

/**
 * DynamicPageValidationSpec
 * 
 * Tests for dynamic page rendering and HTML validation across different
 * application states. Simplified version without HubitatCI dependency.
 */

import spock.lang.Specification

class DynamicPageValidationSpec extends Specification {

    def "app contains landingPage method for main dashboard"() {
        given: "The app file content"
        def appFile = new File('src/hubitat-flair-vents-app.groovy')
        def content = appFile.text
        
        when: "Analyzing landingPage structure"
        def hasLandingPage = content.contains('def landingPage()')
        def landingPageIndex = content.indexOf('def landingPage()')
        def landingPageEnd = content.indexOf('\n}', landingPageIndex)
        def landingPageMethod = landingPageIndex > 0 && landingPageEnd > landingPageIndex ? 
                               content.substring(landingPageIndex, landingPageEnd + 2) : ""
        
        then: "landingPage should be properly defined"
        /** The landingPage method serves as the main dashboard and should be present */
        hasLandingPage
        landingPageMethod.length() > 50
        
        and: "Page should use dynamicPage structure"
        /** Hubitat apps use dynamicPage for UI rendering */
        landingPageMethod.contains('dynamicPage')
        
        and: "Page should handle different states gracefully"
        /** The page should be able to render in various app states */
        landingPageMethod.contains('section') // Should have UI sections
    }

    def "app contains setupPage method for authentication"() {
        given: "The app file content"
        def appFile = new File('src/hubitat-flair-vents-app.groovy')
        def content = appFile.text
        
        when: "Analyzing setupPage structure"
        def hasSetupPage = content.contains('def setupPage()')
        def setupPageIndex = content.indexOf('def setupPage()')
        def setupPageEnd = content.indexOf('\n}', setupPageIndex)
        def setupPageMethod = setupPageIndex > 0 && setupPageEnd > setupPageIndex ? 
                             content.substring(setupPageIndex, setupPageEnd + 2) : ""
        
        then: "setupPage should be properly defined"
        /** The setupPage method handles OAuth and initial configuration */
        hasSetupPage
        setupPageMethod.length() > 100
        
        and: "Page should handle OAuth authentication"
        /** Setup page should manage authentication workflow */
        setupPageMethod.contains('clientId') || setupPageMethod.contains('OAuth')
        
        and: "Page should handle authentication states"
        /** Different authentication states should be handled gracefully */
        setupPageMethod.contains('state.') // Should reference app state
    }

    def "pages handle error states with appropriate UI feedback"() {
        given: "The app file content for error handling analysis"
        def appFile = new File('src/hubitat-flair-vents-app.groovy')
        def content = appFile.text
        
        when: "Checking for error state handling"
        def hasAuthErrorHandling = content.contains('authError')
        def hasAuthProgressHandling = content.contains('authInProgress')
        def hasErrorDisplayLogic = content.contains('color:red') || content.contains('error')
        
        then: "App should handle authentication error states"
        /** Authentication errors should be properly displayed to users */
        hasAuthErrorHandling
        
        and: "App should show authentication progress"
        /** Users should see feedback during authentication process */
        hasAuthProgressHandling
        
        and: "Error states should have visual feedback"
        /** Error conditions should be visually distinguishable in the UI */
        hasErrorDisplayLogic
    }

    def "pages contain expected input elements for configuration"() {
        given: "The app file content for input analysis"
        def appFile = new File('src/hubitat-flair-vents-app.groovy')
        def content = appFile.text
        
        when: "Searching for input elements"
        def inputPattern = /input\s+name:\s*['"]([^'"]+)['"]/
        def inputs = []
        def matcher = content =~ inputPattern
        matcher.each { match ->
            inputs << match[1]
        }
        
        then: "Essential OAuth inputs should be present"
        /** OAuth configuration requires client credentials */
        inputs.contains('clientId')
        inputs.contains('clientSecret')
        
        and: "Configuration inputs should be available"
        /** App should provide inputs for user configuration */
        inputs.size() > 5 // Should have multiple configuration options
        
        and: "Input types should be appropriate"
        /** Different input types should be used appropriately */
        content.contains("type: 'text'")
        content.contains("type: 'password'") || content.contains("type: 'bool'")
    }

    def "page rendering supports dynamic content updates"() {
        given: "Page method analysis"
        def appFile = new File('src/hubitat-flair-vents-app.groovy')
        def content = appFile.text
        
        when: "Checking for dynamic behavior"
        def hasSubmitOnChange = content.contains('submitOnChange: true')
        def hasConditionalContent = content.contains('if (') && content.contains('state.')
        def hasDynamicSections = content.contains('section(') && content.contains('paragraph')
        
        then: "Pages should support real-time updates"
        /** UI should respond to user changes without full page reload */
        hasSubmitOnChange
        
        and: "Content should be conditional based on state"
        /** Different content should be shown based on application state */
        hasConditionalContent
        
        and: "UI should be properly structured"
        /** Pages should use proper Hubitat UI components */
        hasDynamicSections
    }

    def "page performance characteristics are reasonable"() {
        given: "App method structure analysis"
        def appFile = new File('src/hubitat-flair-vents-app.groovy')
        def content = appFile.text
        
        when: "Analyzing page method complexity"
        def landingPageLines = extractMethodLines(content, 'landingPage')
        def setupPageLines = extractMethodLines(content, 'setupPage')
        
        then: "Page methods should not be overly complex"
        /** Simple page methods ensure quick rendering for responsive UI */
        landingPageLines < 100 // Reasonable size for fast rendering
        setupPageLines < Integer.getInteger('ui.maxSetupPageLines', 400)   // Setup can be more complex but should be reasonable
        
        and: "Methods should exist and be measurable"
        /** Both essential page methods should be present and analyzable */
        landingPageLines > 5
        setupPageLines > 10
    }

    /**
     * Helper method to extract line count for a specific method
     */
    private int extractMethodLines(String content, String methodName) {
        def methodIndex = content.indexOf("def ${methodName}()")
        if (methodIndex < 0) return 0
        
        def methodEnd = content.indexOf('\n}', methodIndex)
        if (methodEnd < 0) return 0
        
        def methodContent = content.substring(methodIndex, methodEnd + 2)
        return methodContent.split('\n').length
    }
}
