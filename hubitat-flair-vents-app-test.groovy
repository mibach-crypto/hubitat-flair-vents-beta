/**
 * Hubitat Flair Vents Integration
 * Version 0.240
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

// ------------------------------
// Constants and Configuration
// ------------------------------

@Field DabManager dabManager
@Field DabUIManager dabUIManager

// Base URL for Flair API endpoints.
@Field static final String BASE_URL = 'https://api.flair.co'

// Instance-based cache durations (reduced from 60s to 30s for better responsiveness)
@Field static final Long ROOM_CACHE_DURATION_MS = 30000 // 30 second cache duration
@Field static final Long DEVICE_CACHE_DURATION_MS = 30000 // 30 second cache duration for device readings
@Field static final Integer MAX_CACHE_SIZE = 50 // Maximum cache entries per instance
@Field static final Integer DEFAULT_HISTORY_RETENTION_DAYS = 10 // Default days to retain DAB history
@Field static final Integer DAILY_SUMMARY_PAGE_SIZE = 30 // Entries per page for daily summary

// Content-Type header for API requests.
@Field static final String CONTENT_TYPE = 'application/json'

// HVAC mode constants.
@Field static final String COOLING = 'cooling'
@Field static final String HEATING = 'heating'

// Pending HVAC mode values returned by the thermostat.
@Field static final String PENDING_COOL = 'pending cool'
@Field static final String PENDING_HEAT = 'pending heat'

// Delay (in milliseconds) before re-reading temperature after an HVAC event.
@Field static final Integer TEMP_READINGS_DELAY_MS = 30000  // 30 seconds

// Minimum and maximum vent open percentages (in %).
@Field static final BigDecimal MIN_PERCENTAGE_OPEN = 0.0
@Field static final BigDecimal MAX_PERCENTAGE_OPEN = 100.0

// Threshold (in °C) used to trigger a pre-adjustment of vent settings before the setpoint is reached.
@Field static final BigDecimal VENT_PRE_ADJUST_THRESHOLD = 0.2

// HVAC timing constants.
@Field static final BigDecimal MAX_MINUTES_TO_SETPOINT = 60       // Maximum minutes to reach setpoint.
@Field static final BigDecimal MIN_MINUTES_TO_SETPOINT = 1        // Minimum minutes required to compute temperature change rate.
// Temperature offset (in °C) applied to thermostat setpoints.
@Field static final BigDecimal SETPOINT_OFFSET = 0.7

// Acceptable temperature change rate limits (in °C per minute).
@Field static final BigDecimal MAX_TEMP_CHANGE_RATE = 1.5
@Field static final BigDecimal MIN_TEMP_CHANGE_RATE = 0.001

// Temperature sensor accuracy and noise filtering
@Field static final BigDecimal TEMP_SENSOR_ACCURACY = 0.5  // ±0.5°C typical sensor accuracy
@Field static final BigDecimal MIN_DETECTABLE_TEMP_CHANGE = 0.1  // Minimum change to consider real
@Field static final Integer MIN_RUNTIME_FOR_RATE_CALC = 5  // Minimum minutes before calculating rate

// Minimum combined vent airflow percentage across all vents (to ensure proper HVAC operation).
@Field static final BigDecimal MIN_COMBINED_VENT_FLOW = 30.0

// INCREMENT_PERCENTAGE is used as a base multiplier when incrementally increasing vent open percentages
// during airflow adjustments. For example, if the computed proportion for a vent is 0.5,
// then the vent’s open percentage will be increased by 1.5 * 0.5 = 0.75% in that iteration.
// This increment is applied repeatedly until the total combined airflow meets the minimum target.
@Field static final BigDecimal INCREMENT_PERCENTAGE = 1.5

// Maximum number of standard (non-Flair) vents allowed.
@Field static final Integer MAX_STANDARD_VENTS = 15

// Maximum iterations for the while-loop when adjusting vent openings.
@Field static final Integer MAX_ITERATIONS = 500
// Quick controls verification timing
@Field static final Integer VENT_VERIFY_DELAY_MS = 5000
@Field static final Integer MAX_VENT_VERIFY_ATTEMPTS = 3

// HTTP timeout for API requests (in seconds).
@Field static final Integer HTTP_TIMEOUT_SECS = 5

// Default opening percentage for standard (non-Flair) vents (in %).
@Field static final Integer STANDARD_VENT_DEFAULT_OPEN = 50

// Temperature tolerance for rebalancing vent operations (in °C).
@Field static final BigDecimal REBALANCING_TOLERANCE = 0.5

// Temperature boundary adjustment for airflow calculations (in °C).
@Field static final BigDecimal TEMP_BOUNDARY_ADJUSTMENT = 0.1

// Thermostat hysteresis to prevent cycling (in °C).
@Field static final BigDecimal THERMOSTAT_HYSTERESIS = 0.6  // ~1°F

// Minimum average difference between duct and room temperature (in °C)
// required to determine that the HVAC system is actively heating or cooling.
@Field static final BigDecimal DUCT_TEMP_DIFF_THRESHOLD = 0.5

// Polling intervals based on HVAC state (in minutes).
@Field static final Integer POLLING_INTERVAL_ACTIVE = 3     // When HVAC is running
@Field static final Integer POLLING_INTERVAL_IDLE = 10      // When HVAC is idle

// Delay before initializing room states after certain events (in milliseconds).
@Field static final Integer INITIALIZATION_DELAY_MS = 3000

// Delay after a thermostat state change before reinitializing (in milliseconds).
@Field static final Integer POST_STATE_CHANGE_DELAY_MS = 1000

// Simple API throttling delay to prevent overwhelming the Flair API (in milliseconds).
@Field static final Integer API_CALL_DELAY_MS = 1000 * 3

// Maximum concurrent HTTP requests to prevent API overload.
@Field static final Integer MAX_CONCURRENT_REQUESTS = 8

// Maximum number of retry attempts for async API calls.
@Field static final Integer MAX_API_RETRY_ATTEMPTS = 5

// Consecutive failures per URI before resetting API connection.
@Field static final Integer API_FAILURE_THRESHOLD = 3

// ------------------------------
// End Constants
// ------------------------------

// Adaptive DAB seeding defaults (for abrupt condition changes)
@Field static final Boolean ADAPTIVE_BOOST_ENABLED = true
@Field static final Integer ADAPTIVE_LOOKBACK_PERIODS = 3
@Field static final BigDecimal ADAPTIVE_THRESHOLD_PERCENT = 25.0
@Field static final BigDecimal ADAPTIVE_BOOST_PERCENT = 12.5
@Field static final BigDecimal ADAPTIVE_MAX_BOOST_PERCENT = 25.0

// Raw data cache defaults and fallbacks
@Field static final Integer RAW_CACHE_DEFAULT_HOURS = 24
@Field static final Integer RAW_CACHE_MAX_ENTRIES = 20000
@Field static final BigDecimal DEFAULT_COOLING_SETPOINT_C = 24.0
@Field static final BigDecimal DEFAULT_HEATING_SETPOINT_C = 20.0

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

preferences {
  page(name: 'mainPage')
  page(name: 'flairControlPanel')
  page(name: 'dabLiveDiagnosticsPage')
  page(name: 'flairControlPanel2')
  page(name: 'efficiencyDataPage')
  page(name: 'dabChartPage')
  page(name: 'dabRatesTablePage')
  page(name: 'dabActivityLogPage')
  page(name: 'dabHistoryPage')
  page(name: 'dabProgressPage')
  page(name: 'quickControlsPage')
  page(name: 'diagnosticsPage')
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
          state.remove('authError')  // Clear any previous error when starting new auth
          runIn(2, 'autoAuthenticate')
        }

        if (state.flairAccessToken && !state.authError) {
          paragraph "<span style='color: green;'>Authenticated successfully</span>"
        } else if (state.authError && !state.authInProgress) {
          section {
            paragraph "<span style='color: red;'>${state.authError}</span>"
            input name: 'retryAuth', type: 'button', title: 'Retry Authentication', submitOnChange: true
            paragraph "<small>If authentication continues to fail, verify your credentials are correct and try again.</small>"
          }
        } else if (state.authInProgress) {
 
          paragraph "<span style='color: orange;'>Authenticating... Please wait.</span>"
          paragraph "<small>This may take 10-15 seconds. The page will refresh automatically when complete.</small>"
        } else {
          paragraph "<span style='color: orange;'>Ready to authenticate...</span>"
        }

      }

    }

    if (state.flairAccessToken) {
      section('HVAC Status') {
        input name: 'refreshHvacNow', type: 'button', title: 'Refresh HVAC Status', submitOnChange: true
        if (settings?.refreshHvacNow) {
         
         try { updateHvacStateFromDuctTemps() } catch (ignore) { }
          app.updateSetting('refreshHvacNow','')
        }
        def cur = atomicState?.thermostat1State?.mode ?: (atomicState?.hvacCurrentMode ?: 'idle')
        def last = atomicState?.hvacLastMode ?: '-'
        def ts = atomicState?.hvacLastChangeTs
        def tz = location?.timeZone ?: TimeZone.getTimeZone('UTC')
        def tsStr = ts ? new Date(ts as Long).format('yyyy-MM-dd HH:mm:ss', tz) : '-'
  
       paragraph "Current: <b>${cur}</b> | Last: <b>${last}</b> | Changed: <b>${tsStr}</b>"
      }
      // Fast access to Quick Controls at the top
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

      // Polling intervals (register inputs early for validators)
      section('Polling Intervals') {
        input name: 'pollingIntervalActive', type: 'number', title: 'Active HVAC polling interval (minutes)', defaultValue: 3, submitOnChange: true
        input name: 'pollingIntervalIdle', type: 'number', title: 'Idle HVAC polling interval (minutes)', defaultValue: 10, submitOnChange: true
      }

      if (state.ventOpenDiscrepancies) {
        section('Vent Synchronization Issues') {
          state.ventOpenDiscrepancies.each { id, info ->
            paragraph "<span style='color: red;'>${info.name ?: id} expected ${info.target}% but reported ${info.actual}%</span>"
          }
        }
      }

      section('<h2>Dynamic Airflow Balancing</h2>') {
        input name: 'dabEnabled', type: 'bool', title: 'Use Dynamic Airflow Balancing', defaultValue: false, submitOnChange: true
      }
      if (dabEnabled) {
        section('Thermostat & Globals') {
          input name: 'thermostat1', type: 'capability.thermostat', title: 'Optional: Thermostat for global setpoint', multiple: false, required: false
          input name: 'thermostat1TempUnit', type: 'enum', title: 'Units used by Thermostat', defaultValue: 2,
                options: [1: 'Celsius (°C)', 2: 'Fahrenheit (°F)']
          input name: 'thermostat1AdditionalStandardVents', type: 'number', title: 'Count of conventional Vents', defaultValue: 0, submitOnChange: true
          paragraph '<small>Enter the total number of standard (non-Flair) adjustable vents in the home associated ' +
       
                     'with the chosen thermostat, excluding Flair vents. This ensures the combined airflow does not drop ' +
                    'below a specified percent to prevent HVAC issues.</small>'
          input name: 'thermostat1CloseInactiveRooms', type: 'bool', title: 'Close vents on inactive rooms', defaultValue: true, submitOnChange: true
          input name: 'fanOnlyOpenAllVents', type: 'bool', title: 'Fan-only: open all vents to 100%', defaultValue: true, submitOnChange: true
          input name: 'dabHistoryRetentionDays', type: 'number', 
                 title: 'DAB history retention (days)', defaultValue: DEFAULT_HISTORY_RETENTION_DAYS, submitOnChange: true

          if (settings.dabHistoryRetentionDays && settings.dabHistoryRetentionDays < 1) {
            app.updateSetting('dabHistoryRetentionDays', 1)
          }
          // Mirror to atomicState for CI-safe access in methods
          try { atomicState.dabHistoryRetentionDays = (settings?.dabHistoryRetentionDays ?: DEFAULT_HISTORY_RETENTION_DAYS) as Integer } catch (e) { log(3, 'App', "Error mirroring dabHistoryRetentionDays: ${e.message}") }

          if (settings.thermostat1AdditionalStandardVents < 0) {
            app.updateSetting('thermostat1AdditionalStandardVents', 0)
          } else if (settings.thermostat1AdditionalStandardVents > MAX_STANDARD_VENTS) {
            app.updateSetting('thermostat1AdditionalStandardVents', MAX_STANDARD_VENTS)
          }
          }
          
          // Quick Safety Limits
          section('Quick Safety Limits') {
            input name: 'allowFullClose', type: 'bool', 
                   title: 'Allow vents to fully close (0%)', defaultValue: false, submitOnChange: true
            input name: 'minVentFloorPercent', type: 'number', title: 'Minimum vent opening floor (%)', defaultValue: 10, submitOnChange: true
          }

          // Night override (simple schedule)
          section('Night Override (per-room)') {
            input name: 'nightOverrideEnable', type: 'bool', title: 'Enable night override', defaultValue: false, submitOnChange: true
   
            input name: 'nightOverrideRooms', type: 'capability.switchLevel', title: 'Rooms (vents) to override', multiple: true, required: false, submitOnChange: true
            input name: 'nightOverridePercent', type: 'number', title: 'Override vent percent (%)', defaultValue: 100, submitOnChange: true
            input name: 'nightOverrideStart', type: 'time', title: 'Start time', required: false, submitOnChange: true
            input name: 'nightOverrideEnd', type: 'time', title: 'End time', required: false, submitOnChange: true
     
            input name: 'applyNightOverrideNow', type: 'button', title: 'Apply Now', submitOnChange: true
            input name: 'clearManualOverrides', type: 'button', title: 'Clear Manual Overrides', submitOnChange: true
            if (settings?.applyNightOverrideNow) { activateNightOverride(); app.updateSetting('applyNightOverrideNow','') }
            if (settings?.clearManualOverrides) { clearAllManualOverrides(); app.updateSetting('clearManualOverrides','') }
          }

      // Dashboard tiles
      section('Dashboard Tiles') {
        input name: 'enableDashboardTiles', type: 'bool', title: 'Enable vent dashboard tiles', defaultValue: false, submitOnChange: true
        input name: 'syncVentTiles', type: 'button', title: 'Create/Sync Tiles', submitOnChange: true
        if (settings?.syncVentTiles) {
          try { syncVentTiles() } catch (e) { logError "Tile sync failed: ${e?.message}" } finally { app.updateSetting('syncVentTiles','') }
        
        }
      }

          // Raw Data Cache (for diagnostics and optional DAB calculations)
          section('Raw Data Cache') {
            input name: 'enableRawCache', type: 'bool', title: 'Enable raw data cache (24h)', defaultValue: true, submitOnChange: true
            input name: 'rawDataRetentionHours', type: 'number', title: 'Raw data retention (hours)', defaultValue: RAW_CACHE_DEFAULT_HOURS, submitOnChange: true
           
           input name: 'useCachedRawForDab', type: 'bool', title: 'Calculate DAB using cached raw data', defaultValue: false, submitOnChange: true
          }

  // Data smoothing and robustness (optional)
          section('DAB Data Smoothing (optional)') {
            input name: 'enableEwma', type: 'bool', title: 'Use EWMA smoothing for hourly averages', defaultValue: false, submitOnChange: true
            input name: 'ewmaHalfLifeDays', type: 'number', title: 'EWMA half-life (days per hour-slot)', defaultValue: 3, submitOnChange: true
            input name: 'enableOutlierRejection', type: 'bool', title: 'Robust outlier handling (MAD)', defaultValue: true, submitOnChange: true
            input name: 'outlierThresholdMad', type: 'number', title: 'Outlier threshold (k x MAD)', defaultValue: 3, submitOnChange: true
            input name: 'outlierMode', type: 'enum', title: 'Outlier mode', options: ['reject':'Reject', 'clip':'Clip to bound'], defaultValue: 'clip', submitOnChange: true
            // Mirror to atomicState for CI-safe access
  
           try {
              atomicState.enableEwma = settings?.enableEwma == true
              atomicState.ewmaHalfLifeDays = (settings?.ewmaHalfLifeDays ?: 3) as Integer
              atomicState.enableOutlierRejection = settings?.enableOutlierRejection != false
              atomicState.outlierThresholdMad = (settings?.outlierThresholdMad ?: 3) as Integer
             
              atomicState.outlierMode = settings?.outlierMode ?: 'clip'
              atomicState.carryForwardLastHour = settings?.carryForwardLastHour != false
      
              atomicState.enableAdaptiveBoost = settings?.enableAdaptiveBoost != false
              atomicState.adaptiveLookbackPeriods = (settings?.adaptiveLookbackPeriods ?: 3) as Integer
              atomicState.adaptiveThresholdPercent = (settings?.adaptiveThresholdPercent ?: 25) as BigDecimal
              atomicState.adaptiveBoostPercent = (settings?.adaptiveBoostPercent ?: 12.5) as BigDecimal
              atomicState.adaptiveMaxBoostPercent = (settings?.adaptiveMaxBoostPercent ?: 25) as BigDecimal
        
            } catch (e) { log(3, 'App', "Error mirroring DAB settings: ${e.message}") }
          }
          
          // Efficiency Data Management Link
          section {
            href name: 'efficiencyDataLink', title: 'Backup & Restore Efficiency Data',
                 description: 'Save your learned room efficiency data to restore after app updates',
                 page: 'efficiencyDataPage'

 
            // Show current status summary
            def vents = getChildDevices().findAll { it.hasAttribute('percent-open') }
      if (vents.size() > 0) {
              def roomsWithData = vents.findAll {
                (it.currentValue('room-cooling-rate') ?: 0) > 0 || (it.currentValue('room-heating-rate') ?: 0) > 0
              }
              paragraph "<small><b>Current Status:</b> ${roomsWithData.size()} of ${vents.size()} rooms have learned efficiency data</small>"
            }
          }
// Hourly DAB Chart Link
          section {
            href name: 'dabChartLink', title: 'View Hourly DAB Rates',
  
                 description: 'Visualize 24-hour average airflow rates for each room',
                 page: 'dabChartPage'
          }
// Hourly DAB Rates Table Link
          section {
            href name: 'dabRatesTableLink', title: 'View DAB Rates Table',
              
               description: 'Tabular hourly DAB calculations for each room',
                 page: 'dabRatesTablePage'
          }
// DAB Progress Page Link
          section {
            href name: 'dabProgressLink', title: 'View DAB Progress',
                 description: 'Track DAB progress by date and hour',
     
                 page: 'dabProgressPage'
          }
// Daily DAB Summary Link
          section {
            href name: 'dabDailySummaryLink', title: 'View Daily DAB Summary',
                 description: 'Daily airflow averages per room and mode',
                 page: 'dabDailySummaryPage'
 
          }
// DAB Activity Log Link
          section {
            href name: 'dabActivityLogLink', title: 'View DAB Activity Log',
                 description: 'See recent HVAC mode transitions',
                 page: 'dabActivityLogPage'
          }
// DAB History Export
    
          section {
            input name: 'dabHistoryFormat', type: 'enum', title: 'Export Format',
                  options: ['json': 'JSON', 'csv': 'CSV'], defaultValue: 'json',
                  submitOnChange: true
            input name: 'exportDabHistory', type: 'button',
               
               title: 'Export DAB History', submitOnChange: true
            if (state.dabHistoryExportStatus) {
              paragraph state.dabHistoryExportStatus
            }
      if (state.dabHistoryExportData) {
              paragraph "<textarea rows='8' cols='80' readonly>" +
                        
                        "${state.dabHistoryExportData}" + "</textarea>"
            }
          }
        }
// Run integrity check / reindex
        section {
          input name: 'runDabHistoryCheck', type: 'button', title: 'Reindex DAB History Now', submitOnChange: true
          if (settings?.runDabHistoryCheck) {
            def result = reindexDabHistory()
      
            state.dabHistoryCheckStatus = "\u2713 Reindexed DAB history: ${result.entries} entries across ${result.rooms} rooms."
 app.updateSetting('runDabHistoryCheck', '')
          }
      if (state.dabHistoryCheckStatus) {
            paragraph state.dabHistoryCheckStatus
          }
// Quick Controls Link (avoid nested section calls in CI)
          href name: 'quickControlsLink', title: '\u26A1 Quick Controls',
               description: 'Rapid per-room manual control and bulk actions',
           
               page: 'quickControlsPage'
        }
// Only show vents in DAB section, not pucks
      def vents = getChildDevices().findAll { it.hasAttribute('percent-open') }
    section('Thermostat Mapping') {
        for (child in vents) {
          input name: "vent${child.getId()}Thermostat", type: 'capability.temperatureMeasurement', title: "Optional: Temperature sensor for ${child.getLabel()}", multiple: false, required: false
        }
      }
    section('Vent Options') {
     
       input name: 'ventGranularity', type: 'enum', title: 'Vent Adjustment Granularity (in %)',
              options: ['5':'5%', '10':'10%', '25':'25%', '50':'50%', '100':'100%'],
              defaultValue: '5', required: true, submitOnChange: true
        paragraph '<small>Select how granular the vent adjustments should be. For example, if you choose 50%, vents ' +
                  'will only adjust to 0%, 50%, or 100%. Lower percentages allow for finer control, but may ' +
                  'result in more frequent adjustments (which could affect battery-powered vents).</small>'
      }
// Optional per-vent weighting within a room (to bias distribution)
      section('Per-Vent Weighting (optional)') {
        vents.each { v ->
          input name: "vent${v.getId()}Weight", type: 'number', title: "Weight for ${v.getLabel()} (default 1.0)", defaultValue: 1.0, submitOnChange: true
     
        }
        paragraph '<small>When a room has multiple vents, the system calculates a room-level target and then vents are adjusted individually. ' +
                  'Weights bias openings within a room: higher weight => relatively more opening. Leave at 1.0 for equal weighting.</small>'
      }
      if (state.ventPatchDiscrepancies) {
        section('Vent Sync Issues') {
          state.ventPatchDiscrepancies.each { id, info ->
            paragraph "<span style='color: red;'>${info.name ?: id}: requested ${info.requested}% but reported ${info.reported}%</span>"
          }
        }
      }
    } else {
    
       section {
        paragraph 'Device discovery button is hidden until authorization is completed.'
 }
// CI-safe: register commonly read inputs so initialize() can read them without strict-mode violations
      section('DAB Setup (Registration Only)') {
        input name: 'dabEnabled', type: 'bool', title: 'Use Dynamic Airflow Balancing', defaultValue: false, submitOnChange: false
        input name: 'enableDashboardTiles', type: 'bool', title: 'Enable vent dashboard tiles', defaultValue: false, submitOnChange: false
        input name: 'nightOverrideEnable', type: 'bool', title: 'Enable night override', defaultValue: false, submitOnChange: false
      }
    }
    section('Validation') {
      input name: 'validateNow', type: 'button', title: 'Validate Settings', submitOnChange: true
      if (state.lastValidationResult?.message) {
        def color = state.lastValidationResult.success ? 'green' : 'red'
        paragraph "<span style='color: ${color};'>${state.lastValidationResult.message}</span>"
      }
    }
    section('Debug Options') {
      input name: 'debugLevel', type: 'enum', title: 'Choose debug level', defaultValue: 0,
            options: [0: 'None', 1: 'Level 1 (All)', 2: 'Level 2', 3: 'Level 3'], submitOnChange: true
      input name: 'failFastFinalization', type: 'bool', title: 'Enable Fail Fast Mode for Finalization', defaultValue: false, submitOnChange: true
      href name: 'diagnosticsLink', title: 'View Diagnostics',
           description: 'Troubleshoot vent data and logs', page: 'diagnosticsPage'
      href name: 'dabLiveDiagnosticsLink', title: 'DAB Live Diagnostics',
           description: 'Run a live simulation of the DAB calculation',
           page: 'dabLiveDiagnosticsPage'
    }
  }
}
// Simple, Hubitat-compatible control panel (no JS required)
def flairControlPanel() {
  dynamicPage(name: 'flairControlPanel', title: 'Flair Control Panel', install: false, uninstall: false) {
    def vents = getChildDevices()?.findAll { it.hasAttribute('percent-open') } ?: []
    if (!vents) {
      section {
        paragraph 'No vents available. Run discovery from the main page.'
      }
      return
    }

    // Build 1 representative device per room
    def byRoom = [:]
    vents.each { v ->
      def rid = v.currentValue('room-id') ?: v.getDeviceNetworkId()
      if (!byRoom.containsKey(rid)) { byRoom[rid] = [] }
      byRoom[rid] << v
    }

    // Actions (apply immediately when buttons are pressed)
    byRoom.each { roomId, list ->
      
       def v = list[0]
      def roomName = v.currentValue('room-name') ?: v.getLabel()
      def tempC = v.currentValue('room-current-temperature-c')
      def setpC = v.currentValue('room-set-point-c')
      def active = (v.currentValue('room-active') ?: 'false')
      def toF = { c -> c != null ? (((c as BigDecimal) * 9/5) + 32) : null }
      def fmt1 = { x -> x != null ? (((x as BigDecimal) * 10).round() / 10) : '-' }
      def tempF = fmt1(toF(tempC))
      def setpF = fmt1(toF(setpC))

      section("${roomName}") {
        paragraph "Temp: <b>${tempF}&deg;F</b> | Setpoint: <b>${setpF}&deg;F</b> | Active: <b>${active}</b>"
        // Per-room setpoint nudge buttons
        input name: "cp_room_${roomId}_sp_down", type: 'button', title: 'Setpoint -1 F', submitOnChange: true
        input name: "cp_room_${roomId}_sp_up", type: 'button', title: 'Setpoint +1 F', submitOnChange: true
  
       input name: "cp_room_${roomId}_active", type: 'enum', title: 'Set room active', options: ['true','false'], submitOnChange: true

        // Render vents
        list.each { dv ->
          def lvl = (dv.currentValue('percent-open') ?: dv.currentValue('level') ?: 0)
          paragraph "- ${dv.getLabel()}: ${lvl}%"
        }

        // Handle presses inline
        if (settings?."cp_room_${roomId}_sp_up" ) {
 
          try {
            BigDecimal curF = setpF as BigDecimal
            patchRoomSetPoint(v, (curF + 1) as BigDecimal)
          } catch (e) { log(3, 'App', "Error adjusting setpoint: ${e.message}") }
          app.updateSetting("cp_room_${roomId}_sp_up", '')
        }
        if (settings?."cp_room_${roomId}_sp_down" ) {
          try {
 
           BigDecimal curF = setpF as BigDecimal
            patchRoomSetPoint(v, (curF - 1) as BigDecimal)
          } catch (e) { log(3, 'App', "Error adjusting setpoint: ${e.message}") }
          app.updateSetting("cp_room_${roomId}_sp_down", '')
        }
        def sel = settings?."cp_room_${roomId}_active"
        if (sel != null && sel != "") {
       
           try { patchRoom(v, sel) } catch (e) { log(3, 'App', "Error setting room active: ${e.message}") }
          app.updateSetting("cp_room_${roomId}_active", '')
        }
      }
    }

    section {
      href name: 'backToMain', title: 'Back to Main', description: 'Return to main settings', page: 'mainPage'
    }
  }
}

def diagnosticsPage() {
  dynamicPage(name: 'diagnosticsPage', title: 'Diagnostics') {
    section('Cached Device Data') {
      def cache = state."instanceCache_${getInstanceId()}_deviceCache"
      
       if (cache) {
        cache.each { id, data ->
          paragraph "<b>${id}</b>: ${data}"
        }
      } else {
        paragraph 'No cached device data.'
 }
      input name: 'resetCache', type: 'button', title: 'Reset Cache'
    }
    section('Recent Error Logs') {
      def logs = state.recentErrors ?: []
      if (logs) {
        logs.reverse().each { paragraph it }
      } else {
        paragraph 'No recent errors.'
 }
    }
    section('Decision Trace (last 60)') {
      def decisions = state.recentVentDecisions ?: []
      if (decisions) {
        decisions.each { d ->
          paragraph "<code>${d.ts} [${d.mode}] ${d.stage} - ${d.room}: T=${d.temp}, SP=${d.setpoint}, rate=${d.rate}, atSP=${d.atSp}, override=${d.override}, pct=${d.pct}%</code>"
        }
      } else {
        paragraph 'No recent decisions recorded.'
 }
    }
    section('Health Check') {
      if (state.healthCheckResults) {
        paragraph state.healthCheckResults.results.join('<br/>')
        paragraph "<small>Last run: ${state.healthCheckResults.timestamp}</small>"
      } else {
        paragraph 'No health check run yet.'
 }
      input name: 'runHealthCheck', type: 'button', title: 'Run Health Check'
    }
    section('Diagnostics Export (JSON)') {
      input name: 'exportDiagnosticsNow', type: 'button', title: 'Export Snapshot', submitOnChange: true
      if (settings?.exportDiagnosticsNow) {
        try { state.diagnosticsJson = buildDiagnosticsJson() } catch (e) { state.diagnosticsJson = '{}'; log(3, 'App', "Error building diagnostics JSON: ${e.message}") }
        app.updateSetting('exportDiagnosticsNow','')
      }
      paragraph 'Copy JSON from app logs (next release will render textarea safely).'
 }
    section('Raw Data Cache') {
      def entries = (atomicState?.rawDabSamplesEntries ?: [])
      paragraph "Raw cache enabled: ${settings?.enableRawCache == true}"
      paragraph "Entries: ${entries.size()} | Retention (h): ${settings?.rawDataRetentionHours ?: RAW_CACHE_DEFAULT_HOURS}"
      input name: 'exportRawCacheNow', type: 'button', title: 'Export Raw Cache (JSON)', submitOnChange: true
      input name: 'clearRawCacheNow', type: 'button', title: 'Clear Raw Cache', submitOnChange: true
      if (settings?.exportRawCacheNow) {
        try { state.rawCacheJson = buildRawCacheJson() } catch (e) { state.rawCacheJson = '{}'; log(3, 'App', "Error building raw cache JSON: ${e.message}") }
        app.updateSetting('exportRawCacheNow','')
      }
      if (settings?.clearRawCacheNow) {
        clearRawCache()
        app.updateSetting('clearRawCacheNow','')
      }
      paragraph 'Exported data is stored in state and shown in logs.'
 }
    
    section('Actions') {
      input name: 'reauthenticate', type: 'button', title: 'Re-Authenticate'
      input name: 'resyncVents', type: 'button', title: 'Re-Sync Vents'
    }
  }
}
// New, styled control panel compatible with Hubitat pages (no JS required)
def flairControlPanel2() {
  dynamicPage(name: 'flairControlPanel2', title: 'Flair Control Panel', install: false, uninstall: false) {
    section {
      paragraph """
        <style>
          .flair-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(320px,1fr));gap:12px}
         
           .room-card{background:#f9f9f9;border-radius:8px;box-shadow:0 1px 3px rgba(0,0,0,.12);padding:12px;border-left:5px solid #9ca3af}
          .room-card.cooling{border-left-color:#3b82f6}
          .room-card.heating{border-left-color:#f59e0b}
          .room-head{display:flex;justify-content:space-between;align-items:center;margin-bottom:8px}
          .room-name{font-weight:600}
          .room-meta{font-size:12px;color:#374151}
          .vent-item{font-size:12px;color:#111}
        </style>
      """
    }

def vents = getChildDevices()?.findAll { it.hasAttribute('percent-open') } ?: []
    if (!vents) {
  
       section { paragraph 'No vents available. Run discovery from the main page.'
 }
      return
    }

def rooms = [:]
    vents.each { dv ->
      def rid = dv.currentValue('room-id') ?: dv.getDeviceNetworkId()
      (rooms[rid] = (rooms[rid] ?: []) ) << dv
    }
    rooms.each { roomId, list ->
      def v = list[0]
      def roomName = v.currentValue('room-name') ?: v.getLabel()
      def tempC = v.currentValue('room-current-temperature-c')
      def setpC = v.currentValue('room-set-point-c')
      
       def active = (v.currentValue('room-active') ?: 'false')
      def toF = { c -> c != null ? (((c as BigDecimal) * 9/5) + 32) : null }
      def fmt1 = { x -> x != null ? (((x as BigDecimal) * 10).round() / 10) : '-' }
      def tempF = fmt1(toF(tempC))
      def setpF = fmt1(toF(setpC))
      def hvacMode = (atomicState?.thermostat1State?.mode ?: atomicState?.hvacCurrentMode ?: 'idle')

      section(roomName) {
        paragraph """
          <div class='flair-grid'>
            <div class='room-card ${hvacMode}'>
              <div class='room-head'>
          
           <div class='room-name'>${roomName}</div>
                <div class='room-meta'>Active: <b>${active}</b></div>
              </div>
              <div class='room-meta'>Temp: <b>${tempF}&deg;F</b> &nbsp;|&nbsp; Setpoint: <b>${setpF}&deg;F</b></div>
            </div>
          </div>
        """
        input name: "cp2_room_${roomId}_sp_down", type: 'button', title: 'Setpoint -1 F', submitOnChange: true
        input name: "cp2_room_${roomId}_sp_up", type: 'button', title: 'Setpoint +1 F', submitOnChange: true
        input name: "cp2_room_${roomId}_active", type: 'enum', title: 'Set room active', options: ['true','false'], submitOnChange: true

        list.each { dv ->
          def lvl = (dv.currentValue('percent-open') ?: dv.currentValue('level') ?: 0)
          paragraph "<div class='vent-item'>&ndash; ${dv.getLabel()}: ${lvl}%</div>"
        }
        if (settings?."cp2_room_${roomId}_sp_up") {
          try { if (setpF != '-') { patchRoomSetPoint(v, ((setpF as BigDecimal) + 1) as BigDecimal) } } catch (e) { log(3, 'App', "Error adjusting setpoint: ${e.message}") }
          app.updateSetting("cp2_room_${roomId}_sp_up", '')
        }
        if (settings?."cp2_room_${roomId}_sp_down") {
          try { if (setpF != '-') { patchRoomSetPoint(v, ((setpF as BigDecimal) - 1) as BigDecimal) } } catch (e) { log(3, 'App', "Error adjusting setpoint: ${e.message}") }
          app.updateSetting("cp2_room_${roomId}_sp_down", '')
        }
        def sel = settings?."cp2_room_${roomId}_active"
        if (sel != null && sel != "") {
          try { patchRoom(v, sel) } catch (e) { log(3, 'App', "Error setting room active: ${e.message}") }
          app.updateSetting("cp2_room_${roomId}_active", '')
        }
      }
    }
    section { href name: 'backToMain', title: 'Back to Main', description: 'Return to main settings', page: 'mainPage' }
  }
}

// Backend helper for future client use (JSON string)
String getRoomDataForPanel() {
  def out = []
  def vents = getChildDevices()?.findAll { it.hasAttribute('percent-open') } ?: []
  def rooms = [:]
  vents.each { dv ->
    def rid = dv.currentValue('room-id') ?: dv.getDeviceNetworkId()
    (rooms[rid] = (rooms[rid] ?: []) ) << dv
  }
  rooms.each { rid, list ->
    def v = list[0]
    def toF = { c -> c != null ? (((c as BigDecimal) * 9/5) + 32) : null }
    out << [
      id: rid,
      name: v.currentValue('room-name') ?: v.getLabel(),
      tempF: toF(v.currentValue('room-current-temperature-c')),
      setpointF: toF(v.currentValue('room-set-point-c')),
      active: (v.currentValue('room-active') ?: 'false'),
      vents: list.collect { dv -> [name: dv.getLabel(), level: (dv.currentValue('percent-open') ?: dv.currentValue('level') ?: 0)] }
    ]
  }
  try { return groovy.json.JsonOutput.toJson(out) } catch (e) { log(3, 'App', "Error building room panel JSON: ${e.message}"); return '[]' }
}

def performHealthCheck() {
  def results = []
  results << (state.flairAccessToken ? 'Auth token present' : 'Auth token missing')
  try {
    httpGet([
      uri: "${BASE_URL}/api/structures",
      headers: [Authorization: "Bearer ${state.flairAccessToken}"],
      timeout: HTTP_TIMEOUT_SECS,
      contentType: CONTENT_TYPE
    ]) { resp ->
      results << "API reachable: HTTP ${resp.status}"
    }
  } catch (e) {
    results << "API error: ${e.message}"
  }
  def ventCount = getChildDevices().findAll { it.hasAttribute('percent-open') }.size()
 
   results << "Vents discovered: ${ventCount}"
  state.healthCheckResults = [
    timestamp: new Date().format('yyyy-MM-dd HH:mm:ss', location.timeZone ?: TimeZone.getTimeZone('UTC')),
    results: results
  ]
}

def resetCaches() {
  def instanceId = getInstanceId()
  def cacheKey = "instanceCache_${instanceId}"
  ['roomCache', 'roomCacheTimestamps', 'deviceCache', 'deviceCacheTimestamps',
   'pendingRoomRequests', 'pendingDeviceRequests', 'initialized'].each { suffix ->
    state.remove("${cacheKey}_${suffix}")
  }
  log('Instance caches cleared', 2)
}
// ------------------------------
// List and Device Discovery Functions
// ------------------------------
def listDiscoveredDevices() {
  def children = getChildDevices()
  // Filter only vents by checking for percent-open attribute which pucks don't have
  def vents = children.findAll { it.hasAttribute('percent-open') }
  BigDecimal maxCoolEfficiency = 0
  BigDecimal maxHeatEfficiency = 0

  vents.each { vent ->
    def coolRate = vent.currentValue('room-cooling-rate') ?: 0
    def heatRate = vent.currentValue('room-heating-rate') ?: 0
    maxCoolEfficiency = maxCoolEfficiency.max(coolRate)
    maxHeatEfficiency = maxHeatEfficiency.max(heatRate)
  }

  def builder = new StringBuilder()
  builder << '''
  <style>
    .device-table { width: 100%; border-collapse: collapse; font-family: Arial, sans-serif; color: black; }
    .device-table th, .device-table td { padding: 8px; text-align: left; border-bottom: 1px solid #ddd; }
    .device-table th { background-color: #f2f2f2; color: #333; }
    .device-table tr:hover { background-color: #f5f5f5; }
    .device-table a { color: #333; text-decoration: none; }
    .device-table a:hover { color: #666; }
    .device-table th:not(:first-child), .device-table td:not(:first-child) { text-align: center; }
    .warning-message { color: darkorange; }
    .danger-message { color: red; }
  </style>
  <table class="device-table">
    <thead>
      <tr>
        <th>Device</th>
        <th>Cooling Efficiency</th>
        <th>Heating Efficiency</th>
      </tr>
    </thead>
    <tbody>
  '''

  vents.each { vent ->
    def coolRate = vent.currentValue('room-cooling-rate') ?: 0
    def heatRate = vent.currentValue('room-heating-rate') ?: 0
    def coolEfficiency = maxCoolEfficiency > 0 ? roundBigDecimal((coolRate / maxCoolEfficiency) * 100, 0) : 0
    def heatEfficiency = maxHeatEfficiency > 0 ? roundBigDecimal((heatRate / maxHeatEfficiency) * 100, 0) : 0
    def warnMsg = 'This vent is very inefficient, consider installing an HVAC booster.'

    def coolClass = coolEfficiency <= 25 ? 'danger-message' : (coolEfficiency <= 45 ? 'warning-message' : '')
    def heatClass = heatEfficiency <= 25 ? 'danger-message' : (heatEfficiency <= 45 ? 'warning-message' : '')

    def coolHtml = coolEfficiency <= 45 ? "<span class='${coolClass}' title='${warnMsg}'>${coolEfficiency}% (Booster Recommended)</span>" : "${coolEfficiency}%"
    def heatHtml = heatEfficiency <= 45 ? "<span class='${heatClass}' title='${warnMsg}'>${heatEfficiency}% (Booster Recommended)</span>" : "${heatEfficiency}%"

    builder << "<tr><td><a href='/device/edit/${vent.getId()}'>${vent.getLabel()}</a></td><td>${coolHtml}</td><td>${heatHtml}</td></tr>"
  }
  builder << '</tbody></table>'

  section {
    paragraph 'Discovered devices:'
    paragraph builder.toString()
  }
}
def getStructureId() {
  if (!settings?.structureId) { getStructureData() }
  return settings?.structureId
}

def updated() {
  log.debug 'Hubitat Flair App updating'
  try { state.remove('dabRatesTableHtml') } catch (ignore) { }
  try { state.remove('dabProgressTableHtml') } catch (ignore2) { }
  initialize()
}

def installed() {
  log.debug 'Hubitat Flair App installed'
  initialize()
}

def uninstalled() {
  log.debug 'Hubitat Flair App uninstalling'
  removeChildren()
  unschedule()
  unsubscribe()
}

def initialize() {
  try {
    dabManager = new DabManager(this)
    dabUIManager = new DabUIManager(this, dabManager)
    runEvery5Minutes('dabHealthMonitor')
  } catch (e) { log(3, 'App', "Error initializing managers: ${e.message}") }

  unsubscribe()

  // Ensure DAB history data structures exist
  initializeDabHistory()

  // Initialize instance-based caches
  initializeInstanceCaches()
  
  // Clean up any existing BigDecimal precision issues
  cleanupExistingDecimalPrecision()

  // Ensure required DAB tracking structures exist
  initializeDabTracking()

  // Check if we need to auto-authenticate on startup
  if (settings?.clientId && settings?.clientSecret) {
    if (!state.flairAccessToken) {
      log(2, 'App', 'No access token found on initialization, auto-authenticating...')
   
       autoAuthenticate()
    } else {
      unschedule('login')
      runEvery1Hour('login')
    }
  }
// HVAC state will be updated after each vent refresh;
  if (isDabEnabled()) {
    updateHvacStateFromDuctTemps()
    unschedule('updateHvacStateFromDuctTemps')
    runEvery1Minute('updateHvacStateFromDuctTemps')
    unschedule('aggregateDailyDabStats')
    runEvery1Day('aggregateDailyDabStats')
    // Raw data cache samplers
    try {
      if (settings?.enableRawCache) {
        runEvery1Minute('sampleRawDabData')
        runEvery1Hour('pruneRawCache')
      } else {
        unschedule('sampleRawDabData')
        unschedule('pruneRawCache')
      }
    } catch (e) { log(3, 'App', "Raw cache scheduler error: ${e.message}") }
    try {
      if (settings?.thermostat1) {
        subscribe(settings.thermostat1, 'thermostatOperatingState', 'thermostat1ChangeStateHandler')
        subscribe(settings.thermostat1, 'temperature', 'thermostat1ChangeTemp')
        subscribe(settings.thermostat1, 'coolingSetpoint', 'thermostat1ChangeTemp')
        subscribe(settings.thermostat1, 'heatingSetpoint', 'thermostat1ChangeTemp')
      }
    } catch (e) { log(3, 'App', "Thermostat subscription error: ${e.message}") }
  } else {
    unschedule('updateHvacStateFromDuctTemps')
    unschedule('aggregateDailyDabStats')
    unschedule('sampleRawDabData')
    unschedule('pruneRawCache')
  }
// Schedule periodic cleanup of instance caches and pending requests
  runEvery5Minutes('cleanupPendingRequests')
  runEvery10Minutes('clearRoomCache')
  runEvery5Minutes('clearDeviceCache')

  // Schedule/subscribe for tiles and overrides
  if (settings?.enableDashboardTiles) {
    try {
      subscribeToVentEventsForTiles()
      runEvery5Minutes('refreshVentTiles')
    } catch (e) { log(3, 'App', "Tile scheduler/subscription error: ${e.message}") }
  } else {
    try { unschedule('refreshVentTiles') } catch (ignore) { }
  
  }
      if (settings?.nightOverrideEnable) {
    try {
      if (settings?.nightOverrideStart) { schedule(settings.nightOverrideStart, 'activateNightOverride') }
      if (settings?.nightOverrideEnd) { schedule(settings.nightOverrideEnd, 'deactivateNightOverride') }
    } catch (e) { log(3, 'App', "Night override scheduling error: ${e.message}") }
  } else {
    try { unschedule('activateNightOverride');
 unschedule('deactivateNightOverride') } catch (ignore) { }
  }
}

// ------------------------------
// Helper Functions
// ------------------------------

def openAllVents(Map ventIdsByRoomId, int percentOpen) {
  ventIdsByRoomId.each { roomId, ventIds ->
    ventIds.each { ventId ->
      def vent = getChildDevice(ventId)
      if (vent) { patchVent(vent, percentOpen) }
    }
  }
}
private BigDecimal getRoomTemp(def vent) {
  def ventId = vent.getId()
  def roomName = vent.currentValue('room-name') ?: 'Unknown'
  def tempDevice = settings."vent${ventId}Thermostat"
  // If enabled, prefer latest cached raw sample
  if (settings?.useCachedRawForDab) {
    def samp = getLatestRawSample(vent.getDeviceNetworkId())
    if (samp && samp.size() >= 9) {
      def roomC = samp[5]; if (roomC != null) { return roomC as BigDecimal }
    }
  }
      if (tempDevice) {
    def temp = tempDevice.currentValue('temperature')
    if (temp == null) {
      log(2, 'App', "WARNING: Temperature device ${tempDevice?.getLabel() ?: 'Unknown'} for room '${roomName}' is not reporting temperature!")
      def roomTemp = vent.currentValue('room-current-temperature-c') ?: 0
      log(3, 'App', "Falling back to room temperature for '${roomName}': ${roomTemp}°C")
      return roomTemp
    }
      if (settings.thermostat1TempUnit == '2') {
      temp = convertFahrenheitToCentigrade(temp)
    }
    log(3, 'App', "Got temp from ${tempDevice?.getLabel() ?: 'Unknown'} for '${roomName}': ${temp}°C")
    return temp
  }

def roomTemp = vent.currentValue('room-current-temperature-c')
  
   if (roomTemp == null) {
    log(2, 'App', "ERROR: No temperature available for room '${roomName}' - neither from Puck nor from room API!")
    return 0
  }
  log(3, 'App', "Using room temperature for '${roomName}': ${roomTemp}°C")
  return roomTemp
}
def atomicStateUpdate(String stateKey, String key, value) {
  atomicState.updateMapValue(stateKey, key, value)
}

def getThermostatSetpoint(String hvacMode) {
  if (!settings?.thermostat1) {
    return null
  }

def thermostat = settings.thermostat1
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
    logError 'A thermostat is selected, but it has no readable setpoint property. Please check the device.'
    return null
  }
      if (settings.thermostat1TempUnit == '2') {
    setpoint = convertFahrenheitToCentigrade(setpoint)
  }
  return setpoint
}
def getGlobalSetpoint(String hvacMode) {
  try {
    def sp = getThermostatSetpoint(hvacMode)
    if (sp != null) { return sp }
  } catch (ignore) { }
// Compute median of room set-points (Celsius) from vents
  def vents = getChildDevices()?.findAll { it.hasAttribute('percent-open') } ?: []
  def list = vents.collect { it.currentValue('room-set-point-c') }.findAll { it != null }.collect { it as BigDecimal }
      if (list && list.size() > 0) {
    def sorted = list.sort()
    int mid = sorted.size().intdiv(2)
    return sorted[mid] as BigDecimal
 
   }
  return (hvacMode == COOLING ? DEFAULT_COOLING_SETPOINT_C : DEFAULT_HEATING_SETPOINT_C)
}

def roundBigDecimal(BigDecimal number, int scale = 3) {
  number.setScale(scale, BigDecimal.ROUND_HALF_UP)
}
def roundToDecimalPlaces(def value, int decimalPlaces) {
  if (value == null || value == 0) return 0
  
  try {
    def doubleValue = value as Double
    def multiplier = Math.pow(10, decimalPlaces)
    def rounded = Math.round(doubleValue * multiplier) / multiplier
    return rounded as Double
  } catch (Exception e) {
    log(2, 'App', "Error rounding value ${value}: ${e.message}")
    return 0
  }
}
def cleanDecimalForJson(def value) {
  if (value == null ||
 value == 0) { return 0.0d }
  
  try {
    def stringValue = value.toString()
    def doubleValue = Double.parseDouble(stringValue)
    if (!Double.isFinite(doubleValue)) { return 0.0d }
    def multiplier = 1000000000.0d  // 10^9 for 10 decimal places
    def rounded = Math.round(doubleValue * multiplier) / multiplier
   
     return Double.valueOf(rounded)
  } catch (Exception e) {
    log(3, 'App', "Error cleaning decimal for JSON: ${e.message}")
    return 0.0d
  }
}
int roundToNearestMultiple(BigDecimal num) {
  int granularity = settings.ventGranularity ?
 settings.ventGranularity.toInteger() : 5
  return (int)(Math.round(num / granularity) * granularity)
}

def convertFahrenheitToCentigrade(BigDecimal tempValue) {
  (tempValue - 32) * (5 / 9)
}

def rollingAverage(BigDecimal currentAverage, BigDecimal newNumber, BigDecimal weight = 1, int numEntries = 10) {
  if (numEntries <= 0) { return 0 }
  BigDecimal base = (currentAverage ?: 0) == 0 ? newNumber : currentAverage
  BigDecimal sum = base * (numEntries - 1)
  def weightedValue = (newNumber - base) * weight
  def numberToAdd = base + weightedValue
  sum += numberToAdd
  return sum / numEntries
}

def hasRoomReachedSetpoint(String hvacMode, BigDecimal setpoint, BigDecimal currentTemp, BigDecimal offset = 0) {
  (hvacMode == COOLING && currentTemp <= (setpoint - offset)) ||
  (hvacMode == HEATING && currentTemp >= (setpoint + offset))
}
def calculateHvacMode(BigDecimal temp, BigDecimal coolingSetpoint, BigDecimal heatingSetpoint) {
  try {
    if (temp != null) {
      if (coolingSetpoint != null && temp >= (coolingSetpoint + SETPOINT_OFFSET)) { return COOLING }
      if (heatingSetpoint != null && temp <= (heatingSetpoint - SETPOINT_OFFSET)) { return HEATING }
    }
  } catch (ignore) { }
  return calculateHvacModeRobust()
}
def calculateHvacModeRobust() { return dabManager.calculateHvacModeRobust() }

def resetApiConnection() {
  logWarn 'Resetting API connection'
  atomicState.failureCounts = [:]
  authenticate()
}
def noOpHandler(resp, data) {
}

def login() {
  authenticate()
  getStructureData()
}

def authenticate(int retryCount = 0) {
  log(2, 'App', 'Getting access_token from Flair using async method')
  state.authInProgress = true
  state.remove('authError')  // Clear any previous error state
  
  def uri = "${BASE_URL}/oauth2/token"
  def body = "client_id=${settings?.clientId}&client_secret=${settings?.clientSecret}" +
    '&scope=vents.view+vents.edit+structures.view+structures.edit+pucks.view+pucks.edit&grant_type=client_credentials'
  
  def params = [
    uri: uri, 
    body: body, 
    timeout: HTTP_TIMEOUT_SECS,
    contentType: 'application/x-www-form-urlencoded'
  ]
  
  if (canMakeRequest()) {
    incrementActiveRequests()
    try {
      asynchttpPost('handleAuthResponse', params, [retryCount: retryCount])
    } catch (Exception e) {
      def err = "Authentication request failed: ${e.message}"; logError err
      state.authError = err
      state.authInProgress = false
      decrementActiveRequests(); return err
    }
  } else {
    state.authInProgress = false
    if (retryCount < MAX_API_RETRY_ATTEMPTS) {
      runInMillis(API_CALL_DELAY_MS, 'retryAuthenticateWrapper', [data: [retryCount: retryCount + 1]])
    } else {
      def err = "Authentication failed after ${MAX_API_RETRY_ATTEMPTS} retries"; logError err
      state.authError = err
    }
  }
  return ''
}
def retryAuthenticateWrapper(data) {
 
   authenticate(data?.retryCount ?: 0)
}

def handleAuthResponse(resp, data) {
  decrementActiveRequests()
  try {
    log(2, 'App', "handleAuthResponse called with resp status: ${resp?.getStatus()}")
    state.authInProgress = false
    
    if (!resp) {
      state.authError = "Authentication failed: No response from Flair API"
      logError state.authError
      return
    }
      if (resp.hasError()) {
      def status = resp.getStatus()
      
 def errorMsg = "Authentication failed with HTTP ${status}";
      if (status == 401) {
        errorMsg += ": Invalid credentials. Please verify your Client ID and Client Secret."
      } else if (status == 403) {
        errorMsg += ": Access forbidden. Please verify your OAuth credentials have proper permissions."
      } else if (status == 429) {
        errorMsg += ": Rate limited. Please wait a few minutes and try again."
      } else {
        errorMsg += ": ${resp.getErrorMessage() ?: 'Unknown error'}"
      }
      state.authError = errorMsg
       logError state.authError
      return
    }

def respJson = resp.getJson()
    
    if (respJson?.access_token) {
      state.flairAccessToken = respJson.access_token
      state.remove('authError')
      log(2, 'App', 'Authentication successful')
    
       
      runIn(2, 'getStructureDataAsync')
    } else {
      def errorDetails = respJson?.error_description ?: respJson?.error ?: 'No access token in response'
      state.authError = "Authentication failed: ${errorDetails}. " +
                        "Please verify your OAuth 2.0 credentials are correct."
      logError state.authError
    }
  } catch (Exception e) {
    state.authInProgress = false
    state.authError = "Authentication processing failed: ${e.message}"
    logError "handleAuthResponse exception: ${e.message}"
    log(3, 'App', "Exception stack trace: ${e.getStackTrace()}")
  }
}

def appButtonHandler(String btn) {
  switch (btn) {
    case 'authenticate':
    case 'retryAuth':
      login()
      unschedule('login')
      runEvery1Hour('login')
      break
    case 'discoverDevices':
      discover()
      break
    case 'runHealthCheck':
      performHealthCheck()
      break
    case 'reauthenticate':
      autoReauthenticate()
      break
  
     case 'resetCache':
      resetCaches()
      break
    case 'resyncVents':
      discover()
      break
    case 'exportEfficiencyData':
      handleExportEfficiencyData()
      break
    case 'importEfficiencyData':
      handleImportEfficiencyData()
      break
    case 'clearExportData':
      handleClearExportData()
      break
  }
}
def autoAuthenticate() {
  if (settings?.clientId && settings?.clientSecret && !state.flairAccessToken) {
    log(3, 'App', 'Auto-authenticating with provided credentials')
    login()
    unschedule('login')
    runEvery1Hour('login')
  }
}
def autoReauthenticate() {
  log(2, 'App', 'Token expired or invalid, re-authenticating...')
  state.remove('flairAccessToken')
  state.remove('authError')
  if (authenticate() == '') {
    unschedule('login')
    runEvery1Hour('login')
    log(2, 'App', 'Re-authentication successful, rescheduled hourly token refresh')
  }
}
private void discover() {
  atomicState.remove('ventsByRoomId')
  def structureId = getStructureId()
  def ventsUri = "${BASE_URL}/api/structures/${structureId}/vents"
  log(2, 'API', "Calling vents endpoint: ${ventsUri}", ventsUri)
  getDataAsync(ventsUri, 'handleDeviceList', [deviceType: 'vents'])
  def pucksUri = "${BASE_URL}/api/structures/${structureId}/pucks"
  log(2, 'API', "Calling pucks endpoint: ${pucksUri}", pucksUri)
  getDataAsync(pucksUri, 'handleDeviceList', [deviceType: 'pucks'])
  def roomsUri = "${BASE_URL}/api/structures/${structureId}/rooms?include=pucks"
  log(2, 'API', "Calling rooms endpoint for pucks: ${roomsUri}", roomsUri)
  getDataAsync(roomsUri, 'handleRoomsWithPucks')
  def allPucksUri = "${BASE_URL}/api/pucks"
  log(2, 'API', "Calling all pucks endpoint: ${allPucksUri}", allPucksUri)
  getDataAsync(allPucksUri, 'handleAllPucks')
}

def handleAllPucks(resp, data) {
  decrementActiveRequests()
  try {
    log(2, 'App', "handleAllPucks called")
    if (!isValidResponse(resp)) { 
      log(2, 'App', "handleAllPucks: Invalid response status: ${resp?.getStatus()}")
      return 
    }

def respJson = resp?.getJson()
  log(2, 'App', "All pucks endpoint response: has data=${respJson?.data != null}, count=${respJson?.data?.size() ?: 0}")
  
  if (respJson?.data) {
    def puckCount = 0
    respJson.data.each { puckData ->
      try {
        if (puckData?.id) {
          puckCount++
          def puckId = puckData?.id?.toString()?.trim()
          def puckName = puckData?.attributes?.name?.toString()?.trim() ?: "Puck-${puckId}"
            
            log(3, 'App', "Creating puck from all pucks endpoint: ${puckName} (${puckId})")
            def device = [
              id   : puckId,
              type : 'pucks',
              label: puckName
            ]
            
    
             def dev = makeRealDevice(device)
            if (dev) {
              log(2, 'App', "Created puck device: ${puckName}")
            }
          }
        } catch (Exception e) {
          log(3, 'App', "Error processing puck from all pucks: ${e.message}")        }
      }
      if (puckCount > 0) {
        log(3, 'App', "Discovered ${puckCount} pucks from all pucks endpoint")
      }
    }
  } catch (Exception e) {
    log(3, 'App', "Error in handleAllPucks: ${e.message}")
  }
}

def handleRoomsWithPucks(resp, data) {
  decrementActiveRequests()
  try {
    log(2, 'App', "handleRoomsWithPucks called")
    if (!isValidResponse(resp)) { 
      log(2, 'App', "handleRoomsWithPucks: Invalid response status: ${resp?.getStatus()}")
      return 
    }

def respJson = resp.getJson()
    log(3, 'App', "handleRoomsWithPucks response: has included=${respJson?.included != null}, included count=${respJson?.included?.size() ?: 0}, has data=${respJson?.data != null}, data count=${respJson?.data?.size() ?: 0}")
    
    if (respJson?.included) {
      def puckCount = 0
      respJson.included.each { it ->
      
       try {
          if (it?.type == 'pucks' && it?.id) {
            puckCount++
            def puckId = it.id?.toString()?.trim()
            if (!puckId || puckId.isEmpty()) {
              log(2, 'App', "Skipping puck with invalid ID")
              return // Skip this puck
            }

def puckName = it.attributes?.name?.toString()?.trim()
            if (!puckName || puckName.isEmpty()) {
            
               puckName = "Puck-${puckId}"
            }
            if (!puckName || puckName.isEmpty()) {
               log(3, 'App', "Skipping puck with empty name even after fallback")
              return
            }
          
           
            def device = [
              id   : puckId,
              type : 'pucks',
    
                     label: puckName
            ]
            
            def dev = makeRealDevice(device)
            if (dev) {
              log(2, 'App', "Created puck device: ${puckName}")
            }
     
           }
        } catch (Exception e) {
          log(3, 'App', "Error processing puck in loop: ${e.message}, line: ${e.stackTrace?.find()?.lineNumber}")
        }
      }
      if (puckCount > 0) {
        log(3, 'App', "Discovered ${puckCount} pucks from rooms include")
      }
    }
  } catch (Exception e) { log(3, 'App', "Error in handleRoomsWithPucks: ${e.message} at line ${e.stackTrace?.find()?.lineNumber}") }
  
  try {
    if (respJson?.data) {
      def roomPuckCount = 0
      respJson.data.each { room ->
        if (room.relationships?.pucks?.data) {
          room.relationships.pucks.data.each { puck ->
            try {
              roomPuckCount++
        
               def puckId = puck.id?.toString()?.trim(); if (!puckId || puckId.isEmpty()) {
                log(2, 'App', "Skipping puck with invalid ID in room ${room.attributes?.name}")
                return
              }
              def puckName = "Puck-${puckId}";
              if (room.attributes?.name) {
   
                             puckName = "${room.attributes.name} Puck"
              }
              
              log(3, 'App', "Creating puck device from room reference: ${puckName} (${puckId})")
              
              def device = [
  
                             id   : puckId,
                type : 'pucks',
                label: puckName
              ]
              
              def dev = makeRealDevice(device)
  
                           if (dev) {
                log(2, 'App', "Created puck device from room reference: ${puckName}")
              }
            } catch (Exception e) {
              log(3, 'App', "Error creating puck from room reference: ${e.message}")            }
          }
        }
      }
      if (roomPuckCount > 0) {
        log(3, 'App', "Found ${roomPuckCount} puck references in rooms")
      }
    }
  } catch (Exception e) {
    log(3, 'App', "Error checking room puck relationships: ${e.message}")
  }
}
def handleDeviceList(resp, data) {
  decrementActiveRequests()
  log(2, 'App', "handleDeviceList called for ${data?.deviceType}")
  if (!isValidResponse(resp)) {
    if (resp?.hasError() && resp.getStatus() == 404 && data?.deviceType == 'pucks') {
      log(2, 'App', "Pucks endpoint returned 404 - this is normal, trying other methods")
    } else if (data?.deviceType == 'pucks') {
      log(2, 'App', "Pucks endpoint failed with error: ${resp?.getStatus()}")
    }
    return 
  }

def respJson = resp?.getJson()
  if (!respJson?.data || respJson.data.isEmpty()) {
    if (data?.deviceType == 'pucks') {
      log(2, 'App', "No pucks found in structure endpoint - they may be included with rooms instead")
    } else {
      logWarn "No devices discovered. This may occur with OAuth 1.0 credentials. " +
              "Please ensure you're using OAuth 2.0 credentials or Legacy API (OAuth 1.0) credentials."    }
    return
  }

def ventCount = 0
  def puckCount = 0
  respJson.data.each { it ->
    if (it?.type == 'vents' || it?.type == 'pucks') {
      if (it.type == 'vents') {
        ventCount++
      } else if (it.type == 'pucks') {
        puckCount++
      }

def device = [
        id   : it?.id,
        type : it?.type,
        label: it?.attributes?.name
      ]
      def dev = makeRealDevice(device)
      if (dev && it.type == 'vents') {
        processVentTraits(dev, [data: it])
      }
    }
  }
  log(3, 'App', "Discovered ${ventCount} vents and ${puckCount} pucks")
  if (ventCount == 0 && puckCount == 0) {
    logWarn "No devices found in the structure. This typically happens with incorrect OAuth credentials."
  }
}

def makeRealDevice(Map device) {
  if (!device?.id || !device?.label || !device?.type) {
    logError "Invalid device data: ${device}"
    return null
  }

def deviceId = device.id?.toString()?.trim()
  def deviceLabel = device.label?.toString()?.trim()
  
  if (!deviceId || deviceId.isEmpty() || !deviceLabel || deviceLabel.isEmpty()) {
    logError "Invalid device ID or label: id=${deviceId}, label=${deviceLabel}"
    return null
  }

def newDevice = getChildDevice(deviceId)
  if (!newDevice) {
    def deviceType = device.type == 'vents' ? 'Flair vents' : 'Flair pucks'
    try {
      newDevice = addChildDevice('bot.flair', deviceType, deviceId, [name: deviceLabel, label: deviceLabel])
    } catch (Exception e) {
      logError "Failed to add child device: ${e.message}"
      return null
    }
  }
  return newDevice
}

def getDeviceData(device) {
  log(3, 'App', "Refresh device details for ${device}")
  def deviceId = device.getDeviceNetworkId()
  def roomId = device.currentValue('room-id')
  def isPuck = !device.hasAttribute('percent-open')
  
  if (isPuck) {
    getDeviceDataWithCache(device, deviceId, 'pucks', 'handlePuckGet')
    getDeviceReadingWithCache(device, deviceId, 'pucks', 'handlePuckReadingGet')
    getRoomDataWithCache(device, deviceId, isPuck)
  } else {
    getDeviceReadingWithCache(device, deviceId, 'vents', 'handleDeviceGet')
    getRoomDataWithCache(device, deviceId, isPuck)
  }
}
def getRoomDataWithCache(device, deviceId, isPuck) {
  def roomId = device.currentValue('room-id')
  
  if (roomId) {
    def cachedData = getCachedRoomData(roomId)
    if (cachedData) {
      log(3, 'App', "Using cached room data for room ${roomId}")
      processRoomTraits(device, cachedData)
      return
    }
    if (isRequestPending(roomId)) {
      return
    }
    markRequestPending(roomId)
  }
  def endpoint = isPuck ? "pucks" : "vents"
  getDataAsync("${BASE_URL}/api/${endpoint}/${deviceId}/room", 'handleRoomGetWithCache', [device: device])
}

def getDeviceDataWithCache(device, deviceId, deviceType, callback) {
  def cacheKey = "${deviceType}_${deviceId}"
  def cachedData = getCachedDeviceReading(cacheKey)
  if (cachedData) {
    log(3, 'App', "Using cached ${deviceType} data for device ${deviceId}")
    if (callback == 'handlePuckGet') {
      handlePuckGet([getJson: { cachedData }], [device: device])
    }
    return
  }
  if (isDeviceRequestPending(cacheKey)) {
    return
  }
  markDeviceRequestPending(cacheKey)
  def uri = "${BASE_URL}/api/${deviceType}/${deviceId}"
  getDataAsync(uri, callback + 'WithCache', [device: device, cacheKey: cacheKey])
}

def getDeviceReadingWithCache(device, deviceId, deviceType, callback) {
  def cacheKey = "${deviceType}_reading_${deviceId}"
  def cachedData = getCachedDeviceReading(cacheKey)
  if (cachedData) {
    log(3, 'App', "Using cached ${deviceType} reading for device ${deviceId}")
    if (callback == 'handlePuckReadingGet') {
      handlePuckReadingGet([getJson: { cachedData }], [device: device])
    } else if (callback == 'handleDeviceGet') {
      handleDeviceGet([getJson: { cachedData }], [device: device])
    }
    return
  }
  if (isDeviceRequestPending(cacheKey)) {
  }
  markDeviceRequestPending(cacheKey)
  def uri = (deviceType == 'pucks'
             ? "${BASE_URL}/api/pucks/${deviceId}/current-reading"
             : "${BASE_URL}/api/vents/${deviceId}/current-reading")
  getDataAsync(uri, callback + 'WithCache', [device: device, cacheKey: cacheKey])
}

def handleRoomGet(resp, data) {
  decrementActiveRequests()
  if (!isValidResponse(resp) || !data?.device) { return }
  processRoomTraits(data.device, resp.getJson())
}
def handleRoomGetWithCache(resp, data) {
  def roomData = null
  def roomId = null
  
  try {
    if (data?.device) {
      roomId = data.device.currentValue('room-id')
    }
    
    if (isValidResponse(resp) && data?.device) {
      roomData = resp.getJson()
      if (roomData?.data?.id) {
        roomId = roomData.data.id
      }
      
      if (roomId) {
        cacheRoomData(roomId, roomData)
        log(3, 'App', "Cached room data for room ${roomId}")
      }
      
      processRoomTraits(data.device, roomData)
    } else {
      log(2, 'App', "Room data request failed for device ${data?.device}, status: ${resp?.getStatus()}")
    }
  } catch (Exception e) {
    log(3, 'App', "Error in handleRoomGetWithCache: ${e.message}")
  } finally {
    if (roomId) {
      clearPendingRequest(roomId)
      log(3, 'App', "Cleared pending request for room ${roomId}")
    }
  }
}
def clearRoomCache() {
  initializeInstanceCaches()
  def instanceId = getInstanceId()
  def cacheKey = "instanceCache_${instanceId}"
  def currentTime = getCurrentTime()
  def expiredRooms = []
  
  def roomCacheTimestamps = state."${cacheKey}_roomCacheTimestamps"
  def roomCache = state."${cacheKey}_roomCache"
  
  roomCacheTimestamps.each { roomId, timestamp ->
    if ((currentTime - timestamp) > ROOM_CACHE_DURATION_MS) {
      expiredRooms << roomId
    }
  }
  
  expiredRooms.each { roomId ->
   
   roomCache.remove(roomId)
    roomCacheTimestamps.remove(roomId)
    log(4, 'App', "Cleared expired cache for room ${roomId}")
  }
}
def clearDeviceCache() {
  initializeInstanceCaches()
  def instanceId = getInstanceId()
  def cacheKey = "instanceCache_${instanceId}"
  def currentTime = getCurrentTime()
  def expiredDevices = []
  
  def deviceCacheTimestamps = state."${cacheKey}_deviceCacheTimestamps"
  def deviceCache = state."${cacheKey}_deviceCache"
  
  deviceCacheTimestamps.each { deviceKey, timestamp ->
    if ((currentTime - timestamp) > DEVICE_CACHE_DURATION_MS) {
      expiredDevices << deviceKey
    }
  }
  
  expiredDevices.each { deviceKey ->
   
   deviceCache.remove(deviceKey)
    deviceCacheTimestamps.remove(deviceKey)
    log(4, 'App', "Cleared expired cache for device ${deviceKey}")
  }
}
def cleanupPendingRequests() {
  initializeInstanceCaches()
  def instanceId = getInstanceId()
  def cacheKey = "instanceCache_${instanceId}"
  
  def pendingRoomRequests = state."${cacheKey}_pendingRoomRequests"
  def pendingDeviceRequests = state."${cacheKey}_pendingDeviceRequests"
  
  def currentActiveRequests = atomicState.activeRequests ?: 0
  if (currentActiveRequests >= MAX_CONCURRENT_REQUESTS) {
    log(2, 'App', "CRITICAL: Active request counter is stuck at ${currentActiveRequests}/${MAX_CONCURRENT_REQUESTS} - resetting to 0")
    atomicState.activeRequests = 0
  }
  
  def roomsToClean = []
  pendingRoomRequests.each { roomId, isPending ->
    if (isPending) {
      roomsToClean << roomId
    }
  }
  
  roomsToClean.each { roomId ->
    pendingRoomRequests[roomId] = false
  }
      if (roomsToClean.size() > 0) {
    log(2, 'App', "Cleared ${roomsToClean.size()} stuck pending request flags for rooms: ${roomsToClean.join(', ')}")
  }
  def devicesToClean = []
  pendingDeviceRequests.each { deviceKey, isPending ->
    if (isPending) {
      devicesToClean << deviceKey
    }
  }
  
  devicesToClean.each { deviceKey ->
    pendingDeviceRequests[deviceKey] = false
  }
      if (devicesToClean.size() > 0) {
    log(2, 'App', "Cleared ${devicesToClean.size()} stuck pending request flags for devices: ${devicesToClean.join(', ')}")
  }
}

def handleDeviceGet(resp, data) {
  decrementActiveRequests()
  if (!isValidResponse(resp) ||
 !data?.device) { return }
  processVentTraits(data.device, resp.getJson())
}
def handleDeviceGetWithCache(resp, data) {
  def deviceData = null
  def cacheKey = data?.cacheKey
  
  try {
    if (isValidResponse(resp) && data?.device) {
      deviceData = resp.getJson()
      
      if (cacheKey && deviceData) {
        cacheDeviceReading(cacheKey, deviceData)
        log(4, 'App', "Cached device reading for ${cacheKey}")
      }
      
      processVentTraits(data.device, deviceData)
    } else {
      if (resp instanceof Exception || resp.toString().contains('LimitExceededException')) {
        logWarn "Device reading request failed due to hub load: ${resp.toString()}"
      } else {
        log(3, 'App', "Device reading request failed for ${cacheKey}, status: ${resp?.getStatus()}")
      }
    }
 
   } catch (Exception e) {
    logWarn "Error in handleDeviceGetWithCache: ${e.message}"
  } finally {
    if (cacheKey) {
      clearDeviceRequestPending(cacheKey)
      log(1, 'App', "Cleared pending device request for ${cacheKey}")
    }
  }
}

def handlePuckGet(resp, data) {
  decrementActiveRequests()
  if (!isValidResponse(resp) ||
 !data?.device) { return }

def respJson = resp.getJson()
  if (respJson?.data) {
    def puckData = respJson.data
    if (puckData?.attributes?.'current-temperature-c' != null) {
      def tempC = puckData.attributes['current-temperature-c']
      def tempF = (tempC * 9/5) + 32
      sendEvent(data.device, [name: 'temperature', value: tempF, unit: '°F'])
      log(2, 'App', "Puck temperature: ${tempF}°F")
    }
      if (puckData?.attributes?.'current-humidity' != null) {
      sendEvent(data.device, [name: 'humidity', value: puckData.attributes['current-humidity'], unit: '%'])
    }
      if (puckData?.attributes?.voltage != null) {
      try {
        def voltage = puckData.attributes.voltage as BigDecimal
        def battery = ((voltage - 2.0) / 1.6) * 100  // Assuming 2.0V = 0%, 3.6V = 100%
        battery = Math.max(0, Math.min(100, battery.round() as int))
        sendEvent(data.device, [name: 'battery', value: battery, unit: '%'])
      } catch (Exception e) {
        log(2, 'App', "Error calculating battery for puck: ${e.message}")
      }
    }
    ['inactive', 'created-at', 'updated-at', 'current-rssi', 'name'].each { attr ->
      if (puckData.attributes && puckData.attributes[attr] != null) {
        sendEvent(data.device, [name: attr, value: puckData.attributes[attr]])
      }
    }
  }
}
def handlePuckGetWithCache(resp, data) {
  def deviceData = null
  def cacheKey = data?.cacheKey
  
  try {
    if (isValidResponse(resp) && data?.device) {
      deviceData = resp.getJson()
      
      if (cacheKey && deviceData) {
        cacheDeviceReading(cacheKey, deviceData)
        log(3, 'App', "Cached puck data for ${cacheKey}")
      }
      handlePuckGet([getJson: { deviceData }], data)
    }
  } finally {
    if (cacheKey) {
      clearDeviceRequestPending(cacheKey)
    }
  }
}

def handlePuckReadingGet(resp, data) {
  decrementActiveRequests()
  if (!isValidResponse(resp) ||
 !data?.device) { return }

def respJson = resp.getJson()
  if (respJson?.data) {
    def reading = respJson.data
    if (reading.attributes?.'room-temperature-c' != null) {
      def tempC = reading.attributes['room-temperature-c']
      def tempF = (tempC * 9/5) + 32
      sendEvent(data.device, [name: 'temperature', value: tempF, unit: '°F'])
      log(2, 'App', "Puck temperature from reading: ${tempF}°F")
    }
      if (reading.attributes?.humidity != null) {
      sendEvent(data.device, [name: 'humidity', value: reading.attributes.humidity, unit: '%'])
    }
      if (reading.attributes?.'system-voltage' != null) {
      try {
        def voltage = reading.attributes['system-voltage']
        sendEvent(data.device, [name: 'voltage', value: voltage, unit: 'V'])
        def battery = ((voltage - 2.0) / 1.6) * 100
        battery = Math.max(0, Math.min(100, battery.round() as int))
 
               sendEvent(data.device, [name: 'battery', value: battery, unit: '%'])
      } catch (Exception e) {
        log(2, 'App', "Error calculating battery from reading: ${e.message}")
      }
    }
  }
}
def handlePuckReadingGetWithCache(resp, data) {
  def deviceData = null
  def cacheKey = data?.cacheKey
  
  try {
    if (isValidResponse(resp) && data?.device) {
      deviceData = resp.getJson()
      
     
       if (cacheKey && deviceData) {
        cacheDeviceReading(cacheKey, deviceData)
        log(3, 'App', "Cached puck reading for ${cacheKey}")
      }
      handlePuckReadingGet([getJson: { deviceData }], data)
    }
  } finally {
    if (cacheKey) {
      clearDeviceRequestPending(cacheKey)
    }
  }
}

def traitExtract(device, details, String propNameData, String propNameDriver = propNameData, unit = null) {
  try {
    def propValue = details.data.attributes[propNameData]
    if (propValue != null) {
      def eventData = [name: propNameDriver, value: propValue]
      if (unit) { eventData.unit = unit }
      sendEvent(device, eventData)
    }
  } catch (err) {
    logWarn err
  }
}

def processVentTraits(device, details) {
  logDetails "Processing Vent data for ${device}", details, 1
  if (!details?.data) {
    logWarn "Failed extracting data for ${device}"
    return
  }
  ['firmware-version-s', 'rssi', 'connected-gateway-name', 'created-at', 'duct-pressure',
   'percent-open', 'duct-temperature-c', 'motor-run-time', 'system-voltage', 'motor-current',
   'has-buzzed', 'updated-at', 'inactive'].each { attr ->
      traitExtract(device, details, attr, attr == 'percent-open' ?
 'level' : attr, attr == 'percent-open' ? '%' : null)
   }
   
    if (details?.data?.attributes?.'system-voltage' != null) {
     def voltage = details.data.attributes['system-voltage']
     sendEvent(device, [name: 'voltage', value: voltage, unit: 'V'])
   }
}

def processRoomTraits(device, details) {
  if (!device || !details?.data || !details.data.id) { return }
  logDetails "Processing Room data for ${device}", details, 1
  sendEvent(device, [name: 'room-id', value: details.data.id])
  [
    'name': 'room-name',
    'current-temperature-c': 'room-current-temperature-c',
    'room-conclusion-mode': 'room-conclusion-mode',
    'room-humidity-away-min': 'room-humidity-away-min',
    'room-type': 'room-type',
    'temp-away-min-c': 'room-temp-away-min-c',
    'level': 'room-level',
    'hold-until': 'room-hold-until',
    'room-away-mode': 'room-away-mode',
    'heat-cool-mode': 'room-heat-cool-mode',
    'updated-at': 'room-updated-at',
    'state-updated-at': 'room-state-updated-at',
    'set-point-c': 'room-set-point-c',
    'hold-until-schedule-event': 'room-hold-until-schedule-event',
    'frozen-pipe-pet-protect': 'room-frozen-pipe-pet-protect',
    'created-at': 'room-created-at',
    'windows': 'room-windows',
    'air-return': 'room-air-return',
    'current-humidity': 'room-current-humidity',
    'hold-reason': 'room-hold-reason',
    'occupancy-mode': 'room-occupancy-mode',
    'temp-away-max-c': 'room-temp-away-max-c',
    'humidity-away-max': 'room-humidity-away-max',
    'preheat-precool': 'room-preheat-precool',
    'active': 'room-active',
    'set-point-manual': 'room-set-point-manual',
    'pucks-inactive': 'room-pucks-inactive'
  ].each { key, driverKey ->
    traitExtract(device, details, key, driverKey)
  }
      if (details?.data?.relationships?.structure?.data) {
    sendEvent(device, [name: 'structure-id', value: details.data.relationships.structure.data.id])
  }
      if (details?.data?.relationships['remote-sensors']?.data && 
      !details.data.relationships['remote-sensors'].data.isEmpty()) {
    def remoteSensor = details.data.relationships['remote-sensors'].data.first()
    if (remoteSensor?.id) {
      def uri = "${BASE_URL}/api/remote-sensors/${remoteSensor.id}/sensor-readings"
      getDataAsync(uri, 'handleRemoteSensorGet', [device: device])
    }
 
   }
  updateByRoomIdState(details)
}

def handleRemoteSensorGet(resp, data) {
  decrementActiveRequests()
  if (!data) { return }
  if (resp?.hasError() && resp.getStatus() == 404) {
    log(1, 'App', "No remote sensor data available for ${data?.device?.getLabel() ?: 'unknown device'}")
    return
  }
      if (!isValidResponse(resp)) { return }
  try {
    def details = resp.getJson()
    if (!details?.data?.first()) { return }

def propValue = details.data.first().attributes['occupied']
  
     sendEvent(data.device, [name: 'room-occupied', value: propValue])
  } catch (Exception e) {
    log(2, 'App', "Error parsing remote sensor JSON: ${e.message}")
    return
  }
}

def updateByRoomIdState(details) {
  if (!details?.data?.relationships?.vents?.data) { return }

def roomId = details.data.id?.toString()
  if (!atomicState.ventsByRoomId?.get(roomId)) {
    def ventIds = details.data.relationships.vents.data.collect { it.id }
    atomicStateUpdate('ventsByRoomId', roomId, ventIds)
  }
}

def patchStructureData(Map attributes) {
  def body = [data: [type: 'structures', attributes: attributes]]
  def uri = "${BASE_URL}/api/structures/${getStructureId()}"
  patchDataAsync(uri, null, body)
}

def getStructureDataAsync(int retryCount = 0) {
  log(2, 'App', 'Getting structure data asynchronously')
  def uri = "${BASE_URL}/api/structures"
  def headers = [ Authorization: "Bearer ${state.flairAccessToken}" ]
  def httpParams = [ 
    uri: uri, 
    headers: headers, 
    contentType: CONTENT_TYPE, 
    timeout: HTTP_TIMEOUT_SECS 
  ]
  
  if (canMakeRequest()) {
    incrementActiveRequests()
    try {
      asynchttpGet('handleStructureResponse', httpParams)
    } catch (Exception e) {
      logError "Structure data request failed: ${e.message}"; decrementActiveRequests()
    }
  } else {
    if (retryCount < MAX_API_RETRY_ATTEMPTS) {
      runInMillis(API_CALL_DELAY_MS, 'retryGetStructureDataAsyncWrapper', [data: [retryCount: retryCount + 1]])
    } else {
      logError "getStructureDataAsync failed after ${MAX_API_RETRY_ATTEMPTS} retries";
    }
  }
}
def retryGetStructureDataAsyncWrapper(data) {
  getStructureDataAsync(data?.retryCount ?: 0)
}

def handleStructureResponse(resp, data) {
  decrementActiveRequests()
  try {
    if (!isValidResponse(resp)) { 
      logError "Structure data request failed"; return 
    }

def response = resp.getJson()
    if (!response?.data?.first()) {
      logError 'No structure data available'; return
    }

def myStruct = response.data.first()
    if (myStruct?.id) {
      app.updateSetting('structureId', myStruct.id)
      log(2, 'App', "Structure loaded: id=${myStruct.id}, name=${myStruct.attributes?.name}")
    }
  } catch (Exception e) { logError "Structure data processing failed: ${e.message}" }
}

def getStructureData(int retryCount = 0) {
  log(1, 'App', 'getStructureData')
  
 
   if (!canMakeRequest()) {
    if (retryCount < MAX_API_RETRY_ATTEMPTS) {
      log(2, 'App', "Structure data request delayed due to concurrent limit (attempt ${retryCount + 1}/${MAX_API_RETRY_ATTEMPTS})")
      runInMillis(API_CALL_DELAY_MS, 'retryGetStructureDataWrapper', [data: [retryCount: retryCount + 1]])
      return
    } else {
      logError "getStructureData failed after ${MAX_API_RETRY_ATTEMPTS} attempts due to concurrent limits"; return
    }
  
  }

def uri = "${BASE_URL}/api/structures"
  def headers = [ Authorization: "Bearer ${state.flairAccessToken}" ]
  def httpParams = [ uri: uri, headers: headers, contentType: CONTENT_TYPE, timeout: HTTP_TIMEOUT_SECS ]
  
  incrementActiveRequests()
  
  try {
    httpGet(httpParams) { resp ->
      decrementActiveRequests()
      
      if (!resp.success) { 
        throw new Exception("HTTP request failed with status: ${resp.status}")
      }

def response = resp.getData()
      if (!response) {
     
       logError 'getStructureData: no data'
        return
      }
      logDetails 'Structure response: ', response, 1
      def myStruct = response.data.first()
      if (!myStruct?.attributes) {
        logError 'getStructureData: no structure data'; return
      }
      log(3, 'App', "Structure loaded: id=${myStruct.id}, name=${myStruct.attributes.name}, mode=${myStruct.attributes.mode}")
      app.updateSetting('structureId', myStruct.id)
    }
  } catch (Exception e) {
    decrementActiveRequests()
    
    if (retryCount < MAX_API_RETRY_ATTEMPTS) {
      log(2, 'App', "Structure data request failed (attempt ${retryCount + 1}/${MAX_API_RETRY_ATTEMPTS}): ${e.message}")
      runInMillis(API_CALL_DELAY_MS, 'retryGetStructureDataWrapper', [data: [retryCount: retryCount + 1]])
    } else {
      logError "getStructureData failed after ${MAX_API_RETRY_ATTEMPTS} attempts: ${e.message}"
    }
  }
}
def retryGetStructureDataWrapper(data) {
  getStructureData(data?.retryCount ?: 0)
}