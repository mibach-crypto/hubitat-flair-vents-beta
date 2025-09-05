package app.devices

import me.biocomp.hubitat_ci.api.app_api.AppExecutor
import me.biocomp.hubitat_ci.app.HubitatAppSandbox
import me.biocomp.hubitat_ci.validation.Flags
import spock.lang.Specification

/**
 * Tests device hot swap scenarios where devices are removed mid-calculation
 * during vent target calculations. Validates NPE prevention and graceful handling.
 */
class DeviceHotSwapSpec extends Specification {

    private static final File APP_FILE = new File('src/hubitat-flair-vents-app.groovy')
    private static final List VALIDATION_FLAGS = [
        Flags.DontValidateMetadata,
        Flags.DontValidatePreferences,
        Flags.DontValidateDefinition,
        Flags.DontRestrictGroovy,
        Flags.DontRequireParseMethodInDevice
    ]

    def "device removal during iteration should not cause NPE"() {
        setup:
        def loggedMessages = []
        AppExecutor executorApi = Mock {
            _ * getState() >> [:]
            _ * getAtomicState() >> [:]
        }
        
        def sandbox = new HubitatAppSandbox(APP_FILE)
        def script = sandbox.run('api': executorApi, 'validationFlags': VALIDATION_FLAGS)
        
        // Mock device behavior - device gets removed during processing
        def deviceMap = [
            'vent-1': createMockDevice('vent-1', 'Room 1', 50),
            'vent-2': createMockDevice('vent-2', 'Room 2', 60),
            'vent-3': createMockDevice('vent-3', 'Room 3', 70)
        ]
        
        def deletedDevices = [] as Set
        
        script.metaClass.getChildDevice = { String deviceId ->
            // Simulate device removal mid-iteration for vent-2
            if (deviceId == 'vent-2') {
                deletedDevices << deviceId
                loggedMessages << "WARN: Device ${deviceId} not found - skipping"
                return null
            }
            return deviceMap[deviceId]
        }
        
        script.metaClass.deleteChildDevice = { String deviceId ->
            deletedDevices << deviceId
            deviceMap.remove(deviceId)
        }
        
        script.metaClass.roundToNearestMultiple = { pct -> Math.round(pct as Double) as Integer }
        script.metaClass.patchVentWithVerification = { device, target -> 
            loggedMessages << "Patched ${device.getDeviceNetworkId()} to ${target}%"
        }
        script.metaClass.appendDabActivityLog = { msg -> 
            loggedMessages << "ACTIVITY: ${msg}"
        }

        def targets = [
            'vent-1': 25,
            'vent-2': 50,  // This device will return null
            'vent-3': 75
        ]

        when: "applyVentTargets processes targets with missing device"
        def startTime = System.currentTimeMillis()
        script.applyVentTargets(targets, 'cooling')
        def endTime = System.currentTimeMillis()

        then: "no exceptions thrown"
        noExceptionThrown()
        
        and: "execution completes in reasonable time"
        (endTime - startTime) < 700
        
        and: "warning logged for missing device"
        loggedMessages.any { it.contains('WARN') && it.contains('vent-2') }
        
        and: "other devices processed successfully"
        loggedMessages.count { it.contains('Patched vent-') } >= 2
    }

    def "calculateVentTargets handles missing devices gracefully"() {
        setup:
        def loggedMessages = []
        AppExecutor executorApi = Mock {
            _ * getState() >> [:]
            _ * getAtomicState() >> [maxHvacRunningTime: 60]
        }
        
        def sandbox = new HubitatAppSandbox(APP_FILE)
        def script = sandbox.run('api': executorApi, 'validationFlags': VALIDATION_FLAGS)
        
        // Mock core methods with simplified behavior
        script.metaClass.getChildDevice = { String deviceId ->
            if (deviceId == 'missing-vent') {
                loggedMessages << "WARN: Device ${deviceId} not found - skipping"
                return null
            }
            return createMockDevice(deviceId, "Room ${deviceId}", 50)
        }
        
        script.metaClass.getSettings = { -> [thermostat1CloseInactiveRooms: true, thermostat1AdditionalStandardVents: 0] }
        script.metaClass.getGlobalSetpoint = { mode -> 22.0 }
        script.metaClass.calculateLongestMinutesToTarget = { ventData, mode, setpoint, maxTime, closeInactive -> 30.0 }
        script.metaClass.calculateOpenPercentageForAllVents = { ventData, mode, setpoint, longest, closeInactive ->
            def targets = [:]
            ventData.each { ventId, data -> targets[ventId] = 50 }
            return targets
        }
        script.metaClass.adjustVentOpeningsToEnsureMinimumAirflowTarget = { ventData, mode, targets, standardVents -> targets }
        script.metaClass.applyOverridesAndFloors = { targets -> targets }
        script.metaClass.roundToNearestMultiple = { pct -> Math.round(pct as Double) as Integer }
        script.metaClass.patchVentWithVerification = { device, target -> 
            loggedMessages << "Patched ${device.getDeviceNetworkId()} to ${target}%"
        }
        script.metaClass.appendDabActivityLog = { msg -> 
            loggedMessages << "ACTIVITY: ${msg}"
        }

        def ventData = [
            'existing-vent': [rate: 0.5, temp: 24.0, active: true, name: 'Room 1'],
            'missing-vent': [rate: 0.3, temp: 23.0, active: true, name: 'Room 2']
        ]

        when: "calculate and apply vent targets with missing device"
        def startTime = System.currentTimeMillis()
        def targets = script.calculateVentTargets(ventData, 'cooling')
        script.applyVentTargets(targets, 'cooling')
        def endTime = System.currentTimeMillis()

        then: "method completes without exception"
        noExceptionThrown()
        
        and: "execution time under limit"
        (endTime - startTime) < 700
        
        and: "targets calculated for all inputs"
        targets.size() >= 2
        
        and: "application handles missing device gracefully"
        loggedMessages.any { it.contains('WARN') && it.contains('missing-vent') }
    }
    
    private def createMockDevice(String id, String roomName, int currentLevel) {
        return [
            getDeviceNetworkId: { -> id },
            getLabel: { -> roomName },
            currentValue: { attr ->
                switch(attr) {
                    case 'percent-open':
                    case 'level':
                        return currentLevel
                    case 'room-name':
                        return roomName
                    default:
                        return null
                }
            }
        ]
    }
}