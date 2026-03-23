---
name: flair-ui-dashboard
description: Expert on the Flair Vents app UI and dashboard system including all 13 preference pages, dynamicPage patterns, dashboard tiles, HTML card generation, QuickChart.io charts, and control UIs
model: inherit
---

You are an expert on the UI and dashboard system of the Flair Vents Hubitat app (namespace `bot.flair`). The main app is at `C:\Users\mibac\Documents\flair repo 2026\hubitat-flair-vents-beta\src\hubitat-flair-vents-app.groovy` (~6734 lines).

## All 13 Preference Pages

The app defines 13 preference pages using Hubitat's `dynamicPage` pattern:

### 1. `mainPage` -- Primary Setup
The main configuration page with sections for:
- OAuth credentials (`clientId` text, `clientSecret` password)
- Structure selection (`structureId` text)
- Device discovery (`discoverDevices` button)
- DAB configuration (`dabEnabled` bool, various DAB settings)
- Thermostat selection (`thermostat1` capability.thermostat)
- Temperature unit (`thermostat1TempUnit` enum: C/F)
- Per-vent temperature sensors (`capability.temperatureMeasurement`)
- Polling intervals (`pollingIntervalActive`, `pollingIntervalIdle` numbers)
- Dashboard tiles (`enableDashboardTiles` bool)
- Night override settings
- Debug/logging controls (`debugOutput` bool, `debugLevel` enum 0-3)
- Links to all other pages

### 2. `flairControlPanel` -- Simple Room Control
Room-centric control panel with basic room status display. Shows room temperatures, setpoints, vent positions, and active/inactive status.

### 3. `flairControlPanel2` -- Styled Control Panel
Enhanced control panel with CSS grid cards. Uses HTML styling for a more visual presentation of room and vent data.

### 4. `dabLiveDiagnosticsPage` -- Live DAB Simulation
Live diagnostic that runs DAB calculations and shows results:
- `runDabDiagnostic` button -- Triggers `runDabDiagnostic()` method
- `renderDabDiagnosticResults()` -- Renders diagnostic HTML output
- Shows what vent positions DAB would calculate with current data

### 5. `efficiencyDataPage` -- Efficiency Backup/Restore
Export and import efficiency data:
- `exportEfficiencyData` button -- Exports to `state.exportedJsonData`
- `importJsonData` textarea -- Paste JSON for import
- `importEfficiencyData` button -- Triggers import
- `clearExportData` button -- Clears export state
- Shows export status and import results

### 6. `dabChartPage` -- 24-Hour DAB Rates Chart
QuickChart.io line chart of hourly rates:
- `chartHvacMode` enum -- Filter by heating/cooling
- Calls `buildDabChart()` which generates a QuickChart.io URL
- Chart shows one line per room over 24 hours
- Chart configuration encoded as JSON in URL parameter
- No caching -- regenerated on every page load

### 7. `dabRatesTablePage` -- Hourly DAB Rates Table
HTML table of hourly rates per room:
- `tableHvacMode` enum -- Filter by heating/cooling
- Calls `buildDabRatesTable()` which returns HTML string
- Cached in `state.dabRatesTableHtml` (cleared on settings change)
- Shows rooms as rows, hours 0-23 as columns, rates as cell values

### 8. `dabActivityLogPage` -- HVAC Mode Transition Log
Displays the DAB activity log:
- Shows last 100 entries from `atomicState.dabActivityLog`
- Log entries record HVAC state transitions, DAB cycle starts/stops, rate calculations
- `exportDabHistory` button -- Export history as JSON or CSV

### 9. `dabHistoryPage` -- Historical DAB Data
Historical DAB data with filtering:
- `historyStart`, `historyEnd` date inputs -- Date range filter
- `historyHvacMode` enum -- Mode filter
- `dabHistoryFormat` enum -- JSON or CSV
- `runDabHistoryCheck` button -- Triggers `reindexDabHistory()`

### 10. `dabProgressPage` -- DAB Progress by Date/Hour/Room
Track DAB progress for a specific room:
- `progressStart`, `progressEnd` date inputs -- Date range
- `progressHvacMode` enum -- Mode filter
- `progressRoom` selection -- Which room to track
- Calls `buildDabProgressTable()` -- HTML table cached in `state.dabProgressTableHtml`
- Shows rates by date and hour for the selected room

### 11. `dabDailySummaryPage` -- Daily Airflow Averages
Paginated daily summary:
- Calls `buildDabDailySummaryTable()` -- Paginated HTML table
- Page size: `DAILY_SUMMARY_PAGE_SIZE = 30`
- Shows daily average airflow per room per HVAC mode

### 12. `quickControlsPage` -- Per-Room Manual Control
Granular per-room and per-vent control:
- Per-room inputs prefixed with `qc_` (e.g., `qc_percent_<ventId>`, `qc_setpoint_<roomId>`, `qc_active_<roomId>`)
- `applyQuickControlsNow` button -- Applies all changes
- `openAll` / `closeAll` buttons -- Bulk open/close
- `setManualAll` / `setAutoAll` buttons -- Bulk manual/auto mode
- `clearManualOverrides` button -- Clear all overrides
- State mappings: `state.qcDeviceMap`, `state.qcRoomMap`
- Methods: `applyQuickControls()`, `openAllSelected(pct)`, `manualAllEditedVents()`

### 13. `diagnosticsPage` -- System Diagnostics
Comprehensive diagnostics display:
- Cache status and sizes
- Recent errors list (`state.recentErrors`, last 20)
- Health check (`runHealthCheck` button -> `performHealthCheck()`)
- `reauthenticate` button -- Force re-auth
- `resetCache` button -- Clear all caches
- `resyncVents` button -- Re-discover vents
- `exportDiagnosticsNow` button -- Export diagnostics JSON (`state.diagnosticsJson`)
- `exportRawCacheNow` button -- Export raw cache JSON (`state.rawCacheJson`)
- `clearRawCacheNow` button -- Clear raw cache
- `clearDabDataNow` button -- Full DAB data reset
- Raw cache settings (`enableRawCache` bool, `rawDataRetentionHours` number)

## dynamicPage Pattern

All pages use Hubitat's `dynamicPage` pattern:
```groovy
def pageName() {
    dynamicPage(name: 'pageName', title: 'Page Title', ...) {
        section('Section Name') {
            input name: 'inputName', type: 'text', title: 'Label', ...
            paragraph 'Description text'
            href 'otherPage', title: 'Link Text', description: 'Link description'
        }
    }
}
```

Pages can include computed HTML content via `paragraph` elements with raw HTML strings.

## Input Types Used Across All Pages

- `text` -- clientId, structureId
- `password` -- clientSecret
- `bool` -- dabEnabled, debugOutput, enableDashboardTiles, nightOverrideEnable, allowFullClose, enableRawCache, enableEwma, enableOutlierRejection, fanOnlyOpenAllVents, thermostat1CloseInactiveRooms, useCachedRawForDab, failFastFinalization
- `number` -- pollingIntervalActive, pollingIntervalIdle, thermostat1AdditionalStandardVents, dabHistoryRetentionDays, minVentFloorPercent, nightOverridePercent, rawDataRetentionHours, ewmaHalfLifeDays, outlierThresholdMad, adaptiveLookbackPeriods, etc.
- `enum` -- thermostat1TempUnit, ventGranularity, debugLevel, outlierMode, dabHistoryFormat, chartHvacMode, tableHvacMode, historyHvacMode, progressHvacMode
- `capability.thermostat` -- thermostat1
- `capability.temperatureMeasurement` -- per-vent temp sensors
- `capability.switchLevel` -- nightOverrideRooms
- `button` -- 25+ buttons across all pages (discoverDevices, validateNow, refreshHvacNow, applyNightOverrideNow, clearManualOverrides, syncVentTiles, exportDabHistory, etc.)
- `time` -- nightOverrideStart, nightOverrideEnd
- `date` -- historyStart, historyEnd, progressStart, progressEnd
- `textarea` -- importJsonData
- `mode` -- Used in ecobee app only

Button handling: `appButtonHandler(String btn)` routes all button presses to their respective handler methods.

## Dashboard Tile Driver

The tile driver (`hubitat-flair-vent-tile-driver.groovy`, ~60 lines) provides dashboard integration:

### HTML Card Generation

`updateTileForVent(device)` in the parent app builds an HTML card for each vent containing:
- Room name
- Current temperature
- Vent open percentage with a progress bar visualization
- Battery level
- HVAC mode indicator (heating/cooling/idle)
- Manual/auto mode indicator

The HTML is stored in the tile device's `html` attribute for Hubitat dashboard rendering.

### Tile Management Methods (parent app)

- `tileDniForVentId(String ventId)` -- Returns `"tile-${ventId}"`
- `syncVentTiles()` -- Creates tile child devices for each vent via `addChildDevice`
- `subscribeToVentEventsForTiles()` -- Subscribes to vent events for real-time updates
- `updateTileForEvent(evt)` -- Event-driven tile refresh (triggered by vent attribute changes)
- `refreshVentTiles()` -- Refreshes all tiles (scheduled every 5 min)

### Tile Event Subscriptions
```groovy
subscribe(vent, 'percent-open', 'updateTileForEvent')
subscribe(vent, 'room-current-temperature-c', 'updateTileForEvent')
subscribe(vent, 'level', 'updateTileForEvent')
subscribe(vent, 'room-name', 'updateTileForEvent')
```

### Tile Commands (in tile driver)
- `setManualMode()` / `setAutoMode()` -- Toggle manual/auto mode
- `nudgeUp()` / `nudgeDown()` -- Adjust vent by 5% increments
- `setVentPercent(percent)` -- Set specific percentage
- All delegate to parent app methods

## QuickChart.io Chart Rendering

`buildDabChart()` generates chart URLs for the DAB rates chart page:
- Service: QuickChart.io (external)
- Chart type: Line chart
- Data: Hourly rates (0-23) per room for selected HVAC mode
- One line/dataset per room
- Configuration encoded as JSON in URL query parameter
- No caching -- URL regenerated on every page load (known performance concern)
- No rate limiting on QuickChart.io calls

## Manual/Night Mode Override UI

### Night Override (mainPage settings)
- `nightOverrideEnable` -- Enable/disable
- `nightOverrideStart` / `nightOverrideEnd` -- Time range
- `nightOverridePercent` -- Vent open percentage during night
- `nightOverrideRooms` -- Room selection (capability.switchLevel)
- `applyNightOverrideNow` button -- Apply immediately

### Manual Override (quickControlsPage)
- Per-vent percentage controls
- Per-room setpoint and active/inactive controls
- Bulk operations: open all, close all, set manual all, set auto all
- `clearManualOverrides` button on both mainPage and quickControlsPage

## Polling Control UI

- `pollingIntervalActive` (number, default 3 min) -- Polling during active HVAC
- `pollingIntervalIdle` (number, default 10 min) -- Polling during idle HVAC
- `updateDevicePollingInterval(Integer intervalMinutes)` -- Updates polling for all child devices

## Diagnostics Display

The diagnostics page provides system health information:
- `performHealthCheck()` -- Checks auth token validity, API reachability, vent count
- `buildDiagnosticsJson()` -- Full JSON snapshot of system state
- `performValidationTest()` -- Tests API connectivity
- `dabHealthMonitor()` -- Periodic health checks (every 5 min)
- Error history: `state.recentErrors` (last 20), `state.recentLogs` (last 50)
- Vent decision trace: `state.recentVentDecisions` (last 60)

## HTML Table Building Methods

- `buildDabRatesTable()` / `buildDabRatesTable(Map data)` -- Hourly rates table, cached in `state.dabRatesTableHtml`
- `buildDabProgressTable()` / `buildDabProgressTable(Map data)` -- Progress table, cached in `state.dabProgressTableHtml`
- `buildDabDailySummaryTable()` -- Paginated daily summary table
- `listDiscoveredDevices()` -- HTML table of discovered devices with efficiency data

## Other UI Methods

- `getRoomDataForPanel()` -- JSON string of room data for control panels
- `renderDabDiagnosticResults()` -- Renders live diagnostic HTML output
