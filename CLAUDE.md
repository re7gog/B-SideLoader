# CLAUDE.md

Guidance for Claude Code (claude.ai/code) when working in this repository.

## Project

B-SideLoader is an Android app (Kotlin + Jetpack Compose) that finds, installs and auto-updates
APKs published on **GitHub releases** and in **Telegram channels** — an Obtainium-like app store.
It can also install a local APK. Targets Android 8.0+ (`minSdk 26`), `compileSdk`/`targetSdk` 37.
More sources may be added later; the architecture is built for that (see *Adding a source*).

## Build & run

Gradle wrapper (`./gradlew` / `gradlew.bat`), version catalog at `gradle/libs.versions.toml` — add
or bump dependencies **there**, referenced as `libs.*` aliases.

```bash
./gradlew assembleDebug                       # debug APK (per-ABI splits + universal)
./gradlew installDebug                        # build + install on a connected device
./gradlew :app:testDebugUnitTest              # JVM unit tests (fast, no device)
./gradlew :app:connectedDebugAndroidTest      # instrumented tests (needs a device)
./gradlew :app:assembleRelease                # runs R8; the only way to catch a missing keep rule
./gradlew lint
```

Two modules: `:app` and `:tdlib` (Telegram native wrapper — see below).

### Required secrets

The Telegram feature needs an API id/hash from <https://my.telegram.org/apps>. They are obfuscated
at native-compile time. Put them in `local.properties` (or supply them as env vars in CI):

- `ID_SECRET`, `MASK_SECRET`, `HASH_SECRET` — read by `tdlib/build.gradle.kts` `getSecret()` and
  passed as CMake/cpp flags to `tdlib/src/main/cpp/native-lib.cpp`, which reconstructs the values
  at runtime and exposes them through `org.drinkless.tdlib.Secrets`. Never hardcode them in source.

`local.properties` also holds `sdk.dir` and is git-ignored. IDE tip: set
`idea.max.intellisense.filesize=5000` in `idea.properties` — `TdApi.java` is ~4.8 MB.

## Architecture

Clean-ish layering inside a single module, with Hilt DI throughout. Package root
`dev.re7gog.b_sideloader`. **Dependencies point inwards**: `ui` -> `domain` <- `data`. The domain
layer has no Android, Room, Retrofit or TDLib imports; check that before adding one.

```
core/       coroutines/  DispatcherProvider, cancellation-safe runCatching
            log/         Logger seam (debug-only chatter compiled behind a lambda)

domain/     model/       TrackedApp, AppSource, UpdateCandidate, InstallProgress, AppSettings...
            error/       AppError - the closed hierarchy every failure maps to
            repository/  AppsRepository, GithubRepository, TelegramRepository, Settings/Secrets
            installer/   InstallerGateway, PackageInspector, ApkStagingArea
            background/  BackgroundWorkScheduler, BackgroundRestrictions, BackgroundHealth
            device/      DeviceInfo
            selection/   NameMatcher, AbiMatcher, Github/TelegramApkSelector  (pure, unit-tested)
            usecase/     ObserveTrackedApps, ResolveUpdate, InstallApp, RunUpdateSweep, ...

data/       local/       Room database, DAO, entities  (+ exported schemas in app/schemas)
            remote/      Retrofit GithubApi, DTOs, mappers, OkHttp interceptors
            telegram/    TdlibClient (JNI -> coroutines) + TelegramRepositoryImpl + mappers
            installer/   session/ and privileged/ backends, event bus, staging, gateway
            background/  WorkManager scheduler, worker, monitor service, notifications, OEM quirks
            settings/    DataStore-backed settings
            encrypt/     Keystore AES-GCM + SecureSecretsRepository
            mapper/      entity <-> domain
            error/       Throwable -> AppError
            device/      AndroidDeviceInfo
            di/          Hilt modules (+ src/debug for debug-only bindings)

ui/         BSideLoaderApp.kt      navigation-suite shell + Nav3 entryProvider
            navigation/            NavKeys, NavigationState, Navigator
            common/                component/ text/ error/ permission/ util/  (shared widgets)
            feature/<name>/        Screen + ViewModel + UiState per feature
            theme/
```

### Rules that keep the layering honest

1. **The domain owns the models.** Room entities, DTOs and `TdApi` types never leave `data`; each
   has a mapper in `data/mapper` or `data/*/mapper`. UI-shaped state lives with its feature.
2. **Failures are values of one type.** Data-layer code translates its exceptions to `AppError`
   (`data/error/ThrowableToAppError.kt`, `apiCall { }`); the UI turns an `AppError` into text in
   `ui/common/error/AppErrorText.kt`, which is exhaustive — add a case there when you add one to
   `AppError`.
3. **Cancellation is never swallowed.** Use `runCatchingCancellable` / `suspendRunCatching` from
   `core/coroutines`, or rethrow via `Throwable.rethrowIfCancellation()`. A bare
   `catch (e: Exception)` around suspending code is a bug.
4. **No `Dispatchers.X` outside `DefaultDispatcherProvider`.** Inject `DispatcherProvider`.
5. **No `Context` in a ViewModel.** Produce `UiText` and resolve it in the composable.
6. **UI state is immutable.** `@Immutable data class` + `ImmutableList` (kotlinx-collections-
   immutable) so Compose can skip recomposition.
7. **Selection logic is pure.** Anything deciding *which* APK wins goes in `domain/selection`, so
   the details-screen preview and the background sweep run the exact same code.

### Key flows

- **Update resolution.** `ResolveUpdateUseCase` asks the source repository for raw releases or
  messages and hands them to the matching `domain/selection` selector, which applies the app's
  filters and then `AbiMatcher`. `UpdateCheck.status` compares the winner with what is installed.
- **Install.** `InstallAppUseCase` streams `InstallerGateway.install(DownloadRef)` and persists the
  app on success — install and database write are one operation, so nothing has to be correlated
  afterwards. `InstallerGatewayImpl` picks a backend per call from the current settings:
  `SessionApkInstaller` (standard `PackageInstaller`, user-confirmed) or `PrivilegedApkInstaller`
  (Shizuku/Sui/Dhizuku via `hidden-api-bypass` + `refine`). Results are matched by request id
  through `InstallEventBus`; sessions are abandoned on failure *and* on cancellation.
- **Background updates.** `SyncBackgroundWorkUseCase` reconciles `WorkManagerBackgroundScheduler`
  with the settings on app start, on boot (`BootReceiver`) and after every relevant toggle.
  `BackgroundMode.Periodic` uses `UpdateCheckWorker`; `Persistent` uses `UpdateMonitorService`
  (a `specialUse` foreground service — `dataSync` is capped at ~6 h/day on Android 14+).
  `RunUpdateSweepUseCase` isolates per-app failures but always propagates cancellation.
- **OEM background limits.** `AndroidBackgroundRestrictions` detects the ROM vendor and resolves
  *only that vendor's* autostart activities, verifying each exists before launching it. The
  "Background reliability" settings screen turns that into a checklist with per-ROM instructions,
  because no autostart allowlist can be read or requested through an API.
- **Secrets.** `EncryptionManager` (AES-256-GCM, hardware Keystore) + `SecureSecretsRepository`
  hold the TDLib database key and the GitHub token. The token is also exposed synchronously via
  `AuthTokenSource` so `GithubAuthInterceptor` can attach it without blocking the OkHttp thread.

### Navigation 3

There is no `NavController`. The back stack is app state:

- `ui/navigation/NavKeys.kt` — `@Serializable ... : NavKey` destinations; arguments are properties.
- `ui/navigation/NavigationState.kt` — one `NavBackStack` per top-level destination plus which tab
  is showing; converts to `NavEntry`s with a `SaveableStateHolder` **and** a `ViewModelStore`
  decorator per stack (the latter is what scopes `hiltViewModel` to an entry).
- `ui/navigation/Navigator.kt` — the only thing allowed to mutate that state; encodes "exit through
  home" and the post-install jump back to the apps list.
- `ui/BSideLoaderApp.kt` — one `entryProvider { }` wiring every destination to its screen.

Screens receive lambdas, never the navigator. A ViewModel that needs a nav argument takes it via
assisted injection: `@HiltViewModel(assistedFactory = ...)` plus
`hiltViewModel<VM, VM.Factory>(creationCallback = { it.create(args) })` — see `AppDetailsViewModel`.

### R8 / keep rules

AGP 9 source-set convention: rules live in `app/src/main/keepRules/` (any `.keep` file) and are
picked up automatically — there is no `proguardFiles` entry, and `keepRules.includeDefault` already
pulls in `proguard-android-optimize.txt`. `:tdlib` publishes `consumer-rules.keep` so the app's R8
pass knows the JNI surface must survive; `:tdlib` itself is not minified (the app minifies
everything once). Note `android.r8.strictFullModeForKeepRules` is on by default: `-keep class A` no
longer implies keeping `A`'s default constructor.

Verify keep-rule changes with `./gradlew :app:assembleRelease`, then check
`app/build/outputs/mapping/release/configuration.txt` (which rules reached R8) and `mapping.txt`
(what survived).

### `:tdlib` module

Prebuilt TDLib native libraries stripped from Telegram X live in `tdlib/src/main/libs/<abi>/`
(`libtdjni.so`, `libsslx.so`, `libcryptox.so`). Java bindings are `org.drinkless.tdlib.Client` and
`TdApi` (generated, enormous). `TdlibClient` owns the native client and adapts it to coroutines;
`TelegramRepositoryImpl` maps to domain models. The separate CMake `native-lib` exists only to hold
the obfuscated API secrets.

## Testing

`./gradlew :app:testDebugUnitTest` — 91 JVM tests covering selection logic, mappers, error
translation, use cases, ViewModels and the navigation state machine. Fakes (not mocks) live in
`app/src/test/java/.../testing/`.

`./gradlew :app:connectedDebugAndroidTest` — Room DAO tests against real SQLite, plus Compose UI
tests. **Robolectric is deliberately not used**; see `docs/testing.md` for the toolchain reason and
for how to re-enable it.

## Conventions

- Kotlin official style (`kotlin.code.style=official`), non-transitive R classes, Gradle
  configuration cache on.
- New DI bindings go in `data/di/*Module.kt`; debug-only bindings in `app/src/debug/.../di/`.
- ViewModels are constructor-injected `@HiltViewModel`; screens have a stateless overload taking a
  UI state and callbacks, so they can be tested and previewed without a ViewModel.
- Room schema is exported to `app/schemas`. Changing it means bumping `AppsDatabase.DB_VERSION`,
  adding a `Migration` to `AppsDatabase.MIGRATIONS`, committing the new JSON, and adding a
  migration test. There is **no** destructive fallback.
- **Kotlin block comments nest.** A `/*` sequence inside a KDoc (for example writing a glob such as
  `src/main/` followed by a double star) silently swallows the rest of the file. Avoid it.

## Adding a source

1. Add a variant to `AppSource` (and `AppSourceKind.storedValue`) in `domain/model/TrackedApp.kt`.
2. Add a repository interface in `domain/repository/SourceRepositories.kt` and its selector in
   `domain/selection/`.
3. Implement the repository in `data/`, with DTOs and a mapper; bind it in `RepositoryModule`.
4. Add branches in `ResolveUpdateUseCase` / `ListUpdateCandidatesUseCase` and in the Room mapper.
5. Add a `SearchSource` entry with its branch in `SearchScreen`, plus a `NavKey` and an
   `AppDetailsArgs` variant.

Nothing else in the UI has to change.
