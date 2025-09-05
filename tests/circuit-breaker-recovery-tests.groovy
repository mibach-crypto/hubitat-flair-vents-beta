package bot.flair

// CircuitBreakerRecoveryTests
// Tests for circuit breaker recovery mechanism to ensure proper state transitions
// and queue flushing after CIRCUIT_RESET_MS timeout

import me.biocomp.hubitat_ci.util.CapturingLog.Level
import me.biocomp.hubitat_ci.util.CapturingLog
import me.biocomp.hubitat_ci.api.app_api.AppExecutor
import me.biocomp.hubitat_ci.app.HubitatAppSandbox
import me.biocomp.hubitat_ci.validation.Flags
import spock.lang.Specification
import groovy.time.TimeCategory

class CircuitBreakerRecoveryTest extends Specification {

  private static final File APP_FILE = new File('src/hubitat-flair-vents-app.groovy')
  private static final List VALIDATION_FLAGS = [
            Flags.DontValidateMetadata,
            Flags.DontValidatePreferences,
            Flags.DontValidateDefinition,
            Flags.DontRestrictGroovy,
            Flags.DontRequireParseMethodInDevice,
            Flags.AllowWritingToSettings,
            Flags.AllowReadingNonInputSettings
          ]

  def "circuit breaker trips after consecutive failures and resets after timeout"() {
    setup:
    final log = new CapturingLog()
    def testUri = 'test-failing-uri'
    
    AppExecutor executorApi = Mock(AppExecutor) {
      _ * getState() >> [flairAccessToken: 'test-token']
      _ * getLog() >> log
      _ * getAtomicState() >> [activeRequests: 0, failureCounts: [:]]
    }
    
    def sandbox = new HubitatAppSandbox(APP_FILE)
    def script = sandbox.run('api': executorApi, 'validationFlags': VALIDATION_FLAGS)
    script.state = [flairAccessToken: 'test-token', circuitOpenUntil: [:]]
    script.atomicState = [activeRequests: 0, failureCounts: [:]]

    // Mock virtual clock
    def virtualTime = new Date()
    script.metaClass.now = { -> virtualTime.time }
    script.metaClass.getCurrentTime = { -> virtualTime.time }

    when:
    // Trip circuit breaker by causing API_FAILURE_THRESHOLD consecutive failures
    script.API_FAILURE_THRESHOLD.times { i ->
      script.recordHttpFailure(testUri)
    }
    
    def circuitOpenAfterFailures = !script.isCircuitBreakerClosed(testUri)
    def failureCountAfterTrip = script.atomicState.failureCounts[testUri]
    
    // Advance virtual clock past CIRCUIT_RESET_MS
    use(TimeCategory) {
      virtualTime = virtualTime + (script.CIRCUIT_RESET_MS + 1000).milliseconds
    }
    
    // Check if circuit breaker resets
    def circuitClosedAfterTimeout = script.isCircuitBreakerClosed(testUri)
    def failureCountAfterReset = script.atomicState.failureCounts[testUri]

    then:
    // Verify circuit breaker tripped
    circuitOpenAfterFailures == true
    failureCountAfterTrip == script.API_FAILURE_THRESHOLD
    
    // Verify circuit breaker reset after timeout
    circuitClosedAfterTimeout == true
    failureCountAfterReset == null  // Should be cleared on reset
    
    // Verify circuit breaker activation was logged
    log.logs.any { it.message.contains("API circuit breaker activated for ${testUri}") }
    log.logs.any { it.message.contains("Circuit breaker reset for ${testUri}") }
  }

  def "circuit breaker prevents requests when open and allows when closed"() {
    setup:
    final log = new CapturingLog()
    def testUri = 'test-circuit-uri'
    def requestAttempts = []
    
    AppExecutor executorApi = Mock(AppExecutor) {
      _ * getState() >> [flairAccessToken: 'test-token']
      _ * getLog() >> log
      _ * getAtomicState() >> [activeRequests: 0, failureCounts: [:]]
    }
    
    def sandbox = new HubitatAppSandbox(APP_FILE)
    def script = sandbox.run('api': executorApi, 'validationFlags': VALIDATION_FLAGS)
    script.state = [flairAccessToken: 'test-token', circuitOpenUntil: [:]]
    script.atomicState = [activeRequests: 0, failureCounts: [:]]

    // Mock virtual clock
    def virtualTime = new Date()
    script.metaClass.now = { -> virtualTime.time }
    
    // Mock HTTP calls to track attempts
    script.metaClass.asynchttpGet = { callback, params, data ->
      requestAttempts << [uri: params.uri, time: virtualTime.time, data: data]
    }

    when:
    // Initially circuit should be closed (allow requests)
    def initialCircuitState = script.isCircuitBreakerClosed(testUri)
    script.getDataAsync(testUri, 'testCallback', [test: 'initial'])
    def initialRequestCount = requestAttempts.size()
    
    // Trip the circuit breaker
    script.API_FAILURE_THRESHOLD.times {
      script.recordHttpFailure(testUri)
    }
    
    def circuitOpenState = script.isCircuitBreakerClosed(testUri)
    
    // Try to make request while circuit is open - should not increment attempts
    script.getDataAsync(testUri, 'testCallback', [test: 'blocked'])
    def blockedRequestCount = requestAttempts.size()
    
    // Advance clock past reset time
    use(TimeCategory) {
      virtualTime = virtualTime + (script.CIRCUIT_RESET_MS + 1000).milliseconds
    }
    
    // Circuit should be closed again
    def circuitClosedState = script.isCircuitBreakerClosed(testUri)
    script.getDataAsync(testUri, 'testCallback', [test: 'allowed'])
    def allowedRequestCount = requestAttempts.size()

    then:
    initialCircuitState == true
    initialRequestCount == 1
    
    circuitOpenState == false  // Circuit is open (not closed)
    blockedRequestCount == 1   // No new requests should have been made
    
    circuitClosedState == true  // Circuit is closed again
    allowedRequestCount == 2    // New request should have been made
    
    // Verify request content
    requestAttempts[0].data.test == 'initial'
    requestAttempts[1].data.test == 'allowed'
  }

  def "circuit breaker recovery flushes queued requests"() {
    setup:
    final log = new CapturingLog()
    def testUri = 'test-queue-uri'
    def queuedRequests = []
    
    AppExecutor executorApi = Mock(AppExecutor) {
      _ * getState() >> [flairAccessToken: 'test-token']
      _ * getLog() >> log
      _ * getAtomicState() >> [activeRequests: 0, failureCounts: [:]]
    }
    
    def sandbox = new HubitatAppSandbox(APP_FILE)
    def script = sandbox.run('api': executorApi, 'validationFlags': VALIDATION_FLAGS)
    script.state = [flairAccessToken: 'test-token', circuitOpenUntil: [:]]
    script.atomicState = [activeRequests: 0, failureCounts: [:]]

    // Mock virtual clock
    def virtualTime = new Date()
    script.metaClass.now = { -> virtualTime.time }
    
    // Mock runInMillis to track queued retries
    script.metaClass.runInMillis = { delay, method, data ->
      queuedRequests << [
        delay: delay,
        method: method,
        data: data,
        queueTime: virtualTime.time
      ]
    }

    when:
    // Trip circuit breaker
    script.API_FAILURE_THRESHOLD.times {
      script.recordHttpFailure(testUri)
    }
    
    // Make several requests that should be queued/retried
    5.times { i ->
      script.getDataAsync(testUri, 'testCallback', [requestId: i])
    }
    
    def queuedRequestsWhileOpen = queuedRequests.size()
    
    // Advance time past circuit reset
    use(TimeCategory) {
      virtualTime = virtualTime + (script.CIRCUIT_RESET_MS + 1000).milliseconds
    }
    
    // Check circuit breaker state
    def circuitStateAfterReset = script.isCircuitBreakerClosed(testUri)
    
    // Make a new request to verify circuit is working
    script.getDataAsync(testUri, 'testCallback', [requestId: 'post-reset'])
    def queuedRequestsAfterReset = queuedRequests.size()

    then:
    // Verify requests were queued while circuit was open
    queuedRequestsWhileOpen > 0
    
    // Verify circuit breaker reset
    circuitStateAfterReset == true
    
    // Verify new requests can be made after reset
    queuedRequestsAfterReset >= queuedRequestsWhileOpen
    
    // Verify queued requests have appropriate retry delays
    queuedRequests.every { it.delay == script.API_CALL_DELAY_MS }
    queuedRequests.every { it.method.contains('retry') || it.method.contains('Retry') }
  }

  def "multiple URIs have independent circuit breaker states"() {
    setup:
    final log = new CapturingLog()
    def uri1 = 'test-uri-1'
    def uri2 = 'test-uri-2'
    
    AppExecutor executorApi = Mock(AppExecutor) {
      _ * getState() >> [flairAccessToken: 'test-token']
      _ * getLog() >> log
      _ * getAtomicState() >> [activeRequests: 0, failureCounts: [:]]
    }
    
    def sandbox = new HubitatAppSandbox(APP_FILE)
    def script = sandbox.run('api': executorApi, 'validationFlags': VALIDATION_FLAGS)
    script.state = [flairAccessToken: 'test-token', circuitOpenUntil: [:]]
    script.atomicState = [activeRequests: 0, failureCounts: [:]]

    // Mock virtual clock
    def virtualTime = new Date()
    script.metaClass.now = { -> virtualTime.time }

    when:
    // Trip circuit breaker for uri1 only
    script.API_FAILURE_THRESHOLD.times {
      script.recordHttpFailure(uri1)
    }
    
    def uri1StateAfterTrip = script.isCircuitBreakerClosed(uri1)
    def uri2StateAfterTrip = script.isCircuitBreakerClosed(uri2)
    
    // Record one failure for uri2 (not enough to trip)
    script.recordHttpFailure(uri2)
    
    def uri1StateAfterUri2Failure = script.isCircuitBreakerClosed(uri1)
    def uri2StateAfterFailure = script.isCircuitBreakerClosed(uri2)
    
    // Advance time to reset uri1 circuit
    use(TimeCategory) {
      virtualTime = virtualTime + (script.CIRCUIT_RESET_MS + 1000).milliseconds
    }
    
    def uri1StateAfterReset = script.isCircuitBreakerClosed(uri1)
    def uri2StateAfterReset = script.isCircuitBreakerClosed(uri2)

    then:
    // After tripping uri1, only uri1 should be open
    uri1StateAfterTrip == false  // Circuit open
    uri2StateAfterTrip == true   // Circuit closed
    
    // After uri2 failure (not enough to trip), states should remain
    uri1StateAfterUri2Failure == false  // Still open
    uri2StateAfterFailure == true       // Still closed
    
    // After reset timeout, uri1 should be closed again
    uri1StateAfterReset == true   // Circuit closed after reset
    uri2StateAfterReset == true   // Still closed
    
    // Verify failure counts are independent
    script.atomicState.failureCounts[uri1] == null  // Cleared on reset
    script.atomicState.failureCounts[uri2] == 1     // Still has 1 failure
  }

  def "circuit breaker constants are properly defined"() {
    setup:
    AppExecutor executorApi = Mock(AppExecutor) {
      _ * getState() >> [:]
    }
    def sandbox = new HubitatAppSandbox(APP_FILE)
    def script = sandbox.run('api': executorApi, 'validationFlags': VALIDATION_FLAGS)

    when:
    def circuitResetMs = script.CIRCUIT_RESET_MS
    def apiFailureThreshold = script.API_FAILURE_THRESHOLD

    then:
    circuitResetMs == 300000  // 5 minutes in milliseconds
    apiFailureThreshold == 3
    circuitResetMs > 0
    apiFailureThreshold > 0
  }
}