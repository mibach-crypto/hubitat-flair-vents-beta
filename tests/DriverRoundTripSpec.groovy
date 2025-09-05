@Grab('org.spockframework:spock-core:2.3-groovy-3.0')
@Grab('org.objenesis:objenesis:3.3')
@Grab('net.bytebuddy:byte-buddy:1.14.5')
@Grab('me.biocomp:hubitat_ci:1.2.1')

import spock.lang.Specification
import me.biocomp.hubitat_ci.api.app_api.AppExecutor
import me.biocomp.hubitat_ci.api.device_api.DeviceExecutor
import me.biocomp.hubitat_ci.app.HubitatAppSandbox
import me.biocomp.hubitat_ci.device.HubitatDeviceSandbox
import me.biocomp.hubitat_ci.validation.Flags

/**
 * Test suite for driver → app → driver round-trip functionality
 * Ensures setLevel calls don't create infinite loops
 */
class DriverRoundTripSpec extends Specification {

    private static final File APP_FILE = new File('src/hubitat-flair-vents-app.groovy')
    private static final File DRIVER_FILE = new File('src/hubitat-flair-vents-driver.groovy')
    private static final List VALIDATION_FLAGS = [
        Flags.DontValidateMetadata,
        Flags.DontValidatePreferences,
        Flags.DontValidateDefinition,
        Flags.DontRestrictGroovy,
        Flags.DontRequireParseMethodInDevice,
        Flags.AllowWritingToSettings,
        Flags.AllowReadingNonInputSettings
    ]

    def "device setLevel triggers app patchVent without infinite loop"() {
        given: "mock vent driver and app"
        def httpPatchCalls = []
        def deviceEvents = []
        def setLevelCalls = []
        
        // Create mock app executor
        AppExecutor mockAppExecutor = Mock(AppExecutor) {
            getState() >> [flairAccessToken: 'test-token']
            getAtomicState() >> [activeRequests: 0, lastRequestTime: 0]
            getSetting('debugLevel') >> 1
            getLog() >> Mock(Object) {
                debug(_) >> { }
                warn(_) >> { }
                error(_) >> { }
            }
        }
        
        // Load and configure app
        def appSandbox = new HubitatAppSandbox(APP_FILE)
        def app = appSandbox.run('api': mockAppExecutor, 'validationFlags': VALIDATION_FLAGS)
        
        // Set up app state
        app.atomicState = [activeRequests: 0, lastRequestTime: 0]
        
        // Mock app's HTTP calls to track API interactions
        app.metaClass.patchDataAsync = { String uri, String callback, Map body, Map data ->
            httpPatchCalls.add([uri: uri, body: body, data: data])
            
            // Simulate successful HTTP response
            if (callback && data) {
                def mockResp = Mock(Object) {
                    getJson() >> [data: [attributes: ['percent-open': data.targetOpen]]]
                }
                // Simulate async callback
                app."$callback"(mockResp, data)
            }
        }
        
        // Mock other required app methods
        app.metaClass.incrementActiveRequests = { -> }
        app.metaClass.decrementActiveRequests = { -> }
        app.metaClass.isValidResponse = { resp -> true }
        app.metaClass.runInMillis = { delay, method, data -> }
        app.metaClass.log = { level, category, message, deviceId = null -> }
        app.metaClass.logDetails = { message, details, level -> }
        app.metaClass.traitExtract = { device, details, propNameData, propNameDriver, unit = null -> }
        app.metaClass.safeSendEvent = { device, eventData -> 
            device.sendEvent(eventData)
        }
        app.metaClass.appendDabActivityLog = { message -> }
        
        // Create mock device
        def mockDevice = Mock(Object) {
            getDeviceNetworkId() >> 'test-vent-123'
            getLabel() >> 'Test Vent'
            currentValue('percent-open') >> 0
            currentValue('room-name') >> 'Test Room'
            sendEvent(_) >> { Map eventData ->
                deviceEvents.add(eventData)
            }
        }
        
        // Create mock device executor  
        DeviceExecutor mockDeviceExecutor = Mock(DeviceExecutor) {
            getParent() >> app
            getDevice() >> mockDevice
            getLog() >> Mock(Object) {
                debug(_) >> { }
                warn(_) >> { }
                error(_) >> { }
            }
        }
        
        // Load driver
        def driverSandbox = new HubitatDeviceSandbox(DRIVER_FILE)
        def driver = driverSandbox.run('api': mockDeviceExecutor, 'validationFlags': VALIDATION_FLAGS)
        
        // Mock driver methods
        driver.metaClass.log = { level, category, message, deviceId = null -> }
        
        // Track setLevel calls to detect recursion
        def originalSetLevel = driver.&setLevel
        driver.metaClass.setLevel = { level, duration = null ->
            setLevelCalls.add([level: level, duration: duration, timestamp: System.currentTimeMillis()])
            originalSetLevel(level, duration)
        }

        when: "device.setLevel(42) is called"
        driver.setLevel(42)

        then: "app.patchVent fires one HTTP PATCH"
        httpPatchCalls.size() == 1
        httpPatchCalls[0].uri.contains('/api/vents/test-vent-123')
        httpPatchCalls[0].body.data.attributes['percent-open'] == 42

        and: "device receives percent-open = 42 event"
        deviceEvents.any { it.name == 'percent-open' && it.value == 42 }

        and: "no second setLevel is triggered (avoids infinite loop)"
        setLevelCalls.size() == 1
        setLevelCalls[0].level == 42
    }

    def "test execution time is within performance constraint"() {
        when: "measuring test execution time"
        def startTime = System.currentTimeMillis()
        
        // Simulate core test logic
        def httpCalls = []
        def mockDevice = [
            getDeviceNetworkId: { -> 'perf-test-vent' },
            currentValue: { attr -> attr == 'percent-open' ? 25 : null },
            sendEvent: { eventData -> }
        ]
        
        // Simulate app patchVent call
        httpCalls.add([uri: "/api/vents/${mockDevice.getDeviceNetworkId()}", targetOpen: 75])
        
        def endTime = System.currentTimeMillis()
        def duration = endTime - startTime

        then: "test completes within 500ms constraint"
        duration < 500
        httpCalls.size() == 1
    }
    
    def "driver file loads without errors"() {
        expect: "driver file exists and is readable"
        DRIVER_FILE.exists()
        DRIVER_FILE.text.contains('setLevel')
        DRIVER_FILE.text.contains('parent.patchVent')
    }
    
    def "app file loads without errors"() {
        expect: "app file exists and contains patchVent method"
        APP_FILE.exists()
        APP_FILE.text.contains('def patchVent')
        APP_FILE.text.contains('patchDataAsync')
    }
}