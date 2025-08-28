/**
 * Flair Vent Tile (virtual)
 * Displays combined HTML for Hubitat Dashboard (Attribute template)
 */

metadata {
    definition(name: 'Flair Vent Tile', namespace: 'bot.flair', author: 'Codex') {
        capability 'Sensor'
        capability 'Actuator'
        capability 'Refresh'
        capability 'SwitchLevel'
        capability 'TemperatureMeasurement'

        attribute 'html', 'STRING'

        command 'setManualMode'
        command 'setAutoMode'
        command 'nudgeUp'
        command 'nudgeDown'
        command 'setVentPercent', [[name: 'percent*', type: 'NUMBER']]
    }

    preferences {
        input name: 'debugOutput', type: 'bool', title: 'Enable debug logging', defaultValue: false
    }
}

private logD(msg) { if (settings?.debugOutput) log.debug "[Tile] ${device.label}: ${msg}" }

def installed() { refresh() }
def updated() { refresh() }
def refresh() { logD 'refresh' }

def setLevel(level) { setVentPercent(level as int) }

def setVentPercent(percent) {
    logD "setVentPercent ${percent}"
    try { parent?.tileSetVentPercent(device?.deviceNetworkId, percent as int) } catch (e) { log.warn "Tile setVentPercent error: ${e?.message}" }
}

def setManualMode() {
    logD 'setManualMode'
    try { parent?.tileSetManualMode(device?.deviceNetworkId) } catch (e) { log.warn "Tile setManualMode error: ${e?.message}" }
}

def setAutoMode() {
    logD 'setAutoMode'
    try { parent?.tileSetAutoMode(device?.deviceNetworkId) } catch (e) { log.warn "Tile setAutoMode error: ${e?.message}" }
}

def nudgeUp() {
    Integer lvl = (device.currentValue('level') ?: 0) as int
    setVentPercent(Math.min(100, lvl + 5))
}

def nudgeDown() {
    Integer lvl = (device.currentValue('level') ?: 0) as int
    setVentPercent(Math.max(0, lvl - 5))
}

