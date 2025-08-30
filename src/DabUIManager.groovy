// File: DabUIManager.groovy
class DabUIManager {
    def app
    def dabManager

    DabUIManager(parent, dabManager = null) {
        this.app = parent
        this.dabManager = dabManager
    }

    // DAB Activity Log page
    def dabActivityLogPage() {
        return app.dynamicPage(name: 'dabActivityLogPage', title: 'DAB Activity Log', install: false, uninstall: false) {
            app.section {
                def entries = app.atomicState?.dabActivityLog ?: []
                if (entries) {
                    entries.reverse().each { app.paragraph "<code>${it}</code>" }
                } else {
                    app.paragraph 'No activity yet.'
                }
            }
            app.section {
                app.href name: 'backToMain', title: 'Back to Main Settings', description: 'Return to the main app configuration', page: 'mainPage'
            }
        }
    }

    // Rates table page with async build + cache
    def dabRatesTablePage() {
        return app.dynamicPage(name: 'dabRatesTablePage', title: 'Hourly DAB Rates Table', install: false, uninstall: false) {
            app.section {
                app.input name: 'tableHvacMode', type: 'enum', title: 'HVAC Mode', required: false, submitOnChange: true,
                        options: [(app.COOLING): 'Cooling', (app.HEATING): 'Heating', 'both': 'Both']

                try {
                    def prevMode = app.atomicState?.prev_tableHvacMode
                    def nowMode = app.settings?.tableHvacMode
                    if (prevMode != nowMode) {
                        app.state.dabRatesTableHtml = null
                        app.atomicState.prev_tableHvacMode = nowMode
                    }
                } catch (ignore) { }

                if (!app.state.dabRatesTableHtml) {
                    app.paragraph 'Loading...'
                    app.runInMillis(100, 'buildDabRatesTable', [data: [overwrite: true]])
                } else {
                    app.paragraph app.state.dabRatesTableHtml
                }
            }
            app.section {
                app.href name: 'backToMain', title: 'Back to Main Settings', description: 'Return to the main app configuration', page: 'mainPage'
            }
        }
    }

    def dabChartPage() {
        return app.dynamicPage(name: 'dabChartPage', title: 'Hourly DAB Rates', install: false, uninstall: false) {
            app.section {
                app.input name: 'chartHvacMode', type: 'enum', title: 'HVAC Mode', required: false, submitOnChange: true,
                        options: [(app.COOLING): 'Cooling', (app.HEATING): 'Heating', 'both': 'Both']
                app.paragraph app.buildDabChart()
            }
            app.section {
                app.href name: 'backToMain', title: 'Back to Main Settings', description: 'Return to the main app configuration', page: 'mainPage'
            }
        }
    }

    def dabProgressPage() {
        return app.dynamicPage(name: 'dabProgressPage', title: 'DAB Progress', install: false, uninstall: false) {
            app.section {
                def vents = app.getChildDevices()?.findAll { it.hasAttribute('percent-open') } ?: []
                boolean hasVents = vents && vents.size() > 0
                if (hasVents) {
                    Map roomOptions = vents.collectEntries { v -> [(v.currentValue('room-id') ?: v.getId()): (v.currentValue('room-name') ?: v.getLabel())] }
                    app.input name: 'progressRoom', type: 'enum', title: 'Room', required: false, submitOnChange: true, options: roomOptions
                } else {
                    app.paragraph '<p>No vents available. Add vents to use DAB Progress.</p>'
                }
                app.input name: 'progressHvacMode', type: 'enum', title: 'HVAC Mode', required: false, submitOnChange: true,
                        options: [(app.COOLING): 'Cooling', (app.HEATING): 'Heating', 'both': 'Both']
                app.input name: 'progressStart', type: 'date', title: 'Start Date', required: false, submitOnChange: true
                app.input name: 'progressEnd', type: 'date', title: 'End Date', required: false, submitOnChange: true

                try {
                    def prevRoom = app.atomicState?.prev_progressRoom
                    def prevMode = app.atomicState?.prev_progressHvacMode
                    def prevStart = app.atomicState?.prev_progressStart
                    def prevEnd = app.atomicState?.prev_progressEnd
                    def nowRoom = app.settings?.progressRoom
                    def nowMode = app.settings?.progressHvacMode
                    def nowStart = app.settings?.progressStart
                    def nowEnd = app.settings?.progressEnd
                    if (prevRoom != nowRoom || prevMode != nowMode || prevStart != nowStart || prevEnd != nowEnd) {
                        app.state.dabProgressTableHtml = null
                        app.atomicState.prev_progressRoom = nowRoom
                        app.atomicState.prev_progressHvacMode = nowMode
                        app.atomicState.prev_progressStart = nowStart
                        app.atomicState.prev_progressEnd = nowEnd
                    }
                } catch (ignore2) { }

                if (hasVents) {
                    if (!app.state.dabProgressTableHtml) {
                        app.paragraph 'Loading...'
                        app.runInMillis(100, 'buildDabProgressTable', [data: [overwrite: true]])
                    } else {
                        app.paragraph app.state.dabProgressTableHtml
                    }
                }
            }
            app.section {
                app.href name: 'backToMain', title: 'Back to Main Settings', description: 'Return to the main app configuration', page: 'mainPage'
            }
        }
    }

    def dabDailySummaryPage() {
        return app.dynamicPage(name: 'dabDailySummaryPage', title: 'Daily DAB Summary', install: false, uninstall: false) {
            app.section {
                app.input name: 'dailySummaryPage', type: 'number', title: 'Page', required: false, defaultValue: 1, submitOnChange: true
                app.paragraph app.buildDabDailySummaryTable()
            }
            app.section {
                app.href name: 'backToMain', title: 'Back to Main Settings', description: 'Return to the main app configuration', page: 'mainPage'
            }
        }
    }

    def dabLiveDiagnosticsPage() {
        return app.dynamicPage(name: 'dabLiveDiagnosticsPage', title: 'DAB Live Diagnostics', install: false, uninstall: false) {
            app.section('Controls') {
                app.input name: 'runDabDiagnostic', type: 'button', title: 'Run DAB Calculation Now', submitOnChange: true
                if (app.settings?.runDabDiagnostic) {
                    try { app.runDabDiagnostic() } catch (e) { app.paragraph 'Error:' }
                    app.app.updateSetting('runDabDiagnostic','')
                }
            }
            if (app.state?.dabDiagnosticResult) {
                app.section('DAB Calculation Results') {
                    app.paragraph app.renderDabDiagnosticResults()
                }
            }
        }
    }
}

