package bot.flair

import me.biocomp.hubitat_ci.util.CapturingLog
import me.biocomp.hubitat_ci.api.app_api.AppExecutor
import me.biocomp.hubitat_ci.app.HubitatAppSandbox
import me.biocomp.hubitat_ci.validation.Flags
import spock.lang.Specification

class EwmaOutlierTests extends Specification {

  private static final File APP_FILE = new File('src/hubitat-flair-vents-app.groovy')
  private static final List VALIDATION_FLAGS = [
    Flags.DontValidateMetadata,
    Flags.DontValidatePreferences,
    Flags.DontValidateDefinition,
    Flags.DontRestrictGroovy,
    Flags.DontRequireParseMethodInDevice
  ]

  def "EWMA prefers smoothed value when enabled"() {
    setup:
    final log = new CapturingLog()
    AppExecutor executorApi = Mock {
      _ * getState() >> [:]
      _ * getAtomicState() >> [:]
      _ * getLog() >> log
    }
    def sandbox = new HubitatAppSandbox(APP_FILE)
    def script = sandbox.run('api': executorApi, 'validationFlags': VALIDATION_FLAGS)
    script.settings = [enableEwma: true, ewmaHalfLifeDays: 3, dabHistoryRetentionDays: 10]

    when:
    // Seed history with stable value 1.0 for hour 10
    (1..5).each { script.appendHourlyRate('room-1', 'cooling', 10, 1.0G); script.appendDabHistory('room-1', 'cooling', 10, 1.0G) }
    // Update EWMA with a stronger new value 2.0
    def smoothed = script.updateEwmaRate('room-1', 'cooling', 10, 2.0G)

    then:
    smoothed > 1.0G && smoothed < 2.0G
    script.getAverageHourlyRate('room-1', 'cooling', 10) == smoothed
  }

  def "MAD outlier detection clips extreme spike"() {
    setup:
    final log = new CapturingLog()
    AppExecutor executorApi = Mock {
      _ * getState() >> [:]
      _ * getAtomicState() >> [:]
      _ * getLog() >> log
    }
    def sandbox = new HubitatAppSandbox(APP_FILE)
    def script = sandbox.run('api': executorApi, 'validationFlags': VALIDATION_FLAGS)
    script.settings = [enableOutlierRejection: true, outlierThresholdMad: 3, outlierMode: 'clip', dabHistoryRetentionDays: 10]

    when:
    (1..7).each { script.appendHourlyRate('room-9', 'heating', 22, 1.0G); script.appendDabHistory('room-9', 'heating', 22, 1.0G) }
    def decision = script.assessOutlierForHourly('room-9', 'heating', 22, 10.0G)

    then:
    decision.action in ['clip','reject']
  }
}

