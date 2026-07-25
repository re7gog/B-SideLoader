# Testing

## Running the tests

```bash
./gradlew :app:testDebugUnitTest              # JVM, no device, ~seconds
./gradlew :app:connectedDebugAndroidTest      # needs a connected device or emulator
```

Reports: `app/build/reports/tests/testDebugUnitTest/index.html` and
`app/build/reports/androidTests/connected/index.html`.

## What is tested where

### `app/src/test` — local JVM tests (91)

Everything that does not need the Android framework. These are the tests that should catch a
regression before a build finishes.

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
| Background sweep | `domain/usecase/RunUpdateSweepUseCase` | one failing app not aborting the sweep, the check-only fallback when silent installs are impossible, cancellation propagating |
| Apps list | `ui/feature/apps/AppsListViewModelTest` | installed state reacting to package changes, selection behaviour, bulk actions skipping apps that are not installed |
| Navigation | `ui/navigation/NavigatorTest` | per-tab back stacks, "exit through home", the post-install jump to the apps list |

Test doubles are **fakes**, not mocks (`app/src/test/java/.../testing/Fakes.kt`): a fake repository
really stores what you put in it, so the tests assert on behaviour rather than on which methods
were called, and survive refactors. `Fixtures.kt` has builders with defaults so each test states
only the field it is about.

Coroutines are driven through `TestDispatcherProvider` / `MainDispatcherRule`. That works only
because production code injects `DispatcherProvider` instead of touching `Dispatchers` directly.

### `app/src/androidTest` — instrumented tests

| Suite | Why it needs a device |
|---|---|
| `data/local/AppsDaoTest` | The `@Relation` join, `ON DELETE CASCADE` and `COLLATE NOCASE` are SQLite behaviours; an in-memory Room database on a real device is the only thing that proves them. |
| `ui/feature/apps/AppsListScreenTest` | Compose UI behaviour: tap vs long-press, selection mode not navigating, the empty state. |

`HiltTestRunner` substitutes `HiltTestApplication` for the real `Application`, which otherwise
starts TDLib and reconciles background work on create.

## Why there is no Robolectric

Robolectric was set up first, and it cannot run in this toolchain. Every Robolectric test — down to
a two-line "resolve a string resource" smoke test — fails during class loading:

```
java.lang.SecurityException: SHA-256 digest error for org/conscrypt/OpenSSLProvider.class
java.lang.InternalError: cannot create instance of org.bouncycastle.jcajce.provider.digest.Blake2b$Mappings
  : java.lang.SecurityException: SHA-256 digest error for org/bouncycastle/internal/asn1/misc/MiscObjectIdentifiers.class
```

Both conscrypt and BouncyCastle ship **signed** jars, and Robolectric loads both when it sets up an
Android image. Gradle rewrites dependency jars on the way onto the test classpath — the instrumented
copy of `bcprov-jdk18on` has different class bytes than the original (4288 → 4290 bytes for one
class) — which invalidates the signature, so the JVM refuses to load the classes. Confirmed to be
independent of this project: it reproduces with the configuration cache disabled, is not fixed by
pinning a newer conscrypt, and excluding the jars only turns the `SecurityException` into a
`NoClassDefFoundError` because Robolectric reflects on the provider classes regardless.

Rather than leave a configured-but-broken tool in the build, Robolectric is not a dependency, and
the framework-dependent UI tests live in `androidTest` where they actually run.

**To re-enable it** on a toolchain where signed jars survive (a stock JDK, or a Gradle version that
does not rewrite them):

1. Add back `robolectric = "4.16.1"` and the `robolectric` library alias in
   `gradle/libs.versions.toml`, and `testImplementation(libs.robolectric)` plus the
   `androidx.test.core` / `androidx.junit` / Compose test artifacts to the `test` configuration.
2. Recreate `app/src/test/resources/robolectric.properties` with `sdk=35` (Robolectric 4.16 has no
   image for this project's `targetSdk`).
3. Move `app/src/androidTest/java/.../ui/feature/apps/AppsListScreenTest.kt` to `src/test`; it needs
   no changes beyond the package move.

`testOptions.unitTests.isIncludeAndroidResources` is deliberately left enabled in
`app/build.gradle.kts` so step 1 is all that is required.

## Adding tests

- Pure logic (a new selector, a mapper, a use case) → `src/test`, no Android imports, no Robolectric
  needed. This is where new tests should go by default.
- A ViewModel → `src/test` with `MainDispatcherRule` and fakes; assert on the `uiState` flow with
  Turbine.
- A screen → `src/androidTest`, against the screen's stateless overload (the one that takes a UI
  state and callbacks) so no ViewModel or database is involved.
- A schema change → bump `AppsDatabase.DB_VERSION`, add the `Migration`, commit the generated
  `app/schemas/<version>.json`, and add a `MigrationTestHelper` case in `src/androidTest`. The
  schemas directory is already wired into the instrumented test assets.
