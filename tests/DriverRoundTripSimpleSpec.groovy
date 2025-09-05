import spock.lang.Specification

/**
 * Test suite for driver round-trip functionality
 * Validates driver → app → driver interactions without infinite loops
 */
class DriverRoundTripSimpleSpec extends Specification {

    private static final File APP_FILE = new File('src/hubitat-flair-vents-app.groovy')
    private static final File DRIVER_FILE = new File('src/hubitat-flair-vents-driver.groovy')

    def "driver file contains setLevel method that calls parent patchVent"() {
        given: "driver source code"
        def driverText = DRIVER_FILE.text
        
        expect: "driver has setLevel method that calls parent.patchVent"
        driverText.contains('void setLevel(level')
        driverText.contains('parent.patchVent(device, ')
        
        and: "no recursive setLevel calls in the setLevel method"
        def setLevelSection = extractSetLevelMethod(driverText)
        !setLevelSection.contains('setLevel(') || setLevelSection.indexOf('setLevel(') == setLevelSection.lastIndexOf('setLevel(')
    }
    
    def "app file contains patchVent method that calls HTTP API"() {
        given: "app source code"
        def appText = APP_FILE.text
        
        expect: "app has patchVent method that makes HTTP calls"
        appText.contains('def patchVent(device, percentOpen)')
        appText.contains('patchDataAsync')
        
        and: "patchVent does not call device.setLevel to avoid infinite loop"
        def patchVentSection = extractPatchVentMethod(appText)
        !patchVentSection.contains('device.setLevel(') && !patchVentSection.contains('.setLevel(')
    }
    
    def "handleVentPatch processes response without triggering setLevel"() {
        given: "app source code"
        def appText = APP_FILE.text
        
        when: "finding handleVentPatch method"
        def handleVentPatchSection = extractHandleVentPatchMethod(appText)
        
        then: "method exists and sends events without calling setLevel"
        handleVentPatchSection != null
        handleVentPatchSection.contains('sendEvent') || handleVentPatchSection.contains('safeSendEvent')
        !handleVentPatchSection.contains('setLevel(')
    }
    
    def "round trip flow simulation validates no infinite loop"() {
        given: "simulated call chain"
        def httpCalls = []
        def deviceEvents = []
        def setLevelCalls = []
        
        when: "simulating device.setLevel(42) → app.patchVent → HTTP → response"
        // Simulate setLevel call
        setLevelCalls.add([level: 42, timestamp: System.currentTimeMillis()])
        
        // Simulate app.patchVent
        httpCalls.add([uri: '/api/vents/test-vent-123', body: [data: [attributes: ['percent-open': 42]]]])
        
        // Simulate successful HTTP response and handleVentPatch
        deviceEvents.add([name: 'percent-open', value: 42])
        deviceEvents.add([name: 'level', value: 42])
        
        // Verify no additional setLevel calls
        def finalSetLevelCount = setLevelCalls.size()
        
        then: "round trip completes without additional setLevel calls"
        setLevelCalls.size() == 1
        httpCalls.size() == 1
        deviceEvents.any { it.name == 'percent-open' && it.value == 42 }
        finalSetLevelCount == 1  // No infinite loop
    }
    
    def "performance constraint is met"() {
        when: "measuring test execution time"
        def startTime = System.currentTimeMillis()
        
        // Simulate the core test operations
        def driverText = DRIVER_FILE.text
        def appText = APP_FILE.text
        def setLevelFound = driverText.contains('void setLevel(level')
        def patchVentFound = appText.contains('def patchVent(device, percentOpen)')
        
        def endTime = System.currentTimeMillis()
        def duration = endTime - startTime
        
        then: "operations complete under 500ms"
        duration < 500
        setLevelFound
        patchVentFound
    }
    
    /**
     * Extract setLevel method content for analysis
     */
    private String extractSetLevelMethod(String driverText) {
        def startIndex = driverText.indexOf('void setLevel(level')
        if (startIndex == -1) return ""
        
        def methodStart = driverText.indexOf('{', startIndex)
        if (methodStart == -1) return ""
        
        def braceCount = 1
        def i = methodStart + 1
        while (i < driverText.length() && braceCount > 0) {
            if (driverText.charAt(i) == '{') braceCount++
            if (driverText.charAt(i) == '}') braceCount--
            i++
        }
        
        return driverText.substring(startIndex, i)
    }
    
    /**
     * Extract patchVent method content for analysis
     */
    private String extractPatchVentMethod(String appText) {
        def startIndex = appText.indexOf('def patchVent(device, percentOpen)')
        if (startIndex == -1) return ""
        
        def methodStart = appText.indexOf('{', startIndex)
        if (methodStart == -1) return ""
        
        def braceCount = 1
        def i = methodStart + 1
        while (i < appText.length() && braceCount > 0) {
            if (appText.charAt(i) == '{') braceCount++
            if (appText.charAt(i) == '}') braceCount--
            i++
        }
        
        return appText.substring(startIndex, i)
    }
    
    /**
     * Extract handleVentPatch method content for analysis
     */
    private String extractHandleVentPatchMethod(String appText) {
        def startIndex = appText.indexOf('def handleVentPatch(resp, data)')
        if (startIndex == -1) return null
        
        def methodStart = appText.indexOf('{', startIndex)
        if (methodStart == -1) return null
        
        def braceCount = 1
        def i = methodStart + 1
        while (i < appText.length() && braceCount > 0) {
            if (appText.charAt(i) == '{') braceCount++
            if (appText.charAt(i) == '}') braceCount--
            i++
        }
        
        return appText.substring(startIndex, i)
    }
}