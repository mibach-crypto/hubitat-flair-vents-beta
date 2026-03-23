# Hubitat Debugging Guide: Exhaustive Reference

## Table of Contents

1. [Hubitat Debugging Tools and Techniques](#1-hubitat-debugging-tools-and-techniques)
2. [Logging System](#2-logging-system)
3. [Common Error Patterns](#3-common-error-patterns)
4. [Debugging Strategies](#4-debugging-strategies)
5. [Common Hubitat Groovy Pitfalls](#5-common-hubitat-groovy-pitfalls)

---

## 1. Hubitat Debugging Tools and Techniques

### 1.1 The Hubitat Execution Environment

- **Groovy Version**: Hubitat uses Groovy 2.4 running inside a sandboxed execution environment.
- **No Local Emulation**: There is no way to fully re-create the Hubitat app or driver sandbox on your own computer. You need a hub to actually test execution. However, basic Groovy syntax can be tested locally before deployment.
- **Sandbox**: The Hubitat Elevation app and driver "sandbox" allows only certain Java/Groovy classes. Attempting to import disallowed classes produces security violations or `ClassNotFoundException`.

### 1.2 Development Tools

**Code Editors**:
- **VSCode** with Groovy syntax extensions is the most popular option, providing syntax highlighting and basic autocomplete.
- The **Hubitat Developer VSCode Plugin** enables automatic code pushing to the hub with hotkeys.
- The **built-in hub code editor** works for simpler projects but lacks advanced features.

**Testing Approaches**:
- Use **virtual devices** to test apps without physical hardware. A "Virtual Switch" using the "Virtual Switch" driver allows sending `on()` and `off()` commands and simulating device behavior.
- The hub has a **"Virtual test device"** option in the "Add Devices" section.
- Manually run commands from the device page to verify commands work before testing with apps.
- Some developers use a **dedicated development hub** separate from their production hub for cleaner log filtering and isolated testing.
- One community member created a **Hubitat emulator in Groovy** for local unit testing, demonstrating that local testing is possible with custom infrastructure, but this is not officially supported.

### 1.3 Hub Monitoring

- **Hub Information Device** driver: monitors free memory and other hub metrics.
- **App Statistics**: available in logs, showing memory state size for each app (most use under 5,000 bytes).
- The hub displays a **"Hub Low on Memory"** alert when free memory drops below 120,000 KB.

---

## 2. Logging System

### 2.1 Log Levels

Hubitat provides five log levels, listed in decreasing order of severity:

| Level | Method | Color in UI | Purpose |
|-------|--------|-------------|---------|
| **error** | `log.error` | Red box | Critical errors requiring investigation |
| **warn** | `log.warn` | Orange/yellow box | Potential problems, not necessarily critical |
| **info** | `log.info` | White box | Normal operational messages, status updates |
| **debug** | `log.debug` | Blue box | Detailed debugging information |
| **trace** | `log.trace` | Gray box | Most verbose; fine-grained tracing |

**When to use each level**:
- `log.error`: Exceptions, failed operations, conditions that prevent normal functioning.
- `log.warn`: Degraded conditions, fallback behavior triggered, recoverable errors.
- `log.info`: Normal operational events, state changes, successful completions.
- `log.debug`: Variable values, method entry/exit, decision points, data inspection.
- `log.trace`: Extremely detailed execution flow, raw data dumps, protocol-level messages.

### 2.2 Basic Usage

```groovy
log.debug "The value of state.foo is ${state.foo}"
log.info "Device ${device.displayName} turned on"
log.warn "Unexpected response code: ${resp.status}"
log.error "Failed to connect: ${e.message}"
log.trace "Raw data received: ${rawData}"
```

**Important**: Use curly braces `${}` around variable names for proper GString interpolation. Without them, Groovy will not substitute variable values.

### 2.3 The logEnable / txtEnable Pattern

Hubitat drivers conventionally use two boolean preferences to control logging:

**`logEnable`** — Enable debug logging:
- Defaults to `true` upon driver installation.
- **Automatically disables after 30 minutes** to prevent log flooding.
- Also auto-disables 30 minutes after being manually re-enabled.
- Used for troubleshooting with driver authors.

**`txtEnable`** — Enable descriptionText logging:
- Defaults to `true` and remains on unless manually disabled.
- Logs human-friendly event descriptions like "Living Room Dimmer level is 50%".
- Shows in the device event log.
- Does NOT show when commands are executed, only when the device generates state changes.

**Standard implementation pattern**:

```groovy
preferences {
    input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
}

def updated() {
    log.info "Updated..."
    if (logEnable) runIn(1800, logsOff)  // Auto-disable after 30 minutes
}

def logsOff() {
    log.warn "Debug logging disabled"
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}

// Usage throughout the driver:
def parse(String description) {
    if (logEnable) log.debug "Parsing: ${description}"
    // ... processing ...
    def descriptionText = "${device.displayName} switch is ${value}"
    if (txtEnable) log.info descriptionText
    sendEvent(name: "switch", value: value, descriptionText: descriptionText)
}
```

### 2.4 descriptionText Convention

The `descriptionText` is a human-friendly description included with events:

```groovy
def descriptionText = "${device.displayName} ${eventName} is ${eventValue}"
// Examples:
// "Living Room Dimmer level is 50%"
// "Bedroom Button pushed is 1 [physical]"
// "Front Door contact is open"
```

It is optional but recommended. It appears in the device event log and is controlled by the `txtEnable` preference. The "Enable descriptionText logging" and "Enable debug logging" options affect **Logs** only and do NOT affect the display of entries in the **Events** tab.

### 2.5 Performance Impact of Logging

- Writing log entries has **little chance of affecting hub performance** under normal circumstances.
- However, **heavy logging activity** (e.g., chatty devices, Z-Wave traffic logging) can increase resource usage.
- Disabling logs does NOT fix underlying device problems; it only hides symptoms.
- Past logs are **periodically pruned** to a specific size, so there are no long-term storage concerns.
- Best practice: keep logging disabled unless actively troubleshooting; enable temporarily during rule setup or device configuration.

### 2.6 Apps vs Drivers Logging

For **apps**, developers typically use an `enableLogging` input preference:

```groovy
preferences {
    section {
        input "enableLogging", "bool", title: "Enable Debug Logging?", required: false, defaultValue: true
    }
}

def logDebug(msg) {
    if (enableLogging) log.debug msg
}
```

For **drivers**, use the standard `logEnable`/`txtEnable` pattern described above.

---

## 3. Common Error Patterns

### 3.1 NullPointerException

**The most common error in Hubitat development.**

Typical forms:
- `java.lang.NullPointerException: Cannot invoke method minus() on null object`
- `java.lang.NullPointerException: Cannot invoke method toInteger() on null object`
- `java.lang.NullPointerException: Cannot invoke method replace() on null object`
- `java.lang.NullPointerException: Cannot invoke method toLong() on null object on line XXXX`
- `java.lang.NullPointerException: Cannot get property 'XXX' on null object`

**Common causes**:
- Accessing a state variable that was never set.
- Referencing a device that has been removed.
- Calling a method on a settings value that the user hasn't configured yet.
- Using `state.someMap.someKey` when `state.someMap` is null.
- Event handler receiving events from devices that no longer exist.

**Prevention strategies**:

```groovy
// Use Groovy safe navigation operator
def value = state.myMap?.myKey?.toInteger()

// Explicit null checks
if (state.myVar != null) {
    def result = state.myVar.toInteger()
}

// Elvis operator for defaults
def value = state.myVar ?: 0

// Safe method calls
device?.displayName ?: "Unknown Device"
```

### 3.2 Timeout Errors

**HTTP Timeouts**:
- `java.net.SocketTimeoutException: Read timed out` — the remote server did not respond in time.
- `org.apache.http.conn.ConnectTimeoutException` — could not establish connection.

**Handling**:
```groovy
try {
    httpGet([uri: "http://example.com", timeout: 10]) { resp ->
        // process response
    }
} catch (java.net.SocketTimeoutException e) {
    log.warn "Request timed out: ${e.message}"
} catch (org.apache.http.conn.ConnectTimeoutException e) {
    log.warn "Connection timed out: ${e.message}"
} catch (Exception e) {
    log.error "HTTP request failed: ${e.message}"
}
```

**Execution Timeouts**:
- Hubitat has execution time limits for apps and drivers. Long-running methods may be terminated by the platform.
- Avoid `pauseExecution()` or `Thread.sleep()` for extended delays; use `runIn()` instead to schedule follow-up work.

### 3.3 State and atomicState Corruption

**state** writes data just before the app goes to sleep (batch write at exit).
**atomicState** commits changes immediately as they are made (write-through).

**Corruption scenarios**:
- Two concurrent executions of the same app both read state, modify it, and write back — the last write wins, losing data from the first.
- Async HTTP callbacks modifying `state.devices` simultaneously cause race conditions where updates are lost.
- Storing mutable objects in state and modifying them in-place without reassignment — changes may not be persisted.

**Prevention**:
```groovy
// Use atomicState for data that may be accessed concurrently
atomicState.counter = (atomicState.counter ?: 0) + 1

// Or use singleThreaded: true in the definition
definition(
    name: "My App",
    namespace: "myNamespace",
    singleThreaded: true
)

// For state, always reassign the entire object after modification
def myList = state.myList ?: []
myList.add(newItem)
state.myList = myList  // Reassign to trigger persistence
```

### 3.4 Event Subscription Failures

**Common cause**: Using capability names instead of attribute names in `subscribe()`.

```groovy
// WRONG - will silently fail or hang
subscribe(selectedDevices, "capability.temperatureMeasurement", handler)

// CORRECT - use the attribute name
subscribe(selectedDevices, "temperature", handler)
```

**Capability-to-attribute mapping** (common ones):
```groovy
@Field static Map capabilityToAttribute = [
    "capability.temperatureMeasurement": "temperature",
    "capability.battery": "battery",
    "capability.contactSensor": "contact",
    "capability.motionSensor": "motion",
    "capability.switch": "switch",
    "capability.switchLevel": "level",
    "capability.lock": "lock",
    "capability.presenceSensor": "presence"
]
```

**filterEvents parameter**: The `subscribe()` method includes a `filterEvents` parameter (defaults to `true`) that ignores events where values did not change, unless `isStateChange` is set on the event. Set to `false` to receive all events:

```groovy
subscribe(device, "temperature", handler, [filterEvents: false])
```

**Subscribe to all events from a device** (v2.2.1+):
```groovy
subscribe(device, "allHandler")  // No attribute specified = all events
```

**Debugging subscribe failures**: Add logging before and after the subscribe call. If the second log line never appears, suspect incorrect parameter types.

```groovy
log.debug "About to subscribe..."
subscribe(myDevice, "switch", switchHandler)
log.debug "Subscribe completed"  // If this never appears, check parameters
```

### 3.5 Scheduling Issues

**Duplicate schedules**: The `overwrite` parameter for `schedule()` and `runIn()` defaults to `true`, canceling previous schedules. Setting it to `false` creates duplicates.

```groovy
// Default: replaces previous schedule for this handler
runIn(60, "myHandler")

// Creates a DUPLICATE schedule
runIn(60, "myHandler", [overwrite: false])
```

**Missed schedules and rapid fire**: If the `misfire` parameter is set to `"ignore"`, the scheduler tries to fire as soon as it can. If several firings were missed, several rapid firings may occur as the scheduler catches up.

**runIn not working from updated()**: A common issue where `runIn()` works from `configure()` but fails from `updated()`. Root cause: a broad `unschedule()` call in the `updated()` call stack cancels the newly created `runIn()`. Fix: make `unschedule()` calls specific.

```groovy
// WRONG - cancels everything including the runIn you just created
def updated() {
    unschedule()        // Cancels ALL scheduled jobs
    runIn(30, "refresh") // This gets canceled by the unschedule above
}

// CORRECT - be specific about what to unschedule
def updated() {
    unschedule("refresh")  // Only cancel the specific handler
    runIn(30, "refresh")
}
```

**Method name quoting**: Use quoted strings for method names in `runIn()`:
```groovy
// SAFER - explicit string
runIn(30, "refreshChildren")

// RISKY - could be shadowed by a property with the same name
runIn(30, refreshChildren)
```

**Cron scheduling format**: Hubitat uses a 7-parameter Quartz cron string:
```
"Seconds Minutes Hours DayOfMonth Month DayOfWeek Year"
```

**Common scheduling bugs**:
- Schedules that randomly stop working (often after hub reboot if no `systemStart` subscription).
- Methods that work for a while then stop without errors.
- Scheduling from "Save Preferences" vs clicking other commands behaves differently in some edge cases.

### 3.6 HTTP Request Failures

**Synchronous HTTP methods** (`httpGet`, `httpPost`, etc.):
```groovy
try {
    httpGet([uri: "https://api.example.com/data", timeout: 15]) { resp ->
        if (resp.status == 200) {
            def data = resp.data
            // process data
        } else {
            log.warn "Unexpected status: ${resp.status}"
        }
    }
} catch (groovy.net.http.HttpResponseException e) {
    log.error "HTTP error: ${e.statusCode} - ${e.message}"
} catch (java.net.SocketTimeoutException e) {
    log.warn "Timeout: ${e.message}"
} catch (Exception e) {
    log.error "Request failed: ${e.message}"
}
```

**Asynchronous HTTP methods** (`asynchttpGet`, `asynchttpPost`, etc.):
```groovy
def fetchData() {
    def params = [
        uri: "https://api.example.com/data",
        requestContentType: "application/json",
        headers: ["Authorization": "Bearer ${state.token}"],
        timeout: 15
    ]
    asynchttpGet("handleResponse", params, [context: "fetchData"])
}

def handleResponse(response, data) {
    if (response.hasError()) {
        log.error "Async request failed: ${response.getErrorMessage()}"
        return
    }
    if (response.status == 200) {
        try {
            def json = response.getJson()
            // process json
        } catch (Exception e) {
            log.error "Failed to parse response: ${e.message}"
            log.debug "Raw response: ${response.getData()}"
        }
    } else {
        log.warn "Unexpected status: ${response.status}"
    }
}
```

**AsyncResponse methods**:
- `int getStatus()` — HTTP status code
- `Map<String, String> getHeaders()` — Response headers
- `String getData()` — Response body as string
- `Object getJson()` — Parsed JSON (as Map/List)
- `GPathResult getXml()` — Parsed XML structure
- `boolean hasError()` — Error flag
- `String getErrorMessage()` — Error description

**Known issues**:
- Async HTTP requests **cannot be canceled** once sent; they complete or timeout naturally.
- Earlier firmware versions had a JSON parsing bug where `getJson()` returned a string instead of parsed objects.
- Character encoding issues with non-UTF-8 responses.
- 408 responses from async HTTP actions may not include the data map in the callback.
- `httpGet` response returning null object is a known issue — always check for null.

### 3.7 JSON Parsing Errors

```groovy
import groovy.json.JsonSlurper

// Parsing JSON from a string
try {
    def slurper = new JsonSlurper()
    def result = slurper.parseText(jsonString)
} catch (groovy.json.JsonException e) {
    log.error "JSON parse error: ${e.message}"
    log.debug "Attempted to parse: ${jsonString}"
}

// Parsing response data — response.data may already be parsed
// Don't double-parse!
httpGet(params) { resp ->
    def data = resp.data  // This may already be a parsed object
    // If you need to parse from raw string:
    // def data = new JsonSlurper().parseText(resp.getData())
}
```

**Pitfall**: Using `JsonSlurper` inside a driver's `parse()` method can throw `groovy.lang.MissingMethodException` if the method signature does not match. Ensure correct import and usage.

**Lax parsing**: For non-strict JSON, use `JsonParserType.LAX`:
```groovy
import groovy.json.JsonSlurper
import groovy.json.JsonParserType

def slurper = new JsonSlurper().setType(JsonParserType.LAX)
def result = slurper.parseText(relaxedJsonString)
```

### 3.8 Device Communication Failures

**LAN devices**:
- Use `HubAction` for HTTP, TCP, UDP, WOL, and UPnP SSDP messages.
- Incoming traffic to port 39501 on the hub is routed to a device with a DNI matching the IP address or MAC address of the source.
- LAN devices vary greatly in their communication protocols; documented APIs offer the best chance at a working driver.

```groovy
import hubitat.device.HubAction
import hubitat.device.Protocol

def sendCommand(String cmd) {
    def action = new HubAction(
        method: "GET",
        path: "/api/${cmd}",
        headers: [HOST: "${state.ip}:${state.port}"]
    )
    sendHubCommand(action)
}
```

**Common communication issues**:
- Device IP address changed (use DHCP reservation).
- DNI (Device Network ID) mismatch preventing response routing.
- Firewall blocking hub-to-device communication.
- Device API rate limiting.

### 3.9 Hub Resource Exhaustion

**Symptoms**:
- Very slow web interface, delayed clicks.
- Motion sensors delayed or not responding.
- Voice commands delayed.
- "Hub Low on Memory" alert.

**Common causes**:
- Frequent Maker API `getDevices()` calls (e.g., every 2 seconds).
- Z-Wave polling consuming memory.
- Large state variables in apps/drivers.
- Memory leaks from poorly written custom apps.
- Too many chatty devices generating excessive events.

**Monitoring and diagnosis**:
- Use the **Hub Information Device** driver to track free memory over time.
- Check **App Statistics** in logs for memory state sizes.
- Memory threshold for warnings: ~120,000 KB.
- Most apps should use under 5,000 bytes of state.

**Resolution**:
- Reduce polling frequency.
- Minimize state variable size.
- Use `@Field` variables for runtime-only data (kept in memory, not persisted to DB).
- Remove or disable problematic custom apps.
- Reboot the hub to clear memory (temporary fix).

---

## 4. Debugging Strategies

### 4.1 Live Logging vs Past Logs

**Live Logs** (default view):
- Starts blank; data appears as apps/devices generate entries.
- Real-time streaming of log entries.
- Best for: actively watching behavior while triggering automations.
- Strategy: open Logs in a separate browser tab, trigger the automation or device action, watch for errors.

**Past Logs**:
- Shows recent log history as a snapshot (does not update in real time).
- Contains only entries that existed when the page was loaded.
- Best for: reviewing what happened after the fact, scanning for error patterns.
- Tip: scan Past Logs for "error" or "warning" entries to identify problems.
- Periodically pruned to a specific size.

### 4.2 Log Filtering and Navigation

- **Click device/app name** at the top of the log page to filter to only that item's logs.
- **Click the event type** in an error entry to navigate to the related app or device.
- Each log entry shows:
  - The affected device or app (e.g., `app:2` or `device:15`) on the far left.
  - Error line numbers pinpointing the problematic code line.
  - The log level tag (debug, info, warn, error, trace).

### 4.3 Debugging state and atomicState

**Inspecting state values**:
```groovy
// Log the entire state object
log.debug "Current state: ${state}"

// Log specific state variables
log.debug "state.devices = ${state.devices}"
log.debug "state.lastRun = ${state.lastRun}"

// Check state variable types
log.debug "Type of state.counter: ${state.counter?.class}"
```

**Key difference**:
- `state`: read at startup, written at exit of event execution. Other threads will NOT see changes until the writing thread exits.
- `atomicState`: written to and read from the database immediately. Changes are visible "as they happen." Available for **apps only**, NOT drivers.
- Both serialize/deserialize to/from JSON, which can cause type coercion issues (see Section 5).

**Debugging concurrent state issues**:
```groovy
// Add timestamps to track ordering
atomicState.lastUpdate = [time: now(), method: "myMethod", value: newValue]
log.debug "Updated at ${now()} from myMethod: ${newValue}"
```

### 4.4 Testing Apps Without Real Devices

1. **Create virtual devices**: Use Devices > Add Device > Virtual to create Virtual Switch, Virtual Contact Sensor, Virtual Motion Sensor, etc.
2. **Manual command testing**: From the device page, use command buttons (e.g., On, Off, Open, Close) to simulate device actions.
3. **Verify commands work**: If a command doesn't work from the device page, it won't work from any app either.
4. **Use multiple virtual devices**: Create a complete virtual setup to test multi-device automations.
5. **Check the Events tab**: After triggering actions, check the device's Events tab for event history.

### 4.5 Isolating Issues in Parent/Child Apps

**Architecture**:
- Parent app code must be created FIRST before adding child app code (otherwise you get an error).
- Child apps communicate with parent via parent methods.
- Parent apps communicate with children via `childApps` collection.

**Debugging parent-child communication**:
```groovy
// In parent app
def getChildData() {
    childApps.each { child ->
        log.debug "Child app ${child.id}: ${child.label}"
    }
}

// In child app
def notifyParent(data) {
    log.debug "Sending to parent: ${data}"
    parent.receiveFromChild(data)
    log.debug "Parent notified successfully"
}
```

**Common issues**:
- `NullPointerException` when child tries to call parent method that doesn't exist.
- Parent not finding child apps after hub reboot (subscriptions not restored).
- State not shared between parent and child (each has its own state).

### 4.6 HTTP Request/Response Debugging

```groovy
// Debug the request parameters
log.debug "Sending request to: ${params.uri}"
log.debug "Headers: ${params.headers}"
log.debug "Body: ${params.body}"

// Debug the response
httpGet(params) { resp ->
    log.debug "Status: ${resp.status}"
    log.debug "Content-Type: ${resp.contentType}"
    log.debug "Headers: ${resp.headers.collect { "${it.name}: ${it.value}" }}"
    log.debug "Data: ${resp.data}"
}

// For async calls, debug in the callback
def handleResponse(response, data) {
    log.debug "Async response status: ${response.status}"
    log.debug "Has error: ${response.hasError()}"
    if (response.hasError()) {
        log.debug "Error: ${response.getErrorMessage()}"
    }
    log.debug "Raw data: ${response.getData()}"
    log.debug "Passed data: ${data}"
}
```

### 4.7 Event Inspection

**Device Events tab**: Shows recent event history including:
- Date and time stamp of when an event occurred.
- Event type (e.g., switch, level, motion).
- Whether it was **physical** (someone physically operated the device) or **digital** (triggered by an app or rule).
- The triggering app listed under "Triggered apps" section.

**Debugging event flow**:
```groovy
def eventHandler(evt) {
    log.debug "Event received:"
    log.debug "  name: ${evt.name}"
    log.debug "  value: ${evt.value}"
    log.debug "  displayName: ${evt.displayName}"
    log.debug "  descriptionText: ${evt.descriptionText}"
    log.debug "  source: ${evt.source}"
    log.debug "  isStateChange: ${evt.isStateChange}"
    log.debug "  type: ${evt.type}"        // physical or digital
    log.debug "  device: ${evt.device}"
    log.debug "  deviceId: ${evt.deviceId}"
    log.debug "  date: ${evt.date}"
}
```

### 4.8 Device Event History Analysis

```groovy
// Get recent events for a device
def events = device.events(max: 50)
events.each { evt ->
    log.debug "${evt.date} - ${evt.name}: ${evt.value} (${evt.type})"
}

// Get events since a specific time
def since = new Date() - 1  // 24 hours ago
def recentEvents = device.eventsSince(since)
```

---

## 5. Common Hubitat Groovy Pitfalls

### 5.1 Import Restrictions and ClassNotFoundException

Hubitat's sandbox allows only specific Java/Groovy classes. The official allowed imports list is maintained at `https://docs2.hubitat.com/en/developer/allowed-imports`.

**Common disallowed classes**:
- Custom class definitions inside apps may fail.
- Third-party libraries cannot be imported.
- Some `java.util.concurrent.atomic` classes (e.g., `AtomicLong`) are not available.
- `@Synchronized` annotation is not supported.

**What IS allowed**:
- `java.util.concurrent` classes like `ConcurrentLinkedQueue` and `Semaphore`.
- `synchronized()` keyword on shared objects.
- `@Field` declarations.
- Standard Groovy collections and utilities.
- `groovy.json.JsonSlurper` and `groovy.json.JsonOutput`.
- `hubitat.device.HubAction` and related Hubitat classes.
- `groovy.transform.Field`.

**Debugging ClassNotFoundException**:
```groovy
// If you see: "Importing [ClassName] is not allowed"
// Check the allowed imports list and find an alternative

// Example: Instead of java.util.concurrent.atomic.AtomicLong
// Use atomicState for thread-safe counters:
atomicState.counter = (atomicState.counter ?: 0) + 1
```

### 5.2 Sandbox Security Violations

**Typical error**: `SecurityException` when attempting operations outside the sandbox.

**Common triggers**:
- File system access (not allowed).
- Network operations outside of provided HTTP methods.
- Reflection or dynamic class loading.
- Creating custom threads directly.

### 5.3 Threading and Concurrency Issues

**Execution model**: Hubitat uses a multi-threaded execution environment. Commands and events can run simultaneously. The platform does NOT provide built-in thread safety primitives at the app/driver level.

**state vs atomicState under concurrency**:
- `state`: Read at startup, written at exit. Other threads do NOT see changes until the writing thread exits.
- `atomicState`: Written to and read from DB immediately. BUT requires serializing objects, modifying copies, then re-storing — this does NOT guarantee thread safety despite the name.

**singleThreaded option** (v2.2.9+):
```groovy
definition(
    name: "My App",
    namespace: "myNamespace",
    singleThreaded: true  // Queue all method calls, execute one at a time
)
```
- Hub executes all methods sequentially for this instance.
- Requests are queued FIFO.
- **Less overhead than atomicState**.
- Only applies to top-level methods called by the hub core, not internal method calls.
- Treats each method as a transaction, automatically committing changes even if exceptions occur.

**Available concurrency tools**:
- `synchronized()` keyword.
- `java.util.concurrent.ConcurrentLinkedQueue`.
- `java.util.concurrent.Semaphore`.
- `@Field` variables (reduce but don't eliminate race windows).

**NOT available**:
- `@Synchronized` annotation.
- `java.util.concurrent.atomic.AtomicLong`.

**Common race condition patterns**:
```groovy
// DANGEROUS - async callbacks stomping on each other
def callback1(response, data) {
    def devices = state.devices  // Both callbacks read same state
    devices[ip1] = device1
    state.devices = devices      // Second callback overwrites first
}

// SAFER - use atomicState with explicit merge
def callback1(response, data) {
    def devices = atomicState.devices ?: [:]
    devices[ip1] = device1
    atomicState.devices = devices  // Immediate write, but still not atomic
}

// SAFEST - use singleThreaded: true or sequential processing
```

### 5.4 Memory Limits and How to Avoid Them

**State size guidelines**:
- State data is serialized/deserialized to/from JSON on every execution.
- Large state variables slow down every execution of your app/driver.
- Most apps should use under 5,000 bytes of state.
- State around 63,000 bytes has been reported to work but is not recommended.
- No officially documented hard limit, but performance degrades with size.

**Strategies to reduce memory usage**:

```groovy
// Use @Field for runtime-only data (not persisted to DB)
@Field static Map cache = [:]  // Shared across all instances, lives in memory only

// Don't store large data in state
// BAD:
state.allDeviceData = [/* huge map */]

// BETTER: Store only what you need
state.lastDeviceId = deviceId
state.lastValue = value

// Use data device storage for large persistent data
// or make API calls to fetch data on demand
```

**@Field variables**:
- Declared at the top level with `@Field` annotation.
- Stored in memory between runs, NOT persisted to the database.
- Shared among all instances of the same app/driver.
- Lost on hub reboot.
- Useful for caching data that can be regenerated.

### 5.5 String Handling in the Hubitat Sandbox

**GString vs String**:
```groovy
// GString (interpolated) - use double quotes
def greeting = "Hello, ${name}!"

// Plain String - use single quotes
def literal = 'No interpolation here: ${name}'  // Literal text, no substitution

// Multiline strings
def multi = """
    Device: ${device.displayName}
    Value: ${value}
"""
```

**Safe navigation for null strings**:
```groovy
// Safe navigation operator prevents NPE
def upper = myString?.toUpperCase()  // Returns null if myString is null

// Elvis operator for defaults
def name = deviceName ?: "Unknown"

// Safe chaining
def length = response?.data?.toString()?.length() ?: 0
```

**Common string pitfalls**:
- GString objects are not the same as String objects in some contexts (e.g., as Map keys).
- Converting GString to String: `"${value}".toString()` or `"$value" as String`.

### 5.6 Date/Time Handling Issues

**Common errors**:
- `groovy.lang.MissingMethodException: No signature of method: toDateTime() is applicable for argument types: (java.util.Date)` — `toDateTime()` does not accept Date objects.
- `Cannot invoke method minus() on null object` — time input types are stored as strings, not numbers.

**Correct patterns**:
```groovy
// Use timeToday with location.timeZone
def today = timeToday(null, location.timeZone)

// Get current timestamp
def now = now()  // Returns Long milliseconds

// Use TimeCategory for date arithmetic
import groovy.time.TimeCategory
use(TimeCategory) {
    def tomorrow = new Date() + 1.day
    def anHourAgo = new Date() - 1.hour
}

// Convert time input (string) to Date
def timeValue = timeToday(settings.myTime, location.timeZone)

// Format dates
def formatted = new Date().format("yyyy-MM-dd HH:mm:ss", location.timeZone)
```

**Pitfalls**:
- Time inputs from user preferences are stored as **strings**, not Date objects.
- Always use `location.timeZone` to ensure correct timezone.
- `now()` returns epoch milliseconds (Long), not a Date object.
- Storing Date objects in state causes serialization issues — store as epoch Long instead.

### 5.7 Collection Manipulation Pitfalls

**ConcurrentModificationException**:
```groovy
// WRONG - modifying collection while iterating
myList.each { item ->
    if (item.expired) myList.remove(item)  // ConcurrentModificationException!
}

// CORRECT - use removeAll with a closure
myList.removeAll { it.expired }

// CORRECT - iterate over a copy
def copy = myList.collect()
copy.each { item ->
    if (item.expired) myList.remove(item)
}

// CORRECT - use Iterator.remove()
def iter = myList.iterator()
while (iter.hasNext()) {
    if (iter.next().expired) iter.remove()
}
```

**State serialization type coercion**:
```groovy
// Map keys stored in state change type!
state.myMap = [1: "one", 2: "two"]  // Integer keys
// After retrieval:
state.myMap.each { k, v ->
    log.debug "Key type: ${k.class}"  // String, NOT Integer!
}

// Workaround: always use String keys in maps stored in state
state.myMap = ["1": "one", "2": "two"]
```

**ArrayList race condition**:
- `removeAt(0)` on a list with 2 items can result in 0 items if concurrent modification occurs.
- Use `ConcurrentLinkedQueue` or `singleThreaded: true` for shared collections.

### 5.8 Lifecycle Method Pitfalls

**App lifecycle**:
```groovy
def installed() {
    log.debug "Installed"
    initialize()
}

def updated() {
    log.debug "Updated"
    unsubscribe()    // Remove old subscriptions
    unschedule()     // Remove old schedules
    initialize()     // Re-initialize
}

def uninstalled() {
    log.debug "Uninstalled"
    // Cleanup: remove child devices, etc.
}

def initialize() {
    // Set up subscriptions and schedules
    subscribe(mySwitch, "switch", switchHandler)
    runEvery1Hour("refreshData")
}
```

**Critical: Hub reboot behavior**:
- `installed()` and `updated()` are NOT called after hub reboot.
- `initialize()` has NO special significance — it is purely a naming convention.
- To respond to hub reboots, subscribe to the `systemStart` event:
```groovy
subscribe(location, "systemStart", "hubRebootHandler")

def hubRebootHandler(evt) {
    log.info "Hub rebooted, re-initializing..."
    initialize()
}
```

**unsubscribe() and unschedule() edge cases**:
- Calling `unsubscribe()` or `unschedule()` does NOT block until callbacks complete.
- Any callback currently running WILL run to completion.
- New subscriptions cease, but in-flight events are not canceled.
- Calling `unschedule()` without parameters cancels ALL scheduled jobs, including any `runIn()` calls you just set up.
- Never block in `updated()` with mutex/semaphore — it hangs the UI indefinitely.

### 5.9 sendEvent Pitfalls

```groovy
// Basic sendEvent
sendEvent(name: "switch", value: "on", descriptionText: "${device.displayName} is on")

// isStateChange: force event even if value hasn't changed
// Often needed for button events
sendEvent(name: "pushed", value: 1, isStateChange: true)

// WRONG - using createEvent (deprecated in some contexts)
// Use sendEvent instead

// descriptionText format convention
def descriptionText = "${device.displayName} ${eventName} is ${eventValue}"
```

### 5.10 Driver-Specific Pitfalls

**Ensure correct driver assignment**:
- If you modify code but see no effect, verify the device is using YOUR custom driver, not Hubitat's default driver.
- After changing driver TYPE, click SAVE, then revisit device details and click CONFIGURE.

**parse() method timing**:
- For Zigbee/Z-Wave drivers, `parse()` only executes when the physical device sends a message.
- Simply editing code won't trigger logging — you must cause the device to transmit data.
- If you see "debug logging is disabled," the device may not be using your custom driver code.

**Driver state vs app state**:
- Drivers do NOT have `atomicState` — only `state`.
- For concurrency in drivers, use `singleThreaded: true` or `@Field` variables.
- State updates in drivers: "state.var value updates I have given up relying on in drivers" — consider using events and acknowledgments instead.

### 5.11 Common Method Signature Errors

**MissingMethodException**:
```groovy
// Often caused by incorrect parameter types
// Example: passing a TimeZone where a String is expected
// Fix: check the Hubitat API documentation for correct method signatures

// Common in scheduling:
// groovy.lang.MissingMethodException related to scheduling methods
// Check: are you passing a closure instead of a method name string?

// WRONG:
runIn(60, { doSomething() })

// CORRECT:
runIn(60, "doSomething")
```

### 5.12 Dynamic Page vs Static Preferences

**atomicState in preferences**:
```groovy
// WRONG - atomicState variables can't be used in static preferences
preferences {
    input "device", "enum", options: atomicState.discoveredDevices
    // Error: "Cannot get property 'discoveredDevices' on null object"
}

// CORRECT - use dynamicPage
preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Setup") {
        section {
            input "device", "enum", options: atomicState.discoveredDevices
        }
    }
}
```

---

## Quick Reference: Debugging Checklist

1. **Check Logs first**: Open Live Logs, trigger the problematic action, look for red error entries.
2. **Read the error line number**: The log shows exactly which line failed.
3. **Verify driver assignment**: Make sure the device is using your driver, not a default.
4. **Check null values**: Most errors are NullPointerException — add safe navigation `?.` operators.
5. **Verify subscribe parameters**: Use attribute names, not capability names.
6. **Check scheduling conflicts**: Ensure `unschedule()` isn't canceling your new schedules.
7. **Inspect state**: Log `state` and `atomicState` values to verify they contain what you expect.
8. **Watch for type coercion**: State serialization converts Integer map keys to Strings.
9. **Test with virtual devices**: Isolate app logic from device communication issues.
10. **Check hub memory**: If the hub is sluggish, check free memory via Hub Information Device.
11. **Hub reboot recovery**: Verify your app subscribes to `systemStart` if it needs to re-initialize.
12. **Concurrency issues**: If data is lost or corrupted, consider `singleThreaded: true` or `atomicState`.

---

## Quick Reference: Log Level Decision Tree

```
Is it a critical failure that prevents normal operation?
  YES -> log.error
  NO  -> Is it a degraded or unexpected condition?
    YES -> log.warn
    NO  -> Is it a normal operational event the user should know about?
      YES -> log.info (with txtEnable check for drivers)
      NO  -> Is it useful for debugging specific issues?
        YES -> log.debug (with logEnable check)
        NO  -> Is it extremely detailed protocol/data tracing?
          YES -> log.trace
          NO  -> Don't log it
```
