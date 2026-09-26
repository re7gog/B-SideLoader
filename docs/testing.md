# Testing

## Running the tests

```bash
./gradlew :app:testDebugUnitTest              # every automated test: JVM + Robolectric, no device
```

Report: `app/build/reports/tests/testDebugUnitTest/index.html`. Run one suite with
`--tests "*.SessionApkInstallerTest"`.

There are no instrumented tests. The app is tested by hand on a physical phone, and nothing in
this project should start an emulator (see `CLAUDE.md`). `src/androidTest` only keeps
`HiltTestRunner`, so a device test can be added later without re-plumbing the runner.

## What is tested where

Everything lives in `app/src/test`. Suites that need the Android framework run on
[Robolectric](https://robolectric.org) (`@RunWith(AndroidJUnit4::class)`); the rest are plain
JUnit and never touch it.

### Pure logic — plain JUnit

| Area | Suite | What it pins down |
|---|---|---|
| Filtering | `domain/selection/NameMatcherTest` | word vs regex matching, case-insensitivity, and how a half-typed regex degrades |
| Architecture matching | `domain/selection/AbiMatcherTest` | universal vs split APKs, 64-bit devices accepting 32-bit splits, the fallback when nothing is installable |
| GitHub selection | `domain/selection/GithubApkSelectorTest` | prereleases, release filters, skipping releases with no matching asset, preferring the asset this device can run |
| Telegram selection | `domain/selection/TelegramApkSelectorTest` | album grouping (a caption in a sibling message), the `.apk` suffix rule, newest-first ordering |
| Persistence mapping | `data/mapper/AppMappersTest` | entity ↔ domain round trips, dropping rows whose details table is missing, stability of the stored source discriminator |
| Error translation | `data/error/ThrowableToAppErrorTest` | IO → `Network`, GitHub's 403-means-rate-limit header quirk, and that cancellation is rethrown rather than mapped |
| Update resolution | `domain/usecase/ResolveUpdateUseCaseTest` | every `UpdateStatus`, and that source failures propagate instead of silently reading as "no update" |
| Install | `domain/usecase/InstallAppUseCaseTest` | insert-vs-update on success, nothing written on failure, the Telegram cache copy being dropped |
| Self-update | `domain/usecase/ReconcileSelfUpdateUseCaseTest` | judging a pending self-update by the version code in the next process |
| Background sweep | `domain/usecase/RunUpdateSweepUseCaseTest` | one failing app not aborting the sweep, the check-only fallback when silent installs are impossible, cancellation propagating |
| ViewModels | `ui/feature/apps/AppsListViewModelTest`, `ui/feature/appdetails/AppDetailsViewModelTest` | installed state reacting to package changes, selection, bulk actions, the details state machine |
| Navigation | `ui/navigation/NavigatorTest` | per-tab back stacks, "exit through home", the post-install jump to the apps list |

### Framework-bound — Robolectric

| Area | Suite | What it pins down |
|---|---|---|
| DAO | `data/local/AppsDaoTest` | the `@Relation` join, `ON DELETE CASCADE`, `COLLATE NOCASE` ordering and matching — against real SQLite |
| Migrations | `data/local/AppsDatabaseMigrationTest` | a version-1 database built from `schemas/1.json`, opened through `DatabaseModule`, gains the self row exactly once and keeps its data |
| Self row | `data/local/SelfAppSeedTest` | a new database tracks the app itself; a row the user deleted stays deleted |
| Repository | `data/repository/RoomAppsRepositoryTest` | app and details rows written together on insert and update, lookups by source, observed flows |
| Settings | `data/settings/DataStoreSettingsRepositoryTest` | the on-disk key names, defaults, the legacy background flag, no re-emission for unrelated keys |
| Self-update state | `data/settings/DataStoreSelfUpdateStateRepositoryTest` | the pending record surviving a new instance, partial records ignored, legacy keys cleaned |
| Secrets | `data/encrypt/SecureSecretsRepositoryTest` | the token never stored in plaintext, a lost Keystore key discarding its ciphertext, the TDLib key reused and migrated |
| Device | `data/device/AndroidDeviceInfoTest` | vendor detection, the TDLib display name, silent self-updates only from Android 12 |
| Scheduling | `data/background/WorkManagerBackgroundSchedulerTest` | the end state for every settings combination: periodic job constraints, UPDATE-not-KEEP, the interval clamp, the monitor service |
| Worker | `data/background/UpdateCheckWorkerTest` | success/retry/failure as WorkManager sees it, alerts only for updates it could not install |
| Notifications | `data/background/NotificationCenterTest` | channels, the updates alert and where it leads, the Android 13 permission gate |
| OEM restrictions | `data/background/AndroidBackgroundRestrictionsTest` | only the detected vendor's autostart screens are tried, missing ones are skipped, "could not open" instead of a crash |
| Packages | `data/installer/AndroidPackageInspectorTest` | installed versions, launching, package broadcasts, the receiver going away with the last collector |
| Manual install staging | `data/installer/CacheApkStagingAreaTest` | reading a real APK's manifest from a content URI, rejecting a non-APK, keeping one staged copy |
| Session installs | `data/installer/SessionApkInstallerTest` | session → commit → receiver → bus → outcome; request-id matching, the confirmation dialog, conflict splitting, sessions abandoned on failure and cancellation |
| Error text | `ui/common/error/AppErrorTextTest` | every `AppError` rendered in English and Russian, with its details intact |
| Screens | `ui/feature/*/…ScreenTest`, `ManualInstallPaneTest` | apps list, app details, search, settings, Telegram sign-in, background checklist, manual install |

## Writing tests

- Pure logic (a new selector, a mapper, a use case) → plain JUnit, no Android imports. This is
  where new tests should go by default.
- A ViewModel → plain JUnit with `MainDispatcherRule` and fakes; assert on the `uiState` flow
  with Turbine.
- Anything that calls the framework → Robolectric. Prefer the real Android class over a fake of
  it: Robolectric's shadows are where the value is (`shadowOf(application).nextStartedActivity`,
  `shadowOf(packageManager).installPackage(...)`, `ShadowBuild`, ...).
- A screen → Robolectric with `createAndroidComposeRule<ComponentActivity>()`, against the
  screen's stateless overload, or with a ViewModel built from fakes passed as its `viewModel`
  argument. Resolve strings with `composeRule.activity.getString(...)` rather than hard-coding
  English.
- A schema change → bump `AppsDatabase.DB_VERSION`, add the `Migration`, commit the generated
  `app/schemas/<version>.json`, and add a case to `AppsDatabaseMigrationTest`.

Test doubles are **fakes**, not mocks (`app/src/test/java/.../testing/Fakes.kt`): a fake repository
really stores what you put in it, so the tests assert on behaviour rather than on which methods were
called, and survive refactors. `Fixtures.kt` has builders with defaults so each test states only
the field it is about.

Coroutines are driven through `runTest`, `MainDispatcherRule` and injected dispatchers. That only
works because production code injects `DispatcherProvider` instead of touching `Dispatchers`
directly. Integration suites over Room or DataStore use `DefaultDispatcherProvider()`: those
libraries run on their own threads anyway.

## Robolectric in this project

### How it is set up

- **Version and SDK.** Robolectric 4.17, which ships an image for API 37. Tests run
  against `targetSdk` unless they say otherwise; the few that cover an older-API branch use
  `@Config(sdk = [Build.VERSION_CODES.R])`. Each extra SDK is a one-time download of its image.
- **Application.** `src/test/resources/robolectric.properties` swaps `BSideApplication` for a plain
  `Application`. The real one builds the Hilt graph, starts TDLib (a native library the JVM does
  not have) and reconciles background work on create.
- **Hilt.** Only where it cannot be avoided: an `@AndroidEntryPoint` receiver injects itself from
  the application, so `SessionApkInstallerTest` runs as `@HiltAndroidTest` with
  `@Config(application = HiltTestApplication::class)`. Everything else builds its subject by hand.
- **JVM flags.** `app/build.gradle.kts` gives the test JVM a 2 GB heap and
  `--add-exports=java.base/jdk.internal.access=ALL-UNNAMED`: Robolectric's `FileDescriptor`
  interceptor, reached while the API 37 image starts, uses a JDK-internal class that JDK 17+ does
  not export. Without the flag every test fails with an `IllegalAccessException`.
- **Resources.** `unitTests.isIncludeAndroidResources` is on, so the merged manifest, resources and
  assets are available; the exported Room schemas are on the test classpath as plain resources.

### The refine plugin, and why Robolectric used to fail

Robolectric was dropped once because every test died while loading classes:

```
java.lang.SecurityException: SHA-256 digest error for org/conscrypt/OpenSSLProvider.class
```

The cause was the `dev.rikka.tools.refine` Gradle plugin. It registers its ASM transform with
`InstrumentationScope.ALL` on every component — unit tests included — and its factory claims every
class is instrumentable. So AGP re-serialised every class of every dependency jar on the test
classpath, class bytes changed, and the **signed** BouncyCastle and Conscrypt jars Robolectric
loads failed their signature check.

`app/build.gradle.kts` therefore no longer applies the plugin. It registers `RefineFactory` itself
in an `androidComponents` block: `ALL` for the app and instrumented tests, exactly as the plugin
did, and `PROJECT` for host tests, which leaves dependency jars untouched. The app's only refined
references are in `PrivilegedApkInstaller`, which no unit test runs.

### Test doubles for what Robolectric does not model

- **`FakeAndroidKeyStore`** — Robolectric has no `AndroidKeyStore` provider. This one keeps keys in
  memory and accepts a `KeyGenParameterSpec`; the AES-GCM sealing is still the JVM's real cipher.
  `loseAllKeys()` simulates a restore or lock-screen reset.
- **`ShadowCommitOnlySession`** — Robolectric's `PackageInstaller.Session.commit` reports success at
  once, through an intent with no `EXTRA_STATUS`, which no real installer sends. This shadow only
  records the commit; the test then plays the system by sending a verdict through the committed
  `IntentSender`, so it arrives only if the installer tagged it correctly.
- **`ExportedSchema`** — replays an exported `schemas/<version>.json` as SQL. It replaces
  `MigrationTestHelper`, which reads schemas from test-APK assets that a local test does not have.

### Known quirks

- **Windows file locks.** Once Robolectric's package parser has read an APK, it keeps the file
  open, and Windows refuses to delete an open file. `CacheApkStagingAreaTest` plants its copy for
  the `clear()` case for that reason; a device does not have the problem.
- **Screens are small.** Robolectric's default display is phone-sized, and most screens are
  `LazyColumn`s. Scroll with `performScrollToNode` before asserting on, or clicking, anything
  below the fold — a click on an off-screen node silently does nothing.
- **Started activities.** Launching the Compose test activity is itself a started activity; call
  `shadowOf(application).clearNextStartedActivities()` before asserting what a screen launched.
- **One DataStore per process.** `Context.appPreferences` outlives each test's `Application`, so
  DataStore suites clear it in `@Before`.
