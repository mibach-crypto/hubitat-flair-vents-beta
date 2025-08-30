// File: DabManager.groovy
class DabManager {
    def app

    DabManager(parent) {
        this.app = parent
    }

    // Rebalance vents after sufficient runtime and finalize states
    def reBalanceVents() {
        def thermostatState = app.atomicState.thermostat1State
        if (!thermostatState || !thermostatState.startedRunning) {
            app.log(3, 'App', 'Skipping rebalance: HVAC cycle not properly started.')
            return
        }

        def runningMinutes = (app.now() - thermostatState.startedRunning) / (1000 * 60)
        if (runningMinutes < app.MIN_RUNTIME_FOR_RATE_CALC) {
            app.log(3, 'App', "Skipping rebalance: HVAC has only been running for ${runningMinutes} minutes.")
            return
        }

        app.log(3, 'App', 'Rebalancing Vents!!!')
        app.appendDabActivityLog("Rebalancing vents")
        def params = [
            ventIdsByRoomId: app.atomicState.ventsByRoomId,
            startedCycle: thermostatState.startedCycle,
            startedRunning: thermostatState.startedRunning,
            finishedRunning: app.now(),
            hvacMode: thermostatState.mode
        ]
        app.finalizeRoomStates(params)
        app.initializeRoomStates(thermostatState.mode)
        try {
            def decisions = (app.state.recentVentDecisions ?: []).findAll { it.stage == 'final' }
            def changed = decisions.findAll { it.pct != null }
            def summary = changed.takeRight(5).collect { d -> "${d.room}:${d.pct}%" }
            if (summary) { app.appendDabActivityLog("Changes: ${summary.join(', ')}") }
        } catch (ignore) { }
    }

    // Evaluate whether to trigger a rebalance; throttled by lastRebalanceTime
    def evaluateRebalancingVents() {
        def thermostatState = app.atomicState.thermostat1State
        if (!thermostatState) { return }

        def lastRebalance = app.atomicState.lastRebalanceTime ?: 0
        if ((app.now() - lastRebalance) < (app.MIN_RUNTIME_FOR_RATE_CALC * 60 * 1000)) {
            return
        }

        def ventIdsByRoomId = app.atomicState.ventsByRoomId
        String hvacMode = thermostatState.mode
        def setPoint = app.getGlobalSetpoint(hvacMode)

        ventIdsByRoomId.each { roomId, ventIds ->
            for (ventId in ventIds) {
                try {
                    def vent = app.getChildDevice(ventId)
                    if (!vent) { continue }
                    if (vent.currentValue('room-active') != 'true') { continue }
                    def currPercentOpen = (vent.currentValue('percent-open') ?: 0).toInteger()
                    if (currPercentOpen <= app.STANDARD_VENT_DEFAULT_OPEN) { continue }
                    def roomTemp = app.getRoomTemp(vent)
                    if (!app.hasRoomReachedSetpoint(hvacMode, setPoint, roomTemp, app.REBALANCING_TOLERANCE)) {
                        continue
                    }
                    app.log(3, 'App', "Rebalancing Vents - '${vent.currentValue('room-name')}' is at ${roomTemp}°C (target: ${setPoint})")

                    app.atomicState.lastRebalanceTime = app.now()
                    reBalanceVents()
                    return
                } catch (err) {
                    app.logError err
                }
            }
        }
    }
}

