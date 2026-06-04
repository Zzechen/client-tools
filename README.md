# client-tools

> Give AI eyes and hands on your Android/iOS app.

Embed the SDK in your app to expose an HTTP interface. The MCP Server wraps those endpoints into tools Claude can call directly — inspect UI, fix layout bugs at runtime, compare against designs, and invoke your app's own business logic. No recompile. No simulator workarounds.

**Platforms:** Android · iOS

---

## Features

**UI Inspection**
- Screenshot, view node tree, query node properties
- DOM query (WebView content)

**UI Manipulation**
- Modify layout properties at runtime (position, size, margin, text style, etc.)
- Simulate tap, scroll

**Design Comparison**
- Push an HTML design overlay onto your app screen
- Auto-align and visually diff node by node

**Custom Routes**
- Register any HTTP route in your app and expose it to AI
- Typical uses: navigate to a page, get current user info, query app state, trigger business logic

**WebView Redirect**
- Replace remote URLs loaded in in-app WebViews with local dev addresses
- Supports regex matching and query param forwarding; noop in release builds

**Other**
- Mock: intercept and simulate network requests
- Image overlay: push local images to device for display
- WebView overlay: show/hide HTML layers over the native UI

---

## How It Works

```
Your App (Android / iOS)
  └── SDK  (HTTP :8080 / :8081)
        └── MCP Server
              └── AI (Claude)
```

The SDK runs a local HTTP server inside your app. The MCP Server translates those endpoints into 27 MCP tools. Claude calls the tools; the tools talk to your app.

---

## Use Cases

**Visual QA**
Claude takes a screenshot, overlays your design file, and identifies every pixel-level mismatch. It then calls `modify_view` to fix margins, sizes, and colors — live, without touching your source code.

**Runtime Layout Fix**
You describe a layout bug. Claude inspects the view tree, locates the offending node, and patches the property on the running app. You see the result immediately.

**App Automation via Custom Routes**
Your app registers a `/navigate` route. Claude calls it to jump to the right screen before running a test, fetching state, or verifying a flow — all without Espresso or XCUITest boilerplate.

---

## Installation

### Android SDK (via JitPack)

Add JitPack to `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        // ...
        maven { url = uri("https://jitpack.io") }
    }
}
```

Add dependencies to `app/build.gradle.kts`:

```kotlin
// debug: full SDK, exposes HTTP interface
debugImplementation("com.github.Zzechen:client-tools:v1.1.0")
// release: noop stub, zero runtime overhead
releaseImplementation("com.github.Zzechen:client-tools-noop:v1.1.0")
```

> `client-tools-noop` implements the same interface. No code changes needed between debug and release.

### iOS SDK (via CocoaPods)

```ruby
pod 'ClientToolsSDK', :git => 'https://github.com/Zzechen/client-tools.git', :tag => 'ios/1.1.0'
```

### MCP Server

```bash
cd mcp && npm install && npm run build
# Forward Android device port (run after each adb connect)
adb forward tcp:8080 tcp:8080
```

Add to your Claude Code `.mcp.json`:

```json
{
  "mcpServers": {
    "client-tools": {
      "command": "node",
      "args": ["/path/to/client-tools/mcp/dist/index.js"]
    }
  }
}
```

---

## Quick Start

**App developers** — Integrate the SDK and register custom routes to expose your app's private capabilities to AI.

→ [Integration Guide](docs/integration.md)

**AI / MCP users** — Use MCP tools to control mobile UI and run visual QA.

→ [MCP Tools Reference](docs/mcp-tools.md) (27 tools)

→ [SDK HTTP API](docs/sdk-http-api.md)

---

## Documentation

| Doc | Contents |
|-----|----------|
| [MCP Tools](docs/mcp-tools.md) | All 27 MCP tools with parameters and return values |
| [SDK HTTP API](docs/sdk-http-api.md) | Full HTTP API reference with Android/iOS comparison |
| [Integration Guide](docs/integration.md) | Step-by-step SDK integration |

---

## Repository Structure

```
clients/
  android/sdk/     — Android SDK (.aar)
  android/demo/    — Android integration example
  ios/sdk/         — iOS SDK (CocoaPod)
  ios/demo/        — iOS integration example
mcp/               — MCP Server (TypeScript)
proto/             — Protocol Buffer definitions
docs/              — Documentation
skill/             — client-tools-inspect skill
tests/             — Runtime E2E test scripts
```

---

## License

[MIT](LICENSE)
