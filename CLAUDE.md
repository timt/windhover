# Windhover

Android app giving real-time 3D wind and a gust-free vario from LXNAV IAS/pressure altitude plus phone sensors. Goals, phases and risks are in `docs/private/project-brief.md` (local only, gitignored — never commit it).

## Working practices
- Kotlin, `kotlin-developer` skill + XP/TDD workflow
- Tickets: Linear, team **Gliding**, project **Windhover** (`GLI-*`). Reference the ticket in commits.
- GPL-3.0, public repo `timt/windhover`

## Layout
- Single module `app`, package `io.github.timt.windhover`
- `core` package: pure Kotlin, no `android.*` imports — unit-tested on the JVM
- `ui` package: Jetpack Compose
- No NDK until Phase 4 (C++ for Larus, or Rust for a clean-room rewrite)

## Commands
```bash
./gradlew testDebugUnitTest   # unit tests (JUnit Jupiter + Strikt)
./gradlew ktlintCheck         # style
./gradlew ktlintFormat        # auto-fix style
./gradlew assembleDebug       # build APK
./gradlew installDebug        # install on connected phone
```

## Constraints
- Never send commands (e.g. `PLXV0`) to an LXNAV unit unless the ticket explicitly calls for it — read-only by default
- Timestamp all data with `SystemClock.elapsedRealtimeNanos()`
