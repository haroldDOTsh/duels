# template

This template is for easy creation of Fulcrum modules. It ships the baseline server configuration (for testing), module metadata, and the Fulcrum bootstrap for you, and lets you start module development without working on boilerplate.

### Info

- Minecraft Version: `1.21.8`
- Protocol Version: `772`
- Toolchain: Java 21, Gradle 8.14, Paper API + Fulcrum runtime 3.0.x

## Quick Start
> [!WARNING]
> FastAsyncWorldEdit (FAWE) is required—the runtime relies on it for arena/world orchestration. Keep the FAWE plugin jar under `run/plugins/`!

> [!TIP]
> `updateFulcrumRuntime` hits the public GitHub API. Export `GITHUB_TOKEN` if you start bumping API limits; the helper already respects the token and takes care of headers.

Fulcrum runtime jars and the template plugin all build through Gradle.

```bash
# compile the plugin and run unit tests
./gradlew clean build

# refresh the Fulcrum runtime jar in run/plugins
./gradlew updateFulcrumRuntime

# boot the bundled Paper server with Fulcrum + this plugin
./gradlew runServer
```

## Environment Notes

- `run/ENVIRONMENT` picks the active environment key (`dev`, `staging`, etc.). The Fulcrum runtime reads it on boot.
- `run/environment.yml` maps each environment to the modules that should load. Add your module name under the matching key when you want it to activate automatically.

That’s it! ship your module, keep the runtime current, and iterate fast without fighting setup each time.
