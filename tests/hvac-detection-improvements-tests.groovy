import spock.lang.Specification
import me.biocomp.hubitat_ci.app.HubitatAppSandbox
import me.biocomp.hubitat_ci.api.app_api.AppExecutor
import me.biocomp.hubitat_ci.validation.Flags

class HvacDetectionImprovementsSpec extends Specification {
  private static final File APP_FILE = new File('src/hubitat-flair-vents-app.groovy')
  private static final List VALIDATION_FLAGS = [
    Flags.DontValidateMetadata,
    Flags.DontValidatePreferences,
    Flags.DontValidateDefinition,
    Flags.DontRestrictGroovy,
    Flags.DontRequireParseMethodInDevice,
    Flags.AllowReadingNonInputSettings
  ]

  def "returns idle when inconclusive with no thermostat fallback"() {
    setup:
    AppExecutor api = Mock { _ * getState() >> [:]; _ * getAtomicState() >> [:] }
    def sandbox = new HubitatAppSandbox(APP_FILE)
    def script = sandbox.run('api': api, 'validationFlags': VALIDATION_FLAGS)
    def smallDiffVent = [currentValue: { attr ->
      switch (attr) {
        case 'duct-temperature-c': return 24.7
        case 'room-current-temperature-c': return 24.4 // diff 0.3C < 0.5C threshold
        default: return null
      }
    }] as Expando

    expect:
    script.calculateHvacModeRobust([smallDiffVent]) == 'idle'
  }

  def "updateHvacStateFromDuctTemps runs even when dabEnabled is false"() {
    setup:
    AppExecutor api = Mock { _ * getState() >> [:]; _ * getAtomicState() >> [:] }
    def sandbox = new HubitatAppSandbox(APP_FILE)
    def script = sandbox.run('api': api, 'validationFlags': VALIDATION_FLAGS, 'userSettingValues': [dabEnabled: false])
    def heatingVent = [currentValue: { attr ->
      attr == 'duct-temperature-c' ? 40 : (attr == 'room-current-temperature-c' ? 20 : null)
    }] as Expando
    // Ensure robust detector sees the test vent
    script.metaClass.calculateHvacModeRobust = { -> script.calculateHvacModeRobust([heatingVent]) }

    when:
    script.updateHvacStateFromDuctTemps()
    then:
    (script.atomicState?.thermostat1State?.mode ?: script.atomicState?.hvacCurrentMode) == 'heating'
  }

  def "triggers cooling cycle when any vent indicates cooling"() {
    setup:
    AppExecutor api = Mock { _ * getState() >> [:]; _ * getAtomicState() >> [:] }
    def sandbox = new HubitatAppSandbox(APP_FILE)
    def script = sandbox.run('api': api, 'validationFlags': VALIDATION_FLAGS)
    def coolingVent = [currentValue: { attr ->
      attr == 'duct-temperature-c' ? 18 : (attr == 'room-current-temperature-c' ? 22 : null)
    }] as Expando
    def neutralVent = [currentValue: { attr ->
      attr == 'duct-temperature-c' ? 22 : (attr == 'room-current-temperature-c' ? 22 : null)
    }] as Expando
    script.metaClass.calculateHvacModeRobust = { -> script.calculateHvacModeRobust([coolingVent, neutralVent]) }

    when:
    script.updateHvacStateFromDuctTemps()
    then:
    (script.atomicState?.thermostat1State?.mode ?: script.atomicState?.hvacCurrentMode) == 'cooling'
  }

  def "gracefully handles vents with null temps"() {
    setup:
    AppExecutor api = Mock { _ * getState() >> [:]; _ * getAtomicState() >> [:] }
    def sandbox = new HubitatAppSandbox(APP_FILE)
    def script = sandbox.run('api': api, 'validationFlags': VALIDATION_FLAGS)
    def offlineVent = [currentValue: { attr -> null }] as Expando

    expect:
    script.calculateHvacModeRobust([offlineVent]) == 'idle'
  }
}

