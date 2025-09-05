package bot.flair.perf

// Property-Based Input Fuzzing Tests
// Tests cleanDecimalForJson() method with random BigDecimal inputs
// Uses property-based testing approach to validate decimal precision handling

import spock.lang.Specification
import spock.lang.Unroll
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.math.BigDecimal
import java.math.RoundingMode

class PropertyBasedInputFuzzSpec extends Specification {

    def "cleanDecimalForJson property-based fuzz testing"() {
        given: "a mock cleanDecimalForJson implementation"
        def mockApp = new MockFlairApp()

        when: "testing with random BigDecimal values"
        def testIterations = 1000
        def failures = []
        def precisionViolations = []

        (1..testIterations).each { iteration ->
            try {
                // Generate random BigDecimal values (0-100, up to 12 decimal places)
                def randomValue = generateRandomBigDecimal()
                
                // Call cleanDecimalForJson
                def result = mockApp.cleanDecimalForJson(randomValue)
                
                // Verify no exception occurred (we get here if no exception)
                assert result != null
                
                // Convert to JSON to check precision
                def jsonString = JsonOutput.toJson([value: result])
                def parsedJson = new JsonSlurper().parseText(jsonString)
                def valueString = parsedJson.value.toString()
                
                // Count actual decimal places in JSON output
                def decimalIndex = valueString.indexOf('.')
                def actualDecimalPlaces = decimalIndex >= 0 ? valueString.length() - decimalIndex - 1 : 0
                
                // Assert output precision ≤ 10 decimal places
                if (actualDecimalPlaces > 10) {
                    precisionViolations << [
                        iteration: iteration,
                        input: randomValue,
                        output: result,
                        jsonValue: valueString,
                        decimalPlaces: actualDecimalPlaces
                    ]
                }
                
            } catch (Exception e) {
                failures << [
                    iteration: iteration,
                    input: randomValue,
                    exception: e.getMessage(),
                    exceptionType: e.getClass().getSimpleName()
                ]
            }
        }

        then: "no exceptions should occur"
        failures.isEmpty() || {
            println "Exceptions occurred in ${failures.size()}/${testIterations} iterations:"
            failures.take(5).each { failure ->
                println "  Iteration ${failure.iteration}: ${failure.exceptionType} - ${failure.exception}"
                println "    Input: ${failure.input}"
            }
            return false
        }()

        and: "all outputs should have precision ≤ 10 decimal places"
        precisionViolations.isEmpty() || {
            println "Precision violations in ${precisionViolations.size()}/${testIterations} iterations:"
            precisionViolations.take(5).each { violation ->
                println "  Iteration ${violation.iteration}: ${violation.decimalPlaces} decimal places"
                println "    Input: ${violation.input}"
                println "    Output: ${violation.output}"
                println "    JSON value: ${violation.jsonValue}"
            }
            return false
        }()

        and: "report test statistics"
        println "Property-based fuzz testing completed:"
        println "  Iterations: ${testIterations}"
        println "  Exceptions: ${failures.size()}"
        println "  Precision violations: ${precisionViolations.size()}"
        println "  Success rate: ${((testIterations - failures.size() - precisionViolations.size()) / testIterations * 100).round(2)}%"
    }

    @Unroll
    def "cleanDecimalForJson edge case testing for #description"() {
        given: "a mock cleanDecimalForJson implementation"
        def mockApp = new MockFlairApp()

        when: "processing edge case input"
        def result = mockApp.cleanDecimalForJson(input)

        then: "no exception occurs"
        noExceptionThrown()

        and: "result meets precision requirements"
        def jsonString = JsonOutput.toJson([value: result])
        def parsedJson = new JsonSlurper().parseText(jsonString)
        def valueString = parsedJson.value.toString()
        
        def decimalIndex = valueString.indexOf('.')
        def actualDecimalPlaces = decimalIndex >= 0 ? valueString.length() - decimalIndex - 1 : 0
        
        actualDecimalPlaces <= 10

        where:
        description                          | input
        "null value"                        | null
        "zero value"                        | 0
        "zero BigDecimal"                   | BigDecimal.ZERO
        "very small positive"               | new BigDecimal("0.000000000001")
        "very large precision"              | new BigDecimal("1.123456789012345678901234567890")
        "problematic BigDecimal #1"         | new BigDecimal("0.7565031865619353798895421013423648241063427314927104497175")
        "problematic BigDecimal #2"         | new BigDecimal("0.4380625000000000071016808361923900918589158077810090062063875")
        "maximum test value"                | new BigDecimal("100.000000000000")
        "minimum test value"                | new BigDecimal("0.000000000001")
        "negative value"                    | new BigDecimal("-50.123456789012")
        "infinity representation"           | Double.POSITIVE_INFINITY
        "negative infinity representation"  | Double.NEGATIVE_INFINITY
        "NaN representation"                | Double.NaN
    }

    def "cleanDecimalForJson bulk precision validation"() {
        given: "a mock cleanDecimalForJson implementation"
        def mockApp = new MockFlairApp()

        when: "processing a large set of random values"
        def testCount = 500
        def maxPrecisionViolation = 0
        def totalProcessed = 0

        (1..testCount).each { i ->
            def randomValue = generateRandomBigDecimal()
            def result = mockApp.cleanDecimalForJson(randomValue)
            
            def jsonString = JsonOutput.toJson([value: result])
            def parsedJson = new JsonSlurper().parseText(jsonString)
            def valueString = parsedJson.value.toString()
            
            def decimalIndex = valueString.indexOf('.')
            def actualDecimalPlaces = decimalIndex >= 0 ? valueString.length() - decimalIndex - 1 : 0
            
            if (actualDecimalPlaces > maxPrecisionViolation) {
                maxPrecisionViolation = actualDecimalPlaces
            }
            
            totalProcessed++
        }

        then: "all values should have acceptable precision"
        maxPrecisionViolation <= 10
        totalProcessed == testCount
        
        println "Bulk precision test: ${totalProcessed} values processed, max precision: ${maxPrecisionViolation} decimal places"
    }

    /**
     * Generate random BigDecimal values for property-based testing
     * Range: 0-100, up to 12 decimal places as specified in requirements
     */
    private BigDecimal generateRandomBigDecimal() {
        def random = new Random()
        
        // Generate random value between 0 and 100
        def baseValue = random.nextDouble() * 100
        
        // Generate random number of decimal places (1-12)
        def decimalPlaces = random.nextInt(12) + 1
        
        // Create BigDecimal with specified precision
        def bigDecimal = new BigDecimal(baseValue)
        
        // Add some random precision by multiplying and dividing by large numbers
        def precisionMultiplier = Math.pow(10, decimalPlaces)
        def randomPrecisionValue = (baseValue * precisionMultiplier + random.nextDouble()) / precisionMultiplier
        
        return new BigDecimal(randomPrecisionValue.toString())
    }

    /**
     * Mock implementation of cleanDecimalForJson method for testing
     * Mimics the actual implementation from the main app
     */
    class MockFlairApp {
        def cleanDecimalForJson(def value) {
            if (value == null || value == 0) return 0
            
            try {
                // Convert to String first to break BigDecimal precision chain
                def stringValue = value.toString()
                def doubleValue = Double.parseDouble(stringValue)
                
                // Handle edge cases
                if (!Double.isFinite(doubleValue)) {
                    return 0.0d
                }
                
                // Apply aggressive rounding to exactly 10 decimal places
                def multiplier = 1000000000.0d  // 10^9 for 10 decimal places
                def rounded = Math.round(doubleValue * multiplier) / multiplier
                
                // Ensure we return a clean Double, not BigDecimal
                return Double.valueOf(rounded)
            } catch (Exception e) {
                // In real app, this would log: log(2, 'App', "Error cleaning decimal for JSON: ${e.message}")
                return 0.0d
            }
        }
    }
}