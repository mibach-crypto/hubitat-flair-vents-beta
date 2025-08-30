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

    // --- Efficiency Data UI & helpers ---
    def efficiencyDataPage() {
        def vents = app.getChildDevices().findAll { it.hasAttribute('percent-open') }
        def roomsWithData = vents.findAll { (it.currentValue('room-cooling-rate') ?: 0) > 0 || (it.currentValue('room-heating-rate') ?: 0) > 0 }
        def exportJsonData = ""
        if (roomsWithData.size() > 0) {
            try {
                def efficiencyData = dabManager?.exportEfficiencyData() ?: [:]
                exportJsonData = generateEfficiencyJSON(efficiencyData)
            } catch (Exception e) {
                app.log(2, 'App', "Error generating export data: ${e.message}")
            }
        }
        return app.dynamicPage(name: 'efficiencyDataPage', title: 'Backup & Restore Efficiency Data', install: false, uninstall: false) {
            app.section { app.paragraph '<div style="background-color: #f0f8ff; padding: 15px; border-left: 4px solid #007bff; margin-bottom: 20px;"><h3 style="margin-top: 0; color: #0056b3;">What is this?</h3><p style="margin-bottom: 0;">Your Flair vents learn how efficiently each room heats and cools over time. This data helps the system optimize energy usage. Use this page to backup your data before app updates or restore it after system resets.</p></div>' }
            if (vents.size() > 0) {
                app.section('Current Status') {
                    if (roomsWithData.size() > 0) {
                        app.paragraph "<div style='color: green; font-weight: bold;'>Your system has learned efficiency data for ${roomsWithData.size()} out of ${vents.size()} rooms</div>"
                    } else {
                        app.paragraph "<div style='color: orange; font-weight: bold;'>Your system is still learning (${vents.size()} rooms found, but no efficiency data yet)</div>"
                        app.paragraph '<small>Let your system run for a few heating/cooling cycles before backing up data.</small>'
                    }
                }
            }
            if (roomsWithData.size() > 0 && exportJsonData) {
                app.section('Save Your Data (Backup)') {
                    def currentDate = new Date().format('yyyy-MM-dd')
                    def fileName = "Flair-Backup-${currentDate}.json"
                    def base64Data = exportJsonData.bytes.encodeBase64().toString()
                    def downloadUrl = "data:application/json;charset=utf-8;base64,${base64Data}"
                    app.paragraph 'Your backup data is ready:'
                    app.paragraph "<a href=\"${downloadUrl}\" download=\"${fileName}\">Download ${fileName}</a>"
                }
            } else if (vents.size() > 0) {
                app.section('Save Your Data (Backup)') { app.paragraph 'System is still learning. Check back after a few heating/cooling cycles.' }
            }
            app.section('Step 2: Restore Your Data (Import)') {
                app.paragraph '<p><strong>When should I do this?</strong></p><p>- After reinstalling this app<br>- After resetting your Hubitat hub<br>- After replacing hardware</p>'
                app.paragraph '<p><strong>How to restore your data:</strong></p><p>1. Find your saved backup JSON file (e.g., "Flair-Backup-2025-06-26.json")<br>2. Open the JSON file in Notepad/TextEdit<br>3. Select all text (Ctrl+A) and copy (Ctrl+C)<br>4. Paste it in the box below (Ctrl+V)<br>5. Click "Restore My Data"</p><p><small><strong>Note:</strong> Hubitat does not support file uploads, so please copy/paste the JSON content.</small></p>'
                app.input name: 'importJsonData', type: 'textarea', title: 'Paste JSON Backup Data', required: false, submitOnChange: false
                app.input name: 'importEfficiencyData', type: 'button', title: 'Restore My Data', submitOnChange: true
                if (app.settings?.importEfficiencyData) { handleImportEfficiencyData(); app.app.updateSetting('importEfficiencyData','') }
                if (app.state?.importStatus) { app.paragraph app.state.importStatus }
            }
            app.section('Maintenance') {
                app.input name: 'clearDabDataNow', type: 'button', title: 'Clear All DAB Data', submitOnChange: true
                if (app.settings?.clearDabDataNow) { handleClearDabData(); app.app.updateSetting('clearDabDataNow','') }
            }
        }
    }

    def handleExportEfficiencyData() {
        try {
            app.log(2, 'App', 'Starting efficiency data export')
            def efficiencyData = dabManager?.exportEfficiencyData() ?: [:]
            def jsonData = generateEfficiencyJSON(efficiencyData)
            def roomCount = (efficiencyData.roomEfficiencies ?: []).size()
            app.state.exportStatus = "Exported efficiency data for ${roomCount} rooms. Copy the JSON data below:"
            app.state.exportedJsonData = jsonData
            app.log(2, 'App', "Export completed successfully for ${roomCount} rooms")
        } catch (Exception e) {
            def errorMsg = "Export failed: ${e.message}"
            app.logError errorMsg
            app.state.exportStatus = "${errorMsg}"
            app.state.exportedJsonData = null
        }
    }

    def handleImportEfficiencyData() {
        try {
            app.log(2, 'App', 'Starting efficiency data import')
            app.state.remove('importStatus'); app.state.remove('importSuccess')
            def jsonData = app.settings.importJsonData
            if (!jsonData?.trim()) { app.state.importStatus = 'No JSON data provided. Please paste the exported efficiency data.'; app.state.importSuccess = false; return }
            def result = importEfficiencyData(jsonData.trim())
            if (result.success) {
                def statusMsg = "Import successful! Updated ${result.roomsUpdated} rooms"
                if (result.globalUpdated) { statusMsg += ' and global efficiency rates' }
                if (result.historyRestored) { statusMsg += ', restored history' }
                if (result.activityLogRestored) { statusMsg += ', restored activity log' }
                if (result.roomsSkipped > 0) { statusMsg += ". Skipped ${result.roomsSkipped} rooms (not found)" }
                app.state.importStatus = statusMsg; app.state.importSuccess = true; app.app.updateSetting('importJsonData','')
                app.log(2, 'App', "Import completed: ${result.roomsUpdated} rooms updated, ${result.roomsSkipped} skipped")
            } else {
                app.state.importStatus = "Import failed: ${result.error}"; app.state.importSuccess = false; app.logError "Import failed: ${result.error}"
            }
        } catch (e) { app.state.importStatus = "Import failed: ${e.message}"; app.state.importSuccess = false; app.logError "Import exception: ${e.message}" }
    }

    def handleClearExportData() { try { app.state.remove('exportStatus'); app.state.remove('exportedJsonData') } catch (ignore) { } }
    def generateEfficiencyJSON(data) { def exportData = [exportMetadata: [version: '0.24', exportDate: new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'"), structureId: app.settings.structureId ?: 'Unknown'], efficiencyData: data]; return groovy.json.JsonOutput.toJson(exportData) }
    def importEfficiencyData(jsonContent) { try { def jsonData = new groovy.json.JsonSlurper().parseText(jsonContent); if (!validateImportData(jsonData)) { return [success:false,error:'Invalid data format. Please ensure you are using exported efficiency data.'] } def results = applyImportedEfficiencies(jsonData.efficiencyData); return [success:true, globalUpdated:results.globalUpdated, roomsUpdated:results.roomsUpdated, roomsSkipped:results.roomsSkipped, historyRestored:results.historyRestored, activityLogRestored:results.activityLogRestored, errors:results.errors] } catch (Exception e) { return [success:false, error:e.message] } }
    def validateImportData(jsonData) { if (!jsonData.exportMetadata || !jsonData.efficiencyData) return false; if (!jsonData.efficiencyData.globalRates) return false; if (!jsonData.efficiencyData.roomEfficiencies) return false; if (jsonData.efficiencyData.dabHistory && !(jsonData.efficiencyData.dabHistory instanceof Map)) return false; if (jsonData.efficiencyData.dabActivityLog && !(jsonData.efficiencyData.dabActivityLog instanceof List)) return false; def gr = jsonData.efficiencyData.globalRates; if (gr.maxCoolingRate == null || gr.maxHeatingRate == null) return false; if (gr.maxCoolingRate < 0 || gr.maxHeatingRate < 0) return false; if (gr.maxCoolingRate > 10 || gr.maxHeatingRate > 10) return false; for (room in jsonData.efficiencyData.roomEfficiencies) { if (!room.roomId || !room.roomName || !room.ventId) return false; if (room.coolingRate == null || room.heatingRate == null) return false; if (room.coolingRate < 0 || room.heatingRate < 0) return false; if (room.coolingRate > 10 || room.heatingRate > 10) return false } return true }
    def applyImportedEfficiencies(efficiencyData) { def results=[globalUpdated:false, roomsUpdated:0, roomsSkipped:0, errors:[], historyRestored:false, activityLogRestored:false]; if (efficiencyData.globalRates) { try { app.atomicState.maxCoolingRate = efficiencyData.globalRates.maxCoolingRate } catch (ignore) { } ; try { app.atomicState.maxHeatingRate = efficiencyData.globalRates.maxHeatingRate } catch (ignore2) { } ; results.globalUpdated = true; app.log(2,'App',"Updated global rates: cooling=${efficiencyData.globalRates.maxCoolingRate}, heating=${efficiencyData.globalRates.maxHeatingRate}") } ; efficiencyData.roomEfficiencies?.each { roomData -> def device = matchDeviceByRoomId(roomData.roomId) ?: matchDeviceByRoomName(roomData.roomName); if (device) { app.sendEvent(device,[name:'room-cooling-rate',value:roomData.coolingRate]); app.sendEvent(device,[name:'room-heating-rate',value:roomData.heatingRate]); results.roomsUpdated++; app.log(2,'App',"Updated efficiency for '${roomData.roomName}': cooling=${roomData.coolingRate}, heating=${roomData.heatingRate}") } else { results.roomsSkipped++; results.errors << "Room not found: ${roomData.roomName} (${roomData.roomId})"; app.log(2,'App',"Skipped room '${roomData.roomName}' - no matching device found") } } ; if (efficiencyData.dabHistory) { app.atomicState.dabHistory = efficiencyData.dabHistory; results.historyRestored = true; app.log 'Restored DAB history', 2 } ; if (efficiencyData.dabActivityLog) { app.atomicState.dabActivityLog = efficiencyData.dabActivityLog; results.activityLogRestored = true; app.log 'Restored DAB activity log', 2 } ; return results }
    def matchDeviceByRoomId(roomId) { return app.getChildDevices().find { device -> device.hasAttribute('percent-open') && device.currentValue('room-id') == roomId } }
    def matchDeviceByRoomName(roomName) { return app.getChildDevices().find { device -> device.hasAttribute('percent-open') && device.currentValue('room-name') == roomName } }
}
