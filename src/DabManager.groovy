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

    // --- Pass-through stubs to app methods (to be migrated) ---
    def updateHvacStateFromDuctTemps() { app.updateHvacStateFromDuctTemps() }
    def initializeRoomStates(String hvacMode) { app.initializeRoomStates(hvacMode) }
    def finalizeRoomStates(data) { app.finalizeRoomStates(data) }
    def aggregateDailyDabStats() { app.aggregateDailyDabStats() }
    def sampleRawDabData() { app.sampleRawDabData() }
    def pruneRawCache() { app.pruneRawCache() }
    def dabHealthMonitor() { app.dabHealthMonitor() }

    // Robust HVAC mode detection using median duct-room temperature difference
    def calculateHvacModeRobust() {
        def vents = app.getChildDevices()?.findAll {
            it.currentValue('duct-temperature-c') != null &&
            (it.currentValue('room-current-temperature-c') != null ||
             it.currentValue('current-temperature-c') != null ||
             it.currentValue('temperature') != null)
        }

        def fallbackFromThermostat = {
            try {
                String op = app.settings?.thermostat1?.currentValue('thermostatOperatingState')?.toString()?.toLowerCase()
                if (op in ['heating', 'pending heat']) { return app.HEATING }
                if (op in ['cooling', 'pending cool']) { return app.COOLING }
            } catch (ignore) { }
            return null
        }

        if (!vents || vents.isEmpty()) { return (fallbackFromThermostat() ?: 'idle') }

        def diffs = []
        vents.each { v ->
            try {
                BigDecimal duct = v.currentValue('duct-temperature-c') as BigDecimal
                BigDecimal room = (v.currentValue('room-current-temperature-c') ?:
                                   v.currentValue('current-temperature-c') ?:
                                   v.currentValue('temperature')) as BigDecimal
                BigDecimal diff = duct - room
                diffs << diff
                app.log(4, 'DAB', "Vent ${v?.displayName ?: v?.id}: duct=${duct}C room=${room}C diff=${diff}C", v?.id)
            } catch (ignore) { }
        }
        if (!diffs) { return (fallbackFromThermostat() ?: 'idle') }
        def sorted = diffs.sort()
        BigDecimal median = sorted[sorted.size().intdiv(2)] as BigDecimal
        app.log(4, 'DAB', "Median duct-room temp diff=${median}C")
        if (median > app.DUCT_TEMP_DIFF_THRESHOLD) { return app.HEATING }
        if (median < -app.DUCT_TEMP_DIFF_THRESHOLD) { return app.COOLING }
        return (fallbackFromThermostat() ?: 'idle')
    }

    // History initialization and helpers
    def initializeDabHistory() {
        try {
            def hist = app.atomicState?.dabHistory
            if (hist == null) {
                app.atomicState.dabHistory = [entries: [], hourlyRates: [:]]
                return
            }
            if (hist instanceof List) {
                app.atomicState.dabHistory = [entries: hist, hourlyRates: [:]]
                return
            }
            if (hist instanceof Map) {
                if (hist.entries == null) { hist.entries = [] }
                if (hist.hourlyRates == null) { hist.hourlyRates = [:] }
                if ((hist.hourlyRates == null || hist.hourlyRates.isEmpty()) && hist.entries && hist.entries.size() > 0) {
                    def index = [:]
                    try {
                        Integer retention = app.getRetentionDays()
                        Long cutoff = app.now() - retention * 24L * 60L * 60L * 1000L
                        hist.entries.each { e ->
                            try {
                                Long ts = e[0] as Long; if (ts < cutoff) { return }
                                String r = e[1]; String m = e[2]; Integer h = (e[3] as Integer)
                                BigDecimal rate = e[4] as BigDecimal
                                def room = index[r] ?: [:]
                                def mode = room[m] ?: [:]
                                def list = (mode[h] ?: []) as List
                                list << rate
                                if (list.size() > retention) { list = list[-retention..-1] }
                                mode[h] = list
                                room[m] = mode
                                index[r] = room
                            } catch (ignore) { }
                        }
                        hist.hourlyRates = index
                    } catch (ignore) { }
                }
                try { app.atomicState.dabHistory = hist } catch (ignoreX) { }
            }
        } catch (Exception e) {
            app.logWarn "Failed to initialize/normalize DAB history: ${e?.message}", 'DAB'
        }
    }

    def getHourlyRates(String roomId, String hvacMode, Integer hour) {
        initializeDabHistory()
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
        } catch (ignore) { return [] }
    }

    def getAverageHourlyRate(String roomId, String hvacMode, Integer hour) {
        initializeDabHistory()
        if (app.atomicState?.enableEwma) {
            def ew = getEwmaRate(roomId, hvacMode, hour)
            if (ew != null) { return app.cleanDecimalForJson(ew as BigDecimal) }
        }
        def hist = app.atomicState?.dabHistory
        def rates = []
        try {
            rates = hist?.hourlyRates?.get(roomId)?.get(hvacMode)?.get(hour.toString()) ?: []
        } catch (ignore) { rates = [] }
        if (!rates || rates.size() == 0) {
            rates = getHourlyRates(roomId, hvacMode, hour) ?: []
        }
        if ((!rates || rates.size() == 0)) {
            boolean carry = true
            try { carry = (app.atomicState?.carryForwardLastHour != false) } catch (ignore) { carry = true }
            if (carry) {
                for (int i = 1; i <= 23; i++) {
                    int prevHour = ((hour as int) - i) % 24
                    if (prevHour < 0) { prevHour += 24 }
                    def last = getLastObservedHourlyRate(roomId, hvacMode, prevHour)
                    if (last != null) { return app.cleanDecimalForJson(last as BigDecimal) }
                }
            }
            return 0.0
        }
        BigDecimal sum = 0.0
        rates.each { sum += it as BigDecimal }
        BigDecimal base = (sum / rates.size())
        try {
            boolean enabled = (app.atomicState?.enableAdaptiveBoost != false)
            if (enabled) {
                BigDecimal boost = getAdaptiveBoostFactor(roomId, hvacMode, hour)
                base = base * (1.0 + boost)
            }
        } catch (ignore) { }
        return app.cleanDecimalForJson(base)
    }

    private BigDecimal getLastObservedHourlyRate(String roomId, String hvacMode, Integer hour) {
        try {
            def hist = app.atomicState?.dabHistory
            def entries = (hist instanceof List) ? (hist as List) : (hist?.entries ?: [])
            if (!entries) { return null }
            for (int idx = entries.size()-1; idx >= 0; idx--) {
                def e = entries[idx]
                try {
                    if (e[1] == roomId && e[2] == hvacMode && (e[3] as Integer) == (hour as Integer)) {
                        return (e[4] as BigDecimal)
                    }
                } catch (ignore) { }
            }
        } catch (ignoreOuter) { }
        return null
    }

    private BigDecimal getAdaptiveBoostFactor(String roomId, String hvacMode, Integer hour) {
        try {
            boolean enabled = (app.atomicState?.enableAdaptiveBoost != null) ? (app.atomicState?.enableAdaptiveBoost != false) : app.ADAPTIVE_BOOST_ENABLED
            if (!enabled) { return 0.0 }
            Integer lookback = (app.atomicState?.adaptiveLookbackPeriods ?: app.ADAPTIVE_LOOKBACK_PERIODS) as Integer
            BigDecimal threshPct = (app.atomicState?.adaptiveThresholdPercent ?: app.ADAPTIVE_THRESHOLD_PERCENT) as BigDecimal
            BigDecimal boostPct = (app.atomicState?.adaptiveBoostPercent ?: app.ADAPTIVE_BOOST_PERCENT) as BigDecimal
            BigDecimal boostMaxPct = (app.atomicState?.adaptiveMaxBoostPercent ?: app.ADAPTIVE_MAX_BOOST_PERCENT) as BigDecimal
            def entries = app.atomicState?.adaptiveMarksEntries ?: []
            if (!entries) { return 0.0 }
            int hits = 0
            def seenHours = [] as Set
            for (int i = entries.size()-1; i >=0 && seenHours.size() < lookback; i--) {
                def e = entries[i]
                try {
                    if (e[1] == roomId && e[2] == hvacMode) {
                        Integer hr = (e[3] as Integer)
                        if (hr == (((hour as int) - (seenHours.size()+1) + 24) % 24)) {
                            seenHours << hr
                            BigDecimal ratio = (e[4] as BigDecimal)
                            BigDecimal pct = ratio * 100.0
                            if (pct >= threshPct) { hits++ }
                        }
                    }
                } catch (ignore) { }
            }
            BigDecimal totalBoost = (boostPct * hits)
            if (totalBoost > boostMaxPct) { totalBoost = boostMaxPct }
            return (totalBoost / 100.0)
        } catch (ignoreOuter) { return 0.0 }
    }
    private void appendAdaptiveMark(String roomId, String hvacMode, Integer hour, BigDecimal ratio) {
        try {
            def list = app.atomicState?.adaptiveMarksEntries ?: []
            list << [app.now(), roomId, hvacMode, (hour as Integer), (ratio as BigDecimal)]
            if (list.size() > 5000) { list = list[-5000..-1] }
            app.atomicState.adaptiveMarksEntries = list
        } catch (ignore) { }
    }

    // Export efficiency data (logic)
    def exportEfficiencyData() {
        def data = [
            globalRates: [
                maxCoolingRate: app.cleanDecimalForJson(app.atomicState.maxCoolingRate),
                maxHeatingRate: app.cleanDecimalForJson(app.atomicState.maxHeatingRate)
            ],
            roomEfficiencies: [],
            dabHistory: app.atomicState?.dabHistory ?: [:],
            dabActivityLog: app.atomicState?.dabActivityLog ?: []
        ]
        app.getChildDevices().findAll { it.hasAttribute('percent-open') }.each { device ->
            def coolingRate = device.currentValue('room-cooling-rate') ?: 0
            def heatingRate = device.currentValue('room-heating-rate') ?: 0
            def roomData = [
                roomId: device.currentValue('room-id'),
                roomName: device.currentValue('room-name'),
                ventId: device.getDeviceNetworkId(),
                coolingRate: app.cleanDecimalForJson(coolingRate),
                heatingRate: app.cleanDecimalForJson(heatingRate)
            ]
            data.roomEfficiencies << roomData
        }
        return data
    }

    // Raw cache helpers
    private Integer getRawCacheRetentionHours() {
        try {
            Integer h = (app.settings?.rawDataRetentionHours ?: app.RAW_CACHE_DEFAULT_HOURS) as Integer
            if (h < 1) { h = 1 }
            if (h > 48) { h = 48 }
            return h
        } catch (ignore) { return app.RAW_CACHE_DEFAULT_HOURS }
    }
    private Long getRawCacheCutoffTs() { return app.now() - (getRawCacheRetentionHours() * 60L * 60L * 1000L) }
    private void appendRawDabSample(List entry) {
        try {
            def list = app.atomicState?.rawDabSamplesEntries ?: []
            list << entry
            Long cutoff = getRawCacheCutoffTs()
            list = list.findAll { e ->
                try { return (e[0] as Long) >= cutoff } catch (ignore) { return false }
            }
            if (list.size() > app.RAW_CACHE_MAX_ENTRIES) { list = list[-app.RAW_CACHE_MAX_ENTRIES..-1] }
            app.atomicState.rawDabSamplesEntries = list
            def lastByVent = app.atomicState?.rawDabLastByVent ?: [:]
            lastByVent[(entry[1] as String)] = entry
            app.atomicState.rawDabLastByVent = lastByVent
        } catch (ignore) { }
    }
    def getLatestRawSample(String ventId) { try { return (app.atomicState?.rawDabLastByVent ?: [:])[ventId] as List } catch (ignore) { return null } }
    def pruneRawCache() {
        try {
            def list = app.atomicState?.rawDabSamplesEntries ?: []
            if (!list) { return }
            Long cutoff = getRawCacheCutoffTs()
            list = list.findAll { e ->
                try { return (e[0] as Long) >= cutoff } catch (ignore) { return false }
            }
            if (list.size() > app.RAW_CACHE_MAX_ENTRIES) { list = list[-app.RAW_CACHE_MAX_ENTRIES..-1] }
            app.atomicState.rawDabSamplesEntries = list
            def lastByVent = [:]
            list.each { e -> lastByVent[(e[1] as String)] = e }
            app.atomicState.rawDabLastByVent = lastByVent
        } catch (ignore) { }
    }
    def sampleRawDabData() {
        if (!app.settings?.enableRawCache) { return }
        try {
            Long ts = app.now()
            String hvacMode = calculateHvacModeRobust()
            def vents = app.getChildDevices()?.findAll { it.hasAttribute('percent-open') } ?: []
            vents.each { v ->
                try {
                    String ventId = v.getDeviceNetworkId()
                    String roomId = v.currentValue('room-id')?.toString()
                    BigDecimal ductC = v.currentValue('duct-temperature-c') != null ? (v.currentValue('duct-temperature-c') as BigDecimal) : null
                    def roomCv = (v.currentValue('room-current-temperature-c') ?: v.currentValue('current-temperature-c'))
                    BigDecimal roomC = roomCv != null ? (roomCv as BigDecimal) : null
                    BigDecimal pct = (v.currentValue('percent-open') ?: v.currentValue('level') ?: 0) as BigDecimal
                    boolean active = (v.currentValue('room-active') ?: 'false') == 'true'
                    BigDecimal rSet = v.currentValue('room-set-point-c') != null ? (v.currentValue('room-set-point-c') as BigDecimal) : null
                    appendRawDabSample([ts, ventId, roomId, hvacMode, ductC, roomC, pct, active, rSet])
                } catch (ignore) { }
            }
        } catch (e) { app.log(2, 'App', "Raw sampler error: ${e?.message}") }
    }

    // Rebuild hourly index and daily stats
    def reindexDabHistory() {
        initializeDabHistory()
        def hist = app.atomicState?.dabHistory
        def entries = (hist instanceof List) ? hist : (hist?.entries ?: [])
        if ((!entries || entries.size() == 0) && (hist instanceof Map)) {
            try {
                def legacy = []
                hist.each { k, v ->
                    if (k in ['entries','hourlyRates']) { return }
                    if (v instanceof Map) {
                        v.each { hvacMode, list ->
                            if (list instanceof List) {
                                list.each { rec ->
                                    try {
                                        if (rec instanceof Map && rec.date && rec.hour != null && rec.rate != null) {
                                            Date d = Date.parse('yyyy-MM-dd HH', "${rec.date} ${rec.hour}")
                                            legacy << [d.getTime(), k.toString(), hvacMode.toString(), (rec.hour as Integer), (rec.rate as BigDecimal)]
                                        }
                                    } catch (ignore) { }
                                }
                            }
                        }
                    }
                }
                if (legacy && legacy.size() > 0) { entries = legacy }
            } catch (ignore) { }
        }
        Integer retention = app.getRetentionDays()
        Long cutoff = app.now() - retention * 24L * 60L * 60L * 1000L
        def index = [:]
        int rooms = 0
        try {
            entries.each { e ->
                try {
                    Long ts = e[0] as Long; if (ts < cutoff) { return }
                    String r = e[1]; String m = e[2]; Integer h = (e[3] as Integer)
                    BigDecimal rate = e[4] as BigDecimal
                    def room = index[r] ?: [:]
                    def mode = room[m] ?: [:]
                    def list = (mode[h] ?: []) as List
                    list << rate
                    if (list.size() > retention) { list = list[-retention..-1] }
                    mode[h] = list
                    room[m] = mode
                    index[r] = room
                } catch (ignore) { }
            }
            rooms = index.keySet().size()
            if (hist instanceof List) {
                try { app.atomicState.dabHistory = [entries: entries, hourlyRates: index] } catch (ignore) { }
            } else {
                try { hist.hourlyRates = index } catch (ignore) { }
                try { hist.entries = entries } catch (ignore) { }
                try { app.atomicState.dabHistory = hist } catch (ignore) { }
            }
        } catch (ignore) { }
        try {
            def tz = app.location?.timeZone ?: TimeZone.getTimeZone('UTC')
            def byDay = [:]
            entries.each { e ->
                try {
                    Long ts = e[0] as Long
                    if (ts < cutoff) { return }
                    String r = e[1]; String m = e[2]
                    BigDecimal rate = (e[4] as BigDecimal)
                    String dateStr = new Date(ts).format('yyyy-MM-dd', tz)
                    def roomMap = byDay[r] ?: [:]
                    def modeMap = roomMap[m] ?: [:]
                    def list = (modeMap[dateStr] ?: []) as List
                    list << rate
                    modeMap[dateStr] = list
                    roomMap[m] = modeMap
                    byDay[r] = roomMap
                } catch (ignore) { }
            }
            def stats = [:]
            byDay.each { roomId, modeMap ->
                def roomStats = stats[roomId] ?: [:]
                modeMap.each { hvacMode2, dateMap ->
                    def modeStats = []
                    dateMap.keySet().sort().each { ds ->
                        def list = dateMap[ds]
                        if (list && list.size() > 0) {
                            BigDecimal sum = 0.0
                            list.each { sum += it as BigDecimal }
                            BigDecimal avg = app.cleanDecimalForJson(sum / list.size())
                            modeStats << [date: ds, avg: avg]
                        }
                    }
                    roomStats[hvacMode2] = modeStats
                }
                stats[roomId] = roomStats
            }
            app.atomicState.dabDailyStats = stats
        } catch (ignore) { }
        return [entries: entries?.size() ?: 0, rooms: rooms]
    }

    // Append hourly rate and structured history entries
    def appendHourlyRate(String roomId, String hvacMode, Integer hour, BigDecimal rate) {
        if (!roomId || !hvacMode || hour == null || rate == null) {
            app.recordHistoryError('Null value detected while appending hourly rate')
            return
        }
        initializeDabHistory()
        def hist = app.atomicState?.dabHistory
        if (hist == null) { hist = [entries: [], hourlyRates: [:]] }
        def hourly = (hist instanceof Map) ? (hist?.hourlyRates ?: [:]) : [:]
        def room = hourly[roomId] ?: [:]
        def mode = room[hvacMode] ?: [:]
        String hKey = hour.toString()
        def list = (mode[hKey] ?: []) as List
        list << (rate as BigDecimal)
        Integer retention = app.getRetentionDays()
        if (list.size() > retention) { list = list[-retention..-1] }
        mode[hKey] = list
        room[hvacMode] = mode
        hourly[roomId] = room
        try { hist.hourlyRates = hourly } catch (ignore) { }
        try {
            def entries = (hist instanceof List) ? (hist as List) : (hist.entries ?: [])
            Long ts = app.now()
            Integer hInt = hour as Integer
            entries << [ts, roomId, hvacMode, hInt, (rate as BigDecimal)]
            Long cutoff = ts - retention * 24L * 60L * 60L * 1000L
            entries = entries.findAll { e ->
                try { return (e[0] as Long) >= cutoff } catch (ignore) { return false }
            }
            if (hist instanceof List) {
                app.atomicState.dabHistory = entries
            } else {
                try { hist.entries = entries } catch (ignore) { }
            }
        } catch (ignore) { }
        app.atomicState.dabHistory = hist
        app.atomicState.lastHvacMode = hvacMode
    }

    def appendDabHistory(String roomId, String hvacMode, Integer hour, BigDecimal rate) {
        if (!roomId || !hvacMode || hour == null || rate == null) {
            app.recordHistoryError('Null value detected while appending DAB history entry')
            return
        }
        initializeDabHistory()
        def hist = app.atomicState?.dabHistory
        if (hist == null) { hist = [entries: [], hourlyRates: [:]] }
        def entries = (hist instanceof List) ? (hist as List) : (hist?.entries ?: [])
        Long ts = app.now()
        entries << [ts, roomId, hvacMode, (hour as Integer), (rate as BigDecimal)]
        Integer retention = app.getRetentionDays()
        Long cutoff = ts - retention * 24L * 60L * 60L * 1000L
        entries = entries.findAll { e ->
            try { return (e[0] as Long) >= cutoff } catch (ignore) { return false }
        }
        if (hist instanceof List) {
            try { app.atomicState.dabHistory = entries } catch (ignore) { }
        } else {
            try { hist.entries = entries } catch (ignore) { }
            try { app.atomicState.dabHistory = hist } catch (ignore) { }
        }
        try { if (entries) { app.atomicState.dabHistoryStartTimestamp = (entries[0][0] as Long) } } catch (ignore) { }
        try { app.atomicState.lastHvacMode = hvacMode } catch (ignore) { }
    }
}
