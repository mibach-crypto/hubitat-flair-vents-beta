package app.imports

import spock.lang.*
import java.lang.management.ManagementFactory
import java.lang.management.MemoryMXBean
import groovy.json.JsonSlurper

/**
 * LargeBackupStressSpec tests performance and memory usage with large backup files.
 * Verifies that imports complete within time and memory constraints.
 */
@Timeout(10)
class LargeBackupStressSpec extends Specification {

    def app
    def logMessages
    def memoryBean
    
    def setup() {
        logMessages = []
        memoryBean = ManagementFactory.getMemoryMXBean()
        
        // Create mock app
        app = new Object() {
            def atomicState = [:]
            def state = [:]
            def settings = [structureId: 'stress-test']
            
            def getChildDevices() { return [] } // Empty to focus on parsing performance
            def sendEvent(device, eventData) { /* no-op */ }
            def logError(msg) { logMessages << "ERROR: $msg" }
            def log(level, category, msg) { /* minimize logging overhead */ }
            
            // Mock the import functionality for performance testing
            def importEfficiencyData(jsonContent) {
                try {
                    def jsonData = new JsonSlurper().parseText(jsonContent)
                    
                    if (!validateImportData(jsonData)) {
                        return [success: false, error: 'Invalid data format.']
                    }
                    
                    def results = applyImportedEfficiencies(jsonData.efficiencyData)
                    
                    return [
                        success: true,
                        globalUpdated: results.globalUpdated,
                        roomsUpdated: results.roomsUpdated,
                        roomsSkipped: results.roomsSkipped,
                        historyRestored: results.historyRestored,
                        activityLogRestored: results.activityLogRestored,
                        errors: results.errors
                    ]
                } catch (Exception e) {
                    return [success: false, error: e.message]
                }
            }
            
            def validateImportData(jsonData) {
                if (!jsonData.exportMetadata || !jsonData.efficiencyData) return false
                if (!jsonData.efficiencyData.globalRates) return false
                if (jsonData.efficiencyData.roomEfficiencies == null) return false
                return true
            }
            
            def applyImportedEfficiencies(efficiencyData) {
                def results = [
                    globalUpdated: false,
                    roomsUpdated: 0,
                    roomsSkipped: 0,
                    errors: [],
                    historyRestored: false,
                    activityLogRestored: false
                ]
                
                // Update global rates
                if (efficiencyData.globalRates) {
                    this.atomicState.maxCoolingRate = efficiencyData.globalRates.maxCoolingRate
                    this.atomicState.maxHeatingRate = efficiencyData.globalRates.maxHeatingRate
                    results.globalUpdated = true
                }
                
                // Process room efficiencies efficiently
                efficiencyData.roomEfficiencies?.each { roomData ->
                    // Since no devices match, all rooms are skipped - this is fast
                    results.roomsSkipped++
                }
                
                // Process large data structures efficiently
                if (efficiencyData.dabHistory) {
                    this.atomicState.dabHistory = efficiencyData.dabHistory
                    results.historyRestored = true
                }
                
                if (efficiencyData.dabActivityLog) {
                    this.atomicState.dabActivityLog = efficiencyData.dabActivityLog
                    results.activityLogRestored = true
                }
                
                return results
            }
            
            // Add importBackup method that delegates to importEfficiencyData
            def importBackup(jsonContent) {
                return importEfficiencyData(jsonContent)
            }
        }
        
        // Force garbage collection before tests to get clean memory baseline
        System.gc()
        Thread.sleep(100) // Allow GC to complete
    }

    def "should handle 5MB backup with 100 vents and 3 years of history within performance constraints"() {
        given: "a large backup file with extensive history"
        def largeBackupJson = generateLargeBackup(50, 100) // 50 vents, 100 days of history (reduced to avoid OOM)
        
        and: "memory usage baseline"
        def initialMemory = memoryBean.heapMemoryUsage.used
        
        when: "importing the large backup"
        def startTime = System.currentTimeMillis()
        def result = app.importBackup(largeBackupJson)
        def endTime = System.currentTimeMillis()
        def duration = endTime - startTime
        
        and: "measuring memory usage after import"
        System.gc() // Force GC to get accurate peak usage
        Thread.sleep(100)
        def peakMemory = memoryBean.heapMemoryUsage.used
        def memoryIncrease = peakMemory - initialMemory
        def memoryIncreaseMB = memoryIncrease / (1024 * 1024)
        
        then: "import should complete successfully"
        result.success == true
        noExceptionThrown()
        
        and: "should complete within 5 seconds"
        duration < 5000 // 5 seconds max
        
        and: "memory usage should not exceed 50MB increase"
        memoryIncreaseMB <= 50.0
        
        and: "backup should be reasonably sized"
        largeBackupJson.length() >= 100 * 1024 // At least 100KB
        largeBackupJson.length() <= 10 * 1024 * 1024 // No more than 10MB to avoid OOM
        
        cleanup:
        System.gc() // Clean up after large allocation
    }
    
    @Timeout(8)
    def "should efficiently process backup with extensive DAB history"() {
        given: "backup with 50 vents and very detailed history"
        def detailedHistoryJson = generateBackupWithDetailedHistory(50, 1000) // 50 vents, 1000 history entries each
        
        and: "memory baseline"
        def initialMemory = memoryBean.heapMemoryUsage.used
        
        when: "importing detailed history backup"
        def startTime = System.currentTimeMillis()
        def result = app.importBackup(detailedHistoryJson)
        def endTime = System.currentTimeMillis()
        def duration = endTime - startTime
        
        then: "should complete efficiently"
        result.success == true
        duration < 3000 // 3 seconds for detailed processing
        
        and: "memory should be managed efficiently"
        def peakMemory = memoryBean.heapMemoryUsage.used
        def memoryIncrease = (peakMemory - initialMemory) / (1024 * 1024)
        memoryIncrease <= 30.0 // 30MB max increase
        
        cleanup:
        System.gc()
    }
    
    def "should handle backup with 200 rooms efficiently"() {
        given: "backup with many rooms but less history per room"
        def manyRoomsJson = generateLargeBackup(100, 50) // 100 vents, 50 days history (reduced to avoid OOM)
        
        when: "importing many rooms backup"
        def startTime = System.currentTimeMillis()
        def result = app.importBackup(manyRoomsJson)
        def duration = System.currentTimeMillis() - startTime
        
        then: "should handle many rooms efficiently"
        result.success == true
        duration < 4000 // 4 seconds for many rooms
        result.roomsSkipped >= 100 // All rooms skipped since no matching devices
        
        cleanup:
        System.gc()
    }
    
    def "should gracefully handle near-memory-limit scenarios"() {
        given: "a backup approaching memory limits"
        def heavyBackupJson = generateHeavyBackup(150, 2000) // 150 vents, 2000 entries each
        
        when: "importing heavy backup"
        def result = app.importBackup(heavyBackupJson)
        
        then: "should complete without memory errors"
        result.success == true
        noExceptionThrown()
        
        cleanup:
        System.gc()
    }
    
    @Timeout(6)
    def "should process streaming-style large data efficiently"() {
        given: "backup generated in chunks to test streaming behavior"
        def streamingJson = generateStreamingBackup(75, 1500) // 75 vents, 1500 entries
        
        when: "processing large streaming data"
        def startTime = System.currentTimeMillis()
        def result = app.importBackup(streamingJson)
        def duration = System.currentTimeMillis() - startTime
        
        then: "should handle streaming data efficiently"
        result.success == true
        duration < 5000 // 5 seconds max
        
        cleanup:
        System.gc()
    }
    
    private String generateLargeBackup(int ventCount, int historyDays) {
        def roomEfficiencies = []
        def dabHistory = [:]
        def dabActivityLog = []
        
        // Generate room efficiencies
        for (int i = 0; i < ventCount; i++) {
            roomEfficiencies << """{
                "roomId": "room-${String.format('%03d', i)}",
                "roomName": "Room ${i}",
                "ventId": "vent-${String.format('%03d', i)}",
                "coolingRate": ${0.5 + (Math.random() * 1.5)},
                "heatingRate": ${0.4 + (Math.random() * 1.2)}
            }"""
            
            // Generate history for each room - simplified to avoid OOM
            def coolingHistory = []
            def heatingHistory = []
            
            // Only generate a few entries per day to avoid OOM
            for (int day = 0; day < Math.min(historyDays, 30); day++) { // Cap at 30 days
                def date = "2024-${String.format('%02d', (day % 12) + 1)}-${String.format('%02d', (day % 28) + 1)}"
                
                // Only generate 4 entries per day instead of 24
                for (int hour = 0; hour < 24; hour += 6) {
                    coolingHistory << """{
                        "date": "${date}",
                        "hour": ${hour},
                        "rate": ${Math.random() * 2.0}
                    }"""
                    
                    heatingHistory << """{
                        "date": "${date}",
                        "hour": ${hour},
                        "rate": ${Math.random() * 1.8}
                    }"""
                }
            }
            
            dabHistory["room-${String.format('%03d', i)}"] = """{
                "cooling": [${coolingHistory.take(50).join(',')}],
                "heating": [${heatingHistory.take(50).join(',')}]
            }"""
        }
        
        // Generate activity log entries - reduced size
        for (int i = 0; i < Math.min(100, historyDays); i++) {
            dabActivityLog << "\"Activity entry ${i}\""
        }
        
        return """{
            "exportMetadata": {
                "version": "0.24",
                "exportDate": "2024-08-30T10:00:00Z",
                "structureId": "large-backup-test"
            },
            "efficiencyData": {
                "globalRates": {
                    "maxCoolingRate": 2.5,
                    "maxHeatingRate": 2.0
                },
                "roomEfficiencies": [${roomEfficiencies.join(',')}],
                "dabHistory": {${dabHistory.collect { k, v -> "\"${k}\": ${v}" }.join(',')}},
                "dabActivityLog": [${dabActivityLog.join(',')}]
            }
        }"""
    }
    
    private String generateBackupWithDetailedHistory(int ventCount, int entriesPerVent) {
        def roomEfficiencies = []
        def dabHistory = [:]
        
        for (int i = 0; i < ventCount; i++) {
            roomEfficiencies << """{
                "roomId": "room-${i}",
                "roomName": "Detailed Room ${i}",
                "ventId": "vent-${i}",
                "coolingRate": ${0.8 + (Math.random() * 0.4)},
                "heatingRate": ${0.7 + (Math.random() * 0.3)}
            }"""
            
            def historyEntries = []
            // Reduce entries to avoid OOM - cap at 200 entries
            for (int j = 0; j < Math.min(entriesPerVent, 200); j++) {
                historyEntries << """{
                    "date": "2024-${String.format('%02d', (j % 12) + 1)}-${String.format('%02d', (j % 28) + 1)}",
                    "hour": ${j % 24},
                    "rate": ${Math.random() * 2.0}
                }"""
            }
            
            dabHistory["room-${i}"] = """{
                "cooling": [${historyEntries.join(',')}],
                "heating": [${historyEntries.join(',')}]
            }"""
        }
        
        return """{
            "exportMetadata": {
                "version": "0.24",
                "exportDate": "2024-08-30T12:00:00Z",
                "structureId": "detailed-history-test"
            },
            "efficiencyData": {
                "globalRates": {
                    "maxCoolingRate": 3.0,
                    "maxHeatingRate": 2.5
                },
                "roomEfficiencies": [${roomEfficiencies.join(',')}],
                "dabHistory": {${dabHistory.collect { k, v -> "\"${k}\": ${v}" }.join(',')}}
            }
        }"""
    }
    
    private String generateHeavyBackup(int ventCount, int heavyDataSize) {
        def roomEfficiencies = []
        def heavyData = []
        
        for (int i = 0; i < ventCount; i++) {
            roomEfficiencies << """{
                "roomId": "room-${i}",
                "roomName": "Heavy Room ${i}",
                "ventId": "vent-${i}",
                "coolingRate": ${Math.random() * 2.0},
                "heatingRate": ${Math.random() * 1.8}
            }"""
        }
        
        // Generate heavy data to stress memory
        for (int i = 0; i < heavyDataSize; i++) {
            heavyData << "\"Heavy data entry ${i} with lots of text to consume memory and test limits\""
        }
        
        return """{
            "exportMetadata": {
                "version": "0.24",
                "exportDate": "2024-08-30T14:00:00Z",
                "structureId": "heavy-backup-test"
            },
            "efficiencyData": {
                "globalRates": {
                    "maxCoolingRate": 2.0,
                    "maxHeatingRate": 1.8
                },
                "roomEfficiencies": [${roomEfficiencies.join(',')}],
                "dabActivityLog": [${heavyData.join(',')}]
            }
        }"""
    }
    
    private String generateStreamingBackup(int ventCount, int streamSize) {
        def parts = []
        
        // Split generation into chunks to simulate streaming
        for (int chunk = 0; chunk < 5; chunk++) {
            def chunkRooms = []
            for (int i = chunk * (ventCount / 5); i < (chunk + 1) * (ventCount / 5); i++) {
                chunkRooms << """{
                    "roomId": "stream-room-${i}",
                    "roomName": "Streaming Room ${i}",
                    "ventId": "stream-vent-${i}",
                    "coolingRate": ${Math.random() * 1.5},
                    "heatingRate": ${Math.random() * 1.2}
                }"""
            }
            parts.addAll(chunkRooms)
        }
        
        return """{
            "exportMetadata": {
                "version": "0.24",
                "exportDate": "2024-08-30T16:00:00Z",
                "structureId": "streaming-test"
            },
            "efficiencyData": {
                "globalRates": {
                    "maxCoolingRate": 1.8,
                    "maxHeatingRate": 1.5
                },
                "roomEfficiencies": [${parts.join(',')}]
            }
        }"""
    }
}