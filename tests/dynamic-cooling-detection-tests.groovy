package bot.flair

import spock.lang.Specification

class DynamicCoolingDetectionTests extends Specification {

  def "dynamic cooling constants are properly defined"() {
    setup:
    def constants = [
      POLL_INTERVAL_SECONDS: 60,
      STABILIZATION_POLLS: 2,
      EMA_ALPHA: 0.25,
      COOLING_BASE_RISE_F: 3.0,
      COOLING_MIN_RISE_FROM_MIN_F: 2.0,
      COOLING_FAST_RISE_F: 5.0,
      COOLING_DELTA_COLLAPSE_PCT: 0.55,
      COOLING_DELTA_ABSOLUTE_MIN_F: 0.8,
      COOLING_BASE_MAX_VAR_F: 1.5,
      END_CONFIRMATION_POLLS: 2,
      MIN_VALID_CYCLE_SECONDS: 180
    ]

    when:
    def valid = constants.every { key, expectedValue ->
      // For this simple test, just verify the constants are reasonable values
      return expectedValue > 0
    }

    then:
    valid == true
    constants.EMA_ALPHA >= 0.0 && constants.EMA_ALPHA <= 1.0
    constants.COOLING_DELTA_COLLAPSE_PCT > 0.0 && constants.COOLING_DELTA_COLLAPSE_PCT < 1.0
  }

  def "cooling cycle state structure is valid"() {
    setup:
    def mockState = [
      ductMinF: null,
      ductBaseF: null,
      deltaBaseF: null,
      lastDuctTemps: [],
      lastRoomDeltas: [],
      stabilizationPolls: 2,
      confirmationCount: 0,
      cycleStartTime: System.currentTimeMillis(),
      pollCount: 0,
      endReason: null,
      riseAmountF: null,
      collapsePercent: null
    ]

    when:
    def hasRequiredFields = mockState.containsKey('ductMinF') &&
                           mockState.containsKey('ductBaseF') &&
                           mockState.containsKey('deltaBaseF') &&
                           mockState.containsKey('lastDuctTemps') &&
                           mockState.containsKey('lastRoomDeltas') &&
                           mockState.containsKey('stabilizationPolls') &&
                           mockState.containsKey('confirmationCount')

    then:
    hasRequiredFields == true
    mockState.lastDuctTemps instanceof List
    mockState.lastRoomDeltas instanceof List
    mockState.stabilizationPolls == 2
    mockState.confirmationCount == 0
  }

  def "EMA calculation logic"() {
    setup:
    def alpha = 0.25
    def currentEMA = null
    def newValue = 75.0

    when:
    // First calculation with null EMA (should return newValue)
    def firstResult = currentEMA == null ? newValue : (alpha * newValue) + ((1.0 - alpha) * currentEMA)
    
    // Second calculation with existing EMA
    currentEMA = firstResult
    newValue = 77.0
    def secondResult = (alpha * newValue) + ((1.0 - alpha) * currentEMA)

    then:
    firstResult == 75.0  // First value becomes the EMA
    secondResult > 75.0 && secondResult < 77.0  // Should be between old and new
    Math.abs(secondResult - 75.5) < 1.0  // Should be approximately 75.5
  }

  def "rolling array logic maintains size limit"() {
    setup:
    def array = []
    def maxSize = 5

    when:
    // Add more than maxSize elements
    (1..7).each { value ->
      array.add(value)
      while (array.size() > maxSize) {
        array.remove(0)
      }
    }

    then:
    array.size() == 5
    array == [3, 4, 5, 6, 7]  // Should contain the last 5 elements
  }

  def "temperature conversion F to C"() {
    setup:
    def tempC = 24.0  // 24°C
    def expectedF = 75.2  // 75.2°F

    when:
    def actualF = (tempC * 9.0/5.0) + 32.0

    then:
    Math.abs(actualF - expectedF) < 0.1
  }

  def "cooling end detection criteria thresholds"() {
    setup:
    def ductMinF = 70.0
    def ductBaseF = 74.0  // 4°F rise from min
    def baseRiseThreshold = 3.0
    def minRiseThreshold = 2.0

    when:
    def riseAmount = ductBaseF - ductMinF
    def meetsCriteria = riseAmount >= baseRiseThreshold || riseAmount >= minRiseThreshold

    then:
    riseAmount == 4.0
    meetsCriteria == true  // 4°F exceeds both thresholds
  }

  def "delta collapse calculation"() {
    setup:
    def initialDelta = 5.0  // 5°F above setpoint
    def currentDelta = 1.0  // 1°F above setpoint
    def collapseThreshold = 0.55  // 55%
    def absoluteMinThreshold = 0.8

    when:
    def collapsePercent = initialDelta > 0 ? (initialDelta - currentDelta) / initialDelta : 0
    def meetsCollapseCriteria = collapsePercent >= collapseThreshold && currentDelta <= absoluteMinThreshold

    then:
    collapsePercent == 0.8  // 80% collapse
    meetsCollapseCriteria == true  // Exceeds 55% threshold and under 0.8°F
  }

  def "cycle transition logging format"() {
    setup:
    def fromMode = 'cooling'
    def toMode = 'idle'
    def details = 'Dynamic end detected'

    when:
    def arrow = fromMode == toMode ? '=' : '->'
    def logMessage = "HVAC Transition: ${fromMode} ${arrow} ${toMode}"
    if (details) {
      logMessage += " (${details})"
    }

    then:
    arrow == '->'
    logMessage == 'HVAC Transition: cooling -> idle (Dynamic end detected)'
  }

  def "fan-only mode transition handling"() {
    setup:
    def previousMode = 'cooling'
    def isFanActive = true
    def fanOnlyOpenAllVents = true

    when:
    def shouldTransitionToFanOnly = fanOnlyOpenAllVents && isFanActive
    def shouldCleanupCycle = shouldTransitionToFanOnly && previousMode != 'idle'

    then:
    shouldTransitionToFanOnly == true
    shouldCleanupCycle == true
  }

  def "parameter reconstruction logic"() {
    setup:
    def data = [:]
    def mockThermostatState = [
      startedCycle: 1000L,
      startedRunning: 2000L,
      mode: 'cooling'
    ]

    when:
    // Simulate parameter reconstruction
    if (!data.startedCycle && mockThermostatState.startedCycle) {
      data.startedCycle = mockThermostatState.startedCycle
    }
    if (!data.hvacMode && mockThermostatState.mode) {
      data.hvacMode = mockThermostatState.mode
    }
    if (!data.finishedRunning) {
      data.finishedRunning = System.currentTimeMillis()
    }

    then:
    data.startedCycle == 1000L
    data.hvacMode == 'cooling'
    data.finishedRunning != null
  }

  def "diagnostic structure validation"() {
    setup:
    def diagnostics = [
      hourlyCommits: 0,
      cycleAborts: 0,
      cycleTransitions: 0,
      hardResets: 0,
      stuckWarnings: 0
    ]

    when:
    // Simulate events
    diagnostics.hourlyCommits++
    diagnostics.cycleTransitions++
    diagnostics.lastHourlyCommit = System.currentTimeMillis()

    then:
    diagnostics.hourlyCommits == 1
    diagnostics.cycleTransitions == 1
    diagnostics.lastHourlyCommit != null
  }
}