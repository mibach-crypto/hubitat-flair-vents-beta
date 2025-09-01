/*
 *  Library: DabManager (core logic)
 *  Namespace: yourns.dab
 */
library(
  author: "Your Name",
  category: "utilities",
  description: "Dynamic Airflow Balancing core logic",
  name: "DabManager",
  namespace: "yourns.dab",
  documentationLink: ""
)

import groovy.transform.Field

@Field static final String COOLING = 'cooling'
@Field static final String HEATING = 'heating'
@Field static final BigDecimal VENT_PRE_ADJUST_THRESHOLD = 0.2
@Field static final BigDecimal MAX_MINUTES_TO_SETPOINT = 60
@Field static final BigDecimal MIN_MINUTES_TO_SETPOINT = 1
@Field static final BigDecimal SETPOINT_OFFSET = 0.7
@Field static final BigDecimal MAX_TEMP_CHANGE_RATE = 1.5
@Field static final BigDecimal MIN_TEMP_CHANGE_RATE = 0.001
@Field static final BigDecimal TEMP_SENSOR_ACCURACY = 0.5
@Field static final BigDecimal MIN_DETECTABLE_TEMP_CHANGE = 0.1
@Field static final Integer  MIN_RUNTIME_FOR_RATE_CALC = 5
@Field static final BigDecimal MIN_COMBINED_VENT_FLOW = 30.0
@Field static final BigDecimal INCREMENT_PERCENTAGE = 1.5
@Field static final Integer  MAX_ITERATIONS = 500
@Field static final BigDecimal REBALANCING_TOLERANCE = 0.5
@Field static final BigDecimal TEMP_BOUNDARY_ADJUSTMENT = 0.1
@Field static final BigDecimal DUCT_TEMP_DIFF_THRESHOLD = 0.5
@Field static final Integer  DEFAULT_HISTORY_RETENTION_DAYS = 10
@Field static final Boolean  ADAPTIVE_BOOST_ENABLED = true
@Field static final Integer  ADAPTIVE_LOOKBACK_PERIODS = 3
@Field static final BigDecimal ADAPTIVE_THRESHOLD_PERCENT = 25.0
@Field static final BigDecimal ADAPTIVE_BOOST_PERCENT = 12.5
@Field static final BigDecimal ADAPTIVE_MAX_BOOST_PERCENT = 25.0
@Field static final Integer  RAW_CACHE_DEFAULT_HOURS = 24
@Field static final Integer  RAW_CACHE_MAX_ENTRIES = 20000
@Field static final BigDecimal DEFAULT_COOLING_SETPOINT_C = 24.0
@Field static final BigDecimal DEFAULT_HEATING_SETPOINT_C = 20.0

// Dynamic Cooling End Detection Constants
@Field static final Integer POLL_INTERVAL_SECONDS = 60
@Field static final Integer STABILIZATION_POLLS = 2
@Field static final BigDecimal EMA_ALPHA = 0.25
@Field static final BigDecimal COOLING_BASE_RISE_F = 3.0
@Field static final BigDecimal COOLING_MIN_RISE_FROM_MIN_F = 2.0
@Field static final BigDecimal COOLING_FAST_RISE_F = 5.0
@Field static final BigDecimal COOLING_DELTA_COLLAPSE_PCT = 0.55
@Field static final BigDecimal COOLING_DELTA_ABSOLUTE_MIN_F = 0.8
@Field static final BigDecimal COOLING_BASE_MAX_VAR_F = 1.5
@Field static final Integer END_CONFIRMATION_POLLS = 2
@Field static final Integer MIN_VALID_CYCLE_SECONDS = 180

// =================================================================================
// Dynamic Cooling End Detection State Management
// =================================================================================

/** Initialize cooling cycle tracking state for dynamic end detection. */
def initializeCoolingCycleState() {
    if (!app.atomicState.coolingCycleState) {
        app.atomicState.coolingCycleState = [
            ductMinF: null,                    // Minimum duct temp seen this cycle
            ductBaseF: null,                   // EMA of base duct temp
            deltaBaseF: null,                  // EMA of hottest-room delta  
            lastDuctTemps: [],                 // Rolling 5 samples of duct temps
            lastRoomDeltas: [],                // Rolling 5 samples of room deltas
            stabilizationPolls: STABILIZATION_POLLS,  // Countdown for stabilization
            confirmationCount: 0,              // Consecutive polls confirming end
            cycleStartTime: app.now(),         // When cycle started
            pollCount: 0,                      // Total polls this cycle
            endReason: null,                   // Why cycle ended
            riseAmountF: null,                 // Amount of rise detected
            collapsePercent: null              // Percent of delta collapse
        ]
        app.log(3, 'CycleCool', 'Initialized cooling cycle state for dynamic end detection')
    }
}

/** Reset cooling cycle state at the start of a new cycle. */
def resetCoolingCycleState() {
    app.atomicState.coolingCycleState = null
    app.log(3, 'CycleCool', 'Reset cooling cycle state')
}

/** Update EMA (Exponential Moving Average) value. */
private def updateEMA(BigDecimal currentEMA, BigDecimal newValue, BigDecimal alpha = EMA_ALPHA) {
    if (currentEMA == null) {
        return newValue
    }
    return (alpha * newValue) + ((1.0 - alpha) * currentEMA)
}

/** Add value to rolling array and maintain max size. */
private def addToRollingArray(List array, def value, int maxSize = 5) {
    array.add(value)
    while (array.size() > maxSize) {
        array.remove(0)
    }
    return array
}

/** Get current duct temperature from all vents (median). */
private def getCurrentDuctTempF() {
    def vents = app.getChildDevices()?.findAll { 
        it.currentValue('duct-temperature-c') != null 
    }
    
    if (!vents) return null
    
    def temps = vents.collect { v ->
        try {
            def tempC = v.currentValue('duct-temperature-c') as BigDecimal
            return (tempC * 9.0/5.0) + 32.0  // Convert to Fahrenheit
        } catch (ignore) { return null }
    }.findAll { it != null }
    
    if (!temps) return null
    
    def sorted = temps.sort()
    return sorted[sorted.size().intdiv(2)] as BigDecimal
}

/** Get current hottest room delta (room temp - setpoint) in Fahrenheit. */
private def getHottestRoomDeltaF() {
    def vents = app.getChildDevices()?.findAll { 
        it.currentValue('room-current-temperature-c') != null ||
        it.currentValue('current-temperature-c') != null
    }
    
    if (!vents) return null
    
    def setpointC = app.getGlobalSetpoint(COOLING) ?: DEFAULT_COOLING_SETPOINT_C
    def setpointF = (setpointC * 9.0/5.0) + 32.0
    
    def deltas = vents.collect { v ->
        try {
            def tempC = (v.currentValue('room-current-temperature-c') ?: 
                        v.currentValue('current-temperature-c')) as BigDecimal
            def tempF = (tempC * 9.0/5.0) + 32.0
            return tempF - setpointF  // How much above setpoint
        } catch (ignore) { return null }
    }.findAll { it != null }
    
    return deltas ? deltas.max() : null
}

/** Dynamic cooling end detection with multiple criteria paths. */
def checkDynamicCoolingEnd() {
    def state = app.atomicState.coolingCycleState
    if (!state) return false
    
    def currentDuctF = getCurrentDuctTempF()
    def currentDeltaF = getHottestRoomDeltaF()
    
    if (currentDuctF == null || currentDeltaF == null) {
        app.log(4, 'CycleCool', 'Cannot check cooling end - missing temperature readings')
        return false
    }
    
    state.pollCount++
    
    // Update minimums and EMAs
    if (state.ductMinF == null || currentDuctF < state.ductMinF) {
        state.ductMinF = currentDuctF
    }
    
    state.ductBaseF = updateEMA(state.ductBaseF, currentDuctF)
    state.deltaBaseF = updateEMA(state.deltaBaseF, currentDeltaF)
    
    // Update rolling arrays
    addToRollingArray(state.lastDuctTemps, currentDuctF, 5)
    addToRollingArray(state.lastRoomDeltas, currentDeltaF, 5)
    
    // Check if we're still in stabilization period
    if (state.stabilizationPolls > 0) {
        state.stabilizationPolls--
        app.log(3, 'CycleCool', "Stabilization: ${state.stabilizationPolls} polls remaining, duct=${String.format('%.1f', currentDuctF)}°F, delta=${String.format('%.1f', currentDeltaF)}°F")
        app.atomicState.coolingCycleState = state
        return false
    }
    
    // Check minimum cycle duration (unless aborted)
    def cycleSeconds = (app.now() - state.cycleStartTime) / 1000
    if (cycleSeconds < MIN_VALID_CYCLE_SECONDS) {
        app.atomicState.coolingCycleState = state
        return false
    }
    
    // Check end criteria paths
    def endDetected = false
    def endReason = null
    def riseAmount = null
    def collapsePercent = null
    
    // Path A: Base Rise Detection
    if (state.ductBaseF != null && state.ductMinF != null) {
        riseAmount = state.ductBaseF - state.ductMinF
        if (riseAmount >= COOLING_BASE_RISE_F || riseAmount >= COOLING_MIN_RISE_FROM_MIN_F) {
            endDetected = true
            endReason = "Rise(A)"
        }
    }
    
    // Path B: Delta Collapse Detection
    if (!endDetected && state.deltaBaseF != null && state.lastRoomDeltas.size() >= 3) {
        def initialDelta = state.lastRoomDeltas[0]
        def currentCollapse = initialDelta > 0 ? (initialDelta - currentDeltaF) / initialDelta : 0
        if (currentCollapse >= COOLING_DELTA_COLLAPSE_PCT && currentDeltaF <= COOLING_DELTA_ABSOLUTE_MIN_F) {
            endDetected = true
            endReason = "DeltaCollapse(B)"
            collapsePercent = currentCollapse
        }
    }
    
    // Path C: Fast Rise Detection
    if (!endDetected && state.lastDuctTemps.size() >= 2) {
        def fastRise = currentDuctF - state.lastDuctTemps[-2]  // Compare to previous poll
        if (fastRise >= COOLING_FAST_RISE_F) {
            endDetected = true
            endReason = "FastRise(C)"
            riseAmount = fastRise
        }
    }
    
    // Path D: Trend Slope Detection (optional - simple version)
    if (!endDetected && state.lastDuctTemps.size() >= 5) {
        def oldTemp = state.lastDuctTemps[0]
        def slope = (currentDuctF - oldTemp) / 5.0  // Rise per poll over 5 polls
        if (slope > 0.5) {  // Rising trend
            endDetected = true
            endReason = "TrendSlope(D)"
            riseAmount = slope * 5.0
        }
    }
    
    // Handle end detection confirmation
    if (endDetected) {
        state.confirmationCount++
        app.log(3, 'CycleCool', "End detected via ${endReason}, confirmation ${state.confirmationCount}/${END_CONFIRMATION_POLLS}")
    } else {
        state.confirmationCount = 0  // Reset if no end detected
    }
    
    // Check if we have enough confirmation
    if (state.confirmationCount >= END_CONFIRMATION_POLLS) {
        state.endReason = endReason
        state.riseAmountF = riseAmount
        state.collapsePercent = collapsePercent
        
        def logMsg = "Cooling cycle end confirmed: ${endReason}"
        if (riseAmount != null) logMsg += ", rise=${String.format('%.1f', riseAmount)}°F"
        if (collapsePercent != null) logMsg += ", collapse=${String.format('%.0f', collapsePercent * 100)}%"
        
        app.log(2, 'CycleCool', logMsg)
        app.atomicState.coolingCycleState = state
        return true
    }
    
    app.atomicState.coolingCycleState = state
    return false
}

/** Enhanced cooling start detection with baseline initialization. */
def checkCoolingStart() {
    def currentDuctF = getCurrentDuctTempF()
    def currentDeltaF = getHottestRoomDeltaF() 
    
    if (currentDuctF == null || currentDeltaF == null) return false
    
    // Use existing threshold-based detection but with baseline init
    def vents = app.getChildDevices()?.findAll {
        it.currentValue('duct-temperature-c') != null &&
        (it.currentValue('room-current-temperature-c') != null || it.currentValue('current-temperature-c') != null)
    }
    
    if (!vents || vents.isEmpty()) return false
    
    def diffs = []
    vents.each { v ->
        try {
            BigDecimal duct = v.currentValue('duct-temperature-c') as BigDecimal
            BigDecimal room = (v.currentValue('room-current-temperature-c') ?: v.currentValue('current-temperature-c')) as BigDecimal
            diffs << (duct - room)
        } catch (ignore) { }
    }
    
    if (!diffs) return false
    
    def sorted = diffs.sort()
    BigDecimal median = sorted[sorted.size().intdiv(2)] as BigDecimal
    
    // Cooling start detected if median difference < -DUCT_TEMP_DIFF_THRESHOLD
    if (median < -DUCT_TEMP_DIFF_THRESHOLD) {
        initializeCoolingCycleState()  // Initialize tracking for this cycle
        return true
    }
    
    return false
}

/** Handle cycle abort situations with proper cleanup and event recording. */
def handleCycleAbort(String reason = 'Unknown') {
    def thermostatState = app.atomicState.thermostat1State
    if (!thermostatState) return
    
    app.log(2, 'DAB', "Cycle abort detected: ${reason}")
    
    // Record as CycleAbort event for diagnostics
    try {
        def diagnostics = app.atomicState.diagnostics ?: [:]
        diagnostics.cycleAborts = (diagnostics.cycleAborts ?: 0) + 1
        diagnostics.lastCycleAbort = app.now()
        diagnostics.lastCycleAbortReason = reason
        app.atomicState.diagnostics = diagnostics
    } catch (ignore) { }
    
    // Clean up all scheduling
    app.unschedule('initializeRoomStates')
    app.unschedule('finalizeRoomStates') 
    app.unschedule('evaluateRebalancingVents')
    app.unschedule('reBalanceVents')
    app.unschedule('performHourlyCommit')
    
    // Clean up state
    resetCoolingCycleState()
    app.atomicState.remove('thermostat1State')
    
    app.appendDabActivityLog("Cycle aborted: ${reason}")
}

/** Handle mid-cycle hourly commits for continuous runs. */
def performHourlyCommit() {
    def thermostatState = app.atomicState.thermostat1State
    if (!thermostatState || !thermostatState.startedRunning) {
        app.log(3, 'DAB', 'Skipping hourly commit: no active HVAC cycle')
        return
    }
    
    def runningMinutes = (app.now() - thermostatState.startedRunning) / (1000 * 60)
    if (runningMinutes < 60) {
        app.log(3, 'DAB', "Skipping hourly commit: cycle only ${Math.round(runningMinutes)} minutes old")
        return
    }
    
    app.log(2, 'DAB', "Performing hourly commit for continuous run (${Math.round(runningMinutes)} minutes)")
    app.appendDabActivityLog("Hourly commit for continuous run")
    
    // Perform finalize and re-initialize to commit current progress
    def params = [
        ventIdsByRoomId: app.atomicState.ventsByRoomId,
        startedCycle: thermostatState.startedCycle,
        startedRunning: thermostatState.startedRunning,
        finishedRunning: app.now(),
        hvacMode: thermostatState.mode
    ]
    
    // Record as HourCommit event
    try {
        def diagnostics = app.atomicState.diagnostics ?: [:]
        diagnostics.hourlyCommits = (diagnostics.hourlyCommits ?: 0) + 1
        diagnostics.lastHourlyCommit = app.now()
        app.atomicState.diagnostics = diagnostics
    } catch (ignore) { }
    
    finalizeRoomStates(params)
    
    // Update start time for next hour but keep original cycle start
    app.atomicStateUpdate('thermostat1State', 'startedRunning', app.now())
    
    initializeRoomStates(thermostatState.mode)
}

/** Initializes a DAB cycle when the HVAC turns on. */
def initializeRoomStates(String hvacMode) {
    if (!app.isDabEnabled()) { return }
    app.log(3, 'DAB', "Initializing room states - hvac mode: ${hvacMode}")
    if (!app.atomicState.ventsByRoomId) { return }
    if (app.settings.fanOnlyOpenAllVents && app.isFanActive()) {
        app.log(2, 'DAB', 'Fan-only mode active - skipping DAB initialization')
        return
    }

    Integer currentHour = new Date().format('H', app.location?.timeZone ?: TimeZone.getTimeZone('UTC')) as Integer
    app.atomicState.ventsByRoomId.each { roomId, ventIds ->
        def avgRate = getAverageHourlyRate(roomId, hvacMode, currentHour)
        ventIds.each { ventId ->
            def vent = app.getChildDevice(ventId)
            if (vent) {
                def attr = hvacMode == COOLING ? 'room-cooling-rate' : 'room-heating-rate'
                app.sendEvent(vent, [name: attr, value: avgRate])
            }
        }
    }

    BigDecimal setpoint = app.getGlobalSetpoint(hvacMode)
    if (!setpoint) { return }

    app.atomicStateUpdate('thermostat1State', 'startedCycle', app.now())
    def rateAndTempPerVentId = getAttribsPerVentIdWeighted(app.atomicState.ventsByRoomId, hvacMode)
    
    def maxRunningTime = app.atomicState.maxHvacRunningTime ?: MAX_MINUTES_TO_SETPOINT
    def longestTimeToTarget = calculateLongestMinutesToTarget(rateAndTempPerVentId, hvacMode, setpoint, maxRunningTime, app.settings.thermostat1CloseInactiveRooms)
    
    if (longestTimeToTarget < 0) {
        app.log(3, 'DAB', "All vents already reached setpoint (${setpoint})")
        longestTimeToTarget = maxRunningTime
    }
    if (longestTimeToTarget == 0) {
        app.log(3, 'DAB', "Opening all vents (setpoint: ${setpoint})")
        app.openAllVents(app.atomicState.ventsByRoomId, app.MAX_PERCENTAGE_OPEN as int)
        return
    }

    app.log(3, 'DAB', "Initializing room states - setpoint: ${setpoint}, longestTimeToTarget: ${app.roundBigDecimal(longestTimeToTarget)}")

    def calcPercentOpen = calculateOpenPercentageForAllVents(rateAndTempPerVentId, hvacMode, setpoint, longestTimeToTarget, app.settings.thermostat1CloseInactiveRooms)
    if (!calcPercentOpen) {
        app.log(3, 'DAB', "No vents are being changed (setpoint: ${setpoint})")
        return
    }

    calcPercentOpen = adjustVentOpeningsToEnsureMinimumAirflowTarget(rateAndTempPerVentId, hvacMode, calcPercentOpen, app.settings.thermostat1AdditionalStandardVents)
    calcPercentOpen = applyOverridesAndFloors(calcPercentOpen)

    def __changeSummary = []
    int __changeCount = 0
    calcPercentOpen.each { ventId, percentOpen ->
        def vent = app.getChildDevice(ventId)
        if (vent) {
            Integer __current = (vent.currentValue('percent-open') ?: vent.currentValue('level') ?: 0) as int
            Integer __target = app.roundToNearestMultiple(percentOpen)
            if (__current != __target) {
                __changeCount++
                def __rn = vent.currentValue('room-name') ?: vent.getLabel()
                __changeSummary << "${__rn}: ${__current}%->${__target}%"
            }
            app.patchVent(vent, __target)
        }
    }
    if (__changeCount > 0) {
        app.appendDabActivityLog("Applied ${__changeCount} vent change(s): ${__changeSummary.take(3).join(', ')}")
    }
}

/** Reconstruct missing parameters for finalize operations to prevent abort. */
def reconstructMissingParameters(data) {
    if (!data) {
        data = [:]
    }
    
    // Reconstruct ventIdsByRoomId if missing
    if (!data.ventIdsByRoomId && app.atomicState.ventsByRoomId) {
        data.ventIdsByRoomId = app.atomicState.ventsByRoomId
        app.log(3, 'DAB', 'Reconstructed missing ventIdsByRoomId parameter')
    }
    
    // Reconstruct cycle times if missing but thermostat state exists
    def thermostatState = app.atomicState.thermostat1State
    if (thermostatState) {
        if (!data.startedCycle && thermostatState.startedCycle) {
            data.startedCycle = thermostatState.startedCycle
            app.log(3, 'DAB', 'Reconstructed missing startedCycle parameter')
        }
        
        if (!data.startedRunning && thermostatState.startedRunning) {
            data.startedRunning = thermostatState.startedRunning
            app.log(3, 'DAB', 'Reconstructed missing startedRunning parameter')
        }
        
        if (!data.hvacMode && thermostatState.mode) {
            data.hvacMode = thermostatState.mode
            app.log(3, 'DAB', 'Reconstructed missing hvacMode parameter')
        }
    }
    
    // Set finishedRunning if missing
    if (!data.finishedRunning) {
        data.finishedRunning = app.now()
        app.log(3, 'DAB', 'Set missing finishedRunning parameter to current time')
    }
    
    return data
}

/** Finalizes a DAB cycle when the HVAC turns off. */
def finalizeRoomStates(data) {
    // Reconstruct missing parameters to prevent abort
    data = reconstructMissingParameters(data)
    
    if (!data.ventIdsByRoomId || !data.startedCycle || !data.finishedRunning) {
        app.logWarn "Finalizing room states: missing required parameters after reconstruction (${data})", 'DAB'
        handleCycleAbort('Missing required parameters')
        return
    }
    if (!data.startedRunning || !data.hvacMode) {
        app.log(2, 'DAB', "Skipping room state finalization - HVAC cycle started before code deployment")
        return
    }
    
    app.log(3, 'DAB', 'Start - Finalizing room states')
    def totalCycleMinutes = (data.finishedRunning - data.startedCycle) / (1000 * 60)
    
    if (totalCycleMinutes >= MIN_MINUTES_TO_SETPOINT) {
        Map<String, BigDecimal> roomRates = [:]
        Integer hour = new Date(data.startedCycle).format('H', app.location?.timeZone ?: TimeZone.getTimeZone('UTC')) as Integer

        data.ventIdsByRoomId.each { roomId, ventIds ->
            try {
                int roomPercentOpen = 0
                try {
                    ventIds.each { vid ->
                        def v = app.getChildDevice(vid)
                        if (!v) { return }
                        def p = (v?.currentValue('percent-open') ?: v?.currentValue('level') ?: 0)
                        try { roomPercentOpen += ((p ?: 0) as BigDecimal).intValue() } catch (ignore) { }
                    }
                    if (roomPercentOpen > 100) { roomPercentOpen = 100 }
                } catch (ignore) { roomPercentOpen = 0 }

                for (ventId in ventIds) {
                    def vent = app.getChildDevice(ventId)
                    if (!vent) { continue }
                    
                    def roomName = vent.currentValue('room-name')
                    def ratePropName = data.hvacMode == COOLING ? 'room-cooling-rate' : 'room-heating-rate'
                    
                    if (roomRates.containsKey(roomName)) {
                        def rate = roomRates[roomName]
                        app.sendEvent(vent, [name: ratePropName, value: rate])
                        continue
                    }
                    
                    def percentOpen = roomPercentOpen
                    BigDecimal currentTemp = app.getRoomTemp(vent)
                    BigDecimal lastStartTemp = vent.currentValue('room-starting-temperature-c') ?: 0
                    BigDecimal currentRate = vent.currentValue(ratePropName) ?: 0
                    def newRate = calculateRoomChangeRate(lastStartTemp, currentTemp, totalCycleMinutes, percentOpen, currentRate)
                    
                    if (newRate <= 0) {
                        def spRoom = vent.currentValue('room-set-point-c') ?: app.getGlobalSetpoint(data.hvacMode)
                        def isAtSetpoint = app.hasRoomReachedSetpoint(data.hvacMode, spRoom, currentTemp)
                        if (isAtSetpoint && currentRate > 0) {
                            newRate = currentRate
                        } else if (percentOpen > 0) {
                            newRate = MIN_TEMP_CHANGE_RATE
                        } else {
                            continue
                        }
                    }
                    
                    def rate = app.rollingAverage(currentRate, newRate, percentOpen / 100, 4)
                    def cleanedRate = app.cleanDecimalForJson(rate)
                    app.sendEvent(vent, [name: ratePropName, value: cleanedRate])
                    app.log(3, 'DAB', "Updating ${roomName}'s ${ratePropName} to ${app.roundBigDecimal(cleanedRate)}")

                    roomRates[roomName] = cleanedRate
                    appendHourlyRate(roomId, data.hvacMode, hour, cleanedRate)
                }
            } catch (Exception e) {
                app.logError("Error processing room ${roomId} in finalizeRoomStates: ${e?.message}", 'DAB', roomId)
                if (app.settings?.failFastFinalization) { throw e }
            }
        }
    } else {
        app.log(3, 'DAB', "Could not calculate room states as it ran for ${totalCycleMinutes} minutes and needs to run for at least ${MIN_MINUTES_TO_SETPOINT} minutes")
    }
    app.log(3, 'DAB', 'End - Finalizing room states')
}

/** Re-evaluates and re-balances all vents during a long HVAC cycle. */
def reBalanceVents() {
    def thermostatState = app.atomicState.thermostat1State
    if (!thermostatState || !thermostatState.startedRunning) {
        app.log(3, 'DAB', 'Skipping rebalance: HVAC cycle not properly started.')
        return
    }

    def runningMinutes = (app.now() - thermostatState.startedRunning) / (1000 * 60)
    if (runningMinutes < MIN_RUNTIME_FOR_RATE_CALC) {
        app.log(3, 'DAB', "Skipping rebalance: HVAC has only been running for ${runningMinutes} minutes.")
        return
    }

    app.log(3, 'DAB', 'Rebalancing Vents!!!')
    app.appendDabActivityLog("Rebalancing vents")
    def params = [
        ventIdsByRoomId: app.atomicState.ventsByRoomId,
        startedCycle: thermostatState.startedCycle,
        startedRunning: thermostatState.startedRunning,
        finishedRunning: app.now(),
        hvacMode: thermostatState.mode
    ]
    finalizeRoomStates(params)
    initializeRoomStates(thermostatState.mode)
}

/** Periodic check to trigger a rebalance. */
def evaluateRebalancingVents() {
    def thermostatState = app.atomicState.thermostat1State
    if (!thermostatState) { return }

    def lastRebalance = app.atomicState.lastRebalanceTime ?: 0
    if ((app.now() - lastRebalance) < (MIN_RUNTIME_FOR_RATE_CALC * 60 * 1000)) {
        return
    }

    def ventIdsByRoomId = app.atomicState.ventsByRoomId
    String hvacMode = thermostatState.mode
    def setPoint = app.getGlobalSetpoint(hvacMode)

    ventIdsByRoomId.each { roomId, ventIds ->
        for (ventId in ventIds) {
            try {
                def vent = app.getChildDevice(ventId)
                if (!vent || vent.currentValue('room-active') != 'true') { continue }
                
                def roomTemp = app.getRoomTemp(vent)
                if (app.hasRoomReachedSetpoint(hvacMode, setPoint, roomTemp, REBALANCING_TOLERANCE)) {
                    app.log(3, 'DAB', "Triggering rebalance because '${vent.currentValue('room-name')}' reached setpoint.")
                    app.atomicState.lastRebalanceTime = app.now()
                    reBalanceVents()
                    return // Exit loops
                }
            } catch (err) {
                app.logError "Error in evaluateRebalancingVents: ${err.message}", "DAB"
            }
        }
    }
}

/** Record cycle transition with consistent logging and ASCII arrows. */
def recordCycleTransition(String fromMode, String toMode, String details = '') {
    def arrow = fromMode == toMode ? '=' : '->'
    def logMessage = "HVAC Transition: ${fromMode} ${arrow} ${toMode}"
    if (details) {
        logMessage += " (${details})"
    }
    
    app.log(2, 'CycleTransition', logMessage)
    app.appendDabActivityLog(logMessage)
    
    // Record transition event for diagnostics
    try {
        def diagnostics = app.atomicState.diagnostics ?: [:]
        diagnostics.cycleTransitions = (diagnostics.cycleTransitions ?: 0) + 1
        diagnostics.lastCycleTransition = app.now()
        diagnostics.lastTransitionDetails = "${fromMode} ${arrow} ${toMode}"
        app.atomicState.diagnostics = diagnostics
    } catch (ignore) { }
}

/** Primary trigger for DAB. Uses duct temps to determine HVAC state. */
def updateHvacStateFromDuctTemps() {
    String previousMode = app.atomicState.thermostat1State?.mode ?: 'idle'
    String hvacMode = (calculateHvacModeRobust() ?: 'idle')
    
    // Check for fan-only mode before processing normal HVAC modes
    if (app.settings.fanOnlyOpenAllVents && app.isFanActive()) {
        if (previousMode != 'idle') {
            recordCycleTransition(previousMode, 'fan-only', 'Fan-only mode detected')
            // Clean up any active DAB cycle
            if (app.atomicState.thermostat1State) {
                app.unschedule('initializeRoomStates')
                app.unschedule('finalizeRoomStates')
                app.unschedule('evaluateRebalancingVents')
                app.unschedule('reBalanceVents')
                app.unschedule('performHourlyCommit')
                app.atomicState.remove('thermostat1State')
                resetCoolingCycleState()
            }
        }
        
        if (app.atomicState.ventsByRoomId) {
            app.log(2, 'DAB', 'Fan-only mode active - opening all vents to 100%')
            app.openAllVents(app.atomicState.ventsByRoomId, app.MAX_PERCENTAGE_OPEN as int)
        }
        return  // Skip normal DAB processing
    }
    
    if (hvacMode != previousMode) {
        recordCycleTransition(previousMode, hvacMode)
        try { app.atomicState.hvacLastMode = previousMode } catch (ignore) { }
        try { app.atomicState.hvacCurrentMode = hvacMode } catch (ignore) { }
        try { app.atomicState.hvacLastChangeTs = app.now() } catch (ignore) { }
    }

    if (hvacMode in [COOLING, HEATING]) {
        if (!app.atomicState.thermostat1State || app.atomicState.thermostat1State?.mode != hvacMode) {
            app.atomicStateUpdate('thermostat1State', 'mode', hvacMode)
            app.atomicStateUpdate('thermostat1State', 'startedRunning', app.now())
            app.unschedule('initializeRoomStates')
            app.runInMillis(app.POST_STATE_CHANGE_DELAY_MS, 'initializeRoomStates', [data: hvacMode])
            app.recordStartingTemperatures()
            app.runEvery5Minutes('evaluateRebalancingVents')
            app.runEvery30Minutes('reBalanceVents')
            app.runEvery1Hour('performHourlyCommit')  // Add hourly commit for continuous runs
            app.updateDevicePollingInterval((app.settings?.pollingIntervalActive ?: app.POLLING_INTERVAL_ACTIVE) as Integer)
        }
    } else { // HVAC is idle
        if (app.atomicState.thermostat1State) {
            app.unschedule('initializeRoomStates')
            app.unschedule('finalizeRoomStates')
            app.unschedule('evaluateRebalancingVents')
            app.unschedule('reBalanceVents')
            app.unschedule('performHourlyCommit')  // Clean up hourly commit scheduling
            app.atomicStateUpdate('thermostat1State', 'finishedRunning', app.now())
            if (app.isDabEnabled()) {
                def params = [
                    ventIdsByRoomId: app.atomicState.ventsByRoomId,
                    startedCycle: app.atomicState.thermostat1State?.startedCycle,
                    startedRunning: app.atomicState.thermostat1State?.startedRunning,
                    finishedRunning: app.atomicState.thermostat1State?.finishedRunning,
                    hvacMode: app.atomicState.thermostat1State?.mode
                ]
                app.runInMillis(app.TEMP_READINGS_DELAY_MS, 'finalizeRoomStates', [data: params])
            }
            resetCoolingCycleState()  // Clean up cooling cycle state
            app.atomicState.remove('thermostat1State')
            app.updateDevicePollingInterval((app.settings?.pollingIntervalIdle ?: app.POLLING_INTERVAL_IDLE) as Integer)
        }
    }
}

/** Robust HVAC mode detection with dynamic cooling end detection. */
def calculateHvacModeRobust() {
    def vents = app.getChildDevices()?.findAll {
        it.currentValue('duct-temperature-c') != null &&
        (it.currentValue('room-current-temperature-c') != null || it.currentValue('current-temperature-c') != null)
    }

    def fallbackFromThermostat = {
        try {
            String op = app.settings?.thermostat1?.currentValue('thermostatOperatingState')?.toString()?.toLowerCase()
            if (op in ['heating', 'pending heat']) { return HEATING }
            if (op in ['cooling', 'pending cool']) { return COOLING }
        } catch (ignore) { }
        return null
    }

    if (!vents || vents.isEmpty()) { return (fallbackFromThermostat() ?: 'idle') }

    def diffs = []
    vents.each { v ->
        try {
            BigDecimal duct = v.currentValue('duct-temperature-c') as BigDecimal
            BigDecimal room = (v.currentValue('room-current-temperature-c') ?: v.currentValue('current-temperature-c')) as BigDecimal
            diffs << (duct - room)
        } catch (ignore) { }
    }

    if (!diffs) { return (fallbackFromThermostat() ?: 'idle') }
    
    def sorted = diffs.sort()
    BigDecimal median = sorted[sorted.size().intdiv(2)] as BigDecimal
    
    def currentState = app.atomicState.thermostat1State?.mode ?: 'idle'
    
    // Check for heating start
    if (median > DUCT_TEMP_DIFF_THRESHOLD) { 
        if (currentState == COOLING) {
            resetCoolingCycleState()  // End cooling tracking if switching to heating
        }
        return HEATING 
    }
    
    // Check for cooling with dynamic end detection
    if (median < -DUCT_TEMP_DIFF_THRESHOLD) {
        if (currentState == COOLING) {
            // Currently cooling - check if we should end dynamically
            if (checkDynamicCoolingEnd()) {
                app.log(2, 'CycleCool', 'Dynamic cooling end detected - transitioning to idle')
                resetCoolingCycleState()
                return 'idle'
            }
            return COOLING  // Continue cooling
        } else {
            // Starting cooling - check traditional start conditions
            if (checkCoolingStart()) {
                app.log(2, 'CycleCool', 'Dynamic cooling start detected')
                return COOLING
            }
        }
    }
    
    // If we were in cooling but no longer meet conditions, check dynamic end
    if (currentState == COOLING) {
        if (checkDynamicCoolingEnd()) {
            app.log(2, 'CycleCool', 'Dynamic cooling end detected via transition check')
            resetCoolingCycleState()
            return 'idle'
        }
        return COOLING  // Continue cooling even if basic threshold not met
    }
    
    // Default fallback
    return (fallbackFromThermostat() ?: 'idle')
}

// --- Enhanced Cycle Diagnostics ---
def runDabDiagnostic() {
    try {
        def currentTime = new Date()
        def timeZone = app.location?.timeZone ?: TimeZone.getTimeZone('UTC')
        def thermostatState = app.atomicState.thermostat1State
        def coolingState = app.atomicState.coolingCycleState
        def diagnostics = app.atomicState.diagnostics ?: [:]
        
        def result = [
            timestamp: currentTime.format('yyyy-MM-dd HH:mm:ss', timeZone),
            currentHvacMode: thermostatState?.mode ?: 'idle',
            cycleInfo: [:]
        ]
        
        // Current cycle information
        if (thermostatState) {
            def runningMinutes = (app.now() - thermostatState.startedRunning) / (1000 * 60)
            result.cycleInfo.runningMinutes = Math.round(runningMinutes)
            result.cycleInfo.startedAt = new Date(thermostatState.startedRunning).format('HH:mm:ss', timeZone)
            
            if (thermostatState.startedCycle) {
                def totalMinutes = (app.now() - thermostatState.startedCycle) / (1000 * 60)
                result.cycleInfo.totalMinutes = Math.round(totalMinutes)
            }
        }
        
        // Cooling cycle debug information
        if (coolingState && thermostatState?.mode == COOLING) {
            def currentDuctF = getCurrentDuctTempF()
            def currentDeltaF = getHottestRoomDeltaF()
            
            result.coolingCycleDebug = [
                pollCount: coolingState.pollCount ?: 0,
                stabilizationRemaining: coolingState.stabilizationPolls ?: 0,
                confirmationCount: coolingState.confirmationCount ?: 0,
                currentDuctTempF: currentDuctF ? String.format('%.1f', currentDuctF) : 'N/A',
                currentRoomDeltaF: currentDeltaF ? String.format('%.1f', currentDeltaF) : 'N/A',
                ductMinF: coolingState.ductMinF ? String.format('%.1f', coolingState.ductMinF) : 'N/A',
                ductBaseF: coolingState.ductBaseF ? String.format('%.1f', coolingState.ductBaseF) : 'N/A',
                deltaBaseF: coolingState.deltaBaseF ? String.format('%.1f', coolingState.deltaBaseF) : 'N/A',
                lastDuctTemps: coolingState.lastDuctTemps?.collect { String.format('%.1f', it) }?.join(', ') ?: 'N/A',
                lastRoomDeltas: coolingState.lastRoomDeltas?.collect { String.format('%.1f', it) }?.join(', ') ?: 'N/A'
            ]
            
            // Calculate potential end reasons for debug
            if (coolingState.ductBaseF != null && coolingState.ductMinF != null) {
                def riseAmount = coolingState.ductBaseF - coolingState.ductMinF
                result.coolingCycleDebug.potentialRiseEnd = String.format('%.1f', riseAmount)
                result.coolingCycleDebug.riseThresholdMet = (riseAmount >= COOLING_BASE_RISE_F || riseAmount >= COOLING_MIN_RISE_FROM_MIN_F)
            }
        }
        
        // System diagnostics
        result.systemDiagnostics = [
            hourlyCommits: diagnostics.hourlyCommits ?: 0,
            lastHourlyCommit: diagnostics.lastHourlyCommit ? new Date(diagnostics.lastHourlyCommit).format('HH:mm:ss', timeZone) : 'Never',
            hardResets: diagnostics.hardResets ?: 0,
            stuckWarnings: diagnostics.stuckWarnings ?: 0
        ]
        
        // Temperature readings from vents
        def vents = app.getChildDevices()?.findAll { 
            it.currentValue('duct-temperature-c') != null 
        }?.take(5) // Limit to first 5 for brevity
        
        if (vents) {
            result.ventReadings = vents.collect { v ->
                try {
                    def ductC = v.currentValue('duct-temperature-c') as BigDecimal
                    def roomC = (v.currentValue('room-current-temperature-c') ?: v.currentValue('current-temperature-c')) as BigDecimal
                    return [
                        name: v.getLabel() ?: v.currentValue('room-name') ?: 'Unknown',
                        ductF: String.format('%.1f', (ductC * 9.0/5.0) + 32.0),
                        roomF: roomC ? String.format('%.1f', (roomC * 9.0/5.0) + 32.0) : 'N/A',
                        diff: roomC ? String.format('%.1f', ductC - roomC) : 'N/A'
                    ]
                } catch (ignore) {
                    return [name: v.getLabel() ?: 'Unknown', error: 'Failed to read temps']
                }
            }
        }
        
        app.state.dabDiagnosticResult = result
        app.log(2, 'DAB', "Diagnostic completed: ${thermostatState?.mode ?: 'idle'} mode, ${result.cycleInfo.runningMinutes ?: 0} min running")
        
    } catch (Exception e) {
        app.logError("DAB diagnostic error: ${e.message}", 'DAB')
        app.state.dabDiagnosticResult = [
            timestamp: new Date().format('yyyy-MM-dd HH:mm:ss', app.location?.timeZone ?: TimeZone.getTimeZone('UTC')),
            error: e.message
        ]
    }
}

// NOTE: other helpers referenced above (e.g., getAverageHourlyRate, appendHourlyRate, etc.)
// must exist somewhere in your app/libraries. Missing ones will fail at runtime.
