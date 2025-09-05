package bot.flair.perf

// Vent Target Performance Tests
// Tests calculateVentTargets() method performance under load for large installations
// Run `gradle slowTest` to execute these performance tests

import spock.lang.Specification
import spock.lang.Unroll
import org.junit.experimental.categories.Category

@Category(SlowTest)
class VentTargetPerformanceSpec extends Specification {

    @Unroll
    def "calculateVentTargets performance test for #ventCount vents"() {
        given: "synthetic vent data for #ventCount vents"
        def ventData = generateSyntheticVentData(ventCount)
        def hvacMode = 'cooling'

        // Create a mock app instance with essential methods
        def mockApp = new MockFlairApp()

        when: "calculateVentTargets is called and timed"
        def startTime = System.nanoTime()
        def result = mockApp.calculateVentTargets(ventData, hvacMode)
        def endTime = System.nanoTime()
        def durationMs = (endTime - startTime) / 1_000_000.0

        then: "execution completes successfully"
        result != null
        result instanceof Map

        and: "wall-clock time is within performance requirements"
        def maxAllowedMs = 200 + (ventCount * 1)
        durationMs <= maxAllowedMs
        
        println "Performance test for ${ventCount} vents: ${durationMs.round(3)}ms (max: ${maxAllowedMs}ms)"

        where:
        ventCount << [5, 50, 100, 200]
    }

    def "calculateVentTargets stress test with 200 vents repeated calls"() {
        given: "synthetic vent data for 200 vents"
        def ventData = generateSyntheticVentData(200)
        def hvacMode = 'cooling'
        def mockApp = new MockFlairApp()

        when: "multiple calls are made to simulate DAB loop"
        def totalDuration = 0.0
        def callCount = 10
        
        (1..callCount).each { call ->
            def startTime = System.nanoTime()
            def result = mockApp.calculateVentTargets(ventData, hvacMode)
            def endTime = System.nanoTime()
            def durationMs = (endTime - startTime) / 1_000_000.0
            totalDuration += durationMs
            
            assert result != null
            assert result instanceof Map
        }

        then: "average execution time meets DAB loop requirements"
        def avgDurationMs = totalDuration / callCount
        def maxDabLoopTime = 60000 // 1 minute in ms
        def maxCallDuration = maxDabLoopTime / 10 // Allow for 10 operations per minute
        
        avgDurationMs <= maxCallDuration
        
        println "Stress test: ${callCount} calls, average: ${avgDurationMs.round(3)}ms (max per call: ${maxCallDuration}ms)"
        println "Total time for ${callCount} calls: ${totalDuration.round(3)}ms"
    }

    /**
     * Generate synthetic vent data for performance testing
     * @param count Number of vents to generate
     * @return Map of vent data suitable for calculateVentTargets()
     */
    private Map generateSyntheticVentData(int count) {
        def ventData = [:]
        
        (1..count).each { i ->
            def ventId = "vent-${String.format('%04d', i)}"
            
            // Generate realistic vent data
            def roomTemp = 20.0 + (Math.random() * 10) // 20-30°C
            def coolingRate = 0.5 + (Math.random() * 2.0) // 0.5-2.5°C/hour
            def heatingRate = 0.5 + (Math.random() * 2.0) // 0.5-2.5°C/hour
            def isActive = Math.random() > 0.1 // 90% active rooms
            
            ventData[ventId] = [
                roomTemp: roomTemp,
                coolingRate: coolingRate,
                heatingRate: heatingRate,
                active: isActive,
                roomId: "room-${i}",
                ventId: ventId
            ]
        }
        
        return ventData
    }

    /**
     * Mock implementation of Flair App for performance testing
     * Implements the calculateVentTargets method and its dependencies
     */
    class MockFlairApp {
        static final int MAX_MINUTES_TO_SETPOINT = 30

        def atomicState = [
            maxHvacRunningTime: 30,
            maxCoolingRate: 2.5,
            maxHeatingRate: 2.5
        ]

        def settings = [
            thermostat1CloseInactiveRooms: false,
            thermostat1AdditionalStandardVents: 0
        ]

        Map calculateVentTargets(Map ventData, String hvacMode) {
            BigDecimal setpoint = getGlobalSetpoint(hvacMode)
            if (setpoint == null) { return [:] }
            
            def maxRunningTime = atomicState.maxHvacRunningTime ?: MAX_MINUTES_TO_SETPOINT
            def longest = calculateLongestMinutesToTarget(ventData, hvacMode, setpoint, maxRunningTime, settings.thermostat1CloseInactiveRooms)
            if (longest < 0) { longest = maxRunningTime }
            
            def targets = calculateOpenPercentageForAllVents(ventData, hvacMode, setpoint, longest, settings.thermostat1CloseInactiveRooms)
            if (!targets) { return [:] }
            
            targets = adjustVentOpeningsToEnsureMinimumAirflowTarget(ventData, hvacMode, targets, settings.thermostat1AdditionalStandardVents)
            targets = applyOverridesAndFloors(targets)
            
            return targets
        }

        BigDecimal getGlobalSetpoint(String hvacMode) {
            return new BigDecimal("22.5") // Default test setpoint
        }

        double calculateLongestMinutesToTarget(Map ventData, String hvacMode, BigDecimal setpoint, int maxRunningTime, boolean closeInactiveRooms) {
            // Simplified calculation for performance testing
            def longestTime = 0.0
            ventData.each { ventId, data ->
                if (data.active || !closeInactiveRooms) {
                    def tempDiff = Math.abs(data.roomTemp - setpoint.doubleValue())
                    def rate = hvacMode == 'cooling' ? data.coolingRate : data.heatingRate
                    def timeToTarget = rate > 0 ? (tempDiff / rate) * 60 : maxRunningTime
                    longestTime = Math.max(longestTime, timeToTarget)
                }
            }
            return Math.min(longestTime, maxRunningTime)
        }

        Map calculateOpenPercentageForAllVents(Map ventData, String hvacMode, BigDecimal setpoint, double longestTime, boolean closeInactiveRooms) {
            def targets = [:]
            ventData.each { ventId, data ->
                if (!data.active && closeInactiveRooms) {
                    targets[ventId] = 0.0
                } else {
                    def tempDiff = Math.abs(data.roomTemp - setpoint.doubleValue())
                    def rate = hvacMode == 'cooling' ? data.coolingRate : data.heatingRate
                    def timeToTarget = rate > 0 ? (tempDiff / rate) * 60 : longestTime
                    
                    // Calculate proportional opening (simplified)
                    def proportion = timeToTarget / longestTime
                    targets[ventId] = Math.min(100.0, Math.max(0.0, proportion * 100.0))
                }
            }
            return targets
        }

        Map adjustVentOpeningsToEnsureMinimumAirflowTarget(Map ventData, String hvacMode, Map targets, int additionalStandardVents) {
            // Simplified airflow adjustment for performance testing
            def totalFlow = targets.values().sum() + (additionalStandardVents * 50)
            def minRequiredFlow = ventData.size() * 15 // Minimum 15% per vent
            
            if (totalFlow < minRequiredFlow) {
                def adjustment = (minRequiredFlow - totalFlow) / targets.size()
                targets = targets.collectEntries { ventId, opening ->
                    [ventId, Math.min(100.0, opening + adjustment)]
                }
            }
            return targets
        }

        Map applyOverridesAndFloors(Map targets) {
            // Apply minimum floor of 5% and maximum of 100%
            return targets.collectEntries { ventId, opening ->
                [ventId, Math.min(100.0, Math.max(5.0, opening))]
            }
        }
    }
}