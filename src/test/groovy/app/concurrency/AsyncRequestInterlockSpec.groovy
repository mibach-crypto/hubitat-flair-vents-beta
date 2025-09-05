package app.concurrency

// AsyncRequestInterlockSpec
// Tests for async HTTP request interlocking to ensure activeRequests never exceeds MAX_CONCURRENT_REQUESTS
// and never goes negative during concurrent operations

import me.biocomp.hubitat_ci.util.CapturingLog.Level
import me.biocomp.hubitat_ci.util.CapturingLog
import me.biocomp.hubitat_ci.api.app_api.AppExecutor
import me.biocomp.hubitat_ci.app.HubitatAppSandbox
import me.biocomp.hubitat_ci.validation.Flags
import spock.lang.Specification
import groovy.time.TimeCategory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class AsyncRequestInterlockSpec extends Specification {

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

  def "concurrent getDataAsync calls respect MAX_CONCURRENT_REQUESTS limit"() {
    setup:
    final log = new CapturingLog()
    def activeRequestsHistory = new ConcurrentLinkedQueue<Integer>()
    def requestCompletions = new CountDownLatch(20)
    
    AppExecutor executorApi = Mock(AppExecutor) {
      _ * getState() >> [flairAccessToken: 'test-token']
      _ * getLog() >> log
      _ * getAtomicState() >> [activeRequests: 0, failureCounts: [:]]
    }
    
    def sandbox = new HubitatAppSandbox(APP_FILE)
    def script = sandbox.run('api': executorApi, 'validationFlags': VALIDATION_FLAGS)
    script.state = [flairAccessToken: 'test-token', circuitOpenUntil: [:]]
    script.atomicState = [activeRequests: 0, failureCounts: [:]]

    // Mock virtual clock and HTTP calls
    def virtualTime = new Date()
    script.metaClass.now = { -> virtualTime.time }
    
    def completedRequests = []
    script.metaClass.asynchttpGet = { callback, params, data ->
      // Record current active request count when request is made
      def currentActive = script.atomicState.activeRequests
      activeRequestsHistory.offer(currentActive)
      
      // Simulate async completion after virtual time advancement
      completedRequests << [callback: callback, params: params, data: data]
    }

    when:
    // Launch 20 simultaneous getDataAsync calls
    def executor = Executors.newFixedThreadPool(20)
    
    20.times { i ->
      executor.submit {
        try {
          script.getDataAsync("test-uri-${i}", 'testCallback', [requestId: i])
        } catch (Exception e) {
          log.debug("Request ${i} failed: ${e.message}")
        } finally {
          requestCompletions.countDown()
        }
      }
    }
    
    // Wait for all requests to be submitted
    requestCompletions.await(5, TimeUnit.SECONDS)
    executor.shutdown()
    
    then:
    // Verify activeRequests never exceeded MAX_CONCURRENT_REQUESTS
    def maxRecorded = activeRequestsHistory.max() ?: 0
    maxRecorded <= script.MAX_CONCURRENT_REQUESTS
    
    // Verify activeRequests is never negative
    def minRecorded = activeRequestsHistory.min() ?: 0
    minRecorded >= 0
    
    // Verify final active request count is correct
    script.atomicState.activeRequests <= script.MAX_CONCURRENT_REQUESTS
    script.atomicState.activeRequests >= 0
    
    // Log captured metrics for analysis
    log.info("Active requests history: ${activeRequestsHistory.toList()}")
    log.info("Final active requests: ${script.atomicState.activeRequests}")
    log.info("MAX_CONCURRENT_REQUESTS: ${script.MAX_CONCURRENT_REQUESTS}")
  }

  def "activeRequests counter handles decrements correctly and prevents negative values"() {
    setup:
    final log = new CapturingLog()
    AppExecutor executorApi = Mock(AppExecutor) {
      _ * getState() >> [:]
      _ * getLog() >> log
      _ * getAtomicState() >> [activeRequests: 0]
    }
    
    def sandbox = new HubitatAppSandbox(APP_FILE)
    def script = sandbox.run('api': executorApi, 'validationFlags': VALIDATION_FLAGS)
    script.atomicState = [activeRequests: 0]

    when:
    // Test normal increment/decrement cycle
    script.incrementActiveRequests()
    def afterIncrement = script.atomicState.activeRequests
    
    script.decrementActiveRequests()
    def afterDecrement = script.atomicState.activeRequests
    
    // Test multiple decrements beyond zero
    script.decrementActiveRequests()
    script.decrementActiveRequests()
    def afterExtraDecrements = script.atomicState.activeRequests

    then:
    afterIncrement == 1
    afterDecrement == 0
    afterExtraDecrements == 0  // Should be clamped to 0, not negative
    
    // Verify warning was logged about clamping
    log.logs.any { it.message.contains("Active request counter was already at") }
  }

  def "virtual clock advancement simulates request completion timing"() {
    setup:
    final log = new CapturingLog()
    def completionTimes = []
    
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
    
    // Mock HTTP calls to track timing
    def pendingRequests = []
    script.metaClass.asynchttpGet = { callback, params, data ->
      pendingRequests << [
        callback: callback, 
        params: params, 
        data: data, 
        startTime: virtualTime.time,
        completed: false
      ]
    }

    when:
    // Make 5 requests
    5.times { i ->
      script.getDataAsync("test-uri-${i}", 'testCallback', [requestId: i])
    }
    
    def initialActiveCount = script.atomicState.activeRequests
    
    // Advance virtual clock and simulate completions
    use(TimeCategory) {
      virtualTime = virtualTime + 2.seconds
      
      // Complete first 3 requests
      3.times { i ->
        if (i < pendingRequests.size() && !pendingRequests[i].completed) {
          script.asyncHttpGetWrapper(
            [hasError: { -> false }, getStatus: { -> 200 }, getJson: { -> [data: [id: "response-${i}"]] }],
            pendingRequests[i].data
          )
          pendingRequests[i].completed = true
          completionTimes << virtualTime.time
        }
      }
    }
    
    def midActiveCount = script.atomicState.activeRequests
    
    // Complete remaining requests
    use(TimeCategory) {
      virtualTime = virtualTime + 3.seconds
      
      (3..<pendingRequests.size()).each { i ->
        if (!pendingRequests[i].completed) {
          script.asyncHttpGetWrapper(
            [hasError: { -> false }, getStatus: { -> 200 }, getJson: { -> [data: [id: "response-${i}"]] }],
            pendingRequests[i].data
          )
          pendingRequests[i].completed = true
          completionTimes << virtualTime.time
        }
      }
    }
    
    def finalActiveCount = script.atomicState.activeRequests

    then:
    initialActiveCount == 5
    midActiveCount == 2  // After completing 3 requests
    finalActiveCount == 0  // After completing all requests
    
    // Verify all requests never caused negative counts
    initialActiveCount >= 0
    midActiveCount >= 0
    finalActiveCount >= 0
    
    // Verify timing progression
    completionTimes.size() == 5
    completionTimes.every { it >= 0 }
  }

  def "canMakeRequest handles stuck counter detection correctly"() {
    setup:
    final log = new CapturingLog()
    AppExecutor executorApi = Mock(AppExecutor) {
      _ * getState() >> [:]
      _ * getLog() >> log
      _ * getAtomicState() >> [activeRequests: 0]
    }
    
    def sandbox = new HubitatAppSandbox(APP_FILE)
    def script = sandbox.run('api': executorApi, 'validationFlags': VALIDATION_FLAGS)
    script.atomicState = [activeRequests: 0]

    when:
    // Simulate stuck counter by setting it above limit
    script.atomicState.activeRequests = script.MAX_CONCURRENT_REQUESTS + 2
    def beforeReset = script.atomicState.activeRequests
    
    // canMakeRequest should detect and reset stuck counter
    def canMakeAfterStuck = script.canMakeRequest()
    def afterReset = script.atomicState.activeRequests

    then:
    beforeReset > script.MAX_CONCURRENT_REQUESTS
    canMakeAfterStuck == true  // Should allow request after reset
    afterReset == 0  // Should be reset to 0
    
    // Verify critical log message was generated
    log.logs.any { it.message.contains("CRITICAL: Active request counter is stuck") }
    log.logs.any { it.message.contains("Reset active request counter to 0") }
  }
}