# Hubitat Elevation Platform Capabilities - Exhaustive Reference

## Table of Contents
1. [Hub Hardware Generations](#hub-hardware-generations)
2. [C-8 Pro Specifications](#c-8-pro-specifications)
3. [Supported Protocols](#supported-protocols)
4. [Built-in Apps](#built-in-apps)
5. [Dashboards](#dashboards)
6. [Hub Mesh (Multi-Hub Networking)](#hub-mesh)
7. [Modes and Mode Manager](#modes-and-mode-manager)
8. [Hubitat Safety Monitor (HSM)](#hubitat-safety-monitor)
9. [Scenes and Groups](#scenes-and-groups)
10. [Maker API](#maker-api)
11. [Developer Environment](#developer-environment)
12. [Device Capabilities System](#device-capabilities-system)
13. [OAuth Support](#oauth-support)
14. [Cloud and External Integrations](#cloud-and-external-integrations)
15. [Hub Variables](#hub-variables)
16. [Administration and Settings](#administration-and-settings)
17. [Mobile App](#mobile-app)
18. [Community Ecosystem](#community-ecosystem)
19. [Network Architecture](#network-architecture)
20. [Known Limitations and Workarounds](#known-limitations-and-workarounds)
21. [Platform Comparison](#platform-comparison)

---

## 1. Hub Hardware Generations <a name="hub-hardware-generations"></a>

### Overview of Models
Hubitat has released several hardware generations, each improving on the last:

| Model | Processor | RAM | Z-Wave Radio | Zigbee | Notable Features |
|-------|-----------|-----|-------------|--------|-----------------|
| C-3 | Older ARM | Limited | 500 series | Yes | Original model, no longer sold |
| C-5 | ARM | 512MB | 500 series | Yes | Matter compatible (C-5+), still supported |
| C-7 | ARM Cortex-A53 | 1GB | 700 series | Zigbee 3.0 | S2 security, SmartStart |
| C-8 | 1.416 GHz Cortex-A53 | 1GB | 800 series | Zigbee 3.0 | External antennas, Z-Wave LR, Wi-Fi |
| C-8 Pro | 2.015 GHz Cortex-A55 | 2GB | 800 series | Zigbee 3.0 | Fastest CPU, 2x RAM, Bluetooth, Matter 1.5 |

### Key Generation Differences
- **C-5 to C-7**: Upgraded Z-Wave radio from 500 to 700 series, added S2 security and SmartStart support
- **C-7 to C-8**: Z-Wave 800 series radio, Z-Wave Long Range support, external antennas for both Z-Wave and Zigbee, built-in Wi-Fi, improved processor
- **C-8 to C-8 Pro**: Significantly faster processor (2.015 GHz Cortex-A55 vs 1.416 GHz Cortex-A53), doubled RAM (2GB vs 1GB), added Bluetooth radio

### Software Compatibility
All hub models run the same Hubitat Elevation platform software. The same apps, drivers, and automations work across all models. Firmware updates are delivered to all supported models simultaneously.

---

## 2. C-8 Pro Specifications <a name="c-8-pro-specifications"></a>

### Hardware
- **SoC**: ARM Cortex-A55 at 2.015 GHz (likely Amlogic S905X3)
- **RAM**: 2 GB
- **Storage**: 8 GB flash
- **Power**: 5V via USB-C port
- **Dimensions**: 8.2 x 7.5 x 1.7 cm
- **OS**: Linux-based

### Radios and Antennas
- **Z-Wave**: 800 series radio with external antenna
  - US frequency: 908.4 MHz (region-specific variants available)
  - Z-Wave Long Range (ZWLR) support
  - S2 security framework
  - SmartStart support
- **Zigbee**: Zigbee 3.0 radio with external antenna
- **Bluetooth**: Dedicated Bluetooth radio (C-8 Pro exclusive)
  - Supports BTHome v2 devices (e.g., Shelly sensors)
  - Used for hub onboarding
  - Additional Bluetooth formats expected in future updates
- **Wi-Fi**: Built-in (specific standard not publicly documented)

### Connectivity
- **Ethernet**: RJ45 port (recommended for reliability)
- **Wi-Fi**: Built-in wireless networking
- **Important**: Do not use both Wi-Fi and Ethernet simultaneously; connecting Ethernet does not auto-disable Wi-Fi

### What's in the Box
- Hubitat Elevation C-8 Pro Hub
- Region-specific power supply (US: 120V)
- Ethernet cable
- 2 external antennas (pre-attached or included)

### Regional Variants
- North America (US frequency Z-Wave 908.4 MHz)
- Europe (EU frequency Z-Wave 868.42 MHz)
- UK/Ireland
- Australia/New Zealand

---

## 3. Supported Protocols <a name="supported-protocols"></a>

### Z-Wave
- **Z-Wave 800 Series** (C-8/C-8 Pro): Latest generation with improved range and power efficiency
- **Z-Wave Long Range (ZWLR)**:
  - Star topology (hub-and-spoke) instead of mesh
  - Up to ~1.5 miles line of sight range
  - Supports up to 4,000 nodes
  - Reduced latency (no multi-hop routing)
  - Available on C-8 and C-8 Pro models
- **Z-Wave Mesh**: Traditional mesh networking for standard Z-Wave devices
- **S2 Security**: Encryption framework for Z-Wave devices
- **SmartStart**: QR code-based device inclusion
- **Device Inclusion/Exclusion**: Pairing and unpairing via hub UI
- **Z-Wave Details Page**: Shows routing, ghost node detection, firmware info

### Zigbee
- **Zigbee 3.0**: Full support for the Zigbee 3.0 standard
- **Zigbee Mesh**: Self-healing mesh network with repeaters
- **Direct Pairing**: Zigbee devices pair directly to the hub
- **Aqara Support**: Aqara Zigbee devices can pair directly or via Matter bridge (Aqara M3 Hub)

### Matter
- **Matter Controller**: Hubitat can pair and control Matter devices
- **Matter 1.5**: Latest Matter standard support
- **Matter Bridge Support**: Can incorporate devices from Matter bridges (e.g., Aqara Hub M3, Philips Hue Bridge) without custom code
- **NOT a Matter Bridge**: Hubitat does not expose its own devices as Matter devices to other ecosystems
- **Thread**: Hubitat does NOT have a built-in Thread radio; a separate Thread Border Router (TBR) is required for Matter-over-Thread devices
- **Compatibility**: Matter support available on C-5 and newer hub models

### Bluetooth (C-8 Pro Only)
- **BTHome v2**: Supports BTHome v2 standard devices
- **Shelly Devices**: Primary commercial BTHome v2 devices supported
- **Hub Onboarding**: Bluetooth used for initial hub setup
- **Future Expansion**: Additional Bluetooth formats planned

### LAN (Local Network)
- **HTTP/HTTPS**: Communicate with LAN devices via HTTP requests
- **Raw TCP/UDP**: Direct socket communication
- **UPnP/SSDP**: Device discovery protocols
- **Wake-on-LAN (WOL)**: Wake networked devices
- **Telnet**: Used for Lutron integration and other devices
- **Incoming Traffic**: Port 39501 receives unsolicited device messages
- **HubAction**: Object for sending HTTP, TCP, UDP, and other messages

### Cloud Devices
- Cloud-connected device drivers for internet-based services
- Apps can create child devices for cloud-integrated hardware

### Virtual Devices
- Software-only devices for automation logic
- Types include: Virtual Switch, Virtual Dimmer, Virtual Motion Sensor, Virtual Contact Sensor, Virtual Presence Sensor, Virtual Lock, Virtual Thermostat, Scene Activator, and more
- Used for IP-based device control, scene triggers, automation logic, and inter-system integration

---

## 4. Built-in Apps <a name="built-in-apps"></a>

Hubitat includes a comprehensive suite of built-in apps covering automation, device management, integrations, and monitoring. All are installed from Apps > Add Built-In App.

### Automation Apps

#### Rule Machine
The most powerful built-in automation engine:
- Responds to one or more "trigger events" with ordered action sequences
- Conditional actions (if/then/else logic)
- Custom actions for any device command
- Hub variables integration for data sharing between rules
- Delayed actions, repeating actions, wait for events
- Rule 5.1 is the current version
- Can control virtually any device attribute or command

#### Basic Rules
Beginner-friendly automation:
- Simple "When THIS happens, do THAT" logic
- Triggers: time, device events (motion, contact, button), mode changes
- Actions: switch on/off, lock/unlock, notifications, TTS
- Supports restrictions (time, day, mode, device state)
- Multiple actions with delays between them
- Great starting point for new users

#### Simple Automation Rules
(Formerly "Simple Lighting") - Straightforward automations:
- Restrictions: time windows (with sunrise/sunset offsets), days of week, modes, illuminance levels
- Switch-based disable option
- Simpler than Rule Machine but more capable than Basic Rules

#### Room Lighting
Advanced lighting automation:
- Device table for configuring activation settings
- Room Lights Activator child device (works with voice assistants)
- Captures and replays lighting states
- Integration with motion, presence, and other triggers
- Mode-based behavior variations

#### Motion and Mode Lighting
Lighting control based on motion and hub mode:
- Automatic light control when motion is detected
- Different behaviors per mode (day/night/away)
- Configurable timeouts and dimming

#### Zone Motion Controller
Combines multiple motion sensors into zones:
- Creates virtual "zone" motion sensors
- Multiple physical sensors act as one logical zone
- Used with Rule Machine and other apps for zone-based triggers

### Device Management Apps

#### Button Controller (5.1)
Configure button devices for automation:
- Map any number of buttons to device controls
- Supports push, hold, release, and double-tap events
- Smooth dimming with held/released actions
- Works with any button device (remotes, keypads, switches)

#### Groups and Scenes
Organize devices into groups and capture scenes:
- **Groups**: Collection of lights/switches controlled together as one device
- **Scenes**: Capture exact device states and replay them
- Scene Activator virtual device integrates with all other apps
- Works with voice assistants (Alexa, Google)

#### Lock Code Manager
Manage smart lock codes:
- Add and manage users on supported locks
- Set access limits or block access
- Auto-imports existing codes on first run
- Works with locks and keypads

#### Device Firmware Updater
Update device firmware from the hub:
- Supports OTA firmware updates for compatible devices
- Z-Wave and Zigbee device firmware management

#### Preference Manager
Bulk manage device preferences and settings across multiple devices.

### Climate and Scheduling Apps

#### Thermostat Scheduler
Automate thermostat settings:
- Schedule-based adjustments (time, day of week)
- Mode-based thermostat control
- Automatic setpoint and mode changes
- Eliminates manual thermostat adjustments

#### Thermostat Controller
Direct thermostat control and monitoring interface.

### Security and Monitoring

#### Hubitat Safety Monitor (HSM)
See dedicated [HSM section](#hubitat-safety-monitor) below.

#### Notifications
Hub event notification system:
- Push notifications to mobile devices
- Integration with automations and rules
- Alert history in mobile app

### Integration Apps

#### Maker API
See dedicated [Maker API section](#maker-api) below.

#### Lutron Integrator
Integration with Lutron Caseta and RadioRA systems:
- Requires Lutron Smart Bridge Pro (non-Pro not supported)
- Uses Telnet protocol for local, two-way communication
- Supports Pico remotes as button controllers
- Controls Lutron switches, dimmers, and shades

#### Hue Bridge Integration
Connect Philips Hue bridges for local control of Hue devices.

#### Rachio Integration
Integration with Rachio smart sprinkler controllers.

#### Amazon Echo Skill (Alexa)
Expose Hubitat devices to Amazon Alexa for voice control.

#### Google Home Integration
Expose Hubitat devices to Google Home/Assistant.

#### Apple HomeKit Integration
Expose Hubitat devices to Apple HomeKit/Home app.

### Hub Management Apps

#### Mode Manager
See dedicated [Modes section](#modes-and-mode-manager) below.

#### Hub Link (Legacy)
Legacy multi-hub linking (deprecated in favor of Hub Mesh).

#### Link to Hub
Connect to devices shared from another hub.

#### Easy Dashboard
See [Dashboards section](#dashboards) below.

#### Hubitat Dashboard
See [Dashboards section](#dashboards) below.

---

## 5. Dashboards <a name="dashboards"></a>

### Hubitat Dashboard (Standard)
Full-featured, highly customizable dashboard system:

#### Layout and Grid
- Configurable grid with selectable rows and columns
- Adjustable grid gap between tiles
- Custom background images or colors (HTML color values)
- Adjustable corner rounding for tiles
- Font size and icon size configuration

#### Tile Configuration
- 3-dot menu on each tile for editing
- Move tiles via arrow buttons or grid position selection
- Change device, template, or options per tile
- Resize tiles to span multiple grid cells
- Custom colors per device state
- Custom icons per state
- Background color per state/template

#### Display Options
- Cloud and LAN refresh interval settings
- Language customization per dashboard
- Show/hide information toggles
- Custom CSS support for advanced styling

#### Access
- Local LAN access (fastest)
- Cloud access (remote)
- Mobile app integration
- Shareable links

### Easy Dashboard
Simplified dashboard for quick setup:
- Drag-and-drop tile arrangement
- Simply select devices to add them
- Toggle edit mode for modifications
- Grid-based layout
- Less customization than standard Dashboard
- No custom CSS support
- Recommended for beginners or quick setups

### Third-Party Dashboards
- **HD+ (Hubitat Dashboard Plus)**: Enhanced community dashboard with additional features
- **Tile Builder**: Community app for building custom dashboard tiles
- **SharpTools**: External dashboard service with Hubitat integration

---

## 6. Hub Mesh (Multi-Hub Networking) <a name="hub-mesh"></a>

### Overview
Hub Mesh enables seamless device sharing between multiple Hubitat Elevation hubs on the same local network.

### Key Features
- **Device Sharing**: Share any device from one hub to another using "share and link" setup
- **Mode Synchronization**: Optionally sync mode changes across hubs
- **Hub Variable Sharing**: Share hub variables between hubs
- **Multi-Hub Linking**: Multiple hubs can link to devices shared through Hub Mesh
- **Protocol Agnostic**: Works regardless of device protocol (Zigbee, Z-Wave, Matter, LAN, cloud, virtual)

### Technical Details
- **Discovery**: Uses mDNS for hub discovery on the LAN
- **Communication**: TCP over port 8083
- **Minimum Version**: Requires platform version 2.2.4.x or later on all hubs
- **Compatibility**: Only between Hubitat Elevation hubs (not other platforms)

### Use Cases
- Distribute Z-Wave/Zigbee devices across multiple hubs for better mesh coverage
- Separate device types onto dedicated hubs (e.g., Zigbee hub + Z-Wave hub)
- Isolate problematic devices or protocols
- Scale beyond single-hub device limits
- Geographic distribution across large properties

### Hub Link (Deprecated)
Hub Link is no longer available for new installations. Hub Mesh supersedes it with more features, easier configuration, and fewer limitations.

---

## 7. Modes and Mode Manager <a name="modes-and-mode-manager"></a>

### Modes Overview
Modes are a fundamental building block for automation in Hubitat:
- Represent the current state/context of the home (e.g., Day, Night, Away)
- Apps can change behavior based on active mode
- Only one mode active at a time
- Created and managed in Settings > Modes

### Default Modes
Four default modes (all customizable):
1. **Day**
2. **Evening**
3. **Night**
4. **Away**

Users can rename, remove, or create additional custom modes.

### Mode Manager App
Built-in app for automating mode changes via four methods:

1. **Time-Based**: Change mode at specific times, sunrise/sunset (with offsets), or the earlier/later of sunrise/sunset and a specific time
2. **Presence-Based**: Change mode on arrival or departure of presence sensors
3. **Button-Based**: Change mode on push, hold, release, or double-tap of button devices
4. **Switch-Based**: Change mode when switches turn on or off

### Mode Integration
Modes integrate with:
- Rule Machine (conditional logic based on mode)
- Room Lighting (different lighting per mode)
- Thermostat Scheduler (temperature per mode)
- Motion and Mode Lighting
- HSM (arm/disarm based on mode)
- Any custom app that supports mode awareness

---

## 8. Hubitat Safety Monitor (HSM) <a name="hubitat-safety-monitor"></a>

### Overview
HSM is the built-in security and safety monitoring system.

### Armed States
- **Armed-Away**: Full monitoring when no one is home
- **Armed-Home**: Partial monitoring when home (e.g., only doors/windows)
- **Armed-Night**: Night-time monitoring configuration
- **Disarmed**: Monitoring inactive

### Intrusion Detection
- Motion sensor alerts
- Contact sensor alerts (doors/windows)
- Configurable per armed state (different sensors for Away vs Home)
- Option to use all sensors or select specific ones

### Safety Monitoring
- **Water Leak Detection**: Water leak sensor monitoring
- **Smoke Detection**: Smoke detector monitoring
- **Custom Alerts**: Support for acceleration, sound, humidity, temperature sensors

### Alert Actions
- Push notifications
- Text/SMS messages
- Siren activation
- Flashing lights
- TTS (text-to-speech) voice alerts
- Custom rule triggers

### HSM Interface (Developer)
- Programmatic interface for apps and drivers
- Can read HSM state and trigger arm/disarm
- Event subscription for state changes

---

## 9. Scenes and Groups <a name="scenes-and-groups"></a>

### Groups
- Collection of lights or switches controlled together
- Act as a single device for automation purposes
- Group dimming, color, and on/off control
- Reduces complexity in automations

### Scenes
- Capture exact state of multiple devices at a point in time
- Replay captured state to restore devices to those settings
- Scene Activator virtual device created for each scene
- Activator device works with:
  - Simple Automation Rules
  - Motion Lighting
  - Button Controller
  - Rule Machine
  - Hubitat Dashboard
  - Alexa and Google Assistant
  - Any app supporting switch or button devices

---

## 10. Maker API <a name="maker-api"></a>

### Overview
RESTful HTTP API for reading device states and controlling devices. No custom code required on the hub.

### Endpoint URL Structure
- **Local**: `http://[hub_ip]/apps/api/[app_id]/[endpoint]?access_token=[token]`
- **Cloud**: Similar structure via Hubitat cloud relay

### Available Endpoints

#### Device Listing
- `GET /devices` - Returns all authorized devices (id, name, label)
- `GET /devices/all` - Returns all devices with full details

#### Device Information
- `GET /devices/[id]` - Full device info including capabilities, attributes, commands

#### Device Commands
- `GET /devices/[id]/commands` - List available commands for a device
- `GET /devices/[id]/[command]` - Send a command to a device
- `GET /devices/[id]/[command]/[value]` - Send a command with a secondary value

#### Device Events
- `GET /devices/[id]/events` - Recent events for a device

#### Hub Information
- `GET /hub/modes` - List hub modes
- `GET /hub/mode/[id]` - Set hub mode
- `GET /hub/hsm/[command]` - HSM arm/disarm

### Event Streaming
- **HTTP POST**: Configure a URL to receive device events via HTTP POST (webhook-style)
- **EventSocket WebSocket**: Real-time event stream at `ws://[hub_ip]/eventsocket`
  - Receives all device state updates in real time
  - Does not deduplicate events (may receive 2-5 identical events)
  - Very fast and efficient
  - Requires hub login if Hub Login Security is enabled
- **LogSocket WebSocket**: `ws://[hub_ip]/logsocket` for log streaming

### Authentication
- Access token required for all requests
- Token generated during Maker API setup
- Token acts like username/password (keep secure)
- Can reset token via "Create New Access Token" in app settings

### Security
- Select which devices are authorized/exposed via Maker API
- Separate instances can be created with different device sets
- Local-only or cloud-accessible configuration

---

## 11. Developer Environment <a name="developer-environment"></a>

### Language and Runtime
- **Language**: Groovy (version 2.4)
- **Execution**: Sandboxed environment on the hub
- **IDE**: Built-in web-based code editor (Apps Code / Drivers Code)
- **Access**: Developer Tools section in hub sidebar

### App Development
Apps are the means by which users configure automations:
- Built-in apps + user/custom apps
- Apps added via Apps Code page
- Can create child devices
- Support preferences (user inputs), pages, and sections
- Support mappings (URL endpoints) with OAuth

### Driver Development
Drivers enable communication with devices:
- Built-in drivers for common Zigbee, Z-Wave, LAN, cloud devices
- Custom drivers added via Drivers Code page
- Handle device commands and parse incoming messages
- Support capabilities, attributes, and commands

### Common Methods Available to Apps and Drivers

#### Scheduling
- `runIn(seconds, methodName)` - Schedule a method to run after delay
- `schedule(cronExpression, methodName)` - Cron-based scheduling
- `runEvery1Minute()`, `runEvery5Minutes()`, etc.
- `unschedule()` - Remove scheduled tasks

#### HTTP Methods (Synchronous)
- `httpGet()`, `httpPost()`, `httpPut()`, `httpDelete()`

#### HTTP Methods (Asynchronous)
- `asynchttpGet(callback, params)` - Async GET with callback
- `asynchttpPost(callback, params)` - Async POST with callback
- `asynchttpPut()`, `asynchttpDelete()`, `asynchttpPatch()`, `asynchttpHead()`
- Returns control immediately; response handled in callback method

#### WebSocket (Drivers Only)
- Open and maintain WebSocket connections from drivers
- `parse()` method receives incoming messages
- `webSocketStatus()` receives connection status updates
- Cannot open WebSocket from apps (driver only)

#### State and Data Persistence
- `state` object for persisting data between executions
- Works well for small amounts of data
- Available in both apps and drivers

#### Logging
- `log.debug()`, `log.info()`, `log.warn()`, `log.error()`, `log.trace()`
- Minimal performance impact
- Configurable log levels per app/driver

#### Device Communication
- `HubAction` object for HTTP, raw TCP, UDP, WOL, UPnP SSDP messages
- Port 39501 receives unsolicited incoming traffic matched by DNI (IP or MAC)

### LAN and Cloud Driver Architecture
- LAN devices: Communicate via local network protocols
- Cloud devices: Communicate via internet APIs
- Some integrations use app + driver combination
- Apps can create child devices for multi-device integrations
- Documented APIs preferred; local APIs preferred over cloud

### Developer Best Practices
- Use specific types (or void) for variables and methods for speed/memory
- Keep state data small
- Minimize logging in production
- Use async HTTP methods for non-blocking operations
- Test thoroughly before deploying to production hub

### Bundles
Package apps and drivers together for distribution via the Bundles system.

---

## 12. Device Capabilities System <a name="device-capabilities-system"></a>

### Overview
Capabilities are a standardized interface system:
- Each capability defines a set of commands and/or attributes
- Drivers declare which capabilities they implement
- Apps select devices by capability (e.g., "capability.switch")
- Enables standardized app-device interaction

### Capability Structure
Each capability has:
- **Long ID**: Numeric identifier
- **String Name**: Human-readable name
- **Reference**: Used in app inputs (e.g., `capability.refresh`)
- **Attributes**: State values the device reports
- **Commands**: Actions the device can perform

### Common Capabilities (Examples)
- **Switch**: on(), off(); switch attribute (on/off)
- **SwitchLevel**: setLevel(); level attribute (0-100)
- **ColorControl**: setColor(), setHue(), setSaturation()
- **MotionSensor**: motion attribute (active/inactive)
- **ContactSensor**: contact attribute (open/closed)
- **TemperatureMeasurement**: temperature attribute
- **Lock**: lock(), unlock(); lock attribute
- **Thermostat**: setHeatingSetpoint(), setCoolingSetpoint(), etc.
- **Button**: pushed, held, released, doubleTapped attributes
- **PresenceSensor**: presence attribute (present/not present)
- **Refresh**: refresh() command
- **Actuator**: Marker capability for actuating devices
- **Sensor**: Marker capability for sensor devices

### Capability Object API
Programmatic inspection of capabilities:
- List attributes supported by a capability
- Query capability properties
- Used in driver definitions and app device selection

---

## 13. OAuth Support <a name="oauth-support"></a>

### Overview
OAuth is an industry-standard authentication mechanism supported for app-to-app and external service communication.

### Enabling OAuth
1. Navigate to Apps Code in the hub UI
2. Open the app code
3. Click the "OAuth" button in the code editor
4. Enable OAuth for the app
5. Generate an access token

### Access Token
- Created via `createAccessToken()` method in app code
- Automatically sets `state.accessToken`
- Used as query parameter `access_token` in API endpoints
- Required for authenticating all requests to app-defined endpoints

### Mappings
- Apps can define URL endpoint mappings
- Mappings create REST API endpoints on the hub
- Accessible locally and (optionally) via cloud
- OAuth must be enabled for mappings to work

### OAuth Flow (Outbound)
- For integrating with 3rd party APIs
- Redirect user to vendor's OAuth endpoint
- Handle OAuth callback with authorization code
- Exchange code for access token
- Use token for API requests

### Use Cases
- External service integrations
- Custom REST APIs on the hub
- Third-party app authentication
- Dashboard and web app access

---

## 14. Cloud and External Integrations <a name="cloud-and-external-integrations"></a>

### Voice Assistants

#### Amazon Alexa
- Built-in Amazon Echo Skill integration
- Expose selected Hubitat devices to Alexa
- Voice control of lights, switches, locks, thermostats
- Requires internet connection for Alexa commands

#### Google Home
- Built-in Google Home integration
- Expose devices to Google Assistant
- Voice control and routines

#### Apple HomeKit
- Built-in HomeKit integration
- Expose devices to Apple Home app
- Siri voice control
- Local + cloud access via Apple infrastructure

### Platform Integrations

#### IFTTT
- Built-in IFTTT support
- Connect Hubitat with hundreds of IFTTT-supported services
- Device control and mode-based triggers/actions

#### Lutron Caseta / RadioRA
- Built-in Lutron Integrator app
- Requires Lutron Smart Bridge Pro (Telnet support)
- Two-way local communication
- Pico remotes as button controllers
- Shade and dimmer control

#### Philips Hue
- Built-in Hue Bridge Integration
- Local control of Hue devices via Hue Bridge

#### Rachio
- Built-in Rachio Integration
- Smart sprinkler control

#### Ecobee
- Thermostat integration

#### Matter Bridges
- Aqara Hub M3 (Matter Bridge)
- Philips Hue Bridge (Matter Bridge)
- Other Matter bridge devices

### Local Processing vs Cloud
- **All automations run locally** on the hub
- No cloud dependency for core functionality
- Home continues to work during internet outages
- Cloud used only for:
  - Voice assistant commands (Alexa, Google, Siri)
  - Remote access via Hubitat cloud portal
  - Cloud-dependent device integrations
  - IFTTT triggers/actions
  - Remote dashboard access

---

## 15. Hub Variables <a name="hub-variables"></a>

### Overview
Hub variables store values that can be shared across multiple apps and rules.

### Variable Types
- **Number**: Integer (positive or negative)
- **Decimal**: Floating-point number
- **String**: Text (letters, numbers, spaces, etc.)
- **Boolean**: true or false
- **DateTime**: Date, time, or both

### Variable Connectors
- Connectors are virtual devices tied to hub variable values
- Allow apps that don't support direct hub variable access to use variable values
- Direct variable access is now preferred over connectors (when the app supports it)
- Existing connectors continue to work (no need to migrate)

### Use Cases
- Share data between multiple rules or apps
- Store calculated values for reuse
- Create inter-app communication
- Track states not tied to physical devices
- Store configuration values used across automations

### Hub Variable API (Developer)
- Programmatic access to hub variables from apps and drivers
- Read and write variable values
- Subscribe to variable change events

---

## 16. Administration and Settings <a name="administration-and-settings"></a>

### Hub Details
- View firmware/platform version
- Edit hub name
- Set location (latitude/longitude)
- Configure time zone
- Set temperature scale (Fahrenheit/Celsius)

### Network Setup
- Static IP configuration
- DNS settings
- Wi-Fi configuration
- Ethernet settings
- Important: Don't use Wi-Fi and Ethernet simultaneously

### Hub Login Security
- Configure local admin UI authentication
- Username/password protection for hub web interface
- Affects WebSocket connections (requires login for events)

### Backup and Restore
- **Automatic Backups**:
  - Generated on every successful reboot
  - Nightly backup at ~3 AM local time
  - Stored locally on the hub
  - Contains hub database only (settings, apps, devices)
  - Does NOT backup File Manager uploads
- **Manual Backup**: Download full backup of current settings and database
- **Restore**: Upload a backup file to restore hub to previous state
- **Cloud Backup**: Available for off-hub backup storage

### Firmware Updates
- Check for updates: Settings > Check for Updates
- One-click update process
- Hub reboots automatically after update
- Regular updates add features, expand compatibility, support new standards

### File Manager
- Upload local files for use with apps
- Store images, HTML, CSS, JavaScript files
- Files accessible via hub URL
- Not included in automated backups

### Modes (Settings)
- Create, rename, or remove hub modes
- Separate from Mode Manager app (which automates mode changes)

### Hub Variables (Settings)
- Create and manage hub variables and connectors
- See [Hub Variables section](#hub-variables)

### Hub Mesh (Settings)
- Configure device sharing between hubs
- See [Hub Mesh section](#hub-mesh)

### Z-Wave Details
- View Z-Wave network topology
- Identify ghost nodes
- Check device routes
- Firmware information per device
- Repair and refresh options

### Zigbee Details
- View Zigbee network information
- Device listing and status

### Hub Events
- View hub-level events
- System diagnostics
- Performance monitoring

### Device Migration
- Migrate devices between hubs
- Requires backup/restore process
- Z-Wave devices may need re-inclusion on new hub

---

## 17. Mobile App <a name="mobile-app"></a>

### Hubitat Elevation Mobile App
Available for iOS (App Store) and Android (Google Play):

#### Features
- **Dashboard Access**: View and control dashboards
- **Push Notifications**: Receive hub event alerts
- **Notification History**: View past notifications (1-5 recent on Home tab, full list on Notifications tab)
- **Geofencing/Presence**: Use phone as presence sensor

#### Geofence/Presence Detection
- Phone sends presence events (present/not present) based on geofence
- Configurable geofence radius and location
- Enable/disable via More > Geofence > Settings
- Requires background/always-on location permission
- Device appears in hub's Devices list automatically

#### Requirements
- iOS or Android device
- Push notification permission
- Location access permission (for geofencing)
- Internet connection for remote access

---

## 18. Community Ecosystem <a name="community-ecosystem"></a>

### Hubitat Community Forum
- Active community at community.hubitat.com
- Categories: Built-in Apps, Custom Apps, Developers, Integrations, Tips
- Hubitat staff participate actively
- Wealth of user-created drivers, apps, and support

### Hubitat Package Manager (HPM)
Community-driven package management system:
- Install, uninstall, and update third-party packages
- Three installation methods:
  1. Search by Keywords
  2. Browse by Tags
  3. Install from URL
- Automatic update notifications
- Optional component selection
- Modify installed packages
- Developer support for package manifests and repositories

### Popular Community Apps (Examples)
- **Tile Builder**: Custom dashboard tile creation
- **HD+ Dashboard**: Enhanced dashboard experience
- **MQTT Bridge**: MQTT protocol integration
- **webCoRE** (legacy): Complex automation engine
- Hundreds of custom drivers for specific devices

### Custom App/Driver Installation
1. Navigate to Apps Code or Drivers Code
2. Create New App/Driver
3. Paste Groovy code
4. Save
5. Or use HPM for managed installation

---

## 19. Network Architecture <a name="network-architecture"></a>

### Local Processing
- All smart home automation executes locally on the hub
- No cloud dependency for device control or automation
- Sub-second response times for local operations
- Privacy: No data sent to cloud for core functionality

### Network Ports and Protocols
- **Port 80**: Hub web interface (HTTP)
- **Port 8083**: Hub Mesh TCP communication
- **Port 39501**: Incoming LAN device traffic
- **WebSocket**: eventsocket and logsocket for real-time events
- **mDNS**: Hub Mesh discovery
- **TCP/UDP**: Custom LAN device communication
- **HTTPS**: Cloud relay and remote access

### Hub Hardware Architecture
- Linux-based operating system
- Quad-core ARM processor
- Flash storage for firmware and database
- Dedicated radio chips for Z-Wave and Zigbee
- External antennas for improved radio range (C-8/C-8 Pro)

### Recommended Network Setup
- Ethernet connection preferred over Wi-Fi for reliability
- Static IP recommended for consistent access
- Quality router with good DHCP and mDNS support
- Separate IoT VLAN optional but supported

---

## 20. Known Limitations and Workarounds <a name="known-limitations-and-workarounds"></a>

### Platform Limitations
- **Groovy 2.4**: Older Groovy version; no Groovy 3.x/4.x features
- **No Thread Radio**: Requires external Thread Border Router for Thread devices
- **Not a Matter Bridge**: Cannot expose Hubitat devices to other Matter controllers
- **Single Hub Mode**: Only one mode active at a time (no per-room modes natively)
- **WebSocket in Drivers Only**: Cannot open WebSocket connections from apps
- **File Manager Backups**: Uploaded files not included in automatic backups

### Z-Wave Considerations
- **Ghost Nodes**: Force-removing or resetting devices without exclusion creates ghost nodes
  - Check Settings > Z-Wave Details for devices without routes
  - Remove ghosts to maintain mesh health
- **Proper Exclusion**: Always run Z-Wave Exclusion before removing a device
- **Migration Issues**: Z-Wave mesh can degrade after hub migration; may require re-pairing
- **800 Series EU Issues**: Some Z-Wave 800 devices have SDK-related security workarounds needed for EU/UK

### Zigbee Considerations
- Some devices may need specific drivers (built-in or community)
- Zigbee channel selection important to avoid Wi-Fi interference
- Repeater placement affects mesh quality

### Performance Tips
- Use specific types for variables in custom code (speed/memory improvement)
- Keep `state` data small
- Minimize excessive logging in production
- Consider multiple hubs for large device counts
- Reboot hub periodically for optimal performance

### Device Compatibility
- 1,000+ devices from 100+ brands supported
- Some devices require community drivers
- Matter expanding compatibility continuously
- Check Hubitat compatibility list before purchasing devices

---

## 21. Platform Comparison <a name="platform-comparison"></a>

### Hubitat vs SmartThings

| Feature | Hubitat | SmartThings |
|---------|---------|-------------|
| Processing | 100% Local | Primarily Cloud |
| Internet Required | No (for automations) | Yes |
| Speed | Sub-second local | Cloud latency |
| Privacy | Data stays local | Cloud processing |
| Setup Difficulty | Moderate | Easy |
| Customization | Very High (Groovy) | Moderate (Routines) |
| Price | One-time hub cost | One-time hub cost |
| Subscription | None required | None required |
| Company | Hubitat Inc. | Samsung |
| User Base | Power users/enthusiasts | Mass market |

### Hubitat vs Home Assistant

| Feature | Hubitat | Home Assistant |
|---------|---------|---------------|
| Processing | Local | Local |
| Hardware | Purpose-built hub | User-supplied (Pi, NUC, etc.) |
| Setup | Plug-and-play | Manual installation |
| Language | Groovy | Python/YAML |
| Integrations | 1000+ devices, 100+ brands | 2000+ integrations |
| Cost | Hub purchase ($100-150) | Free software + hardware |
| Updates | Automatic OTA | Manual or auto |
| Community | Active forum | Very large community |
| Open Source | No | Yes |
| Z-Wave/Zigbee | Built-in radios | External sticks needed |
| Difficulty | Moderate | Advanced |

### What Makes Hubitat Unique
1. **Truly Local**: Everything runs on the hub - no cloud dependency
2. **Purpose-Built Hardware**: Integrated Z-Wave, Zigbee, (Bluetooth) radios
3. **No Subscription**: Full functionality with no recurring fees
4. **Groovy Customization**: Write custom apps and drivers
5. **Hub Mesh**: Native multi-hub networking
6. **Active Development**: Regular firmware updates with new features
7. **Privacy**: No data leaves your network for core functionality
8. **Reliability**: Works during internet outages
9. **Speed**: Local processing = minimal latency
10. **Matter Controller**: Forward-compatible with Matter standard

---

## Appendix A: Maker API Quick Reference

### Local Endpoints
```
GET  /apps/api/{appId}/devices                          → List all devices
GET  /apps/api/{appId}/devices/all                      → All devices with full details
GET  /apps/api/{appId}/devices/{id}                     → Device info (capabilities, attributes, commands)
GET  /apps/api/{appId}/devices/{id}/commands             → Device commands
GET  /apps/api/{appId}/devices/{id}/events               → Device events
GET  /apps/api/{appId}/devices/{id}/{command}             → Send command
GET  /apps/api/{appId}/devices/{id}/{command}/{value}     → Send command with value
GET  /apps/api/{appId}/hub/modes                          → List modes
GET  /apps/api/{appId}/hub/mode/{id}                      → Set mode
GET  /apps/api/{appId}/hub/hsm/{command}                  → HSM control
```
All endpoints require `?access_token={token}` parameter.

### WebSocket Endpoints
```
ws://{hub_ip}/eventsocket    → Real-time device events
ws://{hub_ip}/logsocket      → Real-time log stream
```

## Appendix B: Developer Method Quick Reference

### Scheduling
```groovy
runIn(seconds, "methodName")
runInMillis(ms, "methodName")
schedule("0 0 12 * * ?", "methodName")  // Cron
runEvery1Minute("methodName")
runEvery5Minutes("methodName")
runEvery10Minutes("methodName")
runEvery15Minutes("methodName")
runEvery30Minutes("methodName")
runEvery1Hour("methodName")
runEvery3Hours("methodName")
unschedule("methodName")
unschedule()  // Remove all
```

### HTTP (Async)
```groovy
asynchttpGet("callbackMethod", [uri: "http://...", ...])
asynchttpPost("callbackMethod", [uri: "http://...", body: "...", ...])
asynchttpPut(callback, params)
asynchttpDelete(callback, params)
asynchttpPatch(callback, params)
asynchttpHead(callback, params)
```

### Logging
```groovy
log.debug "message"
log.info "message"
log.warn "message"
log.error "message"
log.trace "message"
```

### Device Communication
```groovy
def action = new hubitat.device.HubAction(/* HTTP/TCP/UDP params */)
sendHubCommand(action)
```

## Appendix C: Hub Network Ports Summary

| Port | Protocol | Purpose |
|------|----------|---------|
| 80 | HTTP | Hub web interface |
| 443 | HTTPS | Cloud relay / remote access |
| 8083 | TCP | Hub Mesh communication |
| 39501 | TCP | Incoming LAN device traffic |
| — | WebSocket | eventsocket, logsocket |
| — | mDNS | Hub Mesh discovery |
| — | Various | Z-Wave, Zigbee, BLE (radio) |
