package bot.flair.perf

/**
 * Marker interface for slow tests that should be excluded from regular CI runs
 * Use this category to mark performance tests and other long-running tests
 * 
 * Usage:
 * @Category(SlowTest)
 * class MyPerformanceSpec extends Specification { ... }
 */
interface SlowTest {
    // Marker interface - no methods needed
}