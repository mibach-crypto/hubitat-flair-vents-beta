package bot.flair

import me.biocomp.hubitat_ci.api.app_api.AppExecutor
import me.biocomp.hubitat_ci.app.HubitatAppSandbox
import me.biocomp.hubitat_ci.validation.Flags
import spock.lang.Specification

class QuickControlsTests extends Specification {
  private static final File APP_FILE = new File('src/hubitat-flair-vents-app.groovy')
  private static final List VALIDATION_FLAGS = [
    Flags.DontValidateMetadata,
    Flags.DontValidatePreferences,
    Flags.DontValidateDefinition,
    Flags.DontRestrictGroovy,
    Flags.DontRequireParseMethodInDevice,
    Flags.AllowReadingNonInputSettings
  ]

  def "quick controls include vents with and without getTypeName"() {
    setup:
    AppExecutor executorApi = Mock { _ * getState() >> [:] }
    def sandbox = new HubitatAppSandbox(APP_FILE)
    def script = sandbox.run('api': executorApi, 'validationFlags': VALIDATION_FLAGS)
    script.atomicState = [:]

    def ventWithMethod = new Expando(
      getTypeName: { 'Flair vents' },
      getDeviceNetworkId: { 'vent1' },
      getLabel: { 'Vent 1' },
      currentValue: { attr ->
        switch (attr) {
          case 'room-id': return 'room1'
          case 'room-name': return 'Room 1'
          case 'percent-open': return 50
          default: return null
        }
      }
    )

    def ventWithoutMethod = new Expando(
      typeName: 'Flair vents',
      getDeviceNetworkId: { 'vent2' },
      getLabel: { 'Vent 2' },
      currentValue: { attr ->
        switch (attr) {
          case 'room-id': return 'room2'
          case 'room-name': return 'Room 2'
          case 'percent-open': return 25
          default: return null
        }
      }
    )

    script.metaClass.getChildDevices = { -> [ventWithMethod, ventWithoutMethod] }

    when:
    def page = script.quickControlsPage()

    then:
    script.atomicState.qcDeviceMap.values().containsAll(['vent1', 'vent2'])
    page.toString().contains('Room 1')
    page.toString().contains('Room 2')
  }
}
