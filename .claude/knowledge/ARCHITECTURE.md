# Hubitat Expert Agent Architecture

## Overview
7 parent agents + 6 subagents each = 42 domain subagents
6 Groovy 2.4.21 language subagents (cross-cutting, available to any parent)
Total: 55 agent definitions in `~/.claude/agents/`

## Knowledge Bases (in ~/.claude/groovy-masters/)
- `lang-core-knowledge.md` — Groovy syntax, operators, semantics
- `oop-closures-knowledge.md` — OOP, traits, closures, delegation
- `gdk-testing-knowledge.md` — GDK, collections, IO, Spock
- `data-integration-knowledge.md` — JSON, XML, SQL, templates, Java interop
- `tooling-build-knowledge.md` — groovyc, Gradle, Maven, Grape, CLI
- `hubitat-developer-docs.md` — Developer API, lifecycle, sandbox, imports
- `hubitat-platform-capabilities.md` — Platform features, C-8 Pro, protocols
- `hubitat-debugging-guide.md` — Debugging techniques, errors, pitfalls
- `flair-app-analysis.md` — Flair Vents app deep analysis

## Hierarchy

### Parent 1: hubitat-debugger
Master of debugging Hubitat apps and drivers.
KB: hubitat-debugging-guide.md
Subagents:
1. `debug-log-analyst` — Log levels, filtering, live vs past, descriptionText patterns
2. `debug-error-diagnostician` — Exception types, NullPointer, timeouts, ClassNotFound
3. `debug-state-inspector` — state/atomicState, serialization, race conditions, singleThreaded
4. `debug-http-api` — HTTP/API errors, OAuth token issues, circuit breakers, async callbacks
5. `debug-scheduling-events` — Schedule failures, duplicate schedules, event subscription issues
6. `debug-performance` — Memory limits, execution timeouts, hub resource exhaustion

### Parent 2: hubitat-flair-master
Master of the user's Flair Vents Beta application.
KB: flair-app-analysis.md
Subagents:
1. `flair-oauth-api` — OAuth2 client_credentials, token refresh, Flair API endpoints, JSON:API
2. `flair-dab-algorithm` — Exponential vent model, rate learning, EWMA smoothing, MAD, adaptive boost
3. `flair-hvac-control` — HVAC state detection, duct temps, thermostat fallback, heating/cooling
4. `flair-device-mgmt` — Vent/Puck/Tile drivers, parent-child delegation, attributes, commands
5. `flair-caching-data` — LRU cache, raw data cache (24h), efficiency export/import, atomicState
6. `flair-ui-dashboard` — 13 preference pages, diagnostics, DAB charts, quick controls, tiles

### Parent 3: hubitat-app-developer
Master of Hubitat app development patterns.
KB: hubitat-developer-docs.md
Subagents:
1. `appdev-lifecycle` — installed/updated/uninstalled/initialize, definition, metadata, libraries
2. `appdev-preferences-ui` — All 17 input types, dynamicPage, sections, submitOnChange
3. `appdev-subscriptions` — subscribe() overloads, handler signatures, filterEvents, location events
4. `appdev-scheduling` — runIn, runEvery*, schedule/cron, runOnce, unschedule, overwrite behavior
5. `appdev-http-integration` — Sync/async HTTP, OAuth mappings, HubAction, async callbacks
6. `appdev-state-data` — state vs atomicState, parent/child patterns, hub variables, #include

### Parent 4: hubitat-syntax-restrictions
Master of Groovy 2.4.21 diffs and Hubitat sandbox constraints.
KB: lang-core-knowledge.md + hubitat-developer-docs.md (restrictions)
Subagents:
1. `syntax-imports` — Allowed vs blocked imports, ClassNotFoundException, workarounds
2. `syntax-sandbox` — No file I/O, no threading, no reflection, execution/memory limits
3. `syntax-groovy24-gaps` — Missing features vs modern Groovy, no lambdas, no method refs
4. `syntax-types-coercion` — Dynamic typing, GString vs String, auto-boxing, safe navigation
5. `syntax-collections` — State serialization Integer→String keys, ConcurrentModification, JSON
6. `syntax-migration` — SmartThings porting, namespace changes, API differences, removed features

### Parent 5: hubitat-platform
Master of all Hubitat platform capabilities.
KB: hubitat-platform-capabilities.md
Subagents:
1. `platform-protocols` — Z-Wave 800, Zigbee 3.0, LAN, Matter 1.5, Bluetooth, virtual devices
2. `platform-builtin-apps` — Rule Machine, Simple Automation, Groups, HSM, Mode Manager, etc.
3. `platform-dashboards` — Hubitat/Easy Dashboard, tiles, layouts, custom tiles, attribute tiles
4. `platform-integrations` — Maker API, EventSocket, LogSocket, cloud, Alexa/Google/HomeKit
5. `platform-hub-mesh` — Multi-hub networking, device sharing, modes, hub variables, mDNS
6. `platform-admin` — Backup/restore, firmware, network config, File Manager, settings

### Parent 6: hubitat-c8pro
Master of C-8 Pro hub hardware and unique capabilities.
KB: hubitat-platform-capabilities.md (C-8 Pro sections)
Subagents:
1. `c8pro-hardware` — CPU, RAM, storage, radios, antennas, power, form factor
2. `c8pro-zwave-lr` — Z-Wave 800 + Long Range, 4000 nodes, range, ghost node handling
3. `c8pro-zigbee` — Zigbee 3.0 mesh, channels, interference, pairing, group messaging
4. `c8pro-matter-thread` — Matter 1.5 controller (not bridge), Thread limits, external TBR
5. `c8pro-bluetooth` — BTHome v2 (C-8 Pro exclusive), supported sensors, limitations
6. `c8pro-optimization` — Memory, radio coexistence, antenna placement, firmware, performance

### Parent 7: hubitat-testing
Master of testing and code quality for Hubitat apps.
KB: gdk-testing-knowledge.md + flair-app-analysis.md
Subagents:
1. `testing-spock` — Spock framework specs, blocks, data-driven, mocking, interactions
2. `testing-virtual-devices` — Virtual device testing, event simulation, no-hardware testing
3. `testing-unit-patterns` — Mocking Hubitat platform APIs, state simulation, isolation
4. `testing-integration` — Parent/child testing, API mocks, schedule testing, end-to-end
5. `testing-code-quality` — Anti-patterns, code smells, refactoring for Hubitat constraints
6. `testing-ci-automation` — CI/CD setup, custom property overrides, automated pipelines

## Cross-Cutting: Groovy 2.4.21 Language Subagents
Available to ANY parent agent for language-specific questions.
1. `groovy-lang-core` — KB: lang-core-knowledge.md
2. `groovy-oop-closures` — KB: oop-closures-knowledge.md
3. `groovy-metaprogramming` — KB: (to be regenerated)
4. `groovy-gdk-testing` — KB: gdk-testing-knowledge.md
5. `groovy-data-integration` — KB: data-integration-knowledge.md
6. `groovy-tooling-build` — KB: tooling-build-knowledge.md
