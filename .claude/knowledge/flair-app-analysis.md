# Flair Vents Hubitat Integration -- Exhaustive Codebase Analysis

## 1. Project Overview

**App Name**: Flair Vents
**Version**: 0.239 (app), 0.234 (drivers), 0.232 (package manifest)
**Author**: Jaime Botero (ljbotero)
**License**: Apache 2.0
**Namespace**: `bot.flair`
**Repository**: `hubitat-flair-vents` (GitHub: ljbotero)
**Hubitat Package Manager ID**: `f2f5af6b-3729-41a2-8292-48d36b485b52`

**Purpose**: Hubitat integration for Flair Smart Vents and Pucks that implements Dynamic Airflow Balancing (DAB) -- a machine-learning algorithm that tracks per-room heating/cooling efficiency and automatically optimizes vent positions to reduce HVAC runtime.

---

## 2. Directory Structure

```
hubitat-flair-vents-beta/
  .gitignore
  .groovylintrc.json           # Groovy lint configuration
  build.gradle                 # Gradle build (Java 11, Spock 1.2, hubitat_ci)
  packageManifest.json         # HPM package manifest
  repository.json              # HPM repository descriptor
  CHANGELOG.md                 # Version history
  README.md                    # User documentation
  TESTING.md                   # Test guide
  architecture.md              # Architecture reference
  hubitat-flair-vents-device.png  # Screenshot
  src/
    hubitat-flair-vents-app.groovy          # Main parent app (~6734 lines, 275KB)
    hubitat-flair-vents-driver.groovy       # Vent device driver (~185 lines)
    hubitat-flair-vent-tile-driver.groovy   # Dashboard tile virtual driver (~60 lines)
    hubitat-flair-vents-pucks-driver.groovy # Puck device driver (~136 lines)
    hubitat-ecobee-smart-participation.groovy # Ecobee sensor participation app (~183 lines)
  tests/
    37 test files (Spock/Groovy)
```

---

## 3. Source File Analysis

### 3.1 hubitat-flair-vents-app.groovy (Parent App)

**Type**: Hubitat App (Parent)
**Lines**: ~6734
**Version**: 0.239

#### Definition Block
```groovy
definition(
    name: 'Flair Vents',
    namespace: 'bot.flair',
    author: 'Jaime Botero',
    description: 'Provides discovery and control capabilities for Flair Vent devices',
    category: 'Discovery',
    oauth: false,
    singleInstance: false
)
```

#### Import Statements
```groovy
import groovy.transform.Field
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.net.URLEncoder
```

All standard Groovy/Java imports. No Hubitat-specific or problematic imports.

#### Preferences Pages
The app defines 13 preference pages:
1. `mainPage` -- Primary setup (OAuth, discovery, DAB config, debug)
2. `flairControlPanel` -- Simple room-centric control panel
3. `flairControlPanel2` -- Styled control panel with CSS grid cards
4. `dabLiveDiagnosticsPage` -- Live DAB simulation
5. `efficiencyDataPage` -- Backup/restore efficiency data
6. `dabChartPage` -- 24-hour hourly DAB rates chart (QuickChart.io)
7. `dabRatesTablePage` -- Tabular hourly DAB rates
8. `dabActivityLogPage` -- HVAC mode transition log
9. `dabHistoryPage` -- Historical DAB data with date/mode filters
10. `dabProgressPage` -- Track DAB progress by date/hour/room
11. `dabDailySummaryPage` -- Daily airflow averages per room/mode
12. `quickControlsPage` -- Per-room manual control, bulk actions
13. `diagnosticsPage` -- Cache, errors, health check, raw cache export

#### Input Types Used
- `text` (clientId, structureId)
- `password` (clientSecret)
- `bool` (dabEnabled, debugOutput, enableDashboardTiles, nightOverrideEnable, allowFullClose, enableRawCache, enableEwma, enableOutlierRejection, fanOnlyOpenAllVents, thermostat1CloseInactiveRooms, useCachedRawForDab, failFastFinalization)
- `number` (pollingIntervalActive, pollingIntervalIdle, thermostat1AdditionalStandardVents, dabHistoryRetentionDays, minVentFloorPercent, nightOverridePercent, rawDataRetentionHours, ewmaHalfLifeDays, outlierThresholdMad, adaptiveLookbackPeriods, etc.)
- `enum` (thermostat1TempUnit, ventGranularity, debugLevel, outlierMode, dabHistoryFormat, chartHvacMode, tableHvacMode, historyHvacMode, progressHvacMode)
- `capability.thermostat` (thermostat1)
- `capability.temperatureMeasurement` (per-vent temperature sensors)
- `capability.switchLevel` (nightOverrideRooms)
- `button` (discoverDevices, validateNow, refreshHvacNow, applyNightOverrideNow, clearManualOverrides, syncVentTiles, exportDabHistory, runDabHistoryCheck, exportEfficiencyData, importEfficiencyData, clearExportData, openAll, closeAll, setManualAll, setAutoAll, applyQuickControlsNow, runHealthCheck, reauthenticate, resetCache, resyncVents, exportDiagnosticsNow, exportRawCacheNow, clearRawCacheNow, runDabDiagnostic, clearDabDataNow)
- `time` (nightOverrideStart, nightOverrideEnd)
- `date` (historyStart, historyEnd, progressStart, progressEnd)
- `textarea` (importJsonData)
- `mode` (not used in main app; used in ecobee app)

#### Constants (@Field static final)

**API/Network Constants:**
- `BASE_URL = 'https://api.flair.co'`
- `CONTENT_TYPE = 'application/json'`
- `HTTP_TIMEOUT_SECS = 5`
- `API_CALL_DELAY_MS = 3000` (throttle delay)
- `MAX_CONCURRENT_REQUESTS = 8`
- `MAX_API_RETRY_ATTEMPTS = 5`
- `API_FAILURE_THRESHOLD = 3` (circuit breaker trigger)

**HVAC Mode Constants:**
- `COOLING = 'cooling'`
- `HEATING = 'heating'`
- `PENDING_COOL = 'pending cool'`
- `PENDING_HEAT = 'pending heat'`

**Temperature/Rate Constants:**
- `SETPOINT_OFFSET = 0.7` (C)
- `MAX_TEMP_CHANGE_RATE = 1.5` (C/min)
- `MIN_TEMP_CHANGE_RATE = 0.001` (C/min)
- `TEMP_SENSOR_ACCURACY = 0.5` (C)
- `MIN_DETECTABLE_TEMP_CHANGE = 0.1` (C)
- `MIN_RUNTIME_FOR_RATE_CALC = 5` (minutes)
- `VENT_PRE_ADJUST_THRESHOLD = 0.2` (C)
- `THERMOSTAT_HYSTERESIS = 0.6` (C)
- `DUCT_TEMP_DIFF_THRESHOLD = 0.5` (C)

**Vent Constants:**
- `MIN_PERCENTAGE_OPEN = 0.0`
- `MAX_PERCENTAGE_OPEN = 100.0`
- `MIN_COMBINED_VENT_FLOW = 30.0` (safety floor %)
- `INCREMENT_PERCENTAGE = 1.5` (airflow adjustment step)
- `MAX_STANDARD_VENTS = 15`
- `MAX_ITERATIONS = 500` (adjustment loop limit)
- `STANDARD_VENT_DEFAULT_OPEN = 50` (%)
- `VENT_VERIFY_DELAY_MS = 5000`
- `MAX_VENT_VERIFY_ATTEMPTS = 3`

**Timing Constants:**
- `MAX_MINUTES_TO_SETPOINT = 60`
- `MIN_MINUTES_TO_SETPOINT = 1`
- `TEMP_READINGS_DELAY_MS = 30000`
- `POLLING_INTERVAL_ACTIVE = 3` (minutes)
- `POLLING_INTERVAL_IDLE = 10` (minutes)
- `INITIALIZATION_DELAY_MS = 3000`
- `POST_STATE_CHANGE_DELAY_MS = 1000`

**Cache Constants:**
- `ROOM_CACHE_DURATION_MS = 30000`
- `DEVICE_CACHE_DURATION_MS = 30000`
- `MAX_CACHE_SIZE = 50`
- `DEFAULT_HISTORY_RETENTION_DAYS = 10`
- `DAILY_SUMMARY_PAGE_SIZE = 30`
- `RAW_CACHE_DEFAULT_HOURS = 24`
- `RAW_CACHE_MAX_ENTRIES = 20000`

**Adaptive DAB Constants:**
- `ADAPTIVE_BOOST_ENABLED = true`
- `ADAPTIVE_LOOKBACK_PERIODS = 3`
- `ADAPTIVE_THRESHOLD_PERCENT = 25.0`
- `ADAPTIVE_BOOST_PERCENT = 12.5`
- `ADAPTIVE_MAX_BOOST_PERCENT = 25.0`

**Default Setpoints:**
- `DEFAULT_COOLING_SETPOINT_C = 24.0`
- `DEFAULT_HEATING_SETPOINT_C = 20.0`
- `REBALANCING_TOLERANCE = 0.5` (C)
- `TEMP_BOUNDARY_ADJUSTMENT = 0.1` (C)

#### Property Overrides (Test/CI Compatibility Layer)

Lines 31-101: Custom `getProperty()` and `setProperty()` overrides for `settings`, `location`, and `atomicState`. These provide fallback maps (`__atomicStateFallback`, `__settingsFallback`, `__locationFallback`) so the app can run in CI/test environments where Hubitat platform objects are unavailable.

Helper methods:
- `mirrorAdvancedSettingsFromMap(Map)` -- Copies DAB settings from a settings map into atomicState
- `readOptionalSettingValue(String key, def defaultValue)` -- Safe settings reader with multiple fallback paths
- `readOptionalBooleanSetting(String key, boolean defaultValue)` -- Boolean parser
- `readOptionalIntegerSetting(String key, Integer defaultValue)` -- Integer parser
- `isRawCacheEnabledSetting()` -- Checks if raw data cache is enabled

#### Methods -- Complete Inventory

##### Lifecycle Methods
- `installed()` -- Calls initializeDabHistory() + initialize()
- `updated()` -- Clears cached table HTML, calls initializeDabHistory() + initialize()
- `uninstalled()` -- Calls removeChildren(), unschedule(), unsubscribe()
- `initialize()` -- Main initialization: schedules health monitor, subscribes to thermostat events, sets up DAB polling, cache cleanup, tile subscriptions, night override schedules

##### Authentication Methods
- `authenticate(int retryCount)` -- Async OAuth2 client_credentials grant to `${BASE_URL}/oauth2/token`
- `handleAuthResponse(resp, data)` -- Processes auth response, stores token in `state.flairAccessToken`
- `retryAuthenticateWrapper(data)` -- Retry wrapper for runInMillis
- `autoAuthenticate()` -- Auto-auth when credentials present but no token
- `autoReauthenticate()` -- Re-auth on token expiration (401/403)
- `login()` -- authenticate() + getStructureData()

##### API Communication Methods
- `getDataAsync(String uri, String callback, data, int retryCount)` -- Async GET with throttling, retry, exponential backoff
- `patchDataAsync(String uri, String callback, body, data, int retryCount)` -- Async PATCH with throttling, retry
- `retryGetDataAsyncWrapper(data)` -- GET retry wrapper
- `retryPatchDataAsyncWrapper(data)` -- PATCH retry wrapper
- `asyncHttpCallback(response, Map data)` -- Centralized async callback dispatcher, always decrements active requests
- `asyncHttpGetWrapper(resp, Map data)` -- Legacy CI/test shim
- `isValidResponse(resp)` -- Validates HTTP response, triggers re-auth on 401/403
- `noOpHandler(resp, data)` -- No-op callback for fire-and-forget patches

##### Request Throttling/Circuit Breaker
- `canMakeRequest()` -- Checks if under MAX_CONCURRENT_REQUESTS; auto-resets stuck counters
- `incrementActiveRequests()` -- Increments atomicState.activeRequests
- `decrementActiveRequests()` -- Decrements (floor at 0)
- `initRequestTracking()` -- Ensures activeRequests exists
- `ensureFailureCounts()` -- Ensures failureCounts map exists
- `incrementFailureCount(String uri)` -- Tracks per-URI failures; triggers circuit breaker at threshold
- `resetApiConnection()` -- Clears failure counts, re-authenticates

##### Structure/Discovery Methods
- `getStructureId()` -- Returns settings.structureId, fetches if missing
- `getStructureData(int retryCount)` -- Synchronous (httpGet) structure fetch with retry
- `getStructureDataAsync(int retryCount)` -- Async structure fetch
- `handleStructureResponse(resp, data)` -- Processes structure response
- `retryGetStructureDataWrapper(data)` -- Sync retry wrapper
- `retryGetStructureDataAsyncWrapper(data)` -- Async retry wrapper
- `discover()` -- Initiates device discovery (vents + pucks from multiple endpoints)
- `handleDeviceList(resp, data)` -- Processes discovered vents/pucks
- `handleAllPucks(resp, data)` -- Processes pucks from /api/pucks
- `handleRoomsWithPucks(resp, data)` -- Processes pucks from rooms/include endpoint
- `makeRealDevice(Map device)` -- Creates child device (vent or puck) via addChildDevice

##### Device Data Methods
- `getDeviceData(device)` -- Refresh device: gets readings and room data with caching
- `getRoomDataWithCache(device, deviceId, isPuck)` -- Room data with LRU cache
- `getDeviceDataWithCache(device, deviceId, deviceType, callback)` -- Device data with cache
- `getDeviceReadingWithCache(device, deviceId, deviceType, callback)` -- Reading data with cache
- `handleRoomGet(resp, data)` -- Processes room response (uncached)
- `handleRoomGetWithCache(resp, data)` -- Processes room response + caches
- `handleDeviceGet(resp, data)` -- Processes vent reading (uncached)
- `handleDeviceGetWithCache(resp, data)` -- Processes vent reading + caches
- `handlePuckGet(resp, data)` -- Processes puck attributes (temp, humidity, battery, voltage)
- `handlePuckGetWithCache(resp, data)` -- Puck data + cache
- `handlePuckReadingGet(resp, data)` -- Processes puck current-reading
- `handlePuckReadingGetWithCache(resp, data)` -- Puck reading + cache
- `handleRemoteSensorGet(resp, data)` -- Processes occupancy from remote sensors
- `traitExtract(device, details, String propNameData, String propNameDriver, unit)` -- Extracts attribute from API response and sends event
- `processVentTraits(device, details)` -- Maps all vent API attributes to device events
- `processRoomTraits(device, details)` -- Maps all room API attributes to device events
- `updateByRoomIdState(details)` -- Updates atomicState.ventsByRoomId mapping

##### Vent Patching Methods
- `patchVent(device, percentOpen)` -- Public entry: applies manual overrides, delegates to patchVentDevice
- `patchVentDevice(device, percentOpen, attempt)` -- Sends PATCH to Flair API, schedules verification
- `handleVentPatch(resp, data)` -- Processes vent patch response, updates local state
- `verifyVentPercentOpen(data)` -- Reads vent current-reading to verify position
- `handleVentVerify(resp, data)` -- Verifies and retries if vent didn't reach target
- `patchRoom(device, active)` -- Sets room active/inactive via API
- `handleRoomPatch(resp, data)` -- Processes room patch response
- `patchRoomSetPoint(device, temp)` -- Sets room setpoint via API (converts F->C if needed)
- `handleRoomSetPointPatch(resp, data)` -- Processes setpoint patch response
- `patchStructureData(Map attributes)` -- Patches structure attributes

##### Instance-Based Caching Infrastructure
- `getCurrentTime()` -- Returns now()
- `getInstanceId()` -- Returns app.getId() or test fallback
- `initializeInstanceCaches()` -- Initializes per-instance cache maps in state
- `cacheRoomData(String roomId, Map roomData)` -- LRU room cache (max 50 entries)
- `getCachedRoomData(String roomId)` -- Retrieve from room cache
- `getRoomCacheSize()` -- Returns room cache size
- `cacheRoomDataWithTimestamp(String roomId, Map roomData, Long timestamp)` -- Test helper
- `isCacheExpired(String roomId)` -- Checks 30s expiry
- `markRequestPending(String requestId)` / `isRequestPending()` / `clearPendingRequest()` -- Room request dedup
- `cacheDeviceReading(String deviceKey, Map deviceData)` -- LRU device reading cache
- `getCachedDeviceReading(String deviceKey)` -- Retrieve from device cache
- `isDeviceRequestPending()` / `markDeviceRequestPending()` / `clearDeviceRequestPending()` -- Device request dedup
- `clearInstanceCache()` -- Clears all caches
- `clearRoomCache()` -- Periodic cleanup of expired room cache entries (scheduled every 10 min)
- `clearDeviceCache()` -- Periodic cleanup of expired device cache entries (scheduled every 5 min)
- `cleanupPendingRequests()` -- Resets stuck pending flags and request counters (scheduled every 5 min)
- `resetCaches()` -- Removes all instance cache state keys

##### Raw DAB Data Cache (24h Rolling)
- `getRawCacheRetentionHours()` -- Returns retention hours (1-48, default 24)
- `getRawCacheCutoffTs()` -- Calculates cutoff timestamp
- `appendRawDabSample(List entry)` -- Appends raw sample [ts, ventId, roomId, hvacMode, ductC, roomC, pct, active, rSet]
- `getLatestRawSample(String ventId)` -- O(1) lookup from last-by-vent index
- `pruneRawCache()` -- Removes expired entries (scheduled hourly)
- `sampleRawDabData()` -- Samples all vents (scheduled every minute when enabled)
- `buildRawCacheJson()` -- Export raw cache as JSON
- `clearRawCache()` -- Deletes raw cache state

##### HVAC State Detection
- `calculateHvacMode(BigDecimal temp, BigDecimal coolingSetpoint, BigDecimal heatingSetpoint)` -- Parameter-based inference with setpoint offset
- `calculateHvacMode()` -- Zero-arg convenience overload
- `calculateHvacModeRobust()` -- Median duct-room temperature difference with thermostat fallback
- `updateHvacStateFromDuctTemps()` -- Periodic (every minute) HVAC state evaluator; triggers DAB cycle start/stop
- `isFanActive(String opState)` -- Detects fan-only mode from thermostat or HVAC idle state
- `isThermostatAboutToChangeState(String hvacMode, BigDecimal setpoint, BigDecimal temp)` -- Pre-adjustment trigger

##### Thermostat Event Handlers
- `thermostat1ChangeTemp(evt)` -- Handles temperature/setpoint changes with hysteresis filtering
- `thermostat1ChangeStateHandler(evt)` -- Handles operating state changes (cooling/heating/idle/fan)

##### DAB Core Algorithm Methods
- `initializeRoomStates(String hvacMode)` -- Seeds per-room rates from hourly history, calculates vent positions
- `finalizeRoomStates(data)` -- End-of-cycle: calculates new rates from temperature changes, updates history
- `recordStartingTemperatures()` -- Records room temps at cycle start
- `reBalanceVents()` -- Mid-cycle rebalancing (scheduled every 30 min during active HVAC)
- `evaluateRebalancingVents()` -- Checks if any room reached setpoint early (scheduled every 5 min)
- `calculateVentOpenPercentage(roomName, startTemp, setpoint, hvacMode, maxRate, longestTime)` -- Exponential model: `0.0991 * exp((targetRate/maxRate) * 2.3) * 100`
- `calculateOpenPercentageForAllVents(rateAndTempPerVentId, hvacMode, setpoint, longestTime, closeInactive)` -- Iterates all vents, applies per-room setpoints
- `calculateLongestMinutesToTarget(rateAndTempPerVentId, hvacMode, setpoint, maxRunningTime, closeInactive)` -- Finds the room that takes longest to reach setpoint
- `calculateRoomChangeRate(lastStartTemp, currentTemp, totalMinutes, percentOpen, currentRate)` -- Computes adjusted rate: `(rate/maxRate) / (percentOpen/100)`
- `_validateRateCalculationInputs()` -- Validates inputs for rate calc
- `_isTemperatureChangeSignificant()` -- Checks if temp change exceeds noise threshold
- `_handleInsignificantTemperatureChange()` -- Returns MIN_TEMP_CHANGE_RATE or -1
- `_adjustForSensorAccuracy()` -- Adjusts for sensor accuracy range
- `_clampAndCleanRate()` -- Clamps rate to [MIN, MAX] range
- `adjustVentOpeningsToEnsureMinimumAirflowTarget(rateAndTempPerVentId, hvacMode, calculatedPercentOpen, additionalStandardVents)` -- Ensures combined airflow >= 30%
- `applyOverridesAndFloors(Map calc)` -- Applies manual overrides and minimum vent floor

##### DAB Data Collection/Retrieval
- `getAttribsPerVentId(ventsByRoomId, hvacMode)` -- Gets rate/temp/active per vent
- `getAttribsPerVentIdWeighted(ventsByRoomId, hvacMode)` -- Same but applies per-vent weight bias
- `getRoomTemp(def vent)` -- Gets room temperature (from cached raw, assigned sensor, or room API)
- `getThermostatSetpoint(String hvacMode)` -- Gets setpoint from thermostat with offset
- `getGlobalSetpoint(String hvacMode)` -- Falls back to median room setpoints when no thermostat

##### DAB History & Hourly Rates
- `initializeDabHistory()` -- Ensures atomicState.dabHistory is normalized (entries + hourlyRates)
- `getHourlyRates(String roomId, String hvacMode, Integer hour)` -- Gets all stored rates for a room/mode/hour
- `getAverageHourlyRate(String roomId, String hvacMode, Integer hour)` -- Average with EWMA, carry-forward, and adaptive boost
- `getLastObservedHourlyRate(String roomId, String hvacMode, Integer hour)` -- Most recent rate
- `appendHourlyRate(String roomId, String hvacMode, Integer hour, BigDecimal rate)` -- Appends to both hourlyRates index and flat entries
- `appendDabHistory(String roomId, String hvacMode, Integer hour, BigDecimal rate)` -- Appends to flat entries list
- `reindexDabHistory()` -- Rebuilds hourlyRates index from entries, recomputes daily stats
- `aggregateDailyDabStats()` -- Aggregates previous day's hourly rates into daily averages (scheduled daily)
- `appendDabActivityLog(String message)` -- Appends to activity log (last 100) and archive (last 1000)
- `readDabHistoryArchive()` -- Reads structured archive
- `checkDabHistoryIntegrity()` -- Checks legacy history for missing hours
- `recordHistoryError(String message)` -- Records history errors
- `getRetentionDays()` -- Returns configured retention (default 10)

##### EWMA & Outlier Handling
- `getEwmaRate(roomId, hvacMode, hour)` -- Retrieves EWMA-smoothed rate
- `updateEwmaRate(roomId, hvacMode, hour, newRate)` -- Updates EWMA: `alpha * new + (1-alpha) * prev`
- `computeEwmaAlpha()` -- Computes alpha from half-life: `1 - 2^(-1/N)`
- `assessOutlierForHourly(roomId, hvacMode, hour, candidate)` -- MAD-based outlier detection (clip or reject)

##### Adaptive Boost
- `getAdaptiveBoostFactor(roomId, hvacMode, hour)` -- Computes boost from recent large upward corrections
- `appendAdaptiveMark(roomId, hvacMode, hour, ratio)` -- Records adaptive adjustment vs seeded rate

##### Dashboard Tiles
- `tileDniForVentId(String ventId)` -- Returns `"tile-${ventId}"`
- `syncVentTiles()` -- Creates tile child devices for each vent
- `subscribeToVentEventsForTiles()` -- Subscribes to vent events for real-time tile updates
- `updateTileForEvent(evt)` -- Event-driven tile refresh
- `refreshVentTiles()` -- Refreshes all tiles (scheduled every 5 min)
- `updateTileForVent(device)` -- Builds HTML card with progress bar, temp, battery, mode

##### Manual Override / Night Override
- `activateNightOverride()` -- Sets manual overrides for selected rooms at configured percent
- `deactivateNightOverride()` -- Removes manual overrides for night rooms
- `clearAllManualOverrides()` -- Clears all manual overrides
- `tileSetVentPercent(String tileDni, Integer percent)` -- Tile callback: set vent percent + manual override
- `tileSetManualMode(String tileDni)` -- Tile callback: lock current position as manual
- `tileSetAutoMode(String tileDni)` -- Tile callback: remove manual override

##### Quick Controls
- `applyQuickControls()` -- Processes all qc_ settings inputs for percent/setpoint/active
- `openAllSelected(Integer pct)` -- Bulk open/close with manual override stickiness
- `manualAllEditedVents()` -- Sets manual mode for all edited vents
- `quickControlsPage()` -- Renders the quick controls UI

##### Polling Control
- `updateDevicePollingInterval(Integer intervalMinutes)` -- Updates polling for all child vents and pucks

##### Efficiency Data Export/Import
- `exportEfficiencyData()` -- Collects global rates, per-room rates, history, activity log
- `generateEfficiencyJSON(data)` -- Wraps with metadata (version, date, structureId)
- `handleExportEfficiencyData()` -- Button handler for export
- `handleImportEfficiencyData()` -- Button handler for import
- `importEfficiencyData(jsonContent)` -- Parses JSON, validates, applies
- `validateImportData(jsonData)` -- Validates structure, rate bounds (0-10)
- `applyImportedEfficiencies(efficiencyData)` -- Updates global rates, per-room rates, history, activity log
- `matchDeviceByRoomId(roomId)` -- Finds device by room-id attribute
- `matchDeviceByRoomName(roomName)` -- Finds device by room-name attribute
- `handleClearExportData()` -- Clears export state
- `handleClearDabData()` -- Resets all DAB data and rates
- `normalizeImportInput(rawValue)` -- Handles UnvalidatedInput from hubitat_ci
- `normalizeImportErrorMessage(String message)` -- Normalizes error messages
- `readUserSettingsMapForImport()` -- Reflective access to userSettingsMap field
- `isVentDeviceForImport(device)` -- Safe vent detection for import
- `getChildDevicesForImport()` -- Robust child device access with multiple fallbacks
- `readDeviceAttrForImport(device, String attr)` -- Safe attribute read
- `readDeviceIdForImport(device)` -- Safe device ID read

##### DAB History Export
- `exportDabHistory(String format)` -- Exports as JSON or CSV
- `handleExportDabHistory()` -- Button handler

##### Chart/Table Building
- `buildDabChart()` -- Builds QuickChart.io line chart URL from hourly rates
- `buildDabRatesTable()` -- HTML table of hourly rates per room (String return)
- `buildDabRatesTable(Map data)` -- Async-friendly wrapper that caches result
- `buildDabProgressTable()` -- HTML table of rates by date/hour for selected room
- `buildDabProgressTable(Map data)` -- Async wrapper
- `buildDabDailySummaryTable()` -- Paginated daily summary table

##### Utility Methods
- `roundBigDecimal(BigDecimal number, int scale)` -- Rounds with HALF_UP
- `roundToDecimalPlaces(def value, int decimalPlaces)` -- Double-based rounding
- `cleanDecimalForJson(def value)` -- Aggressive rounding for JSON serialization (9 decimal places)
- `roundToNearestMultiple(BigDecimal num)` -- Rounds to configured granularity (5/10/25/50/100%)
- `convertFahrenheitToCentigrade(BigDecimal tempValue)` -- F to C conversion
- `rollingAverage(BigDecimal currentAverage, BigDecimal newNumber, BigDecimal weight, int numEntries)` -- Weighted rolling average
- `hasRoomReachedSetpoint(String hvacMode, BigDecimal setpoint, BigDecimal currentTemp, BigDecimal offset)` -- Setpoint comparison with direction awareness
- `removeChildren()` -- Deletes all child devices
- `atomicStateUpdate(String stateKey, String key, value)` -- Safely updates nested atomicState maps
- `cleanupExistingDecimalPrecision()` -- Fixes BigDecimal precision issues in stored data
- `initializeDabTracking()` -- Ensures dabHistory and dabActivityLog exist

##### Logging Methods
- `log(int level, String module, String msg, String correlationId)` -- Debug logger with level filtering
- `logError(String msg, String module, String correlationId)` -- Error logger, maintains recentErrors list (last 20)
- `logWarn(String msg, String module, String correlationId)` -- Warning logger
- `logDetails(String msg, details, int level)` -- Detailed debug logger
- `logVentDecision(Map entry)` -- Records vent decision trace (last 60)

##### Validation
- `validatePreferences()` -- Validates clientId and clientSecret
- `performValidationTest()` -- Tests API connectivity
- `performHealthCheck()` -- Checks auth token, API reachability, vent count

##### Diagnostics
- `buildDiagnosticsJson()` -- JSON snapshot of system state
- `dabHealthMonitor()` -- Periodic health checks (every 5 min)
- `runDabDiagnostic()` -- Live diagnostic pass of DAB calculations
- `renderDabDiagnosticResults()` -- Renders diagnostic HTML

##### Miscellaneous
- `appButtonHandler(String btn)` -- Routes button presses
- `safeSendEvent(device, Map eventData)` -- sendEvent wrapper for test safety
- `getThermostat1Mode()` -- Safe getter from atomicState
- `isDabEnabled()` -- CI-safe read of dabEnabled toggle
- `logValidationFailure(String field, String reason)` -- Structured validation failure log
- `getRoomDataForPanel()` -- JSON string of room data for panels
- `listDiscoveredDevices()` -- HTML table of discovered devices with efficiency

#### State and atomicState Usage

**state (non-concurrent, UI/persistence):**
- `state.flairAccessToken` -- OAuth access token
- `state.authInProgress` -- Authentication in progress flag
- `state.authError` -- Last auth error message
- `state.recentErrors` -- List of last 20 errors
- `state.recentLogs` -- List of last 50 log entries
- `state.recentVentDecisions` -- List of last 60 vent decision traces
- `state.healthCheckResults` -- Last health check result
- `state.lastValidationResult` -- Last validation test result
- `state.ventOpenDiscrepancies` -- Map of vents that failed to reach target
- `state.ventPatchDiscrepancies` -- Legacy discrepancy map
- `state.exportStatus` / `state.exportedJsonData` -- Export results
- `state.importStatus` / `state.importSuccess` -- Import results
- `state.dabHistoryExportStatus` / `state.dabHistoryExportData` -- DAB history export
- `state.dabHistoryCheckStatus` -- Reindex result
- `state.dabRatesTableHtml` -- Cached rates table HTML
- `state.dabProgressTableHtml` -- Cached progress table HTML
- `state.dabDiagnosticResult` -- Live diagnostic results
- `state.diagnosticsJson` -- Exported diagnostics JSON
- `state.rawCacheJson` -- Exported raw cache JSON
- `state.clearDabStatus` -- Clear DAB result message
- `state.circuitOpenUntil` -- Map of URI -> circuit breaker expiry timestamps
- `state.qcDeviceMap` / `state.qcRoomMap` -- Quick controls ID mappings
- `state.instanceCache_<id>_*` -- Per-instance cache maps (room, device, timestamps, pending flags)

**atomicState (concurrent-safe):**
- `atomicState.thermostat1State` -- Current HVAC cycle state {mode, startedRunning, finishedRunning, startedCycle}
- `atomicState.thermostat1Mode` -- Structure mode (manual/auto)
- `atomicState.ventsByRoomId` -- Map of roomId -> List of ventIds
- `atomicState.maxCoolingRate` / `atomicState.maxHeatingRate` -- Global max rates
- `atomicState.maxHvacRunningTime` -- Rolling average of HVAC run times
- `atomicState.dabHistory` -- Map: {entries: [flat list], hourlyRates: {nested index}}
- `atomicState.dabActivityLog` -- List of activity log strings (last 100)
- `atomicState.dabHistoryArchive` -- Structured archive (last 1000)
- `atomicState.dabHistoryErrors` -- List of history error messages
- `atomicState.dabHistoryStartTimestamp` -- First entry timestamp
- `atomicState.dabDailyStats` -- Aggregated daily statistics
- `atomicState.dabEwma` -- EWMA smoothed rates per room/mode/hour
- `atomicState.lastHvacMode` -- Last known HVAC mode
- `atomicState.hvacCurrentMode` / `atomicState.hvacLastMode` / `atomicState.hvacLastChangeTs` -- HVAC transition tracking
- `atomicState.activeRequests` -- Current HTTP request count
- `atomicState.failureCounts` -- Per-URI failure counts
- `atomicState.lastSignificantTemp` -- Hysteresis tracking
- `atomicState.tempDiffsInsideThreshold` -- Pre-adjustment flag
- `atomicState.lastRebalanceTime` -- Prevents rapid rebalancing
- `atomicState.currentPollingInterval` -- Current polling interval
- `atomicState.manualOverrides` -- Map of ventId -> override percent
- `atomicState.lastSeededRate` -- Seeded rates for adaptive analysis
- `atomicState.seededHour` -- Hour when rates were seeded
- `atomicState.adaptiveMarksEntries` -- Adaptive adjustment marks (last 5000)
- `atomicState.rawDabSamplesEntries` -- Raw 24h data cache
- `atomicState.rawDabLastByVent` -- Latest raw sample per vent
- `atomicState.dabHistoryRetentionDays` -- Mirrored setting
- `atomicState.enableEwma` / `atomicState.ewmaHalfLifeDays` -- Mirrored EWMA settings
- `atomicState.enableOutlierRejection` / `atomicState.outlierThresholdMad` / `atomicState.outlierMode` -- Mirrored outlier settings
- `atomicState.enableRawCache` / `atomicState.rawDataRetentionHours` -- Mirrored raw cache settings
- `atomicState.carryForwardLastHour` -- Mirrored setting
- `atomicState.enableAdaptiveBoost` / `atomicState.adaptiveLookbackPeriods` / etc. -- Mirrored adaptive settings
- `atomicState.verboseLogging` -- Verbose logging flag
- `atomicState.dabEnabled` -- Mirrored DAB enabled flag
- `atomicState.prev_tableHvacMode` / `atomicState.prev_progressRoom` / etc. -- UI state caching for HTML invalidation
- `atomicState.progressRoom` -- Mirrored progress room selection
- `atomicState.chartHvacMode` -- Mirrored chart mode selection

#### Event Subscriptions
- `thermostat, 'thermostatOperatingState'` -> `thermostat1ChangeStateHandler`
- `thermostat, 'temperature'` -> `thermostat1ChangeTemp`
- `thermostat, 'coolingSetpoint'` -> `thermostat1ChangeTemp`
- `thermostat, 'heatingSetpoint'` -> `thermostat1ChangeTemp`
- `vent, 'percent-open'` -> `updateTileForEvent` (when tiles enabled)
- `vent, 'room-current-temperature-c'` -> `updateTileForEvent`
- `vent, 'level'` -> `updateTileForEvent`
- `vent, 'room-name'` -> `updateTileForEvent`

#### Scheduling
- `runEvery5Minutes('dabHealthMonitor')` -- Health monitoring
- `runEvery1Hour('login')` -- Token refresh
- `runEvery1Minute('updateHvacStateFromDuctTemps')` -- HVAC state detection (when DAB enabled)
- `runEvery1Day('aggregateDailyDabStats')` -- Daily aggregation
- `runEvery1Minute('sampleRawDabData')` -- Raw data sampling (when raw cache enabled)
- `runEvery1Hour('pruneRawCache')` -- Raw cache cleanup
- `runEvery5Minutes('cleanupPendingRequests')` -- Stuck request cleanup
- `runEvery10Minutes('clearRoomCache')` -- Room cache cleanup
- `runEvery5Minutes('clearDeviceCache')` -- Device cache cleanup
- `runEvery5Minutes('refreshVentTiles')` -- Tile refresh (when tiles enabled)
- `runEvery5Minutes('evaluateRebalancingVents')` -- Rebalancing check (during active HVAC)
- `runEvery30Minutes('reBalanceVents')` -- Full rebalance (during active HVAC)
- `schedule(nightOverrideStart, 'activateNightOverride')` -- Night override start
- `schedule(nightOverrideEnd, 'deactivateNightOverride')` -- Night override end
- Various `runInMillis()` and `runIn()` for delayed operations

#### HTTP API Calls

**Endpoints Used:**
- `POST /oauth2/token` -- OAuth2 client_credentials authentication
- `GET /api/structures` -- Get home structures
- `GET /api/structures/{id}/vents` -- Discover vents
- `GET /api/structures/{id}/pucks` -- Discover pucks
- `GET /api/structures/{id}/rooms?include=pucks` -- Rooms with puck includes
- `GET /api/pucks` -- All pucks (direct)
- `GET /api/vents/{id}/current-reading` -- Vent current reading
- `GET /api/pucks/{id}` -- Puck details
- `GET /api/pucks/{id}/current-reading` -- Puck current reading
- `GET /api/vents/{id}/room` -- Room data for vent
- `GET /api/pucks/{id}/room` -- Room data for puck
- `GET /api/remote-sensors/{id}/sensor-readings` -- Occupancy data
- `PATCH /api/vents/{id}` -- Update vent percent-open
- `PATCH /api/rooms/{id}` -- Update room active status or set-point
- `PATCH /api/structures/{id}` -- Update structure mode

**Authentication**: OAuth 2.0 client_credentials grant. Scopes: `vents.view vents.edit structures.view structures.edit pucks.view pucks.edit`

**Request/Response Pattern**: JSON:API format (`{ data: { type: '...', attributes: {...}, relationships: {...} } }`)

**Retry Logic**: Exponential backoff (`2^retryCount * API_CALL_DELAY_MS`), max 5 retries. Circuit breaker after 3 consecutive failures per URI (5-minute cooldown).

#### Error Handling Patterns
- All HTTP callbacks wrapped in try/catch
- `isValidResponse()` validates responses, triggers re-auth on 401/403
- `decrementActiveRequests()` always called in finally blocks
- Safe navigation (`?.`) used extensively
- `logError()` maintains rotating error list (last 20)
- Circuit breaker pattern for API failures
- Stuck request counter auto-reset
- Test-safe wrappers for sendEvent, getChildDevices, etc.

#### TODO/FIXME/HACK Comments
- Line 29: `//attribute "percent-open-reason", "string"` -- commented-out attribute in driver
- Line 4559: `// enforceMonotonicVentOpenings removed by request`
- Line 4511: `// Monotonic ordering removed by request; rely on learned efficiency`
- Various `// CI-safe:` comments indicating test compatibility workarounds

#### Code Quality Observations
1. **Very large monolith**: The main app is ~6734 lines in a single file. The architecture.md mentions DabManager/DabUIManager libraries (v0.240.0) but these are not present in the source -- the app is a single monolithic file.
2. **Duplicate code**: `initializeInstanceCaches()` has duplicate lines 1569-1573 (activeRequests and circuitOpenUntil initialization repeated).
3. **One synchronous HTTP call remains**: `getStructureData()` uses `httpGet()` (synchronous) despite the async-only policy stated in the README. The async variant `getStructureDataAsync()` exists but `getStructureData()` is still called from `login()`.
4. **State bloat risk**: The app stores large data structures in atomicState (dabHistory entries, raw cache up to 20000 entries, adaptive marks up to 5000). These could exceed Hubitat's state storage limits.
5. **Inconsistent method visibility**: Mix of `def`, `private`, and `void` method declarations without consistent access control.
6. **Double decrement risk**: `asyncHttpCallback()` always calls `decrementActiveRequests()` in its finally block, but individual handlers (handleDeviceGet, handleRoomGet, etc.) also call `decrementActiveRequests()`. This could lead to double-decrement. The design seems to be that the centralized callback handles it, but individual handlers called directly (not through asyncHttpCallback) also decrement.

---

### 3.2 hubitat-flair-vents-driver.groovy (Vent Driver)

**Type**: Hubitat Device Driver
**Lines**: ~185
**Version**: 0.234

#### Definition
```groovy
definition(name: 'Flair vents', namespace: 'bot.flair', author: 'Jaime Botero')
```

#### Capabilities
- `Refresh`
- `SwitchLevel`
- `VoltageMeasurement`

#### Custom Attributes (42 total)
- Vent-specific: rssi, connected-gateway-name, has-buzzed, updated-at, inactive, created-at, percent-open, setup-lightstrip, motor-overdrive-ms, duct-temperature-c, duct-pressure, firmware-version-s, motor-run-time, motor-current
- Room-specific: structure-id, room-id, room-name, room-current-temperature-c, room-starting-temperature-c, room-conclusion-mode, room-humidity-away-min, room-type, room-temp-away-min-c, room-level, room-hold-until, room-away-mode, room-heat-cool-mode, room-updated-at, room-state-updated-at, room-set-point-c, room-hold-until-schedule-event, room-frozen-pipe-pet-protect, room-created-at, room-windows, room-air-return, room-current-humidity, room-hold-reason, room-occupancy-mode, room-temp-away-max-c, room-humidity-away-max, room-preheat-precool, room-active, room-set-point-manual, room-pucks-inactive, room-occupied, room-cooling-rate, room-heating-rate

#### Custom Commands
- `setRoomActive(active)` -- Sets room active/away via parent
- `setRoomSetPoint(temperature)` -- Sets room setpoint via parent

#### Preferences
- `devicePoll` (number, default 3 minutes)
- `debugOutput` (bool)
- `verboseLogging` (bool)

#### Methods
- `log(level, module, msg, correlationId)` -- Multi-level logger with verbose state logging
- `setRefreshSchedule()` -- Cron schedule: `0 0/${devicePoll} * 1/1 * ? *`
- `installed()` / `updated()` / `uninstalled()` / `initialize()` / `refresh()` -- Lifecycle
- `settingsRefresh()` -- Calls `parent.getDeviceData(device)` then `parent.updateHvacStateFromDuctTemps()`
- `setLevel(level, duration)` -- Calls `parent.patchVent(device, level)`
- `getLastEventTime()` -- Returns state.lastEventTime
- `setDeviceState(attr, value)` / `getDeviceState(attr)` -- State get/set helpers
- `setRoomActive(isActive)` -- Calls `parent.patchRoom(device, isActive)`
- `setRoomSetPoint(temp)` -- Calls `parent.patchRoomSetPoint(device, temp)`
- `updateParentPollingInterval(Integer intervalMinutes)` -- Updates polling from parent request

**Parent-Child Communication**: All data fetching and API calls go through parent methods. The driver is a thin shell.

---

### 3.3 hubitat-flair-vent-tile-driver.groovy (Tile Driver)

**Type**: Hubitat Device Driver (Virtual)
**Lines**: ~60
**Version**: Not specified
**Author**: Codex

#### Definition
```groovy
definition(name: 'Flair Vent Tile', namespace: 'bot.flair', author: 'Codex')
```

#### Capabilities
- `Sensor`, `Actuator`, `Refresh`, `SwitchLevel`, `TemperatureMeasurement`

#### Custom Attributes
- `html` (STRING) -- Dashboard HTML content

#### Commands
- `setManualMode()` -- Calls `parent.tileSetManualMode()`
- `setAutoMode()` -- Calls `parent.tileSetAutoMode()`
- `nudgeUp()` -- Increases level by 5%
- `nudgeDown()` -- Decreases level by 5%
- `setVentPercent(percent)` -- Calls `parent.tileSetVentPercent()`
- `setLevel(level)` -- Routes to setVentPercent

All commands wrapped in try/catch for error safety.

---

### 3.4 hubitat-flair-vents-pucks-driver.groovy (Puck Driver)

**Type**: Hubitat Device Driver
**Lines**: ~136
**Version**: 0.234

#### Definition
```groovy
definition(name: 'Flair pucks', namespace: 'bot.flair', author: 'Jaime Botero')
```

#### Capabilities
- `Refresh`, `TemperatureMeasurement`, `RelativeHumidityMeasurement`, `MotionSensor`, `Battery`, `VoltageMeasurement`

#### Custom Attributes (26 total)
- Puck-specific: current-rssi, rssi, firmware-version-s, inactive, created-at, updated-at, name, gateway-connected, light-level, air-pressure
- Room-specific: room-id, room-name, room-active, room-current-temperature-c, room-current-humidity, room-set-point-c, room-set-point-manual, room-heat-cool-mode, room-occupied, room-occupancy-mode, room-pucks-inactive, room-frozen-pipe-pet-protect, room-preheat-precool, room-humidity-away-min/max, room-temp-away-min/max-c, room-hold-reason, room-hold-until-schedule-event, room-created-at, room-updated-at, room-state-updated-at, structure-id

#### Commands
- `setRoomActive(active)` -- Sets room active/away via parent

#### Methods
Same pattern as vent driver: lifecycle methods, settingsRefresh (parent.getDeviceData), updateParentPollingInterval.

---

### 3.5 hubitat-ecobee-smart-participation.groovy (Ecobee App)

**Type**: Hubitat App (Standalone)
**Lines**: ~183

#### Definition
```groovy
definition(
    name: 'Ecobee Smart Participation',
    namespace: 'bot.ecobee.smart',
    author: 'Jaime Botero',
    description: 'Chooses the most critical sensors in sensor participation based on temperatures',
    category: 'Discovery',
    oauth: false, singleInstance: false
)
```

#### Import
```groovy
import groovy.transform.Field
@Field static String OCCUPIED = 'occupied'
```

#### Purpose
Automatically selects which Ecobee remote sensors participate in comfort programs based on temperature readings. During cooling, hottest sensors are included; during heating, coldest sensors are included.

#### Preferences
- thermostat (capability.thermostat)
- sensors (capability.temperatureMeasurement, multiple)
- programs (enum from thermostat programsList)
- range (enum: quarter/third/median)
- occupancyModes (mode)
- debugLevel (enum: 0-3)

#### Key Methods
- `thermostatChangeStateHandler(evt)` -- Triggers recalculation on operating state change
- `recalculateSensorParticipation()` -- Main logic: gets temps/occupancy, calls init
- `recalculateSensorParticipationInit(sensorsList, heatingSetpoint, coolingSetpoint, useOccupancy)` -- Algorithm: computes average temp, determines if cooling/heating, selects sensors within range
- `getThermostatPrograms()` -- Parses programsList JSON from thermostat

**Scheduling**: `runEvery1Hour('recalculateSensorParticipation')`

---

## 4. API Integration Summary

### External API: Flair Cloud API
- **Base URL**: `https://api.flair.co`
- **Auth**: OAuth 2.0 client_credentials (`/oauth2/token`)
- **Scopes**: vents.view, vents.edit, structures.view, structures.edit, pucks.view, pucks.edit
- **Format**: JSON:API (`{ data: { type, id, attributes, relationships } }`)
- **Timeout**: 5 seconds
- **Throttling**: Max 8 concurrent requests, 3-second delay between retries
- **Circuit Breaker**: 3 failures per URI -> 5-minute cooldown + re-auth
- **Token Refresh**: Hourly via `runEvery1Hour('login')`

### External Service: QuickChart.io
- Used for rendering DAB hourly rate charts
- Chart configuration encoded as JSON in URL parameter

---

## 5. Architecture Summary

### Parent-Child Relationships
```
Flair Vents App (Parent)
  |-- Flair vents (Child Device) x N
  |-- Flair pucks (Child Device) x N
  |-- Flair Vent Tile (Child Device, virtual) x N
```

### Data Flow
1. **Discovery**: App -> Flair API -> Create child devices
2. **Polling**: Driver schedule -> parent.getDeviceData() -> Flair API (async) -> processTraits -> sendEvent to driver
3. **DAB Cycle Start**: HVAC detected (duct temps or thermostat event) -> recordStartingTemperatures -> initializeRoomStates -> calculateVentOpenPercentage -> patchVent -> Flair API
4. **DAB Cycle End**: HVAC idle detected -> finalizeRoomStates -> calculateRoomChangeRate -> appendHourlyRate -> update rates on devices
5. **Rebalancing**: Every 5 min check + every 30 min full rebalance during active HVAC
6. **Manual Control**: User -> driver setLevel/setRoomActive -> parent.patchVent/patchRoom -> Flair API

### Key Algorithm: DAB Vent Position Calculation
```
targetRate = |setpoint - currentTemp| / longestTimeToTarget
percentOpen = 0.0991 * exp((targetRate / roomMaxRate) * 2.3) * 100
```
Then clamped to [0, 100], rounded to granularity, adjusted for minimum airflow (30%), and overrides/floors applied.

### Key Algorithm: Room Change Rate Learning
```
diffTemps = |startTemp - endTemp|
rate = diffTemps / totalMinutes
approxRate = (rate / maxRate) / (percentOpen / 100)
newAverage = rollingAverage(currentRate, approxRate, weight=percentOpen/100, entries=4)
```

---

## 6. Test Infrastructure

**Framework**: Spock 1.2 on Groovy 2.5.4
**CI Library**: hubitat_ci 0.17 (me.biocomp.hubitat_ci)
**Build**: Gradle with JaCoCo coverage
**Java**: Toolchain pinned to JDK 11
**Test count**: 37 test files, 50+ individual test cases

Test files cover: airflow adjustment, API communication, authentication, constants validation, DAB charts/history/progress, decimal precision, device drivers, efficiency export/import, EWMA/outlier, hourly/daily DAB, HVAC detection, caching, math calculations, request throttling, room change rate, room setpoints, temperature conversion, thermostat setpoint/state, time calculations, vent control/opening/operations, voltage attributes.

---

## 7. Dependencies

### Production (from build.gradle)
- `org.apache.commons:commons-io:1.3.2` -- Standard Java
- `org.codehaus.groovy:groovy-all:2.5.4` -- Standard Groovy
- `org.codehaus.groovy:groovy-dateutil:2.5.4` -- Standard Groovy
- `org.codehaus.groovy.modules.http-builder:http-builder:0.7.1` -- Standard Groovy

### Test
- `org.spockframework:spock-core:1.2-groovy-2.5` -- Test framework
- `me.biocomp.hubitat_ci:hubitat_ci:0.17` -- Hubitat CI sandbox
- `net.bytebuddy:byte-buddy:1.12.18` -- Mocking support

All dependencies are standard Java/Groovy ecosystem. The `hubitat_ci` library simulates the Hubitat sandbox for testing. None are problematic for the Hubitat sandbox at runtime since the build.gradle skips compiling main sources (`compileGroovy.enabled = false`) -- tests load app Groovy via the CI sandbox.

---

## 8. Potential Issues and Anti-Patterns

1. **Single synchronous HTTP call**: `getStructureData()` at line 3254 uses `httpGet()`. This violates the app's own async-only policy and could block the hub.

2. **Double decrement of activeRequests**: The centralized `asyncHttpCallback()` always decrements in its finally block, but individual callback methods (handleDeviceGet, handleRoomGet, handlePuckGet, etc.) also call `decrementActiveRequests()`. When routed through asyncHttpCallback, this results in double decrement. The design appears intentional (individual handlers decrement for direct calls, asyncHttpCallback for routed calls), but it's fragile.

3. **Large atomicState usage**: dabHistory entries, rawDabSamplesEntries (up to 20000), adaptiveMarksEntries (up to 5000) stored in atomicState could hit Hubitat's state size limits (~100KB per app).

4. **Missing brace/indentation issues**: The mainPage() method has inconsistent indentation and some questionable brace placement (lines 435-437 show a closing brace for thermostat section that doesn't align).

5. **Duplicate initialization code**: Lines 1569-1573 in `initializeInstanceCaches()` duplicate the activeRequests and circuitOpenUntil initialization.

6. **No rate limiting on QuickChart.io calls**: `buildDabChart()` generates a QuickChart URL on every page load without caching.

7. **Verbose CI compatibility layer**: ~100 lines dedicated to property overrides and fallback mechanisms for test environments. While necessary, this adds complexity to the production code path.
