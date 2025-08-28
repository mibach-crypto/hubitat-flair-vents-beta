package bot.flair

import me.biocomp.hubitat_ci.util.CapturingLog
import me.biocomp.hubitat_ci.api.app_api.AppExecutor
import me.biocomp.hubitat_ci.app.HubitatAppSandbox
import me.biocomp.hubitat_ci.validation.Flags
import spock.lang.Specification

class DabHistoryArchiveTests extends Specification {

  private static final File APP_FILE = new File('src/hubitat-flair-vents-app.groovy')
  private static final List VALIDATION_FLAGS = [
    Flags.DontValidateMetadata,
    Flags.DontValidatePreferences,
    Flags.DontValidateDefinition,
    Flags.DontRestrictGroovy,
    Flags.DontRequireParseMethodInDevice
  ]

  def setup() {
    new File('data').deleteDir()
  }

  def "activity logs are archived and retrievable"() {
    setup:
    final log = new CapturingLog()
    AppExecutor executorApi = Mock {
      _ * getState() >> [:]
      _ * getAtomicState() >> [:]
      _ * getLog() >> log
    }
    def sandbox = new HubitatAppSandbox(APP_FILE)
    def script = sandbox.run('api': executorApi, 'validationFlags': VALIDATION_FLAGS)
    // CI-safe: inject location via getter rather than assigning
    script.metaClass.getLocation = { -> [timeZone: TimeZone.getTimeZone('UTC')] }
    // CI-safe: provide atomicState map via getter
    def ast = [:]
    script.metaClass.getAtomicState = { -> ast }

    when:
    script.appendDabActivityLog('testing archive')

    then:
    def history = script.readDabHistoryArchive()
    history.any { it.type == 'activity' && it.message == 'testing archive' }
  }
}
