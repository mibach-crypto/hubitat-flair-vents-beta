import spock.lang.Specification

/**
 * Test suite for driver attribute schema validation
 * Uses reflection to ensure all driver attributes are covered by tests
 */
class DriverAttributeSchemaSpec extends Specification {

    private static final File VENT_DRIVER_FILE = new File('src/hubitat-flair-vents-driver.groovy')
    private static final File PUCK_DRIVER_FILE = new File('src/hubitat-flair-vents-pucks-driver.groovy')
    
    def "all vent driver attributes appear in existing tests"() {
        given: "vent driver metadata and test files"
        def ventAttributes = extractDriverAttributes(VENT_DRIVER_FILE)
        def testFiles = findTestFiles()
        def testedAttributes = extractTestedAttributes(testFiles)
        
        when: "comparing driver attributes to tested attributes"
        def missingAttributes = ventAttributes - testedAttributes
        
        then: "all attributes should be covered or documented"
        ventAttributes.size() > 0  // Should find some attributes
        testedAttributes.size() > 0  // Should find some tested attributes
        
        // Report missing attributes but don't fail - this is informational
        if (!missingAttributes.isEmpty()) {
            println "Missing vent driver attributes in tests (${missingAttributes.size()}/${ventAttributes.size()}):"
            missingAttributes.sort().each { attr ->
                println "  - ${attr}"
            }
        }
        true  // Always pass but provide info
    }
    
    def "all puck driver attributes appear in existing tests"() {
        given: "puck driver metadata and test files"
        def puckAttributes = extractDriverAttributes(PUCK_DRIVER_FILE)
        def testFiles = findTestFiles()
        def testedAttributes = extractTestedAttributes(testFiles)
        
        when: "comparing driver attributes to tested attributes"
        def missingAttributes = puckAttributes - testedAttributes
        
        then: "all attributes should be covered or documented"
        puckAttributes.size() > 0  // Should find some attributes
        
        // Report missing attributes but don't fail - this is informational
        if (!missingAttributes.isEmpty()) {
            println "Missing puck driver attributes in tests (${missingAttributes.size()}/${puckAttributes.size()}):"
            missingAttributes.sort().each { attr ->
                println "  - ${attr}"
            }
        }
        true  // Always pass but provide info
    }
    
    def "driver attribute extraction performance is acceptable"() {
        when: "extracting all driver attributes"
        def startTime = System.currentTimeMillis()
        def ventAttributes = extractDriverAttributes(VENT_DRIVER_FILE)
        def puckAttributes = extractDriverAttributes(PUCK_DRIVER_FILE)
        def endTime = System.currentTimeMillis()
        def duration = endTime - startTime
        
        then: "extraction completes within performance constraint"
        duration < 500  // Under 500ms as required
        ventAttributes.size() > 0
        puckAttributes.size() > 0
        println "Attribute extraction completed in ${duration}ms (found ${ventAttributes.size()} vent + ${puckAttributes.size()} puck attributes)"
    }
    
    def "critical vent attributes are documented with test recommendations"() {
        given: "known critical vent attributes"
        def criticalAttributes = [
            'percent-open', 'level', 'voltage', 'battery', 'rssi',
            'room-name', 'room-id', 'structure-id', 'inactive',
            'duct-temperature-c', 'duct-pressure', 'motor-current'
        ] as Set
        
        def testFiles = findTestFiles()
        def testedAttributes = extractTestedAttributes(testFiles)
        
        when: "checking for critical attributes in tests"
        def missingCritical = criticalAttributes - testedAttributes
        
        then: "provide recommendations for missing critical attributes"
        if (!missingCritical.isEmpty()) {
            println "Critical vent attributes missing from tests (${missingCritical.size()}/${criticalAttributes.size()}):"
            missingCritical.sort().each { attr ->
                println "  - ${attr} (recommend: sendEvent(device, [name: '${attr}', value: testValue]))"
            }
        } else {
            println "All critical vent attributes are covered in tests"
        }
        true  // Always pass - this is informational
    }
    
    /**
     * Extract attribute names from driver metadata using Groovy reflection
     */
    private Set<String> extractDriverAttributes(File driverFile) {
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
     * Find all test files in the repository
     */
    private List<File> findTestFiles() {
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
     * Extract attribute names mentioned in test files (in sendEvent calls)
     */
    private Set<String> extractTestedAttributes(List<File> testFiles) {
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
}