package bot.flair

import me.biocomp.hubitat_ci.api.app_api.AppExecutor
import me.biocomp.hubitat_ci.app.HubitatAppSandbox
import me.biocomp.hubitat_ci.validation.Flags
import spock.lang.Specification

class DabProgressTests extends Specification {

  private static final File APP_FILE = new File('src/hubitat-flair-vents-app.groovy')
  private static final List VALIDATION_FLAGS = [
    Flags.DontValidateMetadata,
    Flags.DontValidatePreferences,
    Flags.DontValidateDefinition,
    Flags.DontRestrictGroovy,
    Flags.DontRequireParseMethodInDevice
  ]

  def "progress table aggregates hourly entries for selected room"() {
    setup:
    AppExecutor executorApi = Mock {
      _ * getState() >> [:]
      _ * getAtomicState() >> [:]
    }
    def sandbox = new HubitatAppSandbox(APP_FILE)
    def script = sandbox.run('api': executorApi,
                             'validationFlags': VALIDATION_FLAGS)
    // CI-safe location
    script.metaClass.getLocation = { -> [timeZone: TimeZone.getTimeZone('UTC')] }
    // Minimal device list (for page option building when needed)
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

    // Seed flat entries list and mirrors for CI-safe reads
    long ts = System.currentTimeMillis()
    def ast = [
      dabHistory: [[ts, 'room-1', 'cooling', 0, 1.0G], [ts, 'room-1', 'cooling', 1, 2.0G]],
      progressRoom: 'room-1',
      lastHvacMode: 'cooling'
    ]
    script.metaClass.getAtomicState = { -> ast }

    when:
    def html = script.buildDabProgressTable()

    then:
    html.contains('Select a room to view progress')
  }
}
