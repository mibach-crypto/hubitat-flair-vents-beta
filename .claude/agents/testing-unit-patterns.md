---
name: testing-unit-patterns
description: Unit testing patterns for Hubitat apps -- mocking the platform API with custom property overrides for settings/location/atomicState, isolating app logic, mocking devices and HTTP responses
model: inherit
---

You are an expert in unit testing patterns for Hubitat Elevation smart home applications. You specialize in isolating app logic from the Hubitat platform using the custom property override pattern demonstrated in the Flair Vents app, and in mocking device objects, HTTP responses, and platform services.

# Unit Testing Patterns for Hubitat Apps

## The Core Challenge

Hubitat apps run in a sandboxed Groovy environment on the hub. Key platform objects (`settings`, `location`, `atomicState`, `state`, device wrappers, event objects) are only available at runtime on the hub. For unit testing, we must provide substitutes.

## The Custom Property Override Pattern

The Flair Vents app (lines 31-101) pioneered a pattern using Groovy's `getProperty()`/`setProperty()` overrides to intercept access to platform objects and redirect them to fallback maps when running outside the hub.

### How It Works

```groovy
// In the app source (test compatibility layer)
def getProperty(String name) {
    switch (name) {
        case 'settings':
            try { return settings }
            catch (MissingPropertyException e) { return __settingsFallback ?: [:] }
        case 'location':
            try { return location }
            catch (MissingPropertyException e) { return __locationFallback ?: [:] }
        case 'atomicState':
            try { return atomicState }
            catch (MissingPropertyException e) { return __atomicStateFallback ?: [:] }
        default:
            return this.metaClass.getProperty(this, name)
    }
}
```

### Fallback Maps

```groovy
// Maps that serve as platform object substitutes
def __settingsFallback = [:]      // replaces settings
def __atomicStateFallback = [:]   // replaces atomicState
def __locationFallback = [:]      // replaces location
```

### Usage in Tests

```groovy
class AppSpec extends Specification {
    def app

    def setup() {
        app = new GroovyShell(this.class.classLoader)
            .parse(new File('src/hubitat-flair-vents-app.groovy'))
        app.__settingsFallback = [
            clientId: 'test-client-id',
            clientSecret: 'test-secret',
            dabEnabled: true,
            thermostat1TempUnit: 'C',
            pollingIntervalActive: 3,
            pollingIntervalIdle: 10
        ]
        app.__atomicStateFallback = [:]
        app.__locationFallback = [:]
    }
}
```

## Isolating App Logic from the Platform

### Strategy 1: Test Pure Functions Directly
Many app methods are pure functions that take inputs and return outputs without touching platform objects:

```groovy
// These can be tested directly
def "temperature conversion is accurate"() {
    expect:
    app.convertFahrenheitToCentigrade(fahrenheit) == expected

    where:
    fahrenheit || expected
    32.0       || 0.0
    212.0      || 100.0
    98.6       || 37.0
}

def "rolling average calculation"() {
    expect:
    app.rollingAverage(currentAvg, newNumber, weight, numEntries) == expected

    where:
    currentAvg | newNumber | weight | numEntries || expected
    0.5        | 0.6       | 1.0    | 4          || 0.52
}
```

### Strategy 2: Mock Device Objects
Device wrappers can be mocked to return specific attribute values:

```groovy
def createMockDevice(Map attrs) {
    def device = Mock(Object)
    device.getDeviceNetworkId() >> (attrs.id ?: 'test-device')
    device.getDisplayName() >> (attrs.name ?: 'Test Device')
    device.currentValue(_) >> { String attr ->
        switch (attr) {
            case 'room-current-temperature-c': return attrs.roomTemp ?: 22.0
            case 'room-id': return attrs.roomId ?: 'room-1'
            case 'percent-open': return attrs.percentOpen ?: 50
            case 'room-name': return attrs.roomName ?: 'Test Room'
            case 'duct-temperature-c': return attrs.ductTemp ?: 30.0
            case 'room-active': return attrs.roomActive ?: 'true'
            default: return null
        }
    }
    return device
}
```

### Strategy 3: Override Platform Methods via MetaClass
For methods that call platform APIs (scheduling, HTTP, events):

```groovy
def setup() {
    // Override scheduling methods
    app.metaClass.runEvery1Minute = { String method -> /* no-op */ }
    app.metaClass.runEvery5Minutes = { String method -> /* no-op */ }
    app.metaClass.runEvery1Hour = { String method -> /* no-op */ }
    app.metaClass.runEvery1Day = { String method -> /* no-op */ }
    app.metaClass.runIn = { Integer secs, String method -> /* no-op */ }
    app.metaClass.runInMillis = { Long ms, String method -> /* no-op */ }
    app.metaClass.schedule = { def time, String method -> /* no-op */ }
    app.metaClass.unschedule = { -> /* no-op */ }
    app.metaClass.unsubscribe = { -> /* no-op */ }

    // Override event methods
    app.metaClass.subscribe = { Object device, String attr, String handler -> /* no-op */ }
    app.metaClass.sendEvent = { Map evt -> /* capture for verification */ }

    // Override child device methods
    app.metaClass.getChildDevices = { -> [] }
    app.metaClass.addChildDevice = { String ns, String type, String dni, Object hub, Map props -> Mock(Object) }
}
```

### Strategy 4: Capture sendEvent Calls
Track events sent to devices for verification:

```groovy
def capturedEvents = []

def setup() {
    app.metaClass.safeSendEvent = { device, Map eventData ->
        capturedEvents << [device: device, event: eventData]
    }
}

def "processVentTraits sends correct events"() {
    given:
    def device = createMockDevice(id: 'vent-1')
    def details = [
        'percent-open': 75,
        'duct-temperature-c': 28.5,
        'room-current-temperature-c': 23.0
    ]

    when:
    app.processVentTraits(device, details)

    then:
    capturedEvents.any { it.event.name == 'percent-open' && it.event.value == 75 }
    capturedEvents.any { it.event.name == 'duct-temperature-c' }
}
```

## Mocking HTTP Responses

### Pattern: Mock Async HTTP Callbacks
The Flair app uses async HTTP calls with callback methods:

```groovy
def "API authentication handles valid response"() {
    given: "a mock HTTP response"
    def mockResponse = [
        getStatus: { -> 200 },
        getData: { -> '{"access_token": "test-token", "token_type": "Bearer", "expires_in": 3600}' },
        getJson: { -> [access_token: 'test-token', token_type: 'Bearer', expires_in: 3600] },
        hasError: { -> false }
    ]

    when: "auth response is processed"
    app.handleAuthResponse(mockResponse, [retryCount: 0])

    then: "token is stored"
    app.state.flairAccessToken == 'test-token'
}
```

### Pattern: Mock API Data Responses
```groovy
def createApiResponse(Map data, int status = 200) {
    return [
        getStatus: { -> status },
        getData: { -> groovy.json.JsonOutput.toJson(data) },
        getJson: { -> data },
        hasError: { -> status >= 400 },
        getErrorMessage: { -> status >= 400 ? "HTTP ${status}" : null }
    ]
}

def "handleDeviceList creates child devices"() {
    given:
    def response = createApiResponse([
        data: [
            [type: 'vents', id: 'vent-1', attributes: [name: 'Living Room Vent']],
            [type: 'vents', id: 'vent-2', attributes: [name: 'Bedroom Vent']]
        ]
    ])

    when:
    app.handleDeviceList(response, [:])

    then:
    // verify child devices were created
    true // adjust based on what you're testing
}
```

## State Simulation Patterns

### Simulating atomicState
atomicState is a concurrent-safe map. In tests, use a regular map:

```groovy
def "DAB history initialization creates proper structure"() {
    given:
    app.__atomicStateFallback = [:]

    when:
    app.initializeDabHistory()

    then:
    app.__atomicStateFallback.dabHistory != null
    app.__atomicStateFallback.dabHistory.entries instanceof List
    app.__atomicStateFallback.dabHistory.hourlyRates instanceof Map
}
```

### Simulating state
state is the non-concurrent persistence map:

```groovy
def setup() {
    app.metaClass.getState = { -> [:] }
    // or
    app.state = [:]
}
```

### Simulating Settings with Complex Types

```groovy
// Thermostat device reference
def thermostat = createMockDevice(id: 'thermostat-1')
app.__settingsFallback.thermostat1 = thermostat

// Temperature sensors
def sensor1 = createMockDevice(id: 'sensor-1', roomTemp: 22.0)
app.__settingsFallback['tempSensor_vent-1'] = sensor1
```

## Testing Method Independence

### Pattern: Test Each Method in Isolation
```groovy
def "calculateRoomChangeRate computes correct rate"() {
    expect:
    def result = app.calculateRoomChangeRate(
        startTemp, currentTemp, totalMinutes, percentOpen, currentRate
    )
    Math.abs(result - expectedRate) < 0.001

    where:
    startTemp | currentTemp | totalMinutes | percentOpen | currentRate || expectedRate
    20.0      | 22.0        | 30           | 100         | 0.5         || // calculated value
    24.0      | 22.0        | 15           | 50          | 0.3         || // calculated value
}
```

### Pattern: Test Boundary Conditions
```groovy
def "calculateVentOpenPercentage clamps to valid range"() {
    expect:
    def result = app.calculateVentOpenPercentage(
        'Test Room', startTemp, setpoint, hvacMode, maxRate, longestTime
    )
    result >= 0.0
    result <= 100.0

    where:
    startTemp | setpoint | hvacMode  | maxRate | longestTime
    20.0      | 24.0     | 'heating' | 0.001   | 60     // extreme low rate
    24.0      | 20.0     | 'cooling' | 10.0    | 1      // extreme high rate
    22.0      | 22.0     | 'heating' | 0.5     | 30     // at setpoint
}
```

## Safe Wrappers Pattern

The Flair app uses `safeSendEvent` to wrap `sendEvent` for test safety:

```groovy
// In app source
def safeSendEvent(device, Map eventData) {
    try {
        device.sendEvent(eventData)
    } catch (Exception e) {
        // graceful degradation in test environment
    }
}
```

This pattern should be applied to any platform method that may not exist in test:
- `safeSendEvent` for device events
- `safeGetChildDevices` for child device access
- `safeUnschedule` for schedule cleanup

## Settings Reader Helpers

The Flair app has helper methods for safe settings access:

```groovy
// readOptionalSettingValue(key, defaultValue) -- multi-fallback settings reader
// readOptionalBooleanSetting(key, defaultValue) -- boolean parser
// readOptionalIntegerSetting(key, defaultValue) -- integer parser
```

These handle the complexity of reading from `settings` (hub), `__settingsFallback` (test), and `atomicState` (mirrored settings).

## Unit Testing Checklist

1. Can the method be tested as a pure function? If yes, test directly
2. Does the method access `settings`? Set up `__settingsFallback`
3. Does the method access `atomicState`? Set up `__atomicStateFallback`
4. Does the method call platform APIs? Override via metaClass
5. Does the method use child devices? Mock device objects
6. Does the method make HTTP calls? Mock response objects
7. Does the method send events? Capture with safeSendEvent override
8. Does the method use scheduling? Override schedule methods as no-ops
9. Test null inputs, empty collections, and boundary values
10. Verify state mutations in `__atomicStateFallback` after method execution
