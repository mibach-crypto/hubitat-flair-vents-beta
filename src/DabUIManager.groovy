/*
 *  Library: DabUIManager (UI layer)
 *  Namespace: bot.flair
 */
library(
  author: "Jaime Botero",
  category: "utilities",
  description: "Dynamic Airflow Balancing UI helpers",
  name: "DabUIManager",
  namespace: "bot.flair",
  documentationLink: ""
)

// =================================================================================
// UI Page Definitions
// =================================================================================

/** Renders the DAB Activity Log page. */
def dabActivityLogPage() {
    app.dynamicPage(name: 'dabActivityLogPage', title: 'DAB Activity Log', install: false, uninstall: false) {
        section {
            def entries = app.atomicState?.dabActivityLog ?: []
            if (entries) {
                entries.reverse().each { paragraph "<code>${it}</code>" }
            } else {
                paragraph 'No activity yet.'
            }
        }
    }
}

/** Renders the DAB Rates Table page with async loading. */
def dabRatesTablePage() {
    app.dynamicPage(name: 'dabRatesTablePage', title: 'Hourly DAB Rates Table', install: false, uninstall: false) {
        section {
            input name: 'tableHvacMode', type: 'enum', title: 'HVAC Mode', required: false, submitOnChange: true,
                    options: [ 'cooling': 'Cooling', 'heating': 'Heating', 'both': 'Both' ]

            try {
                if (app.atomicState?.prev_tableHvacMode != app.settings?.tableHvacMode) {
                    app.state.remove('dabRatesTableHtml')
                    app.atomicState.prev_tableHvacMode = app.settings?.tableHvacMode
                }
            } catch (ignore) { }

            if (!app.state.dabRatesTableHtml) {
                paragraph 'Loading...'
                app.runInMillis(100, 'buildDabRatesTableWrapper', [data: [overwrite: true]])
            } else {
                paragraph app.state.dabRatesTableHtml
            }
        }
    }
}

/** Renders the DAB Chart page. */
def dabChartPage() {
    app.dynamicPage(name: 'dabChartPage', title: 'Hourly DAB Rates', install: false, uninstall: false) {
        section {
            input name: 'chartHvacMode', type: 'enum', title: 'HVAC Mode', required: false, submitOnChange: true,
                    options: [ 'cooling': 'Cooling', 'heating': 'Heating' ]
            paragraph buildDabChart()
        }
    }
}

/** Renders the DAB Progress page. */
def dabProgressPage() {
    app.dynamicPage(name: 'dabProgressPage', title: 'DAB Progress', install: false, uninstall: false) {
        section {
            def vents = app.getChildDevices()?.findAll { it.hasAttribute('percent-open') } ?: []
            if (vents) {
                Map roomOptions = vents.collectEntries { v -> [(v.currentValue('room-id') ?: v.getId()): (v.currentValue('room-name') ?: v.getLabel())] }
                input name: 'progressRoom', type: 'enum', title: 'Room', required: false, submitOnChange: true, options: roomOptions
            }

            input name: 'progressHvacMode', type: 'enum', title: 'HVAC Mode', required: false, submitOnChange: true,
                    options: [ 'cooling': 'Cooling', 'heating': 'Heating', 'both': 'Both' ]
            input name: 'progressStart', type: 'date', title: 'Start Date', required: false, submitOnChange: true
            input name: 'progressEnd', type: 'date', title: 'End Date', required: false, submitOnChange: true

            try {
                if (app.atomicState?.prev_progressRoom != app.settings?.progressRoom) {
                    app.state.remove('dabProgressTableHtml')
                    app.atomicState.prev_progressRoom = app.settings?.progressRoom
                }
            } catch (ignore) { }

            if (!app.state.dabProgressTableHtml) {
                paragraph 'Loading...'
                app.runInMillis(100, 'buildDabProgressTableWrapper', [data: [overwrite: true]])
            } else {
                paragraph app.state.dabProgressTableHtml
            }
        }
    }
}

/** Renders the DAB Daily Summary page. */
def dabDailySummaryPage() {
    app.dynamicPage(name: 'dabDailySummaryPage', title: 'Daily DAB Summary', install: false, uninstall: false) {
        section {
            input name: 'dailySummaryPage', type: 'number', title: 'Page', required: false, defaultValue: 1, submitOnChange: true
            paragraph buildDabDailySummaryTable()
        }
    }
}

/** Renders the DAB Live Diagnostics page. */
def dabLiveDiagnosticsPage() {
    app.dynamicPage(name: 'dabLiveDiagnosticsPage', title: 'DAB Live Diagnostics', install: false, uninstall: false) {
        section('Controls') {
            input name: 'runDabDiagnostic', type: 'button', title: 'Run DAB Calculation Now', submitOnChange: true
            if (app.settings?.runDabDiagnostic) {
                try { runDabDiagnostic() } catch (e) { paragraph "Error running diagnostic: ${e.message}" }
                app.updateSetting('runDabDiagnostic', false)
            }
        }
        if (app.state?.dabDiagnosticResult) {
            section('DAB Calculation Results') {
                paragraph renderDabDiagnosticResults()
            }
        }
    }
}

/** Backup & Restore page. */
def efficiencyDataPage() {
    app.dynamicPage(name: 'efficiencyDataPage', title: 'Backup & Restore Efficiency Data', install: false, uninstall: false) {
        section("Export") {
            input name: 'exportEfficiencyData', type: 'button', title: 'Generate Backup', submitOnChange: true
            if (app.settings.exportEfficiencyData) {
                app.handleExportEfficiencyData()
                app.updateSetting('exportEfficiencyData', false)
            }
            if (app.state.exportJsonData) {
                input name: 'efficiencyJson', type: 'textarea', title: 'Backup Data (copy this)', value: app.state.exportJsonData, required: false
            }
        }
        section("Import") {
            input name: 'importJsonData', type: 'textarea', title: 'Paste Backup Data', required: false
            input name: 'importEfficiencyData', type: 'button', title: 'Restore Data', submitOnChange: true
            if (app.settings.importEfficiencyData) {
                app.handleImportEfficiencyData()
                app.updateSetting('importEfficiencyData', false)
            }
        }
    }
}

// =================================================================================
// UI Helper Methods
// =================================================================================

def buildDabChart() {
    def hvacMode = app.settings?.chartHvacMode ?: 'cooling'
    def vents = app.getChildDevices()?.findAll { it.hasAttribute('percent-open') } ?: []
    if (!vents) { return '<p>No vents available.</p>' }

    def data = [:]
    vents.each { vent ->
        def roomId = vent.currentValue('room-id')
        def roomName = vent.currentValue('room-name') ?: vent.getLabel()
        if (roomId && !data[roomName]) {
            data[roomName] = [:]
            (0..23).each { hour ->
                def rate = getAverageHourlyRate(roomId, hvacMode, hour)
                data[roomName][hour] = app.roundBigDecimal(rate, 4)
            }
        }
    }

    if (data.isEmpty()) { return '<p>No data to display.</p>' }

    def series = data.collect { roomName, hourlyData ->
        def rates = hourlyData.sort { it.key }.collect { it.value }
        "{ name: '${roomName}', data: ${rates} }"
    }.join(',')

    return """
        <div id="dab-chart" style="width:100%; height:400px;"></div>
        <script src="https://code.highcharts.com/highcharts.js"></script>
        <script>
            Highcharts.chart('dab-chart', {
                title: { text: 'Hourly DAB Rates (${hvacMode})' },
                xAxis: { categories: ${(0..23).collect { "'${it}'" }} },
                yAxis: { title: { text: 'Rate (°C/min)' } },
                series: [${series}]
            });
        </script>
    """
}

def buildDabRatesTable(Map data) {
    app.initializeDabHistory()
    def hist = app.atomicState?.dabHistory
    def vents = app.getChildDevices()?.findAll { it.hasAttribute('percent-open') } ?: []
    Map roomNames = vents.collectEntries { v -> [(v.currentValue('room-id') ?: v.getId()): (v.currentValue('room-name') ?: v.getLabel())] }
    String hvacMode = app.settings?.tableHvacMode ?: 'both'
    def modes = hvacMode == 'both' ? ['cooling', 'heating'] : [hvacMode]

    def hourlyRates = hist?.hourlyRates ?: [:]
    if (hourlyRates.isEmpty()) {
        app.state.dabRatesTableHtml = '<p>No hourly rates available yet.</p>'
        return app.state.dabRatesTableHtml
    }

    def html = new StringBuilder()
    html << "<table class='standard-table'>"
    html << "<tr><th>Room</th><th>Mode</th>"
    (0..23).each { hr -> html << "<th class='right-align'>${hr}</th>" }
    html << "</tr>"

    hourlyRates.each { roomId, modeMap ->
        modes.each { mode ->
            if (modeMap[mode]) {
                html << "<tr><td>${roomNames[roomId] ?: roomId}</td><td>${mode}</td>"
                (0..23).each { hour ->
                    def rates = modeMap[mode][hour.toString()] ?: []
                    def avg = rates ? (rates.sum() / rates.size()) : 0
                    html << "<td class='right-align'>${app.roundBigDecimal(avg, 4)}</td>"
                }
                html << "</tr>"
            }
        }
    }
    html << "</table>"
    app.state.dabRatesTableHtml = html.toString()
    return app.state.dabRatesTableHtml
}

def buildDabProgressTable(Map data) {
    // Implementation in main app file
    app.state.dabProgressTableHtml = app.buildDabProgressTable()
    return app.state.dabProgressTableHtml
}

def buildDabDailySummaryTable() {
    // Implementation in main app file
    return app.buildDabDailySummaryTable()
}

def renderDabDiagnosticResults() {
    def results = app.state?.dabDiagnosticResult
    if (!results) { return '<p>No diagnostic results to display.</p>' }

    def sb = new StringBuilder()
    sb << '<h3>Inputs</h3>'
    sb << "<p><b>HVAC Mode:</b> ${results.inputs.hvacMode}</p>"
    sb << "<p><b>Global Setpoint:</b> ${app.roundBigDecimal(results.inputs.globalSetpoint, 2)} &deg;C</p>"
    sb << '<h4>Room Data</h4>'
    sb << "<table class='standard-table'><tr><th>Room</th><th>Temp (&deg;C)</th><th>Rate</th></tr>"
    results.inputs.rooms.each { roomId, roomData ->
        sb << "<tr><td>${roomData.name}</td><td>${app.roundBigDecimal(roomData.temp, 2)}</td><td>${app.roundBigDecimal(roomData.rate, 4)}</td></tr>"
    }
    sb << '</table>'

    sb << '<h3>Calculations</h3>'
    sb << "<p><b>Longest Time to Target:</b> ${app.roundBigDecimal(results.calculations.longestTimeToTarget, 2)} minutes</p>"
    sb << '<h4>Initial Vent Positions</h4>'
    sb << "<table class='standard-table'><tr><th>Vent</th><th>Position</th></tr>"
    results.calculations.initialVentPositions?.each { ventId, pos ->
        def vent = app.getChildDevice(ventId)
        sb << "<tr><td>${vent?.getLabel()}</td><td>${app.roundBigDecimal(pos, 1)}%</td></tr>"
    }
    sb << '</table>'

    sb << '<h3>Adjustments</h3>'
    sb << '<h4>Minimum Airflow Adjustments</h4>'
    sb << "<table class='standard-table'><tr><th>Vent</th><th>Position</th></tr>"
    results.adjustments.minimumAirflowAdjustments?.each { ventId, pos ->
        def vent = app.getChildDevice(ventId)
        sb << "<tr><td>${vent?.getLabel()}</td><td>${app.roundBigDecimal(pos, 1)}%</td></tr>"
    }
    sb << '</table>'

    sb << '<h3>Final Output</h3>'
    sb << '<h4>Final Vent Positions</h4>'
    sb << "<table class='standard-table'><tr><th>Vent</th><th>Position</th></tr>"
    results.finalOutput.finalVentPositions?.each { ventId, pos ->
        def vent = app.getChildDevice(ventId)
        sb << "<tr><td>${vent?.getLabel()}</td><td>${app.roundToNearestMultiple(pos)}%</td></tr>"
    }
    sb << '</table>'
    return sb.toString()
}

def handleImportEfficiencyData() {
    try {
        def jsonData = app.settings.importJsonData
        if (!jsonData?.trim()) { 
            app.state.importStatus = 'No JSON data provided.'
            return 
        }
        
        def data = new groovy.json.JsonSlurper().parseText(jsonData)
        if (!data.efficiencyData) {
            app.state.importStatus = 'Invalid data format.'
            return
        }

        data.efficiencyData.roomEfficiencies?.each { roomData ->
            def vent = app.getChildDevices().find { it.currentValue('room-id') == roomData.roomId }
            if (vent) {
                app.sendEvent(vent, [name: 'room-cooling-rate', value: roomData.coolingRate])
                app.sendEvent(vent, [name: 'room-heating-rate', value: roomData.heatingRate])
            }
        }
        if (data.efficiencyData.dabHistory) {
            app.atomicState.dabHistory = data.efficiencyData.dabHistory
        }
        app.state.importStatus = 'Import successful!'
    } catch (e) {
        app.state.importStatus = "Import failed: ${e.message}"
    }
}

def handleClearDabData() {
    app.atomicState.remove('dabHistory')
    app.atomicState.remove('dabEwma')
    app.atomicState.remove('dabDailyStats')
    app.atomicState.remove('dabActivityLog')
    app.getChildDevices().each {
        if (it.hasAttribute('room-cooling-rate')) {
            app.sendEvent(it, [name: 'room-cooling-rate', value: 0])
            app.sendEvent(it, [name: 'room-heating-rate', value: 0])
        }
    }
}