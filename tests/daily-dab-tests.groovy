package bot.flair

import me.biocomp.hubitat_ci.api.app_api.AppExecutor
import me.biocomp.hubitat_ci.app.HubitatAppSandbox
import me.biocomp.hubitat_ci.validation.Flags
import spock.lang.Specification

class DailyDabTests extends Specification {

  private static final File APP_FILE = new File('src/hubitat-flair-vents-app.groovy')
  private static final List VALIDATION_FLAGS = [
    Flags.DontValidateMetadata,
    Flags.DontValidatePreferences,
    Flags.DontValidateDefinition,
    Flags.DontRestrictGroovy,
    Flags.DontRequireParseMethodInDevice
  ]

  def "aggregateDailyDabStats calculates average per room and mode"() {
    setup:
    AppExecutor executorApi = Mock {
      _ * getState() >> [:]
    }
    def sandbox = new HubitatAppSandbox(APP_FILE)
    def script = sandbox.run('api': executorApi, 'validationFlags': VALIDATION_FLAGS)
    script.location = [timeZone: TimeZone.getTimeZone('UTC')]
    def yesterday = (new Date() - 1).format('yyyy-MM-dd', TimeZone.getTimeZone('UTC'))
    script.atomicState = [dabHistory: [room1: [cooling: [[date: yesterday, hour: 0, rate: 1.0], [date: yesterday, hour: 1, rate: 2.0], [date: '2020-01-01', hour: 0, rate: 5.0]]]]]
    script.settings = [dabHistoryRetentionDays: 10]

    when:
    script.aggregateDailyDabStats()

    then:
    script.atomicState.dabDailyStats.room1.cooling.find { it.date == yesterday }.avg == 1.5
  }

  def "initialize schedules daily aggregation when DAB enabled"() {
    setup:
    AppExecutor executorApi = Mock {
      _ * getState() >> [:]
    }
    def sandbox = new HubitatAppSandbox(APP_FILE)
    def script = sandbox.run('api': executorApi, 'validationFlags': VALIDATION_FLAGS)
    script.settings = [dabEnabled: true]
    boolean runEvery1DayCalled = false
    String scheduledMethod
    script.metaClass.runEvery1Day = { String method ->
      runEvery1DayCalled = true
      scheduledMethod = method
    }
    script.metaClass.unsubscribe = { -> }
    script.metaClass.initializeInstanceCaches = { -> }
    script.metaClass.cleanupExistingDecimalPrecision = { -> }
    script.metaClass.updateHvacStateFromDuctTemps = { -> }
    script.metaClass.runEvery1Minute = { String m -> }
    script.metaClass.runEvery5Minutes = { String m -> }
    script.metaClass.runEvery10Minutes = { String m -> }
    script.metaClass.runEvery1Hour = { m -> }
    script.metaClass.unschedule = { String m -> }
    script.metaClass.autoAuthenticate = { -> }

    when:
    script.initialize()

    then:
    runEvery1DayCalled
    scheduledMethod == 'aggregateDailyDabStats'
  }
}
