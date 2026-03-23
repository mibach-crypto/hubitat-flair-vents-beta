# Hubitat Elevation Developer Documentation - Comprehensive Reference

## Table of Contents
1. [Platform Overview](#1-platform-overview)
2. [App Development](#2-app-development)
3. [Driver Development](#3-driver-development)
4. [Common Methods (Apps & Drivers)](#4-common-methods-apps--drivers)
5. [Platform Objects](#5-platform-objects)
6. [Device Interfaces](#6-device-interfaces)
7. [Capabilities Reference](#7-capabilities-reference)
8. [Groovy Sandbox & Restrictions](#8-groovy-sandbox--restrictions)
9. [Porting from SmartThings](#9-porting-from-smartthings)
10. [Best Practices](#10-best-practices)

---

## 1. Platform Overview

### Runtime Environment
- Hubitat apps and drivers are written in **Groovy 2.4** (specifically 2.4.21)
- Code runs inside the Hubitat Elevation **sandbox** execution environment
- The sandbox provides Hubitat-specific methods and restricts access to certain Java/Groovy classes
- Apps can only run commands on devices the user has selected in the app
- A reasonable subset of Groovy/Java classes is allowed; others (like `System` methods) are blocked

### Two Types of User Code
1. **Apps** - Automate logic, subscribe to events, control devices, provide UI
2. **Drivers** - Communicate with physical/virtual devices, handle protocol data, generate events

### Key Architectural Concepts
- Apps are NOT always "running" - they wake in response to events:
  - Device or hub/location events the app subscribed to via `subscribe()`
  - Schedules created via `runIn()`, `schedule()`, or similar methods
  - UI rendering when user opens the app
  - Installation, update, or uninstallation lifecycle callbacks
- Drivers handle incoming data from devices via `parse()` and send commands to devices
- Both apps and drivers can use HTTP methods, scheduling, and state storage

### Documentation Sources
- Official docs: https://docs2.hubitat.com/en/developer
- Legacy docs: https://docs.hubitat.com/ (may be offline)
- Community: https://community.hubitat.com/
- Example code: https://github.com/hubitat/HubitatPublic/tree/master/examples

---

## 2. App Development

### 2.1 App Structure

An app consists of three main sections:

```groovy
definition(
    name: "My App Name",
    namespace: "myNamespace",
    author: "Author Name",
    description: "App description",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
    importUrl: "https://raw.githubusercontent.com/...",
    singleInstance: false,     // if true, only one instance allowed
    singleThreaded: false      // if true, prevents simultaneous execution
)

preferences {
    // UI pages and inputs
}

// General code section - methods, handlers, etc.
```

### 2.2 App Lifecycle Methods

```groovy
def installed() {
    // Called when app is first installed
    log.debug "Installed with settings: ${settings}"
    initialize()
}

def updated() {
    // Called when app preferences are updated (user presses "Done")
    log.debug "Updated with settings: ${settings}"
    unsubscribe()
    unschedule()
    initialize()
}

def uninstalled() {
    // Called when app is uninstalled
    log.debug "Uninstalled"
}

def initialize() {
    // Common initialization code called from both installed() and updated()
    subscribe(myDevice, "switch", switchHandler)
}
```

### 2.3 Preferences and Input Types

#### Page Structure
```groovy
preferences {
    page(name: "mainPage", title: "Settings", install: true, uninstall: true) {
        section("Devices") {
            input "mySwitch", "capability.switch", title: "Select a switch", required: true
            input "myText", "text", title: "Enter text", defaultValue: "hello"
        }
        section("Options") {
            input "myBool", "bool", title: "Enable feature", defaultValue: false
        }
    }
}
```

#### Dynamic Pages
```groovy
preferences {
    page(name: "mainPage")
    page(name: "settingsPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Main", nextPage: "settingsPage") {
        section {
            input "selectedDevice", "capability.switch", title: "Pick a device"
        }
    }
}

def settingsPage() {
    dynamicPage(name: "settingsPage", title: "Settings", install: true, uninstall: true) {
        section {
            if (selectedDevice) {
                paragraph "You selected: ${selectedDevice.displayName}"
            }
            input "delay", "number", title: "Delay (seconds)", defaultValue: 5
        }
    }
}
```

#### Complete Input Types

| Type | Value Type | Description | UI Element |
|------|-----------|-------------|------------|
| `bool` | Boolean | True/false toggle | On/off slider |
| `button` | N/A | Clickable button | Button (triggers `appButtonHandler(String btn)`) |
| `capability.capabilityName` | DeviceWrapper | Device selector filtered by capability | Device picker dropdown |
| `checkbox` | Boolean | Checkbox input | Checkbox |
| `color` | String | Color picker | Color picker widget |
| `date` | String | Date input | Date picker |
| `decimal` | BigDecimal | Decimal number input | Number field |
| `device.driverName` | DeviceWrapper | Device selector filtered by driver name | Device picker |
| `email` | String | Email address input | Email field |
| `enum` | String/List | Dropdown selection | Pull-down menu |
| `href` | N/A | Link to another page or external URL | Clickable link |
| `hub` | Hub | Hub selector | Hub picker |
| `icon` | String | Icon URL selector | Icon picker |
| `mode` | String | Mode selector | Mode dropdown |
| `number` | Integer/Long | Integer number input | Number field |
| `password` | String | Password input (masked) | Password field |
| `phone` | String | Phone number input | Phone field |
| `text` | String | Single-line text input | Text field |
| `textarea` | String | Multi-line text input | Resizable text box |
| `time` | String | Time picker (format: "yyyy-MM-dd'T'HH:mm:ss.sssXX") | Time picker |

#### Input Options
```groovy
input(
    name: "elementName",
    type: "elementType",
    title: "Display Title",
    description: "Description text",
    required: true,            // whether input is required
    defaultValue: "default",   // default value
    multiple: true,            // allow multiple selections (capability/enum)
    submitOnChange: true,      // refresh page when value changes
    options: ["opt1", "opt2"], // for enum type
    range: "1..100",           // for number/decimal types
    width: 6                   // column width (1-12 grid)
)
```

#### Accessing Input Values
```groovy
// All three of these are equivalent:
settings.myInputName
settings["myInputName"]
myInputName  // using name directly as if it were a variable
```

The `settings` map stores all user-provided input values with the input's `name` as the key.

#### Section Options
```groovy
section(title: "Section Title", hideable: true, hidden: false) {
    // inputs go here
}
```

#### Other Page Elements
```groovy
paragraph "Some informational text"
paragraph "<b>Bold text</b> with <a href='url'>link</a>"
href "pageName", title: "Go to page", description: "Click here"
href url: "https://example.com", title: "External link"
```

### 2.4 Event Subscriptions

#### Subscribe Method Signatures
```groovy
// Subscribe to a specific attribute on a device
void subscribe(DeviceWrapper device, String attributeName, handlerMethod, Map options = null)

// Subscribe to a specific attribute on multiple devices
void subscribe(DeviceWrapperList devices, String attributeName, handlerMethod, Map options = null)

// Subscribe to ALL events from a device (since 2.2.1)
void subscribe(DeviceWrapper device, String handlerMethod, Map options = null)

// Subscribe to ALL events from multiple devices (since 2.2.1)
void subscribe(DeviceWrapperList devices, String handlerMethod, Map options = null)

// Subscribe to location events (e.g., mode changes)
void subscribe(Location location, String attributeName, handlerMethod, Map options = null)

// Subscribe to app events
void subscribe(InstalledAppWrapper app, handlerMethod)
```

#### Options
- `filterEvents` (Boolean, defaults to `true`): If true, ignores events where value did not change unless `isStateChange` is true on the event

#### Handler Method Signature
```groovy
def switchHandler(evt) {
    log.debug "Event: ${evt.name} = ${evt.value}"
    log.debug "Device: ${evt.device.displayName}"
    log.debug "Description: ${evt.descriptionText}"
    log.debug "Is state change: ${evt.isStateChange}"
}
```

#### Common Subscription Patterns
```groovy
// Subscribe to switch events
subscribe(mySwitch, "switch", switchHandler)

// Subscribe to motion events
subscribe(myMotion, "motion", motionHandler)

// Subscribe to mode changes
subscribe(location, "mode", modeHandler)

// Subscribe to HSM status
subscribe(location, "hsmStatus", hsmHandler)

// Unsubscribe from all
unsubscribe()

// Unsubscribe from specific device
unsubscribe(mySwitch)
```

### 2.5 Scheduling

#### runIn - Delayed Execution
```groovy
// Run handler after delay
void runIn(Long delayInSeconds, String handlerMethod, Map options = null)

// Options:
//   overwrite: (Boolean, default true) cancel previous schedule for this handler
//   data: (Map) data to pass to handler
//   misfire: "ignore" (default) - skip if missed

runIn(60, "myHandler")
runIn(30, "myHandler", [data: [key: "value"]])
runIn(10, "myHandler", [overwrite: false])  // don't cancel existing schedule
```

#### Periodic Execution
```groovy
// Run every N minutes/hours
void runEvery1Minute(String handlerMethod)
void runEvery5Minutes(String handlerMethod)
void runEvery10Minutes(String handlerMethod)
void runEvery15Minutes(String handlerMethod)
void runEvery30Minutes(String handlerMethod)
void runEvery1Hour(String handlerMethod)
void runEvery3Hours(String handlerMethod)
```

#### schedule - Cron Expression
```groovy
// Quartz cron format: "Seconds Minutes Hours DayOfMonth Month DayOfWeek Year"
void schedule(String cronExpression, String handlerMethod, Map options = null)

// Examples:
schedule("0 0 12 * * ?", "noonHandler")           // Every day at noon
schedule("0 */10 * ? * *", "every10Min")           // Every 10 minutes
schedule("0 0 8 ? * MON-FRI", "weekdayMorning")   // Weekdays at 8am
schedule("0 15 10 * * ? 2025", "specific")         // 10:15am daily in 2025
```

#### runOnce - Run at Specific Time
```groovy
void runOnce(Date dateTime, String handlerMethod, Map options = null)
void runOnce(String dateTimeString, String handlerMethod, Map options = null)
```

#### Unschedule
```groovy
unschedule()                    // Remove ALL scheduled tasks
unschedule("specificHandler")   // Remove schedule for specific handler
```

### 2.6 State and atomicState

#### state - Lazy Write
```groovy
// state writes data just BEFORE the app goes to sleep
state.myVariable = "value"
state.myMap = [key1: "val1", key2: "val2"]
state.myList = [1, 2, 3]

// Reading state
def val = state.myVariable
```

#### atomicState - Immediate Write
```groovy
// atomicState commits changes as soon as they are made
// Use when multiple threads might access the same data
atomicState.counter = 0
atomicState.counter = atomicState.counter + 1
```

#### Key Differences
- `state` and `atomicState` refer to the **same underlying data** and can be mixed
- `state` is faster but writes are deferred
- `atomicState` is slower but immediately consistent
- You **cannot** access parent/child app state directly between apps

### 2.7 Mappings (OAuth / API Endpoints)

#### Setting Up Mappings
```groovy
mappings {
    path("/myEndpoint") {
        action: [
            GET: "getHandler",
            POST: "postHandler",
            PUT: "putHandler",
            DELETE: "deleteHandler"
        ]
    }
    path("/device/:id/command/:cmd") {
        action: [GET: "deviceCommandHandler"]
    }
}
```

#### OAuth Setup
1. Enable OAuth using the OAuth button in the app code editor
2. Generate an access token with `createAccessToken()`
3. Access token stored in `state.accessToken`

#### Endpoint URLs
```groovy
// Local endpoint
// http://[hub-ip]/apps/api/[app-id]/[endpoint-path]?access_token=[token]

// Cloud endpoint
// https://cloud.hubitat.com/api/[hubUID]/apps/[appId]/[path]?access_token=[token]

// Getting endpoint URLs
getFullLocalApiServerUrl()   // Local base URL
getFullApiServerUrl()        // Cloud base URL
```

#### Handler Methods
```groovy
def getHandler() {
    // Implicit 'request' object available
    def source = request.requestSource  // "local" or "cloud"
    def headers = request.headers

    // Return JSON
    render contentType: "application/json", data: '{"status":"ok"}', status: 200
}

def postHandler() {
    def body = request.JSON  // parsed JSON body
    // Process...
    render contentType: "application/json", data: '{"received":true}', status: 200
}

def deviceCommandHandler() {
    def deviceId = params.id      // path parameter
    def command = params.cmd      // path parameter
    // Process...
    render contentType: "text/html", data: "<h1>Done</h1>", status: 200
}
```

### 2.8 Parent/Child App Pattern

#### Parent App Definition
```groovy
definition(
    name: "My Parent App",
    namespace: "myNamespace",
    author: "Author",
    description: "Parent app",
    singleInstance: true
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Parent App", install: true, uninstall: true) {
        section {
            app(name: "childApps", appName: "My Child App", namespace: "myNamespace",
                title: "Add New Child", multiple: true)
        }
    }
}

def installed() {
    log.debug "Parent installed"
}

def updated() {
    log.debug "Parent updated"
}
```

#### Child App Definition
```groovy
definition(
    name: "My Child App",
    namespace: "myNamespace",
    author: "Author",
    description: "Child app",
    parent: "myNamespace:My Parent App"  // Links to parent
)

preferences {
    page(name: "childPage")
}

def childPage() {
    dynamicPage(name: "childPage", title: "Child Settings", install: true, uninstall: true) {
        section {
            label title: "Name this instance", required: true
        }
    }
}
```

#### Parent/Child Methods
```groovy
// In parent - manage children
getChildApps()              // List<ChildAppWrapper> - all child apps
getAllChildApps()            // List<ChildAppWrapper> - all child apps including paused
getChildAppById(Long id)    // ChildAppWrapper by ID
getChildAppByLabel(String)  // ChildAppWrapper by label
addChildApp(String namespace, String name, String label, Map properties)
deleteChildApp(Long id)

// In child - access parent
getParent()                 // returns parent app wrapper
parent.myMethod()           // call method on parent
```

### 2.9 Button Handler
```groovy
// When using input type "button", define this handler:
def appButtonHandler(String buttonName) {
    switch(buttonName) {
        case "myButton":
            log.debug "Button pressed!"
            break
    }
}
```

---

## 3. Driver Development

### 3.1 Driver Structure

```groovy
metadata {
    definition(
        name: "My Driver",
        namespace: "myNamespace",
        author: "Author Name",
        importUrl: "https://raw.githubusercontent.com/..."
    ) {
        capability "Switch"
        capability "Actuator"
        capability "Refresh"

        // Custom commands
        command "myCustomCommand"
        command "setLevel", [[name: "level", type: "NUMBER", description: "Level 0-100"]]
        command "setColor", [[name: "color", type: "JSON_OBJECT", description: "Color map"]]

        // Custom attributes
        attribute "myAttribute", "string"
        attribute "enumAttribute", "enum", ["value1", "value2", "value3"]
        attribute "numberAttribute", "number"

        // Fingerprints (for Zigbee/Z-Wave auto-detection)
        fingerprint profileId: "0104", inClusters: "0000,0003,0006", outClusters: "0019",
                    manufacturer: "ACME", model: "Widget", deviceJoinName: "ACME Widget"
    }

    preferences {
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "txtEnable", type: "bool", title: "Enable description text logging", defaultValue: true
    }
}
```

### 3.2 Driver Lifecycle Methods

```groovy
def installed() {
    // Called when device is first created
    log.debug "Installed"
}

def updated() {
    // Called when preferences are saved
    log.debug "Updated"
    if (logEnable) runIn(1800, "logsOff")  // Auto-disable debug logging after 30 min
}

def uninstalled() {
    // Called when device is removed
    log.debug "Uninstalled"
}

def configure() {
    // Called when "Configure" button is pressed (if capability Configuration is declared)
    log.debug "Configuring device"
}

def initialize() {
    // Not automatically called - must be called explicitly from installed()/updated()
}

def logsOff() {
    log.warn "Debug logging disabled"
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}
```

### 3.3 Commands (Implementing Capabilities)

```groovy
// Switch capability requires on() and off()
def on() {
    if (txtEnable) log.info "${device.displayName} was turned on"
    sendEvent(name: "switch", value: "on", descriptionText: "${device.displayName} was turned on")
}

def off() {
    if (txtEnable) log.info "${device.displayName} was turned off"
    sendEvent(name: "switch", value: "off", descriptionText: "${device.displayName} was turned off")
}

// SwitchLevel capability requires setLevel()
def setLevel(level, duration = 0) {
    if (level < 0) level = 0
    if (level > 100) level = 100
    sendEvent(name: "level", value: level, unit: "%",
              descriptionText: "${device.displayName} level was set to ${level}%")
}
```

### 3.4 sendEvent / createEvent

```groovy
// sendEvent - fires event immediately (RECOMMENDED)
sendEvent(name: "switch", value: "on")

// Full sendEvent with all properties
sendEvent(
    name: "temperature",       // attribute name (required)
    value: 72.5,               // attribute value (required)
    unit: "F",                 // unit of measurement
    descriptionText: "${device.displayName} temperature is 72.5F",
    isStateChange: true,       // force event even if value unchanged
    type: "physical",          // "physical" or "digital"
    data: [raw: "0x48"]        // additional data map
)

// For button events, isStateChange is typically needed:
sendEvent(name: "pushed", value: 1, isStateChange: true,
          descriptionText: "${device.displayName} button 1 was pushed")
```

### 3.5 parse() Method

The `parse()` method handles raw incoming data from devices.

```groovy
// For Zigbee devices
def parse(String description) {
    if (logEnable) log.debug "parse: ${description}"
    def descMap = zigbee.parseDescriptionAsMap(description)

    if (descMap.cluster == "0006") {
        // On/Off cluster
        def value = descMap.value == "01" ? "on" : "off"
        sendEvent(name: "switch", value: value)
    }
}

// For Z-Wave devices
def parse(String description) {
    def cmd = zwave.parse(description, commandClassVersions)
    if (cmd) {
        zwaveEvent(cmd)
    }
}

// For LAN devices (HTTP responses)
def parse(String description) {
    def msg = parseLanMessage(description)
    def body = msg.body
    def json = msg.json  // if JSON response
    def xml = msg.xml    // if XML response
    def headers = msg.headers
    def status = msg.status
}
```

### 3.6 Device Data, State, and Attributes

```groovy
// Device data (persistent metadata)
device.getDataValue("key")
device.updateDataValue("key", "value")
device.removeDataValue("key")

// Device state (like app state)
state.myVar = "value"
def val = state.myVar

// Device properties
device.displayName        // user-set name
device.name               // driver-set name
device.deviceNetworkId    // DNI
device.id                 // device ID
device.endpointId         // Zigbee endpoint
device.zigbeeId           // Zigbee IEEE address
device.hub                // Hub object

// Current attribute values
device.currentValue("switch")           // returns current value (cached)
device.currentValue("switch", true)     // skip cache, read from DB
device.currentState("temperature")      // returns State object
device.latestValue("switch")
device.latestState("temperature")

// Update device settings programmatically
device.updateSetting("settingName", [value: "newValue", type: "text"])
device.removeSetting("settingName")

// Get supported capabilities/commands
device.getSupportedCommands()
device.getSupportedAttributes()
device.getCapabilities()
```

### 3.7 Parent/Child Driver Pattern

#### Parent Driver
```groovy
metadata {
    definition(name: "My Parent Driver", namespace: "myNs", author: "Author") {
        capability "Configuration"
    }
}

def installed() {
    createChildDevices()
}

def createChildDevices() {
    addChildDevice("myNs", "My Child Driver", "${device.deviceNetworkId}-ep1",
        [name: "Child 1", label: "${device.displayName} - Endpoint 1", isComponent: true])
}

// Component methods called by children
def componentOn(cd) {
    log.debug "componentOn from ${cd.displayName}"
    // Send appropriate command to physical device
}

def componentOff(cd) {
    log.debug "componentOff from ${cd.displayName}"
}
```

#### Child Driver
```groovy
metadata {
    definition(name: "My Child Driver", namespace: "myNs", author: "Author") {
        capability "Switch"
    }
}

def on() {
    parent?.componentOn(this.device)
}

def off() {
    parent?.componentOff(this.device)
}
```

#### Parent/Child Device Methods
```groovy
// In parent driver
addChildDevice(String namespace, String typeName, String deviceNetworkId, Map properties)
// properties: name, label, isComponent (boolean)
getChildDevices()
getChildDevice(String deviceNetworkId)
deleteChildDevice(String deviceNetworkId)

// In child driver
getParent()  // returns parent device wrapper
```

---

## 4. Common Methods (Apps & Drivers)

### 4.1 HTTP Methods - Synchronous

```groovy
// httpGet
httpGet(String uri, Closure closure)
httpGet(Map params, Closure closure)

// httpPost
httpPost(String uri, String body, Closure closure)
httpPost(Map params, Closure closure)

// httpPut
httpPut(String uri, String body, Closure closure)
httpPut(Map params, Closure closure)

// httpDelete
httpDelete(Map params, Closure closure)

// Params Map:
[
    uri: "https://api.example.com",     // required
    path: "/endpoint",                   // optional path
    query: [param1: "val1"],            // URL query params
    headers: ["Authorization": "Bearer token"],
    requestContentType: "application/json",
    contentType: "application/json",     // Accept header
    body: '{"key":"value"}',            // or Map for auto-encoding
    textParser: true,                   // force text response parsing
    ignoreSSLIssues: true               // skip SSL verification
]

// Example
httpGet("https://api.example.com/data") { resp ->
    log.debug "Status: ${resp.status}"
    log.debug "Data: ${resp.data}"
}

httpPost([uri: "https://api.example.com/post", body: [name: "test"]]) { resp ->
    if (resp.status == 200) {
        def json = resp.data
    }
}
```

### 4.2 HTTP Methods - Asynchronous (RECOMMENDED)

```groovy
// Signatures (all six methods follow same pattern):
void asynchttpGet(String callbackMethod, Map params, Map data = null)
void asynchttpPost(String callbackMethod, Map params, Map data = null)
void asynchttpPut(String callbackMethod, Map params, Map data = null)
void asynchttpDelete(String callbackMethod, Map params, Map data = null)
void asynchttpPatch(String callbackMethod, Map params, Map data = null)
void asynchttpHead(String callbackMethod, Map params, Map data = null)

// Params Map (same as synchronous):
[
    uri: "https://api.example.com",        // required
    path: "/endpoint",
    query: [param1: "val1"],
    headers: ["Authorization": "Bearer token"],
    requestContentType: "application/json",
    contentType: "application/json",
    body: '{"key":"value"}'                // for POST/PUT/PATCH
]

// Callback Method:
def myCallback(response, data) {
    // response is AsyncResponse object
    // data is the optional Map passed as third arg
}

// AsyncResponse Object Methods:
//   getStatus()      -> int (HTTP status code)
//   getHeaders()     -> Map<String, String>
//   getData()        -> String (response body)
//   getJson()        -> Object (parsed JSON)
//   getXml()         -> GPathResult (parsed XML)
//   getErrorData()   -> String
//   getErrorJson()   -> Object
//   getErrorXml()    -> GPathResult
//   getErrorMessage() -> String
//   hasError()       -> boolean

// Example:
def fetchData() {
    def params = [
        uri: "https://api.example.com/data",
        headers: ["Authorization": "Bearer ${state.token}"],
        contentType: "application/json"
    ]
    asynchttpGet("handleResponse", params, [source: "fetchData"])
}

def handleResponse(response, data) {
    if (response.hasError()) {
        log.error "HTTP error: ${response.getErrorMessage()}"
        return
    }
    log.debug "Source: ${data.source}"
    log.debug "Status: ${response.status}"
    def json = response.json
    // Process json...
}
```

### 4.3 Scheduling Methods

```groovy
// Delayed execution
void runIn(Long delayInSeconds, String handlerMethod, Map options = null)
// options: overwrite (Boolean, default true), data (Map), misfire ("ignore")

// Periodic execution
void runEvery1Minute(String handlerMethod)
void runEvery5Minutes(String handlerMethod)
void runEvery10Minutes(String handlerMethod)
void runEvery15Minutes(String handlerMethod)
void runEvery30Minutes(String handlerMethod)
void runEvery1Hour(String handlerMethod)
void runEvery3Hours(String handlerMethod)

// Cron-based scheduling (Quartz format)
void schedule(String cronExpression, String handlerMethod, Map options = null)
// Format: "Seconds Minutes Hours DayOfMonth Month DayOfWeek [Year]"

// Run at specific time
void runOnce(Date dateTime, String handlerMethod, Map options = null)
void runOnce(String isoDateTimeString, String handlerMethod, Map options = null)

// Cancel schedules
void unschedule()                      // Cancel all
void unschedule(String handlerMethod)  // Cancel specific handler
```

### 4.4 Time/Date Utility Methods

```groovy
// Current time
Long now()                              // Current time in milliseconds since epoch
Date new Date()                         // Current date/time

// Time operations
Date toDateTime(String dateTimeString)  // Parse ISO date string to Date
Date timeToday(String timeString, TimeZone tz = null)  // Today's date + specified time
Date timeTodayAfter(String startTimeString, String timeString, TimeZone tz = null)

// Sunrise/Sunset
Map getSunriseAndSunset(Map options = null)
// options: sunriseOffset (int minutes), sunsetOffset (int minutes)
// Returns: [sunrise: Date, sunset: Date]

Date getTodaysSunset(TimeZone tz = null)
Date getTomorrowsSunset(TimeZone tz = null)
```

### 4.5 Location and Temperature Methods

```groovy
// Temperature
String getTemperatureScale()  // "F" or "C"

// Location access
Location getLocation()        // or just `location`
String location.name
String location.mode
List<Mode> location.modes
TimeZone location.timeZone
BigDecimal location.latitude
BigDecimal location.longitude
Date location.sunrise
Date location.sunset
List<Hub> location.hubs
Hub location.hub

// Hub access
Hub location.hub
String location.hub.name
String location.hub.firmwareVersionString
String location.hub.localIP
String location.hub.zigbeeId
String location.hub.zigbeeEui
Integer location.hub.id
String location.hub.type
String location.hub.hardwareID
Integer location.hub.localSrvPortTCP
Integer location.hub.uptime
```

### 4.6 Logging

```groovy
log.trace "Trace level message"    // Most verbose, white label
log.debug "Debug level message"    // Blue label
log.info "Info level message"      // Green label
log.warn "Warning message"         // Yellow label
log.error "Error message"          // Red label

// Common pattern: conditional logging
if (logEnable) log.debug "Debug: ${someValue}"
if (txtEnable) log.info "${device.displayName} is ${value}"
```

### 4.7 Notification Methods (Apps Only)

```groovy
// Send push notification
sendPush(String message)

// Send notification to specific device
device.deviceNotification(String message)
```

### 4.8 Other Utility Methods

```groovy
// Execution control
void pauseExecution(Long milliseconds)  // Sleep/pause for N ms (use sparingly!)

// Color utilities
String convertHueToGenericColorName(Integer hue, Integer saturation)
// Returns: "Red", "Orange", "Yellow", "Green", "Spring", "Cyan", "Azure", "Blue", "Violet", "Magenta", "Rose", "White"

// Math utilities (standard Groovy/Java Math class available)
Math.round(value)
Math.abs(value)
Math.min(a, b)
Math.max(a, b)

// Encoding
String URLEncoder.encode(String, "UTF-8")
String URLDecoder.decode(String, "UTF-8")

// Hub UID
String getHubUID()
```

### 4.9 Hub Variable Methods

```groovy
// Get all global variables
Map getAllGlobalVars()
// Returns: [varName: [type: xx, value: xx, deviceId: xx], ...]

// Get specific variable
GlobalVariable getGlobalVar(String name)
// Returns: [name: xx, type: xx, value: xx, deviceId: xx, attribute: xx]

// Set variable value
void setGlobalVar(String name, value)

// Register variable as "in use" by this app/driver
void addInUseGlobalVar(String variableName)
void addInUseGlobalVar(List variableNames)

// Remove from "in use"
void removeInUseGlobalVar(String variableName)

// Subscribe to variable changes
subscribe(location, "variable:variableName.value", handler)
```

---

## 5. Platform Objects

### 5.1 Event Object

Properties available on event objects passed to handlers:

```groovy
def handler(evt) {
    evt.name           // String - attribute name (e.g., "switch", "motion")
    evt.value          // String - attribute value (e.g., "on", "active")
    evt.displayName    // String - device display name
    evt.descriptionText // String - human-readable description
    evt.unit           // String - unit of measurement (e.g., "F", "%")
    evt.type           // String - "physical" or "digital"
    evt.isStateChange  // Boolean - whether value changed
    evt.device         // DeviceWrapper - the source device
    evt.deviceId       // Long - device ID
    evt.date           // Date - when event occurred
    evt.data           // String - additional data (JSON string)
    evt.source         // String - event source
    evt.id             // Long - event ID
    evt.unixTime       // Long - epoch milliseconds
    evt.dateValue      // Date - date parsed from value (if applicable)
    evt.numberValue    // Number - numeric parsed value (if applicable)
    evt.floatValue     // Float
    evt.doubleValue    // Double
    evt.integerValue   // Integer
    evt.longValue      // Long
    evt.jsonValue      // Object - JSON parsed value (if applicable)
}
```

### 5.2 Device Object

```groovy
// Properties
device.id                      // Long
device.name                    // String (driver-set)
device.displayName             // String (user-set)
device.deviceNetworkId         // String (DNI)
device.hub                     // Hub
device.endpointId              // String (Zigbee)
device.zigbeeId                // String (Zigbee IEEE)
device.data                    // Map (custom device data)

// Methods
device.currentValue(String attributeName, Boolean skipCache = false)
device.currentState(String attributeName, Boolean skipCache = false)
device.latestValue(String attributeName)
device.latestState(String attributeName)
device.getCurrentStates()      // List of all current states
device.eventsSince(Date, Map options = null)
device.eventsBetween(Date start, Date end, Map options = null)
device.statesSince(String attributeName, Date, Map options = null)
device.getSupportedCommands()
device.getSupportedAttributes()
device.getCapabilities()

// Data methods
device.getDataValue(String key)
device.updateDataValue(String key, String value)
device.removeDataValue(String key)

// Settings
device.updateSetting(String name, Map valueAndType)
device.removeSetting(String name)
device.getSetting(String name)
```

### 5.3 App Object

```groovy
// Properties
app.id                    // Long
app.name                  // String
app.label                 // String
app.installationState     // String

// Methods
app.getSubscribedDeviceById(Long id)
app.sendEvent(Map properties)  // fire app event
app.getLocationEventsSince(String name, Date since)

// Child app management
app.addChildApp(String namespace, String name, String label, Map properties)
app.deleteChildApp(Long id)
app.getChildApps()
app.getAllChildApps()
app.getChildAppById(Long id)
app.getChildAppByLabel(String label)

// Parent access
app.getParent()
```

### 5.4 HubAction Object

```groovy
// Constructor
new hubitat.device.HubAction(
    String action,                    // The command/message to send
    hubitat.device.Protocol protocol, // Protocol enum
    String dni,                       // Device Network ID (optional)
    Map options                       // Additional options (optional)
)

// Protocol enum values:
hubitat.device.Protocol.LAN
hubitat.device.Protocol.TELNET
hubitat.device.Protocol.RAW_LAN
hubitat.device.Protocol.ZWAVE
hubitat.device.Protocol.ZIGBEE

// Options:
//   callback: String - method name for response callback
//   parseWarning: Boolean - send errors to parse/callback method
//   timeout: Integer - response timeout in seconds (1-300, default 10)
//   type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT (for UDP)
//   destinationAddress: String - "ip:port" for UDP

// LAN HTTP example
def action = new hubitat.device.HubAction(
    method: "GET",
    path: "/api/data",
    headers: [HOST: "${deviceIP}:80", "Content-Type": "application/json"],
    null,
    [callback: "handleResponse"]
)
sendHubCommand(action)

// HubMultiAction - send multiple actions
new hubitat.device.HubMultiAction(List<HubAction> actions, hubitat.device.Protocol protocol)
```

---

## 6. Device Interfaces

All interfaces are **driver-only** (cannot be used from apps).

### 6.1 MQTT Interface

```groovy
// Connect to MQTT broker
interfaces.mqtt.connect(String broker, String clientId, String username, String password)
interfaces.mqtt.connect(String broker, String clientId, String username, String password, Map options)
// options: lastWillTopic, lastWillQos, lastWillMessage, lastWillRetain, cleanSession

// Disconnect
interfaces.mqtt.disconnect()

// Publish message
interfaces.mqtt.publish(String topic, String payload, Integer qos = 1, Boolean retained = false)

// Subscribe to topic
interfaces.mqtt.subscribe(String topic, Integer qos = 1)

// Unsubscribe
interfaces.mqtt.unsubscribe(String topic)

// Parse incoming message
def parse(String event) {
    def message = interfaces.mqtt.parseMessage(event)
    log.debug "Topic: ${message.topic}, Payload: ${message.payload}"
}

// Required status callback
def mqttClientStatus(String message) {
    log.debug "MQTT Status: ${message}"
    // message examples: "Status: Connection succeeded", "Error: ..."
}
```

### 6.2 WebSocket Interface

```groovy
// Connect
interfaces.webSocket.connect(String url)
interfaces.webSocket.connect(String url, Map options)
// options: pingInterval (int seconds, default 30), byteInterface (boolean, default false),
//          headers (Map), rawHeaders (String[])

// Disconnect
interfaces.webSocket.close()

// Send message
interfaces.webSocket.sendMessage(String message)

// Required: parse incoming messages (standard parse method)
def parse(String message) {
    log.debug "WS received: ${message}"
}

// Required status callback
def webSocketStatus(String message) {
    log.debug "WebSocket Status: ${message}"
    // Handles disconnections, errors
    if (message.startsWith("status: open")) {
        log.info "WebSocket connected"
    } else if (message.startsWith("failure:")) {
        log.error "WebSocket failure: ${message}"
        // Reconnect logic...
    }
}
```

### 6.3 Raw Socket Interface (TCP)

```groovy
// Connect
interfaces.rawSocket.connect(String host, Integer port)
interfaces.rawSocket.connect(String host, Integer port, Map options)
// options: byteInterface (boolean), readDelay (int ms, default 150), eol (String end-of-line)

// Disconnect
interfaces.rawSocket.close()

// Send message
interfaces.rawSocket.sendMessage(String message)

// Required parse method
def parse(String message) {
    log.debug "Socket received: ${message}"
}

// Required status callback
def socketStatus(String message) {
    log.debug "Socket Status: ${message}"
}
```

### 6.4 Telnet Interface

```groovy
// Connect
telnetConnect(String ip, Integer port, String username, String password)
telnetConnect(Map options, String ip, Integer port, String username, String password)
// options: termChars (List of int), termString (String)
// Pass null for username/password if not required

// Disconnect
telnetClose()

// Send command
sendHubCommand(new hubitat.device.HubAction(command, hubitat.device.Protocol.TELNET))

// Required parse method
def parse(String message) {
    log.debug "Telnet received: ${message}"
}

// Required status callback
def telnetStatus(String message) {
    log.debug "Telnet Status: ${message}"
}
```

### 6.5 EventStream Interface (Server-Sent Events)

```groovy
// Connect
interfaces.eventStream.connect(String url)
interfaces.eventStream.connect(String url, Map options)
// options: rawData (boolean), headers (Map), pingInterval (int), ignoreSSLIssues (boolean)

// Disconnect
interfaces.eventStream.close()

// Required parse method
def parse(String message) {
    log.debug "SSE received: ${message}"
}

// Required status callback
def eventStreamStatus(String message) {
    log.debug "EventStream Status: ${message}"
}
```

---

## 7. Capabilities Reference

### Common Capabilities

| Capability | Commands | Attributes |
|-----------|----------|------------|
| **Actuator** | (none - marker) | (none) |
| **Sensor** | (none - marker) | (none) |
| **Switch** | `on()`, `off()` | `switch` (enum: on, off) |
| **SwitchLevel** | `setLevel(level, duration)` | `level` (number 0-100) |
| **Refresh** | `refresh()` | (none) |
| **Configuration** | `configure()` | (none) |
| **Battery** | (none) | `battery` (number 0-100) |
| **TemperatureMeasurement** | (none) | `temperature` (number) |
| **RelativeHumidityMeasurement** | (none) | `humidity` (number 0-100) |
| **IlluminanceMeasurement** | (none) | `illuminance` (number) |
| **MotionSensor** | (none) | `motion` (enum: active, inactive) |
| **ContactSensor** | (none) | `contact` (enum: open, closed) |
| **AccelerationSensor** | (none) | `acceleration` (enum: active, inactive) |
| **WaterSensor** | (none) | `water` (enum: wet, dry) |
| **PresenceSensor** | (none) | `presence` (enum: present, not present) |
| **Lock** | `lock()`, `unlock()` | `lock` (enum: locked, unlocked, unknown) |
| **DoorControl** | `open()`, `close()` | `door` (enum: open, closed, opening, closing, unknown) |
| **GarageDoorControl** | `open()`, `close()` | `door` (enum: open, closed, opening, closing, unknown) |
| **WindowShade** | `open()`, `close()`, `setPosition(pos)` | `windowShade` (enum), `position` (number) |
| **Alarm** | `both()`, `off()`, `siren()`, `strobe()` | `alarm` (enum: off, siren, strobe, both) |
| **ColorControl** | `setColor(colormap)`, `setHue(hue)`, `setSaturation(sat)` | `color` (string), `hue` (number 0-100), `saturation` (number 0-100), `colorName` (string) |
| **ColorTemperature** | `setColorTemperature(kelvin, level, duration)` | `colorTemperature` (number), `colorName` (string) |
| **ColorMode** | (none) | `colorMode` (enum: CT, RGB) |
| **Thermostat** | `auto()`, `cool()`, `heat()`, `emergencyHeat()`, `off()`, `fanAuto()`, `fanCirculate()`, `fanOn()`, `setCoolingSetpoint(temp)`, `setHeatingSetpoint(temp)`, `setThermostatFanMode(mode)`, `setThermostatMode(mode)`, `setSchedule(json)` | `temperature`, `coolingSetpoint`, `heatingSetpoint`, `thermostatSetpoint`, `thermostatMode`, `thermostatFanMode`, `thermostatOperatingState`, `supportedThermostatModes`, `supportedThermostatFanModes` |
| **PowerMeter** | (none) | `power` (number, watts) |
| **EnergyMeter** | (none) | `energy` (number, kWh) |
| **VoltageMeasurement** | (none) | `voltage` (number) |
| **PushableButton** | `push(buttonNumber)` | `pushed` (number), `numberOfButtons` (number) |
| **HoldableButton** | `hold(buttonNumber)` | `held` (number), `numberOfButtons` (number) |
| **DoubleTapableButton** | `doubleTap(buttonNumber)` | `doubleTapped` (number), `numberOfButtons` (number) |
| **ReleasableButton** | `release(buttonNumber)` | `released` (number), `numberOfButtons` (number) |
| **Valve** | `open()`, `close()` | `valve` (enum: open, closed) |
| **MusicPlayer** | `mute()`, `nextTrack()`, `pause()`, `play()`, `playTrack(uri)`, `previousTrack()`, `restoreTrack(uri)`, `resumeTrack(uri)`, `setLevel(level)`, `setTrack(uri)`, `stop()`, `unmute()` | `level`, `mute`, `status`, `trackData`, `trackDescription` |
| **AudioVolume** | `mute()`, `unmute()`, `setVolume(volume)`, `volumeDown()`, `volumeUp()` | `mute` (enum), `volume` (number 0-100) |
| **SpeechSynthesis** | `speak(text, volume, voice)` | (none) |
| **Notification** | `deviceNotification(text)` | (none) |
| **HealthCheck** | `ping()` | `healthStatus` (enum) |
| **SignalStrength** | (none) | `lqi` (number), `rssi` (number) |
| **CarbonDioxideMeasurement** | (none) | `carbonDioxide` (number) |
| **CarbonMonoxideDetector** | (none) | `carbonMonoxide` (enum: detected, clear, tested) |
| **SmokeDetector** | (none) | `smoke` (enum: detected, clear, tested) |

### Color Map for setColor()
```groovy
// setColor receives a map with hue, saturation, level (0-100 each)
def colorMap = [hue: 50, saturation: 100, level: 100]
device.setColor(colorMap)
```

---

## 8. Groovy Sandbox & Restrictions

### 8.1 Allowed Imports (Partial List)

#### Core Java
- `java.lang.*` (default)
- `java.util.ArrayList`, `HashMap`, `HashSet`, `LinkedHashMap`, `LinkedList`, `TreeMap`, `TreeSet`
- `java.util.Collections`, `Arrays`
- `java.util.Date`, `Calendar`, `TimeZone`, `UUID`
- `java.util.concurrent.Semaphore`, `ConcurrentHashMap`, `CopyOnWriteArrayList`, `AtomicInteger`
- `java.util.regex.Matcher`, `Pattern`

#### Java Time API
- `java.time.*` (LocalDateTime, ZonedDateTime, Duration, Instant, LocalDate, LocalTime, etc.)
- `java.time.format.DateTimeFormatter`
- `java.time.temporal.TemporalAdjusters`, `ChronoUnit`

#### Formatting
- `java.text.SimpleDateFormat`, `DecimalFormat`
- `java.math.BigDecimal`, `BigInteger`, `MathContext`, `RoundingMode`

#### Cryptography & Security
- `java.security.MessageDigest`, `Signature`
- `javax.crypto.Cipher`, `Mac`, `spec.SecretKeySpec`
- `java.security.KeyFactory`, `PrivateKey`
- JOSE/JWT: `com.nimbusds.*` (token handling libraries)

#### Network & Encoding
- `java.net.URLEncoder`, `URLDecoder`
- `java.util.Base64`
- `groovyx.net.http.HttpResponseException`, `ContentType`, `Method`
- `org.apache.http.*` (selected classes)

#### Compression
- `java.util.zip.GZIPInputStream`, `GZIPOutputStream`, `ZipInputStream`, `ZipOutputStream`
- `java.io.ByteArrayInputStream`, `ByteArrayOutputStream`

#### Groovy Libraries
- `groovy.json.JsonSlurper`, `JsonBuilder`, `JsonOutput`, `JsonGenerator`
- `groovy.xml.XmlParser`, `XmlSlurper`, `MarkupBuilder`, `XmlUtil`
- `groovy.time.TimeCategory`
- `groovy.transform.Field`

#### Hubitat-Specific Classes
- `hubitat.device.HubAction`, `HubMultiAction`, `Protocol`
- `hubitat.helper.HexUtils`, `ColorUtils`, `InterfaceUtils`
- `com.hubitat.app.DeviceWrapper`, `ChildDeviceWrapper`
- `com.hubitat.app.EventSubscriptionWrapper`
- `com.hubitat.hub.domain.Hub`, `Location`, `State`, `Event`

### 8.2 NOT Allowed / Restricted

#### Blocked Classes and Packages
- `java.lang.System` (and system-level methods)
- `java.io.File`, `java.io.FileWriter`, `java.io.FileReader` (no filesystem access)
- `java.lang.Thread`, `java.util.Timer` (no direct threading)
- `groovy.sql.Sql` (no direct database access)
- `groovy.transform.CompileStatic` (blocked on some hub models like C-5)
- `groovy.time.TimeDuration` (not allowed)
- Any external JAR libraries not included in the sandbox

#### Key Restrictions
1. **No filesystem access** - Cannot read/write files on the hub
2. **No direct threading** - Cannot create threads; use `runIn()` and scheduling instead
3. **No class loading** - Cannot dynamically load classes
4. **No reflection** - Limited reflection capabilities
5. **No network sockets** - Must use provided interface methods (MQTT, WebSocket, etc.)
6. **No external library imports** - Only whitelisted classes available
7. **Execution time limits** - Long-running code will be terminated
8. **Memory limits** - Excessive memory usage will cause termination
9. **No direct SQL/DB access** - Must use state/atomicState for persistence
10. **No access to other apps'/drivers' state** - Sandboxed per-instance

### 8.3 Groovy 2.4 Specifics

- Groovy 2.4 does NOT support all Groovy 3.x/4.x features
- No method references (`::` operator)
- No lambda expressions (use closures instead)
- Some AST transformations are restricted
- `@CompileStatic` may not work on all hub hardware versions
- Safe navigation operator (`?.`) IS supported
- Spread operator (`*.`) IS supported
- Elvis operator (`?:`) IS supported
- GString interpolation IS supported (`"Hello ${name}"`)

---

## 9. Porting from SmartThings

### 9.1 Critical Changes

| SmartThings | Hubitat | Notes |
|-------------|---------|-------|
| `physicalgraph.*` | `hubitat.*` | Replace all package references |
| `include 'asynchttp_v1'` | Remove line | Use built-in async methods instead |
| `util.toJson()` | `groovy.json.JsonOutput.toJson()` | Import JsonOutput |
| `data.variable` | `device.data.variable` | Qualify data access |
| `pause(int)` | `pauseExecution(long)` | Different name + param type |
| `evt.doubleValue` | `evt.value as double` | Cast manually |
| `sendHubCommand` in drivers | Available since later releases | Was initially missing |
| Simulator section | Remove entirely | Not supported |
| Tiles section | Remove entirely | Not supported |

### 9.2 UI/Input Syntax Differences
- Use `input "field", "text"` not `input "field", type:"text"`
- Sections require braces: `section("title") { ... }`
- Map-type enum options: `[["key":"display"], ...]`
- Set all icon URLs to empty strings `""`

### 9.3 API Endpoint Differences
- Both LOCAL and CLOUD endpoints supported in Hubitat
- Cloud: `getFullApiServerUrl()`, `apiServerUrl()`, `getApiServerUrl()`
- Local: `getFullLocalApiServerUrl()`, `localApiServerUrl()`, `getLocalApiServerUrl()`

### 9.4 Hub/Location Differences
- Hub IDs are `BigInteger`, not UUIDs
- Get coordinates: `location.getLatitude()`, `location.getLongitude()`
- Hub info: `location.hubs[0]`, then use `hub.getDataValue("localIP")`

---

## 10. Best Practices

### 10.1 Performance
- Use `asynchttp*` methods instead of synchronous `http*` methods wherever possible
- Use specific types (`void`, `String`, `Integer`) instead of `def` for improved speed/memory
- Avoid excessive logging in production - use `logEnable` preference pattern
- Auto-disable debug logging after 30 minutes:
  ```groovy
  if (logEnable) runIn(1800, "logsOff")
  ```

### 10.2 Coding Style
- Follow camelCase convention for methods and variables
- Semicolons are optional in Groovy and typically omitted
- Use descriptive method and variable names
- Attributes should be used when users may want to create automations based on value changes
- State should be used for internal data that doesn't need event generation

### 10.3 Common Patterns

#### Updated/Installed Pattern
```groovy
def installed() {
    initialize()
}

def updated() {
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    // All setup goes here
    subscribe(device, "attribute", handler)
    schedule("0 */5 * ? * *", "periodicTask")
}
```

#### Safe Command Execution
```groovy
def safeCommand(cmd) {
    try {
        cmd()
    } catch (Exception e) {
        log.error "Command failed: ${e.message}"
    }
}
```

#### Debug Logging Auto-Disable
```groovy
preferences {
    input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
}

def updated() {
    if (logEnable) runIn(1800, "logsOff")
}

def logsOff() {
    log.warn "Debug logging disabled"
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}
```

### 10.4 Common Pitfalls

1. **Commands must be the last statement** in Z-Wave/Zigbee drivers - logging after a command return can cause failures
2. **`runEvery1Minute` may not work** - test with longer intervals (5, 10, 15, 30 minutes)
3. **No throttling for LAN commands** - unlike SmartThings, Hubitat doesn't rate-limit LAN requests
4. **State vs atomicState** - use atomicState when concurrent access is possible; state is faster but deferred
5. **Interfaces (MQTT, WebSocket, etc.) are driver-only** - cannot be used in apps
6. **Don't use `Thread.sleep()`** - use `pauseExecution()` instead (and use it sparingly)
7. **Hub variable connectors being deprecated** - use direct `getGlobalVar()`/`setGlobalVar()` access instead
8. **Button events need `isStateChange: true`** - otherwise duplicate button presses are filtered
9. **Import statements** - only whitelisted classes can be imported; check the allowed list before trying
10. **`parse()` runs in driver context** - ensure it handles all possible incoming data formats

### 10.5 Libraries (Reusable Code)

```groovy
// Library definition (in Libraries Code editor)
library(
    name: "MyLibrary",
    namespace: "myNamespace",
    author: "Author",
    description: "Shared utility methods"
)

def mySharedMethod() {
    // Reusable code here
}
```

```groovy
// Include in app or driver
#include myNamespace.MyLibrary

// Library code is automatically appended when the app/driver is saved
// All apps/drivers using a library are recompiled when the library changes
```

---

## Zigbee API Reference

### Zigbee Object Methods

```groovy
// Parse message to map
Map zigbee.parseDescriptionAsMap(String description)
// Handles messages starting with "catchall" or "read attr"

// Send command
List zigbee.command(Integer cluster, Integer command, String payload = "", Map additionalParams = [:], Integer delay = 200)
// Example: zigbee.command(0x0006, 0x01) // On command to On/Off cluster

// Convenience commands
List zigbee.on()
List zigbee.off()
List zigbee.setLevel(Integer level, Integer transitionTime = 0)

// Read attribute
List zigbee.readAttribute(Integer cluster, Integer attributeId, Map additionalParams = [:], Integer delay = 200)
List zigbee.readAttribute(Integer cluster, List<Integer> attributeIds, Map additionalParams = [:], Integer delay = 200)

// Write attribute
List zigbee.writeAttribute(Integer cluster, Integer attributeId, Integer dataType, value, Map additionalParams = [:], Integer delay = 200)

// Configure reporting
List zigbee.configureReporting(Integer cluster, Integer attributeId, Integer dataType,
    Integer minReportTime, Integer maxReportTime, Integer reportableChange = null,
    Map additionalParams = [:], Integer delay = 200)

// Additional params commonly used:
// [mfgCode: 0x1234]  // manufacturer-specific
// [destEndpoint: 0x02]  // target endpoint
// [sourceEndpoint: 0x01]  // source endpoint
```

### Z-Wave API Reference

```groovy
// Parse Z-Wave description
hubitat.zwave.Command zwave.parse(String description, Map commandClassVersions = [:])

// Command class version map
def commandClassVersions = [
    0x20: 1,  // Basic
    0x25: 1,  // Switch Binary
    0x26: 3,  // Switch Multilevel
    0x70: 1,  // Configuration
    0x85: 2,  // Association
    0x86: 2   // Version
]

// zwaveEvent handlers
def zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
    sendEvent(name: "switch", value: cmd.value ? "on" : "off")
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
    sendEvent(name: "switch", value: cmd.value ? "on" : "off")
}

// Secure encapsulation (auto-detects security level)
String zwaveSecureEncap(hubitat.zwave.Command cmd)
String zwaveSecureEncap(String cmd)

// Send Z-Wave commands
sendHubCommand(new hubitat.device.HubAction(zwaveSecureEncap(cmd), hubitat.device.Protocol.ZWAVE))

// Or use delayBetween for multiple commands
def cmds = []
cmds << zwaveSecureEncap(zwave.switchBinaryV1.switchBinaryGet())
cmds << zwaveSecureEncap(zwave.configurationV1.configurationGet(parameterNumber: 1))
delayBetween(cmds, 200)
```

---

## Additional Reference

### parseLanMessage (for LAN drivers)
```groovy
def parseLanMessage(String description)
// Returns map with: headers, body, status, json (if JSON), xml (if XML)
```

### delayBetween (for command lists)
```groovy
List delayBetween(List<String> commands, Integer delay = 200)
// Inserts delay between commands in a list
```

### HexUtils
```groovy
hubitat.helper.HexUtils.integerToHexString(Integer value, Integer minBytes)
hubitat.helper.HexUtils.hexStringToInt(String hexString)
hubitat.helper.HexUtils.hexStringToByteArray(String hexString)
hubitat.helper.HexUtils.byteArrayToHexString(byte[] bytes)
```

### ColorUtils
```groovy
hubitat.helper.ColorUtils.rgbToHSV(List<Integer> rgb)  // [r, g, b] -> [h, s, v]
hubitat.helper.ColorUtils.hsvToRGB(List<Integer> hsv)  // [h, s, v] -> [r, g, b]
```

---

*This document was compiled from the official Hubitat documentation (docs2.hubitat.com), community resources (community.hubitat.com), and example code (github.com/hubitat/HubitatPublic). Last updated: March 2026.*
