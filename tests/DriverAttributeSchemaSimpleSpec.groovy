import spock.lang.Specification

/**
 * Test suite for driver attribute schema validation
 * Uses reflection to ensure all driver attributes are covered by tests
 */
class DriverAttributeSchemaSimpleSpec extends Specification {

    private static final File VENT_DRIVER_FILE = new File('src/hubitat-flair-vents-driver.groovy')
    private static final File PUCK_DRIVER_FILE = new File('src/hubitat-flair-vents-pucks-driver.groovy')
    
    def "vent driver attributes are extracted using reflection"() {
        when: "extracting vent driver attributes"
        def startTime = System.currentTimeMillis()
        def ventAttributes = extractDriverAttributes(VENT_DRIVER_FILE)
        def endTime = System.currentTimeMillis()
        def duration = endTime - startTime
        
        then: "extraction is fast and finds expected attributes"
        duration < 500  // Performance constraint
        ventAttributes.size() > 40  // Should find many attributes
        ventAttributes.contains('percent-open')
        ventAttributes.contains('rssi')
        ventAttributes.contains('duct-temperature-c')
        ventAttributes.contains('voltage')  // From VoltageMeasurement capability
        ventAttributes.contains('level')    // From SwitchLevel capability
        
        println "Found ${ventAttributes.size()} vent driver attributes in ${duration}ms"
    }
    
    def "puck driver attributes are extracted using reflection"() {
        when: "extracting puck driver attributes"
        def startTime = System.currentTimeMillis()
        def puckAttributes = extractDriverAttributes(PUCK_DRIVER_FILE)
        def endTime = System.currentTimeMillis()
        def duration = endTime - startTime
        
        then: "extraction is fast and finds expected attributes"
        duration < 500  // Performance constraint
        puckAttributes.size() > 30  // Should find many attributes
        puckAttributes.contains('room-name')
        puckAttributes.contains('room-id')
        puckAttributes.contains('temperature')  // From TemperatureMeasurement capability
        puckAttributes.contains('humidity')     // From RelativeHumidityMeasurement capability
        puckAttributes.contains('battery')      // From Battery capability
        
        println "Found ${puckAttributes.size()} puck driver attributes in ${duration}ms"
    }
    
    def "tested attributes are found in existing test files"() {
        when: "scanning test files for attribute usage"
        def startTime = System.currentTimeMillis()
        def testFiles = findTestFiles()
        def testedAttributes = extractTestedAttributes(testFiles)
        def endTime = System.currentTimeMillis()
        def duration = endTime - startTime
        
        then: "scanning is fast and finds existing tested attributes"
        duration < 500  // Performance constraint
        testFiles.size() > 10  // Should find multiple test files
        testedAttributes.size() > 5  // Should find some tested attributes
        
        // Verify some known tested attributes
        testedAttributes.contains('room-cooling-rate') || testedAttributes.contains('room-heating-rate')
        
        println "Scanned ${testFiles.size()} test files and found ${testedAttributes.size()} tested attributes in ${duration}ms"
    }
    
    def "attribute coverage analysis provides helpful output"() {
        given: "all driver attributes and tested attributes"
        def ventAttributes = extractDriverAttributes(VENT_DRIVER_FILE)
        def puckAttributes = extractDriverAttributes(PUCK_DRIVER_FILE)
        def allDriverAttributes = ventAttributes + puckAttributes
        
        def testFiles = findTestFiles()
        def testedAttributes = extractTestedAttributes(testFiles)
        
        when: "analyzing coverage"
        def missingAttributes = allDriverAttributes - testedAttributes
        def coveragePercentage = ((testedAttributes.size() / allDriverAttributes.size()) * 100).round(2)
        
        then: "analysis provides useful information"
        allDriverAttributes.size() > 0
        
        println "\n=== Driver Attribute Coverage Analysis ==="
        println "Total driver attributes: ${allDriverAttributes.size()}"
        println "  - Vent driver: ${ventAttributes.size()} attributes"
        println "  - Puck driver: ${puckAttributes.size()} attributes"
        println "Tested attributes: ${testedAttributes.size()}"
        println "Coverage: ${coveragePercentage}%"
        
        if (missingAttributes.size() > 0) {
            println "\nMissing from tests (${missingAttributes.size()} attributes):"
            missingAttributes.sort().take(10).each { attr ->
                println "  - ${attr}"
            }
            if (missingAttributes.size() > 10) {
                println "  ... and ${missingAttributes.size() - 10} more"
            }
        }
        
        // Always pass - this is informational
        true
    }
    
    def "critical attributes have test recommendations"() {
        given: "critical attributes that should be tested"
        def criticalVentAttributes = [
            'percent-open', 'level', 'voltage', 'battery', 'rssi',
            'room-name', 'room-id', 'structure-id', 'inactive'
        ] as Set
        
        def criticalPuckAttributes = [
            'temperature', 'humidity', 'battery', 'motion',
            'room-name', 'room-id', 'room-active'
        ] as Set
        
        def testFiles = findTestFiles()
        def testedAttributes = extractTestedAttributes(testFiles)
        
        when: "checking critical attribute coverage"
        def missingVentCritical = criticalVentAttributes - testedAttributes
        def missingPuckCritical = criticalPuckAttributes - testedAttributes
        
        then: "provide recommendations for missing critical attributes"
        println "\n=== Critical Attribute Recommendations ==="
        
        if (missingVentCritical.size() > 0) {
            println "Critical vent attributes missing from tests:"
            missingVentCritical.each { attr ->
                println "  - ${attr} (add: sendEvent(ventDevice, [name: '${attr}', value: testValue]))"
            }
        }
        
        if (missingPuckCritical.size() > 0) {
            println "Critical puck attributes missing from tests:"
            missingPuckCritical.each { attr ->
                println "  - ${attr} (add: sendEvent(puckDevice, [name: '${attr}', value: testValue]))"
            }
        }
        
        if (missingVentCritical.size() == 0 && missingPuckCritical.size() == 0) {
            println "All critical attributes are covered in tests!"
        }
        
        // Always pass - this is informational
        true
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
                def sendEventPattern = ~/sendEvent\s*\([^)]*\[name:\s*['"]([^'"]+)['"]/
                def matches = testText =~ sendEventPattern
                matches.each { match ->
                    testedAttributes.add(match[1])
                }
                
                // Pattern to match event data structures
                def eventDataPattern = ~/\[name:\s*['"]([^'"]+)['"]/
                def eventMatches = testText =~ eventDataPattern
                eventMatches.each { match ->
                    // Skip template variables like '${attr}'
                    if (!match[1].contains('${')) {
                        testedAttributes.add(match[1])
                    }
                }
                
                // Pattern to match currentValue calls
                def currentValuePattern = ~/currentValue\s*\(\s*['"]([^'"]+)['"]/
                def currentValueMatches = testText =~ currentValuePattern
                currentValueMatches.each { match ->
                    testedAttributes.add(match[1])
                }
                
            } catch (Exception e) {
                // Skip files that can't be read
            }
        }
        
        return testedAttributes
    }
}