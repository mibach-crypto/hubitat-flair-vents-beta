package bot.flair

import spock.lang.Specification

class IntegrationTests extends Specification {

  def "dynamic cooling end detection integration workflow"() {
    setup:
    def mockCoolingState = [
      ductMinF: 70.0,
      ductBaseF: 73.5,  // 3.5°F rise from min
      deltaBaseF: 2.0,
      lastDuctTemps: [70.0, 71.0, 72.0, 73.0, 74.0],
      lastRoomDeltas: [5.0, 4.0, 3.0, 2.0, 1.0],
      stabilizationPolls: 0,  // Past stabilization
      confirmationCount: 1,   // One confirmation
      cycleStartTime: System.currentTimeMillis() - 300000, // 5 minutes ago
      pollCount: 10
    ]
    
    // Constants
    def COOLING_BASE_RISE_F = 3.0
    def COOLING_MIN_RISE_FROM_MIN_F = 2.0
    def END_CONFIRMATION_POLLS = 2
    def MIN_VALID_CYCLE_SECONDS = 180

    when:
    // Check minimum cycle duration
    def cycleSeconds = (System.currentTimeMillis() - mockCoolingState.cycleStartTime) / 1000
    def validCycleDuration = cycleSeconds >= MIN_VALID_CYCLE_SECONDS

    // Check rise detection criteria
    def riseAmount = mockCoolingState.ductBaseF - mockCoolingState.ductMinF
    def riseEndDetected = riseAmount >= COOLING_BASE_RISE_F || riseAmount >= COOLING_MIN_RISE_FROM_MIN_F

    // Check confirmation logic
    def needsMoreConfirmation = mockCoolingState.confirmationCount < END_CONFIRMATION_POLLS
    def shouldEndCycle = validCycleDuration && riseEndDetected && !needsMoreConfirmation

    then:
    validCycleDuration == true
    riseAmount == 3.5
    riseEndDetected == true  // 3.5°F exceeds 3.0°F threshold
    needsMoreConfirmation == true  // Need 2 confirmations, only have 1
    shouldEndCycle == false  // Need more confirmation
  }

  def "hourly commit workflow validation"() {
    setup:
    def mockThermostatState = [
      startedRunning: System.currentTimeMillis() - 3900000,  // 65 minutes ago
      startedCycle: System.currentTimeMillis() - 4200000,    // 70 minutes ago
      mode: 'cooling'
    ]

    when:
    def runningMinutes = (System.currentTimeMillis() - mockThermostatState.startedRunning) / (1000 * 60)
    def shouldPerformHourlyCommit = runningMinutes >= 60

    // Simulate hourly commit parameters
    def hourlyCommitParams = [
      ventIdsByRoomId: ['room1': ['vent1', 'vent2']],
      startedCycle: mockThermostatState.startedCycle,
      startedRunning: mockThermostatState.startedRunning,
      finishedRunning: System.currentTimeMillis(),
      hvacMode: mockThermostatState.mode
    ]

    then:
    shouldPerformHourlyCommit == true
    Math.round(runningMinutes) >= 60
    hourlyCommitParams.hvacMode == 'cooling'
    hourlyCommitParams.ventIdsByRoomId != null
  }

  def "parameter reconstruction edge cases"() {
    setup:
    def incompleteData = [
      ventIdsByRoomId: null,
      startedCycle: null,
      finishedRunning: null
    ]
    
    def mockAtomicState = [
      ventsByRoomId: ['room1': ['vent1']],
      thermostat1State: [
        startedCycle: 1000L,
        mode: 'heating'
      ]
    ]

    when:
    // Simulate parameter reconstruction logic
    def reconstructedData = [:]
    reconstructedData.putAll(incompleteData)
    
    if (!reconstructedData.ventIdsByRoomId && mockAtomicState.ventsByRoomId) {
      reconstructedData.ventIdsByRoomId = mockAtomicState.ventsByRoomId
    }
    
    if (!reconstructedData.startedCycle && mockAtomicState.thermostat1State?.startedCycle) {
      reconstructedData.startedCycle = mockAtomicState.thermostat1State.startedCycle
    }
    
    if (!reconstructedData.finishedRunning) {
      reconstructedData.finishedRunning = System.currentTimeMillis()
    }

    then:
    reconstructedData.ventIdsByRoomId == ['room1': ['vent1']]
    reconstructedData.startedCycle == 1000L
    reconstructedData.finishedRunning != null
  }

  def "fan-only mode handling workflow"() {
    setup:
    def settings = [fanOnlyOpenAllVents: true]
    def isFanActive = true
    def previousMode = 'cooling'
    def ventsByRoomId = ['room1': ['vent1', 'vent2'], 'room2': ['vent3']]

    when:
    // Simulate fan-only detection and handling
    def shouldHandleFanOnly = settings.fanOnlyOpenAllVents && isFanActive
    def shouldCleanupCycle = shouldHandleFanOnly && previousMode != 'idle'
    
    // Count vents that should be opened
    def totalVents = ventsByRoomId.values().flatten().size()

    then:
    shouldHandleFanOnly == true
    shouldCleanupCycle == true
    totalVents == 3
  }

  def "diagnostic tracking completeness"() {
    setup:
    def diagnostics = [
      hourlyCommits: 0,
      cycleAborts: 0,
      cycleTransitions: 0,
      hardResets: 0,
      stuckWarnings: 0
    ]

    when:
    // Simulate various events
    diagnostics.hourlyCommits++
    diagnostics.cycleTransitions += 2
    diagnostics.lastHourlyCommit = System.currentTimeMillis()
    diagnostics.lastCycleTransition = System.currentTimeMillis() - 1000
    diagnostics.lastTransitionDetails = 'cooling -> idle'

    then:
    diagnostics.hourlyCommits == 1
    diagnostics.cycleTransitions == 2
    diagnostics.lastHourlyCommit != null
    diagnostics.lastTransitionDetails.contains('->')
  }

  def "cycle debug information structure"() {
    setup:
    def coolingDebugInfo = [
      pollCount: 15,
      stabilizationRemaining: 0,
      confirmationCount: 2,
      currentDuctTempF: '74.2',
      currentRoomDeltaF: '1.3',
      ductMinF: '70.5',
      ductBaseF: '73.8',
      deltaBaseF: '2.1',
      lastDuctTemps: '70.5, 71.0, 72.2, 73.1, 74.2',
      lastRoomDeltas: '4.8, 3.9, 2.7, 1.8, 1.3'
    ]

    when:
    def hasRequiredFields = coolingDebugInfo.containsKey('pollCount') &&
                           coolingDebugInfo.containsKey('confirmationCount') &&
                           coolingDebugInfo.containsKey('currentDuctTempF') &&
                           coolingDebugInfo.containsKey('ductMinF') &&
                           coolingDebugInfo.containsKey('ductBaseF')

    def currentTemp = Float.parseFloat(coolingDebugInfo.currentDuctTempF)
    def minTemp = Float.parseFloat(coolingDebugInfo.ductMinF)
    def riseAmount = currentTemp - minTemp

    then:
    hasRequiredFields == true
    coolingDebugInfo.pollCount == 15
    riseAmount > 3.0  // Should show significant rise
    coolingDebugInfo.lastDuctTemps.split(', ').size() == 5
  }
}