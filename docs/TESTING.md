# Running Tests

## Subset (no private deps)
- Windows: `.\gradlew.bat clean test`
- macOS/Linux: `./gradlew clean test`

## Full suite (Hubitat CI-dependent)
- Place the jar in `libs/` (e.g., `libs/hubitat_ci-1.2.1.jar`).
- Run: `./gradlew hubitatCiTest`