package bot.flair

import me.biocomp.hubitat_ci.util.CapturingLog
import me.biocomp.hubitat_ci.api.app_api.AppExecutor
import me.biocomp.hubitat_ci.app.HubitatAppSandbox
import me.biocomp.hubitat_ci.validation.Flags
import spock.lang.Specification

class DabChartTests extends Specification {

  private static final File APP_FILE = new File('src/hubitat-flair-vents-app.groovy')
  private static final List VALIDATION_FLAGS = [
    Flags.DontValidateMetadata,
    Flags.DontValidatePreferences,
    Flags.DontValidateDefinition,
    Flags.DontRestrictGroovy,
    Flags.DontRequireParseMethodInDevice
  ]

  def "chart generation produces quickchart link"() {
    setup:
    final log = new CapturingLog()
    AppExecutor executorApi = Mock {
      _ * getState() >> [:]
      _ * getLog() >> log
    }
    def sandbox = new HubitatAppSandbox(APP_FILE)
    def script = sandbox.run('api': executorApi, 'validationFlags': VALIDATION_FLAGS)

    def vent = new Expando(
      hasAttribute: { String attr -> attr == 'percent-open' },
      getId: { '1' },
      getLabel: { 'Room1' },
      currentValue: { String attr -> attr == 'room-name' ? 'Room1' : null }
    )
    script.metaClass.getChildDevices = { -> [vent] }
    script.metaClass.getThermostat1Mode = { -> 'cooling' }
    script.appendHourlyRate('1', 'cooling', 0, 1.0)

    when:
    def html = script.buildDabChart()

    then:
    html.contains('quickchart.io')
  }
}
