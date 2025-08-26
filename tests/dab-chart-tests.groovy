package bot.flair

import me.biocomp.hubitat_ci.util.CapturingLog
import me.biocomp.hubitat_ci.api.app_api.AppExecutor
import me.biocomp.hubitat_ci.app.HubitatAppSandbox
import me.biocomp.hubitat_ci.validation.Flags
import spock.lang.Specification
import groovy.json.JsonSlurper
import java.net.URLDecoder

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
      getId: { 'device-1' },
      getLabel: { 'Room1' },
      currentValue: { String attr ->
        if (attr == 'room-name') return 'Room1'
        if (attr == 'room-id') return 'room-1'
        return null
      }
    )
    script.metaClass.getChildDevices = { -> [vent] }
    script.metaClass.getThermostat1Mode = { -> 'cooling' }
    script.appendHourlyRate('room-1', 'cooling', 0, 1.0)

    when:
    def html = script.buildDabChart()

    then:
    html.contains('quickchart.io')

    and: "dataset uses room-id for hourly rates"
    def encoded = html.split('chart\?c=')[1].split("'")[0]
    def config = new JsonSlurper().parseText(URLDecoder.decode(encoded, 'UTF-8'))
    config.data.datasets[0].data[0] == 1.0d
  }

  def "chart falls back to last recorded mode when thermostat mode missing"() {
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
      getId: { 'device-1' },
      getLabel: { 'Room1' },
      currentValue: { String attr ->
        if (attr == 'room-name') return 'Room1'
        if (attr == 'room-id') return 'room-1'
        return null
      }
    )
    script.metaClass.getChildDevices = { -> [vent] }
    script.metaClass.getThermostat1Mode = { -> null }
    script.appendHourlyRate('room-1', 'heating', 0, 2.0)

    when:
    def html = script.buildDabChart()

    then:
    def encoded = html.split('chart\?c=')[1].split("'")[0]
    def config = new JsonSlurper().parseText(URLDecoder.decode(encoded, 'UTF-8'))
    config.data.datasets[0].data[0] == 2.0d
  }

  def "chart merges heating and cooling data when both selected"() {
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
      getId: { 'device-1' },
      getLabel: { 'Room1' },
      currentValue: { String attr ->
        if (attr == 'room-name') return 'Room1'
        if (attr == 'room-id') return 'room-1'
        return null
      }
    )
    script.metaClass.getChildDevices = { -> [vent] }
    script.metaClass.getThermostat1Mode = { -> null }
    script.metaClass.getSettings = { [chartHvacMode: 'both'] }
    script.appendHourlyRate('room-1', 'cooling', 0, 1.0)
    script.appendHourlyRate('room-1', 'heating', 0, 3.0)

    when:
    def html = script.buildDabChart()

    then:
    def encoded = html.split('chart\?c=')[1].split("'")[0]
    def config = new JsonSlurper().parseText(URLDecoder.decode(encoded, 'UTF-8'))
    config.data.datasets[0].data[0] == 2.0d
  }

  def "numeric room ids are handled correctly"() {
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
      getId: { 'device-1' },
      getLabel: { 'Room1' },
      currentValue: { String attr ->
        if (attr == 'room-name') return 'Room1'
        if (attr == 'room-id') return '123'
        return null
      }
    )
    script.metaClass.getChildDevices = { -> [vent] }
    script.metaClass.getThermostat1Mode = { -> 'cooling' }
    script.appendHourlyRate(123, 'cooling', 0, 4.0)

    when:
    def html = script.buildDabChart()

    then:
    def encoded = html.split('chart\?c=')[1].split("'")[0]
    def config = new JsonSlurper().parseText(URLDecoder.decode(encoded, 'UTF-8'))
    config.data.datasets[0].data[0] == 4.0d
  }
}
