/**
 *  Hubitat Flair Vents Integration
 *  Version 0.240
 *
 *  Copyright 2024 Jaime Botero. All Rights Reserved
 *
 *  Licensed under the Apache License, Version 2.0 (the 'License');
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an 'AS IS' BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
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

// Base URL for Flair API endpoints.
@Field static final String BASE_URL = 'https://api.flair.co'

// Instance-based cache durations (reduced from 60s to 30s for better responsiveness)
@Field static final Long ROOM_CACHE_DURATION_MS = 30000 // 30 second cache duration
@Field static final Long DEVICE_CACHE_DURATION_MS = 30000 // 30 second cache duration for device readings
@Field static final Integer MAX_CACHE_SIZE = 50 // Maximum cache entries per instance

// DEFAULT_HISTORY_RETENTION_DAYS is provided by DabManager library
@Field static final Integer DAILY_SUMMARY_PAGE_SIZE = 30 // Entries per page for daily summary

// Content-Type header for API requests.
@Field static final String CONTENT_TYPE = 'application/json'

// HVAC mode constants.
// COOLING/HEATING are provided by DabManager library
// Pending HVAC mode values returned by the thermostat.
@Field static final String PENDING_COOL = 'pending cool'
@Field static final String PENDING_HEAT = 'pending heat'

// Delay (in milliseconds) before re-reading temperature after an HVAC event.
@Field static final Integer TEMP_READINGS_DELAY_MS = 30000  // 30 seconds

// Minimum and maximum vent open percentages (in %).
@Field static final BigDecimal MIN_PERCENTAGE_OPEN = 0.0
@Field static final BigDecimal MAX_PERCENTAGE_OPEN = 100.0

// Maximum number of standard (non-Flair) vents allowed.

// Threshold (in °C) used to trigger a pre-adjustment of vent settings before the setpoint is reached.
// VENT_PRE_ADJUST_THRESHOLD provided by DabManager library

// HVAC timing constants.
// MAX/MIN_MINUTES_TO_SETPOINT provided by DabManager library

// Temperature offset (in °C) applied to thermostat setpoints.
// SETPOINT_OFFSET provided by DabManager library

// Acceptable temperature change rate limits (in °C per minute).
// MAX/MIN_TEMP_CHANGE_RATE provided by DabManager library

// Temperature sensor accuracy and noise filtering
// TEMP_SENSOR_ACCURACY / MIN_DETECTABLE_TEMP_CHANGE / MIN_RUNTIME_FOR_RATE_CALC provided by DabManager library

// Minimum combined vent airflow percentage across all vents (to ensure proper HVAC operation).
// MIN_COMBINED_VENT_FLOW provided by DabManager library

// INCREMENT_PERCENTAGE is used as a base multiplier when incrementally increasing vent open percentages
// during airflow adjustments. For example, if the computed proportion for a vent is 0.5,
// then the vent's open percentage will be increased by 1.5 * 0.5 = 0.75% in that iteration.
// This increment is applied repeatedly until the total combined airflow meets the minimum target.
// INCREMENT_PERCENTAGE provided by DabManager library

// Maximum number of standard (non-Flair) vents allowed.
@Field static final Integer MAX_STANDARD_VENTS = 15

// Maximum number of standard (non-Flair) vents allowed.

// Maximum iterations for the while-loop when adjusting vent openings.
// MAX_ITERATIONS provided by DabManager library
// Quick controls verification timing
@Field static final Integer VENT_VERIFY_DELAY_MS = 5000
@Field static final Integer MAX_VENT_VERIFY_ATTEMPTS = 3

// HTTP timeout for API requests (in seconds).
@Field static final Integer HTTP_TIMEOUT_SECS = 5

// Default opening percentage for standard (non-Flair) vents (in %).
@Field static final Integer STANDARD_VENT_DEFAULT_OPEN = 50

// Thermostat hysteresis to prevent cycling (in °C).

// Temperature tolerance for rebalancing vent operations (in °C).
// REBALANCING_TOLERANCE provided by DabManager library

// Temperature boundary adjustment for airflow calculations (in °C).
// TEMP_BOUNDARY_ADJUSTMENT provided by DabManager library
// Thermostat hysteresis to prevent cycling (in C).
@Field static final BigDecimal THERMOSTAT_HYSTERESIS = 0.6  // ~1F

// Thermostat hysteresis to prevent cycling (in °C).

// Minimum average difference between duct and room temperature (in °C)
// required to determine that the HVAC system is actively heating or cooling.
// DUCT_TEMP_DIFF_THRESHOLD provided by DabManager library

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
// ADAPTIVE_* provided by DabManager library

// Raw data cache defaults and fallbacks
// RAW_CACHE_* and DEFAULT_*_SETPOINT_C provided by DabManager library

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
    // Add CSS for status messages
    section {
      paragraph getConsolidatedCSS()
    }
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
        paragraph "<span class='error-message'>${validation.errors.clientId}</span>"
      }
      if (validation.errors.clientSecret) {
        paragraph "<span class='error-message'>${validation.errors.clientSecret}</span>"
      }
      if (settings?.clientId && settings?.clientSecret) {
        if (!state.flairAccessToken && !state.authInProgress) {
          state.authInProgress = true
          state.remove('authError')  // Clear any previous error when starting new auth
          runIn(2, 'autoAuthenticate')
        }
      if (state.flairAccessToken && !state.authError) {
          paragraph "<span class='success-message'>Authenticated successfully</span>"
        } else if (state.authError && !state.authInProgress) {
          section {
            paragraph "<span class='error-message'>${state.authError}</span>"
            input name: 'retryAuth', type: 'button', title: 'Retry Authentication', submitOnChange: true
            paragraph "<small>If authentication continues to fail, verify your credentials are correct and try again.</small>"
          }
        } else if (state.authInProgress) {
          paragraph "<span class='info-message'>Authenticating... Please wait.</span>"
          paragraph "<small>This may take 10-15 seconds. The page will refresh automatically when complete.</small>"
        } else {
          paragraph "<span class='info-message'>Ready to authenticate...</span>"
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
        input name: 'pollingIntervalActive', type: 'number', title: 'Active HVAC polling interval (minutes)', defaultValue: 1, submitOnChange: true
        input name: 'pollingIntervalIdle', type: 'number', title: 'Idle HVAC polling interval (minutes)', defaultValue: 10, submitOnChange: true
      }
      if (state.ventOpenDiscrepancies) {
        section('Vent Synchronization Issues') {
          state.ventOpenDiscrepancies.each { id, info ->
            paragraph "<span style='color: red;'>${info.name ?: id} expected ${info.target}% but reported ${info.actual}%</span>"
          }
        }
// Close discrepancies block before proceeding to DAB section
      }
// Removed stray brace to fix if/else structure

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
          input name: 'dabHistoryRetentionDays', type: 'number', title: 'DAB history retention (days)', defaultValue: DEFAULT_HISTORY_RETENTION_DAYS, submitOnChange: true

          if (settings.dabHistoryRetentionDays && settings.dabHistoryRetentionDays < 1) {
            app.updateSetting('dabHistoryRetentionDays', 1)
          }
// Mirror to atomicState for CI-safe access in methods
          try { atomicState.dabHistoryRetentionDays = (settings?.dabHistoryRetentionDays ?: DEFAULT_HISTORY_RETENTION_DAYS) as Integer } catch (ignore) { }
      if (settings.thermostat1AdditionalStandardVents < 0) {
            app.updateSetting('thermostat1AdditionalStandardVents', 0)
          } else if (settings.thermostat1AdditionalStandardVents > MAX_STANDARD_VENTS) {
            app.updateSetting('thermostat1AdditionalStandardVents', MAX_STANDARD_VENTS)
          }
      if (!getThermostat1Mode() || getThermostat1Mode() == 'auto') {
            patchStructureData([mode: 'manual'])
            atomicState?.putAt('thermostat1Mode', 'manual')
        }
          }
// Quick Safety Limits
          section('Quick Safety Limits') {
            input name: 'allowFullClose', type: 'bool', title: 'Allow vents to fully close (0%)', defaultValue: false, submitOnChange: true
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
            } catch (ignore) { }
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
                (it.currentValue('room-cooling-rate') ?: 0) > 0 ||
                (it.currentValue('room-heating-rate') ?: 0) > 0
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
}// Simple, Hubitat-compatible control panel (no JS required)
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
          } catch (ignore) { }
          app.updateSetting("cp_room_${roomId}_sp_up", '')
        }
      if (settings?."cp_room_${roomId}_sp_down" ) {
          try {
            BigDecimal curF = setpF as BigDecimal
            patchRoomSetPoint(v, (curF - 1) as BigDecimal)
          } catch (ignore) { }
          app.updateSetting("cp_room_${roomId}_sp_down", '')
        }

def sel = settings?."cp_room_${roomId}_active"
        if (sel != null && sel != "") {
          try { patchRoom(v, sel) } catch (ignore) { }
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
        try { state.diagnosticsJson = buildDiagnosticsJson() } catch (ignore) { state.diagnosticsJson = '{}' }
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
        try { state.rawCacheJson = buildRawCacheJson() } catch (ignore) { state.rawCacheJson = '{}' }
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
}// New, styled control panel compatible with Hubitat pages (no JS required)
def flairControlPanel2() {
  dynamicPage(name: 'flairControlPanel2', title: 'Flair Control Panel', install: false, uninstall: false) {
    section {
      paragraph getConsolidatedCSS()
    }

def vents = getChildDevices()?.findAll { it.hasAttribute('percent-open') } ?: []
    if (!vents) {
      section { paragraph 'No vents available. Run discovery from the main page.' }
      return
    }

def rooms = [:]
    vents.each { dv ->
      def rid = dv.currentValue('room-id') ?: dv.getDeviceNetworkId()
      (rooms[rid] = (rooms[rid] ?: []) ) << dv
    }    rooms.each { roomId, list ->
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
          try { if (setpF != '-') { patchRoomSetPoint(v, ((setpF as BigDecimal) + 1) as BigDecimal) } } catch (ignore) { }
          app.updateSetting("cp2_room_${roomId}_sp_up", '')
        }
      if (settings?."cp2_room_${roomId}_sp_down") {
          try { if (setpF != '-') { patchRoomSetPoint(v, ((setpF as BigDecimal) - 1) as BigDecimal) } } catch (ignore) { }
          app.updateSetting("cp2_room_${roomId}_sp_down", '')
        }

def sel = settings?."cp2_room_${roomId}_active"
        if (sel != null && sel != "") {
          try { patchRoom(v, sel) } catch (ignore) { }
          app.updateSetting("cp2_room_${roomId}_active", '')
        }
      }
    }
    section { href name: 'backToMain', title: 'Back to Main', description: 'Return to main settings', page: 'mainPage' }
  }
}// Backend helper for future client use (JSON string)
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
  try { return groovy.json.JsonOutput.toJson(out) } catch (ignore) { return '[]' }
}

def performHealthCheck() {
  def results = []
  results << (state.flairAccessToken ? 'Auth token present' : 'Auth token missing')
  
  def ventCount = getChildDevices().findAll { it.hasAttribute('percent-open') }.size()
  results << "Vents discovered: ${ventCount}"
  
  // Perform async health check if we have token
  if (state.flairAccessToken) {
    performHealthCheckAsync()
    results << "API health check initiated (async)"
  } else {
    results << "API health check skipped (no token)"
  }
  
  state.healthCheckResults = [
    timestamp: new Date().format('yyyy-MM-dd HH:mm:ss', location.timeZone ?: TimeZone.getTimeZone('UTC')),
    results: results
  ]
}

def performHealthCheckAsync() {
  // Rate limiting: only allow health check once per minute
  def lastHealthCheck = atomicState.lastHealthCheckTime ?: 0
  def timeSinceLastCheck = getCurrentTime() - lastHealthCheck
  def minHealthCheckInterval = 60000 // 1 minute
  
  if (timeSinceLastCheck < minHealthCheckInterval) {
    log(3, 'HealthCheck', "Health check rate limited - last check ${timeSinceLastCheck / 1000}s ago")
    return
  }
  
  if (!canMakeRequest()) {
    log(2, 'HealthCheck', 'Cannot make health check request - too many active requests')
    return
  }
  
  atomicState.lastHealthCheckTime = getCurrentTime()
  
  // Use unified retry helper
  def httpParams = [
    uri: "${BASE_URL}/api/structures",
    headers: [Authorization: "Bearer ${state.flairAccessToken}"],
    timeout: HTTP_TIMEOUT_SECS,
    contentType: CONTENT_TYPE
  ]
  
  retryAsyncHttpRequest('get', httpParams, 'handleHealthCheckResponse', [:], 0, 3) // Max 3 retries for health check
}

def handleHealthCheckResponse(resp, data) {
  decrementActiveRequests()
  try {
    def currentResults = state.healthCheckResults?.results ?: []
    if (isValidResponse(resp)) {
      currentResults << "API reachable: HTTP ${resp.status}"
      log(3, 'HealthCheck', "API health check successful: HTTP ${resp.status}")
    } else {
      currentResults << "API error: HTTP ${resp.status ?: 'unknown'}"
      log(2, 'HealthCheck', "API health check failed: HTTP ${resp.status ?: 'unknown'}")
    }
    
    // Update health check results
    if (state.healthCheckResults) {
      state.healthCheckResults.results = currentResults
    }
  } catch (Exception e) {
    log(1, 'HealthCheck', "Health check response handling failed: ${e.message}")
  }
}

def resetCaches() {
  def instanceId = getInstanceId()
  def cacheKey = "instanceCache_${instanceId}"
  ['roomCache', 'roomCacheTimestamps', 'deviceCache', 'deviceCacheTimestamps',
   'pendingRoomRequests', 'pendingDeviceRequests', 'initialized'].each { suffix ->
    state.remove("${cacheKey}_${suffix}")
  }
  log 'Instance caches cleared', 2
}

// Internal diagnostics method returning JSON summary
def getDiagnosticsSummary() {
  try {
    def summary = [:]
    def now = getCurrentTime()
    
    // Request management
    summary.requestManagement = [
      activeRequests: atomicState.activeRequests ?: 0,
      lastCallbackTime: atomicState.lastCallbackTime ?: 0,
      timeSinceLastCallback: now - (atomicState.lastCallbackTime ?: now),
      maxConcurrentRequests: MAX_CONCURRENT_REQUESTS,
      apiCallDelayMs: API_CALL_DELAY_MS,
      maxRetryAttempts: MAX_API_RETRY_ATTEMPTS
    ]
    
    // Diagnostics counters
    def diagnostics = atomicState.diagnostics ?: [:]
    summary.diagnostics = [
      hardResets: diagnostics.hardResets ?: 0,
      stuckWarnings: diagnostics.stuckWarnings ?: 0,
      lastHardReset: diagnostics.lastHardReset ?: 0,
      lastStuckWarning: diagnostics.lastStuckWarning ?: 0
    ]
    
    // Cache status
    def instanceId = getInstanceId()
    def cacheKey = "instanceCache_${instanceId}"
    def roomCache = state."${cacheKey}_roomCache" ?: [:]
    def deviceCache = state."${cacheKey}_deviceCache" ?: [:]
    summary.caching = [
      instanceId: instanceId,
      roomCacheEntries: roomCache.size(),
      deviceCacheEntries: deviceCache.size(),
      roomCacheDurationMs: ROOM_CACHE_DURATION_MS,
      deviceCacheDurationMs: DEVICE_CACHE_DURATION_MS,
      maxCacheSize: MAX_CACHE_SIZE
    ]
    
    // Device status
    def children = getChildDevices()
    def vents = children.findAll { it.hasAttribute('percent-open') }
    def pucks = children.findAll { it.hasAttribute('temperature') && !it.hasAttribute('percent-open') }
    summary.devices = [
      totalChildren: children.size(),
      vents: vents.size(),
      pucks: pucks.size(),
      ventIds: vents.collect { it.getId() },
      puckIds: pucks.collect { it.getId() }
    ]
    
    // HVAC state
    summary.hvacState = [
      thermostatState: atomicState.thermostat1State ?: [:],
      ductTempsEnabled: settings?.enableDuctBasedHvacDetection == true,
      dabEnabled: settings?.dabEnabled == true,
      currentHvacMode: getHvacMode()
    ]
    
    // App info
    summary.appInfo = [
      version: '0.240',
      libraryVersions: [
        dabManager: '0.240.0',
        dabUIManager: '0.240.0'
      ],
      lastUpdated: now,
      authStatus: state.flairAccessToken ? 'authenticated' : 'not_authenticated'
    ]
    
    // Settings summary (no sensitive data)
    summary.settings = [
      pollingIntervalActive: atomicState.pollingIntervalActive ?: POLLING_INTERVAL_ACTIVE,
      pollingIntervalIdle: atomicState.pollingIntervalIdle ?: POLLING_INTERVAL_IDLE,
      dabHistoryRetentionDays: atomicState.dabHistoryRetentionDays ?: DEFAULT_HISTORY_RETENTION_DAYS,
      dashboardTilesEnabled: atomicState.enableDashboardTiles == true,
      nightOverrideActive: isNightOverrideActive()
    ]
    
    return groovy.json.JsonOutput.toJson(summary)
  } catch (Exception e) {
    return groovy.json.JsonOutput.toJson([error: "Failed to generate diagnostics: ${e?.message}"])
  }
}

// Consolidated CSS helper
def getConsolidatedCSS() {
  return """
    <style>
      /* Flair Control Panel Styles */
      .flair-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(320px,1fr));gap:12px}
      .room-card{background:#f9f9f9;border-radius:8px;box-shadow:0 1px 3px rgba(0,0,0,.12);padding:12px;border-left:5px solid #9ca3af}
      .room-card.cooling{border-left-color:#3b82f6}
      .room-card.heating{border-left-color:#f59e0b}
      .room-head{display:flex;justify-content:space-between;align-items:center;margin-bottom:8px}
      .room-name{font-weight:600}
      .room-meta{font-size:12px;color:#374151}
      .vent-item{font-size:12px;color:#111}
      
      /* Device Table Styles */
      .device-table { width: 100%; border-collapse: collapse; font-family: Arial, sans-serif; color: black; }
      .device-table th, .device-table td { padding: 8px; text-align: left; border-bottom: 1px solid #ddd; }
      .device-table th { background-color: #f2f2f2; color: #333; }
      .device-table tr:hover { background-color: #f5f5f5; }
      .device-table a { color: #333; text-decoration: none; }
      .device-table a:hover { color: #666; }
      .device-table th:not(:first-child), .device-table td:not(:first-child) { text-align: center; }
      
      /* Standard Table Styles */
      .standard-table { width: 100%; border-collapse: collapse; font-family: Arial, sans-serif; }
      .standard-table th { text-align: left; padding: 4px; background-color: #f2f2f2; font-weight: bold; }
      .standard-table td { text-align: left; padding: 4px; border-bottom: 1px solid #eee; }
      .standard-table .right-align { text-align: right; }
      
      /* Status Messages */
      .warning-message { color: darkorange; cursor: pointer; }
      .danger-message { color: red; cursor: pointer; }
      .success-message { color: green; }
      .info-message { color: orange; }
      .error-message { color: red; }
      
      /* Accessibility improvements */
      [role="button"] { cursor: pointer; }
      [role="button"]:hover { opacity: 0.8; }
      [role="button"]:focus { outline: 2px solid #007cba; outline-offset: 2px; }
    </style>
  """
}

// HTML table generation helper
def generateHtmlTable(Map options) {
  def html = new StringBuilder()
  
  // Table options with defaults
  def tableClass = options.tableClass ?: 'standard-table'
  def headers = options.headers ?: []
  def rows = options.rows ?: []
  def title = options.title
  def pagination = options.pagination
  
  // Add title if provided
  if (title) {
    html << "<h3>${title}</h3>"
  }
  
  // Add pagination info if provided
  if (pagination) {
    html << "<p>Page ${pagination.current} of ${pagination.total}</p>"
  }
  
  // Start table
  html << "<table class='${tableClass}'"
  if (options.id) html << " id='${options.id}'"
  html << ">"
  
  // Add headers
  if (headers) {
    html << "<thead><tr>"
    headers.each { header ->
      def headerClass = header.class ?: ''
      def headerAlign = header.align ? "class='${header.align}'" : ''
      html << "<th ${headerAlign}>${header.text ?: header}</th>"
    }
    html << "</tr></thead>"
  }
  
  // Add rows
  html << "<tbody>"
  rows.each { row ->
    html << "<tr>"
    if (row instanceof List) {
      // Simple list of cell values
      row.each { cell ->
        html << "<td>${cell}</td>"
      }
    } else if (row instanceof Map) {
      // Map with cell data and optional classes
      row.cells?.each { cell ->
        def cellClass = cell.class ? "class='${cell.class}'" : ''
        def cellAlign = cell.align ? "class='${cell.align}'" : ''
        html << "<td ${cellClass} ${cellAlign}>${cell.value ?: cell}</td>"
      }
    }
    html << "</tr>"
  }
  html << "</tbody></table>"
  
  return html.toString()
}

// Remove inline JavaScript helper - replaces onclick with data attributes
def sanitizeHtmlForAccessibility(String htmlContent) {
  // Replace onclick with data-href for CSS-only styling
  def sanitized = htmlContent
    .replaceAll(/onclick="window\.open\('([^']+)'\);"/, 'data-href="$1" role="button" tabindex="0"')
    .replaceAll(/onclick="([^"]+)"/, 'data-action="$1" role="button" tabindex="0"')
  
  return sanitized
}

// Degree symbol standardization helper
def standardizeDegreeSymbols(String text) {
  return text.replaceAll(/°C/, '°C').replaceAll(/°F/, '°F').replaceAll(/&deg;/, '°')
}

// Structured HTTP error classification
def classifyHttpError(def response) {
  if (!response) {
    return [
      category: 'no_response',
      severity: 'high',
      message: 'No response received',
      retryable: true,
      backoffMultiplier: 2.0
    ]
  }
  
  def status = response.getStatus() as Integer
  switch (status) {
    case 200..299:
      return [
        category: 'success',
        severity: 'none',
        message: "Success: HTTP ${status}",
        retryable: false,
        backoffMultiplier: 1.0
      ]
    
    case 400:
      return [
        category: 'client_error',
        severity: 'high',
        message: 'Bad request - check parameters',
        retryable: false,
        backoffMultiplier: 1.0
      ]
    
    case 401:
      return [
        category: 'auth_error',
        severity: 'high',
        message: 'Unauthorized - check credentials',
        retryable: true,
        backoffMultiplier: 1.5,
        shouldReauth: true
      ]
    
    case 403:
      return [
        category: 'permission_error',
        severity: 'high',
        message: 'Forbidden - insufficient permissions',
        retryable: false,
        backoffMultiplier: 1.0
      ]
    
    case 404:
      return [
        category: 'not_found',
        severity: 'medium',
        message: 'Resource not found',
        retryable: false,
        backoffMultiplier: 1.0
      ]
    
    case 429:
      return [
        category: 'rate_limit',
        severity: 'medium',
        message: 'Rate limited - slow down requests',
        retryable: true,
        backoffMultiplier: 3.0
      ]
    
    case 500..599:
      return [
        category: 'server_error',
        severity: 'medium',
        message: "Server error: HTTP ${status}",
        retryable: true,
        backoffMultiplier: 2.0
      ]
    
    default:
      return [
        category: 'unknown_error',
        severity: 'medium',
        message: "Unknown error: HTTP ${status}",
        retryable: true,
        backoffMultiplier: 1.5
      ]
  }
}

// Cache size limiting helper
def limitCacheSize(String cacheKey, Integer maxSize = MAX_CACHE_SIZE) {
  try {
    def cache = state."${cacheKey}" ?: [:]
    if (cache.size() > maxSize) {
      // Remove oldest entries (assuming keys are ordered chronologically or use LRU strategy)
      def keysToRemove = cache.keySet().take(cache.size() - maxSize)
      keysToRemove.each { key ->
        cache.remove(key)
        // Also remove corresponding timestamp cache if it exists
        def timestampKey = "${cacheKey}Timestamps"
        if (state."${timestampKey}") {
          state."${timestampKey}".remove(key)
        }
      }
      state."${cacheKey}" = cache
      log(3, 'Cache', "Limited ${cacheKey} size to ${maxSize} entries (removed ${keysToRemove.size()})")
    }
  } catch (Exception e) {
    log(4, 'Cache', "Failed to limit cache size for ${cacheKey}: ${e?.message}")
  }
}

// Ensure cache expiration uses getCurrentTime()/now() consistently
def isExpired(Long timestamp, Long durationMs) {
  if (!timestamp || !durationMs) return true
  return (getCurrentTime() - timestamp) > durationMs
}

// Self-check method for forbidden tokens and patterns
def performSelfCheck() {
  def results = [:]
  def warnings = []
  def errors = []
  
  try {
    // Check for forbidden synchronous HTTP patterns
    def sourceCode = this.class.getDeclaredMethods().collect { it.toString() }.join(' ')
    
    // Forbidden patterns to check for
    def forbiddenPatterns = [
      'httpGet': 'Use asynchttpGet instead of synchronous httpGet',
      'httpPost': 'Use asynchttpPost instead of synchronous httpPost',
      'httpPut': 'Use asynchttpPut instead of synchronous httpPut',
      'httpDelete': 'Use asynchttpDelete instead of synchronous httpDelete',
      'Thread.sleep': 'Use runIn/runInMillis instead of Thread.sleep',
      'wait()': 'Use scheduled methods instead of wait()',
      'notify()': 'Avoid notify() in Hubitat apps',
      'synchronized': 'Avoid synchronized blocks in Hubitat apps'
    ]
    
    forbiddenPatterns.each { pattern, message ->
      // This is a simplified check - in reality we'd need more sophisticated parsing
      if (sourceCode.contains(pattern)) {
        warnings << "${message} (pattern: ${pattern})"
      }
    }
    
    // Check atomicState usage patterns
    def atomicStateKeys = atomicState.keySet()
    atomicStateKeys.each { key ->
      def value = atomicState."${key}"
      if (value instanceof BigDecimal) {
        warnings << "atomicState.${key} contains BigDecimal - consider converting to Double"
      }
    }
    
    // Check for proper request counter management
    def activeRequests = atomicState.activeRequests ?: 0
    if (activeRequests < 0) {
      errors << "Active request counter is negative: ${activeRequests}"
    }
    if (activeRequests > MAX_CONCURRENT_REQUESTS * 2) {
      warnings << "Active request counter unusually high: ${activeRequests}"
    }
    
    // Check cache sizes
    def instanceId = getInstanceId()
    def base = "instanceCache_${instanceId}"
    def roomCache = state."${base}_roomCache" ?: [:]
    def deviceCache = state."${base}_deviceCache" ?: [:]
    
    if (roomCache.size() > MAX_CACHE_SIZE) {
      warnings << "Room cache oversized: ${roomCache.size()} entries"
    }
    if (deviceCache.size() > MAX_CACHE_SIZE) {
      warnings << "Device cache oversized: ${deviceCache.size()} entries"
    }
    
    // Check for stale caches
    def now = getCurrentTime()
    def roomTimestamps = state."${base}_roomCacheTimestamps" ?: [:]
    def deviceTimestamps = state."${base}_deviceCacheTimestamps" ?: [:]
    
    def oldRoomEntries = roomTimestamps.findAll { key, timestamp ->
      (now - timestamp) > (ROOM_CACHE_DURATION_MS * 10) // 10x normal duration
    }.size()
    
    def oldDeviceEntries = deviceTimestamps.findAll { key, timestamp ->
      (now - timestamp) > (DEVICE_CACHE_DURATION_MS * 10) // 10x normal duration
    }.size()
    
    if (oldRoomEntries > 0) {
      warnings << "${oldRoomEntries} very old room cache entries detected"
    }
    if (oldDeviceEntries > 0) {
      warnings << "${oldDeviceEntries} very old device cache entries detected"
    }
    
    results.status = errors.isEmpty() ? 'passed' : 'failed'
    results.errors = errors
    results.warnings = warnings
    results.timestamp = now
    results.summary = "Self-check ${results.status} with ${errors.size()} errors and ${warnings.size()} warnings"
    
  } catch (Exception e) {
    results.status = 'error'
    results.errors = ["Self-check failed: ${e?.message}"]
    results.warnings = warnings
    results.timestamp = getCurrentTime()
  }
  
  return results
}
def cleanupExpiredCaches() {
  try {
    def instanceId = getInstanceId()
    def base = "instanceCache_${instanceId}"
    def now = getCurrentTime()
    
    // Clean room cache
    def roomCache = state."${base}_roomCache" ?: [:]
    def roomTimestamps = state."${base}_roomCacheTimestamps" ?: [:]
    def expiredRoomKeys = roomTimestamps.findAll { key, timestamp ->
      isExpired(timestamp, ROOM_CACHE_DURATION_MS)
    }.keySet()
    
    expiredRoomKeys.each { key ->
      roomCache.remove(key)
      roomTimestamps.remove(key)
    }
    
    if (expiredRoomKeys) {
      state."${base}_roomCache" = roomCache
      state."${base}_roomCacheTimestamps" = roomTimestamps
      log(3, 'Cache', "Cleaned ${expiredRoomKeys.size()} expired room cache entries")
    }
    
    // Clean device cache
    def deviceCache = state."${base}_deviceCache" ?: [:]
    def deviceTimestamps = state."${base}_deviceCacheTimestamps" ?: [:]
    def expiredDeviceKeys = deviceTimestamps.findAll { key, timestamp ->
      isExpired(timestamp, DEVICE_CACHE_DURATION_MS)
    }.keySet()
    
    expiredDeviceKeys.each { key ->
      deviceCache.remove(key)
      deviceTimestamps.remove(key)
    }
    
    if (expiredDeviceKeys) {
      state."${base}_deviceCache" = deviceCache
      state."${base}_deviceCacheTimestamps" = deviceTimestamps
      log(3, 'Cache', "Cleaned ${expiredDeviceKeys.size()} expired device cache entries")
    }
    
    // Limit HTML cache sizes
    limitCacheSize('dabRatesTableHtml', 10)
    limitCacheSize('dabProgressTableHtml', 10)
    
  } catch (Exception e) {
    log(4, 'Cache', "Cache cleanup error: ${e?.message}")
  }
}

// ------------------------------
// List and Device Discovery Functions
// ------------------------------
def listDiscoveredDevices() {
  final String acBoosterLink = 'https://amzn.to/3QwVGbs'
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

  // Prepare table data
  def headers = [
    [text: 'Device', align: 'left'],
    [text: 'Cooling Efficiency', align: 'center'],
    [text: 'Heating Efficiency', align: 'center']
  ]
  
  def rows = []
  vents.each { vent ->
    def coolRate = vent.currentValue('room-cooling-rate') ?: 0
    def heatRate = vent.currentValue('room-heating-rate') ?: 0
    def coolEfficiency = maxCoolEfficiency > 0 ? roundBigDecimal((coolRate / maxCoolEfficiency) * 100, 0) : 0
    def heatEfficiency = maxHeatEfficiency > 0 ? roundBigDecimal((heatRate / maxHeatEfficiency) * 100, 0) : 0
    def warnMsg = 'This vent is very inefficient, consider installing an HVAC booster.'
    
    def coolClass = coolEfficiency <= 25 ? 'danger-message' : (coolEfficiency <= 45 ? 'warning-message' : '')
    def heatClass = heatEfficiency <= 25 ? 'danger-message' : (heatEfficiency <= 45 ? 'warning-message' : '')
    
    def coolHtml = coolEfficiency <= 45 ? 
      "<span class='${coolClass}' data-href='${acBoosterLink}' title='${warnMsg}' role='button' tabindex='0'>${coolEfficiency}%</span>" : 
      "${coolEfficiency}%"
    def heatHtml = heatEfficiency <= 45 ? 
      "<span class='${heatClass}' data-href='${acBoosterLink}' title='${warnMsg}' role='button' tabindex='0'>${heatEfficiency}%</span>" : 
      "${heatEfficiency}%"
    
    rows << [
      "<a href='/device/edit/${vent.getId()}'>${vent.getLabel()}</a>",
      coolHtml,
      heatHtml
    ]
  }

  def tableHtml = generateHtmlTable([
    tableClass: 'device-table',
    headers: headers,
    rows: rows,
    title: 'Discovered Devices'
  ])

  section {
    paragraph getConsolidatedCSS()
    paragraph tableHtml
  }
}

def getStructureId() {
  if (!settings?.structureId) { getStructureData() }
  return settings?.structureId
}

def updated() {
  log.debug 'Hubitat Flair App updating'
  
  // Validate and clamp settings
  validateAndClampSettings()
  
  // Mirror settings to atomicState
  mirrorSettingsToAtomicState()
  
  // Clear cached HTML so pages rebuild after setting changes
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
  try { runEvery5Minutes('dabHealthMonitor') } catch (ignore) { }
  unsubscribe()

  // Ensure DAB history data structures exist
  initializeDabHistory()

  // Initialize instance-based caches
  initializeInstanceCaches()
  
  // Clean up any existing BigDecimal precision issues
  cleanupExistingDecimalPrecision()

  // Ensure required DAB tracking structures exist
  initializeDabTracking()
  
  // Schedule cache cleanup
  runEvery30Minutes('cleanupExpiredCaches')

  // Check if we need to auto-authenticate on startup
  if (settings?.clientId && settings?.clientSecret) {
    if (!state.flairAccessToken) {
      log(2, 'App', 'No access token found on initialization, auto-authenticating...')
      autoAuthenticate()
    } else {
      // Token exists, ensure hourly refresh is scheduled
      unschedule('login')
      runEvery1Hour('login')
    }
  }
// HVAC state will be updated after each vent refresh; compute initial state now
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
    } catch (e) { log(2, 'App', "Raw cache scheduler error: ${e?.message}") }
// Also subscribe to thermostat events as a fallback when duct temps are not available
    try {
      if (settings?.thermostat1) {
        subscribe(settings.thermostat1, 'thermostatOperatingState', 'thermostat1ChangeStateHandler')
        subscribe(settings.thermostat1, 'temperature', 'thermostat1ChangeTemp')
        subscribe(settings.thermostat1, 'coolingSetpoint', 'thermostat1ChangeTemp')
        subscribe(settings.thermostat1, 'heatingSetpoint', 'thermostat1ChangeTemp')
      }
    } catch (e) {
      log(2, 'App', "Thermostat subscription error: ${e?.message}")
    }
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
  runEvery15Minutes('activeRequestsWatchdog')

  // Schedule/subscribe for tiles and overrides
  if (settings?.enableDashboardTiles) {
    try {
      subscribeToVentEventsForTiles()
      runEvery5Minutes('refreshVentTiles')
    } catch (e) { log(2, 'App', "Tile scheduler/subscription error: ${e?.message}") }
  } else {
    try { unschedule('refreshVentTiles') } catch (ignore) { }
  }
      if (settings?.nightOverrideEnable) {
    try {
      if (settings?.nightOverrideStart) { schedule(settings.nightOverrideStart, 'activateNightOverride') }
      if (settings?.nightOverrideEnd) { schedule(settings.nightOverrideEnd, 'deactivateNightOverride') }
    } catch (e) { log(2, 'App', "Night override scheduling error: ${e?.message}") }
  } else {
    try { unschedule('activateNightOverride'); unschedule('deactivateNightOverride') } catch (ignore) { }
  }
}// ------------------------------
// Helper Functions
// ------------------------------

// Centralized logging wrappers supporting two call styles:
// 1) log(level, domain, message, [ref])
// 2) log("message", level)
def log(Integer level, String domain, String message, Object ref = null) {
  try {
    // Honor debugLevel setting - only log messages at or below the configured level
    Integer configuredLevel = (settings?.debugLevel ?: 0) as Integer
    if (level > 0 && level > configuredLevel && configuredLevel != 1) {
      return // Suppress higher-level debug messages (except level 1 shows all)
    }
    
    String prefix = domain ? "[${domain}] " : ''
    String suffix = ref ? " | ${ref}" : ''
    String msg = "${prefix}${message}${suffix}"
    switch(level) {
      case 4: this.log.trace(msg); break
      case 3: this.log.debug(msg); break
      case 2: this.log.info(msg); break
      case 1: this.log.warn(msg); break
      default: this.log.error(msg); break
    }
  } catch (ignored) { }
}

def log(String message, Integer level = 2) {
  try {
    // Honor debugLevel setting
    Integer configuredLevel = (settings?.debugLevel ?: 0) as Integer
    if (level > 0 && level > configuredLevel && configuredLevel != 1) {
      return // Suppress higher-level debug messages (except level 1 shows all)
    }
    
    switch(level) {
      case 4: this.log.trace(message); break
      case 3: this.log.debug(message); break
      case 2: this.log.info(message); break
      case 1: this.log.warn(message); break
      default: this.log.error(message); break
    }
  } catch (ignored) { }
}

def logWarn(String message, String domain = null) {
  try { this.log.warn("${domain ? '['+domain+'] ' : ''}${message}") } catch (ignored) { }
}

def logError(String message, String domain = null, Object ref = null) {
  try {
    String prefix = domain ? "[${domain}] " : ''
    String suffix = ref ? " | ${ref}" : ''
    this.log.error("${prefix}${message}${suffix}")
  } catch (ignored) { }
}

// Instance-Scoped Cache Helpers
def getInstanceId() {
  try {
    return (app?.id ?: app?.getId() ?: this?.id ?: this?.getId() ?: 'default') as String
  } catch (ignored) {
    return 'default'
  }
}

def getCurrentTime() {
  try { return now() } catch (ignored) { return new Date().getTime() }
}

def initializeInstanceCaches() {
  try {
    def instanceId = getInstanceId()
    def base = "instanceCache_${instanceId}"
    state."${base}_roomCache" = (state."${base}_roomCache" ?: [:])
    state."${base}_roomCacheTimestamps" = (state."${base}_roomCacheTimestamps" ?: [:])
    state."${base}_deviceCache" = (state."${base}_deviceCache" ?: [:])
    state."${base}_deviceCacheTimestamps" = (state."${base}_deviceCacheTimestamps" ?: [:])
    state."${base}_pendingRoomRequests" = (state."${base}_pendingRoomRequests" ?: [:])
    state."${base}_pendingDeviceRequests" = (state."${base}_pendingDeviceRequests" ?: [:])
    state."${base}_initialized" = true
  } catch (ignored) { }
}

def clearPendingRequest(String roomId) {
  try {
    def base = "instanceCache_${getInstanceId()}"
    def pending = state."${base}_pendingRoomRequests" ?: [:]
    pending[roomId] = false
    state."${base}_pendingRoomRequests" = pending
  } catch (ignored) { }
}

def clearDeviceRequestPending(String cacheKey) {
  try {
    def base = "instanceCache_${getInstanceId()}"
    def pending = state."${base}_pendingDeviceRequests" ?: [:]
    pending[cacheKey] = false
    state."${base}_pendingDeviceRequests" = pending
  } catch (ignored) { }
}

def incrementActiveRequests() {
  try {
    Integer cur = (atomicState?.activeRequests ?: 0) as Integer
    atomicState.activeRequests = cur + 1
  } catch (Exception e) { 
    log(4, 'Request', "Failed to increment active requests: ${e.message}")
  }
}

def decrementActiveRequests() {
  try {
    Integer cur = (atomicState?.activeRequests ?: 0) as Integer
    Integer newVal = Math.max(0, cur - 1)
    atomicState.activeRequests = newVal
    
    // Record callback time for watchdog
    atomicState.lastCallbackTime = getCurrentTime()
    
    // Log if we had to clamp to prevent negative
    if (cur < 1 && newVal == 0) {
      log(3, 'Request', "Active request counter was already at ${cur}, clamped to 0")
    }
  } catch (Exception e) { 
    log(4, 'Request', "Failed to decrement active requests: ${e.message}")
  }
}

// Concurrency gate for async HTTP
def canMakeRequest() {
  try { return (atomicState?.activeRequests ?: 0) < (MAX_CONCURRENT_REQUESTS ?: 4) } catch (ignored) { return true }
}

// Unified retry/backoff helper for async HTTP requests
def retryAsyncHttpRequest(String method, Map httpParams, String callbackMethod, Map callbackData = [:], Integer retryCount = 0, Integer maxRetries = MAX_API_RETRY_ATTEMPTS) {
  if (retryCount >= maxRetries) {
    logError "${method.toUpperCase()} ${httpParams.uri} failed after ${maxRetries} retries"
    return false
  }
  
  if (!canMakeRequest()) {
    // If we can't make request now, schedule retry with exponential backoff
    def backoffDelay = API_CALL_DELAY_MS * Math.pow(2, retryCount)
    def maxBackoff = 30000 // Cap at 30 seconds
    def actualDelay = Math.min(backoffDelay as Long, maxBackoff)
    
    log(3, 'HTTP', "${method.toUpperCase()} ${httpParams.uri} delayed ${actualDelay}ms (retry ${retryCount + 1}/${maxRetries})")
    runInMillis(actualDelay, 'executeRetryHttpRequest', [
      method: method,
      httpParams: httpParams,
      callbackMethod: callbackMethod,
      callbackData: callbackData + [retryCount: retryCount + 1],
      maxRetries: maxRetries
    ])
    return false
  }
  
  incrementActiveRequests()
  try {
    switch (method.toLowerCase()) {
      case 'get':
        asynchttpGet(callbackMethod, httpParams, callbackData + [retryCount: retryCount])
        break
      case 'post':
        asynchttpPost(callbackMethod, httpParams, callbackData + [retryCount: retryCount])
        break
      case 'put':
        asynchttpPut(callbackMethod, httpParams, callbackData + [retryCount: retryCount])
        break
      case 'delete':
        asynchttpDelete(callbackMethod, httpParams, callbackData + [retryCount: retryCount])
        break
      default:
        throw new IllegalArgumentException("Unsupported HTTP method: ${method}")
    }
    return true
  } catch (Exception e) {
    decrementActiveRequests()
    log(2, 'HTTP', "${method.toUpperCase()} ${httpParams.uri} request exception (retry ${retryCount + 1}/${maxRetries}): ${e.message}")
    
    // Schedule retry with exponential backoff
    def backoffDelay = API_CALL_DELAY_MS * Math.pow(2, retryCount)
    def maxBackoff = 30000 // Cap at 30 seconds
    def actualDelay = Math.min(backoffDelay as Long, maxBackoff)
    
    runInMillis(actualDelay, 'executeRetryHttpRequest', [
      method: method,
      httpParams: httpParams,
      callbackMethod: callbackMethod,
      callbackData: callbackData + [retryCount: retryCount + 1],
      maxRetries: maxRetries
    ])
    return false
  }
}

// Wrapper for scheduled retry execution
def executeRetryHttpRequest(data) {
  retryAsyncHttpRequest(
    data.method,
    data.httpParams,
    data.callbackMethod,
    data.callbackData ?: [:],
    data.callbackData?.retryCount ?: 0,
    data.maxRetries ?: MAX_API_RETRY_ATTEMPTS
  )
}

// Enhanced callback timeout detection
def enhanceCallbackTimeout() {
  def timeoutMs = 900000 // 15 minutes
  def stuckThreshold = 300000 // 5 minutes for "stuck" detection
  def lastCallbackTime = atomicState.lastCallbackTime ?: getCurrentTime()
  def timeSinceLastCallback = getCurrentTime() - lastCallbackTime
  def activeRequests = atomicState.activeRequests ?: 0
  
  if (activeRequests > 0) {
    if (timeSinceLastCallback > timeoutMs) {
      // Hard reset after 15 minutes
      log(1, 'Watchdog', "CRITICAL: Hard reset ${activeRequests} stuck requests after ${timeSinceLastCallback / 60000} minutes")
      atomicState.activeRequests = 0
      
      // Record diagnostic event
      def diagnostics = atomicState.diagnostics ?: [:]
      diagnostics.hardResets = (diagnostics.hardResets ?: 0) + 1
      diagnostics.lastHardReset = getCurrentTime()
      atomicState.diagnostics = diagnostics
      
    } else if (timeSinceLastCallback > stuckThreshold) {
      // Warning for stuck requests after 5 minutes
      log(2, 'Watchdog', "WARNING: ${activeRequests} requests stuck for ${timeSinceLastCallback / 60000} minutes")
      
      def diagnostics = atomicState.diagnostics ?: [:]
      diagnostics.stuckWarnings = (diagnostics.stuckWarnings ?: 0) + 1
      diagnostics.lastStuckWarning = getCurrentTime()
      atomicState.diagnostics = diagnostics
    }
  }
}

// Settings validation and clamping helper
def validateAndClampSettings() {
  try {
    // Polling intervals
    def activeInterval = settings?.pollingIntervalActive as Integer
    if (activeInterval != null) {
      def clamped = Math.max(1, Math.min(60, activeInterval)) // 1-60 minutes
      if (clamped != activeInterval) {
        app.updateSetting('pollingIntervalActive', clamped)
        log(2, 'Settings', "Clamped active polling interval from ${activeInterval} to ${clamped} minutes")
      }
    }
    
    def idleInterval = settings?.pollingIntervalIdle as Integer
    if (idleInterval != null) {
      def clamped = Math.max(1, Math.min(120, idleInterval)) // 1-120 minutes
      if (clamped != idleInterval) {
        app.updateSetting('pollingIntervalIdle', clamped)
        log(2, 'Settings', "Clamped idle polling interval from ${idleInterval} to ${clamped} minutes")
      }
    }
    
    // Standard vent count
    def standardVents = settings?.thermostat1AdditionalStandardVents as Integer
    if (standardVents != null) {
      def clamped = Math.max(0, Math.min(MAX_STANDARD_VENTS, standardVents))
      if (clamped != standardVents) {
        app.updateSetting('thermostat1AdditionalStandardVents', clamped)
        log(2, 'Settings', "Clamped standard vents count from ${standardVents} to ${clamped}")
      }
    }
    
    // DAB history retention
    def retentionDays = settings?.dabHistoryRetentionDays as Integer
    if (retentionDays != null) {
      def clamped = Math.max(1, Math.min(365, retentionDays)) // 1-365 days
      if (clamped != retentionDays) {
        app.updateSetting('dabHistoryRetentionDays', clamped)
        log(2, 'Settings', "Clamped DAB history retention from ${retentionDays} to ${clamped} days")
      }
    }
    
    // Min vent floor percent
    def minVentFloor = settings?.minVentFloorPercent as Integer
    if (minVentFloor != null) {
      def clamped = Math.max(0, Math.min(50, minVentFloor)) // 0-50%
      if (clamped != minVentFloor) {
        app.updateSetting('minVentFloorPercent', clamped)
        log(2, 'Settings', "Clamped min vent floor from ${minVentFloor}% to ${clamped}%")
      }
    }
    
    // Night override percent
    def nightPercent = settings?.nightOverridePercent as Integer
    if (nightPercent != null) {
      def clamped = Math.max(0, Math.min(100, nightPercent)) // 0-100%
      if (clamped != nightPercent) {
        app.updateSetting('nightOverridePercent', clamped)
        log(2, 'Settings', "Clamped night override percent from ${nightPercent}% to ${clamped}%")
      }
    }
    
    // Raw data retention hours
    def rawRetention = settings?.rawDataRetentionHours as Integer
    if (rawRetention != null) {
      def clamped = Math.max(1, Math.min(168, rawRetention)) // 1-168 hours (1 week)
      if (clamped != rawRetention) {
        app.updateSetting('rawDataRetentionHours', clamped)
        log(2, 'Settings', "Clamped raw data retention from ${rawRetention} to ${clamped} hours")
      }
    }
    
    // EWMA half-life days
    def ewmaHalfLife = settings?.ewmaHalfLifeDays as Integer
    if (ewmaHalfLife != null) {
      def clamped = Math.max(1, Math.min(30, ewmaHalfLife)) // 1-30 days
      if (clamped != ewmaHalfLife) {
        app.updateSetting('ewmaHalfLifeDays', clamped)
        log(2, 'Settings', "Clamped EWMA half-life from ${ewmaHalfLife} to ${clamped} days")
      }
    }
    
    // Outlier threshold MAD
    def outlierThreshold = settings?.outlierThresholdMad as Integer
    if (outlierThreshold != null) {
      def clamped = Math.max(1, Math.min(10, outlierThreshold)) // 1-10
      if (clamped != outlierThreshold) {
        app.updateSetting('outlierThresholdMad', clamped)
        log(2, 'Settings', "Clamped outlier threshold from ${outlierThreshold} to ${clamped}")
      }
    }
    
    // Vent weight validation (clamp between 0.1 and 10.0)
    getChildDevices()?.findAll { it.hasCapability('Switch Level') }?.each { vent ->
      def weightKey = "vent${vent.getId()}Weight"
      def weight = settings?."${weightKey}" as BigDecimal
      if (weight != null) {
        def clamped = Math.max(0.1, Math.min(10.0, weight)) as BigDecimal
        if ((clamped - weight).abs() > 0.001) {
          app.updateSetting(weightKey, clamped)
          log(2, 'Settings', "Clamped ${vent.getLabel()} weight from ${weight} to ${clamped}")
        }
      }
    }
    
  } catch (Exception e) {
    log(4, 'Settings', "Settings validation error: ${e?.message}")
  }
}

// Settings to atomicState mirroring helper
def mirrorSettingsToAtomicState() {
  try {
    // Mirror key settings to atomicState for safe access from libraries
    atomicState.pollingIntervalActive = (settings?.pollingIntervalActive ?: POLLING_INTERVAL_ACTIVE) as Integer
    atomicState.pollingIntervalIdle = (settings?.pollingIntervalIdle ?: POLLING_INTERVAL_IDLE) as Integer
    atomicState.dabHistoryRetentionDays = (settings?.dabHistoryRetentionDays ?: DEFAULT_HISTORY_RETENTION_DAYS) as Integer
    atomicState.minVentFloorPercent = (settings?.minVentFloorPercent ?: 10) as Integer
    atomicState.nightOverridePercent = (settings?.nightOverridePercent ?: 100) as Integer
    atomicState.rawDataRetentionHours = (settings?.rawDataRetentionHours ?: RAW_CACHE_DEFAULT_HOURS) as Integer
    atomicState.ewmaHalfLifeDays = (settings?.ewmaHalfLifeDays ?: 3) as Integer
    atomicState.outlierThresholdMad = (settings?.outlierThresholdMad ?: 3) as Integer
    atomicState.outlierMode = settings?.outlierMode ?: 'clip'
    atomicState.enableOutlierRejection = settings?.enableOutlierRejection != false
    atomicState.useCachedRawForDab = settings?.useCachedRawForDab == true
    atomicState.carryForwardLastHour = settings?.carryForwardLastHour != false
    atomicState.enableAdaptiveBoost = settings?.enableAdaptiveBoost != false
    atomicState.adaptiveLookbackPeriods = (settings?.adaptiveLookbackPeriods ?: 3) as Integer
    atomicState.adaptiveThresholdPercent = (settings?.adaptiveThresholdPercent ?: 25) as BigDecimal
    atomicState.adaptiveBoostPercent = (settings?.adaptiveBoostPercent ?: 12.5) as BigDecimal
    atomicState.adaptiveMaxBoostPercent = (settings?.adaptiveMaxBoostPercent ?: 25) as BigDecimal
    atomicState.fanOnlyOpenAllVents = settings?.fanOnlyOpenAllVents == true
    atomicState.enableDashboardTiles = settings?.enableDashboardTiles == true
  } catch (Exception e) {
    log(4, 'Settings', "Settings mirroring error: ${e?.message}")
  }
}

// Initialize DAB tracking structures and state mirrors
def initializeDabTracking() {
  try {
    atomicState.dabEwma = atomicState.dabEwma ?: [:]
    atomicState.dabDailyStats = atomicState.dabDailyStats ?: [:]
    atomicState.dabActivityLog = atomicState.dabActivityLog ?: []
    atomicState.recentVentDecisions = atomicState.recentVentDecisions ?: []
    state.recentErrors = state.recentErrors ?: []
  } catch (ignored) { }
}

// Clean up any previously stored BigDecimals to hub-safe doubles where necessary
def cleanupExistingDecimalPrecision() {
  try {
    def fixMap = { m ->
      if (!(m instanceof Map)) return m
      m.collectEntries { k, v ->
        if (v instanceof BigDecimal) {
          [(k): (cleanDecimalForJson(v))]
        } else if (v instanceof Map) {
          [(k): fixMap(v)]
        } else if (v instanceof List) {
          [(k): v.collect { it instanceof BigDecimal ? cleanDecimalForJson(it) : it }]
        } else {
          [(k): v]
        }
      }
    }
    try { atomicState.dabEwma = fixMap(atomicState?.dabEwma ?: [:]) } catch (ignored2) { }
    try { atomicState.dabDailyStats = fixMap(atomicState?.dabDailyStats ?: [:]) } catch (ignored3) { }
  } catch (ignored) { }
}

// Instance cache helpers for room/device caching
def cacheRoomData(String roomId, def data) {
  try {
    def base = "instanceCache_${getInstanceId()}"
    def cache = state."${base}_roomCache" ?: [:]
    def ts = state."${base}_roomCacheTimestamps" ?: [:]
    cache[roomId] = data
    ts[roomId] = getCurrentTime()
    // enforce max size
    if (cache.size() > (MAX_CACHE_SIZE ?: 50)) {
      def firstKey = (cache.keySet() as List).first()
      cache.remove(firstKey)
      ts.remove(firstKey)
    }
    state."${base}_roomCache" = cache
    state."${base}_roomCacheTimestamps" = ts
  } catch (ignored) { }
}

def cacheDeviceReading(String deviceKey, def data) {
  try {
    def base = "instanceCache_${getInstanceId()}"
    def cache = state."${base}_deviceCache" ?: [:]
    def ts = state."${base}_deviceCacheTimestamps" ?: [:]
    cache[deviceKey] = data
    ts[deviceKey] = getCurrentTime()
    if (cache.size() > (MAX_CACHE_SIZE ?: 50)) {
      def firstKey = (cache.keySet() as List).first()
      cache.remove(firstKey)
      ts.remove(firstKey)
    }
    state."${base}_deviceCache" = cache
    state."${base}_deviceCacheTimestamps" = ts
  } catch (ignored) { }
}

// Response validation helper
def isValidResponse(resp) {
  try {
    if (!resp) return false
    if (resp.hasError()) return false
    def st = resp.getStatus() as int
    return (st >= 200 && st < 300)
  } catch (ignored) { return false }
}

// Async HTTP wrappers unified to a single callback
def getDataAsync(String uri, String originalCallback, Map data = [:]) {
  if (!state?.flairAccessToken) { return }
  if (!canMakeRequest()) { return }
  incrementActiveRequests()
  try {
    def httpParams = [
      uri: uri,
      headers: [Authorization: "Bearer ${state.flairAccessToken}"],
      timeout: HTTP_TIMEOUT_SECS,
      contentType: CONTENT_TYPE
    ]
    def cbData = [originalCallback: originalCallback, uri: uri]
    if (data) { cbData.putAll(data) }
    asynchttpGet('asyncHttpCallback', httpParams, cbData)
  } catch (Exception e) {
    logWarn "GET ${uri} failed: ${e?.message}", 'HTTP'
    decrementActiveRequests()
  }
}

def patchDataAsync(String uri, String originalCallback = null, Object body = null, Map data = [:]) {
  if (!state?.flairAccessToken) { return }
  if (!canMakeRequest()) { return }
  incrementActiveRequests()
  try {
    def httpParams = [
      uri: uri,
      headers: [
        Authorization: "Bearer ${state.flairAccessToken}",
        'Content-Type': CONTENT_TYPE
      ],
      body: body ? groovy.json.JsonOutput.toJson(body) : null,
      timeout: HTTP_TIMEOUT_SECS,
      contentType: CONTENT_TYPE
    ]
    def cbData = [originalCallback: (originalCallback ?: 'noOpHandler'), uri: uri]
    if (data) { cbData.putAll(data) }
    try {
      // Use POST with method override for PATCH operations
      httpParams.headers['X-HTTP-Method-Override'] = 'PATCH'
      asynchttpPost('asyncHttpCallback', httpParams, cbData)
    } catch (Exception e) {
      logWarn "PATCH ${uri} failed: ${e?.message}", 'HTTP'
      decrementActiveRequests()
    }
  } catch (Exception e) {
    logWarn "PATCH ${uri} build failed: ${e?.message}", 'HTTP'
    decrementActiveRequests()
  }
}

// Raw data sampling and pruning
def getLatestRawSample(String ventId) {
  try {
    def last = atomicState?.rawDabLastByVent ?: [:]
    return last[ventId]
  } catch (ignored) { return null }
}

def sampleRawDabData() {
  try {
    def vents = getChildDevices()?.findAll { it.hasAttribute('percent-open') } ?: []
    if (!vents) { return }
    def entries = (atomicState?.rawDabSamplesEntries ?: []) as List
    def lastByVent = (atomicState?.rawDabLastByVent ?: [:]) as Map
    def ts = now()
    vents.each { v ->
      try {
        def vid = v.getDeviceNetworkId()
        def roomId = v.currentValue('room-id') ?: v.getId()
        BigDecimal duct = (v.currentValue('duct-temperature-c') ?: 0) as BigDecimal
        BigDecimal room = (v.currentValue('room-current-temperature-c') ?: 0) as BigDecimal
        Integer pct = (v.currentValue('percent-open') ?: v.currentValue('level') ?: 0) as Integer
        def rec = [ts, roomId, vid, pct, duct, room]
        entries << rec
        lastByVent[vid] = rec
      } catch (ignored) { }
    }
    // cap entries list
    if (entries.size() > (RAW_CACHE_MAX_ENTRIES ?: 20000)) {
      entries = entries[-(RAW_CACHE_MAX_ENTRIES)..-1]
    }
    atomicState.rawDabSamplesEntries = entries
    atomicState.rawDabLastByVent = lastByVent
  } catch (ignored) { }
}

def pruneRawCache() {
  try {
    def entries = (atomicState?.rawDabSamplesEntries ?: []) as List
    int hours = (settings?.rawDataRetentionHours ?: RAW_CACHE_DEFAULT_HOURS) as int
    long cutoff = now() - (hours * 60L * 60L * 1000L)
    atomicState.rawDabSamplesEntries = entries.findAll { e -> (e[0] as Long) >= cutoff }
  } catch (ignored) { }
}

// Daily aggregation wrapper (delegates to manager if available)
def aggregateDailyDabStats() {
  try {
    initializeDabHistory()
    def tz = location?.timeZone ?: TimeZone.getTimeZone('UTC')
    String targetDate = (new Date() - 1).format('yyyy-MM-dd', tz)
    def hist = atomicState?.dabHistory ?: [:]
    def entries = (hist instanceof List) ? hist : (hist?.entries ?: [])
    Map grouped = [:]
    entries.each { e ->
      try {
        String entryDate = new Date(e[0] as Long).format('yyyy-MM-dd', tz)
        if (entryDate != targetDate) return
        String room = e[1]
        String mode = e[2]
        BigDecimal rate = e[4] as BigDecimal
        grouped[room] = grouped[room] ?: [:]
        grouped[room][mode] = (grouped[room][mode] ?: []) << rate
      } catch (ignored) { }
    }
    Map daily = atomicState?.dabDailyStats ?: [:]
    grouped.each { room, modes ->
      modes.each { mode, rates ->
        BigDecimal avg = rates.sum() / rates.size()
        daily[room] = daily[room] ?: [:]
        daily[room][mode] = [[date: targetDate, avg: avg]]
      }
    }
    atomicState.dabDailyStats = daily
  } catch (ignored) { }
}

// Activity log helper used by DabManager
def appendDabActivityLog(String msg) {
  try {
    def arr = (atomicState?.dabActivityLog ?: []) as List
    arr << (new Date().format('yyyy-MM-dd HH:mm:ss', location?.timeZone ?: TimeZone.getTimeZone('UTC')) + ' - ' + msg)
    // cap log length
    if (arr.size() > 200) { arr = arr[-200..-1] }
    atomicState.dabActivityLog = arr
  } catch (ignored) { }
}

// Misc helpers
def getRetentionDays() {
  try { return (settings?.historyRetentionDays ?: DEFAULT_HISTORY_RETENTION_DAYS) as Integer } catch (ignored) { return DEFAULT_HISTORY_RETENTION_DAYS }
}

def isDabEnabled() { try { return settings?.dabEnabled == true } catch (ignored) { return false } }

def isFanActive() {
  try {
    def st = settings?.thermostat1?.currentValue('thermostatOperatingState')
    return (st?.toString()?.toLowerCase() in ['fan only', 'fan', 'fan_only'])
  } catch (ignored) { return false }
}

def getThermostat1Mode() {
  try { return settings?.thermostat1?.currentValue('thermostatOperatingState') } catch (ignored) { }
  try { return settings?.thermostat1?.currentValue('thermostatMode') } catch (ignored2) { }
  return null
}

// Tile support
def syncVentTiles() {
  try {
    def vents = getChildDevices()?.findAll { it.hasAttribute('percent-open') } ?: []
    vents.each { v ->
      try {
        String tileDni = "tile-${v.getDeviceNetworkId()}"
        def existing = getChildDevice(tileDni)
        if (!existing) {
          addChildDevice('bot.flair', 'Flair Vent Tile', tileDni, [label: "Tile - ${v.getLabel()}", isComponent: false])
        }
      } catch (e) { logWarn("Tile create error: ${e?.message}", 'Tile') }
    }
  } catch (ignored) { }
}

def subscribeToVentEventsForTiles() {
  try {
    def vents = getChildDevices()?.findAll { it.hasAttribute('percent-open') } ?: []
    vents.each { v ->
      try {
        unsubscribe(v)
        subscribe(v, 'percent-open', 'ventEventHandler')
        subscribe(v, 'room-current-temperature-c', 'ventEventHandler')
        subscribe(v, 'room-set-point-c', 'ventEventHandler')
      } catch (ignored) { }
    }
  } catch (ignored) { }
}

def ventEventHandler(evt) { try { refreshVentTiles() } catch (ignored) { } }

def refreshVentTiles() {
  try {
    def vents = getChildDevices()?.findAll { it.hasAttribute('percent-open') } ?: []
    def tiles = getChildDevices()?.findAll { it.getTypeName() == 'Flair Vent Tile' } ?: []
    if (!tiles) { return }
    // Simple HTML: list room name, percent, temp
    vents.each { v ->
      try {
        String tileDni = "tile-${v.getDeviceNetworkId()}"
        def tile = getChildDevice(tileDni)
        if (!tile) { return }
        def name = v.getLabel()
        def pct = v.currentValue('percent-open') ?: v.currentValue('level') ?: 0
        def tC = v.currentValue('room-current-temperature-c')
        def tF = (tC != null) ? (((tC as BigDecimal) * 9/5) + 32) : null
        String html = "<div style='font-family:sans-serif'><b>${name}</b>: ${pct}%" + (tF != null ? " | ${((tF as BigDecimal) * 10).round() / 10} &deg;F" : '') + "</div>"
        sendEvent(tile, [name: 'html', value: html])
        sendEvent(tile, [name: 'level', value: (pct as int)])
      } catch (ignored) { }
    }
  } catch (ignored) { }
}

// Commands invoked by tile driver
def tileSetVentPercent(String ventDni, Integer pct) {
  try {
    def v = getChildDevice(ventDni)
    if (v) { patchVent(v, pct) }
  } catch (ignored) { }
}

def tileSetManualMode(String ventDni) {
  try {
    def overrides = atomicState?.manualOverrides ?: [:]
    overrides[ventDni] = (getChildDevice(ventDni)?.currentValue('percent-open') ?: 0) as Integer
    atomicState.manualOverrides = overrides
  } catch (ignored) { }
}

def tileSetAutoMode(String ventDni) {
  try {
    def overrides = atomicState?.manualOverrides ?: [:]
    overrides.remove(ventDni)
    atomicState.manualOverrides = overrides
  } catch (ignored) { }
}

// Night override helpers
def activateNightOverride() {
  try {
    def vents = (settings?.nightOverrideRooms ?: [])
    Integer pct = (settings?.nightOverridePercent ?: 100) as Integer
    vents?.each { v -> try { patchVent(v, pct) } catch (ignored) { } }
  } catch (ignored) { }
}

def deactivateNightOverride() {
  try {
    clearAllManualOverrides()
  } catch (ignored) { }
}

def clearAllManualOverrides() { try { atomicState.remove('manualOverrides') } catch (ignored) { } }

// Async convenience wrapper
def getStructureDataAsync() { try { getStructureData(0) } catch (ignored) { } }

def removeChildren() {
  try {
    getChildDevices()?.each { child ->
      try { deleteChildDevice(child.deviceNetworkId) } catch (e) {
        logWarn("Failed to delete child ${child?.displayName ?: child?.deviceNetworkId}: ${e?.message}", 'App')
      }
    }
  } catch (ignored) { }
}

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
      def roomC = samp[5]
      if (roomC != null) { return roomC as BigDecimal }
    }
  }
      if (tempDevice) {
    def temp = tempDevice.currentValue('temperature')
    if (temp == null) {
      log(2, 'App', "WARNING: Temperature device ${tempDevice?.getLabel() ?: 'Unknown'} for room '${roomName}' is not reporting temperature!")
      // Fall back to room temperature
      def roomTemp = vent.currentValue('room-current-temperature-c') ?: 0
      log(2, 'App', "Falling back to room temperature for '${roomName}': ${roomTemp}°C")
      return roomTemp
    }
      if (settings.thermostat1TempUnit == '2') {
      temp = convertFahrenheitToCentigrade(temp)
    }
    log(2, 'App', "Got temp from ${tempDevice?.getLabel() ?: 'Unknown'} for '${roomName}': ${temp}°C")
    return temp
  }

def roomTemp = vent.currentValue('room-current-temperature-c')
  if (roomTemp == null) {
    log(2, 'App', "ERROR: No temperature available for room '${roomName}' - neither from Puck nor from room API!")
    return 0
  }
  log(2, 'App', "Using room temperature for '${roomName}': ${roomTemp}°C")
  return roomTemp
}
def atomicStateUpdate(String stateKey, String key, value) {
  atomicState.updateMapValue(stateKey, key, value)
  log(1, 'App', "atomicStateUpdate(${stateKey}, ${key}, ${value})")
}

def getThermostatSetpoint(String hvacMode) {
  // First, check if a thermostat has been selected. If not, return null immediately.
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
    // We only log this error if a thermostat is selected but has no setpoint property.
    logError 'A thermostat is selected, but it has no readable setpoint property. Please check the device.'
    return null
  }
      if (settings.thermostat1TempUnit == '2') {
    setpoint = convertFahrenheitToCentigrade(setpoint)
  }
  return setpoint
}
// Global setpoint resolution that does not require a thermostat.
// Falls back to median room setpoints from vent rooms when thermostat is absent.
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
// Fallback defaults
  return (hvacMode == COOLING ? DEFAULT_COOLING_SETPOINT_C : DEFAULT_HEATING_SETPOINT_C)
}

def roundBigDecimal(BigDecimal number, int scale = 3) {
  number.setScale(scale, BigDecimal.ROUND_HALF_UP)
}// Function to round values to specific decimal places for JSON export
def roundToDecimalPlaces(def value, int decimalPlaces) {
  if (value == null || value == 0) return 0
  
  try {
    // Convert to double
    def doubleValue = value as Double
    
    // Use basic math to round to decimal places - this definitely works in Hubitat
    def multiplier = Math.pow(10, decimalPlaces)
    def rounded = Math.round(doubleValue * multiplier) / multiplier
    
    // Return as Double to ensure proper JSON serialization
    return rounded as Double
  } catch (Exception e) {
    log(2, 'App', "Error rounding value ${value}: ${e.message}")
    return 0
  }
}// Function to clean decimal values for JSON serialization
// Enhanced version to handle Hubitat's BigDecimal precision issues
def cleanDecimalForJson(def value) {
  if (value == null || value == 0) return 0
  
  try {
    // Convert to String first to break BigDecimal precision chain
    def stringValue = value.toString()
    def doubleValue = Double.parseDouble(stringValue)
    
    // Handle edge cases
    if (!Double.isFinite(doubleValue)) {
      return 0.0d
    }
// Apply aggressive rounding to exactly 10 decimal places
    def multiplier = 1000000000.0d  // 10^9 for 10 decimal places
    def rounded = Math.round(doubleValue * multiplier) / multiplier
    
    // Ensure we return a clean Double, not BigDecimal
    return Double.valueOf(rounded)
  } catch (Exception e) {
    log(2, 'App', "Error cleaning decimal for JSON: ${e.message}")
    return 0.0d
  }
}// Modified rounding function that uses the user-configured granularity.
// It has been renamed to roundToNearestMultiple since it rounds a value to the nearest multiple of a given granularity.
int roundToNearestMultiple(BigDecimal num) {
  int granularity = settings.ventGranularity ? settings.ventGranularity.toInteger() : 5
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
  (hvacMode == COOLING && currentTemp <= setpoint - offset) ||
  (hvacMode == HEATING && currentTemp >= setpoint + offset)
}// Determine HVAC mode purely from vent duct temperatures. Returns
// 'heating', 'cooling', or null if HVAC is idle.
def calculateHvacMode(BigDecimal temp, BigDecimal coolingSetpoint, BigDecimal heatingSetpoint) {
  try {
    if (temp != null) {
      // Simple param-based inference with small hysteresis using SETPOINT_OFFSET
      if (coolingSetpoint != null && temp >= (coolingSetpoint + SETPOINT_OFFSET)) { return COOLING }
      if (heatingSetpoint != null && temp <= (heatingSetpoint - SETPOINT_OFFSET)) { return HEATING }
    }
  } catch (ignore) { }
  return calculateHvacModeRobust()
}
// Robust HVAC mode detection using median duct-room temperature difference
// with thermostat operating state as a fallback.
// calculateHvacModeRobust is provided by DabManager library

def resetApiConnection() {
  logWarn 'Resetting API connection'
  atomicState.failureCounts = [:]
  authenticate()
}

def noOpHandler(resp, data) {
  log(3, 'App', 'noOpHandler called')
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
      def err = "Authentication request failed: ${e.message}"
      logError err
      state.authError = err
      state.authInProgress = false
      decrementActiveRequests()  // Decrement on exception
      return err
    }
  } else {
    // If we can't make request now, reschedule authentication
    state.authInProgress = false
    if (retryCount < MAX_API_RETRY_ATTEMPTS) {
      runInMillis(API_CALL_DELAY_MS, 'retryAuthenticateWrapper', [data: [retryCount: retryCount + 1]])
    } else {
      def err = "Authentication failed after ${MAX_API_RETRY_ATTEMPTS} retries"
      logError err
      state.authError = err
    }
  }
  return ''
}// Wrapper method for authenticate retry
def retryAuthenticateWrapper(data) {
  authenticate(data?.retryCount ?: 0)
}

def handleAuthResponse(resp, data) {
  decrementActiveRequests()  // Always decrement when response comes back
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
      def errorMsg = "Authentication failed with HTTP ${status}"
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
      
      // Call getStructureData async after successful auth
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
    log(1, 'App', "Exception stack trace: ${e.getStackTrace()}")
  }
}

def appButtonHandler(String btn) {
  switch (btn) {
    case 'authenticate':
      login()
      unschedule('login')
      runEvery1Hour('login')
      break
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
}// Auto-authenticate when credentials are provided
def autoAuthenticate() {
  if (settings?.clientId && settings?.clientSecret && !state.flairAccessToken) {
    log(2, 'App', 'Auto-authenticating with provided credentials')
    login()
    unschedule('login')
    runEvery1Hour('login')
  }
}// Automatically re-authenticate when token expires
def autoReauthenticate() {
  log(2, 'App', 'Token expired or invalid, re-authenticating...')
  state.remove('flairAccessToken')
  // Clear any error state
  state.remove('authError')
  // Re-authenticate and reschedule
  if (authenticate() == '') {
    // If authentication succeeded, reschedule hourly refresh
    unschedule('login')
    runEvery1Hour('login')
    log(2, 'App', 'Re-authentication successful, rescheduled hourly token refresh')
  }
}
private void discover() {
  log(3, 'App', 'Discovery started')
  atomicState.remove('ventsByRoomId')
  def structureId = getStructureId()
  // Discover vents first
  def ventsUri = "${BASE_URL}/api/structures/${structureId}/vents"
  log(2, 'API', "Calling vents endpoint: ${ventsUri}", ventsUri)
  getDataAsync(ventsUri, 'handleDeviceList', [deviceType: 'vents'])
  // Then discover pucks separately - they might be at a different endpoint
  def pucksUri = "${BASE_URL}/api/structures/${structureId}/pucks"
  log(2, 'API', "Calling pucks endpoint: ${pucksUri}", pucksUri)
  getDataAsync(pucksUri, 'handleDeviceList', [deviceType: 'pucks'])
  // Also try to get pucks from rooms since they might be associated there
  def roomsUri = "${BASE_URL}/api/structures/${structureId}/rooms?include=pucks"
  log(2, 'API', "Calling rooms endpoint for pucks: ${roomsUri}", roomsUri)
  getDataAsync(roomsUri, 'handleRoomsWithPucks')
  // Try getting pucks directly without structure
  def allPucksUri = "${BASE_URL}/api/pucks"
  log(2, 'API', "Calling all pucks endpoint: ${allPucksUri}", allPucksUri)
  getDataAsync(allPucksUri, 'handleAllPucks')
}

def handleAllPucks(resp, data) {
  decrementActiveRequests()  // Always decrement when response comes back
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
            
            log(2, 'App', "Creating puck from all pucks endpoint: ${puckName} (${puckId})")
            
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
          log(1, 'App', "Error processing puck from all pucks: ${e.message}")
        }
      }
      if (puckCount > 0) {
        log(3, 'App', "Discovered ${puckCount} pucks from all pucks endpoint")
      }
    }
  } catch (Exception e) {
    log(1, 'App', "Error in handleAllPucks: ${e.message}")
  }
}

def handleRoomsWithPucks(resp, data) {
  decrementActiveRequests()  // Always decrement when response comes back
  try {
    log(2, 'App', "handleRoomsWithPucks called")
    if (!isValidResponse(resp)) { 
      log(2, 'App', "handleRoomsWithPucks: Invalid response status: ${resp?.getStatus()}")
      return 
    }

def respJson = resp.getJson()
    
    // Log the structure to debug
    log(2, 'App', "handleRoomsWithPucks response: has included=${respJson?.included != null}, included count=${respJson?.included?.size() ?: 0}, has data=${respJson?.data != null}, data count=${respJson?.data?.size() ?: 0}")
    
    // Check if we have included pucks data
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
            // Ensure we have a valid name
            if (!puckName || puckName.isEmpty()) {
              puckName = "Puck-${puckId}"
            }
// Double-check the name is not empty after all processing
            if (!puckName || puckName.isEmpty()) {
              log(2, 'App', "Skipping puck with empty name even after fallback")
              return
            }
            
            log(1, 'App', "About to create puck device with id: ${puckId}, name: ${puckName}")
            
            def device = [
              id   : puckId,
              type : 'pucks',  // Use string literal to ensure it's not null
              label: puckName
            ]
            
            def dev = makeRealDevice(device)
            if (dev) {
              log(2, 'App', "Created puck device: ${puckName}")
            }
          }
        } catch (Exception e) {
          log(1, 'App', "Error processing puck in loop: ${e.message}, line: ${e.stackTrace?.find()?.lineNumber}")
        }
      }
      if (puckCount > 0) {
        log(3, 'App', "Discovered ${puckCount} pucks from rooms include")
      }
    }
  } catch (Exception e) {
    log(1, 'App', "Error in handleRoomsWithPucks: ${e.message} at line ${e.stackTrace?.find()?.lineNumber}")
  }
// Also check if pucks are in the room data relationships
  try {
    if (respJson?.data) {
      def roomPuckCount = 0
      respJson.data.each { room ->
        if (room.relationships?.pucks?.data) {
          room.relationships.pucks.data.each { puck ->
            try {
              roomPuckCount++
              def puckId = puck.id?.toString()?.trim()
              if (!puckId || puckId.isEmpty()) {
                log(2, 'App', "Skipping puck with invalid ID in room ${room.attributes?.name}")
                return
              }
// Create a minimal puck device from the reference
              def puckName = "Puck-${puckId}"
              if (room.attributes?.name) {
                puckName = "${room.attributes.name} Puck"
              }
              
              log(2, 'App', "Creating puck device from room reference: ${puckName} (${puckId})")
              
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
              log(1, 'App', "Error creating puck from room reference: ${e.message}")
            }
          }
        }
      }
      if (roomPuckCount > 0) {
        log(3, 'App', "Found ${roomPuckCount} puck references in rooms")
      }
    }
  } catch (Exception e) {
    log(1, 'App', "Error checking room puck relationships: ${e.message}")
  }
}

def handleDeviceList(resp, data) {
  decrementActiveRequests()  // Always decrement when response comes back
  log(2, 'App', "handleDeviceList called for ${data?.deviceType}")
  if (!isValidResponse(resp)) {
    // Check if this was a pucks request that returned 404
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
              "Please ensure you're using OAuth 2.0 credentials or Legacy API (OAuth 1.0) credentials."
    }
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
    logWarn "No devices found in the structure. " +
            "This typically happens with incorrect OAuth credentials."
  }
}

def makeRealDevice(Map device) {
  // Validate inputs
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
  log(2, 'App', "Refresh device details for ${device}")
  def deviceId = device.getDeviceNetworkId()
  def roomId = device.currentValue('room-id')
  
  // Check if it's a puck by looking for the percent-open attribute which only vents have
  def isPuck = !device.hasAttribute('percent-open')
  
  if (isPuck) {
    // Get puck data and current reading with caching
    getDeviceDataWithCache(device, deviceId, 'pucks', 'handlePuckGet')
    getDeviceReadingWithCache(device, deviceId, 'pucks', 'handlePuckReadingGet')
    // Check cache before making room API call
    getRoomDataWithCache(device, deviceId, isPuck)
  } else {
    // Get vent reading with caching
    getDeviceReadingWithCache(device, deviceId, 'vents', 'handleDeviceGet')
    // Check cache before making room API call
    getRoomDataWithCache(device, deviceId, isPuck)
  }
}// New function to handle room data with caching
def getRoomDataWithCache(device, deviceId, isPuck) {
  def roomId = device.currentValue('room-id')
  
  if (roomId) {
    // Check cache first using instance-based cache
    def cachedData = getCachedRoomData(roomId)
    if (cachedData) {
      log(3, 'App', "Using cached room data for room ${roomId}")
      processRoomTraits(device, cachedData)
      return
    }
// Check if a request is already pending for this room
    if (isRequestPending(roomId)) {
      // log(3, 'App', "Room data request already pending for room ${roomId}, skipping duplicate request")
      return
    }
// Mark this room as having a pending request
    markRequestPending(roomId)
  }
// No valid cache and no pending request, make the API call
  def endpoint = isPuck ? "pucks" : "vents"
  getDataAsync("${BASE_URL}/api/${endpoint}/${deviceId}/room", 'handleRoomGetWithCache', [device: device])
}// New function to handle device data with caching (for pucks)
def getDeviceDataWithCache(device, deviceId, deviceType, callback) {
  def cacheKey = "${deviceType}_${deviceId}"
  
  // Check cache first using instance-based cache
  def cachedData = getCachedDeviceReading(cacheKey)
  if (cachedData) {
    log(3, 'App', "Using cached ${deviceType} data for device ${deviceId}")
    // Process the cached data
    if (callback == 'handlePuckGet') {
      handlePuckGet([getJson: { cachedData }], [device: device])
    }
    return
  }
// Check if a request is already pending
  if (isDeviceRequestPending(cacheKey)) {
    // log(3, 'App', "${deviceType} data request already pending for device ${deviceId}, skipping duplicate request")
    return
  }
// Mark this device as having a pending request
  markDeviceRequestPending(cacheKey)
  
  // No valid cache and no pending request, make the API call
  def uri = "${BASE_URL}/api/${deviceType}/${deviceId}"
  getDataAsync(uri, callback + 'WithCache', [device: device, cacheKey: cacheKey])
}// New function to handle device reading with caching
def getDeviceReadingWithCache(device, deviceId, deviceType, callback) {
  def cacheKey = "${deviceType}_reading_${deviceId}"
  
  // Check cache first using instance-based cache
  def cachedData = getCachedDeviceReading(cacheKey)
  if (cachedData) {
    log(3, 'App', "Using cached ${deviceType} reading for device ${deviceId}")
    // Process the cached data
    if (callback == 'handlePuckReadingGet') {
      handlePuckReadingGet([getJson: { cachedData }], [device: device])
    } else if (callback == 'handleDeviceGet') {
      handleDeviceGet([getJson: { cachedData }], [device: device])
    }
    return
  }
// Check if a request is already pending
  if (isDeviceRequestPending(cacheKey)) {
    // log(3, 'App', "${deviceType} reading request already pending for device ${deviceId}, skipping duplicate request")
    return
  }
// Mark this device as having a pending request
  markDeviceRequestPending(cacheKey)
  
  // No valid cache and no pending request, make the API call
  def uri = deviceType == 'pucks' ? "${BASE_URL}/api/pucks/${deviceId}/current-reading" : "${BASE_URL}/api/vents/${deviceId}/current-reading"
  getDataAsync(uri, callback + 'WithCache', [device: device, cacheKey: cacheKey])
}

def handleRoomGet(resp, data) {
  decrementActiveRequests()  // Always decrement when response comes back
  if (!isValidResponse(resp) || !data?.device) { return }
  processRoomTraits(data.device, resp.getJson())
}// Modified handleRoomGet to include caching
def handleRoomGetWithCache(resp, data) {
  def roomData = null
  def roomId = null
  
  try {
    // First, try to get roomId from device for cleanup purposes
    if (data?.device) {
      roomId = data.device.currentValue('room-id')
    }
      if (isValidResponse(resp) && data?.device) {
      roomData = resp.getJson()
      // Update roomId if we got it from response
      if (roomData?.data?.id) {
        roomId = roomData.data.id
      }
      if (roomId) {
        // Cache the room data using instance-based cache
        cacheRoomData(roomId, roomData)
        log(3, 'App', "Cached room data for room ${roomId}")
      }
      
      processRoomTraits(data.device, roomData)
    } else {
      // Log the error for debugging
      log(2, 'App', "Room data request failed for device ${data?.device}, status: ${resp?.getStatus()}")
    }
  } catch (Exception e) {
    log(1, 'App', "Error in handleRoomGetWithCache: ${e.message}")
  } finally {
    // Always clear the pending flag, even if the request failed
    if (roomId) {
      clearPendingRequest(roomId)
      log(1, 'App', "Cleared pending request for room ${roomId}")
    }
  }
}// Add a method to clear the cache periodically (optional)
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
}// Clear device cache periodically
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
}// Periodic cleanup of pending request flags
def cleanupPendingRequests() {
  initializeInstanceCaches()
  def instanceId = getInstanceId()
  def cacheKey = "instanceCache_${instanceId}"
  
  def pendingRoomRequests = state."${cacheKey}_pendingRoomRequests"
  def pendingDeviceRequests = state."${cacheKey}_pendingDeviceRequests"
  
  // First, check if the active request counter is stuck
  def currentActiveRequests = atomicState.activeRequests ?: 0
  if (currentActiveRequests >= MAX_CONCURRENT_REQUESTS) {
    log(1, 'App', "CRITICAL: Active request counter is stuck at ${currentActiveRequests}/${MAX_CONCURRENT_REQUESTS} - resetting to 0")
    atomicState.activeRequests = 0
    log(1, 'App', "Reset active request counter to 0")
  }
// Collect keys first to avoid concurrent modification
  def roomsToClean = []
  pendingRoomRequests.each { roomId, isPending ->
    if (isPending) {
      roomsToClean << roomId
    }
  }
// Now modify the map outside of iteration
  roomsToClean.each { roomId ->
    pendingRoomRequests[roomId] = false
  }
      if (roomsToClean.size() > 0) {
    log(2, 'App', "Cleared ${roomsToClean.size()} stuck pending request flags for rooms: ${roomsToClean.join(', ')}")
  }
// Same for device requests
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

def activeRequestsWatchdog() {
  try {
    enhanceCallbackTimeout()
  } catch (Exception e) {
    log(4, 'Watchdog', "Watchdog error: ${e?.message}")
  }
}

def handleDeviceGet(resp, data) {
  decrementActiveRequests()  // Always decrement when response comes back
  if (!isValidResponse(resp) || !data?.device) { return }
  processVentTraits(data.device, resp.getJson())
}// Modified handleDeviceGet to include caching
def handleDeviceGetWithCache(resp, data) {
  def deviceData = null
  def cacheKey = data?.cacheKey
  
  try {
    if (isValidResponse(resp) && data?.device) {
      deviceData = resp.getJson()
      
      if (cacheKey && deviceData) {
        // Cache the device data using instance-based cache
        cacheDeviceReading(cacheKey, deviceData)
        log(3, 'App', "Cached device reading for ${cacheKey}")
      }
      
      processVentTraits(data.device, deviceData)
    } else {
      // Handle hub load exceptions specifically
      if (resp instanceof Exception || resp.toString().contains('LimitExceededException')) {
        logWarn "Device reading request failed due to hub load: ${resp.toString()}"
      } else {
        log(2, 'App', "Device reading request failed for ${cacheKey}, status: ${resp?.getStatus()}")
      }
    }
  } catch (Exception e) {
    logWarn "Error in handleDeviceGetWithCache: ${e.message}"
  } finally {
    // Always clear the pending flag
    if (cacheKey) {
      clearDeviceRequestPending(cacheKey)
      log(1, 'App', "Cleared pending device request for ${cacheKey}")
    }
  }
}

def handlePuckGet(resp, data) {
  decrementActiveRequests()  // Always decrement when response comes back
  if (!isValidResponse(resp) || !data?.device) { return }

def respJson = resp.getJson()
  if (respJson?.data) {
    def puckData = respJson.data
    // Extract puck attributes
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
}// Modified handlePuckGet to include caching
def handlePuckGetWithCache(resp, data) {
  def deviceData = null
  def cacheKey = data?.cacheKey
  
  try {
    if (isValidResponse(resp) && data?.device) {
      deviceData = resp.getJson()
      
      if (cacheKey && deviceData) {
        // Cache the device data using instance-based cache
        cacheDeviceReading(cacheKey, deviceData)
        log(3, 'App', "Cached puck data for ${cacheKey}")
      }
// Process using existing logic
      handlePuckGet([getJson: { deviceData }], data)
    }
  } finally {
    // Always clear the pending flag
    if (cacheKey) {
      clearDeviceRequestPending(cacheKey)
    }
  }
}

def handlePuckReadingGet(resp, data) {
  decrementActiveRequests()  // Always decrement when response comes back
  if (!isValidResponse(resp) || !data?.device) { return }

def respJson = resp.getJson()
  if (respJson?.data) {
    def reading = respJson.data
    // Process sensor reading data
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
        // Map system-voltage to voltage attribute for Rule Machine compatibility
        sendEvent(data.device, [name: 'voltage', value: voltage, unit: 'V'])
        def battery = ((voltage - 2.0) / 1.6) * 100
        battery = Math.max(0, Math.min(100, battery.round() as int))
        sendEvent(data.device, [name: 'battery', value: battery, unit: '%'])
      } catch (Exception e) {
        log(2, 'App', "Error calculating battery from reading: ${e.message}")
      }
    }
  }
}// Modified handlePuckReadingGet to include caching
def handlePuckReadingGetWithCache(resp, data) {
  def deviceData = null
  def cacheKey = data?.cacheKey
  
  try {
    if (isValidResponse(resp) && data?.device) {
      deviceData = resp.getJson()
      
      if (cacheKey && deviceData) {
        // Cache the device data using instance-based cache
        cacheDeviceReading(cacheKey, deviceData)
        log(3, 'App', "Cached puck reading for ${cacheKey}")
      }
// Process using existing logic
      handlePuckReadingGet([getJson: { deviceData }], data)
    }
  } finally {
    // Always clear the pending flag
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
    log(1, 'App', "Extracted: ${propNameData} = ${propValue}")
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
      traitExtract(device, details, attr, attr == 'percent-open' ? 'level' : attr, attr == 'percent-open' ? '%' : null)
   }
// Map system-voltage to voltage attribute for Rule Machine compatibility
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
    'humidity-away-min': 'room-humidity-away-min',
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
  decrementActiveRequests()  // Always decrement when response comes back
  if (!data) { return }
// Don't log 404 errors for missing sensors - this is expected
  if (resp?.hasError() && resp.getStatus() == 404) {
    log(1, 'App', "No remote sensor data available for ${data?.device?.getLabel() ?: 'unknown device'}")
    return
  }
      if (!isValidResponse(resp)) { return }
// Additional validation before parsing JSON
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
      logError "Structure data request failed: ${e.message}"
      decrementActiveRequests()  // Decrement on exception
    }
  } else {
    // If we can't make request now, retry later
    if (retryCount < MAX_API_RETRY_ATTEMPTS) {
      runInMillis(API_CALL_DELAY_MS, 'retryGetStructureDataAsyncWrapper', [data: [retryCount: retryCount + 1]])
    } else {
      logError "getStructureDataAsync failed after ${MAX_API_RETRY_ATTEMPTS} retries"
    }
  }
}// Wrapper method for getStructureDataAsync retry
def retryGetStructureDataAsyncWrapper(data) {
  getStructureDataAsync(data?.retryCount ?: 0)
}

def handleStructureResponse(resp, data) {
  def retryCount = data?.retryCount ?: 0
  decrementActiveRequests()  // Always decrement when response comes back
  
  try {
    if (!isValidResponse(resp)) { 
      if (retryCount < MAX_API_RETRY_ATTEMPTS) {
        log(2, 'App', "Structure data response failed (attempt ${retryCount + 1}/${MAX_API_RETRY_ATTEMPTS}): HTTP ${resp?.status ?: 'unknown'}")
        runInMillis(API_CALL_DELAY_MS, 'retryGetStructureDataWrapper', [data: [retryCount: retryCount + 1]])
      } else {
        logError "Structure data request failed after ${MAX_API_RETRY_ATTEMPTS} attempts: HTTP ${resp?.status ?: 'unknown'}"
      }
      return 
    }

    def response = resp.getJson()
    if (!response?.data?.first()) {
      logError 'No structure data available'
      return
    }

    def myStruct = response.data.first()
    if (!myStruct?.attributes) {
      logError 'getStructureData: no structure data'
      return
    }
    
    // Log only essential fields at level 3
    log(3, 'App', "Structure loaded: id=${myStruct.id}, name=${myStruct.attributes.name}, mode=${myStruct.attributes.mode}")
    app.updateSetting('structureId', myStruct.id)
    
    // Only log full response at debug level 1
    logDetails 'Structure response: ', response, 1
    
  } catch (Exception e) {
    if (retryCount < MAX_API_RETRY_ATTEMPTS) {
      log(2, 'App', "Structure data processing failed (attempt ${retryCount + 1}/${MAX_API_RETRY_ATTEMPTS}): ${e.message}")
      runInMillis(API_CALL_DELAY_MS, 'retryGetStructureDataWrapper', [data: [retryCount: retryCount + 1]])
    } else {
      logError "Structure data processing failed after ${MAX_API_RETRY_ATTEMPTS} attempts: ${e.message}"
    }
  }
}

def getStructureData(int retryCount = 0) {
  log(1, 'App', 'getStructureData')
  
  // Check concurrent request limit first
  if (!canMakeRequest()) {
    if (retryCount < MAX_API_RETRY_ATTEMPTS) {
      log(2, 'App', "Structure data request delayed due to concurrent limit (attempt ${retryCount + 1}/${MAX_API_RETRY_ATTEMPTS})")
      // Schedule retry asynchronously to avoid blocking
      runInMillis(API_CALL_DELAY_MS, 'retryGetStructureDataWrapper', [data: [retryCount: retryCount + 1]])
      return
    } else {
      logError "getStructureData failed after ${MAX_API_RETRY_ATTEMPTS} attempts due to concurrent limits"
      return
    }
  }

def uri = "${BASE_URL}/api/structures"
  def headers = [ Authorization: "Bearer ${state.flairAccessToken}" ]
  def httpParams = [ uri: uri, headers: headers, contentType: CONTENT_TYPE, timeout: HTTP_TIMEOUT_SECS ]
  
  incrementActiveRequests()
  
  try {
    asynchttpGet('handleStructureResponse', httpParams, [retryCount: retryCount])
  } catch (Exception e) {
    decrementActiveRequests()
    
    if (retryCount < MAX_API_RETRY_ATTEMPTS) {
      log(2, 'App', "Structure data request failed (attempt ${retryCount + 1}/${MAX_API_RETRY_ATTEMPTS}): ${e.message}")
      // Schedule retry asynchronously
      runInMillis(API_CALL_DELAY_MS, 'retryGetStructureDataWrapper', [data: [retryCount: retryCount + 1]])
    } else {
      logError "getStructureData failed after ${MAX_API_RETRY_ATTEMPTS} attempts: ${e.message}"
    }
  }
  }
}// Wrapper method for synchronous getStructureData retry
def retryGetStructureDataWrapper(data) {
  getStructureData(data?.retryCount ?: 0)
}

def patchVentDevice(device, percentOpen, attempt = 1) {
  int floorPct = 0
  try { floorPct = (settings?.allowFullClose ? 0 : ((settings?.minVentFloorPercent ?: 0) as int)) } catch (ignore) { floorPct = 0 }

def pOpen = Math.min(100, Math.max(floorPct, percentOpen as int))
  def currentOpen = (device?.currentValue('percent-open') ?: 0).toInteger()
  if (pOpen == currentOpen) {
    log(3, 'App', "Keeping ${device} percent open unchanged at ${pOpen}%")
    return
  }
  log(3, 'App', "Setting ${device} percent open from ${currentOpen} to ${pOpen}%")
  def deviceId = device.getDeviceNetworkId()
  def uri = "${BASE_URL}/api/vents/${deviceId}"
  def body = [ data: [ type: 'vents', attributes: [ 'percent-open': pOpen ] ] ]

  // Don't update local state until API call succeeds
  patchDataAsync(uri, 'handleVentPatch', body, [device: device, targetOpen: pOpen])

  // Schedule verification of the vent's reported percent open
  runInMillis(VENT_VERIFY_DELAY_MS, 'verifyVentPercentOpen', [data: [deviceId: deviceId, targetOpen: pOpen, attempt: attempt]])
}// Keep the old method name for backward compatibility
def patchVent(device, percentOpen) {
  try {
    String vid = device?.getDeviceNetworkId()
    def overrides = atomicState?.manualOverrides ?: [:]
    if (vid && overrides?.containsKey(vid)) {
      // Hard-enforce manual override: never let algorithmic calls reduce it
      percentOpen = (overrides[vid] as Integer)
    }
  } catch (ignore) { }
  patchVentDevice(device, percentOpen)
}

def handleVentPatch(resp, data) {
  decrementActiveRequests()  // Always decrement when response comes back
  if (!isValidResponse(resp) || !data) { 
    if (resp instanceof Exception || resp.toString().contains('LimitExceededException')) {
      log(2, 'App', "Vent patch failed due to hub load: ${resp.toString()}")
    } else {
      log(2, 'App', "Vent patch failed - invalid response or data")
    }
    return 
  }
// Get the actual device for processing (handle serialized device objects)
  def device = null
  if (data.device?.getDeviceNetworkId) {
    device = data.device
  } else if (data.device?.deviceNetworkId) {
    device = getChildDevice(data.device.deviceNetworkId)
  }
      if (!device) {
    log(2, 'App', "Could not get device object for vent patch processing")
    return
  }
// Process the API response
  def respJson = resp.getJson()
  traitExtract(device, [data: respJson.data], 'percent-open', 'percent-open', '%')
  traitExtract(device, [data: respJson.data], 'percent-open', 'level', '%')

  // Update local state ONLY after successful API response
  if (data.targetOpen != null) {
    try {
      safeSendEvent(device, [name: 'percent-open', value: data.targetOpen])
      safeSendEvent(device, [name: 'level', value: data.targetOpen])
      def roomName = device.currentValue('room-name') ?: 'Unknown'
      appendDabActivityLog("${roomName} ${device.getDeviceNetworkId()} ${data.targetOpen}%")
      log(3, 'App', "Updated ${device.getLabel()} to ${data.targetOpen}%")
    } catch (Exception e) {
      log(2, 'App', "Error updating device state: ${e.message}")
    }
  }
}// Verify that the vent reached the requested percent open
def verifyVentPercentOpen(data) {
  if (!data?.deviceId || data.targetOpen == null) { return }

def device = getChildDevice(data.deviceId)
  if (!device) { return }

def uri = "${BASE_URL}/api/vents/${data.deviceId}/current-reading"
  getDataAsync(uri, 'handleVentVerify', [device: device, targetOpen: data.targetOpen, attempt: data.attempt ?: 1])
}// Handle verification response and retry if needed
def handleVentVerify(resp, data) {
  decrementActiveRequests()
  if (!isValidResponse(resp) || !data?.device) { return }

def device = data.device
  def attempt = data.attempt ?: 1
  def target = (data.targetOpen ?: 0) as int
  def actual = (resp.getJson()?.data?.attributes?.'percent-open' ?: 0) as int

  if (actual != target) {
    if (attempt < MAX_VENT_VERIFY_ATTEMPTS) {
      def nextAttempt = attempt + 1
      def logLevel = attempt == 1 ? 2 : 1
      log "Vent ${device.getLabel()} reported ${actual}% instead of ${target}% (attempt ${attempt}/${MAX_VENT_VERIFY_ATTEMPTS}), retrying", logLevel
      patchVentDevice(device, target, nextAttempt)
    } else {
      logError "Vent ${device.getLabel()} failed to reach ${target}% after ${MAX_VENT_VERIFY_ATTEMPTS} attempts (reported ${actual}%)"
      state.ventOpenDiscrepancies = state.ventOpenDiscrepancies ?: [:]
      state.ventOpenDiscrepancies[device.getDeviceNetworkId()] = [name: device.getLabel(), target: target, actual: actual]
    }
  } else {
    state.ventOpenDiscrepancies?.remove(device.getDeviceNetworkId())
  }
}

def patchRoom(device, active) {
  def roomId = device.currentValue('room-id')
  if (!roomId || active == null) { return }
      if (active == device.currentValue('room-active')) { return }
  log(3, 'App', "Setting active state to ${active} for '${device.currentValue('room-name')}'")
  def uri = "${BASE_URL}/api/rooms/${roomId}"
  def body = [ data: [ type: 'rooms', attributes: [ 'active': active == 'true' ] ] ]
  patchDataAsync(uri, 'handleRoomPatch', body, [device: device])
}

def handleRoomPatch(resp, data) {
  decrementActiveRequests()  // Always decrement when response comes back
  if (!isValidResponse(resp) || !data) { return }
  traitExtract(data.device, resp.getJson(), 'active', 'room-active')
}

def patchRoomSetPoint(device, temp) {
  def roomId = device.currentValue('room-id')
  if (!roomId || temp == null) { return }
BigDecimal tempC = temp
  if (getTemperatureScale() == 'F') {
    tempC = convertFahrenheitToCentigrade(tempC)
  }
  log(3, 'App', "Setting set-point to ${tempC}°C for '${device.currentValue('room-name')}'")
  def uri = "${BASE_URL}/api/rooms/${roomId}"
  def body = [ data: [ type: 'rooms', attributes: [ 'set-point-c': tempC ] ] ]
  patchDataAsync(uri, 'handleRoomSetPointPatch', body, [device: device])
}

def handleRoomSetPointPatch(resp, data) {
  decrementActiveRequests()
  if (!isValidResponse(resp) || !data) { return }
  traitExtract(data.device, resp.getJson(), 'set-point-c', 'room-set-point-c')
}

def thermostat1ChangeTemp(evt) {
  log(2, 'App', "Thermostat changed temp to: ${evt.value}")
  def temp = settings?.thermostat1?.currentValue('temperature')
  def coolingSetpoint = settings?.thermostat1?.currentValue('coolingSetpoint') ?: 0
  def heatingSetpoint = settings?.thermostat1?.currentValue('heatingSetpoint') ?: 0
  String hvacMode = calculateHvacMode(temp, coolingSetpoint, heatingSetpoint)
  def thermostatSetpoint = getThermostatSetpoint(hvacMode)
  
  // Apply hysteresis to prevent frequent cycling
  def lastSignificantTemp = atomicState.lastSignificantTemp ?: temp
  def tempDiff = Math.abs(temp - lastSignificantTemp)
  
  if (tempDiff >= THERMOSTAT_HYSTERESIS) {
    atomicState.lastSignificantTemp = temp
    log(2, 'App', "Significant temperature change detected: ${tempDiff}°C (threshold: ${THERMOSTAT_HYSTERESIS}°C)")
    
    if (isThermostatAboutToChangeState(hvacMode, thermostatSetpoint, temp)) {
      runInMillis(INITIALIZATION_DELAY_MS, 'initializeRoomStates', [data: hvacMode])
    }
  } else {
    log(3, 'App', "Temperature change ${tempDiff}°C is below hysteresis threshold ${THERMOSTAT_HYSTERESIS}°C - ignoring")
  }
}

def isThermostatAboutToChangeState(String hvacMode, BigDecimal setpoint, BigDecimal temp) {
  if (hvacMode == COOLING && temp + SETPOINT_OFFSET - VENT_PRE_ADJUST_THRESHOLD < setpoint) {
    atomicState.tempDiffsInsideThreshold = false
    return false
  } else if (hvacMode == HEATING && temp - SETPOINT_OFFSET + VENT_PRE_ADJUST_THRESHOLD > setpoint) {
    atomicState.tempDiffsInsideThreshold = false
    return false
  }
      if (atomicState.tempDiffsInsideThreshold == true) { return false }
  atomicState.tempDiffsInsideThreshold = true
  log(3, 'App', "Pre-adjusting vents for upcoming HVAC start. [mode=${hvacMode}, setpoint=${setpoint}, temp=${temp}]")
  return true
}

def thermostat1ChangeStateHandler(evt) {
  log(3, 'App', "Thermostat changed state to: ${evt.value}")
  def hvacMode = evt.value in [PENDING_COOL, PENDING_HEAT] ? (evt.value == PENDING_COOL ? COOLING : HEATING) : evt.value
  switch (hvacMode) {
    case COOLING:
    case HEATING:
      if (atomicState.thermostat1State) {
        log(3, 'App', "initializeRoomStates already executed (${evt.value})")
        return
      }
      atomicStateUpdate('thermostat1State', 'mode', hvacMode)
      atomicStateUpdate('thermostat1State', 'startedRunning', now())
      if (settings?.dabEnabled) {
        unschedule('initializeRoomStates')
        runInMillis(POST_STATE_CHANGE_DELAY_MS, 'initializeRoomStates', [data: hvacMode])
        recordStartingTemperatures()
        runEvery5Minutes('evaluateRebalancingVents')
        runEvery30Minutes('reBalanceVents')
      }
// Update polling to active interval when HVAC is running
      updateDevicePollingInterval((settings?.pollingIntervalActive ?: POLLING_INTERVAL_ACTIVE) as Integer)
      break
    default:
      if (settings?.dabEnabled) {
        unschedule('initializeRoomStates')
        unschedule('finalizeRoomStates')
        unschedule('evaluateRebalancingVents')
        unschedule('reBalanceVents')
      }
      if (atomicState.thermostat1State) {
        atomicStateUpdate('thermostat1State', 'finishedRunning', now())
        def params = [
          ventIdsByRoomId: atomicState.ventsByRoomId,
          startedCycle: atomicState.thermostat1State?.startedCycle,
          startedRunning: atomicState.thermostat1State?.startedRunning,
          finishedRunning: atomicState.thermostat1State?.finishedRunning,
          hvacMode: atomicState.thermostat1State?.mode
        ]
        runInMillis(TEMP_READINGS_DELAY_MS, 'finalizeRoomStates', [data: params])
        atomicState.remove('thermostat1State')
      }
      if (settings.fanOnlyOpenAllVents && isFanActive(evt.value) && atomicState.ventsByRoomId) {
        log(2, 'App', 'Fan-only mode detected - opening all vents to 100%')
        openAllVents(atomicState.ventsByRoomId, MAX_PERCENTAGE_OPEN as int)
      }
// Update polling to idle interval when HVAC is idle
      updateDevicePollingInterval((settings?.pollingIntervalIdle ?: POLLING_INTERVAL_IDLE) as Integer)
      break
  }
}
// Periodically evaluate duct temperatures to determine HVAC state
// without relying on an external thermostat.
// updateHvacStateFromDuctTemps is provided by DabManager library

// reBalanceVents and evaluateRebalancingVents are provided by DabManager library
// Retrieve all stored rates for a specific room, HVAC mode, and hour
def getHourlyRates(String roomId, String hvacMode, Integer hour) {
  initializeDabHistory()
  def hist = atomicState?.dabHistory
  Integer retention = getRetentionDays()
  Long cutoff = now() - retention * 24L * 60L * 60L * 1000L
  // Prefer flat entries list for time-based retention
  def entries = (hist instanceof List) ? hist : (hist?.entries ?: [])
  def list = entries.findAll { entry ->
    try {
      return entry[1] == roomId && entry[2] == hvacMode && entry[3] == (hour as Integer) && (entry[0] as Long) >= cutoff
    } catch (ignore) { return false }
  }*.get(4).collect { it as BigDecimal }
      if (list && list.size() > 0) { return list }
// Fallback to hourlyRates index if entries empty
  try {
    def rates = hist?.hourlyRates?.get(roomId)?.get(hvacMode)?.get(hour.toString()) ?: []
    return rates.collect { it as BigDecimal }
  } catch (ignore) {
    return []
  }
}// -------------
// EWMA + MAD helpers
// -------------
def getEwmaRate(String roomId, String hvacMode, Integer hour) {
  try { return (atomicState?.dabEwma?.get(roomId)?.get(hvacMode)?.get(hour as Integer)) as BigDecimal } catch (ignore) { return null }
}
private BigDecimal updateEwmaRate(String roomId, String hvacMode, Integer hour, BigDecimal newRate) {
  try {
    def map = atomicState?.dabEwma ?: [:]
    def room = map[roomId] ?: [:]
    def mode = room[hvacMode] ?: [:]
    BigDecimal prev = (mode[hour as Integer]) as BigDecimal
    BigDecimal alpha = computeEwmaAlpha()
    BigDecimal updated = (prev == null) ? newRate : (alpha * newRate + (1 - alpha) * prev)
    mode[hour as Integer] = cleanDecimalForJson(updated)
    room[hvacMode] = mode
    map[roomId] = room
    try { atomicState.dabEwma = map } catch (ignore) { }
    return mode[hour as Integer] as BigDecimal
  } catch (ignore) { return newRate }
}
private BigDecimal computeEwmaAlpha() {
  try {
    BigDecimal hlDays = (atomicState?.ewmaHalfLifeDays ?: 3) as BigDecimal
    if (hlDays <= 0) { return 1.0 }
// One observation per day per hour-slot => N = half-life in days
    BigDecimal N = hlDays
    BigDecimal alpha = 1 - Math.pow(2.0, (-1.0 / N.toDouble()))
    return (alpha as BigDecimal)
  } catch (ignore) { return 0.2 }
}
private Map assessOutlierForHourly(String roomId, String hvacMode, Integer hour, BigDecimal candidate) {
  def decision = [action: 'accept']
  try {
    def list = getHourlyRates(roomId, hvacMode, hour) ?: []
    if (!list || list.size() < 4) { return decision }
// Median
    def sorted = list.collect { it as BigDecimal }.sort()
    BigDecimal median = sorted[sorted.size().intdiv(2)]
    // MAD
    def deviations = sorted.collect { (it - median).abs() }

def devSorted = deviations.sort()
    BigDecimal mad = devSorted[devSorted.size().intdiv(2)]
    BigDecimal k = ((atomicState?.outlierThresholdMad ?: 3) as BigDecimal)
    if (mad == 0) {
      // Fallback: standard deviation
      BigDecimal mean = (sorted.sum() as BigDecimal) / sorted.size()
      BigDecimal var = 0.0
      sorted.each { var += (it - mean) * (it - mean) }
      var = var / Math.max(1, sorted.size() - 1)
      BigDecimal sd = Math.sqrt(var as double)
      if (sd == 0) { return decision }
      if ((candidate - mean).abs() > (k * sd)) {
        if ((atomicState?.outlierMode ?: 'clip') == 'reject') return [action:'reject']
        BigDecimal bound = mean + (candidate > mean ? k * sd : -(k * sd))
        return [action:'clip', value: bound]
      }
    } else {
      // Robust scaled MAD ~ sigma
      BigDecimal scale = 1.4826 * mad
      if ((candidate - median).abs() > (k * scale)) {
        if ((atomicState?.outlierMode ?: 'clip') == 'reject') return [action:'reject']
        BigDecimal bound = median + (candidate > median ? k * scale : -(k * scale))
        return [action:'clip', value: bound]
      }
    }
  } catch (ignore) { }
  return decision
}
// Ensure DAB history structures are present and normalize legacy formats
def initializeDabHistory() {
  try {
    def hist = atomicState?.dabHistory
    if (!(hist instanceof Map)) {
      atomicState.dabHistory = [entries: [], hourlyRates: [:]]
      return
    }
    if (!(hist.entries instanceof List)) { hist.entries = [] }
    if (!(hist.hourlyRates instanceof Map)) { hist.hourlyRates = [:] }
  } catch (ignored) { }
}

// Collect efficiency data from vents and state for backup purposes
def exportEfficiencyData() {
  try {
    def data = [
      globalRates: [
        maxCoolingRate: atomicState?.maxCoolingRate ?: 0,
        maxHeatingRate: atomicState?.maxHeatingRate ?: 0
      ],
      roomEfficiencies: [],
      dabHistory: atomicState?.dabHistory ?: [:],
      dabActivityLog: atomicState?.dabActivityLog ?: []
    ]

    def vents = getChildDevices()?.findAll { it.hasAttribute('percent-open') } ?: []
    vents.each { v ->
      try {
        data.roomEfficiencies << [
          roomId: v.currentValue('room-id'),
          roomName: v.currentValue('room-name'),
          ventId: v.deviceNetworkId,
          coolingRate: v.currentValue('room-cooling-rate') ?: 0,
          heatingRate: v.currentValue('room-heating-rate') ?: 0
        ]
      } catch (ignored) { }
    }
    return data
  } catch (ignored) { return [:] }
}
// Build and cache the DAB progress table
// initializeDabHistory is provided by DabManager library
// Async-friendly wrapper to generate and cache the rates table HTML
// buildDabRatesTable(Map data) is provided by DabUIManager library
String buildDabProgressTable() {
  initializeDabHistory()
  def history = atomicState?.dabHistory ?: []
  def entries = (history instanceof List) ? history : (history?.entries ?: [])
  String roomId = null
  try { roomId = (atomicState?.progressRoom as String) } catch (ignore) { }
      if (!roomId) { try { roomId = entries ? entries[0][1] : null } catch (ignore) { } }
// roomId is read from atomicState mirror for CI-safety
  if (!roomId) { return '<p>Select a room to view progress.</p>' }
String hvacMode = settings?.progressHvacMode ?: getThermostat1Mode() ?: atomicState?.lastHvacMode
  if (!hvacMode || hvacMode in ['auto', 'manual']) { hvacMode = atomicState?.lastHvacMode }
  hvacMode = hvacMode ?: COOLING
  Date start = (settings?.progressStart instanceof String && settings.progressStart) ? Date.parse('yyyy-MM-dd', settings.progressStart) : null
  Date end = (settings?.progressEnd instanceof String && settings.progressEnd) ? Date.parse('yyyy-MM-dd', settings.progressEnd) : null
  def modes = hvacMode == 'both' ? [COOLING, HEATING] : [hvacMode]
  def tz = location?.timeZone ?: TimeZone.getTimeZone('UTC')

  def aggregated = [:] // date -> hour -> List<BigDecimal>
  entries.findAll { rec ->
    rec[1] == roomId && modes.contains(rec[2]) &&
    (!start || !new Date(rec[0]).before(start)) &&
    (!end || !new Date(rec[0]).after(end))
  }.each { rec ->
    Date d = new Date(rec[0])
    String dateStr = d.format('yyyy-MM-dd', tz)
    def dayMap = aggregated[dateStr] ?: [:]
    def list = dayMap[rec[3] as Integer] ?: []
    list << (rec[4] as BigDecimal)
    dayMap[rec[3] as Integer] = list
    aggregated[dateStr] = dayMap
  }
      if (!aggregated) { return '<p>No DAB progress history available for the selected period.</p>' }

def dates = aggregated.keySet().sort()
  def hours = (0..23)
  def html = new StringBuilder()
  html << "<table style='width:100%;border-collapse:collapse;'>"
  html << "<tr><th style='text-align:left;padding:4px;'>Date</th>"
  hours.each { hr -> html << "<th style='text-align:right;padding:4px;'>${hr}</th>" }
  html << '</tr>'
  dates.each { dateStr ->
    html << "<tr><td style='text-align:left;padding:4px;'>${dateStr}</td>"
    hours.each { hr ->
      def values = aggregated[dateStr]?.get(hr) ?: []
      BigDecimal avg = 0.0
      if (values) {
        BigDecimal sum = 0.0
        values.each { sum += it as BigDecimal }
        avg = cleanDecimalForJson(sum / values.size())
      }
      html << "<td style='text-align:right;padding:4px;'>${roundBigDecimal(avg)}</td>"
    }
    html << '</tr>'
  }
  html << '</table>'
  html.toString()
}
String buildDabDailySummaryTable() {
  def vents = getChildDevices()?.findAll { it.hasAttribute('percent-open') } ?: []
  Map roomNames = vents.collectEntries { v -> [(v.currentValue('room-id') ?: v.getId()): (v.currentValue('room-name') ?: v.getLabel())] }

def stats = atomicState?.dabDailyStats ?: [:]
  if (!stats || (stats instanceof Map && stats.isEmpty())) {
    // Fallback: compute daily stats on the fly from entries if persisted stats are unavailable
    try {
      def hist = atomicState?.dabHistory
      def entries = (hist instanceof List) ? hist : (hist?.entries ?: [])
      if (entries && entries.size() > 0) {
        def tz = location?.timeZone ?: TimeZone.getTimeZone('UTC')
        def byDay = [:] // room -> mode -> dateStr -> List<BigDecimal>
        entries.each { e ->
          try {
            Long ts = e[0] as Long
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

def rebuilt = [:]
        byDay.each { roomId, modeMap ->
          def roomStats = rebuilt[roomId] ?: [:]
          modeMap.each { hvacMode, dateMap ->
            def modeStats = []
            dateMap.keySet().sort().each { ds ->
              def list = dateMap[ds]
              if (list && list.size() > 0) {
                BigDecimal sum = 0.0
                list.each { sum += it as BigDecimal }
BigDecimal avg = cleanDecimalForJson(sum / list.size())
                modeStats << [date: ds, avg: avg]
              }
            }
            roomStats[hvacMode] = modeStats
          }
          rebuilt[roomId] = roomStats
        }
        stats = rebuilt
      }
// Secondary fallback: support legacy per-room -> mode -> [ {date, hour, rate}, ... ] structure
      if ((!stats || (stats instanceof Map && stats.isEmpty())) && (hist instanceof Map)) {
        def tz = location?.timeZone ?: TimeZone.getTimeZone('UTC')
        def byDay = [:] // room -> mode -> dateStr -> List<BigDecimal>
        try {
          hist.each { roomId, modeOrMeta ->
            // Skip known meta keys
            if (roomId in ['entries','hourlyRates']) { return }
      if (!(modeOrMeta instanceof Map)) { return }
            modeOrMeta.each { hvacMode, recList ->
              if (!(recList instanceof List)) { return }
              recList.each { rec ->
                try {
                  if (!(rec instanceof Map)) { return }
      if (rec.date && rec.hour != null && rec.rate != null) {
                    String dateStr = rec.date.toString()
                    def roomMap = byDay[roomId.toString()] ?: [:]
                    def modeMap = roomMap[hvacMode.toString()] ?: [:]
                    def list = (modeMap[dateStr] ?: []) as List
                    list << (rec.rate as BigDecimal)
                    modeMap[dateStr] = list
                    roomMap[hvacMode.toString()] = modeMap
                    byDay[roomId.toString()] = roomMap
                  }
                } catch (ignore2) { }
              }
            }
          }
        } catch (ignore) { }
      if (byDay && !byDay.isEmpty()) {
          def rebuilt = [:]
          byDay.each { roomId, modeMap ->
            def roomStats = rebuilt[roomId] ?: [:]
            modeMap.each { hvacMode, dateMap ->
              def modeStats = []
              dateMap.keySet().sort().each { ds ->
                def list = dateMap[ds]
                if (list && list.size() > 0) {
                  BigDecimal sum = 0.0
                  list.each { sum += it as BigDecimal }
BigDecimal avg = cleanDecimalForJson(sum / list.size())
                  modeStats << [date: ds, avg: avg]
                }
              }
              roomStats[hvacMode] = modeStats
            }
            rebuilt[roomId] = roomStats
          }
          stats = rebuilt
        }
      }
    } catch (ignore) { }
  }
      if (!stats || (stats instanceof Map && stats.isEmpty())) {
    // As a last resort, compute directly from legacy map if present
    def hist = atomicState?.dabHistory
    def legacyRecords = []
    if (hist instanceof Map) {
      try {
        hist.each { roomId, modeOrMeta ->
          if (roomId in ['entries','hourlyRates']) { return }
      if (!(modeOrMeta instanceof Map)) { return }
          modeOrMeta.each { hvacMode, recList ->
            if (!(recList instanceof List)) { return }
// Group by date and average values
            def byDate = [:]
            recList.each { rec ->
              try {
                if (rec instanceof Map && rec.date && rec.rate != null) {
                  String ds = rec.date.toString()
                  def list = (byDate[ds] ?: []) as List
                  list << (rec.rate as BigDecimal)
                  byDate[ds] = list
                }
              } catch (ignore) { }
            }
            byDate.keySet().each { ds ->
              def list = byDate[ds]
              if (list && list.size() > 0) {
                BigDecimal sum = 0.0
                list.each { sum += it as BigDecimal }
BigDecimal avg = cleanDecimalForJson(sum / list.size())
                legacyRecords << [date: ds, room: roomNames[roomId] ?: roomId, mode: hvacMode, avg: avg]
              }
            }
          }
        }
      } catch (ignore) { }
    }
      if (!legacyRecords || legacyRecords.isEmpty()) { return '<p>No daily statistics available.</p>' }
    legacyRecords.sort { a, b -> b.date <=> a.date }
int page = (settings?.dailySummaryPage ?: 1) as int
    int totalPages = ((legacyRecords.size() - 1) / DAILY_SUMMARY_PAGE_SIZE) + 1
    if (page < 1) { page = 1 }
      if (page > totalPages) { page = totalPages }
int start = (page - 1) * DAILY_SUMMARY_PAGE_SIZE
    int end = Math.min(start + DAILY_SUMMARY_PAGE_SIZE, legacyRecords.size())
    def pageRecords = legacyRecords.subList(start, end)
    def htmlLegacy = new StringBuilder()
    htmlLegacy << "<p>Page ${page} of ${totalPages}</p>"
    htmlLegacy << "<table style='width:100%;border-collapse:collapse;'>"
    htmlLegacy << "<tr><th style='text-align:left;padding:4px;'>Date</th><th style='text-align:left;padding:4px;'>Room</th><th style='text-align:left;padding:4px;'>Mode</th><th style='text-align:right;padding:4px;'>Avg</th></tr>"
    pageRecords.each { r ->
      htmlLegacy << "<tr><td style='text-align:left;padding:4px;'>${r.date}</td><td style='text-align:left;padding:4px;'>${r.room}</td><td style='text-align:left;padding:4px;'>${r.mode}</td><td style='text-align:right;padding:4px;'>${roundBigDecimal(r.avg)}</td></tr>"
    }
    htmlLegacy << '</table>'
    return htmlLegacy.toString()
  }

def records = []
  stats.each { roomId, modeMap ->
    modeMap.each { hvacMode, list ->
      list.each { rec ->
        records << [date: rec.date, room: roomNames[roomId] ?: roomId, mode: hvacMode, avg: rec.avg]
      }
    }
  }
      if (!records) { return '<p>No daily statistics available.</p>' }
  records.sort { a, b -> b.date <=> a.date }
int page = (settings?.dailySummaryPage ?: 1) as int
  int totalPages = ((records.size() - 1) / DAILY_SUMMARY_PAGE_SIZE) + 1
  if (page < 1) { page = 1 }
      if (page > totalPages) { page = totalPages }
int start = (page - 1) * DAILY_SUMMARY_PAGE_SIZE
  int end = Math.min(start + DAILY_SUMMARY_PAGE_SIZE, records.size())
  def pageRecords = records.subList(start, end)
  def html = new StringBuilder()
  html << "<p>Page ${page} of ${totalPages}</p>"
  html << "<table style='width:100%;border-collapse:collapse;'>"
  html << "<tr><th style='text-align:left;padding:4px;'>Date</th><th style='text-align:left;padding:4px;'>Room</th><th style='text-align:left;padding:4px;'>Mode</th><th style='text-align:right;padding:4px;'>Avg</th></tr>"
  pageRecords.each { r ->
    html << "<tr><td style='text-align:left;padding:4px;'>${r.date}</td><td style='text-align:left;padding:4px;'>${r.room}</td><td style='text-align:left;padding:4px;'>${r.mode}</td><td style='text-align:right;padding:4px;'>${roundBigDecimal(r.avg)}</td></tr>"
  }
  html << '</table>'
  html.toString()
}// ------------------------------
// End of Core Functions
// ------------------------------
// ------------------------------
// HTTP Async Callback Shims
// ------------------------------

// (removed) legacy asyncHttpGetWrapper shim; replaced by centralized asyncHttpCallback

def quickControlsPage() {
  dynamicPage(name: 'quickControlsPage', title: '\u26A1 Quick Controls', install: false, uninstall: false) {
    section('Per-Room Status & Controls') {
      def vents = getChildDevices()?.findAll { it.hasAttribute('percent-open') } ?: []
      // Build 1 row per room
      def byRoom = [:]
      state.qcDeviceMap = [:]
      state.qcRoomMap = [:]
      vents.each { v ->
        def rid = v.currentValue('room-id') ?: v.getDeviceNetworkId()
        if (!byRoom.containsKey(rid)) { byRoom[rid] = v }
      }
      byRoom.each { roomId, v ->
        Integer cur = (v.currentValue('percent-open') ?: v.currentValue('level') ?: 0) as int
        def vid = v.getDeviceNetworkId()
        def roomName = v.currentValue('room-name') ?: v.getLabel()
        def tempC = v.currentValue('room-current-temperature-c')
        def setpC = v.currentValue('room-set-point-c')
        def active = v.currentValue('room-active')
        def upd = v.currentValue('updated-at') ?: ''
        def batt = v.currentValue('battery') ?: ''
        def toF = { c -> c != null ? (((c as BigDecimal) * 9/5) + 32) : null }

def fmt1 = { x -> x != null ? (((x as BigDecimal) * 10).round() / 10) : '-' }

def tempF = fmt1(toF(tempC))
        def setpF = fmt1(toF(setpC))
        def vidKey = vid.replaceAll('[^A-Za-z0-9_]', '_')
        def roomKey = roomId.replaceAll('[^A-Za-z0-9_]', '_')
        state.qcDeviceMap[vidKey] = vid
        state.qcRoomMap[roomKey] = roomId
        paragraph "<b>${roomName}</b> - Vent: ${cur}% | Temp: ${tempF} °F | Setpoint: ${setpF} °F | Active: ${active ?: 'false'}" + (batt ? " | Battery: ${batt}%" : "") + (upd ? " | Updated: ${upd}" : "")
        input name: "qc_${vidKey}_percent", type: 'number', title: 'Set vent percent', required: false, submitOnChange: false
        input name: "qc_room_${roomKey}_setpoint", type: 'number', title: 'Set room setpoint (°F)', required: false, submitOnChange: false
        input name: "qc_room_${roomKey}_active", type: 'enum', title: 'Set room active', options: ['true','false'], required: false, submitOnChange: false
      }
      input name: 'applyQuickControlsNow', type: 'button', title: 'Apply All Changes', submitOnChange: true
    }
    section('Active Rooms Now') {
      def vents = getChildDevices()?.findAll { it.hasAttribute('percent-open') } ?: []
      def actives = vents.findAll { (it.currentValue('room-active') ?: 'false') == 'true' }
      if (actives) {
        actives.each { v -> paragraph("* ${v.getLabel()}") }
      } else {
        paragraph 'No rooms are currently marked active.'
      }
    }
    section('Bulk Actions') {
      input name: 'openAll', type: 'button', title: 'Open All 100%', submitOnChange: true
      input name: 'closeAll', type: 'button', title: 'Close All (to floor)', submitOnChange: true
      input name: 'setManualAll', type: 'button', title: 'Set Manual for all edited vents', submitOnChange: true
      input name: 'setAutoAll', type: 'button', title: 'Set Auto for all vents', submitOnChange: true
    }
    section('Actions') {
      if (settings?.applyQuickControlsNow) { applyQuickControls(); app.updateSetting('applyQuickControlsNow','') }
      if (settings?.openAll) { openAllSelected(100); app.updateSetting('openAll','') }
      if (settings?.closeAll) { openAllSelected(settings?.allowFullClose ? 0 : (settings?.minVentFloorPercent ?: 0)); app.updateSetting('closeAll','') }
      if (settings?.setManualAll) { manualAllEditedVents(); app.updateSetting('setManualAll','') }
      if (settings?.setAutoAll) { clearAllManualOverrides(); app.updateSetting('setAutoAll','') }
    }
    section {
      href name: 'backToMain', title: '\u2795 Back to Main Settings', description: 'Return to the main app configuration', page: 'mainPage'
    }
  }
}
def applyQuickControls() {
    def overrides = atomicState?.manualOverrides ?: [:]
    def allKeys = (settings?.keySet() ?: []) as List
    def deviceMap = state?.qcDeviceMap ?: [:]
    def roomMap = state?.qcRoomMap ?: [:]
    // Per-vent percent controls
    def pctKeys = allKeys.findAll { (it as String).startsWith('qc_') && (it as String).endsWith('_percent') }
    pctKeys.each { k ->
      def sid = (k as String).replace('qc_','').replace('_percent','')
      def vid = deviceMap[sid] ?: sid
      def v = getChildDevice(vid)
      if (!v) { return }

def val = settings[k]
      if (val != null && val != "") {
        Integer pct = (val as Integer)
        // Enforce floor for manual entries unless full close allowed
        try {
          if (!(settings?.allowFullClose == true)) {
            int floor = ((settings?.minVentFloorPercent ?: 0) as int)
            if (pct < floor) { pct = floor }
          }
        } catch (ignore) { }
        overrides[vid] = pct
        patchVent(v, pct)
        app.updateSetting(k, '')
      }
    }
// Per-room setpoint controls
    def spKeys = allKeys.findAll { (it as String).startsWith('qc_room_') && (it as String).endsWith('_setpoint') }
    spKeys.each { k ->
      def sid = (k as String).replace('qc_room_','').replace('_setpoint','')
      def roomId = roomMap[sid] ?: sid
      def v = getChildDevices()?.find { it.hasAttribute('percent-open') && (it.currentValue('room-id')?.toString() == roomId) }

def val = settings[k]
      if (v && val != null && val != "") {
        try {
          BigDecimal temp = (val as BigDecimal)
          patchRoomSetPoint(v, temp)
        } catch (ignore) { }
        app.updateSetting(k, '')
      }
    }
// Per-room active controls
    def activeKeys = allKeys.findAll { (it as String).startsWith('qc_room_') && (it as String).endsWith('_active') }
    activeKeys.each { k ->
      def sid = (k as String).replace('qc_room_','').replace('_active','')
      def roomId = roomMap[sid] ?: sid
      def v = getChildDevices()?.find { it.hasAttribute('percent-open') && (it.currentValue('room-id')?.toString() == roomId) }

def val = settings[k]
      if (v && (val == 'true' || val == 'false')) {
        patchRoom(v, val)
        app.updateSetting(k, '')
      }
    }
    atomicState.manualOverrides = overrides
    refreshVentTiles()
}
def openAllSelected(Integer pct) {
  def vents = getChildDevices()?.findAll { it.hasAttribute('percent-open') } ?: []
  // Enforce floor unless full close allowed
  try {
    if (!(settings?.allowFullClose == true)) {
      int floor = ((settings?.minVentFloorPercent ?: 0) as int)
      if (pct < floor) { pct = floor }
    }
  } catch (ignore) { }
// Set a manual override for stickiness and patch
  def overrides = atomicState?.manualOverrides ?: [:]
  vents.each { v ->
    try {
      overrides[v.getDeviceNetworkId()] = pct
      patchVent(v, pct)
    } catch (ignore) { }
  }
  atomicState.manualOverrides = overrides
}
def manualAllEditedVents() {
    def keys = settings?.keySet()?.findAll { (it as String).startsWith('qc_') && (it as String).endsWith('_percent') } ?: []
    def overrides = atomicState?.manualOverrides ?: [:]
    def deviceMap = state?.qcDeviceMap ?: [:]
    keys.each { k ->
      def sid = (k as String).replace('qc_','').replace('_percent','')
      def vid = deviceMap[sid] ?: sid
      def val = settings[k]
      if (val != null && val != "") { overrides[vid] = (val as Integer) }
    }
    atomicState.manualOverrides = overrides
    refreshVentTiles()
  }
def buildDiagnosticsJson() {
  def vents = getChildDevices()?.findAll { it.hasAttribute('percent-open') } ?: []
  def ventSummaries = vents.collect { v ->
    [
      id: v.getDeviceNetworkId(),
      roomId: v.currentValue('room-id'),
      room: v.currentValue('room-name'),
      percent: v.currentValue('percent-open') ?: v.currentValue('level'),
      tempC: v.currentValue('room-current-temperature-c'),
      battery: v.currentValue('battery'),
      voltage: (v.currentValue('voltage') ?: v.currentValue('system-voltage'))
    ]
  }

def snapshot = [
    ts: new Date().format('yyyy-MM-dd HH:mm:ss', location?.timeZone ?: TimeZone.getTimeZone('UTC')),
    hvacMode: atomicState?.thermostat1State?.mode ?: getThermostat1Mode() ?: atomicState?.lastHvacMode,
    setpointSource: (settings?.thermostat1 ? 'thermostat' : (ventSummaries.find { it.setpointC } ? 'room' : 'defaults')),
    activeRequests: atomicState?.activeRequests ?: 0,
    failureCounts: (atomicState?.failureCounts ?: [:]).collectEntries { k,v -> [(k): v] },
    circuitOpenUntil: (state?.circuitOpenUntil ?: [:]).collectEntries { k,v -> [(k): v] },
    ventOpenDiscrepancies: (state?.ventOpenDiscrepancies ?: state?.ventPatchDiscrepancies ?: [:]),
    vents: ventSummaries.take(50),
    lastActivity: (atomicState?.dabActivityLog ?: []).takeRight(10)
  ]
  try { return groovy.json.JsonOutput.toJson(snapshot) } catch (ignore) { return '{}' }
}
def buildRawCacheJson() {
  try {
    def list = (atomicState?.rawDabSamplesEntries ?: [])
    def trimmed = list.size() > 5000 ? list[-5000..-1] : list
    def payload = [
      generatedAt: new Date().format('yyyy-MM-dd HH:mm:ss', location?.timeZone ?: TimeZone.getTimeZone('UTC')),
      retentionHours: settings?.rawDataRetentionHours ?: RAW_CACHE_DEFAULT_HOURS,
      entries: trimmed
    ]
    def json = groovy.json.JsonOutput.toJson(payload)
    log(2, 'App', "Raw cache export: ${trimmed.size()} entries")
    return json
  } catch (e) {
    log(2, 'App', "Raw cache export failed: ${e?.message}")
    return '{}'
  }
}
def clearRawCache() {
  try {
    atomicState.remove('rawDabSamplesEntries')
    atomicState.remove('rawDabLastByVent')
    log(2, 'App', 'Cleared raw data cache')
  } catch (ignore) { }
}

def dabHealthMonitor() {
  try {
    def issues = []
    if (settings?.fanOnlyOpenAllVents && isFanActive()) {
      def vents = getChildDevices()?.findAll { it.hasAttribute('percent-open') } ?: []
      def notOpen = vents.findAll { ((it.currentValue('percent-open') ?: 0) as int) < 95 }
      if (notOpen) { issues << "Fan-only active but ${notOpen.size()} vents not ~100% open" }
    }

def ar = atomicState?.activeRequests ?: 0
    if (ar >= MAX_CONCURRENT_REQUESTS) { issues << "Active requests stuck at ${ar}/${MAX_CONCURRENT_REQUESTS}" }
      if ((state?.ventOpenDiscrepancies ?: state?.ventPatchDiscrepancies)) { issues << 'Outstanding vent-open discrepancies present' }
      if (issues) {
      issues.each { msg -> logWarn msg, 'DAB' }
      try { appendDabActivityLog("Health: " + issues.join('; ')) } catch (ignore) { }
    }
  } catch (e) {
    try { logWarn("Health monitor error: ${e?.message}", 'DAB') } catch (ignore) { }
  }
}

// DAB Live Diagnostics page to run a one-off calculation and display details
// dabLiveDiagnosticsPage is provided by DabUIManager library
// Execute a live diagnostic pass of DAB calculations without changing device state
void runDabDiagnosticLocal() {
  def results = [:]

  // Inputs
  String hvacMode = calculateHvacModeRobust() ?: 'idle'
  BigDecimal globalSp = getGlobalSetpoint(hvacMode)
  results.inputs = [ hvacMode: hvacMode, globalSetpoint: globalSp, rooms: [:] ]

  def vents = getChildDevices()?.findAll { it.hasAttribute('percent-open') } ?: []
  vents.each { vent ->
    try {
      def roomId = vent.currentValue('room-id')?.toString()
      if (roomId && !results.inputs.rooms[roomId]) {
        results.inputs.rooms[roomId] = [
          name: vent.currentValue('room-name') ?: roomId,
          temp: vent.currentValue('room-current-temperature-c'),
          rate: getAverageHourlyRate(roomId, hvacMode, (new Date().format('H', location?.timeZone ?: TimeZone.getTimeZone('UTC')) as Integer))
        ]
      }
    } catch (ignore) { }
  }
// Build ventsByRoomId mapping (roomId -> List of ventIds)
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
// Calculations
  def rateAndTempPerVentId = getAttribsPerVentIdWeighted(ventsByRoomId, hvacMode)
  def longestTimeToTarget = calculateLongestMinutesToTarget(rateAndTempPerVentId, hvacMode, globalSp, (atomicState.maxHvacRunningTime ?: MAX_MINUTES_TO_SETPOINT), settings.thermostat1CloseInactiveRooms)
  def initialPositions = calculateOpenPercentageForAllVents(rateAndTempPerVentId, hvacMode, globalSp, longestTimeToTarget, settings.thermostat1CloseInactiveRooms)
  def minAirflowAdjusted = adjustVentOpeningsToEnsureMinimumAirflowTarget(rateAndTempPerVentId, hvacMode, initialPositions, settings.thermostat1AdditionalStandardVents)
  def finalPositions = applyOverridesAndFloors(minAirflowAdjusted)

  results.calculations = [ longestTimeToTarget: longestTimeToTarget, initialVentPositions: initialPositions ]
  results.adjustments = [ minimumAirflowAdjustments: minAirflowAdjusted ]
  results.finalOutput = [ finalVentPositions: finalPositions ]

  state.dabDiagnosticResult = results
}// Render diagnostic results as an HTML snippet (paragraph-safe)
String renderDabDiagnosticResultsLocal() {
  def results = state?.dabDiagnosticResult
  if (!results) { return '<p>No diagnostic results to display.</p>' }

def sb = new StringBuilder()
  sb << '<h3>Inputs</h3>'
  sb << "<p><b>HVAC Mode:</b> </p>"
  sb << "<p><b>Global Setpoint:</b>  &amp;deg;C</p>"
  sb << '<h4>Room Data</h4>'
  sb << "<table border='1' style='width:100%'><tr><th>Room</th><th>Temp (&amp;deg;C)</th><th>Rate</th></tr>"
  results.inputs.rooms.each { roomId, roomData ->
    sb << "<tr><td></td><td></td><td></td></tr>"
  }
  sb << '</table>'

  sb << '<h3>Calculations</h3>'
  sb << "<p><b>Longest Time to Target:</b>  minutes</p>"
  sb << '<h4>Initial Vent Positions</h4>'
  sb << "<table border='1' style='width:100%'><tr><th>Vent</th><th>Position</th></tr>"
  results.calculations.initialVentPositions?.each { ventId, pos ->
    try { sb << "<tr><td></td><td>%</td></tr>" } catch (ignore) { }
  }
  sb << '</table>'

  sb << '<h3>Adjustments</h3>'
  sb << '<h4>Minimum Airflow Adjustments</h4>'
  sb << "<table border='1' style='width:100%'><tr><th>Vent</th><th>Position</th></tr>"
  results.adjustments.minimumAirflowAdjustments?.each { ventId, pos ->
    try { sb << "<tr><td></td><td>%</td></tr>" } catch (ignore) { }
  }
  sb << '</table>'

  sb << '<h3>Final Output</h3>'
  sb << '<h4>Final Vent Positions</h4>'
  sb << "<table border='1' style='width:100%'><tr><th>Vent</th><th>Position</th></tr>"
  results.finalOutput.finalVentPositions?.each { ventId, pos ->
    try { sb << "<tr><td></td><td>%</td></tr>" } catch (ignore) { }
  }
  sb << '</table>'
  return sb.toString()
}
// Centralized async HTTP callback handler
def asyncHttpCallback(response, Map data) {
  try {
    String originalCallback = data.originalCallback
    if (originalCallback && this.metaClass.respondsTo(this, originalCallback)) {
      // Dynamically call the original intended callback
      this."$originalCallback"(response, data)
    }
  } catch (Exception e) {
    logError("Error in async callback for ${data?.uri}: ${e.message}", "HTTP", data?.uri)
  } finally {
    // This is the crucial part: decrement the counter no matter what.
    decrementActiveRequests()
  }
}

// DAB efficiency helpers
def handleExportEfficiencyData() {
  try {
    def data = exportEfficiencyData()
    state.exportJsonData = generateEfficiencyJSON(data)
    state.exportStatus = 'Export successful'
  } catch (e) {
    state.exportStatus = 'Export failed'
    log(2, 'App', state.exportStatus)
  }
}

def handleClearExportData() {
  try {
    state.remove('exportJsonData')
    state.exportStatus = 'Cleared'
  } catch (ignored) { }
}

// Async builder wrappers used by UI pages

// handleImportEfficiencyData is provided by DabUIManager library
// Optional export/clear handlers are omitted in app; rely on library pages
// DAB lifecycle wrappers to delegate logic to DabManager (ensure consistent runtime usage)
// initializeRoomStates/finalizeRoomStates are provided by DabManager library
// --- DAB UI Page Wrappers (delegated to DabUIManager) ---
// efficiencyDataPage is provided by DabUIManager library

// dabChartPage is provided by DabUIManager library

// dabRatesTablePage is provided by DabUIManager library

// dabActivityLogPage is provided by DabUIManager library

// dabHistoryPage is provided by DabUIManager library

// dabProgressPage is provided by DabUIManager library

// dabDailySummaryPage is provided by DabUIManager library
// Async builder wrappers used by UI pages
def buildDabRatesTableWrapper(Map data) { try { buildDabRatesTable(data) } catch (ignore) { } }

def buildDabProgressTableWrapper(Map data) { try { buildDabProgressTable(data) } catch (ignore) { } }

