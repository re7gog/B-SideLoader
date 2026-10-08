# CLAUDE.md

Guidance for Claude Code (claude.ai/code) when working in this repository.

## Project

B-SideLoader is an Android app (Kotlin + Jetpack Compose) that finds, installs and auto-updates
APKs published on **GitHub releases** and in **Telegram channels** — an Obtainium-like app store.
It can also install a local APK. Targets Android 8.0+ (`minSdk 26`), `compileSdk`/`targetSdk` 37.
More sources may be added later; the architecture is built for that (see *Adding a source*).

**Code search goes through `ast-index` first** — the Grep tool, `grep -r`/`rg` and subagents
included. `.claude/rules/ast-index.md` has the commands and the exceptions; a PreToolUse hook
(`.claude/hooks/ast-index-gate.sh`) bounces the first symbol grep of a session.

## Build & run

Gradle wrapper (`./gradlew` / `gradlew.bat`), version catalog at `gradle/libs.versions.toml` — add
or bump dependencies **there**, referenced as `libs.*` aliases.

```bash
./gradlew assembleDebug                       # debug APK (per-ABI splits + universal)
./gradlew :app:assembleDev                    # release candidate as dev.re7gog.b_sideloader.dev
./gradlew :app:testDebugUnitTest              # every automated test: JVM + Robolectric, no device
./gradlew :app:assembleRelease                # runs R8; needs the release keystore env vars
./gradlew :app:minifyReleaseWithR8            # the release R8 pass without signing (no keystore)
./gradlew lint                                # every check on, every warning fatal — must pass
./gradlew installDebug                        # the user's command, see below — never run it
```

Two modules: `:app` and `:tdlib` (Telegram native wrapper — see below).

### Build types

| | `debug` | `dev` | `release` |
|---|---|---|---|
| Package | `dev.re7gog.b_sideloader` | `dev.re7gog.b_sideloader.dev` | `dev.re7gog.b_sideloader` |
| R8 / shrinking | off | on | on |
| LeakCanary, HTTP logging (`src/debug`) | yes | no | no |
| Signed with | debug key | debug key | release keystore (CI) |
| Tracks its own releases (`TRACKS_ITSELF`) | yes | no | yes |

`dev` is `release` with a package suffix (`initWith`): it installs **next to** the published app,
with its own data, so the developer can try what is about to ship — AI features included — on
their own phone. It is labelled "B-SideLoader Dev" with an amber icon (`app/src/dev/res`). It
never claims a release tag and seeds no self row (`SelfAppSeed`): the published releases are the
release package, so they cannot update it. The suffix is safe because the Shizuku authority is
`${applicationId}.shizuku` and the install result `PendingIntent`s name their receiver explicitly.

### Lint

`lint` blocks in both modules turn on every check (`checkAllWarnings`, test sources included,
plus Slack's Compose rules via `lintChecks`) and fail on any warning (`warningsAsErrors`). There
is no baseline. CI runs `./gradlew lint` before it builds a release. Project-wide exceptions live
in the root `lint.xml`, each with its reason; one-off ones go next to the code (`@Suppress("Id")`,
`tools:ignore="Id"`). A new finding is fixed, not suppressed, unless it is a false positive —
and then the suppression says why.

### Devices, the emulator and manual testing

**Never use an Android emulator — do not create, start or run anything on one — and never
install or run the app on a device yourself.** The developer tests the final result by hand, on
their own physical phone. `installDebug`, `connectedDebugAndroidTest`, `adb` and all emulator/AVD
tooling are theirs to run, not yours.

Verify changes with what runs on this machine: `assembleDebug`, `assembleRelease`,
`testDebugUnitTest` (which includes the Robolectric suites) and `lint`. When a change can only be
confirmed on real hardware — install/update/uninstall flows, Shizuku/Dhizuku, OEM background
behaviour, notifications, LeakCanary reports — finish by telling the user exactly what to check
by hand, rather than trying to check it yourself.

### LeakCanary

Debug builds include LeakCanary (`debugImplementation` in `app/build.gradle.kts`). It installs
itself through its own ContentProvider — there is no setup code — and adds a separate "Leaks"
launcher icon on the phone. Release builds do not contain it; confirm with
`./gradlew :app:dependencies --configuration releaseRuntimeClasspath` if a dependency change could
have leaked it in. Leak reports only appear on the user's device, so ask them for the trace.

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
            selection/   NameMatcher, AbiMatcher, Github/TelegramApkSelector, TargetSelector
                         (pure, unit-tested)
            ai/          LanguageModelGateway - the one port to Gemini Nano or a cloud model
            suggestion/  ExampleFilterDeriver, ProposalVerifier, FilterPrompt (pure)
            usecase/     ObserveTrackedApps, ResolveUpdate, InstallApp, RunUpdateSweep, ...

data/       local/       Room database, DAO, entities  (+ exported schemas in app/schemas)
            remote/      Retrofit GithubApi, DTOs, mappers, OkHttp interceptors
            telegram/    TdlibClient (JNI -> coroutines) + TelegramRepositoryImpl + mappers
            installer/   session/ and privileged/ backends, event bus, staging, gateway
            background/  WorkManager scheduler, worker, monitor service, notifications, OEM quirks
            settings/    DataStore-backed settings
            encrypt/     Keystore AES-GCM + SecureSecretsRepository
            ai/          Gemini Nano (ML Kit Prompt API), OpenAI/Anthropic/Gemini over OkHttp
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
  messages and hands them to the matching `domain/selection` selector. Its `groups` applies the
  app's filters and yields one `CandidateGroup` per release or Telegram message (an album is one
  group, one version) with every APK in it, each flagged by the APK filter; `TargetSelector` then
  picks the newest group with a file `AbiMatcher` says runs here. The details page lists those
  groups (`ListUpdateCandidatesUseCase`) and calls the same `TargetSelector`, so the file it
  highlights is the one an update installs. `UpdateCheck.status` compares the winner with what is
  installed.
- **Install.** `InstallAppUseCase` downloads (`InstallerGateway.download`), installs
  (`installDownloaded`), discards the file and persists the app on success — install and database
  write are one operation, so nothing has to be correlated afterwards. The two phases are separate
  so that downloads can overlap while installs cannot: `InstallScheduler` (domain, `@Singleton`)
  gives each source its own download slots — 1, or `MAX_PARALLEL_DOWNLOADS_PER_SOURCE` with
  `AppSettings.parallelUpdates` on, so GitHub and Telegram always download side by side — and lets
  one install run at a time, B-SideLoader's own last (it kills the process). Manual installs take
  the same install slot. HTTP downloads land in `cacheDir/downloads` (`HttpApkSource`), Telegram
  ones in TDLib's store. `InstallerGatewayImpl` picks a backend per call from the current settings:
  `SessionApkInstaller` (standard `PackageInstaller`, user-confirmed) or `PrivilegedApkInstaller`
  (Shizuku/Sui/Dhizuku via `hidden-api-bypass` + `refine`). Results are matched by request id
  through `InstallEventBus`; sessions are abandoned on failure *and* on cancellation.
  **Pre-approval** (Android 14): for installs the user started in the app (`interactive`), the
  use case asks `InstallerGateway.openPreapprovalSession` for a session *before* the download and
  asks the user to approve it alongside the download, one dialog at a time
  (`InstallScheduler.approval`). Approved -> `installDownloaded(apk, preapproved)` commits that
  session with no dialog; declined -> the download is cancelled and the install ends quietly
  (`AppInstallEvent.Declined`, no `InstallResult`); unavailable -> the usual path. Where it is
  asked at all is `PreapprovalPolicy` (data): standard installer, an installed app whose installer
  of record or update owner is someone else — for apps this app installed the commit is silent
  already, and asking would add a dialog. The request carries the *installed* label and icon
  (`SessionPreapprover`), which the system checks against the APK. The sweep never asks.
  Nothing calls that use case directly but the `@Singleton` `InstallCoordinator`: the apps list,
  the details page and the background sweep all go through it. It never installs the same app
  twice at once, and publishes `installs` (progress per `InstallKey` — the row
  id, or a `Draft` ticket for an app opened from search) and `results`, which is what keeps every
  screen showing the same install. Screens call `install()`, which runs in the application scope
  so it outlives them; the sweep calls `installAndAwait()`, which runs in its own coroutine so
  WorkManager can still cancel it, and waits for an install of that app the user already started
  instead of starting another. `results` is deliberately unbuffered: the entry leaves `installs`
  only after every subscriber has taken the result, so collect it without suspending.
- **Filter suggestions.** The details page's "Suggest" sheet (`ui/feature/filtersuggestion`)
  proposes filters from a file the user picks in the newest release, from their own words, or
  both. An example alone is solved without a model (`ExampleFilterDeriver`: the words that set it
  apart from its siblings, never an ABI marker unless that is the only way). Words go to the
  model behind `LanguageModelGateway`; every reply is a flat JSON object (`FilterPrompt`) and is
  checked by running it — the real selectors over a `SourceSnapshot` fetched once
  (`ProposalVerifier`) — and sent back with what failed, up to `SuggestFiltersUseCase.MAX_ATTEMPTS`.
  The AI never decides what installs: a passing proposal is shown with what each recent release
  would install, and Apply only edits the draft. `LanguageModelGatewayImpl` picks the backend per
  call from `AppSettings.ai` (Off / OnDevice / ApiKey); API keys are in `SecureSecretsRepository`,
  one per provider. Cloud calls use their own `@AiHttpClient` (none of GitHub's interceptors) and
  raw JSON (`CloudProtocol`), no provider SDKs. Gemini Nano only runs in the foreground, so
  nothing in background work may call the gateway with `AiMode.OnDevice`.
- **Background updates.** `SyncBackgroundWorkUseCase` reconciles `WorkManagerBackgroundScheduler`
  with the settings on app start, on boot (`BootReceiver`) and after every relevant toggle.
  `BackgroundMode.Periodic` uses `UpdateCheckWorker`; `Persistent` uses `UpdateMonitorService`
  (a `specialUse` foreground service — `dataSync` is capped at ~6 h/day on Android 14+).
  `RunUpdateSweepUseCase` hands every update to the coordinator at once (the scheduler bounds
  them) and B-SideLoader's own only after the rest; it isolates per-app failures but always
  propagates cancellation.
- **Self-update.** The app tracks itself like any other app: `SelfAppSeed` writes a row pointing at
  `SelfApp.source` (`re7gog/B-SideLoader`) — from `onCreate` for a new database, from the 1 -> 2
  migration for an existing one — except in a `dev` build (`BuildConfig.TRACKS_ITSELF` false),
  which no published release can update. A release build knows which release it is: CI passes the tag as
  `RELEASE_TAG`, and `app/build.gradle.kts` turns it into `BuildConfig.RELEASE_TAG`, `versionName`
  and a `versionCode` that rises with every tag (`v1.2.3` -> `1020399`; pre-release suffixes sort
  below the release). The workflow names each release exactly after its tag, because a GitHub
  app's row stores the release *name*. The seed writes that tag; an untagged local build writes
  nothing (unknown version). Installing this app replaces the running process, so
  `InstallAppUseCase` never writes the row for it: it records a `PendingSelfUpdate` (row id +
  release name) *before* the install, and `ReconcileSelfUpdateUseCase` judges it in the next
  process against the version code remembered in `SelfUpdateStateRepository`: code went up ->
  write the release name; did not -> drop it; nothing pending but the code changed (first start,
  APK installed by hand) -> write the build's own tag. It runs **once per process, before
  anything reads the apps** — `Application.onCreate` starts it, `BootReceiver`
  (`MY_PACKAGE_REPLACED`), the apps list, the details screen, the sweep and the self-install
  itself await it — so no check ever sees the stale row, and a record written by this process's
  own in-flight install is never judged by it. `RunUpdateSweepUseCase` installs this app last,
  because the replace kills whatever is running the sweep.
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
  home".
- `ui/BSideLoaderApp.kt` — one `entryProvider { }` wiring every destination to its screen.
  It also owns the transitions: opening or closing a page (app details, sub-settings) slides it in
  from / out to the right, a tab switch only shifts sideways, and predictive back drags the page
  off to the right (linear, so it tracks the finger) instead of Nav3's default shrink. Which one
  applies comes from `Navigator.isTabSwitch`, not from scene metadata — a tab whose top is an app
  page would otherwise be mistaken for opening that page.

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

### Hidden-API refinement (`dev.rikka.tools.refine`)

`PrivilegedApkInstaller` calls hidden framework APIs through `hidden-stub` classes such as
`PackageInstallerHidden`, which refine's ASM transform rewrites to the real classes. The refine
Gradle plugin is **deliberately not applied** in `:app`: it registers that transform with
`InstrumentationScope.ALL` on every component, unit tests included, which re-serialises every
dependency jar on the test classpath and breaks the signed BouncyCastle/Conscrypt jars Robolectric
loads ("SHA-256 digest error"). `app/build.gradle.kts` registers `RefineFactory` itself in an
`androidComponents` block — `ALL` for the app and instrumented tests, exactly as the plugin did,
`PROJECT` for host (unit) tests. Do not re-add `alias(libs.plugins.refine)` to `:app`.

### `:tdlib` module

Prebuilt TDLib native libraries stripped from Telegram X live in `tdlib/src/main/libs/<abi>/`
(`libtdjni.so`, `libsslx.so`, `libcryptox.so`). Java bindings are `org.drinkless.tdlib.Client` and
`TdApi` (generated, enormous). `TdlibClient` owns the native client and adapts it to coroutines;
`TelegramRepositoryImpl` maps to domain models. The separate CMake `native-lib` exists only to hold
the obfuscated API secrets.

## Testing

`./gradlew :app:testDebugUnitTest` runs every automated test — 449, all on the JVM, no device:

- **Plain JUnit** for pure logic: selection, mappers, error translation, use cases, ViewModels,
  the navigation state machine.
- **Robolectric** (`@RunWith(AndroidJUnit4::class)`) for everything that touches the framework:
  Room against real SQLite and the migrations, DataStore, Keystore-sealed secrets, WorkManager
  scheduling and the worker, notifications, `PackageManager`/`PackageInstaller` sessions, the
  result receivers, OEM background restrictions, and every Compose screen.

There are no instrumented tests; `src/androidTest` only keeps `HiltTestRunner` for the user's own
device runs. Fakes (not mocks) live in `app/src/test/java/.../testing/`. `docs/testing.md` has the
suite map and the Robolectric specifics; the ones that bite:

- `src/test/resources/robolectric.properties` swaps in a plain `Application` — the real one starts
  TDLib and background work. Code behind `@AndroidEntryPoint` (the receivers) is tested with
  `@HiltAndroidTest` + `@Config(application = HiltTestApplication::class)`.
- Screens are tested through their stateless overload, or by passing a ViewModel built from fakes
  as the `viewModel` argument. Long screens are `LazyColumn`s: scroll with `performScrollToNode`
  before asserting on anything below the fold.
- The default SDK is `targetSdk` (37). Older-API branches use `@Config(sdk = [...])`.
- Robolectric has no AndroidKeyStore: `FakeAndroidKeyStore` installs an in-memory provider.
- Robolectric's `PackageInstaller.Session.commit` reports success with no `EXTRA_STATUS`; use
  `ShadowCommitOnlySession` and deliver a realistic verdict through the committed `IntentSender`.
- On Windows, an APK that Robolectric's package parser has read stays open and cannot be deleted.

## Conventions

- Kotlin official style (`kotlin.code.style=official`), non-transitive R classes, Gradle
  configuration cache on.
- New DI bindings go in `data/di/*Module.kt`; debug-only bindings in `app/src/debug/.../di/`.
- ViewModels are constructor-injected `@HiltViewModel`; screens have a stateless overload taking a
  UI state and callbacks, so they can be tested and previewed without a ViewModel.
- Room schema is exported to `app/schemas`. Changing it means bumping `AppsDatabase.DB_VERSION`,
  adding a `Migration` to `AppsDatabase.MIGRATIONS`, committing the new JSON, and adding a case
  to `AppsDatabaseMigrationTest` (it builds the old database from the committed JSON). There is
  **no** destructive fallback.
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
