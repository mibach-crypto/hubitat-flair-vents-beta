package bot.flair

// System Emulation Tests
// Simulates a real Flair setup with multiple vents to verify integration flows

import spock.lang.Specification

class FlairSystemEmulationTest extends Specification {

    private static final File APP_FILE = new File('src/hubitat-flair-vents-app.groovy')

    def "emulates full flair setup and adjusts vents"() {
        setup:
        Map patched = [:]
        def script = new GroovyShell().parse(APP_FILE)

        script.metaClass.settings = [
            dabEnabled: true,
            thermostat1CloseInactiveRooms: false,
            thermostat1AdditionalStandardVents: 0
        ]
        script.metaClass.state = [flairAccessToken: 'test-token']
        script.metaClass.atomicState = [
            thermostat1State: [mode: 'cooling'],
            ventsByRoomId: ['room1': ['vent1'], 'room2': ['vent2']],
            maxHvacRunningTime: 30,
            dabDiagnostics: [:],
            activeRequests: 0,
            requestCounts: [:],
            lastRequestTime: 0,
            stuckRequestCounter: 0
        ]

        def vent1 = new Expando(
            getId: {1},
            getDeviceNetworkId: {'vent1'},
            getLabel: {'Vent1'},
            hasAttribute: { attr -> attr == 'percent-open' || attr == 'room-set-point-c' || attr == 'room-cooling-rate' || attr == 'room-current-temperature-c' || attr == 'room-name' || attr == 'room-active' },
            currentValue: { attr ->
                switch(attr) {
                    case 'room-name': return 'Living Room'
                    case 'percent-open': return 0
                    case 'room-active': return 'true'
                    case 'room-set-point-c': return 22
                    case 'room-cooling-rate': return 0.5
                    case 'room-current-temperature-c': return 25
                    default: return null
                }
            }
        )

        def vent2 = new Expando(
            getId: {2},
            getDeviceNetworkId: {'vent2'},
            getLabel: {'Vent2'},
            hasAttribute: { attr -> attr == 'percent-open' || attr == 'room-set-point-c' || attr == 'room-cooling-rate' || attr == 'room-current-temperature-c' || attr == 'room-name' || attr == 'room-active' },
            currentValue: { attr ->
                switch(attr) {
                    case 'room-name': return 'Bedroom'
                    case 'percent-open': return 0
                    case 'room-active': return 'true'
                    case 'room-set-point-c': return 21
                    case 'room-cooling-rate': return 0.3
                    case 'room-current-temperature-c': return 24
                    default: return null
                }
            }
        )

        script.metaClass.getChildDevice = { id -> id == 'vent1' ? vent1 : id == 'vent2' ? vent2 : null }
        script.metaClass.getChildDevices = { -> [vent1, vent2] }
        script.metaClass.patchVentWithVerification = { device, pct -> patched[device.getDeviceNetworkId()] = pct }
        script.metaClass.appendDabActivityLog = { msg -> }
        script.metaClass.log = { level, tag, msg -> }
        script.metaClass.getRoomTemp = { v -> v.currentValue('room-current-temperature-c') }
        script.metaClass.checkMissingDiagnostics = { -> }

        when:
        def ventData = script.collectVentData('cooling')
        def targets = script.calculateVentTargets(ventData, 'cooling')
        patched.putAll(targets)

        then:
        patched.size() == 2
        patched['vent1'] >= 0 && patched['vent1'] <= 100
        patched['vent2'] >= 0 && patched['vent2'] <= 100
    }
}
