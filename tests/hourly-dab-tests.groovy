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
    def oldTs = (new Date() - 11).getTime()
    script.atomicState = [dabHistoryStartTimestamp: oldTs, dabHistory: [[oldTs, 'room1', 'cooling', 0, 1.0]]]

    then:
    script.atomicState.hourlyRates.room1.cooling['0'].size() == 10
    script.getAverageHourlyRate('room1', 'cooling', 0) == 6.5
  }

    then: 'old entry is purged and average uses remaining values'
    script.atomicState.dabHistory.size() == 1
    script.getAverageHourlyRate('room1', 'cooling', 0) == 2.0
  }
}
