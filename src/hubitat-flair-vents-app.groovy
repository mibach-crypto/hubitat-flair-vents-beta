/**
 * Hubitat Flair Vents Integration
 * Version 0.240 (Refactored)
 *
 * Copyright 2024 Jaime Botero.
 * All Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

import groovy.transform.Field
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.net.URLEncoder

// Include Hubitat Libraries (installed in "Libraries" section)
library "bot.flair.DabManager", "0.240.0"
library "bot.flair.DabUIManager", "0.240.0"

// --- Library Instances ---
def dabManager
def dabUIManager

definition(
    name: 'Flair Vents',
    namespace: 'bot.flair',
    author: 'Jaime Botero',
    description: 'Provides discovery and control capabilities for Flair Vent devices',
    category: 'Discovery',
    oauth: false,
    iconUrl: '',
    iconX2Url: '',
    iconX3Url: '',
    singleInstance: false
)

// =================================================================================
// Preferences and UI Page Definitions
// =================================================================================

preferences {
    page(name: 'mainPage')
    page(name: 'flairControlPanel')
    page(name: 'flairControlPanel2')
    page(name: 'quickControlsPage')
    page(name: 'diagnosticsPage')
    
    // DAB UI Pages (handled by DabUIManager)
    page(name: 'dabLiveDiagnosticsPage')
    page(name: 'efficiencyDataPage')
    page(name: 'dabChartPage')
    page(name: 'dabRatesTablePage')
    page(name: 'dabActivityLogPage')
    page(name: 'dabHistoryPage')
    page(name: 'dabProgressPage')
}

def mainPage() {
  def validation = validatePreferences()
  if (settings?.validateNow) {
    performValidationTest()
    app.updateSetting('validateNow', null)
  }

  dynamicPage(name: 'mainPage', title: 'Setup', install: validation.valid, uninstall: true) {
    section('Flair Control Panel') {
      href name: 'flairControlPanelLink', title: 'Open Flair Control Panel',
           description: 'Room-centric overview and quick adjustments',
           page: 'flairControlPanel2'
    }
    section('OAuth Setup') {
      input name: 'clientId', type: 'text', title: 'Client Id (OAuth 2.0)', required: true, submitOnChange: true
      input name: 'clientSecret', type: 'password', title: 'Client Secret OAuth 2.0', required: true, submitOnChange: true
      paragraph '<small><b>Obtain your client Id and secret from ' +
                "<a href='https://forms.gle/VohiQjWNv9CAP2ASA' target='_blank'>here</a></b></small>"

      if (validation.errors.clientId) {
        paragraph "<span style='color: red;'>${validation.errors.clientId}</span>"
      }
      
      if (validation.errors.clientSecret) {
        paragraph "<span style='color: red;'>${validation.errors.clientSecret}</span>"
      }

      if (settings?.clientId && settings?.clientSecret) {
        if (!state.flairAccessToken && !state.authInProgress) {
          state.authInProgress = true
          state.remove('authError')
          runIn(2, 'autoAuthenticate')
        }

        if (state.flairAccessToken && !state.authError) {
          paragraph "<span style='color: green;'>Authenticated successfully</span>"
        } else if (state.authError && !state.authInProgress) {
          section {
            paragraph "<span style='color: red;'>${state.authError}</span>"
            input name: 'retryAuth', type: 'button', title: 'Retry Authentication', submitOnChange: true
          }
        } else if (state.authInProgress) {
          paragraph "<span style='color: orange;'>Authenticating... Please wait.</span>"
        } else {
          paragraph "<span style='color: orange;'>Ready to authenticate...</span>"
        }
      }
    }

    if (state.flairAccessToken) {
      section('HVAC Status') {
        input name: 'refreshHvacNow', type: 'button', title: 'Refresh HVAC Status', submitOnChange: true
        if (settings?.refreshHvacNow) {
         try { dabManager.updateHvacStateFromDuctTemps() } catch (ignore) { }
          app.updateSetting('refreshHvacNow','')
        }
        def cur = atomicState?.thermostat1State?.mode ?: (atomicState?.hvacCurrentMode ?: 'idle')
        def last = atomicState?.hvacLastMode ?: '-'
        def ts = atomicState?.hvacLastChangeTs
        def tz = location?.timeZone ?: TimeZone.getTimeZone('UTC')
        def tsStr = ts ? new Date(ts as Long).format('yyyy-MM-dd HH:mm:ss', tz) : '-'
        paragraph "Current: <b>${cur}</b> | Last: <b>${last}</b> | Changed: <b>${tsStr}</b>"
      }
      
      section('\u26A1 Quick Controls') {
        href name: 'quickControlsLinkTop', title: '\u26A1 Open Quick Controls',
             description: 'Rapid per-room manual control, setpoints, and bulk actions',
             page: 'quickControlsPage'
      }
      section('Device Discovery') {
        input name: 'discoverDevices', type: 'button', title: 'Discover', submitOnChange: true
        input name: 'structureId', type: 'text', title: 'Home Id (SID)', required: false, submitOnChange: true
      }
      listDiscoveredDevices()

      section('<h2>Dynamic Airflow Balancing</h2>') {
        input name: 'dabEnabled', type: 'bool', title: 'Use Dynamic Airflow Balancing', defaultValue: false, submitOnChange: true
      }
      if (dabEnabled) {
        section('Thermostat & Globals') {
          input name: 'thermostat1', type: 'capability.thermostat', title: 'Optional: Thermostat for global setpoint', multiple: false, required: false
          input name: 'thermostat1TempUnit', type: 'enum', title: 'Units used by Thermostat', defaultValue: 2,
                options: [1: 'Celsius (°C)', 2: 'Fahrenheit (°F)']
          input name: 'thermostat1AdditionalStandardVents', type: 'number', title: 'Count of conventional Vents', defaultValue: 0, submitOnChange: true
          input name: 'thermostat1CloseInactiveRooms', type: 'bool', title: 'Close vents on inactive rooms', defaultValue: true, submitOnChange: true
          input name: 'fanOnlyOpenAllVents', type: 'bool', title: 'Fan-only: open all vents to 100%', defaultValue: true, submitOnChange: true
          input name: 'dabHistoryRetentionDays', type: 'number', title: 'DAB history retention (days)', defaultValue: 10, submitOnChange: true
        }
        
        section('DAB Data Smoothing (optional)') {
            input name: 'enableEwma', type: 'bool', title: 'Use EWMA smoothing for hourly averages', defaultValue: false, submitOnChange: true
            // ... other DAB settings
        }

        section {
            href name: 'efficiencyDataLink', title: 'Backup & Restore Efficiency Data',
                 description: 'Save/restore your learned room efficiency data.',
                 page: 'efficiencyDataPage'
            
            href name: 'dabChartLink', title: 'View Hourly DAB Rates',
                 description: 'Visualize 24-hour average airflow rates for each room',
                 page: 'dabChartPage'

            href name: 'dabRatesTableLink', title: 'View DAB Rates Table',
                 description: 'Tabular hourly DAB calculations for each room',
                 page: 'dabRatesTablePage'

            href name: 'dabActivityLogLink', title: 'View DAB Activity Log',
                 description: 'See recent HVAC mode transitions',
                 page: 'dabActivityLogPage'
        }
      }
    }
    // ... rest of non-DAB UI sections
  }
}

// =================================================================================
// Lifecycle Methods (installed, updated, initialize)
// =================================================================================

def updated() {
  log.debug "Hubitat Flair App updating"
  initialize()
}

def installed() {
  log.debug "Hubitat Flair App installed"
  initialize()
}

def uninstalled() {
  log.debug "Hubitat Flair App uninstalling"
  removeChildren()
  unschedule()
  unsubscribe()
}

def initialize() {
    // Instantiate the library managers
    dabManager = new DabManager(this)
    dabUIManager = new DabUIManager(this, dabManager)

    unsubscribe()
    dabManager.initializeDabHistory()
    initializeInstanceCaches()

    // Authentication
    if (settings?.clientId && settings?.clientSecret) {
        if (!state.flairAccessToken) {
            autoAuthenticate()
        } else {
            unschedule(login)
            runEvery1Hour(login)
        }
    }
  
    // Schedule DAB tasks if enabled
    if (isDabEnabled()) {
        runEvery1Minute('updateHvacStateFromDuctTempsWrapper')
        runEvery1Day('aggregateDailyDabStatsWrapper')
        
        if (settings?.enableRawCache) {
            runEvery1Minute('sampleRawDabDataWrapper')
            runEvery1Hour('pruneRawCacheWrapper')
        }

        if (settings?.thermostat1) {
            subscribe(settings.thermostat1, 'thermostatOperatingState', 'thermostat1ChangeStateHandlerWrapper')
            subscribe(settings.thermostat1, 'temperature', 'thermostat1ChangeTempWrapper')
        }
    } else {
        // Unschedule DAB tasks if disabled
        unschedule('updateHvacStateFromDuctTempsWrapper')
        unschedule('aggregateDailyDabStatsWrapper')
        unschedule('sampleRawDabDataWrapper')
        unschedule('pruneRawCacheWrapper')
    }
    // ... other non-DAB initializations
}

// =================================================================================
// DAB Manager Wrapper/Pass-Through Methods
// These methods are required for Hubitat's scheduler and UI to call the libraries.
// =================================================================================

// --- Logic Wrappers (for Schedulers/Subscriptions) ---
def updateHvacStateFromDuctTempsWrapper() { dabManager.updateHvacStateFromDuctTemps() }
def aggregateDailyDabStatsWrapper() { dabManager.aggregateDailyDabStats() }
def sampleRawDabDataWrapper() { dabManager.sampleRawDabData() }
def pruneRawCacheWrapper() { dabManager.pruneRawCache() }
def thermostat1ChangeStateHandlerWrapper(evt) { dabManager.thermostat1ChangeStateHandler(evt) }
def thermostat1ChangeTempWrapper(evt) { app.thermostat1ChangeTemp(evt) } // Assuming this stays in main app for now

def initializeRoomStates(String hvacMode) { dabManager.initializeRoomStates(hvacMode) }
def finalizeRoomStates(data) { dabManager.finalizeRoomStates(data) }
def reBalanceVents() { dabManager.reBalanceVents() }
def evaluateRebalancingVents() { dabManager.evaluateRebalancingVents() }
def handleClearDabData() { dabManager.handleClearDabData() }
def runDabDiagnostic() { dabUIManager.runDabDiagnostic() }

// --- UI Page Wrappers ---
def dabLiveDiagnosticsPage() { dabUIManager.dabLiveDiagnosticsPage() }
def efficiencyDataPage() { dabUIManager.efficiencyDataPage() }
def dabChartPage() { dabUIManager.dabChartPage() }
def dabRatesTablePage() { dabUIManager.dabRatesTablePage() }
def dabActivityLogPage() { dabUIManager.dabActivityLogPage() }
def dabHistoryPage() { dabUIManager.dabHistoryPage() }
def dabProgressPage() { dabUIManager.dabProgressPage() }
def dabDailySummaryPage() { dabUIManager.dabDailySummaryPage() }

// --- UI Builder Wrappers ---
def buildDabChart() { dabUIManager.buildDabChart() }
def buildDabRatesTable(Map data) { dabUIManager.buildDabRatesTable(data) }
def buildDabProgressTable(Map data) { dabUIManager.buildDabProgressTable(data) }
def buildDabDailySummaryTable() { dabUIManager.buildDabDailySummaryTable() }
def renderDabDiagnosticResults() { dabUIManager.renderDabDiagnosticResults() }

// Async builder wrappers used by UI pages
def buildDabRatesTableWrapper(Map data) { dabUIManager.buildDabRatesTable(data) }
def buildDabProgressTableWrapper(Map data) { dabUIManager.buildDabProgressTable(data) }

// =================================================================================
// Non-DAB Methods (Authentication, API, Device Management, etc.)
// These methods remain in the main app file.
// =================================================================================

@Field static final String BASE_URL = 'https://api.flair.co'
@Field static final String CONTENT_TYPE = 'application/json'
// ... all other non-DAB constants

// --- Authentication and API Communication ---
def login() { /* ... */ }
def authenticate(int retryCount = 0) { /* ... */ }
def handleAuthResponse(resp, data) { /* ... */ }
def getDataAsync(String uri, String callback, data = null, int retryCount = 0) { /* ... */ }
def patchDataAsync(String uri, String callback, body, data = null, int retryCount = 0) { /* ... */ }
def asyncHttpCallback(response, Map data) { /* ... */ }
// ... all other API and helper methods

// --- Device Discovery and Management ---
private void discover() { /* ... */ }
def handleDeviceList(resp, data) { /* ... */ }
def makeRealDevice(Map device) { /* ... */ }
def getDeviceData(device) { /* ... */ }
// ... all other device-related methods

// --- Non-DAB UI Pages ---
def flairControlPanel() { /* ... */ }
def flairControlPanel2() { /* ... */ }
def quickControlsPage() { /* ... */ }
def diagnosticsPage() { /* ... */ }

// ... include all other remaining methods from the original file that are NOT related to DAB.
