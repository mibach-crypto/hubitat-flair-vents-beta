# Hubitat Integration for Flair Smart Vents

This app provides comprehensive control of [Flair Smart Vents](https://flair.co/) through [Hubitat](https://hubitat.com/), introducing intelligent and adaptive air management for your home's HVAC system.


## Key Features

### Dynamic Airflow Balancing
Harness the power of Dynamic Airflow Balancing to refine air distribution throughout your home. Achieve optimal temperatures with fewer vent adjustments, extending the lifespan of vent motors and conserving battery life. Benefits include:
- **Rate of temperature change calculation** in each room for precise vent adjustment.
- **Reduced adjustments** mean less wear on vent motors and quieter operation.
- **Minimum airflow compliance** to prevent HVAC issues from insufficient airflow, particularly useful when integrating Flair Smart Vents with traditional vents.
- **Hourly DAB insights** via built-in chart and table showing per-room calculations for each hour of the day.

### Enhanced Vent Control and Combined Airflow Management
This integration doesn't just enable remote control over each Flair vent; it smartly manages airflow to ensure your HVAC system operates efficiently without damage. Key features include:
- **Precise control** over each vent, allowing you to set exact open levels for customized airflow.
- **Combined airflow management** calculates total airflow from both Smart and conventional vents, ensuring the system meets minimum airflow requirements to safeguard your HVAC system from underperformance or damage.

### Automation Capabilities
Unlock advanced automation with Rule Machine in Hubitat, creating rules to automatically control vent positions based on various triggers such as occupancy, time of day, or specific events. Examples include:
- **Room Use Optimization**: Automate vents to close in unoccupied rooms, focusing climate control where it's needed.
- **Schedule-Based Control**: Set vents to adjust based on time-of-day schedules, enhancing comfort and energy efficiency.

To automate room activity within Rule Machine:
1. Navigate to "Set Variable, Mode or File" > "Run Custom Action".
2. Choose a Flair vent device.
3. For the command, select "setRoomActive".
4. For the parameter, input "true" to activate a room or "false" to deactivate.


### Initial Setup
1. **Install Flair Vent Driver**: In Hubitat, navigate to **Drivers Code > New Driver**, paste the contents of `hubitat-flair-vents-driver.groovy`, and save.
2. **Install Flair App**: Access **Apps Code > New App**, copy and paste `hubitat-flair-vents-app.groovy`, click save, and then **Add User App** to install the Flair integration.
3. **Configure API Credentials**: Request and input Flair API credentials (Client ID and Client Secret) within the Hubitat Flair app setup interface.

## Using The Integration
Control and automation are at your fingertips. Each Flair vent appears as an individual device within Hubitat. You can:
- Set the **vent opening level** with `setLevel` (0 for closed, 100 for fully open).
- Manage **room activity** using the `setRoomActive` command to strategically manage airflow based on room usage.

## Diagnostics & Debugging

Access **View Diagnostics** from the app's Debug Options to troubleshoot your setup.

- **Cached Device Data** – shows current vent cache contents. Use **Reset Cache** if data appears stale.
- **Recent Error Logs** – lists the last 20 errors captured when debug mode is enabled.
- **Health Check** – validates API connectivity and counts discovered vents. Press **Run Health Check** for an updated status.
- **Actions** – buttons to re-authenticate or re-sync vents when needed.

### Enable Debug Mode

In the app's settings, choose a **debug level** greater than `0` under Debug Options. Higher levels output more detailed logs and populate the diagnostics page.


### Prerequisites

Gradle builds use a Java toolchain pinned to **JDK 11**. Install a Java 11
runtime before running any Gradle tasks.

Sample installation commands:

```bash
# SDKMAN!
sdk install java 11.0.20-tem

# Debian/Ubuntu
sudo apt-get install openjdk-11-jdk

# macOS (Homebrew)
brew install openjdk@11
```

Running `gradle test` without JDK 11 results in:

```
> Cannot find a Java installation matching {languageVersion=11}
```

### Running Tests

This project includes a comprehensive test suite covering all critical algorithms:

```bash
# Run all tests
gradle test

# Run tests with coverage report
gradle clean test jacocoTestReport

# View test results
open build/reports/tests/test/index.html
open build/reports/jacoco/test/html/index.html
```

### Test Coverage

- **50+ test cases** covering Dynamic Airflow Balancing algorithms
- **Mathematical precision** validation for temperature calculations
- **Edge case testing** for robust error handling
- **Multi-room scenarios** with realistic HVAC data
- **Safety constraint validation** for minimum airflow requirements

See [TESTING.md](TESTING.md) for detailed testing documentation.

### Architecture

The integration features advanced **Dynamic Airflow Balancing (DAB)** algorithms:
- Temperature change rate learning per room
- Predictive vent positioning using exponential models
- Minimum airflow safety constraints
- Rolling average calculations for efficiency optimization

## Support and Community
Dive deeper into documentation, engage with community discussions, and receive support on the [Hubitat community forum thread](https://community.hubitat.com/t/new-control-flair-vents-with-hubitat-free-open-source-app-and-driver/132728).

- `room-active = true` is shown when devices report active status
- DAB Daily Summary: verify daily table shows yesterdays averages after a day of runtime
- If tiles dont update: open Quick Controls and Apply All Changes; or run Create/Sync Tiles
- For deeper logs: set Debug Level > 0; verbose logs are mirrored safely for CI

```
> Cannot find a Java installation matching {languageVersion=11}
```

### Running Tests

This project includes a comprehensive test suite covering all critical algorithms:

```bash
# Run all tests
gradle test

# Run tests with coverage report
gradle clean test jacocoTestReport

# View test results
open build/reports/tests/test/index.html
open build/reports/jacoco/test/html/index.html
```

### Test Coverage

- **50+ test cases** covering Dynamic Airflow Balancing algorithms
- **Mathematical precision** validation for temperature calculations
- **Edge case testing** for robust error handling
- **Multi-room scenarios** with realistic HVAC data
- **Safety constraint validation** for minimum airflow requirements

See [TESTING.md](TESTING.md) for detailed testing documentation.

### Architecture

The integration features advanced **Dynamic Airflow Balancing (DAB)** algorithms:
- Temperature change rate learning per room
- Predictive vent positioning using exponential models
- Minimum airflow safety constraints
- Rolling average calculations for efficiency optimization

## Support and Community
Dive deeper into documentation, engage with community discussions, and receive support on the [Hubitat community forum thread](https://community.hubitat.com/t/new-control-flair-vents-with-hubitat-free-open-source-app-and-driver/132728).

![Flair Vent Device in Hubitat](hubitat-flair-vents-device.png)

## Quick Setup (Hubitat)

- Install drivers src/hubitat-flair-vents-driver.groovy and src/hubitat-flair-vent-tile-driver.groovy.
- Install app src/hubitat-flair-vents-app.groovy and add it via Apps.
- Enter Flair API credentials, wait for green "Authenticated successfully".
- Discover and sync vents.
- Optional: Enable "Use Dynamic Airflow Balancing" to run on duct-temp detection (works without thermostat).
- Optional: Enable "Dashboard tiles" and click "Create/Sync Tiles".

## Quick Controls (In-App)

Inside the app, open ? Quick Controls. You can:
- Set per-room percent (qc_*) and Apply All Changes
- Bulk Open/Close, Manual/Auto for edited vents
- Manual overrides persist until cleared and are respected by DAB
- Active Rooms list is shown when devices report oom-active = true

## Tiles (Battery/Voltage Badge)

Tiles show: room name, bar %, mode, and when available a small badge with Battery % and Voltage (V). If a tile is blank initially, run Create/Sync Tiles again or use Quick Controls ? Apply All Changes.

## Testing Checklist (Before New Features)

- DAB Daily Summary: verify daily table shows yesterday’s averages after a day of runtime
- DAB Chart: verify quickchart link renders hourly series for rooms
- DAB History: check activity log and integrity page for missing hours notices
- Quick Controls: per-room setpoints apply immediately; overrides persist
- Tiles: verify % updates and battery/voltage appear when reported by devices
- Diagnostics: run Health Check to confirm API reachable and vent count

## Troubleshooting

- If you lack a thermostat: enable duct-temp DAB; the app infers heating/cooling from duct vs room temperature delta
- If tiles don’t update: open Quick Controls and Apply All Changes; or run Create/Sync Tiles
- For deeper logs: set Debug Level > 0; verbose logs are mirrored safely for CI
