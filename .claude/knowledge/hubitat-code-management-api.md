# Hubitat Hub Code Management via HTTP API

The Hubitat Elevation hub exposes an internal HTTP API (same endpoints the browser UI calls) for managing app and driver code. These are NOT part of the Maker API — they're on the hub's main web interface (port 80/443).

## Base URL Pattern

All endpoints use the hub's base URL directly (NOT the Maker API /apps/api/{id} path):

```
http://{HUB_IP}/app/ajax/code
http://{HUB_IP}/driver/ajax/code
```

No access token needed — these endpoints use the hub's session auth (cookie-based). For programmatic access:
1. Hit them without auth (works on local network if hub has no login password set)
2. Pass a session cookie from a prior login POST to /login

## Endpoints for App Code Management

| Operation | Method | Endpoint | Body/Params |
|-----------|--------|----------|-------------|
| List all apps | GET | /app/list or /app/ajax/code?action=list | Returns HTML or JSON |
| Get app source | GET | /app/ajax/code?id={appId} | Returns JSON with source, id, name, status |
| Create new app | POST | /app/ajax/code | id= (empty), version=1, source={groovyCode} |
| Update existing app | POST | /app/ajax/code | id={appId}, version={ver}, source={groovyCode} |
| Import from URL | POST | /app/ajax/code | id= (empty or existing), source= (empty), importUrl={rawGitHubUrl} |

## Endpoints for Driver Code Management

Identical pattern, swap /app/ for /driver/:

| Operation | Method | Endpoint | Body/Params |
|-----------|--------|----------|-------------|
| Get driver source | GET | /driver/ajax/code?id={driverId} | Returns JSON with source |
| Create new driver | POST | /driver/ajax/code | id=, version=1, source={groovyCode} |
| Update existing driver | POST | /driver/ajax/code | id={driverId}, version={ver}, source={groovyCode} |
| Import from URL | POST | /driver/ajax/code | importUrl={rawGitHubUrl} |

## Library Support

Libraries (shared Groovy code) follow the same pattern:
- GET /library/ajax/code?id={libId}
- POST /library/ajax/code with id=, version=, source=

## Key Details

**Content-Type**: All POSTs use `application/x-www-form-urlencoded`, not JSON.

**Response format**: The ajax endpoints return JSON:
```json
{
  "id": 123,
  "version": 2,
  "source": "// groovy code...",
  "name": "My App",
  "namespace": "com.example",
  "status": "compiled"  // or "error" with "errorMessage"
}
```

**Compilation is automatic** — when you POST source code, the hub compiles it immediately. The response tells you if it compiled successfully or had errors.

**Version field** — The hub uses optimistic locking. You must send the current version number when updating. If someone else updated it, the POST fails. Get the current version from the GET response first.

**Import from URL** — The hub fetches the raw Groovy file from the URL, compiles it, and saves it. The URL must return raw .groovy source (e.g., a GitHub raw URL).

## Authentication for Programmatic Access

If the hub has a login password:
```
POST http://{HUB_IP}/login
Content-Type: application/x-www-form-urlencoded
Body: username={user}&password={pass}
```
→ Response sets a session cookie → Pass that cookie on all subsequent requests

If no password is set (common on local-only hubs), all endpoints are open on the LAN.

## HPM (Hubitat Package Manager) Alternative

HPM uses a packageManifest.json format with packageUrl pointing to a JSON manifest that lists apps, drivers, and libraries with their GitHub raw URLs. HPM handles install/update/uninstall through its own app on the hub. The ajax endpoints above are the low-level primitives HPM itself uses under the hood.
