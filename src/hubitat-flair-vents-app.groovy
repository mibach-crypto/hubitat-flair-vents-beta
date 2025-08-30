/**
 *  Hubitat Flair Vents Integration
 *  Version 0.239
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

// ------------------------------
// Constants and Configuration
// ------------------------------

DabManager dabManager
DabUIManager dabUIManager

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

// Threshold (in ÃƒÂ¢Ã¢â‚¬ÂÃ‚Â¬ÃƒÂ¢Ã¢â‚¬â€œÃ¢â‚¬ËœC) used to trigger a pre-adjustment of vent settings before the setpoint is reached.
@Field static final BigDecimal VENT_PRE_ADJUST_THRESHOLD = 0.2

// HVAC timing constants.
@Field static final BigDecimal MAX_MINUTES_TO_SETPOINT = 60       // Maximum minutes to reach setpoint.
@Field static final BigDecimal MIN_MINUTES_TO_SETPOINT = 1        // Minimum minutes required to compute temperature change rate.

// Temperature offset (in ÃƒÂ¢Ã¢â‚¬ÂÃ‚Â¬ÃƒÂ¢Ã¢â‚¬â€œÃ¢â‚¬ËœC) applied to thermostat setpoints.
@Field static final BigDecimal SETPOINT_OFFSET = 0.7

// Acceptable temperature change rate limits (in ÃƒÂ¢Ã¢â‚¬ÂÃ‚Â¬ÃƒÂ¢Ã¢â‚¬â€œÃ¢â‚¬ËœC per minute).
@Field static final BigDecimal MAX_TEMP_CHANGE_RATE = 1.5
@Field static final BigDecimal MIN_TEMP_CHANGE_RATE = 0.001

// Temperature sensor accuracy and noise filtering
@Field static final BigDecimal TEMP_SENSOR_ACCURACY = 0.5  // ÃƒÂ¢Ã¢â‚¬ÂÃ‚Â¬ÃƒÂ¢Ã¢â‚¬â€œÃ¢â‚¬â„¢0.5ÃƒÂ¢Ã¢â‚¬ÂÃ‚Â¬ÃƒÂ¢Ã¢â‚¬â€œÃ¢â‚¬ËœC typical sensor accuracy
@Field static final BigDecimal MIN_DETECTABLE_TEMP_CHANGE = 0.1  // Minimum change to consider real
@Field static final Integer MIN_RUNTIME_FOR_RATE_CALC = 5  // Minimum minutes before calculating rate

// Minimum combined vent airflow percentage across all vents (to ensure proper HVAC operation).
@Field static final BigDecimal MIN_COMBINED_VENT_FLOW = 30.0

// INCREMENT_PERCENTAGE is used as a base multiplier when incrementally increasing vent open percentages
// during airflow adjustments. For example, if the computed proportion for a vent is 0.5,
// then the ventÃƒÅ½Ã¢â‚¬Å“ÃƒÆ’Ã¢â‚¬Â¡ÃƒÆ’Ã¢â‚¬â€œs open percentage will be increased by 1.5 * 0.5 = 0.75% in that iteration.
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

// Temperature tolerance for rebalancing vent operations (in ÃƒÂ¢Ã¢â‚¬ÂÃ‚Â¬ÃƒÂ¢Ã¢â‚¬â€œÃ¢â‚¬ËœC).
@Field static final BigDecimal REBALANCING_TOLERANCE = 0.5

// Temperature boundary adjustment for airflow calculations (in ÃƒÂ¢Ã¢â‚¬ÂÃ‚Â¬ÃƒÂ¢Ã¢â‚¬â€œÃ¢â‚¬ËœC).
@Field static final BigDecimal TEMP_BOUNDARY_ADJUSTMENT = 0.1

// Thermostat hysteresis to prevent cycling (in ÃƒÂ¢Ã¢â‚¬ÂÃ‚Â¬ÃƒÂ¢Ã¢â‚¬â€œÃ¢â‚¬ËœC).
@Field static final BigDecimal THERMOSTAT_HYSTERESIS = 0.6  // ~1ÃƒÂ¢Ã¢â‚¬ÂÃ‚Â¬ÃƒÂ¢Ã¢â‚¬â€œÃ¢â‚¬ËœF

// Minimum average difference between duct and room temperature (in ÃƒÂ¢Ã¢â‚¬ÂÃ‚Â¬ÃƒÂ¢Ã¢â‚¬â€œÃ¢â‚¬ËœC)
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
}\r\n\r\ndef mainPage() {
  def validation = validatePreferences()
  if (settings?.validateNow) {
    performValidationTest()
    app.updateSetting('validateNow', null)
  }\r\n\r\n  dynamicPage(name: 'mainPage', title: 'Setup', install: validation.valid, uninstall: true) {
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
      }\r\n\r\n      if (settings?.clientId && settings?.clientSecret) {
        if (!state.flairAccessToken && !state.authInProgress) {
          state.authInProgress = true
          state.remove('authError')  // Clear any previous error when starting new auth
          runIn(2, 'autoAuthenticate')
        }\r\n\r\n        if (state.flairAccessToken && !state.authError) {
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
        }\r\n\r\n      }\r\n\r\n    }\r\n\r\n    if (state.flairAccessToken) {
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
      }\r\n\r\n      if (state.ventOpenDiscrepancies) {
        section('Vent Synchronization Issues') {
          state.ventOpenDiscrepancies.each { id, info ->
            paragraph "<span style='color: red;'>${info.name ?: id} expected ${info.target}% but reported ${info.actual}%</span>"
          }
        }
        // Close discrepancies block before proceeding to DAB section
      }\r\n\r\n      // Removed stray brace to fix if/else structure

      section('<h2>Dynamic Airflow Balancing</h2>') {
        input name: 'dabEnabled', type: 'bool', title: 'Use Dynamic Airflow Balancing', defaultValue: false, submitOnChange: true
      }
      if (dabEnabled) {
        section('Thermostat & Globals') {
          input name: 'thermostat1', type: 'capability.thermostat', title: 'Optional: Thermostat for global setpoint', multiple: false, required: false
          input name: 'thermostat1TempUnit', type: 'enum', title: 'Units used by Thermostat', defaultValue: 2,
                options: [1: 'Celsius (ÃƒÂ¢Ã¢â‚¬ÂÃ‚Â¬ÃƒÂ¢Ã¢â‚¬â€œÃ¢â‚¬ËœC)', 2: 'Fahrenheit (ÃƒÂ¢Ã¢â‚¬ÂÃ‚Â¬ÃƒÂ¢Ã¢â‚¬â€œÃ¢â‚¬ËœF)']
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
          try { atomicState.dabHistoryRetentionDays = (settings?.dabHistoryRetentionDays ?: DEFAULT_HISTORY_RETENTION_DAYS) as Integer } catch (ignore) { }\r\n\r\n          if (settings.thermostat1AdditionalStandardVents < 0) {
            app.updateSetting('thermostat1AdditionalStandardVents', 0)
          } else if (settings.thermostat1AdditionalStandardVents > MAX_STANDARD_VENTS) {
            app.updateSetting('thermostat1AdditionalStandardVents', MAX_STANDARD_VENTS)
          }\r\n\r\n          if (!getThermostat1Mode() || getThermostat1Mode() == 'auto') {
            patchStructureData([mode: 'manual'])
            atomicState?.putAt('thermostat1Mode', 'manual')
        }
          }
          
          // Quick Safety Limits
          section('Quick Safety Limits') {
            input name: 'allowFullClose', type: 'bool', title: 'Allow vents to fully close (0%)', defaultValue: false, submitOnChange: true
            input name: 'minVentFloorPercent', type: 'number', title: 'Minimum vent opening floor (%)', defaultValue: 10, submitOnChange: true
          }\r\n\r\n          // Night override (simple schedule)
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
          }\r\n\r\n          // Polling intervals (registered so validators accept settings reads)
          section('Polling Intervals') {
            input name: 'pollingIntervalActive', type: 'number', title: 'Active HVAC polling interval (minutes)', defaultValue: 1, submitOnChange: true
            input name: 'pollingIntervalIdle', type: 'number', title: 'Idle polling interval (minutes)', defaultValue: 10, submitOnChange: true
          }\r\n\r\n      // Dashboard tiles
      section('Dashboard Tiles') {
        input name: 'enableDashboardTiles', type: 'bool', title: 'Enable vent dashboard tiles', defaultValue: false, submitOnChange: true
        input name: 'syncVentTiles', type: 'button', title: 'Create/Sync Tiles', submitOnChange: true
        if (settings?.syncVentTiles) {
          try { syncVentTiles() } catch (e) { logError "Tile sync failed: ${e?.message}" } finally { app.updateSetting('syncVentTiles','') }
        }
      }\r\n\r\n          // Raw Data Cache (for diagnostics and optional DAB calculations)
          section('Raw Data Cache') {
            input name: 'enableRawCache', type: 'bool', title: 'Enable raw data cache (24h)', defaultValue: true, submitOnChange: true
            input name: 'rawDataRetentionHours', type: 'number', title: 'Raw data retention (hours)', defaultValue: RAW_CACHE_DEFAULT_HOURS, submitOnChange: true
            input name: 'useCachedRawForDab', type: 'bool', title: 'Calculate DAB using cached raw data', defaultValue: false, submitOnChange: true
          }\r\n\r\n  // Data smoothing and robustness (optional)
          section('DAB Data Smoothing (optional)') {
            input name: 'enableEwma', type: 'bool', title: 'Use EWMA smoothing for hourly averages', defaultValue: false, submitOnChange: true
            input name: 'ewmaHalfLifeDays', type: 'number', title: 'EWMA half-life (days per hour-slot)', defaultValue: 3, submitOnChange: true
            input name: 'enableOutlierRejection', type: 'bool', title: 'Robust outlier handling (MAD)', defaultValue: true, submitOnChange: true
            input name: 'outlierThresholdMad', type: 'number', title: 'Outlier threshold (k ÃƒÂ¢Ã¢â‚¬ÂÃ…â€œÃƒÆ’Ã‚Â¹ MAD)', defaultValue: 3, submitOnChange: true
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
      }\r\n\r\n
      section('Vent Options') {
        input name: 'ventGranularity', type: 'enum', title: 'Vent Adjustment Granularity (in %)',
              options: ['5':'5%', '10':'10%', '25':'25%', '50':'50%', '100':'100%'],
              defaultValue: '5', required: true, submitOnChange: true
        paragraph '<small>Select how granular the vent adjustments should be. For example, if you choose 50%, vents ' +
                  'will only adjust to 0%, 50%, or 100%. Lower percentages allow for finer control, but may ' +
                  'result in more frequent adjustments (which could affect battery-powered vents).</small>'
      }\r\n\r\n      // Optional per-vent weighting within a room (to bias distribution)
      section('Per-Vent Weighting (optional)') {
        vents.each { v ->
          input name: "vent${v.getId()}Weight", type: 'number', title: "Weight for ${v.getLabel()} (default 1.0)", defaultValue: 1.0, submitOnChange: true
        }
        paragraph '<small>When a room has multiple vents, the system calculates a room-level target and then vents are adjusted individually. ' +
                  'Weights bias openings within a room: higher weight => relatively more opening. Leave at 1.0 for equal weighting.</small>'
      }\r\n\r\n      if (state.ventPatchDiscrepancies) {
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
    }\r\n\r\n    section('Validation') {
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
}\r\n\r\n// Simple, Hubitat-compatible control panel (no JS required)
def flairControlPanel() {
  dynamicPage(name: 'flairControlPanel', title: 'Flair Control Panel', install: false, uninstall: false) {
    def vents = getChildDevices()?.findAll { it.hasAttribute('percent-open') } ?: []
    if (!vents) {
      section {
        paragraph 'No vents available. Run discovery from the main page.'
      }
      return
    }\r\n\r\n    // Build 1 representative device per room
    def byRoom = [:]
    vents.each { v ->
      def rid = v.currentValue('room-id') ?: v.getDeviceNetworkId()
      if (!byRoom.containsKey(rid)) { byRoom[rid] = [] }
      byRoom[rid] << v
    }\r\n\r\n    // Actions (apply immediately when buttons are pressed)
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
        }\r\n\r\n        // Handle presses inline
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
    }\r\n\r\n    section {
      href name: 'backToMain', title: 'Back to Main', description: 'Return to main settings', page: 'mainPage'
    }
  }
}\r\n\r\ndef diagnosticsPage() {
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
}\r\n\r\n// New, styled control panel compatible with Hubitat pages (no JS required)
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
    }\r\n\r\n    def vents = getChildDevices()?.findAll { it.hasAttribute('percent-open') } ?: []
    if (!vents) {
      section { paragraph 'No vents available. Run discovery from the main page.' }
      return
    }\r\n\r\n    def rooms = [:]
    vents.each { dv ->
      def rid = dv.currentValue('room-id') ?: dv.getDeviceNetworkId()
      (rooms[rid] = (rooms[rid] ?: []) ) << dv
    }\r\n\r\n    rooms.each { roomId, list ->
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
        }\r\n\r\n        if (settings?."cp2_room_${roomId}_sp_up") {
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
    }\r\n\r\n    section { href name: 'backToMain', title: 'Back to Main', description: 'Return to main settings', page: 'mainPage' }
  }
}\r\n\r\n// Backend helper for future client use (JSON string)
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
}\r\n\r\ndef performHealthCheck() {
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
}\r\n\r\ndef resetCaches() {
  def instanceId = getInstanceId()
  def cacheKey = "instanceCache_${instanceId}"
  ['roomCache', 'roomCacheTimestamps', 'deviceCache', 'deviceCacheTimestamps',
   'pendingRoomRequests', 'pendingDeviceRequests', 'initialized'].each { suffix ->
    state.remove("${cacheKey}_${suffix}")
  }
  log 'Instance caches cleared', 2
}\r\n\r\n// ------------------------------
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
  }\r\n\r\n  def builder = new StringBuilder()
  builder << '''
  <style>
    .device-table { width: 100%; border-collapse: collapse; font-family: Arial, sans-serif; color: black; }
    .device-table th, .device-table td { padding: 8px; text-align: left; border-bottom: 1px solid #ddd; }
    .device-table th { background-color: #f2f2f2; color: #333; }
    .device-table tr:hover { background-color: #f5f5f5; }
    .device-table a { color: #333; text-decoration: none; }
    .device-table a:hover { color: #666; }
    .device-table th:not(:first-child), .device-table td:not(:first-child) { text-align: center; }
    .warning-message { color: darkorange; cursor: pointer; }
    .danger-message { color: red; cursor: pointer; }
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
    def warnMsg = 'This vent is very inefficient, consider installing an HVAC booster. Click for a recommendation.'

    def coolClass = coolEfficiency <= 25 ? 'danger-message' : (coolEfficiency <= 45 ? 'warning-message' : '')
    def heatClass = heatEfficiency <= 25 ? 'danger-message' : (heatEfficiency <= 45 ? 'warning-message' : '')

    def coolHtml = coolEfficiency <= 45 ? "<span class='${coolClass}' onclick=\"window.open('${acBoosterLink}');\" title='${warnMsg}'>${coolEfficiency}%</span>" : "${coolEfficiency}%"
    def heatHtml = heatEfficiency <= 45 ? "<span class='${heatClass}' onclick=\"window.open('${acBoosterLink}');\" title='${warnMsg}'>${heatEfficiency}%</span>" : "${heatEfficiency}%"

    builder << "<tr><td><a href='/device/edit/${vent.getId()}'>${vent.getLabel()}</a></td><td>${coolHtml}</td><td>${heatHtml}</td></tr>"
  }
  builder << '</tbody></table>'

  section {
    paragraph 'Discovered devices:'
    paragraph builder.toString()
  }
}\r\n\r\ndef getStructureId() {
  if (!settings?.structureId) { getStructureData() }
  return settings?.structureId
}\r\n\r\ndef updated() {
  log.debug 'Hubitat Flair App updating'
  // Clear cached HTML so pages rebuild after setting changes
  try { state.remove('dabRatesTableHtml') } catch (ignore) { }
  try { state.remove('dabProgressTableHtml') } catch (ignore2) { }
  initializeDabHistory()
  initialize()
}

def installed() {
  log.debug 'Hubitat Flair App installed'
  initializeDabHistory()
  initialize()
}\r\n\r\ndef uninstalled() {
  log.debug 'Hubitat Flair App uninstalling'
  removeChildren()
  unschedule()
  unsubscribe()
}\r\n\r\ndef initialize() {
  dabManager = new DabManager(this)
  dabUIManager = new DabUIManager(this, dabManager)
  try { runEvery5Minutes('dabHealthMonitor') } catch (ignore) { }\r\n\r\n  unsubscribe()

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
      // Token exists, ensure hourly refresh is scheduled
      unschedule(login)
      runEvery1Hour(login)
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

  // Schedule/subscribe for tiles and overrides
  if (settings?.enableDashboardTiles) {
    try {
      subscribeToVentEventsForTiles()
      runEvery5Minutes('refreshVentTiles')
    } catch (e) { log(2, 'App', "Tile scheduler/subscription error: ${e?.message}") }
  } else {
    try { unschedule('refreshVentTiles') } catch (ignore) { }
  }\r\n\r\n  if (settings?.nightOverrideEnable) {
    try {
      if (settings?.nightOverrideStart) { schedule(settings.nightOverrideStart, 'activateNightOverride') }
      if (settings?.nightOverrideEnd) { schedule(settings.nightOverrideEnd, 'deactivateNightOverride') }
    } catch (e) { log(2, 'App', "Night override scheduling error: ${e?.message}") }
  } else {
    try { unschedule('activateNightOverride'); unschedule('deactivateNightOverride') } catch (ignore) { }
  }
}\r\n\r\n// ------------------------------
// Helper Functions
// ------------------------------

private openAllVents(Map ventIdsByRoomId, int percentOpen) {
  ventIdsByRoomId.each { roomId, ventIds ->
    ventIds.each { ventId ->
      def vent = getChildDevice(ventId)
      if (vent) { patchVent(vent, percentOpen) }
    }
  }
}\r\n\r\nprivate BigDecimal getRoomTemp(def vent) {
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
      log(2, 'App', "Falling back to room temperature for '${roomName}': ${roomTemp}ÃƒÂ¢Ã¢â‚¬ÂÃ‚Â¬ÃƒÂ¢Ã¢â‚¬â€œÃ¢â‚¬ËœC")
      return roomTemp
    }
    if (settings.thermostat1TempUnit == '2') {
      temp = convertFahrenheitToCentigrade(temp)
    }
    log(2, 'App', "Got temp from ${tempDevice?.getLabel() ?: 'Unknown'} for '${roomName}': ${temp}ÃƒÂ¢Ã¢â‚¬ÂÃ‚Â¬ÃƒÂ¢Ã¢â‚¬â€œÃ¢â‚¬ËœC")
    return temp
  }
  
  def roomTemp = vent.currentValue('room-current-temperature-c')
  if (roomTemp == null) {
    log(2, 'App', "ERROR: No temperature available for room '${roomName}' - neither from Puck nor from room API!")
    return 0
  }
  log(2, 'App', "Using room temperature for '${roomName}': ${roomTemp}ÃƒÂ¢Ã¢â‚¬ÂÃ‚Â¬ÃƒÂ¢Ã¢â‚¬â€œÃ¢â‚¬ËœC")
  return roomTemp
}\r\n\r\nprivate atomicStateUpdate(String stateKey, String key, value) {
  atomicState.updateMapValue(stateKey, key, value)
  log(1, 'App', "atomicStateUpdate(${stateKey}, ${key}, ${value})")
}\r\n\r\n
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
}\r\n\r\ndef roundBigDecimal(BigDecimal number, int scale = 3) {
  number.setScale(scale, BigDecimal.ROUND_HALF_UP)
}\r\n\r\n// Function to round values to specific decimal places for JSON export
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
}\r\n\r\n// Function to clean decimal values for JSON serialization
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
}\r\n\r\n// Modified rounding function that uses the user-configured granularity.
// It has been renamed to roundToNearestMultiple since it rounds a value to the nearest multiple of a given granularity.
int roundToNearestMultiple(BigDecimal num) {
  int granularity = settings.ventGranularity ? settings.ventGranularity.toInteger() : 5
  return (int)(Math.round(num / granularity) * granularity)
}\r\n\r\n
def convertFahrenheitToCentigrade(BigDecimal tempValue) {
  (tempValue - 32) * (5 / 9)
}\r\n\r\ndef rollingAverage(BigDecimal currentAverage, BigDecimal newNumber, BigDecimal weight = 1, int numEntries = 10) {
  if (numEntries <= 0) { return 0 }
  BigDecimal base = (currentAverage ?: 0) == 0 ? newNumber : currentAverage
  BigDecimal sum = base * (numEntries - 1)
  def weightedValue = (newNumber - base) * weight
  def numberToAdd = base + weightedValue
  sum += numberToAdd
  return sum / numEntries
}\r\n\r\ndef hasRoomReachedSetpoint(String hvacMode, BigDecimal setpoint, BigDecimal currentTemp, BigDecimal offset = 0) {
  (hvacMode == COOLING && currentTemp <= setpoint - offset) ||
  (hvacMode == HEATING && currentTemp >= setpoint + offset)
}\r\n\r\n// Determine HVAC mode purely from vent duct temperatures. Returns
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
}\r\n\r\n// Robust HVAC mode detection using median duct-room temperature difference
// with thermostat operating state as a fallback.
def calculateHvacModeRobust() { return dabManager.calculateHvacModeRobust() }
}\r\n\r\ndef resetApiConnection() {
  logWarn 'Resetting API connection'
  atomicState.failureCounts = [:]
  authenticate()
}\r\n\r\ndef noOpHandler(resp, data) {
  log(3, 'App', 'noOpHandler called')
}\r\n\r\ndef login() {
  authenticate()
  getStructureData()
}\r\n\r\ndef authenticate(int retryCount = 0) {
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
}\r\n\r\n// Wrapper method for authenticate retry
def retryAuthenticateWrapper(data) {
  authenticate(data?.retryCount ?: 0)
}\r\n\r\ndef handleAuthResponse(resp, data) {
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
}\r\n\r\ndef appButtonHandler(String btn) {
  switch (btn) {
    case 'authenticate':
      login()
      unschedule(login)
      runEvery1Hour(login)
      break
    case 'retryAuth':
      login()
      unschedule(login)
      runEvery1Hour(login)
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
}\r\n\r\n// Auto-authenticate when credentials are provided
def autoAuthenticate() {
  if (settings?.clientId && settings?.clientSecret && !state.flairAccessToken) {
    log(2, 'App', 'Auto-authenticating with provided credentials')
    login()
    unschedule(login)
    runEvery1Hour(login)
  }
}\r\n\r\n// Automatically re-authenticate when token expires
def autoReauthenticate() {
  log(2, 'App', 'Token expired or invalid, re-authenticating...')
  state.remove('flairAccessToken')
  // Clear any error state
  state.remove('authError')
  // Re-authenticate and reschedule
  if (authenticate() == '') {
    // If authentication succeeded, reschedule hourly refresh
    unschedule(login)
    runEvery1Hour(login)
    log(2, 'App', 'Re-authentication successful, rescheduled hourly token refresh')
  }
}\r\n\r\nprivate void discover() {
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
}\r\n\r\n
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
}\r\n\r\ndef handleRoomsWithPucks(resp, data) {
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
}\r\n\r\n
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
}\r\n\r\ndef makeRealDevice(Map device) {
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
}\r\n\r\ndef getDeviceData(device) {
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
}\r\n\r\n// New function to handle room data with caching
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
}\r\n\r\n// New function to handle device data with caching (for pucks)
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
}\r\n\r\n// New function to handle device reading with caching
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
}\r\n\r\ndef handleRoomGet(resp, data) {
  decrementActiveRequests()  // Always decrement when response comes back
  if (!isValidResponse(resp) || !data?.device) { return }
  processRoomTraits(data.device, resp.getJson())
}\r\n\r\n// Modified handleRoomGet to include caching
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
}\r\n\r\n// Add a method to clear the cache periodically (optional)
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
}\r\n\r\n// Clear device cache periodically
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
}\r\n\r\n// Periodic cleanup of pending request flags
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
}\r\n\r\ndef handleDeviceGet(resp, data) {
  decrementActiveRequests()  // Always decrement when response comes back
  if (!isValidResponse(resp) || !data?.device) { return }
  processVentTraits(data.device, resp.getJson())
}\r\n\r\n// Modified handleDeviceGet to include caching
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
}\r\n\r\ndef handlePuckGet(resp, data) {
  decrementActiveRequests()  // Always decrement when response comes back
  if (!isValidResponse(resp) || !data?.device) { return }
  def respJson = resp.getJson()
  if (respJson?.data) {
    def puckData = respJson.data
    // Extract puck attributes
    if (puckData?.attributes?.'current-temperature-c' != null) {
      def tempC = puckData.attributes['current-temperature-c']
      def tempF = (tempC * 9/5) + 32
      sendEvent(data.device, [name: 'temperature', value: tempF, unit: 'ÃƒÂ¢Ã¢â‚¬ÂÃ‚Â¬ÃƒÂ¢Ã¢â‚¬â€œÃ¢â‚¬ËœF'])
      log(2, 'App', "Puck temperature: ${tempF}ÃƒÂ¢Ã¢â‚¬ÂÃ‚Â¬ÃƒÂ¢Ã¢â‚¬â€œÃ¢â‚¬ËœF")
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
}\r\n\r\n// Modified handlePuckGet to include caching
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
}\r\n\r\n
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
      sendEvent(data.device, [name: 'temperature', value: tempF, unit: 'ÃƒÂ¢Ã¢â‚¬ÂÃ‚Â¬ÃƒÂ¢Ã¢â‚¬â€œÃ¢â‚¬ËœF'])
      log(2, 'App', "Puck temperature from reading: ${tempF}ÃƒÂ¢Ã¢â‚¬ÂÃ‚Â¬ÃƒÂ¢Ã¢â‚¬â€œÃ¢â‚¬ËœF")
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
}\r\n\r\n// Modified handlePuckReadingGet to include caching
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
}\r\n\r\ndef traitExtract(device, details, String propNameData, String propNameDriver = propNameData, unit = null) {
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
}\r\n\r\ndef processVentTraits(device, details) {
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
}\r\n\r\ndef processRoomTraits(device, details) {
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
  }\r\n\r\n  if (details?.data?.relationships?.structure?.data) {
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
}\r\n\r\ndef handleRemoteSensorGet(resp, data) {
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
}\r\n\r\ndef updateByRoomIdState(details) {
  if (!details?.data?.relationships?.vents?.data) { return }
  def roomId = details.data.id?.toString()
  if (!atomicState.ventsByRoomId?.get(roomId)) {
    def ventIds = details.data.relationships.vents.data.collect { it.id }
    atomicStateUpdate('ventsByRoomId', roomId, ventIds)
  }
}\r\n\r\ndef patchStructureData(Map attributes) {
  def body = [data: [type: 'structures', attributes: attributes]]
  def uri = "${BASE_URL}/api/structures/${getStructureId()}"
  patchDataAsync(uri, null, body)
}\r\n\r\ndef getStructureDataAsync(int retryCount = 0) {
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
}\r\n\r\n// Wrapper method for getStructureDataAsync retry
def retryGetStructureDataAsyncWrapper(data) {
  getStructureDataAsync(data?.retryCount ?: 0)
}\r\n\r\ndef handleStructureResponse(resp, data) {
  decrementActiveRequests()  // Always decrement when response comes back
  try {
    if (!isValidResponse(resp)) { 
      logError "Structure data request failed"
      return 
    }
    
    def response = resp.getJson()
    if (!response?.data?.first()) {
      logError 'No structure data available'
      return
    }
    
    def myStruct = response.data.first()
    if (myStruct?.id) {
      app.updateSetting('structureId', myStruct.id)
      log(2, 'App', "Structure loaded: id=${myStruct.id}, name=${myStruct.attributes?.name}")
    }
  } catch (Exception e) {
    logError "Structure data processing failed: ${e.message}"
  }
}\r\n\r\ndef getStructureData(int retryCount = 0) {
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
      // Only log full response at debug level 1
      logDetails 'Structure response: ', response, 1
      def myStruct = response.data.first()
      if (!myStruct?.attributes) {
        logError 'getStructureData: no structure data'
        return
      }
      // Log only essential fields at level 3
      log(3, 'App', "Structure loaded: id=${myStruct.id}, name=${myStruct.attributes.name}, mode=${myStruct.attributes.mode}")
      app.updateSetting('structureId', myStruct.id)
    }
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
}\r\n\r\n// Wrapper method for synchronous getStructureData retry
def retryGetStructureDataWrapper(data) {
  getStructureData(data?.retryCount ?: 0)
}\r\n\r\ndef patchVentDevice(device, percentOpen, attempt = 1) {
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
}\r\n\r\n// Keep the old method name for backward compatibility
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
}\r\n\r\ndef handleVentPatch(resp, data) {
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
}\r\n\r\n// Verify that the vent reached the requested percent open
def verifyVentPercentOpen(data) {
  if (!data?.deviceId || data.targetOpen == null) { return }
  def device = getChildDevice(data.deviceId)
  if (!device) { return }
  def uri = "${BASE_URL}/api/vents/${data.deviceId}/current-reading"
  getDataAsync(uri, 'handleVentVerify', [device: device, targetOpen: data.targetOpen, attempt: data.attempt ?: 1])
}\r\n\r\n// Handle verification response and retry if needed
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
}\r\n\r\ndef patchRoom(device, active) {
  def roomId = device.currentValue('room-id')
  if (!roomId || active == null) { return }
  if (active == device.currentValue('room-active')) { return }
  log(3, 'App', "Setting active state to ${active} for '${device.currentValue('room-name')}'")
  def uri = "${BASE_URL}/api/rooms/${roomId}"
  def body = [ data: [ type: 'rooms', attributes: [ 'active': active == 'true' ] ] ]
  patchDataAsync(uri, 'handleRoomPatch', body, [device: device])
}\r\n\r\ndef handleRoomPatch(resp, data) {
  decrementActiveRequests()  // Always decrement when response comes back
  if (!isValidResponse(resp) || !data) { return }
  traitExtract(data.device, resp.getJson(), 'active', 'room-active')
}\r\n\r\ndef patchRoomSetPoint(device, temp) {
  def roomId = device.currentValue('room-id')
  if (!roomId || temp == null) { return }
  BigDecimal tempC = temp
  if (getTemperatureScale() == 'F') {
    tempC = convertFahrenheitToCentigrade(tempC)
  }
  log(3, 'App', "Setting set-point to ${tempC}ÃƒÂ¢Ã¢â‚¬ÂÃ‚Â¬ÃƒÂ¢Ã¢â‚¬â€œÃ¢â‚¬ËœC for '${device.currentValue('room-name')}'")
  def uri = "${BASE_URL}/api/rooms/${roomId}"
  def body = [ data: [ type: 'rooms', attributes: [ 'set-point-c': tempC ] ] ]
  patchDataAsync(uri, 'handleRoomSetPointPatch', body, [device: device])
}\r\n\r\ndef handleRoomSetPointPatch(resp, data) {
  decrementActiveRequests()
  if (!isValidResponse(resp) || !data) { return }
  traitExtract(data.device, resp.getJson(), 'set-point-c', 'room-set-point-c')
}\r\n\r\ndef thermostat1ChangeTemp(evt) {
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
    log(2, 'App', "Significant temperature change detected: ${tempDiff}ÃƒÂ¢Ã¢â‚¬ÂÃ‚Â¬ÃƒÂ¢Ã¢â‚¬â€œÃ¢â‚¬ËœC (threshold: ${THERMOSTAT_HYSTERESIS}ÃƒÂ¢Ã¢â‚¬ÂÃ‚Â¬ÃƒÂ¢Ã¢â‚¬â€œÃ¢â‚¬ËœC)")
    
    if (isThermostatAboutToChangeState(hvacMode, thermostatSetpoint, temp)) {
      runInMillis(INITIALIZATION_DELAY_MS, 'initializeRoomStates', [data: hvacMode])
    }
  } else {
    log(3, 'App', "Temperature change ${tempDiff}ÃƒÂ¢Ã¢â‚¬ÂÃ‚Â¬ÃƒÂ¢Ã¢â‚¬â€œÃ¢â‚¬ËœC is below hysteresis threshold ${THERMOSTAT_HYSTERESIS}ÃƒÂ¢Ã¢â‚¬ÂÃ‚Â¬ÃƒÂ¢Ã¢â‚¬â€œÃ¢â‚¬ËœC - ignoring")
  }
}\r\n\r\ndef isThermostatAboutToChangeState(String hvacMode, BigDecimal setpoint, BigDecimal temp) {
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
}\r\n\r\ndef thermostat1ChangeStateHandler(evt) {
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
      }\r\n\r\n      // Update polling to idle interval when HVAC is idle
      updateDevicePollingInterval((settings?.pollingIntervalIdle ?: POLLING_INTERVAL_IDLE) as Integer)
      break
  }
}\r\n\r\n// Periodically evaluate duct temperatures to determine HVAC state
// without relying on an external thermostat.
def updateHvacStateFromDuctTemps() {
  // Detection runs even if DAB is disabled; only DAB actions are gated by dabEnabled
  String previousMode = atomicState.thermostat1State?.mode ?: 'idle'
  String hvacMode = (calculateHvacModeRobust() ?: 'idle')
  if (hvacMode != previousMode) {
    appendDabActivityLog("Start: ${previousMode} -> ${hvacMode}")
    try { atomicState.hvacLastMode = previousMode } catch (ignore) { }
    try { atomicState.hvacCurrentMode = hvacMode } catch (ignore) { }
    try { atomicState.hvacLastChangeTs = now() } catch (ignore) { }
  }
  if (hvacMode in [COOLING, HEATING]) {
    if (!atomicState.thermostat1State || atomicState.thermostat1State?.mode != hvacMode) {
      atomicStateUpdate('thermostat1State', 'mode', hvacMode)
      atomicStateUpdate('thermostat1State', 'startedRunning', now())
      unschedule('initializeRoomStates')
      runInMillis(POST_STATE_CHANGE_DELAY_MS, 'initializeRoomStates', [data: hvacMode])
      recordStartingTemperatures()
      runEvery5Minutes('evaluateRebalancingVents')
      runEvery30Minutes('reBalanceVents')
      updateDevicePollingInterval((settings?.pollingIntervalActive ?: POLLING_INTERVAL_ACTIVE) as Integer)
    }
  } else {
    if (atomicState.thermostat1State) {
      unschedule('initializeRoomStates')
      unschedule('finalizeRoomStates')
      unschedule('evaluateRebalancingVents')
      unschedule('reBalanceVents')
      atomicStateUpdate('thermostat1State', 'finishedRunning', now())
      if (settings?.dabEnabled) {
        def params = [
          ventIdsByRoomId: atomicState.ventsByRoomId,
          startedCycle: atomicState.thermostat1State?.startedCycle,
          startedRunning: atomicState.thermostat1State?.startedRunning,
          finishedRunning: atomicState.thermostat1State?.finishedRunning,
          hvacMode: atomicState.thermostat1State?.mode
        ]
        runInMillis(TEMP_READINGS_DELAY_MS, 'finalizeRoomStates', [data: params])
      }
      atomicState.remove('thermostat1State')
      updateDevicePollingInterval((settings?.pollingIntervalIdle ?: POLLING_INTERVAL_IDLE) as Integer)
    }
  }
  String currentMode = atomicState.thermostat1State?.mode ?: 'idle'
  if (currentMode != previousMode) {
    appendDabActivityLog("End: ${previousMode} -> ${currentMode}")
  }
}\r\n\r\ndef reBalanceVents() { dabManager.reBalanceVents() }\r\n\r\ndef evaluateRebalancingVents() { dabManager.evaluateRebalancingVents() }\r\n\r\n// Retrieve all stored rates for a specific room, HVAC mode, and hour
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
}\r\n\r\n// -------------
// EWMA + MAD helpers
// -------------
private BigDecimal getEwmaRate(String roomId, String hvacMode, Integer hour) {
  try { return (atomicState?.dabEwma?.get(roomId)?.get(hvacMode)?.get(hour as Integer)) as BigDecimal } catch (ignore) { return null }
}\r\n\r\nprivate BigDecimal updateEwmaRate(String roomId, String hvacMode, Integer hour, BigDecimal newRate) {
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
}\r\n\r\nprivate BigDecimal computeEwmaAlpha() {
  try {
    BigDecimal hlDays = (atomicState?.ewmaHalfLifeDays ?: 3) as BigDecimal
    if (hlDays <= 0) { return 1.0 }
    // One observation per day per hour-slot => N = half-life in days
    BigDecimal N = hlDays
    BigDecimal alpha = 1 - Math.pow(2.0, (-1.0 / N.toDouble()))
    return (alpha as BigDecimal)
  } catch (ignore) { return 0.2 }
}\r\n\r\nprivate Map assessOutlierForHourly(String roomId, String hvacMode, Integer hour, BigDecimal candidate) {
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
}\r\n\r\n// Ensure DAB history structures are present and normalize legacy formats
def initializeDabHistory() { return dabManager.initializeDabHistory() }

// Async-friendly wrapper to generate and cache the rates table HTML
def buildDabRatesTable(Map data) {
  try {
    state.dabRatesTableHtml = buildDabRatesTable()
  } catch (ignore) { }
}

String buildDabProgressTable() {
  initializeDabHistory()
  def history = atomicState?.dabHistory ?: []
  def entries = (history instanceof List) ? history : (history?.entries ?: [])
  String roomId = null
  try { roomId = (atomicState?.progressRoom as String) } catch (ignore) { }
  if (!roomId) { try { roomId = entries ? entries[0][1] : null } catch (ignore) { } }
  // roomId is read from atomicState mirror for CI-safety
  if (!roomId) { return '<p>Select a room to view progress.</p>' }\r\n\r\n  String hvacMode = settings?.progressHvacMode ?: getThermostat1Mode() ?: atomicState?.lastHvacMode
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
  }\r\n\r\n  if (!aggregated) { return '<p>No DAB progress history available for the selected period.</p>' }\r\n\r\n  def dates = aggregated.keySet().sort()
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
}\r\n\r\nString buildDabDailySummaryTable() {
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
}\r\n\r\n// ------------------------------
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
        paragraph "<b>${roomName}</b> - Vent: ${cur}% | Temp: ${tempF} Â°F | Setpoint: ${setpF} Â°F | Active: ${active ?: 'false'}" + (batt ? " | Battery: ${batt}%" : "") + (upd ? " | Updated: ${upd}" : "")
        input name: "qc_${vidKey}_percent", type: 'number', title: 'Set vent percent', required: false, submitOnChange: false
        input name: "qc_room_${roomKey}_setpoint", type: 'number', title: 'Set room setpoint (Â°F)', required: false, submitOnChange: false
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

  private void applyQuickControls() {
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
  }\r\n\r\nprivate void openAllSelected(Integer pct) {
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
}\r\n\r\n  private void manualAllEditedVents() {
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
  }\r\n\r\n

private String buildDiagnosticsJson() {
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
}\r\n\r\nprivate String buildRawCacheJson() {
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
}\r\n\r\nprivate void clearRawCache() {
  try {
    atomicState.remove('rawDabSamplesEntries')
    atomicState.remove('rawDabLastByVent')
    log(2, 'App', 'Cleared raw data cache')
  } catch (ignore) { }
}\r\n\r\ndef dabHealthMonitor() {
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
}\r\n\r\n










// DAB Live Diagnostics page to run a one-off calculation and display details
def dabLiveDiagnosticsPage() { dabUIManager.dabLiveDiagnosticsPage() }
// Execute a live diagnostic pass of DAB calculations without changing device state
void runDabDiagnostic() {
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
  }\r\n\r\n  // Build ventsByRoomId mapping (roomId -> List of ventIds)
  def ventsByRoomId = [:]
  vents.each { v ->
    try {
      def rid = v.currentValue('room-id')?.toString()
      if (!rid) { return }
      def list = ventsByRoomId[rid] ?: []
      list << v.getDeviceNetworkId()
      ventsByRoomId[rid] = list
    } catch (ignore) { }
  }\r\n\r\n  // Calculations
  def rateAndTempPerVentId = getAttribsPerVentIdWeighted(ventsByRoomId, hvacMode)
  def longestTimeToTarget = calculateLongestMinutesToTarget(rateAndTempPerVentId, hvacMode, globalSp, (atomicState.maxHvacRunningTime ?: MAX_MINUTES_TO_SETPOINT), settings.thermostat1CloseInactiveRooms)
  def initialPositions = calculateOpenPercentageForAllVents(rateAndTempPerVentId, hvacMode, globalSp, longestTimeToTarget, settings.thermostat1CloseInactiveRooms)
  def minAirflowAdjusted = adjustVentOpeningsToEnsureMinimumAirflowTarget(rateAndTempPerVentId, hvacMode, initialPositions, settings.thermostat1AdditionalStandardVents)
  def finalPositions = applyOverridesAndFloors(minAirflowAdjusted)

  results.calculations = [ longestTimeToTarget: longestTimeToTarget, initialVentPositions: initialPositions ]
  results.adjustments = [ minimumAirflowAdjustments: minAirflowAdjusted ]
  results.finalOutput = [ finalVentPositions: finalPositions ]

  state.dabDiagnosticResult = results
}\r\n\r\n// Render diagnostic results as an HTML snippet (paragraph-safe)
String renderDabDiagnosticResults() {
  def results = state?.dabDiagnosticResult
  if (!results) { return '<p>No diagnostic results to display.</p>' }\r\n\r\n  def sb = new StringBuilder()
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
}\r\n\r\n




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

// Async-friendly wrapper to generate and cache the progress table HTML
def buildDabProgressTable(Map data) {
  try { atomicState.progressRoom = settings?.progressRoom } catch (ignore) { }
  try {
    state.dabProgressTableHtml = buildDabProgressTable()
  } catch (ignore2) { }
}
















def handleExportEfficiencyData() { dabUIManager.handleExportEfficiencyData() }

def handleImportEfficiencyData() { dabUIManager.handleImportEfficiencyData() }

def handleClearExportData() { dabUIManager.handleClearExportData() }

