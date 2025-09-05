    try {
      def getTypeNameSafe = { d ->
        try { return d?.typeName } catch (ignore) { try { return d?.getTypeName() } catch (ignore2) { return null } }
      }
      def hasAttrSafe = { d, String attr ->
        try { return d?.hasAttribute(attr) } catch (ignore) {
          try { return d?.currentValue(attr) != null } catch (ignore2) { return false }
        }
      }/**
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

// Base URL for Flair API endpoints.
@Field static final String BASE_URL = 'https://api.flair.co'

// Instance-based cache durations (reduced from 60s to 30s for better responsiveness)
@Field static final Long ROOM_CACHE_DURATION_MS = 30000 // 30 second cache duration
@Field static final Long DEVICE_CACHE_DURATION_MS = 30000 // 30 second cache duration for device readings
@Field static final Integer MAX_CACHE_SIZE = 50 // Maximum cache entries per instance
@Field static final Integer DEFAULT_HISTORY_RETENTION_DAYS = 10 // Default days to retain DAB history
@Field static final Integer DAILY_SUMMARY_PAGE_SIZE = 30 // Entries per page for daily summary
@Field static final Integer ACTIVITY_LOG_PAGE_SIZE = 20
@Field static final Integer HISTORY_PAGE_SIZE      = 50

@Field static final Long LOG_RATE_LIMIT_MS = 5000 // Min ms between identical log entries

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
// then the vent's open percentage will be increased by 1.5 * 0.5 = 0.75% in that iteration.
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

// HVAC inference from room temperature trends (fallback when duct temps/thermostat are unavailable)
@Field static final Integer HVAC_TREND_WINDOW_MIN = 3      // minutes
@Field static final BigDecimal HVAC_TREND_THRESHOLD_C = 0.15 // C change within window to infer heat/cool

// Maximum concurrent HTTP requests to prevent API overload.
@Field static final Integer MAX_CONCURRENT_REQUESTS = 8

// Maximum number of retry attempts for async API calls.
@Field static final Integer MAX_API_RETRY_ATTEMPTS = 5

// Consecutive failures per URI before resetting API connection.
@Field static final Integer API_FAILURE_THRESHOLD = 3

// Circuit breaker reset time (in milliseconds).
@Field static final Integer CIRCUIT_RESET_MS = 5 * 60 * 1000  // 5 minutes

// ------------------------------
// Enhanced DAB Constants
// ------------------------------

// Anomaly Detection & Influence Dampening
@Field static final BigDecimal ANOMALY_PERCENT_THRESHOLD = 0.40        // 40% threshold for anomaly detection
@Field static final BigDecimal ANOMALY_MAD_MULTIPLIER = 2.5           // MAD multiplier for outlier detection
@Field static final Integer MIN_SAMPLES_FOR_ANOMALY_CHECK = 4         // Minimum samples needed for anomaly check
@Field static final Integer ANOMALY_DECAY_STEPS = 2                   // Steps for anomaly influence decay (1.0 -> 0.6 -> 0.3 -> expire)

// Adaptive Vent Granularity
@Field static final BigDecimal CV_LOW_THRESHOLD = 0.10                // Coefficient of variation threshold for fine control
@Field static final BigDecimal CV_VERY_LOW_THRESHOLD = 0.05           // Very low CV threshold for ultra-fine control
@Field static final BigDecimal INTERNAL_FINE_STEP = 0.02              // 2% internal fine step size

// Rate Interpretation (Inverse Weight)
@Field static final BigDecimal EPS_RATE = 0.0001                      // Epsilon to prevent division by zero

// Change Dampening
@Field static final BigDecimal MAX_PERCENT_CHANGE_PER_CYCLE = 25.0     // Maximum percent change per cycle (soft limiting)

// HVAC State Tracking
@Field static final BigDecimal DUCT_ROOM_DELTA_HYSTERESIS = 0.3       // Hysteresis for duct-room temperature delta

// ------------------------------
// End Enhanced DAB Constants  
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
  page(name: 'landingPage')
  page(name: 'setupPage')
  page(name: 'efficiencyDataPage')
  page(name: 'dabRatesTablePage')
  page(name: 'dabActivityLogPage')
  page(name: 'dabHistoryPage')
  page(name: 'dabProgressPage')
  page(name: 'dabDailySummaryPage')
  page(name: 'quickControlsPage')
  page(name: 'diagnosticsPage')
  page(name: 'exportLogsPage')
  page(name: 'deviceSyncPage')
  page(name: 'trendDebugPage')
  page(name: 'roomTargetsPage')
}

def landingPage() {
  dynamicPage(name: 'landingPage', title: 'Dashboard', install: true, uninstall: true) {
    section('System Health') {
      def issues = getDataIssues()
      if (issues) {
        issues.each { paragraph "<span style='color:red;'>${it}</span>" }
      } else {
        paragraph "<span style='color:green;'>No data issues detected.</span>"
      }
      def err = state.lastCommandError
      if (err) {
        paragraph "<b>${err.action}</b>: ${err.message}"
        if (err.suggestion) { paragraph "<small>${err.suggestion}</small>" }
        input name: 'clearLastCommandError', type: 'button', title: 'Clear Last Command Error', submitOnChange: true
        if (settings?.clearLastCommandError) {
          state.remove('lastCommandError')
          app.updateSetting('clearLastCommandError', null)
          paragraph "<span style='color: green;'>&#10003; Cleared</span>"
        }
      }
    }
    section('DAB Health') {
      if (settings?.dabEnabled) {
        paragraph "<span style='color:green;'>Dynamic Airflow Balancing enabled</span>"
      } else {
        paragraph "<span style='color:red;'>Dynamic Airflow Balancing disabled</span>"
      }
    }
    section('Rooms') {
      // Identify vent devices by driver type to avoid missing vents when attributes are absent
      def vents = getChildDevices()?.findAll { (it.typeName ?: '') == 'Flair vents' }
      // Fallback to attribute-based detection if no vents were matched by driver type
      if (!vents) { vents = getChildDevices()?.findAll { it.hasAttribute('percent-open') } }
      vents = vents ?: []
      vents.each { vent ->
        String key = "refresh_${vent.getId()}"
        boolean didRefresh = false
        if (settings?."${key}") {
          try { vent.refresh() } catch (ignore) { }
          app.updateSetting(key, null)
          didRefresh = true
        }
        def roomName = vent.currentValue('room-name') ?: vent.getLabel()
        def temp = vent.currentValue('temperature') ?: '-'
        def pct = vent.currentValue('percent-open') ?: '-'
        paragraph "<b>${roomName}</b>: ${temp}&deg; | ${pct}% open"
        input name: key, type: 'button', title: 'Refresh', submitOnChange: true
        if (didRefresh) { paragraph "<span style='color: green;'>&#10003; Refreshed</span>" }
      }
    }
    section('Navigation') {
      href name: 'quickControlsLinkTop', title: '\u26A1 Open Quick Controls', description: 'Per-room manual controls', page: 'quickControlsPage'
      href name: 'setupLink', title: 'Configuration', description: 'One-time setup and advanced settings', page: 'setupPage'
      href name: 'dabRatesTableLink', title: 'View DAB Rates Table', page: 'dabRatesTablePage'
      href name: 'dabProgressLink', title: 'View DAB Progress', page: 'dabProgressPage'
      href name: 'dabDailySummaryLink', title: 'View Daily DAB Summary', page: 'dabDailySummaryPage'
      href name: 'dabActivityLogLink', title: 'View DAB Activity Log', page: 'dabActivityLogPage'
    }
  }
}

def setupPage() {
  def validation = validatePreferences()
  if (settings?.validateNow) {
    performValidationTest()
    app.updateSetting('validateNow', null)
  }

  dynamicPage(name: 'setupPage', title: 'Setup', install: validation.valid, uninstall: true) {
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
      section('System Health') {
        def issues = getDataIssues()
        if (issues) {
          issues.each { paragraph "<span style='color:red;'>${it}</span>" }
        } else {
          paragraph "<span style='color:green;'>No data issues detected.</span>"
        }
        def err = state.lastCommandError
        if (err) {
          paragraph "<b>${err.action}</b>: ${err.message}"
          if (err.suggestion) { paragraph "<small>${err.suggestion}</small>" }
          input name: 'clearLastCommandError', type: 'button', title: 'Clear Last Command Error', submitOnChange: true
          if (settings?.clearLastCommandError) {
            state.remove('lastCommandError')
            app.updateSetting('clearLastCommandError', null)
            paragraph "<span style='color: green;'>&#10003; Cleared</span>"
          }
        }
      }
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
        def cycles = atomicState?.coolingCycleCount ?: 0
        paragraph "Cooling cycles: <b>${cycles}</b>"
        input name: 'resetCoolingCycles', type: 'button', title: 'Reset Cooling Cycle Counter', submitOnChange: true
        if (settings?.resetCoolingCycles) {
          atomicState.coolingCycleCount = 0
          app.updateSetting('resetCoolingCycles', null)
          paragraph "<span style='color: green;'>&#10003; Counter reset</span>"
        }
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
          
          input name: 'manualHvacMode', type: 'enum', title: 'Manual HVAC mode override (no thermostat)', defaultValue: 'auto', options: ['auto', HEATING, COOLING, 'idle'], submitOnChange: true
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

          // Polling intervals (registered so validators accept settings reads)
          section('Polling Intervals') {
            input name: 'pollingIntervalActive', type: 'number', title: 'Active HVAC polling interval (minutes)', defaultValue: 1, submitOnChange: true
            input name: 'pollingIntervalIdle', type: 'number', title: 'Idle polling interval (minutes)', defaultValue: 10, submitOnChange: true
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
          section('Tools') {
            href name: 'deviceSyncLink', title: 'Device Link Checker', description: 'Sync/create missing vents/pucks', page: 'deviceSyncPage'
            href name: 'trendDebugLink', title: 'Trend Debug', description: 'Room-temp trend inference details', page: 'trendDebugPage'
            href name: 'roomTargetsLink', title: 'Room Targets', description: 'Set per-room target temps (DAB-only)', page: 'roomTargetsPage'
          }

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
            input name: 'outlierThresholdMad', type: 'number', title: 'Outlier threshold (k × MAD)', defaultValue: 3, submitOnChange: true
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
      href name: 'diagnosticsLink', title: 'View Diagnostics',
           description: 'Troubleshoot vent data and logs', page: 'diagnosticsPage'
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
    section('Data Health Checks') {
      def issues = getDataIssues()
      if (issues) {
        issues.each { paragraph "<span style='color:red;'>${it}</span>" }
      } else {
        paragraph "<span style='color:green;'>No data issues detected.</span>"
      }
    }
    section('Last Command Error') {
      def err = state.lastCommandError
      if (err) {
        paragraph "<b>${err.action}</b>: ${err.message}"
        if (err.suggestion) { paragraph "<small>${err.suggestion}</small>" }
      } else {
        paragraph 'No command errors recorded.'
      }
    }
    section('Exports') {
      href name: 'exportLogsPage', title: 'Export Diagnostics', description: 'View logs and errors', page: 'exportLogsPage'
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
    section('Tools') {
      href name: 'deviceSyncLink', title: 'Device Link Checker', description: 'Sync/create missing vents/pucks', page: 'deviceSyncPage'
      href name: 'trendDebugLink', title: 'Trend Debug', description: 'Room-temp trend inference details', page: 'trendDebugPage'
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
    
    // Enhanced DAB Snapshot Diagnostics
    section('DAB Snapshot') {
      def events = atomicState.dabEvents ?: []
      def metadata = atomicState.dabMetadata ?: [:]
      def samples = atomicState.dabSamples ?: [:]
      def anomalyInfluence = atomicState.anomalyInfluence ?: [:]
      
      paragraph "Recent DAB events: ${events.size()}"
      paragraph "Hourly rate metadata entries: ${metadata.size()}"
      paragraph "Sample arrays: ${samples.size()}"
      paragraph "Anomaly influence decay entries: ${anomalyInfluence.size()}"
      
      if (events) {
        paragraph "<b>Recent Events (last 10):</b>"
        events.takeRight(10).each { event ->
          def ts = new Date(event.timestamp).format('HH:mm:ss', location?.timeZone ?: TimeZone.getTimeZone('UTC'))
          paragraph "<small>${ts} - ${event.type}: ${event.data}</small>"
        }
      }
      
      input name: 'dumpDabSnapshotNow', type: 'button', title: 'Dump DAB Snapshot', submitOnChange: true
      if (settings?.dumpDabSnapshotNow) {
        try { 
          buildDabSnapshot()
          app.updateSetting('dumpDabSnapshotNow','')
        } catch (Exception e) { 
          log(2, 'DAB', "Error dumping DAB snapshot: ${e.message}")
        }
      }
      paragraph '<small>Snapshot data is logged for analysis. Check app logs for structured JSON output.</small>'
    }
    
    section('Actions') {
      input name: 'reauthenticate', type: 'button', title: 'Re-Authenticate'
      input name: 'resyncVents', type: 'button', title: 'Re-Sync Vents'
    }
  }
}

def exportLogsPage() {
  dynamicPage(name: 'exportLogsPage', title: 'Export Diagnostics', install: false, uninstall: false) {
    section('Recent Logs') {
      def logs = state.recentLogs ?: []
      paragraph "<pre>${JsonOutput.toJson(logs)}</pre>"
    }
    section('Recent Errors') {
      def errs = state.recentErrors ?: []
      paragraph "<pre>${JsonOutput.toJson(errs)}</pre>"
    }
    section('Last Command Error') {
      def err = state.lastCommandError ?: [:]
      paragraph "<pre>${JsonOutput.toJson(err)}</pre>"
    }
  }
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
  log 'Instance caches cleared', 2
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
}

def getStructureId() {
  if (!settings?.structureId) { getStructureData() }
  return settings?.structureId
}

def updated() {
  log.debug 'Hubitat Flair App updating'
  initializeDabHistory()
  initialize()
}

def installed() {
  log.debug 'Hubitat Flair App installed'
  initializeDabHistory()
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
  }

  if (settings?.nightOverrideEnable) {
    try {
      if (settings?.nightOverrideStart) { schedule(settings.nightOverrideStart, 'activateNightOverride') }
      if (settings?.nightOverrideEnd) { schedule(settings.nightOverrideEnd, 'deactivateNightOverride') }
    } catch (e) { log(2, 'App', "Night override scheduling error: ${e?.message}") }
  } else {
    try { unschedule('activateNightOverride'); unschedule('deactivateNightOverride') } catch (ignore) { }
  }
}

// ------------------------------
// Helper Functions
// ------------------------------

private openAllVents(Map ventIdsByRoomId, int percentOpen) {
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
      def roomTemp = vent.currentValue('room-current-temperature-c') ?: '-'
      if (roomTemp == '-') {
        logWarn "Room data unavailable for '${roomName}'. Check network or thermostat connectivity."
      } else {
        log(2, 'App', "Falling back to room temperature for '${roomName}': ${roomTemp}°C")
      }
      return roomTemp
    }
    if (getTemperatureScale() == 'F') {
      temp = convertFahrenheitToCentigrade(temp)
    }
    log(2, 'App', "Got temp from ${tempDevice?.getLabel() ?: 'Unknown'} for '${roomName}': ${temp}°C")
    return temp
  }
  
  def roomTemp = vent.currentValue('room-current-temperature-c')
  if (roomTemp == null || roomTemp == '-') {
    logWarn "Room data unavailable for '${roomName}'. Check network or thermostat connectivity."
    return '-'
  }
  log(2, 'App', "Using room temperature for '${roomName}': ${roomTemp}°C")
  return roomTemp
}

private atomicStateUpdate(String stateKey, String key, value) {
  atomicState.updateMapValue(stateKey, key, value)
  log(1, 'App', "atomicStateUpdate(${stateKey}, ${key}, ${value})")
}

def getThermostatSetpoint(String hvacMode) {
  def thermostat = settings?.thermostat1
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
    logError 'Thermostat has no setpoint property, please choose a valid thermostat'
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
}

// Function to round values to specific decimal places for JSON export
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
}

// Function to clean decimal values for JSON serialization
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
}

// Enhanced rounding function with adaptive granularity based on coefficient of variation
int roundToNearestMultiple(BigDecimal num) {
  int userGranularity = settings.ventGranularity ? settings.ventGranularity.toInteger() : 5
  int adaptiveGranularity = calculateAdaptiveGranularity(userGranularity)
  return adaptiveRound(num, userGranularity, adaptiveGranularity)
}

// Calculate adaptive granularity based on coefficient of variation
int calculateAdaptiveGranularity(int userGranularity) {
  try {
    // Get current vent rates for CV calculation
    def ventRates = getCurrentVentRates()
    if (ventRates.size() < 2) {
      return userGranularity
    }
    
    // Calculate coefficient of variation using inverse-rate weights
    BigDecimal cv = calculateCoefficientOfVariation(ventRates)
    
    log(4, 'DAB', "Coefficient of variation: ${cv.round(4)}")
    
    // Apply adaptive granularity rules
    if (cv < CV_VERY_LOW_THRESHOLD && userGranularity == 5) {
      log(3, 'DAB', "CV (${cv.round(3)}) < ${CV_VERY_LOW_THRESHOLD}, using ultra-fine 2% internal steps")
      return 2 // Will use internal fine stepping with external rounding to 5%
    } else if (cv < CV_LOW_THRESHOLD && userGranularity > 5) {
      log(3, 'DAB', "CV (${cv.round(3)}) < ${CV_LOW_THRESHOLD}, temporarily using 5% granularity instead of ${userGranularity}%")
      return 5
    }
    
    return userGranularity
  } catch (Exception e) {
    log(2, 'DAB', "Error calculating adaptive granularity: ${e.message}")
    return userGranularity
  }
}

// Get current vent rates for CV calculation
def getCurrentVentRates() {
  def ventRates = []
  try {
    def vents = getChildDevices().findAll { it.hasAttribute('percent-open') }
    String hvacMode = atomicState.thermostat1State?.mode ?: atomicState.hvacCurrentMode ?: 'idle'
    
    if (hvacMode in [COOLING, HEATING]) {
      vents.each { vent ->
        try {
          def rate = hvacMode == COOLING ? 
            (vent.currentValue('room-cooling-rate') ?: 0) : 
            (vent.currentValue('room-heating-rate') ?: 0)
          if (rate > 0) {
            ventRates << (rate as BigDecimal)
          }
        } catch (ignore) { }
      }
    }
  } catch (Exception e) {
    log(2, 'DAB', "Error getting current vent rates: ${e.message}")
  }
  return ventRates
}

// Calculate coefficient of variation using inverse-rate weights
BigDecimal calculateCoefficientOfVariation(List rates) {
  if (rates.size() < 2) {
    return 0.0
  }
  
  try {
    // Convert rates to inverse weights
    def weights = rates.collect { rate ->
      1.0 / Math.max(rate as Double, EPS_RATE as Double)
    }
    
    // Calculate mean and standard deviation of weights
    BigDecimal mean = weights.sum() / weights.size()
    BigDecimal sumSquaredDeviations = weights.collect { weight ->
      Math.pow((weight - mean), 2)
    }.sum()
    
    BigDecimal variance = sumSquaredDeviations / weights.size()
    BigDecimal standardDeviation = Math.sqrt(variance)
    
    // Coefficient of variation = standard deviation / mean
    return mean != 0 ? standardDeviation / mean : 0.0
  } catch (Exception e) {
    log(2, 'DAB', "Error calculating coefficient of variation: ${e.message}")
    return 0.0
  }
}

// Adaptive rounding pipeline with internal fine steps
int adaptiveRound(BigDecimal num, int userGranularity, int adaptiveGranularity) {
  try {
    if (adaptiveGranularity == 2 && userGranularity == 5) {
      // Ultra-fine mode: compute internally on 2% increments, round externally to 5%
      int internalTarget = (int)(Math.round(num / INTERNAL_FINE_STEP) * INTERNAL_FINE_STEP)
      int externalTarget = (int)(Math.round(internalTarget / userGranularity) * userGranularity)
      
      log(4, 'DAB', "Adaptive round: ${num} -> internal=${internalTarget}% -> external=${externalTarget}%")
      return externalTarget
    } else {
      // Standard rounding using adaptive granularity
      int result = (int)(Math.round(num / adaptiveGranularity) * adaptiveGranularity)
      if (adaptiveGranularity != userGranularity) {
        log(4, 'DAB', "Adaptive round: ${num} -> ${result}% (using ${adaptiveGranularity}% instead of ${userGranularity}%)")
      }
      return result
    }
  } catch (Exception e) {
    log(2, 'DAB', "Error in adaptive rounding: ${e.message}")
    return (int)(Math.round(num / userGranularity) * userGranularity)
  }
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
}

// Determine HVAC mode purely from vent duct temperatures. Returns
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
def calculateHvacModeRobust(List ventsOverride = null) {
  try { def override = settings?.manualHvacMode; if (override && override != 'auto') { return override } } catch (ignore) { }

  def vents = ventsOverride ?: getChildDevices()?.findAll {
    it.currentValue('duct-temperature-c') != null &&
    (it.currentValue('room-current-temperature-c') != null ||
     it.currentValue('current-temperature-c') != null ||
     it.currentValue('temperature') != null)
  }

  def fallbackFromThermostat = {
    try {
      String op = settings?.thermostat1?.currentValue('thermostatOperatingState')?.toString()?.toLowerCase()
      if (op in ['heating', 'pending heat']) { return HEATING }
      if (op in ['cooling', 'pending cool']) { return COOLING }
    } catch (ignore) { }
    return null
  }

  if (!vents || vents.isEmpty()) { def __tm = inferHvacFromRoomTrends(ventsOverride); return (__tm ?: (fallbackFromThermostat() ?: 'idle')) }

  def diffs = []
  vents.each { v ->
    try {
      BigDecimal duct = v.currentValue('duct-temperature-c') as BigDecimal
      BigDecimal room = (v.currentValue('room-current-temperature-c') ?:
                         v.currentValue('current-temperature-c') ?:
                         v.currentValue('temperature')) as BigDecimal
      BigDecimal diff = duct - room
      diffs << diff
      log(4, 'DAB', "Vent ${v?.displayName ?: v?.id}: duct=${duct}C room=${room}C diff=${diff}C", v?.id)
    } catch (ignore) { }
  }
  if (!diffs) { def __tm = inferHvacFromRoomTrends(ventsOverride); return (__tm ?: (fallbackFromThermostat() ?: 'idle')) }

  // Treat any significant duct temp delta as an active cycle
  if (diffs.any { it < -DUCT_TEMP_DIFF_THRESHOLD }) { return COOLING }
  if (diffs.any { it > DUCT_TEMP_DIFF_THRESHOLD }) { return HEATING }

  // Fall back to thermostat or idle when no vent shows activity
  def sorted = diffs.sort()
  BigDecimal median = sorted[sorted.size().intdiv(2)] as BigDecimal
  log(4, 'DAB', "Median duct-room temp diff=${median}C")
  return (fallbackFromThermostat() ?: 'idle')
}

// Infer HVAC mode from room temperature trends within a short window when duct temps are unavailable.
private String inferHvacFromRoomTrends(List ventsOverride = null) {
  try {
    def nowMs = now()
    def trend = atomicState?.hvacTrend ?: [:]
    def candidates = ventsOverride ?: getChildDevices()
    if (!candidates) { return null }

    candidates.each { v ->
      try {
        def room = (v.currentValue('room-current-temperature-c') ?: v.currentValue('current-temperature-c') ?: v.currentValue('temperature'))
        if (room != null) {
          String key = (v.respondsTo('getId') ? (v.getId()?.toString()) : (v.getDeviceNetworkId()?.toString() ?: v.getLabel()?.toString() ?: UUID.randomUUID().toString()))
          def recs = (trend[key] ?: []) as List
          recs << [ts: nowMs, t: (room as BigDecimal)]
          // keep last 10 minutes of samples
          def cutoff = nowMs - (10 * 60 * 1000)
          recs = recs.findAll { it.ts >= cutoff }
          trend[key] = recs
        }
      } catch (ignore) { }
    }
    atomicState.hvacTrend = trend

    long windowMs = (HVAC_TREND_WINDOW_MIN * 60 * 1000) as long
    def cooling = false
    def heating = false
    trend.values().each { recs ->
      try {
        if (recs.size() < 2) { return }
        def nowRec = recs[-1]
        def ref = recs.find { (nowRec.ts - it.ts) >= windowMs }
        if (!ref) { return }
        BigDecimal delta = (nowRec.t as BigDecimal) - (ref.t as BigDecimal)
        if (delta <= -HVAC_TREND_THRESHOLD_C) { cooling = true }
        if (delta >= HVAC_TREND_THRESHOLD_C) { heating = true }
      } catch (ignore) { }
    }
    if (cooling && !heating) { return COOLING }
    if (heating && !cooling) { return HEATING }
  } catch (ignoreOuter) { }
  return null
}

void removeChildren() {
  def children = getChildDevices()
  log(2, 'Device', "Deleting all child devices: ${children}")
  children.each { if (it) deleteChildDevice(it.getDeviceNetworkId()) }
}

// Append log entry to recent log buffer with simple rate limiting
private void appendRecentLog(int level, String module, String correlationId, String msg) {
  def logs = state.recentLogs ?: []
  def nowMs = now()
  def last = logs ? logs[-1] : null
  if (last && last.msg == msg && last.ms && (nowMs - (last.ms as Long)) < LOG_RATE_LIMIT_MS) {
    return
  }
  def tz = location?.timeZone ?: TimeZone.getTimeZone('UTC')
  def entry = [ts: new Date(nowMs).format("yyyy-MM-dd'T'HH:mm:ssZ", tz),
               ms: nowMs, level: level, module: module, cid: correlationId, msg: msg]
  logs << entry
  state.recentLogs = logs.size() > 50 ? logs[-50..-1] : logs
}

// Only log messages if their level is greater than or equal to the debug level setting.
private void log(int level, String module, String msg, String correlationId = null) {
  int settingsLevel = (settings?.debugLevel as Integer) ?: 0
  if (settingsLevel == 0 || level < settingsLevel) { return }

  String prefix = correlationId ? "[${module}|${correlationId}]" : "[${module}]"
  log.debug "${prefix} ${msg}"
  boolean __verbose = false
  try { __verbose = (atomicState?.verboseLogging == true) } catch (ignore) { }
  if (__verbose) {
    appendRecentLog(level, module, correlationId, msg)
  }
}

// Safe getter for thermostat mode from atomic state
private getThermostat1Mode() {
  return atomicState?.thermostat1Mode
}

private void logValidationFailure(String field, String reason) {
  def msg = JsonOutput.toJson([event: 'validationFailure', field: field, reason: reason])
  try { logWarn(msg, 'Validation') } catch (ignore) { try { log?.warn msg } catch (ignore2) { } }
}

private Map validatePreferences() {
  def errors = [:]
  boolean valid = true

  if (!settings?.clientId) {
    errors.clientId = 'Client ID is required'
    if (settings?.validateNow) logValidationFailure('clientId', 'missing')
    valid = false
  }
  if (!settings?.clientSecret) {
    errors.clientSecret = 'Client Secret is required'
    if (settings?.validateNow) logValidationFailure('clientSecret', 'missing')
    valid = false
  }

  // Polling interval validation moved to runtime to avoid strict CI constraints on reading non-registered inputs

  [valid: valid, errors: errors]
}

private void performValidationTest() {
  def result = [success: false, message: '']
  try {
    if (!state.flairAccessToken) {
      throw new Exception('Missing access token')
    }
    getStructureData()
    result.success = true
    result.message = 'Successfully connected to Flair API'
  } catch (e) {
    result.message = "Connectivity test failed: ${e.message}"
    if (settings?.validateNow) logValidationFailure('connectivity', e.message)
  }
  state.lastValidationResult = result
}


// Safe sendEvent wrapper for test compatibility
private safeSendEvent(device, Map eventData) {
  try {
    sendEvent(device, eventData)
  } catch (Exception e) {
    // In test environment, sendEvent might not be available
    log(2, 'App', "Warning: Could not send event ${eventData} to device ${device}: ${e.message}")
  }
}

// Clean up existing BigDecimal precision issues in stored data
def cleanupExistingDecimalPrecision() {
  try {
    log(2, 'App', "Cleaning up existing decimal precision issues")
    
    // Clean up global rates in atomicState
    if (atomicState.maxCoolingRate) {
      def cleanedCooling = cleanDecimalForJson(atomicState.maxCoolingRate)
      if (cleanedCooling != atomicState.maxCoolingRate) {
        atomicState.maxCoolingRate = cleanedCooling
        log(2, 'App', "Cleaned maxCoolingRate: ${atomicState.maxCoolingRate}")
      }
    }
    
    if (atomicState.maxHeatingRate) {
      def cleanedHeating = cleanDecimalForJson(atomicState.maxHeatingRate)
      if (cleanedHeating != atomicState.maxHeatingRate) {
        atomicState.maxHeatingRate = cleanedHeating
        log(2, 'App', "Cleaned maxHeatingRate: ${atomicState.maxHeatingRate}")
      }
    }
    
    // Clean up device attributes for existing vents
    def devicesUpdated = 0
    getChildDevices().findAll { it.hasAttribute('percent-open') }.each { device ->
      try {
        def coolingRate = device.currentValue('room-cooling-rate')
        def heatingRate = device.currentValue('room-heating-rate')
        
        if (coolingRate && coolingRate != 0) {
          def cleanedCooling = cleanDecimalForJson(coolingRate)
          if (cleanedCooling != coolingRate) {
            sendEvent(device, [name: 'room-cooling-rate', value: cleanedCooling])
            devicesUpdated++
          }
        }
        
        if (heatingRate && heatingRate != 0) {
          def cleanedHeating = cleanDecimalForJson(heatingRate)
          if (cleanedHeating != heatingRate) {
            sendEvent(device, [name: 'room-heating-rate', value: cleanedHeating])
            devicesUpdated++
          }
        }
      } catch (Exception e) {
        log(2, 'App', "Error cleaning device precision for ${device.getLabel()}: ${e.message}")
      }
    }
    
    if (devicesUpdated > 0) {
      log(2, 'App', "Updated decimal precision for ${devicesUpdated} device attributes")
    }
    
  } catch (Exception e) {
    log(2, 'App', "Error during decimal precision cleanup: ${e.message}")
  }
}

def initializeDabTracking() {
  try {
    if (atomicState.dabHistory == null) {
      atomicState.dabHistory = [:]
    }
  } catch (Exception e) {
    logWarn "Failed to initialize DAB history map: ${e.message}"
  }

  try {
    if (atomicState.dabActivityLog == null) {
      atomicState.dabActivityLog = []
    }
  } catch (Exception e) {
    logWarn "Failed to initialize DAB activity log: ${e.message}"
  }
}

// ------------------------------
// Instance-Based Caching Infrastructure
// ------------------------------

// Get current time - now() is always available in Hubitat
private getCurrentTime() {
  return now()
}

// Get unique instance identifier
private getInstanceId() {
  try {
    // Try to use app ID if available (production)
    def appId = app?.getId()?.toString()
    if (appId) {
      return appId
    }
  } catch (Exception e) {
    // Expected in test environment
  }
  
  // For test environment, use current time as unique identifier
  // This provides reasonable uniqueness for test instances
  return "test-${now()}"
}

  // Initialize instance-level cache variables
  private initializeInstanceCaches() {
    // Ensure throttling counters/maps exist for CI/tests and runtime
    try { if (atomicState.activeRequests == null) { atomicState.activeRequests = 0 } } catch (ignore) { }
    try { if (state.circuitOpenUntil == null) { state.circuitOpenUntil = [:] } } catch (ignore) { }
    def instanceId = getInstanceId()
    def cacheKey = "instanceCache_${instanceId}"
  
  if (!state."${cacheKey}_initialized") {
    state."${cacheKey}_roomCache" = [:]
    state."${cacheKey}_roomCacheTimestamps" = [:]
    state."${cacheKey}_deviceCache" = [:]
    state."${cacheKey}_deviceCacheTimestamps" = [:]
    state."${cacheKey}_pendingRoomRequests" = [:]
    state."${cacheKey}_pendingDeviceRequests" = [:]
    state."${cacheKey}_initialized" = true
    log(3, 'App', "Initialized instance-based caches for instance ${instanceId}")
  }
}

// ------------------------------
// Raw DAB Data Cache (24h rolling)
// ------------------------------

private Integer getRawCacheRetentionHours() {
  try {
    Integer h = (settings?.rawDataRetentionHours ?: RAW_CACHE_DEFAULT_HOURS) as Integer
    if (h < 1) { h = 1 }
    if (h > 48) { h = 48 }
    return h
  } catch (ignore) { return RAW_CACHE_DEFAULT_HOURS }
}

private Long getRawCacheCutoffTs() {
  return now() - (getRawCacheRetentionHours() * 60L * 60L * 1000L)
}

private void appendRawDabSample(List entry) {
  try {
    def list = atomicState?.rawDabSamplesEntries ?: []
    list << entry
    // Trim by cutoff time
    Long cutoff = getRawCacheCutoffTs()
    list = list.findAll { e ->
      try { return (e[0] as Long) >= cutoff } catch (ignore) { return false }
    }
    // Hard cap to avoid runaway
    if (list.size() > RAW_CACHE_MAX_ENTRIES) { list = list[-RAW_CACHE_MAX_ENTRIES..-1] }
    atomicState.rawDabSamplesEntries = list
    // Quick latest index for O(1) get
    def lastByVent = atomicState?.rawDabLastByVent ?: [:]
    lastByVent[(entry[1] as String)] = entry
    atomicState.rawDabLastByVent = lastByVent
  } catch (ignore) { }
}

private List getLatestRawSample(String ventId) {
  try { return (atomicState?.rawDabLastByVent ?: [:])[ventId] as List } catch (ignore) { return null }
}

def pruneRawCache() {
  try {
    def list = atomicState?.rawDabSamplesEntries ?: []
    if (!list) { return }
    Long cutoff = getRawCacheCutoffTs()
    list = list.findAll { e ->
      try { return (e[0] as Long) >= cutoff } catch (ignore) { return false }
    }
    if (list.size() > RAW_CACHE_MAX_ENTRIES) { list = list[-RAW_CACHE_MAX_ENTRIES..-1] }
    atomicState.rawDabSamplesEntries = list
    // Optionally rebuild lastByVent map
    def lastByVent = [:]
    list.each { e -> lastByVent[(e[1] as String)] = e }
    atomicState.rawDabLastByVent = lastByVent
  } catch (ignore) { }
}

def sampleRawDabData() {
  if (!settings?.enableRawCache) { return }
  try {
    Long ts = now()
    String hvacMode = calculateHvacModeRobust()
    def vents = getChildDevices()?.findAll { it.hasAttribute('percent-open') } ?: []
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
  } catch (e) {
    log(2, 'App', "Raw sampler error: ${e?.message}")
  }
}

// Room data caching methods
def cacheRoomData(String roomId, Map roomData) {
  initializeInstanceCaches()
  def instanceId = getInstanceId()
  def cacheKey = "instanceCache_${instanceId}"
  
  def roomCache = state."${cacheKey}_roomCache"
  def roomCacheTimestamps = state."${cacheKey}_roomCacheTimestamps"
  
  // Implement LRU cache with max size
  if (roomCache.size() >= MAX_CACHE_SIZE) {
    // Remove least recently used entry (oldest access time)
    def lruKey = null
    def oldestAccessTime = Long.MAX_VALUE
    roomCacheTimestamps.each { key, timestamp ->
      if (timestamp < oldestAccessTime) {
        oldestAccessTime = timestamp
        lruKey = key
      }
    }
    if (lruKey) {
      roomCache.remove(lruKey)
      roomCacheTimestamps.remove(lruKey)
      log(4, 'App', "Evicted LRU cache entry: ${lruKey}")
    }
  }
  
  roomCache[roomId] = roomData
  roomCacheTimestamps[roomId] = getCurrentTime()
}

def getCachedRoomData(String roomId) {
  initializeInstanceCaches()
  def instanceId = getInstanceId()
  def cacheKey = "instanceCache_${instanceId}"
  
  def roomCache = state."${cacheKey}_roomCache"
  def roomCacheTimestamps = state."${cacheKey}_roomCacheTimestamps"
  
  def timestamp = roomCacheTimestamps[roomId]
  if (!timestamp) return null
  
  if (isCacheExpired(roomId)) {
    roomCache.remove(roomId)
    roomCacheTimestamps.remove(roomId)
    return null
  }
  
  // Update access time for LRU tracking when item is accessed
  roomCacheTimestamps[roomId] = getCurrentTime()
  
  return roomCache[roomId]
}

def getRoomCacheSize() {
  initializeInstanceCaches()
  def instanceId = getInstanceId()
  def cacheKey = "instanceCache_${instanceId}"
  def roomCache = state."${cacheKey}_roomCache"
  return roomCache.size()
}

// Test helper method
def cacheRoomDataWithTimestamp(String roomId, Map roomData, Long timestamp) {
  initializeInstanceCaches()
  def instanceId = getInstanceId()
  def cacheKey = "instanceCache_${instanceId}"
  
  def roomCache = state."${cacheKey}_roomCache"
  def roomCacheTimestamps = state."${cacheKey}_roomCacheTimestamps"
  
  roomCache[roomId] = roomData
  roomCacheTimestamps[roomId] = timestamp
}

def isCacheExpired(String roomId) {
  initializeInstanceCaches()
  def instanceId = getInstanceId()
  def cacheKey = "instanceCache_${instanceId}"
  def roomCacheTimestamps = state."${cacheKey}_roomCacheTimestamps"
  
  def timestamp = roomCacheTimestamps[roomId]
  if (!timestamp) return true
  return (getCurrentTime() - timestamp) > ROOM_CACHE_DURATION_MS
}

// Pending request tracking
def markRequestPending(String requestId) {
  initializeInstanceCaches()
  def instanceId = getInstanceId()
  def cacheKey = "instanceCache_${instanceId}"
  def pendingRequests = state."${cacheKey}_pendingRoomRequests"
  pendingRequests[requestId] = true
}

def isRequestPending(String requestId) {
  initializeInstanceCaches()
  def instanceId = getInstanceId()
  def cacheKey = "instanceCache_${instanceId}"
  def pendingRequests = state."${cacheKey}_pendingRoomRequests"
  return pendingRequests[requestId] == true
}

def clearPendingRequest(String requestId) {
  initializeInstanceCaches()
  def instanceId = getInstanceId()
  def cacheKey = "instanceCache_${instanceId}"
  def pendingRequests = state."${cacheKey}_pendingRoomRequests"
  pendingRequests[requestId] = false
}

// Device reading caching methods
def cacheDeviceReading(String deviceKey, Map deviceData) {
  initializeInstanceCaches()
  def instanceId = getInstanceId()
  def cacheKey = "instanceCache_${instanceId}"
  
  def deviceCache = state."${cacheKey}_deviceCache"
  def deviceCacheTimestamps = state."${cacheKey}_deviceCacheTimestamps"
  
  // Implement LRU cache with max size
  if (deviceCache.size() >= MAX_CACHE_SIZE) {
    // Remove least recently used entry (oldest access time)
    def lruKey = null
    def oldestAccessTime = Long.MAX_VALUE
    deviceCacheTimestamps.each { key, timestamp ->
      if (timestamp < oldestAccessTime) {
        oldestAccessTime = timestamp
        lruKey = key
      }
    }
    if (lruKey) {
      deviceCache.remove(lruKey)
      deviceCacheTimestamps.remove(lruKey)
      log(4, 'App', "Evicted LRU device cache entry: ${lruKey}")
    }
  }
  
  deviceCache[deviceKey] = deviceData
  deviceCacheTimestamps[deviceKey] = getCurrentTime()
}

def getCachedDeviceReading(String deviceKey) {
  initializeInstanceCaches()
  def instanceId = getInstanceId()
  def cacheKey = "instanceCache_${instanceId}"
  
  def deviceCache = state."${cacheKey}_deviceCache"
  def deviceCacheTimestamps = state."${cacheKey}_deviceCacheTimestamps"
  
  def timestamp = deviceCacheTimestamps[deviceKey]
  if (!timestamp) return null
  
  if ((getCurrentTime() - timestamp) > DEVICE_CACHE_DURATION_MS) {
    deviceCache.remove(deviceKey)
    deviceCacheTimestamps.remove(deviceKey)
    return null
  }
  
  // Update access time for LRU tracking when item is accessed
  deviceCacheTimestamps[deviceKey] = getCurrentTime()
  
  return deviceCache[deviceKey]
}

// Device pending request tracking
def isDeviceRequestPending(String deviceKey) {
  initializeInstanceCaches()
  def instanceId = getInstanceId()
  def cacheKey = "instanceCache_${instanceId}"
  def pendingRequests = state."${cacheKey}_pendingDeviceRequests"
  return pendingRequests[deviceKey] == true
}

def markDeviceRequestPending(String deviceKey) {
  initializeInstanceCaches()
  def instanceId = getInstanceId()
  def cacheKey = "instanceCache_${instanceId}"
  def pendingRequests = state."${cacheKey}_pendingDeviceRequests"
  pendingRequests[deviceKey] = true
}

def clearDeviceRequestPending(String deviceKey) {
  initializeInstanceCaches()
  def instanceId = getInstanceId()
  def cacheKey = "instanceCache_${instanceId}"
  def pendingRequests = state."${cacheKey}_pendingDeviceRequests"
  pendingRequests[deviceKey] = false
}

// Clear all instance caches
def clearInstanceCache() {
  initializeInstanceCaches()
  def instanceId = getInstanceId()
  def cacheKey = "instanceCache_${instanceId}"
  
  def roomCache = state."${cacheKey}_roomCache"
  def roomCacheTimestamps = state."${cacheKey}_roomCacheTimestamps"
  def deviceCache = state."${cacheKey}_deviceCache"
  def deviceCacheTimestamps = state."${cacheKey}_deviceCacheTimestamps"
  def pendingRoomRequests = state."${cacheKey}_pendingRoomRequests"
  def pendingDeviceRequests = state."${cacheKey}_pendingDeviceRequests"
  
  roomCache.clear()
  roomCacheTimestamps.clear()
  deviceCache.clear()
  deviceCacheTimestamps.clear()
  pendingRoomRequests.clear()
  pendingDeviceRequests.clear()
  log(3, 'App', "Cleared all instance caches")
}

// ------------------------------
// End Instance-Based Caching Infrastructure
// ------------------------------

// Initialize request tracking
private initRequestTracking() {
  if (atomicState.activeRequests == null) {
    atomicState.activeRequests = 0
  }
}

// Check if we can make a request (under concurrent limit and circuit breaker state)
def canMakeRequest() {
  initRequestTracking()
  def currentActiveRequests = atomicState.activeRequests ?: 0
  
  // Immediate stuck counter detection and reset
  if (currentActiveRequests >= MAX_CONCURRENT_REQUESTS) {
    log(1, 'App', "CRITICAL: Active request counter is stuck at ${currentActiveRequests}/${MAX_CONCURRENT_REQUESTS} - resetting immediately")
    atomicState.activeRequests = 0
    log(1, 'App', "Reset active request counter to 0 immediately")
    return true  // Now we can make the request
  }
  
  return currentActiveRequests < MAX_CONCURRENT_REQUESTS
}

// Check if circuit breaker is closed (allows requests)
def isCircuitBreakerClosed(String uri = null) {
  try {
    if (!state.circuitOpenUntil) return true
    if (uri && state.circuitOpenUntil[uri]) {
      def currentTime = now()
      if (currentTime > state.circuitOpenUntil[uri]) {
        // Circuit breaker timeout expired, reset it
        state.circuitOpenUntil.remove(uri)
        atomicState.failureCounts?.remove(uri)
        log(2, 'App', "Circuit breaker reset for ${uri}")
        return true
      }
      return false
    }
    return true
  } catch (Exception e) {
    log(4, 'Request', "Error checking circuit breaker state: ${e.message}")
    return true
  }
}

// Increment active request counter
def incrementActiveRequests() {
  initRequestTracking()
  atomicState.activeRequests = (atomicState.activeRequests ?: 0) + 1
}

// Decrement active request counter
def decrementActiveRequests() {
  initRequestTracking()
  def currentCount = atomicState.activeRequests ?: 0
  atomicState.activeRequests = Math.max(0, currentCount - 1)
  log(1, 'App', "Decremented active requests from ${currentCount} to ${atomicState.activeRequests}")
}

// Wrapper for log.error that respects debugLevel setting
private void logError(String msg, String module = 'App', String correlationId = null) {
  int settingsLevel = (settings?.debugLevel as Integer) ?: 0
  if (settingsLevel > 0) {
    String prefix = correlationId ? "[${module}|${correlationId}]" : "[${module}]"
    log.error "${prefix} ${msg}"
    boolean __verbose = false
    try { __verbose = (atomicState?.verboseLogging == true) } catch (ignore) { }
    if (__verbose) {
      appendRecentLog(0, module, correlationId, msg)
    }
  }
  def ts = new Date().format('yyyy-MM-dd HH:mm:ss', (location?.timeZone ?: TimeZone.getTimeZone('UTC')))
  def errors = (state.recentErrors ?: []) + ["${ts} - ${msg}"]
  if (errors.size() > 20) {
    errors = errors[-20..-1]
  }
  state.recentErrors = errors
}

// Wrapper for log.warn that respects debugLevel setting
private void logWarn(String msg, String module = 'App', String correlationId = null) {
  int settingsLevel = (settings?.debugLevel as Integer) ?: 0
  if (settingsLevel > 0) {
    String prefix = correlationId ? "[${module}|${correlationId}]" : "[${module}]"
    log?.warn "${prefix} ${msg}"
    boolean __verbose = false
    try { __verbose = (atomicState?.verboseLogging == true) } catch (ignore) { }
    if (__verbose) {
      appendRecentLog(1, module, correlationId, msg)
    }
  }
}

// Store the last command error with optional suggested action
private void recordCommandError(String action, String message, String suggestion = null) {
  def tz = location?.timeZone ?: TimeZone.getTimeZone('UTC')
  state.lastCommandError = [
    ts: new Date().format('yyyy-MM-dd HH:mm:ss', tz),
    action: action,
    message: message,
    suggestion: suggestion
  ]
}

// Check for missing data like sensors or network and return issues
private List<String> getDataIssues() {
  List<String> issues = []
  if (!state.flairAccessToken) {
    issues << 'Flair authentication token missing. Re-authenticate to restore connectivity.'
  }
  def vents = getChildDevices()?.findAll { it.hasAttribute('percent-open') }
  if (!vents) {
    issues << 'No vents detected. Ensure devices are paired and online.'
  } else {
    def missingDuct = []
    def missingRoom = []
    def missingIds = []
    vents.each { v ->
      try {
        if (v.currentValue('duct-temperature-c') == null) { missingDuct << (v.getLabel() ?: v.getDeviceNetworkId() ?: v.getId()) }
        def roomT = (v.currentValue('room-current-temperature-c') ?: v.currentValue('current-temperature-c') ?: v.currentValue('temperature'))
        if (roomT == null) { missingRoom << (v.getLabel() ?: v.getDeviceNetworkId() ?: v.getId()) }
        if (!v.currentValue('room-id')) { missingIds << (v.getLabel() ?: v.getDeviceNetworkId() ?: v.getId()) }
      } catch (ignore) { }
    }
    if (missingDuct) { issues << "Vents missing duct-temperature-c: ${missingDuct.join(', ')}" }
    if (missingRoom) { issues << "Vents missing room temperature: ${missingRoom.join(', ')}" }
    if (missingIds) { issues << "Vents missing room-id: ${missingIds.join(', ')}" }
    def countWithBoth = vents.count { it.currentValue('duct-temperature-c') != null && ((it.currentValue('room-current-temperature-c') ?: it.currentValue('current-temperature-c') ?: it.currentValue('temperature')) != null) }
    if (countWithBoth == 0) { issues << 'No vents provide both duct and room temperatures; HVAC inference may be limited.' }
  }
  def __instId  = getInstanceId()
  def __roomMap = state."instanceCache_${__instId}_roomCache" ?: [:]
  if (!__roomMap || __roomMap.isEmpty()) {
    issues << 'Room data unavailable. Check network connectivity and sensors.'
  }
  return issues
}

private logDetails(String msg, details = null, int level = 3) {
  def settingsLevel = (settings?.debugLevel as Integer) ?: 0
  if (settingsLevel == 0) { return }
  if (level >= settingsLevel) {
    if (details) {
      log?.debug "${msg}\n${details}"
    } else {
      log?.debug msg
    }
  }
}

def isValidResponse(resp) {
  if (!resp) {
    log(1, 'App', 'HTTP Null response')
    return false
  }
  try {
    // Check if this is an actual HTTP response object (has hasError method)
    if (resp.hasProperty('hasError') && resp.hasError()) {
      // Check for authentication failures
      if (resp.getStatus() == 401 || resp.getStatus() == 403) {
        log(2, 'App', "Authentication error detected (${resp.getStatus()}), re-authenticating...")
        runIn(1, 'autoReauthenticate')
        return false
      }
      // Don't log 404s at error level - they might be expected
      if (resp.getStatus() == 404) {
        log(1, 'App', "HTTP 404 response")
      } else {
        log(1, 'App', "HTTP response error: ${resp.getStatus()}")
      }
      return false
    }
    
    // If it's not an HTTP response object, check if it's a hub load exception
    if (resp instanceof Exception || resp.toString().contains('LimitExceededException')) {
      log(1, 'App', "Hub load exception detected in response validation")
      return false
    }
    
  } catch (err) {
    log(1, 'App', "HTTP response validation error: ${err.message ?: err.toString()}")
    return false
  }
  return true
}

// Updated getDataAsync to accept a String callback name with simple throttling.
def getDataAsync(String uri, String callback, data = null, int retryCount = 0) {
  atomicState.failureCounts = atomicState.failureCounts ?: [:]
  
  // Check circuit breaker state before making request
  if (!isCircuitBreakerClosed(uri)) {
    if (retryCount < MAX_API_RETRY_ATTEMPTS) {
      log(2, 'App', "Circuit breaker open for ${uri}, scheduling retry ${retryCount + 1}")
      def retryData = [uri: uri, callback: callback, retryCount: retryCount + 1]
      if (data?.device && uri.contains('/room')) {
        retryData.data = [deviceId: data.device.getDeviceNetworkId()]
      } else {
        retryData.data = data
      }
      runInMillis(API_CALL_DELAY_MS, 'retryGetDataAsyncWrapper', [data: retryData])
    } else {
      logError "getDataAsync failed after ${MAX_API_RETRY_ATTEMPTS} retries for URI: ${uri} (circuit breaker open)"
    }
    return
  }
  
  if (canMakeRequest()) {
    atomicState.failureCounts.remove(uri)
    incrementActiveRequests()
    def headers = [ Authorization: "Bearer ${state.flairAccessToken}" ]
    def httpParams = [ uri: uri, headers: headers, contentType: CONTENT_TYPE, timeout: HTTP_TIMEOUT_SECS ]

    try {
      asynchttpGet('asyncHttpGetWrapper', httpParams, [uri: uri, callback: callback, data: data, retryCount: retryCount])
    } catch (Exception e) {
      log(2, 'App', "HTTP GET exception: ${e.message}")
      // Decrement on exception since the request didn't actually happen
      decrementActiveRequests()
      return
    }
  } else {
    if (retryCount < MAX_API_RETRY_ATTEMPTS) {
      def retryData = [uri: uri, callback: callback, retryCount: retryCount + 1]
      if (data?.device && uri.contains('/room')) {
        retryData.data = [deviceId: data.device.getDeviceNetworkId()]
      } else {
        retryData.data = data
      }
      def delay = (Math.pow(2, retryCount) * API_CALL_DELAY_MS).toLong()
      runInMillis(delay, 'retryGetDataAsyncWrapper', [data: retryData])
    } else {
      logError "getDataAsync failed after ${MAX_API_RETRY_ATTEMPTS} retries for URI: ${uri}"
      recordHttpFailure(uri)
    }
  }
}

// Wrapper method for getDataAsync retry
def retryGetDataAsyncWrapper(data) {
  if (!data || !data.uri) {
    logError "retryGetDataAsyncWrapper called with invalid data: ${data}"
    return
  }
  
  // Check if this is a room data request that should go through cache
  if (data.uri.contains('/room') && data.callback == 'handleRoomGetWithCache' && data.data?.deviceId) {
    // When retry data is passed through runInMillis, device objects become serialized
    // So we need to look up the device by ID instead
    def deviceId = data.data.deviceId
    def device = getChildDevice(deviceId)
    
    if (!device) {
      logError "retryGetDataAsyncWrapper: Could not find device with ID ${deviceId}"
      return
    }
    
    def isPuck = !device.hasAttribute('percent-open')
    def roomId = device.currentValue('room-id')
    
    if (roomId) {
      // Check cache first using instance-based cache
      def cachedData = getCachedRoomData(roomId)
      if (cachedData) {
        log(3, 'App', "Using cached room data for room ${roomId} on retry")
        processRoomTraits(device, cachedData)
        return
      }

      // Check if request is already pending
      if (isRequestPending(roomId)) {
        // log(3, 'App', "Room data request already pending for room ${roomId} on retry, skipping")
        return
      }

      markRequestPending(roomId)
    }

    // Re-route through cache check
    getRoomDataWithCache(device, deviceId, isPuck)
  } else {
    // Normal retry for non-room requests
    getDataAsync(data.uri, data.callback, data.data, data.retryCount)
  }
}

// Updated patchDataAsync to accept a String callback name with simple throttling.
// If callback is null, we use a no-op callback.
def patchDataAsync(String uri, String callback, body, data = null, int retryCount = 0) {
  if (!callback) { callback = 'noOpHandler' }
  atomicState.failureCounts = atomicState.failureCounts ?: [:]

  if (canMakeRequest()) {
    atomicState.failureCounts.remove(uri)
    incrementActiveRequests()
    def headers = [ Authorization: "Bearer ${state.flairAccessToken}" ]
    def httpParams = [
       uri: uri,
       headers: headers,
       contentType: CONTENT_TYPE,
       requestContentType: CONTENT_TYPE,
       timeout: HTTP_TIMEOUT_SECS,
       body: JsonOutput.toJson(body)
    ]

    try {
      asynchttpPatch(callback, httpParams, data)
    } catch (Exception e) {
      log(2, 'App', "HTTP PATCH exception: ${e.message}")
      recordCommandError("PATCH ${uri}", e.message, 'Check network connection')
      // Decrement on exception since the request didn't actually happen
      decrementActiveRequests()
      return
    }
  } else {
    if (retryCount < MAX_API_RETRY_ATTEMPTS) {
      def retryData = [uri: uri, callback: callback, body: body, data: data, retryCount: retryCount + 1]
      def delay = (Math.pow(2, retryCount) * API_CALL_DELAY_MS).toLong()
      runInMillis(delay, 'retryPatchDataAsyncWrapper', [data: retryData])
    } else {
      logError "patchDataAsync failed after ${MAX_API_RETRY_ATTEMPTS} retries for URI: ${uri}"
      recordCommandError("PATCH ${uri}", 'Request failed after retries', 'Verify network or Flair service')
      recordHttpFailure(uri)
    }
  }
}

// Wrapper method for patchDataAsync retry
def retryPatchDataAsyncWrapper(data) {
  if (!data || !data.uri || !data.callback) {
    logError "retryPatchDataAsyncWrapper called with invalid data: ${data}"
    return
  }

  patchDataAsync(data.uri, data.callback, data.body, data.data, data.retryCount)
}

private recordHttpFailure(String uri) {
  try { if (state.circuitOpenUntil == null) { state.circuitOpenUntil = [:] } } catch (ignore) { }

  atomicState.failureCounts = atomicState.failureCounts ?: [:]
  def count = (atomicState.failureCounts[uri] ?: 0) + 1
  atomicState.failureCounts[uri] = count
  if (count >= API_FAILURE_THRESHOLD) {
    def msg = "API circuit breaker activated for ${uri} after ${count} failures"
    log(1, 'App', msg)
  try { state.circuitOpenUntil[uri] = now() + CIRCUIT_RESET_MS } catch (ignore2) { }
}
}

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
}

// Wrapper method for authenticate retry
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
}

// Auto-authenticate when credentials are provided
def autoAuthenticate() {
  if (settings?.clientId && settings?.clientSecret && !state.flairAccessToken) {
    log(2, 'App', 'Auto-authenticating with provided credentials')
    login()
    unschedule(login)
    runEvery1Hour(login)
  }
}

// Automatically re-authenticate when token expires
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
  def respJson = null
  try {
    log(2, 'App', "handleRoomsWithPucks called")
    if (!isValidResponse(resp)) {
      log(2, 'App', "handleRoomsWithPucks: Invalid response status: ${resp?.getStatus()}")
      return
    }
    respJson = resp.getJson()
    
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
}

// New function to handle room data with caching
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
}

// New function to handle device data with caching (for pucks)
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
}

// New function to handle device reading with caching
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
}

// Modified handleRoomGet to include caching
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
}

// Add a method to clear the cache periodically (optional)
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

// Clear device cache periodically
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

// Periodic cleanup of pending request flags
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

def handleDeviceGet(resp, data) {
  decrementActiveRequests()  // Always decrement when response comes back
  if (!isValidResponse(resp) || !data?.device) { return }
  processVentTraits(data.device, resp.getJson())
}

// Modified handleDeviceGet to include caching
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
}

// Modified handlePuckGet to include caching
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
}

// Modified handlePuckReadingGet to include caching
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
   
   // Keep switch state in sync with opening percentage for dashboards and rules
   try {
     def pRaw = details?.data?.attributes?.get('percent-open'); if (pRaw != null) {
       BigDecimal p = (pRaw as BigDecimal)
       def sw = (p > 0G) ? 'on' : 'off'
       sendEvent(device, [name: 'switch', value: sw])
     }
   } catch (ignore) { }
if (details?.data?.attributes?.'system-voltage' != null) {
     def voltage = details.data.attributes['system-voltage']
     sendEvent(device, [name: 'voltage', value: voltage, unit: 'V'])
      // Estimate battery percentage from voltage (2.4V=0%, 3.2V=100%)
      try {
        BigDecimal v = (voltage as BigDecimal)
        BigDecimal pct = ((v - 2.4G) / 0.8G) * 100G
        if (pct < 0G) { pct = 0G }
        if (pct > 100G) { pct = 100G }
        sendEvent(device, [name: 'battery', value: pct.setScale(0, BigDecimal.ROUND_HALF_UP) as Integer, unit: '%'])
      } catch (ignore) { }

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
}

// Wrapper method for getStructureDataAsync retry
def retryGetStructureDataAsyncWrapper(data) {
  getStructureDataAsync(data?.retryCount ?: 0)
}

def handleStructureResponse(resp, data) {
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
    httpGet(httpParams) { resp ->
      decrementActiveRequests()

      if (resp.status < 200 || resp.status >= 300) {
        throw new Exception("HTTP request failed with status: ${resp.status}")
      }
      def response = resp.data
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
}

// Wrapper method for synchronous getStructureData retry
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
}

// Keep the old method name for backward compatibility
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

private void patchVentWithVerification(device, Integer percentOpen) {
  patchVent(device, percentOpen)
  try {
    runInMillis(VENT_VERIFY_DELAY_MS, 'verifyVentCommand', [data: [ventId: device.getDeviceNetworkId(), target: percentOpen, attempt: 1]])
  } catch (ignore) { }
}

def verifyVentCommand(Map data) {
  if (!data?.ventId) { return }
  def v = getChildDevice(data.ventId)
  if (!v) { return }
  Integer target = (data.target ?: 0) as Integer
  Integer attempt = (data.attempt ?: 1) as Integer
  Integer current = (v.currentValue('percent-open') ?: v.currentValue('level') ?: 0) as int
  if (current != target && attempt < MAX_VENT_VERIFY_ATTEMPTS) {
    runInMillis(VENT_VERIFY_DELAY_MS, 'verifyVentCommand', [data: [ventId: data.ventId, target: target, attempt: attempt + 1]])
    patchVent(v, target)
  } else if (current != target) {
    logWarn("Vent ${v.currentValue('room-name') ?: data.ventId} failed to reach ${target}% after ${attempt} attempts", 'QuickControl')
  }
}

def handleVentPatch(resp, data) {
  decrementActiveRequests()  // Always decrement when response comes back
  if (!isValidResponse(resp) || !data) {
    if (resp instanceof Exception || resp.toString().contains('LimitExceededException')) {
      log(2, 'App', "Vent patch failed due to hub load: ${resp.toString()}")
      recordCommandError('Vent patch', resp.toString(), 'Reduce hub load or retry')
    } else {
      log(2, 'App', "Vent patch failed - invalid response or data")
      recordCommandError('Vent patch', 'Invalid response from Flair', 'Check network connectivity')
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
    recordCommandError('Vent patch', 'Device object not found', 'Verify vent is paired')
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
}

// Verify that the vent reached the requested percent open
def verifyVentPercentOpen(data) {
  if (!data?.deviceId || data.targetOpen == null) { return }
  def device = getChildDevice(data.deviceId)
  if (!device) { return }
  def uri = "${BASE_URL}/api/vents/${data.deviceId}/current-reading"
  getDataAsync(uri, 'handleVentVerify', [device: device, targetOpen: data.targetOpen, attempt: data.attempt ?: 1])
}

// Handle verification response and retry if needed
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
def updateHvacStateFromDuctTemps() {
  // Enhanced puck-only HVAC cycle reliability with duct-room delta synthesis and hysteresis
  String previousMode = atomicState.thermostat1State?.mode ?: 'idle'
  String hvacMode = (calculateHvacModeRobust() ?: 'idle')
  Long currentTime = now()
  
  // Track atomicState markers for enhanced reliability
  try { atomicState.hvacCurrentMode = hvacMode } catch (ignore) { }
  try { atomicState.hvacLastChangeTs = currentTime } catch (ignore) { }
  
  if (hvacMode != previousMode) {
    // Record HVAC state transition
    recordDabEvent('HvacStateChange', [from: previousMode, to: hvacMode, timestamp: currentTime])
    appendDabActivityLog("HVAC State: ${previousMode} -> ${hvacMode}")
    
    try { atomicState.hvacLastMode = previousMode } catch (ignore) { }
    
    // Enhanced cycle start detection
    if (hvacMode in [COOLING, HEATING] && previousMode == 'idle') {
      try {
        atomicState.startedRunning = currentTime
        atomicState.startedCycle = currentTime
        if (hvacMode == COOLING) {
          atomicState.coolingCycleCount = (atomicState.coolingCycleCount ?: 0) + 1
        }
      } catch (ignore) { }
      recordDabEvent('CycleStart', [mode: hvacMode, timestamp: currentTime])
    }
    
    // Enhanced cycle end detection
    if (hvacMode == 'idle' && previousMode in [COOLING, HEATING]) {
      recordDabEvent('CycleEnd', [mode: previousMode, timestamp: currentTime])
      // Finalize logic will be triggered below
    }
  }
  
  if (hvacMode in [COOLING, HEATING]) {
    if (!atomicState.thermostat1State || atomicState.thermostat1State?.mode != hvacMode) {
      atomicStateUpdate('thermostat1State', 'mode', hvacMode)
      atomicStateUpdate('thermostat1State', 'startedRunning', atomicState.startedRunning ?: currentTime)
      atomicStateUpdate('thermostat1State', 'startedCycle', atomicState.startedCycle ?: currentTime)
      
      if (settings?.dabEnabled) {
        unschedule('initializeRoomStates')
        runInMillis(POST_STATE_CHANGE_DELAY_MS, 'initializeRoomStates', [data: hvacMode])
        recordStartingTemperatures()
        runEvery5Minutes('evaluateRebalancingVents')
        runEvery30Minutes('reBalanceVents')
      }
      updateDevicePollingInterval((settings?.pollingIntervalActive ?: POLLING_INTERVAL_ACTIVE) as Integer)
    }
  } else {
    // Enhanced idle state handling with explicit reason logging
    if (hvacMode == 'idle') {
      logIdleReason()
    }
    
    if (atomicState.thermostat1State) {
      unschedule('initializeRoomStates')
      unschedule('finalizeRoomStates')
      unschedule('evaluateRebalancingVents')
      unschedule('reBalanceVents')
      
      atomicStateUpdate('thermostat1State', 'finishedRunning', currentTime)
      
      // Ensure finalize logic triggers on mode transition events without thermostat
      if (settings?.dabEnabled) {
        def params = [
          ventIdsByRoomId: atomicState.ventsByRoomId,
          startedCycle: atomicState.thermostat1State?.startedCycle,
          startedRunning: atomicState.thermostat1State?.startedRunning,
          finishedRunning: currentTime,
          hvacMode: atomicState.thermostat1State?.mode
        ]
        runInMillis(TEMP_READINGS_DELAY_MS, 'finalizeRoomStates', [data: params])
      }
      atomicState.remove('thermostat1State')
      
      // Clear cycle markers when going idle
      try { 
        atomicState.remove('startedRunning')
        atomicState.remove('startedCycle')
      } catch (ignore) { }
      
      updateDevicePollingInterval((settings?.pollingIntervalIdle ?: POLLING_INTERVAL_IDLE) as Integer)
    }
  }
}

// Helper method to log explicit reason codes when remaining idle
def logIdleReason() {
  try {
    def vents = getChildDevices()?.findAll {
      it.currentValue('duct-temperature-c') != null &&
      (it.currentValue('room-current-temperature-c') != null ||
       it.currentValue('current-temperature-c') != null ||
       it.currentValue('temperature') != null)
    }
    
    if (!vents || vents.isEmpty()) {
      log(3, 'DAB', 'Remaining idle: No vents with valid duct/room temperature readings')
      return
    }
    
    def diffs = []
    vents.each { v ->
      try {
        BigDecimal duct = v.currentValue('duct-temperature-c') as BigDecimal
        BigDecimal room = (v.currentValue('room-current-temperature-c') ?:
                           v.currentValue('current-temperature-c') ?:
                           v.currentValue('temperature')) as BigDecimal
        diffs << (duct - room)
      } catch (ignore) { }
    }
    
    if (!diffs) {
      log(3, 'DAB', 'Remaining idle: Unable to calculate temperature differences')
      return
    }
    
    def sorted = diffs.sort()
    BigDecimal median = sorted[sorted.size().intdiv(2)] as BigDecimal
    BigDecimal absMedian = median.abs()
    
    if (absMedian <= DUCT_TEMP_DIFF_THRESHOLD) {
      log(3, 'DAB', "Remaining idle: Median duct-room delta (${median.round(2)}°C) within threshold (±${DUCT_TEMP_DIFF_THRESHOLD}°C)")
    } else if (absMedian <= DUCT_ROOM_DELTA_HYSTERESIS) {
      log(3, 'DAB', "Remaining idle: Median duct-room delta (${median.round(2)}°C) within hysteresis band")
    } else {
      log(3, 'DAB', "Remaining idle: Insufficient confidence in HVAC state despite delta (${median.round(2)}°C)")
    }
  } catch (Exception e) {
    log(3, 'DAB', "Remaining idle: Error calculating reason - ${e.message}")
  }
}

// Helper method to record DAB events for diagnostics
def recordDabEvent(String eventType, Map data) {
  try {
    def events = atomicState.dabEvents ?: []
    def event = [
      type: eventType,
      timestamp: data.timestamp ?: now(),
      data: data
    ]
    events << event
    
    // Keep only last 50 events to prevent memory growth
    if (events.size() > 50) {
      events = events.takeRight(50)
    }
    
    atomicState.dabEvents = events
    log(4, 'DAB', "Recorded event: ${eventType} - ${data}")
  } catch (Exception e) {
    log(2, 'DAB', "Failed to record event ${eventType}: ${e.message}")
  }
}

def reBalanceVents() {
  log(3, 'App', 'Rebalancing Vents!!!')
  appendDabActivityLog("Rebalancing vents")
  runDynamicAirflowBalancing()
  def params = [
    ventIdsByRoomId: atomicState.ventsByRoomId,
    startedCycle: atomicState.thermostat1State?.startedCycle,
    startedRunning: atomicState.thermostat1State?.startedRunning,
    finishedRunning: now(),
    hvacMode: atomicState.thermostat1State?.mode
  ]
  finalizeRoomStates(params)
  initializeRoomStates(atomicState.thermostat1State?.mode)
  try {
    def decisions = (state.recentVentDecisions ?: []).findAll { it.stage == 'final' }
    def changed = decisions.findAll { it.pct != null }
    def summary = changed.takeRight(5).collect { d -> "${d.room}:${d.pct}%" }
    if (summary) { appendDabActivityLog("Changes: ${summary.join(', ')}") }
  } catch (ignore) { }
}

def evaluateRebalancingVents() {
  if (!atomicState.thermostat1State) { return }
  def ventIdsByRoomId = atomicState.ventsByRoomId
  String hvacMode = atomicState.thermostat1State?.mode
  def setPoint = getGlobalSetpoint(hvacMode)

  ventIdsByRoomId.each { roomId, ventIds ->
    for (ventId in ventIds) {
      try {
        def vent = getChildDevice(ventId)
        if (!vent) { continue }
        if (vent.currentValue('room-active') != 'true') { continue }
        def currPercentOpen = (vent.currentValue('percent-open') ?: 0).toInteger()
        if (currPercentOpen <= STANDARD_VENT_DEFAULT_OPEN) { continue }
        def tVal = getRoomTemp(vent)
        def roomTemp = (tVal instanceof Number ? tVal : 0)
        if (!hasRoomReachedSetpoint(hvacMode, setPoint, roomTemp, REBALANCING_TOLERANCE)) {
          continue
        }
        log(3, 'App', "Rebalancing Vents - '${vent.currentValue('room-name')}' is at ${roomTemp}° (target: ${setPoint})")
        reBalanceVents()
        return // Exit after first rebalancing to avoid multiple adjustments per evaluation
      } catch (err) {
        logError err
      }
    }
  }
}

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
    def rates = hist?.hourlyRates?.get(roomId)?.get(hvacMode)?.get(hour as Integer) ?: []
    return rates.collect { it as BigDecimal }
  } catch (ignore) {
    return []
  }
}

// -------------
// EWMA + MAD helpers
// -------------
private BigDecimal getEwmaRate(String roomId, String hvacMode, Integer hour) {
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
    if (hist == null) {
      atomicState.dabHistory = [entries: [], hourlyRates: [:]]
      return
    }
    if (hist instanceof List) {
      atomicState.dabHistory = [entries: hist, hourlyRates: [:]]
      return
    }
    if (hist instanceof Map) {
      if (hist.entries == null) { hist.entries = [] }
      if (hist.hourlyRates == null) { hist.hourlyRates = [:] }
      // Build hourly index if missing and we have entries
      if ((hist.hourlyRates == null || hist.hourlyRates.isEmpty()) && hist.entries && hist.entries.size() > 0) {
        def index = [:]
        try {
          Integer retention = getRetentionDays()
          Long cutoff = now() - retention * 24L * 60L * 60L * 1000L
          hist.entries.each { e ->
            try {
              Long ts = e[0] as Long; if (ts < cutoff) { return }
              String r = e[1]; String m = e[2]; Integer h = (e[3] as Integer)
              BigDecimal rate = e[4] as BigDecimal
              def room = index[r] ?: [:]
              def mode = room[m] ?: [:]
              def list = (mode[h] ?: []) as List
              list << rate
              // Trim list to retention size
              if (list.size() > retention) { list = list[-retention..-1] }
              mode[h] = list
              room[m] = mode
              index[r] = room
            } catch (ignore) { }
          }
          hist.hourlyRates = index
        } catch (ignore) { }
      }
      try { atomicState.dabHistory = hist } catch (ignoreX) { }
    }
  } catch (Exception e) {
    logWarn "Failed to initialize/normalize DAB history: ${e?.message}"
  }
}

private Integer getRetentionDays() {
  try {
    Integer v = (atomicState?.dabHistoryRetentionDays ?: DEFAULT_HISTORY_RETENTION_DAYS) as Integer
    if (v < 1) { v = 1 }
    return v
  } catch (ignore) { return DEFAULT_HISTORY_RETENTION_DAYS }
}

// CI-safe read for DAB enabled toggle
private boolean isDabEnabled() {
  try {
    // Read from registered inputs
    def st = null
    try { st = settings } catch (ignore) { }
    def val = st?.dabEnabled
    if (val != null) { return val == true }
  } catch (ignore) { }
  // Fallback to mirrored state (used by CI/tests)
  return (atomicState?.dabEnabled == true)
}

// Retrieve the average hourly efficiency rate for a room and HVAC mode
// Retrieve the average hourly efficiency rate for a room and HVAC mode
// Hubitat serializes map keys as Strings when stored in `atomicState`.
// Using a numeric key (e.g. `0`) to retrieve a value that was stored as
// a String ("0") results in a miss and an empty history. To ensure the
// history is accessible, convert the hour to a String for both storage
// and retrieval.
def getAverageHourlyRate(String roomId, String hvacMode, Integer hour) {
  initializeDabHistory()
  // Prefer EWMA if enabled and available
  if (atomicState?.enableEwma) {
    def ew = getEwmaRate(roomId, hvacMode, hour)
    if (ew != null) { return cleanDecimalForJson(ew as BigDecimal) }
  }
  def hist = atomicState?.dabHistory
  def rates = []
  try {
    rates = hist?.hourlyRates?.get(roomId)?.get(hvacMode)?.get(hour as Integer) ?: []
  } catch (ignore) { rates = [] }
  if (!rates || rates.size() == 0) {
    rates = getHourlyRates(roomId, hvacMode, hour) ?: []
  }
  // Carry-forward: if no data for this hour, optionally use the most recent prior hour's last observed rate
  if ((!rates || rates.size() == 0)) {
    boolean carry = true
    try { carry = (atomicState?.carryForwardLastHour != false) } catch (ignore) { carry = true }
    if (carry) {
      for (int i = 1; i <= 23; i++) {
        int prevHour = ((hour as int) - i) % 24
        if (prevHour < 0) { prevHour += 24 }
        def last = getLastObservedHourlyRate(roomId, hvacMode, prevHour)
        if (last != null) { return cleanDecimalForJson(last as BigDecimal) }
      }
    }
    return 0.0
  }
  BigDecimal sum = 0.0
  rates.each { sum += it as BigDecimal }
  BigDecimal base = (sum / rates.size())
  // Apply adaptive boost from recent hours if enabled
  try {
    boolean enabled = (atomicState?.enableAdaptiveBoost != false)
    if (enabled) {
      BigDecimal boost = getAdaptiveBoostFactor(roomId, hvacMode, hour)
      base = base * (1.0 + boost)
    }
  } catch (ignore) { }
  return cleanDecimalForJson(base)
}

// Find the most recent recorded rate for a room/mode/hour within retention
private BigDecimal getLastObservedHourlyRate(String roomId, String hvacMode, Integer hour) {
  try {
    def hist = atomicState?.dabHistory
    def entries = (hist instanceof List) ? (hist as List) : (hist?.entries ?: [])
    if (!entries) { return null }
    // Iterate from newest to oldest
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

// Compute adaptive boost factor (0..ADAPTIVE_MAX_BOOST_PERCENT/100) based on recent hours with large upward corrections
private BigDecimal getAdaptiveBoostFactor(String roomId, String hvacMode, Integer hour) {
  try {
    boolean enabled = (atomicState?.enableAdaptiveBoost != null) ? (atomicState?.enableAdaptiveBoost != false) : ADAPTIVE_BOOST_ENABLED
    if (!enabled) { return 0.0 }
    Integer lookback = (atomicState?.adaptiveLookbackPeriods ?: ADAPTIVE_LOOKBACK_PERIODS) as Integer
    BigDecimal threshPct = (atomicState?.adaptiveThresholdPercent ?: ADAPTIVE_THRESHOLD_PERCENT) as BigDecimal
    BigDecimal boostPct = (atomicState?.adaptiveBoostPercent ?: ADAPTIVE_BOOST_PERCENT) as BigDecimal
    BigDecimal boostMaxPct = (atomicState?.adaptiveMaxBoostPercent ?: ADAPTIVE_MAX_BOOST_PERCENT) as BigDecimal
    def entries = atomicState?.adaptiveMarksEntries ?: []
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
    def list = atomicState?.adaptiveMarksEntries ?: []
    list << [now(), roomId, hvacMode, (hour as Integer), (ratio as BigDecimal)]
    if (list.size() > 5000) { list = list[-5000..-1] }
    atomicState.adaptiveMarksEntries = list
  } catch (ignore) { }
}

// Append a new efficiency rate to the rolling 10-day hourly history
// Ensures the hour key is stored as a String to match Hubitat's
// serialization behaviour.
def appendHourlyRate(String roomId, String hvacMode, Integer hour, BigDecimal rate) {
  if (!roomId || !hvacMode || hour == null || rate == null) {
    recordHistoryError('Null value detected while appending hourly rate')
    return null
  }
  
  initializeDabHistory()
  def currentDate = new Date().format('yyyy-MM-dd', location?.timeZone ?: TimeZone.getTimeZone('UTC'))
  def currentHour = hour as Integer
  
  // Handle hour-slot integrity & duplicate prevention
  def result = handleHourSlotIntegrity(roomId, hvacMode, currentHour, currentDate, rate)
  if (result.isDuplicate) {
    log(3, 'DAB', "Updated existing sample for ${roomId}:${hvacMode}:${currentHour} on ${currentDate}")
    return result.effectiveRate
  }
  
  // Get existing samples for this room/mode/hour
  def samples = getHourlyRateSamples(roomId, hvacMode, currentHour)
  
  // Add new sample
  samples << rate
  
  // Purge old samples beyond retention
  samples = purgeOldSamples(samples, roomId, hvacMode, currentHour)
  
  // Calculate arithmetic mean and metadata
  def metadata = calculateArithmeticMeanWithMetadata(samples, roomId, hvacMode, currentHour, rate)
  
  // Apply carry-forward logic if needed
  if (metadata.samplesCount == 0) {
    metadata = applyCarryForwardLogic(roomId, hvacMode, currentHour, metadata)
  }
  
  // Store samples and metadata
  storeHourlyRateSamples(roomId, hvacMode, currentHour, samples)
  storeHourlyRateMetadata(roomId, hvacMode, currentHour, metadata)
  
  // Also maintain flat entries for compatibility
  maintainFlatEntries(roomId, hvacMode, currentHour, rate)
  
  // Log HourCommit event
  recordDabEvent('HourCommit', [
    room: roomId,
    mode: hvacMode, 
    hour: currentHour,
    rate: rate,
    effectiveRate: metadata.effectiveRate,
    samplesCount: metadata.samplesCount,
    anomalyFlag: metadata.anomalyFlag,
    carryForwardUsed: metadata.carryForwardUsed
  ])
  
  log(3, 'DAB', "HourCommit ${roomId}:${hvacMode}:${currentHour} samples=${metadata.samplesCount} effective=${metadata.effectiveRate.round(4)} anomaly=${metadata.anomalyFlag} carryForward=${metadata.carryForwardUsed}")
  
  return metadata.effectiveRate
}

// Helper method to handle hour-slot integrity and prevent duplicates within same cycle
def handleHourSlotIntegrity(String roomId, String hvacMode, Integer hour, String currentDate, BigDecimal rate) {
  try {
    def samples = atomicState.dabSamples ?: [:]
    def roomKey = "${roomId}:${hvacMode}:${hour}"
    def hourSamples = samples[roomKey] ?: []
    
    // Check for duplicate entry for same date/hour/mode within same cycle
    def existingIndex = hourSamples.findIndexOf { sample ->
      sample.date == currentDate && sample.hour == hour && sample.mode == hvacMode
    }
    
    if (existingIndex >= 0) {
      // Update existing entry instead of adding duplicate
      hourSamples[existingIndex].rate = rate
      hourSamples[existingIndex].timestamp = now()
      samples[roomKey] = hourSamples
      atomicState.dabSamples = samples
      
      // Recalculate effective rate
      def rates = hourSamples.collect { it.rate as BigDecimal }
      def effectiveRate = rates ? rates.sum() / rates.size() : 0.0
      return [isDuplicate: true, effectiveRate: effectiveRate]
    }
    
    return [isDuplicate: false, effectiveRate: null]
  } catch (Exception e) {
    log(2, 'DAB', "Error in handleHourSlotIntegrity: ${e.message}")
    return [isDuplicate: false, effectiveRate: null]
  }
}

// Get hourly rate samples for a specific room/mode/hour
def getHourlyRateSamples(String roomId, String hvacMode, Integer hour) {
  try {
    def samples = atomicState.dabSamples ?: [:]
    def roomKey = "${roomId}:${hvacMode}:${hour}"
    return (samples[roomKey] ?: []).collect { it.rate as BigDecimal }
  } catch (Exception e) {
    log(2, 'DAB', "Error getting hourly rate samples: ${e.message}")
    return []
  }
}

// Purge old samples beyond retention
def purgeOldSamples(List samples, String roomId, String hvacMode, Integer hour) {
  try {
    Integer retention = getRetentionDays()
    if (samples.size() > retention) {
      def purged = samples.size() - retention
      samples = samples.takeRight(retention)
      log(3, 'DAB', "Purged ${purged} old samples for ${roomId}:${hvacMode}:${hour}")
    }
    return samples
  } catch (Exception e) {
    log(2, 'DAB', "Error purging old samples: ${e.message}")
    return samples
  }
}

// Calculate arithmetic mean with metadata including anomaly detection
def calculateArithmeticMeanWithMetadata(List samples, String roomId, String hvacMode, Integer hour, BigDecimal newRate) {
  def metadata = [
    rawAvg: 0.0,
    effectiveRate: 0.0,
    samplesCount: samples.size(),
    carryForwardUsed: false,
    anomalyFlag: false
  ]
  
  if (samples.isEmpty()) {
    return metadata
  }
  
  // Calculate raw arithmetic mean
  BigDecimal sum = samples.sum() as BigDecimal
  BigDecimal rawAvg = sum / samples.size()
  metadata.rawAvg = rawAvg
  
  // Detect anomaly for the new rate
  def anomalyResult = detectAnomaly(samples, newRate)
  metadata.anomalyFlag = anomalyResult.isAnomaly
  
  // Apply anomaly influence dampening if anomaly detected
  BigDecimal effectiveRate = rawAvg
  if (anomalyResult.isAnomaly) {
    BigDecimal anomalyFactor = getAnomalyFactor(roomId, hour)
    // Weight the new rate contribution based on anomaly factor
    BigDecimal adjustedNewRate = newRate * anomalyFactor + rawAvg * (1.0 - anomalyFactor)
    // Recalculate effective rate with adjusted contribution
    BigDecimal adjustedSum = sum - newRate + adjustedNewRate
    effectiveRate = adjustedSum / samples.size()
    
    log(3, 'DAB', "Anomaly detected for ${roomId}:${hvacMode}:${hour} rate=${newRate} factor=${anomalyFactor} adjusted=${effectiveRate}")
    recordDabEvent('Anomaly', [room: roomId, mode: hvacMode, hour: hour, rate: newRate, factor: anomalyFactor])
  }
  
  metadata.effectiveRate = effectiveRate
  return metadata
}

// Anomaly detection using MAD (Median Absolute Deviation)
def detectAnomaly(List samples, BigDecimal candidate) {
  if (samples.size() < MIN_SAMPLES_FOR_ANOMALY_CHECK) {
    return [isAnomaly: false, mad: 0.0, median: 0.0]
  }
  
  def sorted = samples.collect { it as BigDecimal }.sort()
  BigDecimal median = sorted[sorted.size().intdiv(2)]
  
  // Calculate MAD
  def deviations = sorted.collect { (it - median).abs() }
  def devSorted = deviations.sort()
  BigDecimal mad = devSorted[devSorted.size().intdiv(2)]
  
  // Check if candidate is anomaly
  BigDecimal candidateDeviation = (candidate - median).abs()
  BigDecimal threshold = mad * ANOMALY_MAD_MULTIPLIER
  
  boolean isAnomaly = false
  if (mad > 0.0 && candidateDeviation > threshold) {
    BigDecimal percentDeviation = candidateDeviation / median
    if (percentDeviation > ANOMALY_PERCENT_THRESHOLD) {
      isAnomaly = true
    }
  }
  
  return [isAnomaly: isAnomaly, mad: mad, median: median]
}

// Get anomaly influence factor with decay
def getAnomalyFactor(String roomId, Integer hour) {
  try {
    def anomalyInfluence = atomicState.anomalyInfluence ?: [:]
    def key = "${roomId}:${hour}"
    def influence = anomalyInfluence[key] ?: [factor: 1.0, steps: 0]
    
    // Decay the influence over time
    if (influence.steps >= ANOMALY_DECAY_STEPS) {
      anomalyInfluence.remove(key)
      atomicState.anomalyInfluence = anomalyInfluence
      return 1.0
    } else {
      // Calculate current factor: 1.0 -> 0.6 -> 0.3
      BigDecimal currentFactor = 1.0 - (influence.steps * 0.4)
      if (currentFactor < 0.3) currentFactor = 0.3
      
      // Update for next time
      influence.steps += 1
      influence.factor = currentFactor
      anomalyInfluence[key] = influence
      atomicState.anomalyInfluence = anomalyInfluence
      
      return currentFactor
    }
  } catch (Exception e) {
    log(2, 'DAB', "Error getting anomaly factor: ${e.message}")
    return 1.0
  }
}

// Apply carry-forward logic when zero samples
def applyCarryForwardLogic(String roomId, String hvacMode, Integer hour, Map metadata) {
  try {
    // Try to get previous hour's effective rate
    Integer previousHour = (hour - 1 + 24) % 24
    BigDecimal carryForwardRate = getPreviousHourEffectiveRate(roomId, hvacMode, previousHour)
    
    if (carryForwardRate == null || carryForwardRate == 0.0) {
      carryForwardRate = MIN_TEMP_CHANGE_RATE
    }
    
    metadata.effectiveRate = carryForwardRate
    metadata.carryForwardUsed = true
    
    recordDabEvent('CarryForward', [room: roomId, mode: hvacMode, hour: hour, rate: carryForwardRate])
    log(3, 'DAB', "CarryForward ${roomId}:${hvacMode}:${hour} rate=${carryForwardRate}")
    
    return metadata
  } catch (Exception e) {
    log(2, 'DAB', "Error in carry-forward logic: ${e.message}")
    metadata.effectiveRate = MIN_TEMP_CHANGE_RATE
    return metadata
  }
}

// Get previous hour's effective rate
def getPreviousHourEffectiveRate(String roomId, String hvacMode, Integer hour) {
  try {
    def metadata = atomicState.dabMetadata ?: [:]
    def key = "${roomId}:${hvacMode}:${hour}"
    return metadata[key]?.effectiveRate ?: null
  } catch (Exception e) {
    return null
  }
}

// Store hourly rate samples
def storeHourlyRateSamples(String roomId, String hvacMode, Integer hour, List samples) {
  try {
    def allSamples = atomicState.dabSamples ?: [:]
    def roomKey = "${roomId}:${hvacMode}:${hour}"
    
    def sampleData = samples.collect { rate ->
      [
        rate: rate,
        date: new Date().format('yyyy-MM-dd', location?.timeZone ?: TimeZone.getTimeZone('UTC')),
        hour: hour,
        mode: hvacMode,
        timestamp: now()
      ]
    }
    
    allSamples[roomKey] = sampleData
    atomicState.dabSamples = allSamples
  } catch (Exception e) {
    log(2, 'DAB', "Error storing hourly rate samples: ${e.message}")
  }
}

// Store hourly rate metadata
def storeHourlyRateMetadata(String roomId, String hvacMode, Integer hour, Map metadata) {
  try {
    def allMetadata = atomicState.dabMetadata ?: [:]
    def key = "${roomId}:${hvacMode}:${hour}"
    allMetadata[key] = metadata
    atomicState.dabMetadata = allMetadata
  } catch (Exception e) {
    log(2, 'DAB', "Error storing hourly rate metadata: ${e.message}")
  }
}

// Maintain flat entries for compatibility
def maintainFlatEntries(String roomId, String hvacMode, Integer hour, BigDecimal rate) {
  try {
    def hist = atomicState?.dabHistory
    if (hist == null) { hist = [entries: [], hourlyRates: [:]] }
    
    def entries = (hist instanceof List) ? (hist as List) : (hist.entries ?: [])
    Long ts = now()
    entries << [ts, roomId, hvacMode, hour as Integer, rate]
    
    // Purge old entries
    Integer retention = getRetentionDays()
    Long cutoff = ts - retention * 24L * 60L * 60L * 1000L
    entries = entries.findAll { e ->
      try { return (e[0] as Long) >= cutoff } catch (ignore) { return false }
    }
    
    if (hist instanceof List) {
      atomicState.dabHistory = entries
    } else {
      try { hist.entries = entries } catch (ignore) { }
      atomicState.dabHistory = hist
    }
  } catch (Exception e) {
    log(2, 'DAB', "Error maintaining flat entries: ${e.message}")
  }
}

def appendDabHistory(String roomId, String hvacMode, Integer hour, BigDecimal rate) {
  if (!roomId || !hvacMode || hour == null || rate == null) {
    recordHistoryError('Null value detected while appending DAB history entry')
    return
  }
  initializeDabHistory()
  def hist = atomicState?.dabHistory
  if (hist == null) { hist = [entries: [], hourlyRates: [:]] }
  def entries = (hist instanceof List) ? (hist as List) : (hist?.entries ?: [])
  Long ts = now()
  entries << [ts, roomId, hvacMode, (hour as Integer), (rate as BigDecimal)]
  Integer retention = getRetentionDays()
  Long cutoff = ts - retention * 24L * 60L * 60L * 1000L
  entries = entries.findAll { e ->
    try { return (e[0] as Long) >= cutoff } catch (ignore) { return false }
  }
  if (hist instanceof List) {
    try { atomicState.dabHistory = entries } catch (ignore) { }
  } else {
    try { hist.entries = entries } catch (ignore) { }
    try { atomicState.dabHistory = hist } catch (ignore) { }
  }
  try { if (entries) { atomicState.dabHistoryStartTimestamp = (entries[0][0] as Long) } } catch (ignore) { }
  try { atomicState.lastHvacMode = hvacMode } catch (ignore) { }
}

// Aggregate previous day's hourly rates into daily averages
def aggregateDailyDabStats() {
  initializeDabHistory()
  def hist = atomicState?.dabHistory
  def entries = (hist instanceof List) ? hist : (hist?.entries ?: [])
  if ((!entries || entries.size() == 0) && (hist instanceof Map)) {
    // Support legacy per-room structure by rebuilding indexes on the fly
    try {
      def res = reindexDabHistory()
      hist = atomicState?.dabHistory
      entries = (hist instanceof List) ? hist : (hist?.entries ?: [])
    } catch (ignore) { }
  }
  // If still no flat entries, compute aggregation directly from legacy map
  def tz = location?.timeZone ?: TimeZone.getTimeZone('UTC')
  String targetDate = (new Date() - 1).format('yyyy-MM-dd', tz)
  def stats = atomicState?.dabDailyStats ?: [:]
  def agg = [:]
  if (!entries || entries.size() == 0) {
    if (hist instanceof Map) {
      try {
        hist.each { roomId, modeOrMeta ->
          if (roomId in ['entries','hourlyRates']) { return }
          if (!(modeOrMeta instanceof Map)) { return }
          modeOrMeta.each { hvacMode, recList ->
            if (!(recList instanceof List)) { return }
            recList.each { rec ->
              try {
                if (rec instanceof Map && rec.date && rec.hour != null && rec.rate != null) {
                  if (rec.date.toString() != targetDate) { return }
                  def roomMap = agg[roomId.toString()] ?: [:]
                  def list = (roomMap[hvacMode.toString()] ?: []) as List
                  list << (rec.rate as BigDecimal)
                  roomMap[hvacMode.toString()] = list
                  agg[roomId.toString()] = roomMap
                }
              } catch (ignore2) { }
            }
          }
        }
      } catch (ignore) { }
    }
  } else {
    // Use flat entries
    entries.each { e ->
      try {
        Date d = new Date(e[0] as Long)
        String ds = d.format('yyyy-MM-dd', tz)
        if (ds != targetDate) { return }
        String r = e[1]; String m = e[2]
        BigDecimal rate = (e[4] as BigDecimal)
        def roomMap = agg[r] ?: [:]
        def list = (roomMap[m] ?: []) as List
        list << rate
        roomMap[m] = list
        agg[r] = roomMap
      } catch (ignore) { }
    }
  }
  agg.each { roomId, modeMap ->
    modeMap.each { hvacMode, list ->
      if (list && list.size() > 0) {
        BigDecimal sum = 0.0
        list.each { sum += it as BigDecimal }
        BigDecimal avg = cleanDecimalForJson(sum / list.size())
        def roomStats = stats[roomId] ?: [:]
        def modeStats = roomStats[hvacMode] ?: []
        modeStats = modeStats.findAll { it.date != targetDate }
        modeStats << [date: targetDate, avg: avg]
        Integer retention = getRetentionDays()
        String cutoff = (new Date() - retention).format('yyyy-MM-dd', tz)
        modeStats = modeStats.findAll { it.date >= cutoff }
        roomStats[hvacMode] = modeStats
        stats[roomId] = roomStats
      }
    }
  }
  try { atomicState.dabDailyStats = stats } catch (ignore) { }
}

// Rebuild hourlyRates index from timestamped entries and refresh daily stats
def reindexDabHistory() {
  initializeDabHistory()
  def hist = atomicState?.dabHistory
  def entries = (hist instanceof List) ? hist : (hist?.entries ?: [])
  // If no flat entries are present, try to derive from legacy per-room structure
  if ((!entries || entries.size() == 0) && (hist instanceof Map)) {
    try {
      def legacy = []
      def tz = location?.timeZone ?: TimeZone.getTimeZone('UTC')
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
      if (legacy && legacy.size() > 0) {
        entries = legacy
      }
    } catch (ignore) { }
  }
  Integer retention = getRetentionDays()
  Long cutoff = now() - retention * 24L * 60L * 60L * 1000L
  // Rebuild index from scratch
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
    try { atomicState.dabHistory = [entries: entries, hourlyRates: index] } catch (ignore) { }
  } else {
    try { hist.hourlyRates = index } catch (ignore) { }
    try { hist.entries = entries } catch (ignore) { }
    try { atomicState.dabHistory = hist } catch (ignore) { }
  }
  } catch (ignore) { }
  // Recompute daily stats for all days within retention
  try {
    def tz = location?.timeZone ?: TimeZone.getTimeZone('UTC')
    def byDay = [:] // room -> mode -> dateStr -> List<BigDecimal>
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
      stats[roomId] = roomStats
    }
    atomicState.dabDailyStats = stats
  } catch (ignore) { }
  return [entries: entries?.size() ?: 0, rooms: rooms]
}

def appendDabActivityLog(String message) {
  def tz = location?.timeZone ?: TimeZone.getTimeZone('UTC')
  String ts = new Date().format('yyyy-MM-dd HH:mm:ss', tz)
  Long start = atomicState?.dabHistoryStartTimestamp
  if (!start) {
    start = now()
    try { atomicState.dabHistoryStartTimestamp = start } catch (ignore) { }
  }
  String startStr = new Date(start).format('yyyy-MM-dd HH:mm:ss', tz)

  // Maintain legacy string log
  def list = atomicState?.dabActivityLog ?: []
  list << "${ts} (since ${startStr}) - ${message}"
  if (list.size() > 100) { list = list[-100..-1] }
  atomicState.dabActivityLog = list

  // Maintain structured archive for tests/export
  def archive = atomicState?.dabHistoryArchive ?: []
  archive << [type: 'activity', ts: ts, since: startStr, message: message]
  if (archive.size() > 1000) { archive = archive[-1000..-1] }
  try { atomicState.dabHistoryArchive = archive } catch (ignore) { }
}

// Read combined DAB history archive (structured). Falls back to in-memory activity log.
def readDabHistoryArchive() {
  def archive = atomicState?.dabHistoryArchive ?: []
  if (archive && archive instanceof List && archive.size() > 0) { return archive }
  def list = atomicState?.dabActivityLog ?: []
  if (list && list instanceof List) {
    try {
      return list.collect { String s ->
        def msg = s.contains(' - ') ? s.split(' - ', 2)[1] : s
        [type: 'activity', message: msg]
      }
    } catch (ignore) { }
  }
  return []
}

// Verify legacy date->hour history completeness; log missing hours.
def checkDabHistoryIntegrity() {
  try {
    def hist = atomicState?.dabHistory
    if (!(hist instanceof Map)) { return }
    // Detect legacy map keyed by date strings
    hist.each { k, v ->
      if (!(k in ['entries','hourlyRates']) && (v instanceof Map)) {
        String dateStr = k.toString()
        def hoursMap = v
        (0..23).each { hr ->
          if (!hoursMap.containsKey(hr)) {
            String msg = "Integrity: date ${dateStr} missing hour ${hr}"
            logWarn msg, 'DAB'
            appendDabActivityLog msg
          }
        }
      }
    }
  } catch (ignore) { }
}

def recordHistoryError(String message) {
  def errs = atomicState?.dabHistoryErrors ?: []
  String ts = new Date().format('yyyy-MM-dd HH:mm:ss', location?.timeZone ?: TimeZone.getTimeZone('UTC'))
  errs << "${ts} - ${message}"
  atomicState?.dabHistoryErrors = errs
}

private boolean isFanActive(String opState = null) {
  // With thermostat: rely on operating/fan state
  if (settings?.thermostat1) {
    opState = opState ?: settings.thermostat1?.currentValue('thermostatOperatingState')
    if (opState == 'fan only') { return true }
    if (opState == 'idle') {
      def fanMode = settings.thermostat1?.currentValue('thermostatFanMode')
      return fanMode in ['on', 'circulate']
    }
    return false
  }
  // No thermostat: treat HVAC idle (no heat/cool) as "fan/idle" state
  // so that "Fan-only: open all vents" can still be honored.
  try {
    String mode = calculateHvacModeRobust()
    return (mode == null)
  } catch (ignore) { return false }
}

def finalizeRoomStates(data) {
  // Check for required parameters
  if (!data.ventIdsByRoomId || !data.startedCycle || !data.finishedRunning) {
    logWarn "Finalizing room states: missing required parameters (${data})"
    return
  }
  
  // Handle edge case when HVAC was already running during code deployment
  if (!data.startedRunning || !data.hvacMode) {
    log(2, 'App', "Skipping room state finalization - HVAC cycle started before code deployment")
    return
  }
  log(3, 'App', 'Start - Finalizing room states')
  def totalRunningMinutes = (data.finishedRunning - data.startedRunning) / (1000 * 60)
  def totalCycleMinutes = (data.finishedRunning - data.startedCycle) / (1000 * 60)
  log(3, 'App', "HVAC ran for ${totalRunningMinutes} minutes")

  atomicState.maxHvacRunningTime = roundBigDecimal(
      rollingAverage(atomicState.maxHvacRunningTime ?: totalRunningMinutes, totalRunningMinutes), 6)

  if (totalCycleMinutes >= Math.max(MIN_MINUTES_TO_SETPOINT, MIN_RUNTIME_FOR_RATE_CALC)) {
    // Track room rates that have been calculated
    Map<String, BigDecimal> roomRates = [:]
    Integer hour = new Date(data.startedCycle).format('H', location?.timeZone ?: TimeZone.getTimeZone('UTC')) as Integer

    data.ventIdsByRoomId.each { roomId, ventIds ->
      try {
      // Compute aggregated percent-open for the whole room (sum, capped to 100)
      int roomPercentOpen = 0
      try {
        ventIds.each { vid ->
          def v = getChildDevice(vid)
          if (!v) { return }
          def p = (v?.currentValue('percent-open') ?: v?.currentValue('level') ?: 0)
          if (settings?.useCachedRawForDab) {
            def samp = getLatestRawSample(vid)
            if (samp && samp.size() >= 9 && samp[6] != null) { p = (samp[6] as BigDecimal) }
          }
          try { roomPercentOpen += ((p ?: 0) as BigDecimal).intValue() } catch (ignore) { }
        }
        if (roomPercentOpen > 100) { roomPercentOpen = 100 }
      } catch (ignore) { roomPercentOpen = 0 }

      for (ventId in ventIds) {
        def vent = getChildDevice(ventId)
        if (!vent) {
          log(3, 'App', "Failed getting vent Id ${ventId}")
          continue
        }
        
        def roomName = vent.currentValue('room-name')
        def ratePropName = data.hvacMode == COOLING ? 'room-cooling-rate' : 'room-heating-rate'
        
        // Check if rate already calculated for this room
        if (roomRates.containsKey(roomName)) {
          // Use the already calculated rate for this room
          def rate = roomRates[roomName]
          sendEvent(vent, [name: ratePropName, value: rate])
          log(3, 'App', "Applying same ${ratePropName} (${roundBigDecimal(rate)}) to additional vent in '${roomName}'")
          continue
        }
        
        // Calculate rate for this room (first vent in room) using aggregated room opening
        def percentOpen = roomPercentOpen
        def tVal = getRoomTemp(vent)
        BigDecimal currentTemp = (tVal instanceof Number ? tVal : 0)
        BigDecimal lastStartTemp = vent.currentValue('room-starting-temperature-c') ?: 0
        BigDecimal currentRate = vent.currentValue(ratePropName) ?: 0
        def newRate = calculateRoomChangeRate(lastStartTemp, currentTemp, totalCycleMinutes, percentOpen, currentRate)
        
        if (newRate <= 0) {
          log(3, 'App', "New rate for ${roomName} is ${newRate}")
          
          // Check if room is already at or beyond setpoint
          def spRoom = vent.currentValue('room-set-point-c') ?: getGlobalSetpoint(data.hvacMode)
          def isAtSetpoint = hasRoomReachedSetpoint(data.hvacMode, spRoom, currentTemp)
          
          if (isAtSetpoint && currentRate > 0) {
            // Room is already at setpoint - maintain last known efficiency
            log(3, 'App', "${roomName} is already at setpoint, maintaining last known efficiency rate: ${currentRate}")
            newRate = currentRate  // Keep existing rate
          } else if (percentOpen > 0) {
            // Vent was open but no temperature change - use minimum rate
            newRate = MIN_TEMP_CHANGE_RATE
            log(3, 'App', "Setting minimum rate for ${roomName} - no temperature change detected with ${percentOpen}% room opening")
          } else if (currentRate == 0) {
            // Room has zero efficiency and vent was closed - set baseline efficiency
            def maxRate = data.hvacMode == COOLING ? 
                atomicState.maxCoolingRate ?: MAX_TEMP_CHANGE_RATE : 
                atomicState.maxHeatingRate ?: MAX_TEMP_CHANGE_RATE
            newRate = maxRate * 0.1  // 10% of maximum as baseline
            log(3, 'App', "Setting baseline efficiency for ${roomName} (10% of max rate: ${newRate})")
          } else {
            continue  // Skip if vent was closed and room has existing efficiency
          }
        }
        
        def rate = rollingAverage(currentRate, newRate, percentOpen / 100, 4)
        def cleanedRate = cleanDecimalForJson(rate)

        // Skip outlier rejection and EWMA until core behavior is validated
        BigDecimal persistedRate = cleanedRate
        sendEvent(vent, [name: ratePropName, value: cleanedRate])
        log(3, 'App', "Updating ${roomName}'s ${ratePropName} to ${roundBigDecimal(cleanedRate)}")

        // Store the calculated rate for this room
        roomRates[roomName] = cleanedRate
        // Record adaptive adjustment mark vs seeded rate
        try {
          def seeded = (atomicState?.lastSeededRate ?: [:])?.get(roomId)?.get(data.hvacMode)?.get(hour as Integer) as BigDecimal
          if (persistedRate != null && (persistedRate as BigDecimal) > 0) {
            BigDecimal base = (seeded != null && seeded > 0) ? seeded : (persistedRate as BigDecimal)
            BigDecimal ratio = ((persistedRate as BigDecimal) - base) / base
            appendAdaptiveMark(roomId, data.hvacMode, hour, ratio)
          }
        } catch (ignore) { }
        if (persistedRate != null) {
          appendHourlyRate(roomId, data.hvacMode, hour, persistedRate)
          appendDabHistory(roomId, data.hvacMode, hour, persistedRate)
        }
        
        // Track maximum rates for baseline calculations
        if (cleanedRate > 0) {
          if (data.hvacMode == COOLING) {
            def maxCoolRate = atomicState.maxCoolingRate ?: 0
            if (cleanedRate > maxCoolRate) {
              atomicState.maxCoolingRate = cleanDecimalForJson(cleanedRate)
              log(3, 'App', "Updated maximum cooling rate to ${cleanedRate}")
            }
          } else if (data.hvacMode == HEATING) {
            def maxHeatRate = atomicState.maxHeatingRate ?: 0
            if (cleanedRate > maxHeatRate) {
              atomicState.maxHeatingRate = cleanDecimalForJson(cleanedRate)
              log(3, 'App', "Updated maximum heating rate to ${cleanedRate}")
            }
          }
        }
      }
      } catch (Exception e) {
        logError("Error processing room ${roomId} in finalizeRoomStates: ${e?.message}", 'DAB', roomId)
      }
    }
  } else {
    log(3, 'App', "Could not calculate room states as it ran for ${totalCycleMinutes} minutes and needs to run for at least ${MIN_MINUTES_TO_SETPOINT} minutes")
  }
  log(3, 'App', 'End - Finalizing room states')
}

def recordStartingTemperatures() {
  if (!atomicState.ventsByRoomId) { return }
  log(2, 'App', "Recording starting temperatures for all rooms")
  atomicState.ventsByRoomId.each { roomId, ventIds ->
    for (ventId in ventIds) {
      try {
        def vent = getChildDevice(ventId)
        if (!vent) {
          continue
        }
        def tVal = getRoomTemp(vent)
        BigDecimal currentTemp = (tVal instanceof Number ? tVal : 0)
        sendEvent(vent, [name: 'room-starting-temperature-c', value: currentTemp])
        log(2, 'App', "Starting temperature for '${vent.currentValue('room-name')}': ${currentTemp}°C")
      } catch (err) {
        logError err
      }
    }
  }
}

def initializeRoomStates(String hvacMode) {
  if (!settings.dabEnabled) { return }
  log(3, 'App', "Initializing room states - hvac mode: ${hvacMode}")
  if (!atomicState.ventsByRoomId) { return }
  if (settings.fanOnlyOpenAllVents && isFanActive()) {
    log(2, 'App', 'Fan-only mode active - skipping DAB initialization')
    return
  }

  Integer currentHour = new Date().format('H', location?.timeZone ?: TimeZone.getTimeZone('UTC')) as Integer
  atomicState.ventsByRoomId.each { roomId, ventIds ->
    def avgRate = getAverageHourlyRate(roomId, hvacMode, currentHour)
    ventIds.each { ventId ->
      def vent = getChildDevice(ventId)
      if (vent) {
        def attr = hvacMode == COOLING ? 'room-cooling-rate' : 'room-heating-rate'
        sendEvent(vent, [name: attr, value: avgRate])
      }
    }
    // Record seeded rate for adaptive analysis (once per room)
    try {
      def seeded = atomicState?.lastSeededRate ?: [:]
      def roomMap = seeded[roomId] ?: [:]
      def modeMap = roomMap[hvacMode] ?: [:]
      modeMap[currentHour as Integer] = (avgRate as BigDecimal)
      roomMap[hvacMode] = modeMap
      seeded[roomId] = roomMap
      atomicState.lastSeededRate = seeded
      atomicState.seededHour = currentHour
    } catch (ignore) { }
  }

  BigDecimal setpoint = getGlobalSetpoint(hvacMode)
  if (!setpoint) { return }
  atomicStateUpdate('thermostat1State', 'startedCycle', now())
  def rateAndTempPerVentId = getAttribsPerVentIdWeighted(atomicState.ventsByRoomId, hvacMode)
  
  def maxRunningTime = atomicState.maxHvacRunningTime ?: MAX_MINUTES_TO_SETPOINT
  def longestTimeToTarget = calculateLongestMinutesToTarget(rateAndTempPerVentId, hvacMode, setpoint, maxRunningTime, settings.thermostat1CloseInactiveRooms)
  if (longestTimeToTarget < 0) {
    log(3, 'App', "All vents already reached setpoint (${setpoint})")
    longestTimeToTarget = maxRunningTime
  }
  if (longestTimeToTarget == 0) {
    log(3, 'App', "Opening all vents (setpoint: ${setpoint})")
    openAllVents(atomicState.ventsByRoomId, MAX_PERCENTAGE_OPEN as int)
    return
  }
  log(3, 'App', "Initializing room states - setpoint: ${setpoint}, longestTimeToTarget: ${roundBigDecimal(longestTimeToTarget)}")

  def calcPercentOpen = calculateOpenPercentageForAllVents(rateAndTempPerVentId, hvacMode, setpoint, longestTimeToTarget, settings.thermostat1CloseInactiveRooms)
  // Monotonic ordering removed by request; rely on learned efficiency
  if (!calcPercentOpen) {
    log(3, 'App', "No vents are being changed (setpoint: ${setpoint})")
    return
  }

  calcPercentOpen = adjustVentOpeningsToEnsureMinimumAirflowTarget(rateAndTempPerVentId, hvacMode, calcPercentOpen, settings.thermostat1AdditionalStandardVents)

  // Apply manual overrides and floors before patching vents
  calcPercentOpen = applyOverridesAndFloors(calcPercentOpen)

  def __changeSummary = []
  int __changeCount = 0
  calcPercentOpen.each { ventId, percentOpen ->
    def vent = getChildDevice(ventId)
    if (vent) {
      try {
        def rateAttr = hvacMode == COOLING ? 'room-cooling-rate' : 'room-heating-rate'
        def tVal = getRoomTemp(vent)
        BigDecimal vTemp = (tVal instanceof Number ? tVal : 0)
        boolean atSp = hasRoomReachedSetpoint(hvacMode, setpoint, vTemp, REBALANCING_TOLERANCE)
        boolean overridden = (atomicState?.manualOverrides ?: [:]).containsKey(ventId)
        Integer finalPct = roundToNearestMultiple(percentOpen)
        logVentDecision([
          stage: 'final', hvacMode: hvacMode, ventId: ventId,
          room: (vent.currentValue('room-name') ?: vent.getLabel()),
          temp: vTemp, setpoint: setpoint,
          rate: (vent.currentValue(rateAttr) ?: 0),
          atSetpoint: atSp, override: overridden, percent: finalPct
        ])
      } catch (ignore) { }
      Integer __current = (vent.currentValue('percent-open') ?: vent.currentValue('level') ?: 0) as int
      Integer __target = roundToNearestMultiple(percentOpen)
      
      // Enhanced DAB: Apply change dampening and set vent attributes
      try {
        def rawTargetPercent = percentOpen as BigDecimal
        def dampedTargetPercent = applyChangeDampening(ventId, rawTargetPercent, hvacMode)
        __target = roundToNearestMultiple(dampedTargetPercent)
        
        // Set enhanced vent attributes for debugging/diagnostics
        def rateAttr = hvacMode == COOLING ? 'room-cooling-rate' : 'room-heating-rate'
        def rate = vent.currentValue(rateAttr) ?: 0
        def anomalyFactor = getAnomalyFactorForVent(ventId, hvacMode)
        def carryForward = getCarryForwardFlagForVent(ventId, hvacMode)
        
        vent.sendEvent(name: 'dab-raw-target-percent', value: rawTargetPercent.round(1))
        vent.sendEvent(name: 'dab-target-percent', value: dampedTargetPercent.round(1))
        vent.sendEvent(name: 'dab-hourly-rate', value: rate)
        vent.sendEvent(name: 'dab-anomaly-factor', value: anomalyFactor)
        vent.sendEvent(name: 'dab-carry-forward', value: carryForward)
        
      } catch (Exception e) {
        log(2, 'DAB', "Error setting enhanced vent attributes for ${vent?.currentValue('room-name')}: ${e.message}")
      }
      
      if (__current != __target) {
        __changeCount++
        def __rn = vent.currentValue('room-name') ?: vent.getLabel()
        __changeSummary << "${__rn}: ${__current}%->${__target}%"
      }
      patchVent(vent, __target)
    }
  }
  if (__changeCount > 0) {
    appendDabActivityLog("Applied ${__changeCount} vent change(s): ${__changeSummary.take(3).join(', ')}")
  }
}

// Enforce monotonic relationship between temperature delta and vent opening
// - Cooling: larger (temp - setpoint) => opening must be >= openings of rooms with smaller delta
// - Heating: larger (setpoint - temp) => opening must be >= openings of rooms with smaller delta
// enforceMonotonicVentOpenings removed by request

// Enforce global floor and per-vent manual overrides
private Map applyOverridesAndFloors(Map calc) {
  def result = [:]
  int floorPct = 0
  try {
    floorPct = (settings?.allowFullClose ? 0 : ((settings?.minVentFloorPercent ?: 0) as int))
  } catch (ignore) { floorPct = 0 }

  // Build manual override map ventId -> percent
  def overrides = atomicState?.manualOverrides ?: [:]

  calc.each { ventId, pct ->
    def out = overrides.containsKey(ventId) ? (overrides[ventId] as int) : (pct as int)
    if (out < floorPct) { out = floorPct }
    result[ventId] = out
  }
  return result
}

def adjustVentOpeningsToEnsureMinimumAirflowTarget(rateAndTempPerVentId, String hvacMode, Map calculatedPercentOpen, additionalStandardVents) {
  int totalDeviceCount = additionalStandardVents > 0 ? additionalStandardVents : 0
  def sumPercentages = totalDeviceCount * STANDARD_VENT_DEFAULT_OPEN
  calculatedPercentOpen.each { ventId, percent ->
    totalDeviceCount++
    sumPercentages += percent ?: 0
  }
  if (totalDeviceCount <= 0) {
    logWarn 'Total device count is zero'
    return calculatedPercentOpen
  }

  BigDecimal maxTemp = null
  BigDecimal minTemp = null
  rateAndTempPerVentId.each { ventId, stateVal ->
    maxTemp = maxTemp == null || maxTemp < stateVal.temp ? stateVal.temp : maxTemp
    minTemp = minTemp == null || minTemp > stateVal.temp ? stateVal.temp : minTemp
  }
  if (minTemp == null || maxTemp == null) {
    minTemp = 20.0
    maxTemp = 25.0
  } else {
    minTemp = minTemp - TEMP_BOUNDARY_ADJUSTMENT
    maxTemp = maxTemp + TEMP_BOUNDARY_ADJUSTMENT
  }

  def combinedFlowPercentage = (100 * sumPercentages) / (totalDeviceCount * 100)
  if (combinedFlowPercentage >= MIN_COMBINED_VENT_FLOW) {
    log(3, 'App', "Combined vent flow percentage (${combinedFlowPercentage}%) is greater than ${MIN_COMBINED_VENT_FLOW}%")
    return calculatedPercentOpen
  }
  log(3, 'App', "Combined Vent Flow Percentage (${combinedFlowPercentage}) is lower than ${MIN_COMBINED_VENT_FLOW}%")
  def targetPercentSum = MIN_COMBINED_VENT_FLOW * totalDeviceCount
  def diffPercentageSum = targetPercentSum - sumPercentages
  log(2, 'App', "sumPercentages=${sumPercentages}, targetPercentSum=${targetPercentSum}, diffPercentageSum=${diffPercentageSum}")
  int iterations = 0
  while (diffPercentageSum > 0 && iterations++ < MAX_ITERATIONS) {
    for (item in rateAndTempPerVentId) {
      def ventId = item.key
      def stateVal = item.value
      BigDecimal percentOpenVal = calculatedPercentOpen[ventId] ?: 0
      if (percentOpenVal >= MAX_PERCENTAGE_OPEN) {
        percentOpenVal = MAX_PERCENTAGE_OPEN
        calculatedPercentOpen[ventId] = MAX_PERCENTAGE_OPEN
      } else {
        def proportion = hvacMode == COOLING ?
          (stateVal.temp - minTemp) / (maxTemp - minTemp) :
          (maxTemp - stateVal.temp) / (maxTemp - minTemp)
        def increment = INCREMENT_PERCENTAGE * proportion
        percentOpenVal = percentOpenVal + increment
        if (percentOpenVal >= MAX_PERCENTAGE_OPEN) {
          percentOpenVal = MAX_PERCENTAGE_OPEN
        }
        calculatedPercentOpen[ventId] = percentOpenVal
        log(2, 'App', "Adjusting % open from ${roundBigDecimal(percentOpenVal - increment)}% to ${roundBigDecimal(percentOpenVal)}%")
        diffPercentageSum = diffPercentageSum - increment
        if (diffPercentageSum <= 0) { break }
      }
    }
  }
  return calculatedPercentOpen
}

private void runDynamicAirflowBalancing() {
  if (!settings?.dabEnabled) { return }
  String hvacMode = atomicState.thermostat1State?.mode
  if (!hvacMode) { return }
  def ventData = collectVentData(hvacMode)
  if (!ventData) { return }
  def targets = calculateVentTargets(ventData, hvacMode)
  applyVentTargets(targets, hvacMode)
  checkMissingDiagnostics()
}

private Map collectVentData(String hvacMode) {
  def data = [:]
  def ventsByRoomId = atomicState?.ventsByRoomId ?: [:]
  ventsByRoomId.each { roomId, ventIds ->
    ventIds.each { vid ->
      def vent = getChildDevice(vid)
      if (!vent) { return }
      def tempVal = getRoomTemp(vent)
      def record = [
        name: vent.currentValue('room-name') ?: vent.getLabel(),
        temp: (tempVal instanceof Number ? tempVal : 0),
        rate: (hvacMode == COOLING ? vent.currentValue('room-cooling-rate') : vent.currentValue('room-heating-rate')) ?: 0,
        active: (vent.currentValue('room-active') ?: 'false') == 'true',
        setpoint: vent.currentValue('room-set-point-c') ?: '-'
      ]
      if (tempVal == '-' || vent.currentValue('room-set-point-c') == null) {
        logWarn "Room data unavailable for '${record.name}'. Check network or thermostat connectivity."
      }
      data[vid] = record
      logDabDiagnostics(record.name, record)
    }
  }
  return data
}

private Map calculateVentTargets(Map ventData, String hvacMode) {
  BigDecimal setpoint = getGlobalSetpoint(hvacMode)
  if (setpoint == null) { return [:] }
  def maxRunningTime = atomicState.maxHvacRunningTime ?: MAX_MINUTES_TO_SETPOINT
  def longest = calculateLongestMinutesToTarget(ventData, hvacMode, setpoint, maxRunningTime, settings.thermostat1CloseInactiveRooms)
  if (longest < 0) { longest = maxRunningTime }
  def targets = calculateOpenPercentageForAllVents(ventData, hvacMode, setpoint, longest, settings.thermostat1CloseInactiveRooms)
  if (!targets) { return [:] }
  targets = adjustVentOpeningsToEnsureMinimumAirflowTarget(ventData, hvacMode, targets, settings.thermostat1AdditionalStandardVents)
  targets = applyOverridesAndFloors(targets)
  return targets
}

private void applyVentTargets(Map targets, String hvacMode) {
  if (!targets) { return }
  int changes = 0
  def summary = []
  targets.each { ventId, pct ->
    def vent = getChildDevice(ventId)
    if (!vent) { return }
    Integer current = (vent.currentValue('percent-open') ?: vent.currentValue('level') ?: 0) as int
    Integer target = roundToNearestMultiple(pct)
    if (current != target) {
      changes++
      summary << "${vent.currentValue('room-name') ?: vent.getLabel()}: ${current}%->${target}%"
    }
    patchVentWithVerification(vent, target)
  }
  if (changes > 0) {
    appendDabActivityLog("Applied ${changes} vent change(s): ${summary.take(3).join(', ')}")
  }
}

private void logDabDiagnostics(String roomId, Map data) {
  def diag = atomicState.dabDiagnostics ?: [:]
  diag[roomId] = data
  atomicState.dabDiagnostics = diag
  log(2, 'DAB', "Diagnostics for ${roomId}: ${data}")
}

private void checkMissingDiagnostics() {
  def diag = atomicState.dabDiagnostics ?: [:]
  if (!diag) { return }
  def allKeys = diag.values().collectMany { it.keySet() }.unique()
  diag.each { room, info ->
    def missing = allKeys - info.keySet()
    if (missing) {
      log(2, 'DAB', "Room ${room} missing fields: ${missing.join(', ')}")
    }
  }
}

def getAttribsPerVentId(ventsByRoomId, String hvacMode) {
  def rateAndTemp = [:]
  ventsByRoomId.each { roomId, ventIds ->
    for (ventId in ventIds) {
      try {
        def vent = getChildDevice(ventId)
        if (!vent) { continue }
        def rate = hvacMode == COOLING ? (vent.currentValue('room-cooling-rate') ?: 0) : (vent.currentValue('room-heating-rate') ?: 0)
        rate = rate ?: 0
        def isActive = (vent.currentValue('room-active') ?: 'false') == 'true'
        def tVal = getRoomTemp(vent)
        def roomTemp = (tVal instanceof Number ? tVal : 0)
        def roomName = vent.currentValue('room-name') ?: ''
        
        // Log rooms with zero efficiency for debugging
        if (rate == 0) {
          def tempSource = settings."vent${ventId}Thermostat" ? "Puck ${settings."vent${ventId}Thermostat".getLabel()}" : "Room API"
          log(2, 'App', "Room '${roomName}' has zero ${hvacMode} efficiency rate, temp=${roomTemp}°C from ${tempSource}")
        }
        
        rateAndTemp[ventId] = [ rate: rate, temp: roomTemp, active: isActive, name: roomName ]
      } catch (err) {
        logError err
      }
    }
  }
  return rateAndTemp
}

// Weighted variant that applies optional per-vent weights to bias openings in multi-vent rooms
def getAttribsPerVentIdWeighted(ventsByRoomId, String hvacMode) {
  def rateAndTemp = [:]
  ventsByRoomId.each { roomId, ventIds ->
    for (ventId in ventIds) {
      try {
        def vent = getChildDevice(ventId)
        if (!vent) { continue }
        def baseRate = hvacMode == COOLING ? (vent.currentValue('room-cooling-rate') ?: 0) : (vent.currentValue('room-heating-rate') ?: 0)
        baseRate = baseRate ?: 0
        def isActive = (vent.currentValue('room-active') ?: 'false') == 'true'
        def tVal2 = getRoomTemp(vent)
        def roomTemp = (tVal2 instanceof Number ? tVal2 : 0)
        def roomName = vent.currentValue('room-name') ?: ''
        BigDecimal userWeight = 1.0
        try {
          def w = settings?."vent${ventId}Weight"
          if (w != null) {
            userWeight = (w as BigDecimal)
            if (userWeight <= 0) { userWeight = 1.0 }
          }
        } catch (ignore) { userWeight = 1.0 }
        
        // Enhanced: Use inverse weight interpretation for rate processing
        def effectiveRate = baseRate
        BigDecimal inverseWeight = 1.0 / Math.max(effectiveRate as Double, EPS_RATE as Double)
        
        try { 
          // Apply user weighting to the inverse weight (not the rate directly)
          inverseWeight = inverseWeight * userWeight
          effectiveRate = 1.0 / inverseWeight  // Convert back to effective rate for compatibility
        } catch (ignore) { effectiveRate = baseRate }
        
        if ((effectiveRate ?: 0) == 0) {
          def tempSource = settings."vent${ventId}Thermostat" ? "Puck ${settings."vent${ventId}Thermostat".getLabel()}" : "Room API"
          log(2, 'App', "Room '${roomName}' has zero ${hvacMode} efficiency rate, temp=${roomTemp}C from ${tempSource}")
        }
        
        // Store both rate and inverse weight for enhanced calculations
        rateAndTemp[ventId] = [ 
          rate: effectiveRate, 
          inverseWeight: inverseWeight,
          temp: roomTemp, 
          active: isActive, 
          name: roomName 
        ]
        
      } catch (err) {
        logError err
      }
    }
  }
  return rateAndTemp
}

// Apply change dampening (soft limiting) to prevent large changes per cycle
BigDecimal applyChangeDampening(String ventId, BigDecimal rawTargetPercent, String hvacMode) {
  try {
    def vent = getChildDevice(ventId)
    if (!vent) {
      return rawTargetPercent
    }
    
    def currentPercent = (vent.currentValue('percent-open') ?: 0) as BigDecimal
    def roomName = vent.currentValue('room-name') ?: 'Unknown'
    def delta = rawTargetPercent - currentPercent
    def percentChange = currentPercent > 0 ? Math.abs(delta / currentPercent) * 100.0 : 0.0
    
    // Check if we should skip dampening
    def spRoom = vent.currentValue('room-set-point-c') ?: getGlobalSetpoint(hvacMode)
    def tVal = getRoomTemp(vent)
    def roomTemp = (tVal instanceof Number ? tVal : 0)
    def isAtSetpoint = hasRoomReachedSetpoint(hvacMode, spRoom, roomTemp)
    def hasAnomalyFlag = getVentAnomalyFlag(ventId)
    
    if (!isAtSetpoint || hasAnomalyFlag) {
      // Skip dampening if setpoint not reached or anomaly detected
      log(4, 'DAB', "Skipping change dampening for ${roomName}: setpoint=${!isAtSetpoint} anomaly=${hasAnomalyFlag}")
      return rawTargetPercent
    }
    
    if (percentChange > MAX_PERCENT_CHANGE_PER_CYCLE) {
      // Apply soft limiting
      BigDecimal maxDelta = currentPercent * (MAX_PERCENT_CHANGE_PER_CYCLE / 100.0)
      BigDecimal dampedTarget = delta > 0 ? 
        currentPercent + maxDelta : 
        currentPercent - maxDelta
        
      log(3, 'DAB', "Change dampening for ${roomName}: raw=${rawTargetPercent}% -> damped=${dampedTarget}% (limited ${percentChange.round(1)}% change to ${MAX_PERCENT_CHANGE_PER_CYCLE}%)")
      return dampedTarget
    }
    
    return rawTargetPercent
  } catch (Exception e) {
    log(2, 'DAB', "Error applying change dampening: ${e.message}")
    return rawTargetPercent
  }
}

// Helper to check if vent has an anomaly factor below 1.0
def getVentAnomalyFlag(String ventId) {
  try {
    def vent = getChildDevice(ventId)
    def f = vent?.currentValue('dab-anomaly-factor')
    if (f == null) return false
    try { return (f as BigDecimal) < 1.0G } catch (ignore) { return false }
  } catch (ignore) {
    return false
  }
}

// Get anomaly factor for a specific vent using room ID
def getAnomalyFactorForVent(String ventId, String hvacMode) {
  try {
    def vent = getChildDevice(ventId)
    if (!vent) return 1.0
    def roomId = vent.currentValue('room-id')?.toString()
    Integer hr = new Date().format('H', location?.timeZone ?: TimeZone.getTimeZone('UTC')) as Integer
    return roomId ? getAnomalyFactor(roomId, hr) : 1.0
  } catch (Exception e) {
    log(2, 'DAB', "Error getting anomaly factor for vent: ${e.message}")
    return 1.0
  }
}

// Get carry-forward flag for a specific vent
def getCarryForwardFlagForVent(String ventId, String hvacMode) {
  try {
    def vent = getChildDevice(ventId)
    if (!vent) return false
    
    def roomName = vent.currentValue('room-name') ?: 'Unknown'
    Integer currentHour = new Date().format('H', location?.timeZone ?: TimeZone.getTimeZone('UTC')) as Integer
    
    def metadata = atomicState.dabMetadata ?: [:]
    def key = "${roomName}:${hvacMode}:${currentHour}"
    return metadata[key]?.carryForwardUsed ?: false
  } catch (Exception e) {
    log(2, 'DAB', "Error getting carry-forward flag for vent: ${e.message}")
    return false
  }
}

def calculateOpenPercentageForAllVents(rateAndTempPerVentId, String hvacMode, BigDecimal setpoint, longestTime, boolean closeInactive = true) {
  def percentOpenMap = [:]
  rateAndTempPerVentId.each { ventId, stateVal ->
    try {
      def percentageOpen = MIN_PERCENTAGE_OPEN
      def vdev = getChildDevice(ventId)
      BigDecimal absOvr = (atomicState?.roomTargetOverridesC?.get(vdev?.getDeviceNetworkId())) as BigDecimal; BigDecimal biasC = (atomicState?.roomTargetBiasC?.get(vdev?.getDeviceNetworkId())) as BigDecimal; BigDecimal baseSp = (vdev?.currentValue('room-set-point-c') ?: setpoint) as BigDecimal; BigDecimal spForVent = (absOvr != null ? absOvr : (biasC != null ? (baseSp + biasC) : baseSp))
      boolean atSetpoint = hasRoomReachedSetpoint(hvacMode, spForVent, stateVal.temp, REBALANCING_TOLERANCE)

      if (closeInactive && !stateVal.active) {
        log(3, 'App', "Closing vent on inactive room: ${stateVal.name}")
      } else if (atSetpoint) {
        // Room is already on the comfortable side of the setpoint - keep at floor
        percentageOpen = MIN_PERCENTAGE_OPEN
      } else if (stateVal.rate < MIN_TEMP_CHANGE_RATE) {
        // Unknown or too-low learning rate: open fully unless already at/below setpoint
        percentageOpen = MAX_PERCENTAGE_OPEN
        log(3, 'App', "Opening vent at max since change rate is too low: ${stateVal.name}")
      } else {
        percentageOpen = calculateVentOpenPercentage(stateVal.name, stateVal.temp, spForVent, hvacMode, stateVal.rate, longestTime)
      }
      // Trace raw decision before overrides/rounding
      logVentDecision([
        stage: 'raw', hvacMode: hvacMode, ventId: ventId,
        room: stateVal.name, temp: stateVal.temp, setpoint: setpoint,
        rate: stateVal.rate, atSetpoint: atSetpoint, percent: percentageOpen
      ])
      percentOpenMap[ventId] = percentageOpen
    } catch (err) {
      logError err
    }
  }
  return percentOpenMap
}

// Append a compact decision entry for diagnostics (keeps last 60)
private void logVentDecision(Map entry) {
  try {
    def list = state.recentVentDecisions ?: []
    Map e = [
      ts: new Date().format('HH:mm:ss', location?.timeZone ?: TimeZone.getTimeZone('UTC')),
      stage: entry.stage ?: 'raw',
      mode: entry.hvacMode ?: '',
      room: (entry.room ?: ''),
      temp: (entry.temp ?: ''),
      setpoint: (entry.setpoint ?: ''),
      rate: (entry.rate ?: ''),
      atSp: (entry.atSetpoint ?: false),
      override: (entry.override ?: false),
      pct: (entry.percent ?: '')
    ]
    list << e
    if (list.size() > 60) { list = list[-60..-1] }
    state.recentVentDecisions = list
  } catch (ignore) { }
}

def calculateVentOpenPercentage(String roomName, BigDecimal startTemp, BigDecimal setpoint, String hvacMode, BigDecimal maxRate, BigDecimal longestTime) {
  if (hasRoomReachedSetpoint(hvacMode, setpoint, startTemp)) {
    def msg = hvacMode == COOLING ? 'cooler' : 'warmer'
    log(3, 'App', "'${roomName}' is already ${msg} (${startTemp}) than setpoint (${setpoint})")
    return MIN_PERCENTAGE_OPEN
  }
  BigDecimal percentageOpen = MAX_PERCENTAGE_OPEN
  if (maxRate > 0 && longestTime > 0) {
    BigDecimal BASE_CONST = 0.0991
    BigDecimal EXP_CONST = 2.3

    // Calculate the target rate: the average temperature change required per minute.
    def targetRate = Math.abs(setpoint - startTemp) / longestTime
    percentageOpen = BASE_CONST * Math.exp((targetRate / maxRate) * EXP_CONST)
    percentageOpen = roundBigDecimal(percentageOpen * 100, 3)

    // Ensure percentageOpen stays within defined limits.
    percentageOpen = percentageOpen < MIN_PERCENTAGE_OPEN ? MIN_PERCENTAGE_OPEN :
                           (percentageOpen > MAX_PERCENTAGE_OPEN ? MAX_PERCENTAGE_OPEN : percentageOpen)
    log(3, 'App', "changing percentage open for ${roomName} to ${percentageOpen}% (maxRate=${roundBigDecimal(maxRate)})")
  }
  return percentageOpen
}


def calculateLongestMinutesToTarget(rateAndTempPerVentId, String hvacMode, BigDecimal setpoint, maxRunningTime, boolean closeInactive = true) {
  def longestTime = -1
  rateAndTempPerVentId.each { ventId, stateVal ->
    try {
      def minutesToTarget = -1
      if (closeInactive && !stateVal.active) {
        log(3, 'App', "'${stateVal.name}' is inactive")
      } else if (hasRoomReachedSetpoint(hvacMode, (getChildDevice(ventId)?.currentValue('room-set-point-c') ?: setpoint) as BigDecimal, stateVal.temp)) {
        log(3, 'App', "'${stateVal.name}' has already reached setpoint")
      } else if (stateVal.rate > 0) {
        BigDecimal spForVent = (getChildDevice(ventId)?.currentValue('room-set-point-c') ?: setpoint) as BigDecimal
        minutesToTarget = Math.abs(spForVent - stateVal.temp) / stateVal.rate
        // Check for unrealistic time estimates due to minimal temperature change
        if (minutesToTarget > maxRunningTime * 2) {
          logWarn "'${stateVal.name}' shows minimal temperature change (rate: ${roundBigDecimal(stateVal.rate)}°C/min). " +
                  "Estimated time ${roundBigDecimal(minutesToTarget)} minutes is unrealistic."
          minutesToTarget = maxRunningTime  // Cap at max running time
        }
      } else if (stateVal.rate == 0) {
        minutesToTarget = maxRunningTime
        logWarn "'${stateVal.name}' shows no temperature change with vent open"
      }
      if (minutesToTarget > maxRunningTime) {
        logWarn "'${stateVal.name}' is estimated to take ${roundBigDecimal(minutesToTarget)} minutes to reach target temp, which is longer than the average ${roundBigDecimal(maxRunningTime)} minutes"
        minutesToTarget = maxRunningTime
      }
      longestTime = Math.max(longestTime, minutesToTarget.doubleValue())
      log(3, 'App', "Room '${stateVal.name}' temp: ${stateVal.temp}")
    } catch (err) {
      logError err
    }
  }
  return longestTime
}

// Overloaded method for backward compatibility with tests
def calculateRoomChangeRate(def lastStartTemp, def currentTemp, def totalMinutes, def percentOpen, def currentRate) {
  // Null safety checks
  if (lastStartTemp == null || currentTemp == null || totalMinutes == null || percentOpen == null || currentRate == null) {
    log(3, 'App', "calculateRoomChangeRate: null parameter detected")
    return -1
  }
  
  try {
    return calculateRoomChangeRate(
      lastStartTemp as BigDecimal, 
      currentTemp as BigDecimal, 
      totalMinutes as BigDecimal, 
      percentOpen as int, 
      currentRate as BigDecimal
    )
  } catch (Exception e) {
    log(3, 'App', "calculateRoomChangeRate casting error: ${e.message}")
    return -1
  }
}

def calculateRoomChangeRate(BigDecimal lastStartTemp, BigDecimal currentTemp, BigDecimal totalMinutes, int percentOpen, BigDecimal currentRate) {
  if (totalMinutes < MIN_MINUTES_TO_SETPOINT) {
    log(3, 'App', "Insufficient number of minutes required to calculate change rate (${totalMinutes} should be greater than ${MIN_MINUTES_TO_SETPOINT})")
    return -1
  }
  
  // Skip rate calculation if HVAC hasn't run long enough for meaningful temperature changes
  if (totalMinutes < MIN_RUNTIME_FOR_RATE_CALC) {
    log(3, 'App', "HVAC runtime too short for rate calculation: ${totalMinutes} minutes < ${MIN_RUNTIME_FOR_RATE_CALC} minutes minimum")
    return -1
  }
  
  if (percentOpen <= MIN_PERCENTAGE_OPEN) {
    log(3, 'App', "Vent was opened less than ${MIN_PERCENTAGE_OPEN}% (${percentOpen}), therefore it is being excluded")
    return -1
  }
  
  BigDecimal diffTemps = Math.abs(lastStartTemp - currentTemp)
  
  // Check if temperature change is within sensor noise/accuracy range
  if (diffTemps < MIN_DETECTABLE_TEMP_CHANGE) {
    log(2, 'App', "Temperature change (${diffTemps}°C) is below minimum detectable threshold (${MIN_DETECTABLE_TEMP_CHANGE}°C) - likely sensor noise")
    
    // If no meaningful temperature change but vent was significantly open, assign minimum efficiency
    if (percentOpen >= 30) {
      log(2, 'App', "Vent was ${percentOpen}% open but no meaningful temperature change detected - assigning minimum efficiency")
      return MIN_TEMP_CHANGE_RATE
    }
    return -1
  }
  
  // Account for sensor accuracy when detecting minimal changes
  if (diffTemps < TEMP_SENSOR_ACCURACY) {
    log(2, 'App', "Temperature change (${diffTemps}°C) is within sensor accuracy range (±${TEMP_SENSOR_ACCURACY}°C) - adjusting calculation")
    // Use a minimum reliable change for calculation to avoid division by near-zero
    diffTemps = Math.max(diffTemps, MIN_DETECTABLE_TEMP_CHANGE)
  }
  
  BigDecimal rate = diffTemps / totalMinutes
  BigDecimal pOpen = percentOpen / 100
  BigDecimal maxRate = rate.max(currentRate)
  BigDecimal approxRate = maxRate != 0 ? (rate / maxRate) / pOpen : 0
  if (approxRate > MAX_TEMP_CHANGE_RATE) {
    log(3, 'App', "Change rate (${roundBigDecimal(approxRate)}) is greater than ${MAX_TEMP_CHANGE_RATE}, therefore it is being excluded")
    return -1
  } else if (approxRate < MIN_TEMP_CHANGE_RATE) {
    log(3, 'App', "Change rate (${roundBigDecimal(approxRate)}) is lower than ${MIN_TEMP_CHANGE_RATE}, adjusting to minimum (startTemp=${lastStartTemp}, currentTemp=${currentTemp}, percentOpen=${percentOpen}%)")
    // Return minimum rate instead of excluding to prevent zero efficiency
    return MIN_TEMP_CHANGE_RATE
  }
  return approxRate
}

// ------------------------------
// Dynamic Polling Control
// ------------------------------

def updateDevicePollingInterval(Integer intervalMinutes) {
  log(3, 'App', "Updating device polling interval to ${intervalMinutes} minutes")
  
  // Update all child vents
  getChildDevices()?.findAll { it.typeName == 'Flair vents' }?.each { device ->
    try {
      device.updateParentPollingInterval(intervalMinutes)
    } catch (Exception e) {
      log(2, 'App', "Error updating polling interval for vent ${device.getLabel()}: ${e.message}")
    }
  }
  
  // Update all child pucks  
  getChildDevices()?.findAll { it.typeName == 'Flair pucks' }?.each { device ->
    try {
      device.updateParentPollingInterval(intervalMinutes)
    } catch (Exception e) {
      log(2, 'App', "Error updating polling interval for puck ${device.getLabel()}: ${e.message}")
    }
  }
  
  atomicState.currentPollingInterval = intervalMinutes
  log(3, 'App', "Updated polling interval for ${getChildDevices()?.size() ?: 0} devices")
}

// ------------------------------
// Efficiency Data Export/Import Functions
// ------------------------------

def handleExportEfficiencyData() {
  try {
    log(2, 'App', "Starting efficiency data export")
    
    // Collect efficiency data from all vents
    def efficiencyData = exportEfficiencyData()
    
    // Generate JSON format
    def jsonData = generateEfficiencyJSON(efficiencyData)
    
    // Set export status message
    def roomCount = efficiencyData.roomEfficiencies.size()
    state.exportStatus = "Exported efficiency data for ${roomCount} rooms. Copy the JSON data below:"
    
    // Store the JSON data for display
    state.exportedJsonData = jsonData
    
    log(2, 'App', "Export completed successfully for ${roomCount} rooms")
    
  } catch (Exception e) {
    def errorMsg = "Export failed: ${e.message}"
    logError errorMsg
    state.exportStatus = "${errorMsg}"
    state.exportedJsonData = null
  }
}

def handleExportDabHistory() {
  try {
    log "Starting DAB history export", 2
    def format = settings?.dabHistoryFormat ?: 'json'
    def data = exportDabHistory(format)
    if (data) {
      state.dabHistoryExportStatus = "\u2713 DAB history exported as ${format.toUpperCase()}. Copy the data below:"
      state.dabHistoryExportData = data
      log "DAB history export successful", 2
    } else {
      state.dabHistoryExportStatus = '\u2717 No DAB history available.'
      state.dabHistoryExportData = null
      log "No DAB history to export", 2
    }
  } catch (Exception e) {
    def errorMsg = "Export failed: ${e.message}"
    logError errorMsg
    state.dabHistoryExportStatus = "\u2717 ${errorMsg}"
    state.dabHistoryExportData = null
  }
}

// Clear all DAB learned data and history (all rooms)
def handleClearDabData() {
  try {
    def vents = getChildDevices()?.findAll { it.hasAttribute('percent-open') } ?: []
    // Reset per-room rates
    vents.each { v ->
      try { sendEvent(v, [name: 'room-cooling-rate', value: 0.0]) } catch (ignore) { }
      try { sendEvent(v, [name: 'room-heating-rate', value: 0.0]) } catch (ignore) { }
      try { sendEvent(v, [name: 'room-starting-temperature-c', value: null]) } catch (ignore) { }
    }
    // Reset history structures
    atomicState.dabHistory = [entries: [], hourlyRates: [:]]
    atomicState.dabActivityLog = []
    atomicState.remove('dabHistoryArchive')
    atomicState.remove('dabHistoryErrors')
    atomicState.remove('dabDailyStats')
    atomicState.remove('dabHistoryStartTimestamp')
    atomicState.remove('lastHvacMode')
    try { atomicState.maxCoolingRate = 0.0 } catch (ignore) { }
    try { atomicState.maxHeatingRate = 0.0 } catch (ignore) { }
    // Clear any cached export status
    state.remove('dabHistoryExportStatus')
    state.remove('dabHistoryExportData')
    state.clearDabStatus = "\u2713 Cleared all DAB data and room rates."
  } catch (e) {
    state.clearDabStatus = "\u2717 Clear failed: ${e?.message}"
  }
}

def handleImportEfficiencyData() {
  try {
    log(2, 'App', "Starting efficiency data import")
    
    // Clear previous status
    state.remove('importStatus')
    state.remove('importSuccess')
    
    // Get JSON data from user input (coerce to String for CI wrappers)
    def raw = null
    try { raw = settings?.importJsonData } catch (ignore) { }
    String jsonText = raw instanceof CharSequence ? raw.toString() : "${raw ?: ''}"
    // Hubitat CI may return an UnvalidatedInput wrapper. Fallback to userSettingsMap when detected.
    if (!(jsonText?.trim()?.startsWith('{') || jsonText?.trim()?.startsWith('['))) {
      try {
        def usm = this.hasProperty('userSettingsMap') ? this.userSettingsMap : null
        def candidate = usm?.settings?.importJsonData
        if (candidate) { jsonText = "${candidate}" }
      } catch (ignore) { }
    }
    if (!jsonText.trim()) {
      state.importStatus = "No JSON data provided. Please paste the exported efficiency data."
      state.importSuccess = false
      return
    }
    
    // Import the data
    def result = importEfficiencyData(jsonText.trim())
    
    if (result.success) {
      def statusMsg = "Import successful! Updated ${result.roomsUpdated} rooms"
      if (result.globalUpdated) {
        statusMsg += " and global efficiency rates"
      }
      if (result.historyRestored) {
        statusMsg += ", restored history"
      }
      if (result.activityLogRestored) {
        statusMsg += ", restored activity log"
      }
      if (result.roomsSkipped > 0) {
        statusMsg += ". Skipped ${result.roomsSkipped} rooms (not found)"
      }
      
      state.importStatus = statusMsg
      state.importSuccess = true
      
      // Clear the input field after successful import (ignore CI preference reevaluation quirks)
      try { app.updateSetting('importJsonData', '') } catch (ignore) { }
      
      log(2, 'App', "Import completed: ${result.roomsUpdated} rooms updated, ${result.roomsSkipped} skipped")
      
    } else {
      state.importStatus = "Import failed: ${result.error}"
      state.importSuccess = false
      logError "Import failed: ${result.error}"
    }
    
  } catch (Exception e) {
    def errorMsg = "Import failed: ${e.message}"
    logError errorMsg
    state.importStatus = "${errorMsg}"
    state.importSuccess = false
  }
}

def handleClearExportData() {
  try {
    log(2, 'App', "Clearing export data")
    state.remove('exportStatus')
    state.remove('exportedJsonData')
    log(2, 'App', "Export data cleared successfully")
  } catch (Exception e) {
    logError "Failed to clear export data: ${e.message}"
  }
}

def exportEfficiencyData() {
  def data = [
    globalRates: [
      maxCoolingRate: cleanDecimalForJson(atomicState.maxCoolingRate),
      maxHeatingRate: cleanDecimalForJson(atomicState.maxHeatingRate)
    ],
    roomEfficiencies: [],
    dabHistory: atomicState?.dabHistory ?: [:],
    dabActivityLog: atomicState?.dabActivityLog ?: []
  ]
  
  // Only collect from vents (devices with percent-open attribute)
  getChildDevices().findAll { it.hasAttribute('percent-open') }.each { device ->
    def coolingRate = device.currentValue('room-cooling-rate') ?: 0
    def heatingRate = device.currentValue('room-heating-rate') ?: 0
    
    def roomData = [
      roomId: device.currentValue('room-id'),
      roomName: device.currentValue('room-name'),
      ventId: device.getDeviceNetworkId(),
      coolingRate: cleanDecimalForJson(coolingRate),
      heatingRate: cleanDecimalForJson(heatingRate)
    ]
    data.roomEfficiencies << roomData
  }
  
  return data
}

// ------------------------------
// Dashboard Tiles and Manual Overrides (Usability)
// ------------------------------

private String tileDniForVentId(String ventId) { return "tile-${ventId}" }

def syncVentTiles() {
  try {
    def vents = getChildDevices()?.findAll { it.hasAttribute('percent-open') } ?: []
    vents.each { v ->
      String dni = tileDniForVentId(v.getDeviceNetworkId())
      def child = getChildDevice(dni)
      if (!child) {
        try {
          addChildDevice('bot.flair', 'Flair Vent Tile', dni, [name: "Tile ${v.getLabel()}", label: "Tile ${v.getLabel()}", isComponent: true])
        } catch (Exception e) {
          log(2, 'App', "Failed to create tile for ${v.getLabel()}: ${e?.message}")
        }
      }
    }
    refreshVentTiles()
  } catch (err) {
    logError "syncVentTiles error: ${err?.message}"
  }
}

private void subscribeToVentEventsForTiles() {
  try {
    def vents = getChildDevices()?.findAll { it.hasAttribute('percent-open') } ?: []
    unsubscribe(updateTileForEvent)
    vents.each { v ->
      subscribe(v, 'percent-open', updateTileForEvent)
      subscribe(v, 'room-current-temperature-c', updateTileForEvent)
      subscribe(v, 'level', updateTileForEvent)
      subscribe(v, 'room-name', updateTileForEvent)
    }
  } catch (e) {
    log(2, 'App', "subscribeToVentEventsForTiles error: ${e?.message}")
  }
}

def updateTileForEvent(evt) {
  try {
    def dev = evt?.device
    if (dev) { updateTileForVent(dev) }
  } catch (e) { log(2,'App',"updateTileForEvent error: ${e?.message}") }
}

def refreshVentTiles() {
  try {
    def vents = getChildDevices()?.findAll { it.hasAttribute('percent-open') } ?: []
    vents.each { v -> updateTileForVent(v) }
  } catch (e) { log(2,'App',"refreshVentTiles error: ${e?.message}") }
}

private void updateTileForVent(device) {
  try {
    String ventId = device.getDeviceNetworkId()
    def tile = getChildDevice(tileDniForVentId(ventId))
    if (!tile) { return }
    Integer level = (device.currentValue('percent-open') ?: device.currentValue('level') ?: 0) as int
    def name = device.currentValue('room-name') ?: device.getLabel()
    def temp = device.currentValue('room-current-temperature-c') ?: '-'
    if (temp == '-') {
      logWarn "Room data unavailable for '${name}'. Check network or thermostat connectivity."
    }
    def mode = atomicState?.manualOverrides?.containsKey(ventId) ? 'manual' : 'auto'
    def voltage = device.currentValue('voltage') ?: device.currentValue('system-voltage')
    def battery = device.currentValue('battery')
    def html = new StringBuilder()
    html << "<div style='font-family:sans-serif;padding:6px'>"
    html << "<div style='font-weight:bold;font-size:14px;margin-bottom:4px'>${name}</div>"
    // bar
    html << "<div style='height:8px;background:#e5e7eb;border-radius:4px;overflow:hidden'>"
    html << "<div style='height:8px;width:${level}%;background:#3b82f6'></div>"
    html << "</div>"
    html << "<div style='margin-top:6px;font-size:12px;color:#111'>Vent: <b>${level}%</b>"
    if (temp != '-') { html << " &nbsp; Temp: <b>${roundBigDecimal(temp)}</b>&deg;C" } else { html << " &nbsp; Temp: <b>-</b>" }
    if (battery != null) { html << " &nbsp; Battery: <b>${battery}%</b>" }
    if (voltage != null) { html << " &nbsp; V: <b>${roundBigDecimal(voltage)}</b>" }
    html << " &nbsp; Mode: <b>${mode}</b></div>"
    html << "</div>"
    sendEvent(tile, [name: 'html', value: html.toString()])
    sendEvent(tile, [name: 'level', value: level])
    if (temp != '-') { sendEvent(tile, [name: 'temperature', value: temp]) }
  } catch (e) {
    log(2,'App',"updateTileForVent error: ${e?.message}")
  }
}

// Manual override helpers
def activateNightOverride() {
  try {
    if (!settings?.nightOverrideRooms) { return }
    def overrides = atomicState?.manualOverrides ?: [:]
    Integer pct = (settings?.nightOverridePercent ?: 100) as int
    settings.nightOverrideRooms.each { v ->
      def vent = (v instanceof String) ? getChildDevice(v) : v
      if (!vent) { return }
      String vid = vent.getDeviceNetworkId()
      overrides[vid] = pct
      patchVent(vent, pct)
    }
    atomicState.manualOverrides = overrides
    refreshVentTiles()
  } catch (e) { log(2,'App',"activateNightOverride error: ${e?.message}") }
}

def deactivateNightOverride() {
  try {
    if (!atomicState?.manualOverrides) { return }
    if (settings?.nightOverrideRooms) {
      settings.nightOverrideRooms.each { v ->
        def vent = (v instanceof String) ? getChildDevice(v) : v
        if (!vent) { return }
        String vid = vent.getDeviceNetworkId()
        atomicState.manualOverrides.remove(vid)
      }
    } else {
      atomicState.manualOverrides = [:]
    }
    refreshVentTiles()
  } catch (e) { log(2,'App',"deactivateNightOverride error: ${e?.message}") }
}

def clearAllManualOverrides() {
  atomicState.manualOverrides = [:]
  refreshVentTiles()
}

// Tile driver command callbacks
def tileSetVentPercent(String tileDni, Integer percent) {
  try {
    String ventId = tileDni?.replaceFirst('^tile-','')
    def vent = getChildDevice(ventId)
    if (!vent) { return }
    def overrides = atomicState?.manualOverrides ?: [:]
    overrides[ventId] = percent
    atomicState.manualOverrides = overrides
    patchVent(vent, percent)
    refreshVentTiles()
  } catch (e) { log(2,'App',"tileSetVentPercent error: ${e?.message}") }
}

def tileSetManualMode(String tileDni) {
  try {
    String ventId = tileDni?.replaceFirst('^tile-','')
    def vent = getChildDevice(ventId)
    if (!vent) { return }
    def overrides = atomicState?.manualOverrides ?: [:]
    Integer pct = (vent.currentValue('percent-open') ?: vent.currentValue('level') ?: 100) as int
    overrides[ventId] = pct
    atomicState.manualOverrides = overrides
    refreshVentTiles()
  } catch (e) { log(2,'App',"tileSetManualMode error: ${e?.message}") }
}

def tileSetAutoMode(String tileDni) {
  try {
    String ventId = tileDni?.replaceFirst('^tile-','')
    if (atomicState?.manualOverrides) { atomicState.manualOverrides.remove(ventId) }
    refreshVentTiles()
  } catch (e) { log(2,'App',"tileSetAutoMode error: ${e?.message}") }
}

// Export DAB history from atomicState to JSON or CSV
def exportDabHistory(String format = 'json') {
  initializeDabHistory()
  def hist = atomicState?.dabHistory
  def entries = (hist instanceof List) ? hist : (hist?.entries ?: [])
  def records = entries.collect { rec ->
    [timestamp: rec[0], roomId: rec[1], hvacMode: rec[2], hour: rec[3], rate: rec[4]]
  }

  if (format == 'csv') {
    if (!records) { return '' }
    def headers = ['timestamp', 'roomId', 'hvacMode', 'hour', 'rate']
    def lines = []
    lines << headers.join(',')
    records.each { rec ->
      lines << headers.collect { h ->
        def val = rec[h]
        val = val != null ? val.toString().replaceAll('"', '""') : ''
        '"' + val + '"'
      }.join(',')
    }
    return lines.join('\n')
  }

  return JsonOutput.toJson(records)
}

def generateEfficiencyJSON(data) {
  def exportData = [
    exportMetadata: [
      version: '0.24',
      exportDate: new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'"),
      structureId: settings.structureId ?: 'Unknown'
    ],
    efficiencyData: data
  ]
  return JsonOutput.toJson(exportData)
}

def importEfficiencyData(jsonContent) {
  try {
    def jsonData = new groovy.json.JsonSlurper().parseText(jsonContent)
    
    if (!validateImportData(jsonData)) {
      return [success: false, error: 'Invalid data format. Please ensure you are using exported efficiency data.']
    }
    
    def results = applyImportedEfficiencies(jsonData.efficiencyData)
    
    return [
      success: true,
      globalUpdated: results.globalUpdated,
      roomsUpdated: results.roomsUpdated,
      roomsSkipped: results.roomsSkipped,
      historyRestored: results.historyRestored,
      activityLogRestored: results.activityLogRestored,
      errors: results.errors
    ]
  } catch (Exception e) {
    return [success: false, error: e.message]
  }
}

def validateImportData(jsonData) {
  // Check required structure
  if (!jsonData.exportMetadata || !jsonData.efficiencyData) return false
  if (!jsonData.efficiencyData.globalRates) return false
  // Allow empty list for roomEfficiencies, but it must be present
  if (jsonData.efficiencyData.roomEfficiencies == null) return false
  if (jsonData.efficiencyData.dabHistory && !(jsonData.efficiencyData.dabHistory instanceof Map)) return false
  if (jsonData.efficiencyData.dabActivityLog && !(jsonData.efficiencyData.dabActivityLog instanceof List)) return false
  
  // Validate global rates
  def globalRates = jsonData.efficiencyData.globalRates
  if (globalRates.maxCoolingRate == null || globalRates.maxHeatingRate == null) return false
  if (globalRates.maxCoolingRate < 0 || globalRates.maxHeatingRate < 0) return false
  if (globalRates.maxCoolingRate > 10 || globalRates.maxHeatingRate > 10) return false
  
  // Allow per-room entries to be partial; but validate numeric ranges if provided
  for (room in (jsonData.efficiencyData.roomEfficiencies as List)) {
    if (room?.coolingRate != null) {
      if (room.coolingRate < 0 || room.coolingRate > 10) return false
    }
    if (room?.heatingRate != null) {
      if (room.heatingRate < 0 || room.heatingRate > 10) return false
    }
  }
  
  return true
}

def applyImportedEfficiencies(efficiencyData) {
  def results = [
    globalUpdated: false,
    roomsUpdated: 0,
    roomsSkipped: 0,
    errors: [],
    historyRestored: false,
    activityLogRestored: false
  ]
  
  // Update global rates
  if (efficiencyData.globalRates) {
    try { atomicState.maxCoolingRate = efficiencyData.globalRates.maxCoolingRate } catch (ignore) { }
    try { atomicState.maxHeatingRate = efficiencyData.globalRates.maxHeatingRate } catch (ignore) { }
    results.globalUpdated = true
    log(2, 'App', "Updated global rates: cooling=${efficiencyData.globalRates.maxCoolingRate}, heating=${efficiencyData.globalRates.maxHeatingRate}")
  }
  
  // Update room efficiencies
  efficiencyData.roomEfficiencies?.each { roomData ->
    def device = matchDeviceByRoomId(roomData.roomId) ?: matchDeviceByRoomName(roomData.roomName)
    
    if (device) {
      def safeSend = { d, evt ->
        try {
          sendEvent(d, evt)
        } catch (ignored) {
          try { sendEvent(evt) } catch (ignored2) { }
        }
      }
      safeSend(device, [name: 'room-cooling-rate', value: roomData.coolingRate])
      safeSend(device, [name: 'room-heating-rate', value: roomData.heatingRate])
      results.roomsUpdated++
      log(2, 'App', "Updated efficiency for '${roomData.roomName}': cooling=${roomData.coolingRate}, heating=${roomData.heatingRate}")
    } else {
      results.roomsSkipped++
      results.errors << "Room not found: ${roomData.roomName} (${roomData.roomId})"
      log(2, 'App', "Skipped room '${roomData.roomName}' - no matching device found")
    }
  }

  if (efficiencyData.dabHistory) {
    atomicState.dabHistory = efficiencyData.dabHistory
    results.historyRestored = true
    log "Restored DAB history (${efficiencyData.dabHistory.size()} rooms)", 2
  }

  if (efficiencyData.dabActivityLog) {
    atomicState.dabActivityLog = efficiencyData.dabActivityLog
    results.activityLogRestored = true
    log "Restored DAB activity log (${efficiencyData.dabActivityLog.size()} entries)", 2
  }

  return results
}

def matchDeviceByRoomId(roomId) {
  return getChildDevices()?.find { device ->
    device.hasAttribute('percent-open') && device.currentValue('room-id') == roomId
  }
}

def matchDeviceByRoomName(roomName) {
  return getChildDevices()?.find { device ->
    device.hasAttribute('percent-open') && device.currentValue('room-name') == roomName
  }
}

def efficiencyDataPage() {
  // Auto-generate export data on page load
  def vents = getChildDevices().findAll { it.hasAttribute('percent-open') }
  def roomsWithData = vents.findAll { 
    (it.currentValue('room-cooling-rate') ?: 0) > 0 || 
    (it.currentValue('room-heating-rate') ?: 0) > 0 
  }
  
  // Automatically generate JSON data when page loads
  def exportJsonData = ""
  if (roomsWithData.size() > 0) {
    try {
      def efficiencyData = exportEfficiencyData()
      exportJsonData = generateEfficiencyJSON(efficiencyData)
    } catch (Exception e) {
      log(2, 'App', "Error generating export data: ${e.message}")
    }
  }
  
  dynamicPage(name: 'efficiencyDataPage', title: 'Backup & Restore Efficiency Data', install: false, uninstall: false) {
    section {
      paragraph '''
        <div style="background-color: #f0f8ff; padding: 15px; border-left: 4px solid #007bff; margin-bottom: 20px;">
          <h3 style="margin-top: 0; color: #0056b3;">What is this?</h3>
          <p style="margin-bottom: 0;">Your Flair vents learn how efficiently each room heats and cools over time. This data helps the system optimize energy usage. 
          Use this page to backup your data before app updates or restore it after system resets.</p>
        </div>
      '''
    }
    
    // Show current status
    if (vents.size() > 0) {
      section("Current Status") {
        if (roomsWithData.size() > 0) {
          paragraph "<div style='color: green; font-weight: bold;'>Your system has learned efficiency data for ${roomsWithData.size()} out of ${vents.size()} rooms</div>"
        } else {
          paragraph "<div style='color: orange; font-weight: bold;'>Your system is still learning (${vents.size()} rooms found, but no efficiency data yet)</div>"
          paragraph "<small>Let your system run for a few heating/cooling cycles before backing up data.</small>"
        }
      }
    }
    
    // Export Section - Auto-generated
    if (roomsWithData.size() > 0 && exportJsonData) {
      section("Save Your Data (Backup)") {
        // Create base64 encoded download link with current date
        def currentDate = new Date().format("yyyy-MM-dd")
        def fileName = "Flair-Backup-${currentDate}.json"
        def base64Data = exportJsonData.bytes.encodeBase64().toString()
        def downloadUrl = "data:application/json;charset=utf-8;base64,${base64Data}"
        
        paragraph "Your backup data is ready:"
        
        paragraph "<a href=\"${downloadUrl}\" download=\"${fileName}\">Download ${fileName}</a>"
      }
    } else if (vents.size() > 0) {
      section("Save Your Data (Backup)") {
        paragraph "System is still learning. Check back after a few heating/cooling cycles."
      }
    }
    
    // Import Section
    section("Step 2: Restore Your Data (Import)") {
      paragraph '''
        <p><strong>When should I do this?</strong></p>
        <p>- After reinstalling this app<br>
        - After resetting your Hubitat hub<br>
        - After replacing hardware</p>
      '''
      
      paragraph '''
        <p><strong>How to restore your data:</strong></p>
        <p>1. Find your saved backup JSON file (e.g., "Flair-Backup-2025-06-26.json")<br>
        2. Open the JSON file in Notepad/TextEdit<br>
        3. Select all text (Ctrl+A) and copy (Ctrl+C)<br>
        4. Paste it in the box below (Ctrl+V)<br>
        5. Click "Restore My Data"</p>
        
        <p><small><strong>Note:</strong> Hubitat doesn't support file uploads, so we need to copy/paste the JSON content.</small></p>
      '''
      
      input name: 'importJsonData', type: 'textarea', title: 'Paste JSON Backup Data', 
            description: 'Open your backup JSON file and paste ALL the content here',
            required: false, rows: 8
      
      input name: 'importEfficiencyData', type: 'button', title: 'Restore My Data', 
            submitOnChange: true, width: 4
      
      if (state.importStatus) {
        def statusColor = state.importSuccess ? 'green' : 'red'
        def statusIcon = state.importSuccess ? '&#10003;' : '&#10007;'
        paragraph "<div style='color: ${statusColor}; font-weight: bold; margin-top: 15px; padding: 10px; background-color: ${state.importSuccess ? '#e8f5e8' : '#ffe8e8'}; border-radius: 5px;'>${statusIcon} ${state.importStatus}</div>"
        
        if (state.importSuccess) {
          paragraph '''
            <div style="background-color: #e8f5e8; padding: 15px; border-radius: 5px; margin-top: 10px;">
              <h4 style="margin-top: 0; color: #2d5a2d;">Success! What happens now?</h4>
              <p>Your room learning data has been restored. Your Flair vents will now use the saved efficiency information to:</p>
              <ul>
                <li>Optimize airflow to each room</li>
                <li>Reduce energy usage</li>
                <li>Maintain comfortable temperatures</li>
              </ul>
              <p style="margin-bottom: 0;"><strong>You're all set!</strong> The system will continue learning and improving from this restored baseline.</p>
            </div>
          '''
        }
      }
    }

    // Clear/Reset Section
    section("Reset DAB Data") {
      paragraph "Use this to clear learned efficiency rates and history for all rooms. This does not delete devices."
      input name: 'clearDabDataNow', type: 'button', title: 'Clear All DAB Data', submitOnChange: true
      if (settings?.clearDabDataNow) {
        handleClearDabData()
        app.updateSetting('clearDabDataNow','')
      }
      if (state?.clearDabStatus) {
        paragraph state.clearDabStatus
      }
    }
    
    // Help & Tips Section
    section("Need Help?") {
      paragraph '''
        <div style="background-color: #f8f9fa; padding: 15px; border-radius: 5px;">
          <h4 style="margin-top: 0;">Tips for Success</h4>
          <ul style="margin-bottom: 10px;">
            <li><strong>Regular Backups:</strong> Save your data monthly or before any system changes</li>
            <li><strong>File Naming:</strong> Include the date in your backup filename (e.g., "Flair-Backup-2025-06-26")</li>
            <li><strong>Multiple Copies:</strong> Store backups in multiple places (email, cloud storage, USB drive)</li>
            <li><strong>When to Restore:</strong> Only restore data when setting up a new system or after data loss</li>
          </ul>
          
          <h4>Troubleshooting</h4>
          <ul style="margin-bottom: 0;">
            <li><strong>Import Failed:</strong> Make sure you copied ALL the text from your backup file</li>
            <li><strong>No Data to Export:</strong> Let your system run for a few heating/cooling cycles first</li>
            <li><strong>Room Not Found:</strong> Room names may have changed - the system will skip those rooms</li>
            <li><strong>Still Need Help:</strong> Check the Hubitat community forums or contact support</li>
          </ul>
        </div>
      '''
    }
    
    section {
      href name: 'backToMain', title: 'Back to Main Settings', description: 'Return to the main app configuration', page: 'landingPage'
    }
  }
}

/* DUPLICATE REMOVED def dabHistoryPage() {
  dynamicPage(name: 'dabHistoryPage', title: 'DAB History', install: false, uninstall: false) {
    section {
      def vents = getChildDevices()?.findAll { it.hasAttribute('percent-open') } ?: []
      Map roomOptions = vents.collectEntries { v ->
        [(v.currentValue('room-id') ?: v.getId()): (v.currentValue('room-name') ?: v.getLabel())]
      }
      input name: 'historyRoom', type: 'enum', title: 'Room', required: false, submitOnChange: true, options: roomOptions
      input name: 'historyHvacMode', type: 'enum', title: 'HVAC Mode', required: false, submitOnChange: true,
            options: [(COOLING): 'Cooling', (HEATING): 'Heating', 'both': 'Both']
      input name: 'historyStart', type: 'date', title: 'Start Date', required: false, submitOnChange: true
      input name: 'historyStartHour', type: 'number', title: 'Start Hour (0-23)', required: false, range: '0..23', submitOnChange: true
      input name: 'historyEnd', type: 'date', title: 'End Date', required: false, submitOnChange: true
      input name: 'historyEndHour', type: 'number', title: 'End Hour (0-23)', required: false, range: '0..23', submitOnChange: true
      def result = buildDabHistoryTable()
      def allErrors = []
      if (atomicState?.dabHistoryErrors) { allErrors += atomicState.dabHistoryErrors }
      if (result.errors) { allErrors += result.errors }
      if (allErrors) {
        paragraph "<span style='color:red'>${allErrors.join('<br>')}</span>"
      }
      paragraph result.table
    }
    section {
      href name: 'backToMain', title: 'Back to Main Settings', description: 'Return to the main app configuration', page: 'landingPage'
    }
  }
} */

def dabActivityLogPage() {
  dynamicPage(name: 'dabActivityLogPage', title: 'DAB Activity Log', install: false, uninstall: false) {
    section {
      int page = (settings?.activityLogPage ?: 1) as int
      def entries = (atomicState?.dabActivityLog ?: []).reverse()
      if (entries) {
        int totalPages = ((entries.size() - 1) / ACTIVITY_LOG_PAGE_SIZE) + 1
        if (page < 1) { page = 1 }
        if (page > totalPages) { page = totalPages }
        int start = (page - 1) * ACTIVITY_LOG_PAGE_SIZE
        int end = Math.min(start + ACTIVITY_LOG_PAGE_SIZE, entries.size())
        paragraph "<p>Page ${page} of ${totalPages}</p>"
        entries.subList(start, end).each { paragraph "<code>${it}</code>" }
        input name: 'activityLogPage', type: 'number', title: 'Page', defaultValue: page, submitOnChange: true
      } else {
        paragraph 'No activity yet.'
      }
    }
    section {
      href name: 'backToMain', title: 'Back to Main Settings', description: 'Return to the main app configuration', page: 'landingPage'
    }
  }
}

def dabHistoryPage() {
  dynamicPage(name: 'dabHistoryPage', title: 'DAB History', install: false, uninstall: false) {
    section {
      input name: 'historyHvacMode', type: 'enum', title: 'HVAC Mode', required: false, submitOnChange: true,
            options: [(COOLING): 'Cooling', (HEATING): 'Heating', 'both': 'Both']
      input name: 'historyStart', type: 'date', title: 'Start Date', required: false, submitOnChange: true
      input name: 'historyEnd', type: 'date', title: 'End Date', required: false, submitOnChange: true
      input name: 'historyPage', type: 'number', title: 'Page', required: false, defaultValue: 1, submitOnChange: true
    }
    section {
      def history = atomicState?.dabHistory ?: [:]
      def vents = getChildDevices()?.findAll { it.hasAttribute('percent-open') } ?: []
      Map roomNames = vents.collectEntries { v -> [(v.currentValue('room-id') ?: v.getId()): (v.currentValue('room-name') ?: v.getLabel())] }
      String start = settings?.historyStart
      String end = settings?.historyEnd
      String mode = settings?.historyHvacMode ?: 'both'
      List entries = []
      history.each { roomId, modeMap ->
        if (!(modeMap instanceof Map)) { return }
        modeMap.each { hvacMode, records ->
          if (!(records instanceof List)) { return }
          if (mode == 'both' || hvacMode == mode) {
            records.findAll { rec -> rec instanceof Map && rec.date && rec.hour != null && rec.rate != null }.each { rec ->
              if ((!start || rec.date >= start) && (!end || rec.date <= end)) {
                entries << [date: rec.date, hour: (rec.hour as Integer), room: roomNames[roomId] ?: roomId, hvacMode: hvacMode, rate: rec.rate]
              }
            }
          }
        }
      }
      entries.sort { a, b -> (a.date <=> b.date) ?: (a.hour <=> b.hour) }
      if (entries) {
        int page = (settings?.historyPage ?: 1) as int
        int totalPages = ((entries.size() - 1) / HISTORY_PAGE_SIZE) + 1
        if (page < 1) { page = 1 }
        if (page > totalPages) { page = totalPages }
        int startIdx = (page - 1) * HISTORY_PAGE_SIZE
        int endIdx = Math.min(startIdx + HISTORY_PAGE_SIZE, entries.size())
        paragraph "<p>Page ${page} of ${totalPages}</p>"
        entries.subList(startIdx, endIdx).each { e ->
          String hr = e.hour.toString().padLeft(2, '0')
          paragraph "<code>${e.date} ${hr}:00 ${e.room} (${e.hvacMode}) - ${e.rate}</code>"
        }
      } else {
        paragraph 'No DAB history for selected filters.'
      }
    }
    section {
      href name: 'backToMain', title: 'Back to Main Settings', description: 'Return to the main app configuration', page: 'landingPage'
    }
  }
}

def dabRatesTablePage() {
  dynamicPage(name: 'dabRatesTablePage', title: 'Hourly DAB Rates Table', install: false, uninstall: false) {
    section {
      input name: 'tableHvacMode', type: 'enum', title: 'HVAC Mode', required: false, submitOnChange: true,
            options: [(COOLING): 'Cooling', (HEATING): 'Heating', 'both': 'Both']
      paragraph buildDabRatesTable()
    }
    section {
      href name: 'backToMain', title: 'Back to Main Settings', description: 'Return to the main app configuration', page: 'landingPage'
    }
  }
}

def dabProgressPage() {
  dynamicPage(name: 'dabProgressPage', title: 'DAB Progress', install: false, uninstall: false) {
    section {
      def vents = getChildDevices()?.findAll { it.hasAttribute('percent-open') } ?: []
      boolean hasVents = vents && vents.size() > 0
      if (hasVents) {
        Map roomOptions = vents.collectEntries { v -> [(v.currentValue('room-id') ?: v.getId()): (v.currentValue('room-name') ?: v.getLabel())] }
        input name: 'progressRoom', type: 'enum', title: 'Room', required: false, submitOnChange: true, options: roomOptions
      } else {
        paragraph '<p>No vents available. Add vents to use DAB Progress.</p>'
      }
      input name: 'progressHvacMode', type: 'enum', title: 'HVAC Mode', required: false, submitOnChange: true,
            options: [(COOLING): 'Cooling', (HEATING): 'Heating', 'both': 'Both']
      input name: 'progressStart', type: 'date', title: 'Start Date', required: false, submitOnChange: true
      input name: 'progressEnd', type: 'date', title: 'End Date', required: false, submitOnChange: true
      if (hasVents) { paragraph buildDabProgressTable() }
    }
    section {
      href name: 'backToMain', title: 'Back to Main Settings', description: 'Return to the main app configuration', page: 'landingPage'
    }
  }
}

def dabDailySummaryPage() {
  dynamicPage(name: 'dabDailySummaryPage', title: 'Daily DAB Summary', install: false, uninstall: false) {
    section {
      input name: 'dailySummaryPage', type: 'number', title: 'Page', required: false, defaultValue: 1, submitOnChange: true
      paragraph buildDabDailySummaryTable()
    }
    section {
      href name: 'backToMain', title: 'Back to Main Settings', description: 'Return to the main app configuration', page: 'landingPage'
    }
  }
}

String buildDabRatesTable() {
  def vents = getChildDevices().findAll { it.hasAttribute('percent-open') }
  if (!vents || vents.size() == 0) {
    return '<p>No vent data available.</p>'
  }
  String hvacMode = settings?.tableHvacMode ?: getThermostat1Mode() ?: atomicState?.lastHvacMode
  if (!hvacMode || hvacMode in ['auto', 'manual']) {
    hvacMode = atomicState?.lastHvacMode
  }
  hvacMode = hvacMode ?: COOLING
  def hours = (0..23)
  def html = new StringBuilder()
  html << "<table style='width:100%;border-collapse:collapse;'>"
  html << "<tr><th style='text-align:left;padding:4px;'>Room</th>"
  hours.each { hr -> html << "<th style='text-align:right;padding:4px;'>${hr}</th>" }
  html << '</tr>'
  vents.each { vent ->
    def roomId = vent.currentValue('room-id') ?: vent.getId()
    def roomName = vent.currentValue('room-name') ?: vent.getLabel()
    html << "<tr><td style='text-align:left;padding:4px;'>${roomName}</td>"
    hours.each { hr ->
      def value
      if (hvacMode == 'both') {
        def cooling = atomicState?.dabHistory?.hourlyRates?.get(roomId)?.get(COOLING)?.get(hr) ?: []
        def heating = atomicState?.dabHistory?.hourlyRates?.get(roomId)?.get(HEATING)?.get(hr) ?: []
        def combined = (cooling + heating).collect { it as BigDecimal }
        value = combined ? cleanDecimalForJson(combined.sum() / combined.size()) : 0.0
      } else {
        value = getAverageHourlyRate(roomId, hvacMode, hr) ?: 0.0
      }
      html << "<td style='text-align:right;padding:4px;'>${roundBigDecimal(value)}</td>"
    }
    html << '</tr>'
  }
  html << '</table>'
  html.toString()
}

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

// Wrapper for async HTTP GET responses that dispatches to the original callback
// specified in getDataAsync. The actual callbacks handle request accounting.
def asyncHttpGetWrapper(resp, meta) {
  try {
    // Always decrement active requests first
    decrementActiveRequests()
    
    // Check for HTTP errors and record failures
    if (resp?.hasError() || (resp?.getStatus() as int) >= 400) {
      def uri = meta?.uri
      if (uri) {
        recordHttpFailure(uri)
        log(2, 'App', "HTTP error for ${uri}: ${resp?.getStatus()} - ${resp?.getErrorMessage()}")
      }
    }
    
    if (!meta?.callback) { return }
    def cb = meta.callback as String
    if (this.respondsTo(cb)) {
      this."${cb}"(resp, meta.data)
    } else {
      log(1, 'App', "Missing callback '${cb}' for async response")
    }
  } catch (e) {
    log(1, 'App', "asyncHttpGetWrapper error: ${e.message}")
    // Make sure we always decrement on error
    decrementActiveRequests()
  }
}

def quickControlsPage() {
  dynamicPage(name: 'quickControlsPage', title: '\u26A1 Quick Controls', install: false, uninstall: false) {
    section('Per-Room Status & Controls') {
      def children = getChildDevices() ?: []
      children.each { d ->
        String driverName = d.typeName ?: 'Unknown'
        log(4, 'QuickControl', "Child device id=${d.getId()}, label='${d.getLabel()}', driver=${driverName}")
      }
      def vents = children.findAll { (getTypeNameSafe(it) ?: '') == 'Flair vents' || hasAttrSafe(it, 'percent-open') || hasAttrSafe(it, 'level') }
      def skipped = children.findAll { !((it.typeName ?: '') == 'Flair vents' || it.hasAttribute('percent-open')) }
      def skippedDesc = skipped.collect { d ->
        String drv = d.typeName ?: 'Unknown'
        "${d.getId()} (${d.getLabel()}): driver '${drv}' without 'percent-open'"
      }
      // De-duplicate vents by device ID before building the room map
      def uniqueVents = [:]
      vents.each { v -> uniqueVents[v.getId()] = v }
      vents = uniqueVents.values() as List
      // Build 1 row per room
      def byRoom = [:]
      atomicState.qcDeviceMap = [:]
      atomicState.qcRoomMap = [:]
      vents.each { v ->
        def rid = (v.currentValue('room-id') ?: v.getDeviceNetworkId())?.toString()
        if (!byRoom.containsKey(rid)) { byRoom[rid] = v }
      }
      if (byRoom.isEmpty()) {
        String skippedMsg = skippedDesc ? skippedDesc.join(', ') : 'none'
        logWarn("No vents with manual control are available. Skipped devices: ${skippedMsg}", 'QuickControl')
        paragraph 'No vents with manual control are available.'
      }
      byRoom.each { roomId, v ->
        String roomIdStr = roomId?.toString()
        Integer cur = (v.currentValue('percent-open') ?: v.currentValue('level') ?: 0) as int
        def vid = v.getDeviceNetworkId()
        def roomName = v.currentValue('room-name') ?: v.getLabel()
        def tempC = v.currentValue('room-current-temperature-c') ?: '-'
        def setpC = v.currentValue('room-set-point-c') ?: '-'
        def active = v.currentValue('room-active') ?: 'false'
        def upd = v.currentValue('updated-at') ?: ''
        def batt = v.currentValue('battery') ?: ''
        def toF = { c -> c != '-' && c != null ? (((c as BigDecimal) * 9/5) + 32) : null }
        def fmt1 = { x -> x != null ? (((x as BigDecimal) * 10).round() / 10) : '-' }
        def tempF = tempC != '-' ? fmt1(toF(tempC)) : '-'
        def setpF = setpC != '-' ? fmt1(toF(setpC)) : '-'
        def vidKey = vid.replaceAll('[^A-Za-z0-9_]', '_')
        def roomKey = roomIdStr.replaceAll('[^A-Za-z0-9_]', '_')
        atomicState.qcDeviceMap[vidKey] = vid
        atomicState.qcRoomMap[roomKey] = roomIdStr
        if (tempC == '-' || setpC == '-') {
          logWarn "Room data unavailable for '${roomName}'. Check network or thermostat connectivity."
        }
        paragraph "<b>${roomName}</b> - Vent: ${cur}% | Temp: ${tempF} °F | Setpoint: ${setpF} °F | Active: ${active}" + (batt ? " | Battery: ${batt}%" : "") + (upd ? " | Updated: ${upd}" : "")
        input name: "qc_${vidKey}_percent", type: 'number', title: 'Set vent percent', required: false, submitOnChange: false
        input name: "qc_room_${roomKey}_setpoint", type: 'number', title: 'Set room setpoint (°F)', required: false, submitOnChange: false
        input name: "qc_room_${roomKey}_active", type: 'enum', title: 'Set room active', options: ['true','false'], required: false, submitOnChange: false
      }
      input name: 'applyQuickControlsNow', type: 'button', title: 'Apply All Changes', submitOnChange: true
    }
    section('Active Rooms Now') {
      def vents = getChildDevices()?.findAll { (it.typeName ?: '') == 'Flair vents' } ?: []
      vents += getChildDevices()?.findAll { it.hasAttribute('percent-open') } ?: []
      def uniqueVents = [:]
      vents.each { v -> uniqueVents[v.getId()] = v }
      def actives = uniqueVents.values().findAll { (it.currentValue('room-active') ?: 'false') == 'true' }
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
      href name: 'backToMain', title: '\u2795 Back to Main Settings', description: 'Return to the main app configuration', page: 'landingPage'
    }
  }
}

  private void applyQuickControls() {
    def overrides = atomicState?.manualOverrides ?: [:]
    def allKeys = (settings?.keySet() ?: []) as List
    def deviceMap = atomicState?.qcDeviceMap ?: [:]
    def roomMap = atomicState?.qcRoomMap ?: [:]
    // Per-vent percent controls
    def pctKeys = allKeys.findAll { (it as String).startsWith('qc_') && (it as String).endsWith('_percent') }
    pctKeys.each { k ->
      def sid = (k as String).replace('qc_','').replace('_percent','')
      def vid = deviceMap[sid] ?: sid
      def v = getChildDevice(vid)
      if (!v) { return }
      def val = settings[k]
      if (val != null && val != '') {
        Integer pct = (val as Integer)
        // Enforce floor for manual entries unless full close allowed
        try {
          if (!(settings?.allowFullClose == true)) {
            int floor = ((settings?.minVentFloorPercent ?: 0) as int)
            if (pct < floor) { pct = floor }
          }
        } catch (ignore) { }
        overrides[vid] = pct
        patchVentWithVerification(v, pct)
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
      if (v && val != null && val != '') {
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

private void openAllSelected(Integer pct) {
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
      patchVentWithVerification(v, pct)
    } catch (ignore) { }
  }
  atomicState.manualOverrides = overrides
}

  private void manualAllEditedVents() {
    def keys = settings?.keySet()?.findAll { (it as String).startsWith('qc_') && (it as String).endsWith('_percent') } ?: []
    def overrides = atomicState?.manualOverrides ?: [:]
    def deviceMap = atomicState?.qcDeviceMap ?: [:]
    keys.each { k ->
      def sid = (k as String).replace('qc_','').replace('_percent','')
      def vid = deviceMap[sid] ?: sid
      def val = settings[k]
      if (val != null && val != '') { overrides[vid] = (val as Integer) }
    }
    atomicState.manualOverrides = overrides
    refreshVentTiles()
  }



private String buildDiagnosticsJson() {
  def vents = getChildDevices()?.findAll { it.hasAttribute('percent-open') } ?: []
  def ventSummaries = vents.collect { v ->
    def tempC = v.currentValue('room-current-temperature-c') ?: '-'
    if (tempC == '-') {
      logWarn "Room data unavailable for '${v.currentValue('room-name') ?: v.getLabel()}'. Check network or thermostat connectivity."
    }
    [
      id: v.getDeviceNetworkId(),
      roomId: v.currentValue('room-id'),
      room: v.currentValue('room-name'),
      percent: v.currentValue('percent-open') ?: v.currentValue('level'),
      tempC: tempC,
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

private String buildRawCacheJson() {
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

private void clearRawCache() {
  try {
    atomicState.remove('rawDabSamplesEntries')
    atomicState.remove('rawDabLastByVent')
    log(2, 'App', 'Cleared raw data cache')
  } catch (ignore) { }
}

// Build and log DAB snapshot for diagnostics
private void buildDabSnapshot() {
  try {
    def snapshot = [
      generatedAt: new Date().format('yyyy-MM-dd HH:mm:ss', location?.timeZone ?: TimeZone.getTimeZone('UTC')),
      hvacState: [
        currentMode: atomicState.hvacCurrentMode ?: 'unknown',
        lastMode: atomicState.hvacLastMode ?: 'unknown',
        lastChangeTs: atomicState.hvacLastChangeTs ? new Date(atomicState.hvacLastChangeTs).format('yyyy-MM-dd HH:mm:ss', location?.timeZone ?: TimeZone.getTimeZone('UTC')) : null,
        thermostat1State: atomicState.thermostat1State
      ],
      events: atomicState.dabEvents ?: [],
      metadata: atomicState.dabMetadata ?: [:],
      samples: atomicState.dabSamples ?: [:],
      anomalyInfluence: atomicState.anomalyInfluence ?: [:],
      vents: []
    ]
    
    // Add vent-specific data
    def vents = getChildDevices()?.findAll { it.hasAttribute('percent-open') } ?: []
    vents.each { vent ->
      try {
        def tVal = getRoomTemp(vent)
        def ventData = [
          id: vent.getDeviceNetworkId(),
          roomName: vent.currentValue('room-name'),
          percentOpen: vent.currentValue('percent-open') ?: 0,
          roomCoolingRate: vent.currentValue('room-cooling-rate') ?: 0,
          roomHeatingRate: vent.currentValue('room-heating-rate') ?: 0,
          dabRawTargetPercent: vent.currentValue('dab-raw-target-percent') ?: 0,
          dabTargetPercent: vent.currentValue('dab-target-percent') ?: 0,
          dabHourlyRate: vent.currentValue('dab-hourly-rate') ?: 0,
          dabAnomalyFactor: vent.currentValue('dab-anomaly-factor') ?: 1.0,
          dabCarryForward: vent.currentValue('dab-carry-forward') ?: false,
          roomTemp: (tVal instanceof Number ? tVal : '-'),
          roomActive: (vent.currentValue('room-active') ?: 'false') == 'true'
        ]
        snapshot.vents << ventData
      } catch (Exception e) {
        log(2, 'DAB', "Error adding vent data to snapshot: ${e.message}")
      }
    }
    
    // Log structured snapshot
    def jsonOutput = new groovy.json.JsonBuilder(snapshot)
    log(2, 'DAB', "=== DAB SNAPSHOT ===")
    log(2, 'DAB', jsonOutput.toPrettyString())
    log(2, 'DAB', "=== END DAB SNAPSHOT ===")
    
    // Also log key metrics as individual lines for easier parsing
    log(2, 'DAB', "DAB-METRICS: events=${snapshot.events.size()} metadata=${snapshot.metadata.size()} samples=${snapshot.samples.size()} anomaly=${snapshot.anomalyInfluence.size()} vents=${snapshot.vents.size()}")
    
  } catch (Exception e) {
    log(2, 'DAB', "Error building DAB snapshot: ${e.message}")
  }
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













def deviceSyncPage() {
  dynamicPage(name: 'deviceSyncPage', title: 'Device Link Checker') {
    section('Children vs Cloud') {
      paragraph 'Creates any missing child vents/pucks discovered from Flair.'
      input name: 'syncChildrenNow', type: 'button', title: 'Run Sync', submitOnChange: true
      if (settings?.syncChildrenNow) {
        try {
          app.updateSetting('syncChildrenNow','')
          getStructureDataAsync(0)
          paragraph "\u2713 Sync started. Check logs; children will be created if missing."
        } catch (e) {
          paragraph "\u2717 Sync failed: "
        }
      }
      def vents = getChildDevices()?.findAll { it.hasAttribute('percent-open') } ?: []
      def pucks = getChildDevices()?.findAll { (it.typeName ?: '').contains('puck') || !it.hasAttribute('percent-open') } ?: []
      paragraph "Vents:  | Pucks: "
    }
  }
}

def trendDebugPage() {
  dynamicPage(name: 'trendDebugPage', title: 'Trend Debug') {
    section('Recent Room Temperature Trends') {
      def trend = atomicState?.hvacTrend ?: [:]
      if (!trend) { paragraph 'No trend samples collected yet.' }
      trend.keySet().take(25).each { key ->
        def recs = (trend[key] ?: []) as List
        if (recs && recs.size() >= 1) {
          def last = recs[-1]
          def ref = recs.find { (last.ts - it.ts) >= (HVAC_TREND_WINDOW_MIN * 60 * 1000) }
          BigDecimal delta = (ref && last?.t != null && ref?.t != null) ? ((last.t as BigDecimal) - (ref.t as BigDecimal)) : 0G
          paragraph "${key}: now=${last?.t}°C delta(${HVAC_TREND_WINDOW_MIN}m)=${delta}°C samples=${recs.size()}"
        }
      }
      input name: 'clearTrendNow', type: 'button', title: 'Clear Trend Samples', submitOnChange: true
      if (settings?.clearTrendNow) {
        app.updateSetting('clearTrendNow','')
        atomicState.remove('hvacTrend')
        paragraph '\u2713 Cleared trend samples.'
      }
    }
  }
}



def roomTargetsPage() {
  return dynamicPage(name: 'roomTargetsPage', title: 'Room Targets (DAB-only)') {
    section('Targets per Room') {
      paragraph 'Room targets configuration is currently unavailable.'
    }
  }
}

}

