/*
 *  Library: DabManager (core logic)
 *  Namespace: bot.flair
 */
library(
  author: "Jaime Botero",
  category: "utilities",
  description: "Dynamic Airflow Balancing core logic",
  name: "DabManager",
  namespace: "bot.flair",
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

// =================================================================================
// Core Balancing Lifecycle Methods
// =================================================================================

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

/** Finalizes a DAB cycle when the HVAC turns off. */
def finalizeRoomStates(data) {
    if (!data.ventIdsByRoomId || !data.startedCycle || !data.finishedRunning) {
        app.logWarn "Finalizing room states: missing required parameters (${data})", 'DAB'
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

/** Primary trigger for DAB. Uses duct temps to determine HVAC state. */
def updateHvacStateFromDuctTemps() {
    String previousMode = app.atomicState.thermostat1State?.mode ?: 'idle'
    String hvacMode = (calculateHvacModeRobust() ?: 'idle')
    
    if (hvacMode != previousMode) {
        app.appendDabActivityLog("HVAC State Change: ${previousMode} -> ${hvacMode}")
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
            app.updateDevicePollingInterval((app.settings?.pollingIntervalActive ?: app.POLLING_INTERVAL_ACTIVE) as Integer)
        }
    } else { // HVAC is idle
        if (app.atomicState.thermostat1State) {
            app.unschedule('initializeRoomStates')
            app.unschedule('finalizeRoomStates')
            app.unschedule('evaluateRebalancingVents')
            app.unschedule('reBalanceVents')
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
            app.atomicState.remove('thermostat1State')
            app.updateDevicePollingInterval((app.settings?.pollingIntervalIdle ?: app.POLLING_INTERVAL_IDLE) as Integer)
        }
    }
}

/** Robust HVAC mode detection using median duct-room temperature difference. */
def calculateHvacModeRobust() {
    def vents = app.getChildDevices()?.findAll {
        it.currentValue('duct-temperature-c') != null &&
        (it.currentValue('room-current-temperature-c') != null || it.currentValue('current-temperature-c') != null)
    }

    if (!vents || vents.isEmpty()) {
        def fallback = _fallbackFromThermostat()
        if (fallback != null) { return fallback }
        return 'idle'
    }

    def diffs = []
    vents.each { v ->
        try {
            BigDecimal duct = v.currentValue('duct-temperature-c') as BigDecimal
            BigDecimal room = (v.currentValue('room-current-temperature-c') ?: v.currentValue('current-temperature-c')) as BigDecimal
            diffs << (duct - room)
        } catch (ignore) { }
    }

    if (diffs.isEmpty()) {
        def fallback = _fallbackFromThermostat()
        if (fallback != null) { return fallback }
        return 'idle'
    }

    def sorted = diffs.sort()
    BigDecimal median = sorted[sorted.size().intdiv(2)] as BigDecimal

    if (median > DUCT_TEMP_DIFF_THRESHOLD) { return HEATING }
    if (median < -DUCT_TEMP_DIFF_THRESHOLD) { return COOLING }

    def fallback = _fallbackFromThermostat()
    if (fallback != null) { return fallback }
    return 'idle'
}

private def _fallbackFromThermostat() {
    try {
        String op = app.settings?.thermostat1?.currentValue('thermostatOperatingState')?.toString()?.toLowerCase()
        if (op in ['heating', 'pending heat']) { return HEATING }
        if (op in ['cooling', 'pending cool']) { return COOLING }
    } catch (ignore) { }
    return null
}

def runDabDiagnostic() {
    def results = [:]
    String hvacMode = calculateHvacModeRobust() ?: 'idle'
    BigDecimal globalSp = getGlobalSetpoint(hvacMode)
    results.inputs = [ hvacMode: hvacMode, globalSetpoint: globalSp, rooms: [:] ]
    def vents = app.getChildDevices()?.findAll { it.hasAttribute('percent-open') } ?: []
    vents.each { vent ->
        try {
            def roomId = vent.currentValue('room-id')?.toString()
            if (roomId && !results.inputs.rooms[roomId]) {
                results.inputs.rooms[roomId] = [
                    name: vent.currentValue('room-name') ?: roomId,
                    temp: vent.currentValue('room-current-temperature-c'),
                    rate: getAverageHourlyRate(roomId, hvacMode, (new Date().format('H', app.location?.timeZone ?: TimeZone.getTimeZone('UTC')) as Integer))
                ]
            }
        } catch (ignore) { }
    }
    def ventsByRoomId = [:]
    vents.each { v ->
        try {
            def rid = v.currentValue('room-id')?.toString()
            if (!rid) { return }
            def list = ventsByRoomId[rid] ?: []
            list << v.getDeviceNetworkId()
            ventsByRoomId[rid] = list
        } catch (ignore) { }
    }
    def rateAndTempPerVentId = getAttribsPerVentIdWeighted(ventsByRoomId, hvacMode)
    def longestTimeToTarget = calculateLongestMinutesToTarget(rateAndTempPerVentId, hvacMode, globalSp, (app.atomicState.maxHvacRunningTime ?: MAX_MINUTES_TO_SETPOINT), app.settings.thermostat1CloseInactiveRooms)
    def initialPositions = calculateOpenPercentageForAllVents(rateAndTempPerVentId, hvacMode, globalSp, longestTimeToTarget, app.settings.thermostat1CloseInactiveRooms)
    def minAirflowAdjusted = adjustVentOpeningsToEnsureMinimumAirflowTarget(rateAndTempPerVentId, hvacMode, initialPositions, app.settings.thermostat1AdditionalStandardVents)
    def finalPositions = applyOverridesAndFloors(minAirflowAdjusted)
    results.calculations = [ longestTimeToTarget: longestTimeToTarget, initialVentPositions: initialPositions ]
    results.adjustments = [ minimumAirflowAdjustments: minAirflowAdjusted ]
    results.finalOutput = [ finalVentPositions: finalPositions ]
    app.state.dabDiagnosticResult = results
}

// =================================================================================
// Helper Methods
// =================================================================================

/** Records the starting temperature for all rooms. */
def recordStartingTemperatures() {
    if (!app.atomicState.ventsByRoomId) { return }
    app.atomicState.ventsByRoomId.each { roomId, ventIds ->
        ventIds.each { ventId ->
            def vent = app.getChildDevice(ventId)
            if (vent) {
                def temp = getRoomTemp(vent)
                app.sendEvent(vent, [name: 'room-starting-temperature-c', value: temp])
            }
        }
    }
}

/** Calculates the rate of temperature change for a room. */
def calculateRoomChangeRate(BigDecimal startingTemp, BigDecimal currentTemp, BigDecimal minutes, int percentOpen, BigDecimal currentRate) {
    if (minutes < MIN_RUNTIME_FOR_RATE_CALC) {
        return currentRate
    }
    if (percentOpen == 0) {
        return 0
    }
    def tempChange = (currentTemp - startingTemp).abs()
    if (tempChange < MIN_DETECTABLE_TEMP_CHANGE) {
        return currentRate
    }

    def rate = (tempChange / minutes) * (100 / percentOpen)
    return Math.min(MAX_TEMP_CHANGE_RATE, Math.max(MIN_TEMP_CHANGE_RATE, rate))
}

/** Retrieves attributes for each vent, weighted by user settings. */
def getAttribsPerVentIdWeighted(ventsByRoomId, hvacMode) {
    def rateAndTempPerVentId = [:]
    def rateProp = (hvacMode == COOLING) ? 'room-cooling-rate' : 'room-heating-rate'

    ventsByRoomId.each { roomId, ventIds ->
        def roomTotalWeight = 0
        def roomVents = []
        ventIds.each { ventId ->
            def vent = app.getChildDevice(ventId)
            if (vent) {
                def weight = app.settings?."vent${ventId}Weight" ?: 1.0
                roomVents << [vent: vent, weight: (weight as BigDecimal)]
                roomTotalWeight += (weight as BigDecimal)
            }
        }

        ventIds.each { ventId ->
            def vent = app.getChildDevice(ventId)
            if (vent) {
                def temp = getRoomTemp(vent)
                def rate = vent.currentValue(rateProp) ?: 0
                def weight = app.settings?."vent${ventId}Weight" ?: 1.0
                def weightedRate = roomTotalWeight > 0 ? (rate * (weight / roomTotalWeight)) : rate

                rateAndTempPerVentId[ventId] = [
                    temp: temp,
                    rate: weightedRate > 0 ? weightedRate : MIN_TEMP_CHANGE_RATE,
                    isActive: vent.currentValue('room-active') == 'true'
                ]
            }
        }
    }
    return rateAndTempPerVentId
}

/** Calculates the longest time for any room to reach its target temperature. */
def calculateLongestMinutesToTarget(rateAndTempPerVentId, hvacMode, setpoint, maxHvacRunningTime, closeInactiveRooms) {
    BigDecimal longestTime = 0
    rateAndTempPerVentId.each { ventId, attribs ->
        if (closeInactiveRooms && !attribs.isActive) { return }
        if (hasRoomReachedSetpoint(hvacMode, setpoint, attribs.temp)) { return }

        def tempDiff = (setpoint - attribs.temp).abs()
        def timeToTarget = (tempDiff / attribs.rate).abs()
        if (timeToTarget > longestTime) {
            longestTime = timeToTarget
        }
    }
    return Math.min(longestTime, maxHvacRunningTime)
}

/** Calculates the ideal opening percentage for all vents. */
def calculateOpenPercentageForAllVents(rateAndTempPerVentId, hvacMode, setpoint, longestTimeToTarget, closeInactiveRooms) {
    def calcPercentOpen = [:]
    rateAndTempPerVentId.each { ventId, attribs ->
        if (closeInactiveRooms && !attribs.isActive) {
            calcPercentOpen[ventId] = 0
            return
        }
        if (hasRoomReachedSetpoint(hvacMode, setpoint, attribs.temp)) {
            calcPercentOpen[ventId] = 0
            return
        }

        def tempDiff = (setpoint - attribs.temp).abs()
        def requiredRate = longestTimeToTarget > 0 ? (tempDiff / longestTimeToTarget) : 0
        def percentOpen = attribs.rate > 0 ? (requiredRate / attribs.rate) * 100 : 0

        calcPercentOpen[ventId] = Math.min(100, Math.max(0, percentOpen))
    }
    return calcPercentOpen
}

/** Adjusts vent openings to ensure a minimum safe airflow. */
def adjustVentOpeningsToEnsureMinimumAirflowTarget(rateAndTempPerVentId, hvacMode, calcPercentOpen, additionalStandardVents) {
    BigDecimal totalVentCapacity = rateAndTempPerVentId.size() + (additionalStandardVents ?: 0)
    if (totalVentCapacity == 0) return calcPercentOpen

    BigDecimal minCombinedFlow = MIN_COMBINED_VENT_FLOW
    BigDecimal currentTotalFlow = (calcPercentOpen.values().sum() + ((additionalStandardVents ?: 0) * app.STANDARD_VENT_DEFAULT_OPEN)) / totalVentCapacity

    if (currentTotalFlow >= minCombinedFlow) {
        return calcPercentOpen
    }

    def adjustableVents = calcPercentOpen.findAll { it.value < 100 }
    if (adjustableVents.isEmpty()) {
        return calcPercentOpen
    }

    int iterations = 0
    while (currentTotalFlow < minCombinedFlow && iterations < MAX_ITERATIONS) {
        BigDecimal shortfall = minCombinedFlow - currentTotalFlow
        BigDecimal totalProportion = 0
        def proportions = [:]

        adjustableVents.each { ventId, percentOpen ->
            def attribs = rateAndTempPerVentId[ventId]
            if (attribs) {
                def tempDiff = (getGlobalSetpoint(hvacMode) - attribs.temp).abs()
                proportions[ventId] = tempDiff
                totalProportion += tempDiff
            }
        }

        if (totalProportion == 0) { break }

        proportions.each { ventId, proportion ->
            def increment = (proportion / totalProportion) * shortfall * INCREMENT_PERCENTAGE
            calcPercentOpen[ventId] += increment
            if (calcPercentOpen[ventId] > 100) {
                calcPercentOpen[ventId] = 100
            }
        }
        currentTotalFlow = (calcPercentOpen.values().sum() + ((additionalStandardVents ?: 0) * app.STANDARD_VENT_DEFAULT_OPEN)) / totalVentCapacity
        iterations++
    }
    return calcPercentOpen
}

/** Applies manual overrides and minimum opening floors. */
def applyOverridesAndFloors(Map percentOpenings) {
    def finalPositions = [:]
    def overrides = app.atomicState?.manualOverrides ?: [:]
    int floorPct = app.settings?.allowFullClose ? 0 : (app.settings?.minVentFloorPercent ?: 0)

    percentOpenings.each { ventId, percent ->
        if (overrides[ventId] != null) {
            finalPositions[ventId] = overrides[ventId]
        } else {
            finalPositions[ventId] = Math.max(floorPct, percent as int)
        }
    }
    return finalPositions
}

/** Gets the average hourly rate of temperature change for a room. */
def getAverageHourlyRate(String roomId, String hvacMode, Integer hour) {
    def rates = getHourlyRates(roomId, hvacMode, hour)
    if (!rates || rates.isEmpty()) { return 0 }
    return rates.sum() / rates.size()
}

/** Appends a new hourly rate measurement to the history. */
def appendHourlyRate(String roomId, String hvacMode, Integer hour, BigDecimal newRate) {
    if (newRate == null || newRate <= 0) { return }
    app.initializeDabHistory()
    def hist = app.atomicState?.dabHistory ?: [entries:[], hourlyRates:[:]]

    if (app.settings?.enableOutlierRejection) {
        def assessment = assessOutlierForHourly(roomId, hvacMode, hour, newRate)
        if (assessment.action == 'reject') { return }
        if (assessment.action == 'clip') { newRate = assessment.value }
    }
    if (app.settings?.enableEwma) {
        newRate = updateEwmaRate(roomId, hvacMode, hour, newRate)
    }

    try {
        def entries = (hist.entries ?: []) as List
        entries << [app.now(), roomId, hvacMode, hour, newRate]
        hist.entries = entries
    } catch(ignore) {}

    try {
        def roomRates = hist.hourlyRates[roomId] ?: [:]
        def modeRates = roomRates[hvacMode] ?: [:]
        def hourRates = modeRates[hour.toString()] ?: []
        hourRates << newRate
        modeRates[hour.toString()] = hourRates
        roomRates[hvacMode] = modeRates
        hist.hourlyRates[roomId] = roomRates
    } catch(ignore) {}

    app.atomicState.dabHistory = hist
}

/** Retrieves all stored rates for a specific room, HVAC mode, and hour. */
def getHourlyRates(String roomId, String hvacMode, Integer hour) {
    app.initializeDabHistory()
    def hist = app.atomicState?.dabHistory
    Integer retention = app.getRetentionDays()
    Long cutoff = app.now() - retention * 24L * 60L * 60L * 1000L
    def entries = (hist instanceof List) ? hist : (hist?.entries ?: [])
    def list = entries.findAll { entry ->
        try {
            return entry[1] == roomId && entry[2] == hvacMode && entry[3] == (hour as Integer) && (entry[0] as Long) >= cutoff
        } catch (ignore) { return false }
    }*.get(4).collect { it as BigDecimal }
    if (list && list.size() > 0) { return list }
    try {
        def rates = hist?.hourlyRates?.get(roomId)?.get(hvacMode)?.get(hour.toString()) ?: []
        return rates.collect { it as BigDecimal }
    } catch (ignore) {
        return []
    }
}

/** Gets the temperature for a room, preferring a dedicated sensor. */
def getRoomTemp(def vent) {
    def ventId = vent.getId()
    def roomName = vent.currentValue('room-name') ?: 'Unknown'
    def tempDevice = app.settings."vent${ventId}Thermostat"
    if (app.settings?.useCachedRawForDab) {
        def samp = app.getLatestRawSample(vent.getDeviceNetworkId())
        if (samp && samp.size() >= 6) {
            def roomC = samp[5]
            if (roomC != null) { return roomC as BigDecimal }
        }
    }
    if (tempDevice) {
        def temp = tempDevice.currentValue('temperature')
        if (temp == null) {
            def roomTemp = vent.currentValue('room-current-temperature-c') ?: 0
            return roomTemp
        }
        if (app.settings.thermostat1TempUnit == '2') {
            temp = convertFahrenheitToCentigrade(temp)
        }
        return temp
    }
    def roomTemp = vent.currentValue('room-current-temperature-c')
    if (roomTemp == null) {
        return 0
    }
    return roomTemp
}

/** Gets the global setpoint, falling back to room medians if no thermostat. */
def getGlobalSetpoint(String hvacMode) {
    try {
        def sp = getThermostatSetpoint(hvacMode)
        if (sp != null) { return sp }
    } catch (ignore) { }
    def vents = app.getChildDevices()?.findAll { it.hasAttribute('percent-open') } ?: []
    def list = vents.collect { it.currentValue('room-set-point-c') }.findAll { it != null }.collect { it as BigDecimal }
    if (list && list.size() > 0) {
        def sorted = list.sort()
        int mid = sorted.size().intdiv(2)
        return sorted[mid] as BigDecimal
    }
    return (hvacMode == COOLING ? DEFAULT_COOLING_SETPOINT_C : DEFAULT_HEATING_SETPOINT_C)
}

/** Gets the setpoint from the main thermostat. */
def getThermostatSetpoint(String hvacMode) {
    if (!app.settings?.thermostat1) {
        return null
    }
    def thermostat = app.settings.thermostat1
    BigDecimal setpoint
    if (hvacMode == COOLING) {
        setpoint = thermostat?.currentValue('coolingSetpoint')
        if (setpoint != null) { setpoint -= SETPOINT_OFFSET }
    } else {
        setpoint = thermostat?.currentValue('heatingSetpoint')
        if (setpoint != null) { setpoint += SETPOINT_OFFSET }
    }
    if (setpoint == null) {
        setpoint = thermostat?.currentValue('thermostatSetpoint')
    }
    if (setpoint == null) {
        return null
    }
    if (app.settings.thermostat1TempUnit == '2') {
        setpoint = convertFahrenheitToCentigrade(setpoint)
    }
    return setpoint
}

/** Checks if a room has reached its setpoint. */
def hasRoomReachedSetpoint(String hvacMode, BigDecimal setpoint, BigDecimal currentTemp, BigDecimal offset = 0) {
    (hvacMode == COOLING && currentTemp <= setpoint - offset) ||
    (hvacMode == HEATING && currentTemp >= setpoint + offset)
}

/** Converts Fahrenheit to Celsius. */
def convertFahrenheitToCentigrade(BigDecimal tempValue) {
    (tempValue - 32) * (5 / 9)
}

private Map assessOutlierForHourly(String roomId, String hvacMode, Integer hour, BigDecimal candidate) {
    def decision = [action: 'accept']
    try {
        def list = getHourlyRates(roomId, hvacMode, hour) ?: []
        if (!list || list.size() < 4) { return decision }
        def sorted = list.collect { it as BigDecimal }.sort()
        BigDecimal median = sorted[sorted.size().intdiv(2)]
        def deviations = sorted.collect { (it - median).abs() }
        def devSorted = deviations.sort()
        BigDecimal mad = devSorted[devSorted.size().intdiv(2)]
        BigDecimal k = ((app.atomicState?.outlierThresholdMad ?: 3) as BigDecimal)
        if (mad == 0) {
            BigDecimal mean = (sorted.sum() as BigDecimal) / sorted.size()
            BigDecimal sd = Math.sqrt(sorted.collect { (it - mean).pow(2) }.sum() / Math.max(1, sorted.size() - 1))
            if (sd == 0) { return decision }
            if ((candidate - mean).abs() > (k * sd)) {
                if ((app.atomicState?.outlierMode ?: 'clip') == 'reject') return [action:'reject']
                BigDecimal bound = mean + (candidate > mean ? k * sd : -(k * sd))
                return [action:'clip', value: bound]
            }
        } else {
            BigDecimal scale = 1.4826 * mad
            if ((candidate - median).abs() > (k * scale)) {
                if ((app.atomicState?.outlierMode ?: 'clip') == 'reject') return [action:'reject']
                BigDecimal bound = median + (candidate > median ? k * scale : -(k * scale))
                return [action:'clip', value: bound]
            }
        }
    } catch (ignore) { }
    return decision
}

private BigDecimal updateEwmaRate(String roomId, String hvacMode, Integer hour, BigDecimal newRate) {
    try {
        def map = app.atomicState?.dabEwma ?: [:]
        def room = map[roomId] ?: [:]
        def mode = room[hvacMode] ?: [:]
        BigDecimal prev = (mode[hour as Integer]) as BigDecimal
        BigDecimal alpha = computeEwmaAlpha()
        BigDecimal updated = (prev == null) ? newRate : (alpha * newRate + (1 - alpha) * prev)
        mode[hour as Integer] = app.cleanDecimalForJson(updated)
        room[hvacMode] = mode
        map[roomId] = room
        app.atomicState.dabEwma = map
        return mode[hour as Integer] as BigDecimal
    } catch (ignore) { return newRate }
}

private BigDecimal computeEwmaAlpha() {
    try {
        BigDecimal hlDays = (app.atomicState?.ewmaHalfLifeDays ?: 3) as BigDecimal
        if (hlDays <= 0) { return 1.0 }
        BigDecimal N = hlDays
        BigDecimal alpha = 1 - Math.pow(2.0, (-1.0 / N.toDouble()))
        return (alpha as BigDecimal)
    } catch (ignore) { return 0.2 }
}
