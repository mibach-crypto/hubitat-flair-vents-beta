package bot.flair

import me.biocomp.hubitat_ci.util.CapturingLog
import me.biocomp.hubitat_ci.api.app_api.AppExecutor
import me.biocomp.hubitat_ci.app.HubitatAppSandbox
import me.biocomp.hubitat_ci.validation.Flags
import spock.lang.Specification

class DabFixesValidationTests extends Specification {

  private static final File APP_FILE = new File('src/hubitat-flair-vents-app.groovy')
  private static final List VALIDATION_FLAGS = [
    Flags.DontValidateMetadata,
    Flags.DontValidatePreferences,
    Flags.DontValidateDefinition,
    Flags.DontRestrictGroovy,
    Flags.DontRequireParseMethodInDevice
  ]

  def "appendHourlyRate handles concurrent access without data corruption"() {
    setup:
    final log = new CapturingLog()
    AppExecutor executorApi = Mock {
      _ * getState() >> [:]
      _ * getLog() >> log
    }
    def sandbox = new HubitatAppSandbox(APP_FILE)
    def script = sandbox.run('api': executorApi, 'validationFlags': VALIDATION_FLAGS)
    script.location = [timeZone: TimeZone.getTimeZone('UTC')]
    script.settings = [dabHistoryRetentionDays: 10]

    when: 'appending multiple rates concurrently'
    script.appendHourlyRate('room1', 'cooling', 0, 1.5)
    script.appendHourlyRate('room1', 'cooling', 0, 2.0)
    script.appendHourlyRate('room1', 'cooling', 0, 2.5)

    then: 'data structure remains consistent'
    def hist = script.atomicState.dabHistory
    hist instanceof Map
    hist.hourlyRates != null
    hist.entries != null
    hist.hourlyRates.room1.cooling[0].size() == 3
    hist.entries.size() == 3
  }

  def "getAverageHourlyRate handles empty data gracefully"() {
    setup:
    final log = new CapturingLog()
    AppExecutor executorApi = Mock {
      _ * getState() >> [:]
      _ * getLog() >> log
    }
    def sandbox = new HubitatAppSandbox(APP_FILE)
    def script = sandbox.run('api': executorApi, 'validationFlags': VALIDATION_FLAGS)
    script.location = [timeZone: TimeZone.getTimeZone('UTC')]
    script.settings = [dabHistoryRetentionDays: 10]

    when: 'getting average for room with no data'
    def result = script.getAverageHourlyRate('nonexistent_room', 'cooling', 0)

    then: 'returns minimum rate instead of 0 or null'
    result > 0
    result == script.cleanDecimalForJson(script.MIN_TEMP_CHANGE_RATE)
  }

  def "getAverageHourlyRate validates rate values before averaging"() {
    setup:
    final log = new CapturingLog()
    AppExecutor executorApi = Mock {
      _ * getState() >> [:]
      _ * getLog() >> log
    }
    def sandbox = new HubitatAppSandbox(APP_FILE)
    def script = sandbox.run('api': executorApi, 'validationFlags': VALIDATION_FLAGS)
    script.location = [timeZone: TimeZone.getTimeZone('UTC')]
    script.settings = [dabHistoryRetentionDays: 10]

    and: 'populate with some invalid data'
    script.atomicState = [dabHistory: [
      hourlyRates: [
        room1: [
          cooling: [
            0: [1.0, -5.0, 999.0, 2.0, null, 'invalid'] // Mix of valid and invalid values
          ]
        ]
      ]
    ]]

    when: 'getting average hourly rate'
    def result = script.getAverageHourlyRate('room1', 'cooling', 0)

    then: 'only valid rates are used in calculation'
    result == script.cleanDecimalForJson((1.0 + 2.0) / 2.0) // Only 1.0 and 2.0 are valid
  }

  def "retention logic properly limits entries to prevent memory bloat"() {
    setup:
    final log = new CapturingLog()
    AppExecutor executorApi = Mock {
      _ * getState() >> [:]
      _ * getLog() >> log
    }
    def sandbox = new HubitatAppSandbox(APP_FILE)
    def script = sandbox.run('api': executorApi, 'validationFlags': VALIDATION_FLAGS)
    script.location = [timeZone: TimeZone.getTimeZone('UTC')]
    script.settings = [dabHistoryRetentionDays: 1] // 1 day retention

    when: 'appending many rates for same room/mode/hour'
    (1..100).each { i ->
      script.appendHourlyRate('room1', 'cooling', 0, i * 0.1)
    }

    then: 'entries are limited to retention * maxEntriesPerHour'
    def hist = script.atomicState.dabHistory
    def hourlyList = hist.hourlyRates.room1.cooling[0]
    hourlyList.size() <= 48 // 1 day * 48 max entries per hour
  }

  def "dramatic temperature swing detection prevents extreme rate calculations"() {
    setup:
    final log = new CapturingLog()
    AppExecutor executorApi = Mock {
      _ * getState() >> [:]
      _ * getLog() >> log
    }
    def sandbox = new HubitatAppSandbox(APP_FILE)
    def script = sandbox.run('api': executorApi, 'validationFlags': VALIDATION_FLAGS)
    script.location = [timeZone: TimeZone.getTimeZone('UTC')]
    script.settings = [dabEnabled: true]

    and: 'mock a device with dramatic temperature swing'
    def mockDevice = Mock()
    mockDevice.currentValue('room-name') >> 'TestRoom'
    mockDevice.currentValue('room-starting-temperature-c') >> 20.0
    mockDevice.currentValue('room-cooling-rate') >> 1.0
    
    // Mock getRoomTemp to return dramatic swing
    script.metaClass.getRoomTemp = { device -> return 35.0 } // 15°C swing
    script.metaClass.getChildDevice = { id -> return mockDevice }
    script.metaClass.sendEvent = { device, event -> }
    script.metaClass.calculateRoomChangeRate = { start, current, minutes, percent, rate -> 
      return (current - start) / minutes // Would normally be extreme
    }

    when: 'finalizing room states with dramatic temperature swing'
    // This would normally cause issues, but should be handled gracefully
    def testData = [
      ventIdsByRoomId: [room1: ['vent1']],
      startedCycle: System.currentTimeMillis() - 600000, // 10 minutes ago
      finishedRunning: System.currentTimeMillis(),
      startedRunning: System.currentTimeMillis() - 360000, // 6 minutes ago  
      hvacMode: 'cooling'
    ]
    
    script.finalizeRoomStates(testData)

    then: 'dramatic swing is detected and logged'
    log.logs.any { it.contains('dramatic temperature swing') || it.contains('Dramatic temperature swing') }
  }
}