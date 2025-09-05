package app.lifecycle

/**
 * Simple Lifecycle Test - Minimal Version
 * 
 * Very basic tests for app lifecycle without external dependencies.
 */

import spock.lang.Specification

class SimpleLifecycleSpec extends Specification {

    def "test basic Spock functionality"() {
        given: "A simple setup"
        def value = 42
        
        when: "A calculation is performed"
        def result = value * 2
        
        then: "The result is correct"
        result == 84
    }

    def "test app file existence"() {
        given: "The app file path"
        def appFile = new File('src/hubitat-flair-vents-app.groovy')
        
        when: "Checking file existence"
        def exists = appFile.exists()
        
        then: "The app file should exist"
        exists == true
    }
}