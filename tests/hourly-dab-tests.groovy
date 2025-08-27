package bot.flair

import me.biocomp.hubitat_ci.util.CapturingLog
import me.biocomp.hubitat_ci.api.app_api.AppExecutor
import me.biocomp.hubitat_ci.app.HubitatAppSandbox
import me.biocomp.hubitat_ci.validation.Flags
import spock.lang.Specification

class HourlyDabTests extends Specification {

  private static final File APP_FILE = new File('src/hubitat-flair-vents-app.groovy')
  private static final List VALIDATION_FLAGS = [
    Flags.DontValidateMetadata,
    Flags.DontValidatePreferences,
    Flags.DontValidateDefinition,
    Flags.DontRestrictGroovy,
    Flags.DontRequireParseMethodInDevice
  ]

  def "timestamped rate history purges entries older than retention"() {
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

    and: 'prepopulate history with an old entry'
    def oldDate = (new Date() - 11).format('yyyy-MM-dd', TimeZone.getTimeZone('UTC'))
    script.atomicState = [hourlyRates: [room1: [cooling: [[date: oldDate, hour: 0, rate: 1.0]]]]]

    then:
    script.atomicState.hourlyRates.room1.cooling['0'].size() == 10
    script.getAverageHourlyRate('room1', 'cooling', 0) == 6.5
  }

  def "hourly rates are retrieved when hour keys are strings"() {
    setup:
    final log = new CapturingLog()
    AppExecutor executorApi = Mock {
      _ * getState() >> [:]
      _ * getLog() >> log
    }
    def sandbox = new HubitatAppSandbox(APP_FILE)
    def script = sandbox.run('api': executorApi, 'validationFlags': VALIDATION_FLAGS)

    when:
    script.appendHourlyRate('room1', 'cooling', 0, 5.0)
    // Simulate Hubitat's JSON serialization which converts numeric keys to strings
    script.atomicState.hourlyRates = new groovy.json.JsonSlurper().parseText(
        groovy.json.JsonOutput.toJson(script.atomicState.hourlyRates))

    then:
    script.getAverageHourlyRate('room1', 'cooling', 0) == 5.0d
  }
}
