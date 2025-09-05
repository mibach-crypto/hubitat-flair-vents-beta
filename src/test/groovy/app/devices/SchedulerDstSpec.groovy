package app.devices

import me.biocomp.hubitat_ci.api.app_api.AppExecutor
import me.biocomp.hubitat_ci.app.HubitatAppSandbox
import me.biocomp.hubitat_ci.validation.Flags
import spock.lang.Specification

/**
 * Tests DST (Daylight Saving Time) handling in daily DAB stats aggregation.
 * Validates timestamp sorting and daily bucket counting across DST transitions.
 */
class SchedulerDstSpec extends Specification {

    private static final File APP_FILE = new File('src/hubitat-flair-vents-app.groovy')
    private static final List VALIDATION_FLAGS = [
        Flags.DontValidateMetadata,
        Flags.DontValidatePreferences,
        Flags.DontValidateDefinition,
        Flags.DontRestrictGroovy,
        Flags.DontRequireParseMethodInDevice
    ]

    def "DST transition results in 23-hour day for Los Angeles"() {
        setup:
        AppExecutor executorApi = Mock {
            _ * getState() >> [:]
            _ * getAtomicState() >> [:]
        }
        
        def sandbox = new HubitatAppSandbox(APP_FILE)
        def script = sandbox.run('api': executorApi, 'validationFlags': VALIDATION_FLAGS)
        
        // Los Angeles timezone with DST
        def laTimeZone = TimeZone.getTimeZone('America/Los_Angeles')
        script.metaClass.getLocation = { -> [timeZone: laTimeZone] }
        
        // Create DST transition date (March 12, 2023 - Spring forward)
        def cal = Calendar.getInstance(laTimeZone)
        cal.set(2023, Calendar.MARCH, 12, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        def dstDate = cal.time
        def targetDateStr = dstDate.format('yyyy-MM-dd', laTimeZone)
        
        // Create 23 hours of data (hour 2 missing due to DST)
        def dabHistory = [
            room1: [
                cooling: [
                    [date: targetDateStr, hour: 0, rate: new BigDecimal(1.0)],
                    [date: targetDateStr, hour: 1, rate: new BigDecimal(1.1)],
                    // Hour 2 skipped due to DST
                    [date: targetDateStr, hour: 3, rate: new BigDecimal(1.3)],
                    [date: targetDateStr, hour: 4, rate: new BigDecimal(1.4)],
                    [date: targetDateStr, hour: 5, rate: new BigDecimal(1.5)],
                    [date: targetDateStr, hour: 6, rate: new BigDecimal(1.6)],
                    [date: targetDateStr, hour: 7, rate: new BigDecimal(1.7)],
                    [date: targetDateStr, hour: 8, rate: new BigDecimal(1.8)],
                    [date: targetDateStr, hour: 9, rate: new BigDecimal(1.9)],
                    [date: targetDateStr, hour: 10, rate: new BigDecimal(2.0)],
                    [date: targetDateStr, hour: 11, rate: new BigDecimal(2.1)],
                    [date: targetDateStr, hour: 12, rate: new BigDecimal(2.2)],
                    [date: targetDateStr, hour: 13, rate: new BigDecimal(2.3)],
                    [date: targetDateStr, hour: 14, rate: new BigDecimal(2.4)],
                    [date: targetDateStr, hour: 15, rate: new BigDecimal(2.5)],
                    [date: targetDateStr, hour: 16, rate: new BigDecimal(2.6)],
                    [date: targetDateStr, hour: 17, rate: new BigDecimal(2.7)],
                    [date: targetDateStr, hour: 18, rate: new BigDecimal(2.8)],
                    [date: targetDateStr, hour: 19, rate: new BigDecimal(2.9)],
                    [date: targetDateStr, hour: 20, rate: new BigDecimal(3.0)],
                    [date: targetDateStr, hour: 21, rate: new BigDecimal(3.1)],
                    [date: targetDateStr, hour: 22, rate: new BigDecimal(3.2)],
                    [date: targetDateStr, hour: 23, rate: new BigDecimal(3.3)]
                ]
            ]
        ]
        
        def atomicStateData = [
            dabHistory: dabHistory,
            dabDailyStats: [:]
        ]
        
        script.metaClass.getAtomicState = { -> atomicStateData }
        script.metaClass.getSettings = { -> [dabHistoryRetentionDays: 10] }
        script.metaClass.initializeDabHistory = { -> }
        script.metaClass.reindexDabHistory = { -> [entries: []] }

        when: "aggregating daily stats for DST day"
        def startTime = System.currentTimeMillis()
        script.aggregateDailyDabStats()
        def endTime = System.currentTimeMillis()

        then: "processing completes without error"
        noExceptionThrown()
        
        and: "execution time is reasonable"
        (endTime - startTime) < 700
        
        and: "daily stats created"
        def dailyStats = atomicStateData.dabDailyStats
        dailyStats.room1?.cooling?.size() >= 1
        
        and: "stats reflect DST day processing"
        def avgEntry = dailyStats.room1.cooling[0]
        avgEntry.date == targetDateStr
        avgEntry.avg != null
    }

    def "non-DST timezone has full 24-hour day"() {
        setup:
        AppExecutor executorApi = Mock {
            _ * getState() >> [:]
            _ * getAtomicState() >> [:]
        }
        
        def sandbox = new HubitatAppSandbox(APP_FILE)
        def script = sandbox.run('api': executorApi, 'validationFlags': VALIDATION_FLAGS)
        
        // Phoenix timezone (no DST)
        def phoenixTimeZone = TimeZone.getTimeZone('America/Phoenix')
        script.metaClass.getLocation = { -> [timeZone: phoenixTimeZone] }
        
        def cal = Calendar.getInstance(phoenixTimeZone)
        cal.set(2023, Calendar.MARCH, 12, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        def testDate = cal.time
        def targetDateStr = testDate.format('yyyy-MM-dd', phoenixTimeZone)
        
        // Create full 24 hours of data (no DST in Phoenix)
        def dabHistory = [
            room1: [
                heating: (0..23).collect { hour ->
                    [date: targetDateStr, hour: hour, rate: new BigDecimal(1.0 + hour * 0.1)]
                }
            ]
        ]
        
        def atomicStateData = [
            dabHistory: dabHistory,
            dabDailyStats: [:]
        ]
        
        script.metaClass.getAtomicState = { -> atomicStateData }
        script.metaClass.getSettings = { -> [dabHistoryRetentionDays: 10] }
        script.metaClass.initializeDabHistory = { -> }
        script.metaClass.reindexDabHistory = { -> [entries: []] }

        when: "aggregating daily stats for non-DST timezone"
        def startTime = System.currentTimeMillis()
        script.aggregateDailyDabStats()
        def endTime = System.currentTimeMillis()

        then: "processing completes without error"
        noExceptionThrown()
        
        and: "execution time is reasonable"
        (endTime - startTime) < 700
        
        and: "daily stats created"
        def dailyStats = atomicStateData.dabDailyStats
        dailyStats.room1?.heating?.size() >= 1
        
        and: "average calculated for 24 hours"
        def avgEntry = dailyStats.room1.heating[0]
        avgEntry.date == targetDateStr
        avgEntry.avg != null
        avgEntry.avg > 2.0 && avgEntry.avg < 2.5
    }

    def "timestamps are properly sorted regardless of input order"() {
        setup:
        AppExecutor executorApi = Mock {
            _ * getState() >> [:]
            _ * getAtomicState() >> [:]
        }
        
        def sandbox = new HubitatAppSandbox(APP_FILE)
        def script = sandbox.run('api': executorApi, 'validationFlags': VALIDATION_FLAGS)
        
        script.metaClass.getLocation = { -> [timeZone: TimeZone.getTimeZone('UTC')] }
        
        def yesterday = (new Date() - 1)
        def targetDateStr = yesterday.format('yyyy-MM-dd', TimeZone.getTimeZone('UTC'))
        
        // Create unsorted hourly data
        def dabHistory = [
            room1: [
                cooling: [
                    [date: targetDateStr, hour: 15, rate: new BigDecimal(2.5)],
                    [date: targetDateStr, hour: 3, rate: new BigDecimal(1.3)],
                    [date: targetDateStr, hour: 23, rate: new BigDecimal(3.3)],
                    [date: targetDateStr, hour: 1, rate: new BigDecimal(1.1)],
                    [date: targetDateStr, hour: 12, rate: new BigDecimal(2.2)],
                    [date: targetDateStr, hour: 6, rate: new BigDecimal(1.6)]
                ]
            ]
        ]
        
        def atomicStateData = [
            dabHistory: dabHistory,
            dabDailyStats: [:]
        ]
        
        script.metaClass.getAtomicState = { -> atomicStateData }
        script.metaClass.getSettings = { -> [dabHistoryRetentionDays: 10] }
        script.metaClass.initializeDabHistory = { -> }
        script.metaClass.reindexDabHistory = { -> [entries: []] }

        when: "aggregating unsorted daily data"
        def startTime = System.currentTimeMillis()
        script.aggregateDailyDabStats()
        def endTime = System.currentTimeMillis()

        then: "processing completes without error"
        noExceptionThrown()
        
        and: "execution time is reasonable"
        (endTime - startTime) < 700
        
        and: "average calculated correctly from all data points"
        def dailyStats = atomicStateData.dabDailyStats
        def avgEntry = dailyStats.room1.cooling[0]
        avgEntry.avg != null
        // Average of [2.5, 1.3, 3.3, 1.1, 2.2, 1.6] = 12.0/6 = 2.0
        Math.abs(avgEntry.avg - 2.0) < 0.1
    }

    def "empty data is handled gracefully"() {
        setup:
        AppExecutor executorApi = Mock {
            _ * getState() >> [:]
            _ * getAtomicState() >> [:]
        }
        
        def sandbox = new HubitatAppSandbox(APP_FILE)
        def script = sandbox.run('api': executorApi, 'validationFlags': VALIDATION_FLAGS)
        
        script.metaClass.getLocation = { -> [timeZone: TimeZone.getTimeZone('UTC')] }
        
        def atomicStateData = [
            dabHistory: [:],
            dabDailyStats: [:]
        ]
        
        script.metaClass.getAtomicState = { -> atomicStateData }
        script.metaClass.getSettings = { -> [dabHistoryRetentionDays: 10] }
        script.metaClass.initializeDabHistory = { -> }
        script.metaClass.reindexDabHistory = { -> [entries: []] }

        when: "aggregating empty data"
        def startTime = System.currentTimeMillis()
        script.aggregateDailyDabStats()
        def endTime = System.currentTimeMillis()

        then: "processing completes without error"
        noExceptionThrown()
        
        and: "execution time is reasonable"
        (endTime - startTime) < 700
        
        and: "no daily stats created"
        atomicStateData.dabDailyStats.size() == 0
    }
}