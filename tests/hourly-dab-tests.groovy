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

    when: 'appending a new rate'
    script.appendHourlyRate('room1', 'cooling', 0, 2.0)

    then: 'old entry is purged and average uses remaining values'
    script.atomicState.hourlyRates.room1.cooling.size() == 1
    script.getAverageHourlyRate('room1', 'cooling', 0) == 2.0
  }
}
