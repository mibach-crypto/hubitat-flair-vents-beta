# Async Request Interlocking and Circuit Breaker Recovery Implementation

## Overview
This implementation provides comprehensive async HTTP request interlocking and circuit breaker recovery functionality for the Hubitat Flair Vents integration app.

## Key Features

### 1. Request Interlocking
- **MAX_CONCURRENT_REQUESTS**: Limited to 8 concurrent requests
- **Active Request Tracking**: Atomic counter that never goes negative
- **Stuck Counter Detection**: Automatic reset when counter exceeds limit
- **Thread-Safe Operations**: Proper increment/decrement handling

### 2. Circuit Breaker Recovery
- **CIRCUIT_RESET_MS**: 5-minute timeout (300,000ms)
- **API_FAILURE_THRESHOLD**: 3 consecutive failures trigger circuit open
- **Per-URI Circuit States**: Independent circuit breakers for different endpoints
- **Automatic Recovery**: Circuit resets after timeout period

### 3. Virtual Clock Support
- Tests use `groovy.time.TimeCategory` for deterministic timing
- No real delays in test execution
- Predictable advancement of time for circuit breaker recovery testing

## Implementation Details

### Constants Added
```groovy
@Field static final Integer CIRCUIT_RESET_MS = 5 * 60 * 1000  // 5 minutes
```

### New Methods
```groovy
// Check if circuit breaker is closed (allows requests)
def isCircuitBreakerClosed(String uri = null)

// Record HTTP failure and potentially trip circuit breaker
private recordHttpFailure(String uri)
```

### Enhanced Methods
- `canMakeRequest()`: Now includes stuck counter detection
- `getDataAsync()`: Circuit breaker state checking before requests
- `asyncHttpGetWrapper()`: Automatic request counter management and failure recording

## Test Specifications

### AsyncRequestInterlockSpec
Located in: `src/test/groovy/app/concurrency/AsyncRequestInterlockSpec.groovy`

**Tests:**
1. **Concurrent Request Limits**: 20 simultaneous `getDataAsync` calls
   - Verifies `activeRequests` never exceeds `MAX_CONCURRENT_REQUESTS`
   - Ensures counter never goes negative
   - Uses virtual clock for deterministic completion

2. **Counter Management**: Increment/decrement behavior
   - Tests normal increment/decrement cycles
   - Verifies negative value prevention (clamping to 0)
   - Confirms warning logs for unexpected states

3. **Virtual Clock Timing**: Request completion simulation
   - Advances virtual time using `TimeCategory`
   - Tracks request completion timing
   - Validates counter consistency throughout lifecycle

4. **Stuck Counter Detection**: Automatic reset functionality
   - Simulates stuck counter scenarios
   - Verifies automatic reset when over limit
   - Confirms critical logging for stuck states

### CircuitBreakerRecoverySpec
Located in: `src/test/groovy/app/concurrency/CircuitBreakerRecoverySpec.groovy`

**Tests:**
1. **Circuit Breaker Trip/Reset**: Basic state transitions
   - Trips breaker with `API_FAILURE_THRESHOLD` failures
   - Advances virtual clock past `CIRCUIT_RESET_MS`
   - Verifies state transitions and failure count clearing

2. **Request Blocking**: Circuit open/closed behavior
   - Blocks requests when circuit is open
   - Allows requests when circuit is closed
   - Maintains request attempt tracking

3. **Queue Flushing**: Retry mechanism during circuit open state
   - Queues requests when circuit is open
   - Tracks retry scheduling with proper delays
   - Verifies queue processing after circuit reset

4. **Independent Circuit States**: Per-URI circuit management
   - Tests multiple URIs with independent states
   - Verifies isolated failure counting
   - Confirms independent reset timing

5. **Constants Validation**: Configuration verification
   - Validates `CIRCUIT_RESET_MS = 300000` (5 minutes)
   - Confirms `API_FAILURE_THRESHOLD = 3`

## Test Files Created

### In Standard Location (src/test/groovy/app/concurrency/)
- `AsyncRequestInterlockSpec.groovy`
- `CircuitBreakerRecoverySpec.groovy`

### In Project Pattern (tests/)
- `async-request-interlock-tests.groovy`
- `circuit-breaker-recovery-tests.groovy`

## Key Assertions

### Request Interlocking
- `activeRequests` never exceeds `MAX_CONCURRENT_REQUESTS`
- `activeRequests` never becomes negative
- Stuck counter detection and automatic reset
- Proper logging for critical states

### Circuit Breaker Recovery  
- Circuit trips after 3 consecutive failures
- Circuit resets after 5-minute timeout
- Independent per-URI circuit states
- Proper queuing and retry scheduling
- Failure count clearing on reset

## Dependencies
- Spock Framework for BDD-style testing
- Groovy TimeCategory for virtual clock
- HubitatAppSandbox for app testing environment
- Java concurrent utilities for threading tests

## Deterministic Testing
All tests are designed to be deterministic:
- No real time delays (virtual clock only)
- Controlled request completion simulation
- Predictable failure injection
- Consistent state verification

## Usage Notes
- Tests validate core concurrency safety
- Virtual clock ensures fast, reliable test execution
- Comprehensive logging captures all state transitions
- Both positive and negative test scenarios included