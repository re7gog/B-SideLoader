# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

B-SideLoader is an Android app (Kotlin + Jetpack Compose) that downloads, installs, and auto-updates APKs sourced from **GitHub releases** and **Telegram channels** — an Obtainium-like app store built with Compose. Targets Android 8.0+ (minSdk 26). More sources may be added later

## Build & Run

Uses the Gradle wrapper (`./gradlew` / `gradlew.bat`) with a version catalog at `gradle/libs.versions.toml` — add/upgrade dependencies there, referenced as `libs.*` aliases in build files.

```bash
./gradlew assembleDebug          # build debug APK (per-ABI splits + universal)
./gradlew installDebug           # build + install on connected device/emulator
./gradlew :app:testDebugUnitTest              # JVM unit tests
./gradlew :app:connectedDebugAndroidTest      # instrumented tests (needs device)
./gradlew :app:testDebugUnitTest --tests "dev.re7gog.b_sideloader.SomeTest"   # single test
./gradlew lint                   # Android lint
```

Two modules: `:app` (the application) and `:tdlib` (Telegram native library wrapper, see below).

### Required secrets (Telegram build)

The app will not build a working Telegram feature without a Telegram API id/hash from https://my.telegram.org/apps. These are obfuscated at native-compile time. Provide them via `local.properties` (or env vars in CI) — they are read by `tdlib/build.gradle.kts` `getSecret()` and passed as CMake/cpp flags:

- `ID_SECRET`, `MASK_SECRET`, `HASH_SECRET` — used by `tdlib/src/main/cpp/native-lib.cpp` to reconstruct the api id/hash at runtime, exposed via `org.drinkless.tdlib.Secrets.getApiId()/getApiHash()` (JNI). Never hardcode real credentials in source.

`local.properties` also holds `sdk.dir` and is git-ignored. IDE tip (from README): set `idea.max.intellisense.filesize=5000` in `idea.properties` — `TdApi.java` is enormous.

## Architecture

MVVM (Google's Modern Android Development flavor) with Hilt DI throughout. Package root: `dev.re7gog.b_sideloader`. Layered:

- **`ui/`** — Compose screens grouped by feature under `ui/features/<feature>/` (apps_list, search_app, app_details, settings, telegram_login). Each feature typically has `*Screen.kt` (Composable), `*ViewModel.kt` (`@HiltViewModel`), and `*UiState.kt`. `MainActivity` hosts a single `NavHost`; `ui/navigation/AppDestinations.kt` defines type-safe `@Serializable` routes and the top-level nav items.
- **`domain/`** — interfaces and models decoupling UI from data: `repository/AppsRepository`, `logic/IInstallManager`, and `model/` (notably the `AppType` sealed class: `GithubApp` vs `TelegramApp`).
- **`data/`** — implementations, organized by concern:
  - `di/` — Hilt `@Module`s (`SingletonComponent`) wiring database, network, installer, telegram.
  - `local/` — Room DB (`AppsDatabase`, `dao/`, `entities/`). One `AppEntity` "apps" table + separate `GithubDetailsEntity`/`TelegramDetailsEntity`, joined via `@Relation` in `domain/model/AppWithDetails`. **Schema version is 1, `exportSchema = false`, no migrations yet**.
  - `remote/` — Retrofit `GithubApi` + DTOs, OkHttp (see `NetworkModule`).
  - `telegram/` — `TelegramManager` (`@Singleton`) wraps the TDLib JNI `Client`, exposing auth state / chats / file updates as coroutine `Flow`s.
  - `installer/`, `updater/`, `background/`, `settings/`, `encrypt/` — see below.

### Key cross-cutting flows

- **Installer strategy** (`data/installer/`): `InstallManager` implements `IInstallManager` and picks an installer at call time based on the `useShizuku` setting — `ShizukuInstaller` (privileged: Shizuku/Sui/Root/Dhizuku via `hidden-api-bypass` + `refine`) or `SessionInstaller` (standard `PackageInstaller` session, user-confirmed). Install/uninstall results arrive via the `InstallReceiver`/`UninstallReceiver` broadcast receivers (custom actions declared in the manifest) routed through `InstallEventManager`. Download+install progress is surfaced as `Flow<Float>`.
- **Auto-update** (`data/background/` + `data/updater/`): `BSideApplication` (`@HiltAndroidApp`, also the `WorkManager` `Configuration.Provider`) enqueues a periodic (6h) `UpdateCheckWorker` (`@HiltWorker`) gated on settings (autoupdate on, metered-network preference). The worker calls `UpdatesManager.checkAllUpdates()`, which iterates DB apps, checks GitHub/Telegram for newer versions, and either installs silently (when privileged) or posts a notification via `NotificationHelper` channels.
- **Encryption** (`data/encrypt/`): private data (TDLib DB key, GitHub token) is AES-256-GCM encrypted with a master key held in the Android hardware Keystore. `EncryptionManager` does the crypto; `SecureStorage` persists ciphertext+IV in `SharedPreferences`. Do not log or persist plaintext secrets.
- **Settings** (`data/settings/SettingsManager`): DataStore Preferences, exposed as `Flow`s (e.g. `useShizuku`, `useAutoupdates`, `useMobileData`, `useDynamicColor`).

### TDLib module (`:tdlib`)

Prebuilt TDLib native libraries stripped from TelegramX live in `tdlib/src/main/libs/<abi>/` (`libtdjni.so`, `libsslx.so`, `libcryptox.so`). Java bindings are `org.drinkless.tdlib.Client` / `TdApi` (`TdApi.java` is generated and huge). `TelegramManager` loads `tdjni` and drives async requests through TDLib's update callback, adapting them to Kotlin `Flow`/`suspend`. The separate `native-lib` (CMake, `native-lib.cpp`) exists only to hold the obfuscated API secrets.

## Conventions

- Kotlin official code style (`kotlin.code.style=official`), non-transitive R classes, Gradle configuration cache enabled.
- New DI bindings go in a `data/di/*Module.kt`; ViewModels are constructor-injected `@HiltViewModel`.
- Cross-layer async is coroutine `Flow`-based; managers own their own `CoroutineScope(Dispatchers.X + SupervisorJob())`.
- Navigation routes are `@Serializable` classes/objects in `ui/navigation/` — add a route there and a `composable<Route>` block in `MainActivity`, don't use string routes.