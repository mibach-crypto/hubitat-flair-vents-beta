package bot.flair

import me.biocomp.hubitat_ci.util.CapturingLog
import me.biocomp.hubitat_ci.util.CapturingLog.Level
import me.biocomp.hubitat_ci.api.app_api.AppExecutor
import me.biocomp.hubitat_ci.app.HubitatAppSandbox
import me.biocomp.hubitat_ci.validation.Flags
import spock.lang.Specification

class DabHistoryIntegrityTests extends Specification {

  private static final File APP_FILE = new File('src/hubitat-flair-vents-app.groovy')
  private static final List VALIDATION_FLAGS = [
    Flags.DontValidateMetadata,
    Flags.DontValidatePreferences,
    Flags.DontValidateDefinition,
    Flags.DontRestrictGroovy,
    Flags.DontRequireParseMethodInDevice
  ]

  def "integrity check logs missing hours"() {
    setup:
      final log = new CapturingLog()
      AppExecutor executorApi = Mock {
        _ * getState() >> [:]
        _ * getLog() >> log
      }
      def sandbox = new HubitatAppSandbox(APP_FILE)
      def script = sandbox.run('api': executorApi,
                               'validationFlags': VALIDATION_FLAGS,
                               'userSettingValues': [debugLevel:1])
      script.atomicState.dabHistory = ['2024-01-01':[0:[rate:1], 2:[rate:2]]]

    when:
      script.checkDabHistoryIntegrity()

    then:
      script.atomicState.dabActivityLog[0].contains('missing hour 1')
      log.records.any { it[0] == Level.warn && it[1].contains('missing hour 1') }
  }
}
