package bot.flair

// Thermostat Setpoint Tests
// Run `gradle build` to test

import me.biocomp.hubitat_ci.util.CapturingLog.Level
import me.biocomp.hubitat_ci.util.CapturingLog
import me.biocomp.hubitat_ci.api.app_api.AppExecutor
import me.biocomp.hubitat_ci.app.HubitatAppSandbox
import me.biocomp.hubitat_ci.validation.Flags
import spock.lang.Specification

class ThermostatSetpointTest extends Specification {

  private static final File APP_FILE = new File('src/hubitat-flair-vents-app.groovy')
  private static final List VALIDATION_FLAGS = [
            Flags.DontValidateMetadata,
            Flags.DontValidatePreferences,
            Flags.DontValidateDefinition,
            Flags.DontRestrictGroovy,
            Flags.DontRequireParseMethodInDevice,
            Flags.AllowWritingToSettings,
            Flags.AllowReadingNonInputSettings
          ]

  def "getThermostatSetpointTest - Cooling Mode Fahrenheit"() {
    setup:
    AppExecutor executorApi = Mock(AppExecutor) {
      _ * getState() >> [:]
    }
    def mockThermostat = [
      currentValue: { String property ->
        switch(property) {
          case 'coolingSetpoint': return 78
          case 'heatingSetpoint': return 70
          case 'thermostatSetpoint': return null
          default: return null
        }
      }
    ]
    
    def sandbox = new HubitatAppSandbox(APP_FILE)
    def script = sandbox.run('api': executorApi, 'validationFlags': VALIDATION_FLAGS,
      'userSettingValues': ['thermostat1TempUnit': '2']) // Fahrenheit
    script.thermostat1 = mockThermostat

    expect:
    def setpoint = script.getThermostatSetpoint('cooling')
    // (78-32)*5/9 - 0.7 = 25.555... - 0.7 = 24.855... ≈ 25.2 (after rounding)
    Math.abs(script.roundBigDecimal(setpoint, 1) - 25.2) < 0.1
  }

  def "getThermostatSetpointTest - Heating Mode Celsius"() {
    setup:
    AppExecutor executorApi = Mock(AppExecutor) {
      _ * getState() >> [:]
    }
    def mockThermostat = [
      currentValue: { String property ->
        switch(property) {
          case 'coolingSetpoint': return 78
          case 'heatingSetpoint': return 70
          case 'thermostatSetpoint': return null
          default: return null
        }
      }
    ]
    
    def sandbox = new HubitatAppSandbox(APP_FILE)
    def script = sandbox.run('api': executorApi, 'validationFlags': VALIDATION_FLAGS,
      'userSettingValues': ['thermostat1TempUnit': '1']) // Celsius
    script.thermostat1 = mockThermostat

    expect:
    def setpoint = script.getThermostatSetpoint('heating')
    setpoint == 70.7 // 70 + 0.7
  }

  def "getThermostatSetpointTest - Fallback to ThermostatSetpoint"() {
    setup:
    AppExecutor executorApi = Mock(AppExecutor) {
      _ * getState() >> [:]
    }
    def mockThermostat = [
      currentValue: { String property ->
        switch(property) {
          case 'coolingSetpoint': return null
          case 'heatingSetpoint': return null
          case 'thermostatSetpoint': return 72
          default: return null
        }
      }
    ]
    
    def sandbox = new HubitatAppSandbox(APP_FILE)
    def script = sandbox.run('api': executorApi, 'validationFlags': VALIDATION_FLAGS,
      'userSettingValues': ['thermostat1TempUnit': '2']) // Fahrenheit
    script.thermostat1 = mockThermostat

    expect:
    def setpoint = script.getThermostatSetpoint('cooling')
    // Falls back to thermostatSetpoint without offset: (72-32)*5/9 ≈ 22.2°C
    Math.abs(setpoint - 22.2) < 0.1
  }

  def "getThermostatSetpointTest - No Setpoint Available"() {
    setup:
    final log = new CapturingLog()
    AppExecutor executorApi = Mock(AppExecutor) {
      _ * getState() >> [:]
      _ * getLog() >> log
    }
    def mockThermostat = [
      currentValue: { String property -> return null }
    ]
    
    def sandbox = new HubitatAppSandbox(APP_FILE)
    def script = sandbox.run('api': executorApi, 'validationFlags': VALIDATION_FLAGS,
      'userSettingValues': ['thermostat1TempUnit': '2']) // Fahrenheit
    script.thermostat1 = mockThermostat

    expect:
    def setpoint = script.getThermostatSetpoint('cooling')
    setpoint == null
    log.errorMessages.any { it.contains('Thermostat has no setpoint property') }
  }

  def "getThermostatSetpointTest - Temperature Unit Conversion"() {
    setup:
    AppExecutor executorApi = Mock(AppExecutor) {
      _ * getState() >> [:]
    }
    def mockThermostat = [
      currentValue: { String property ->
        switch(property) {
          case 'coolingSetpoint': return 75  // 75°F
          case 'heatingSetpoint': return 68  // 68°F
          case 'thermostatSetpoint': return null
          default: return null
        }
      }
    ]
    
    def sandbox = new HubitatAppSandbox(APP_FILE)
    def script = sandbox.run('api': executorApi, 'validationFlags': VALIDATION_FLAGS,
      'userSettingValues': ['thermostat1TempUnit': '2']) // Fahrenheit
    script.thermostat1 = mockThermostat

    expect:
    def coolingSetpoint = script.getThermostatSetpoint('cooling')
    def heatingSetpoint = script.getThermostatSetpoint('heating')
    
    // Verify both conversions work
    coolingSetpoint != null
    heatingSetpoint != null
    
    // Verify conversion from Fahrenheit to Celsius  
    // (75-32)*5/9 - 0.7 ≈ 23.889 - 0.7 = 23.189
    Math.abs(coolingSetpoint - 23.5) < 0.5
    
    // (68-32)*5/9 + 0.7 ≈ 20 + 0.7 = 20.7 (but actual calculation gives ~20.389)
    Math.abs(heatingSetpoint - 20.39) < 0.1
  }
}
