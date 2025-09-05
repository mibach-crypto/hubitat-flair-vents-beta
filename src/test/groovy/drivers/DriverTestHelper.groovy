/**
 * Helper utilities for driver testing
 * Provides common functionality for attribute extraction and mocking
 */
class DriverTestHelper {
    
    /**
     * Extract attribute names from driver metadata using reflection
     */
    static Set<String> extractDriverAttributes(File driverFile) {
        def attributes = [] as Set<String>
        
        if (!driverFile.exists()) {
            return attributes
        }
        
        def driverText = driverFile.text
        
        // Pattern to match attribute declarations in metadata
        def attributePattern = ~/attribute\s+'([^']+)'/
        def matches = driverText =~ attributePattern
        
        matches.each { match ->
            attributes.add(match[1])
        }
        
        // Also extract attributes from capability declarations that automatically provide attributes
        def capabilityPatterns = [
            'VoltageMeasurement': ['voltage'],
            'TemperatureMeasurement': ['temperature'],
            'RelativeHumidityMeasurement': ['humidity'],
            'MotionSensor': ['motion'],
            'Battery': ['battery'],
            'SwitchLevel': ['level'],
            'Refresh': []  // No automatic attributes
        ]
        
        capabilityPatterns.each { capabilityName, attrs ->
            if (driverText.contains("capability '${capabilityName}'")) {
                attributes.addAll(attrs)
            }
        }
        
        return attributes
    }
    
    /**
     * Extract attribute names mentioned in test files (in sendEvent calls)
     */
    static Set<String> extractTestedAttributes(List<File> testFiles) {
        def testedAttributes = [] as Set<String>
        
        testFiles.each { testFile ->
            try {
                def testText = testFile.text
                
                // Pattern to match sendEvent calls with name parameter
                def sendEventPattern = ~/sendEvent\s*\(\s*[^,]*,?\s*\[\s*name:\s*['"]([^'"]+)['"]/
                def matches = testText =~ sendEventPattern
                
                matches.each { match ->
                    testedAttributes.add(match[1])
                }
                
                // Pattern to match event data structures
                def eventDataPattern = ~/\[\s*name:\s*['"]([^'"]+)['"]/
                def eventMatches = testText =~ eventDataPattern
                
                eventMatches.each { match ->
                    testedAttributes.add(match[1])
                }
                
                // Pattern to match currentValue calls
                def currentValuePattern = ~/currentValue\s*\(\s*['"]([^'"]+)['"]/
                def currentValueMatches = testText =~ currentValuePattern
                
                currentValueMatches.each { match ->
                    testedAttributes.add(match[1])
                }
                
                // Pattern to match hasAttribute calls
                def hasAttributePattern = ~/hasAttribute\s*\(\s*['"]([^'"]+)['"]/
                def hasAttributeMatches = testText =~ hasAttributePattern
                
                hasAttributeMatches.each { match ->
                    testedAttributes.add(match[1])
                }
                
            } catch (Exception e) {
                // Skip files that can't be read
            }
        }
        
        return testedAttributes
    }
    
    /**
     * Find all test files in the repository
     */
    static List<File> findTestFiles() {
        def testFiles = []
        
        // Look in tests directory
        def testsDir = new File('tests')
        if (testsDir.exists()) {
            testsDir.listFiles()?.each { file ->
                if (file.name.endsWith('.groovy')) {
                    testFiles.add(file)
                }
            }
        }
        
        // Look in src/test directory tree
        def srcTestDir = new File('src/test')
        if (srcTestDir.exists()) {
            srcTestDir.traverse(type: groovy.io.FileType.FILES) { file ->
                if (file.name.endsWith('.groovy')) {
                    testFiles.add(file)
                }
            }
        }
        
        return testFiles
    }
    
    /**
     * Create a mock device with common vent attributes
     */
    static Object createMockVentDevice(String deviceId = 'test-vent-123', String label = 'Test Vent') {
        def deviceEvents = []
        
        return [
            getDeviceNetworkId: { -> deviceId },
            getLabel: { -> label },
            currentValue: { String attr ->
                switch(attr) {
                    case 'percent-open': return 0
                    case 'room-name': return 'Test Room'
                    case 'room-id': return 'test-room-456'
                    case 'level': return 0
                    case 'voltage': return 3.2
                    case 'battery': return 75
                    default: return null
                }
            },
            sendEvent: { Map eventData ->
                deviceEvents.add(eventData)
            },
            getEvents: { -> deviceEvents }
        ]
    }
    
    /**
     * Create a mock app executor for testing
     */
    static Object createMockAppExecutor() {
        return [
            getState: { -> [flairAccessToken: 'test-token'] },
            getAtomicState: { -> [activeRequests: 0, lastRequestTime: 0] },
            getSetting: { String key -> key == 'debugLevel' ? 1 : null },
            getLog: { -> 
                [
                    debug: { msg -> },
                    warn: { msg -> },
                    error: { msg -> }
                ]
            }
        ]
    }
}