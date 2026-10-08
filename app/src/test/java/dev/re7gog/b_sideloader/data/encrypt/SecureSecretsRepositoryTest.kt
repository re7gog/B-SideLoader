package dev.re7gog.b_sideloader.data.encrypt

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.re7gog.b_sideloader.core.coroutines.DefaultDispatcherProvider
import dev.re7gog.b_sideloader.core.log.NoopLogger
import dev.re7gog.b_sideloader.domain.model.AiProvider
import dev.re7gog.b_sideloader.testing.FakeAndroidKeyStore
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The GitHub token and TDLib's database key at rest.
 *
 * Runs against [FakeAndroidKeyStore]: the Keystore itself is simulated, but the AES-GCM sealing,
 * the Base64 storage and every recovery path are the production code.
 */
@RunWith(AndroidJUnit4::class)
class SecureSecretsRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE)

    @Before
    fun installKeyStore() = FakeAndroidKeyStore.install()

    @After
    fun uninstallKeyStore() = FakeAndroidKeyStore.uninstall()

    /** A fresh instance has an empty cache, like the next process start. */
    private fun repository() = SecureSecretsRepository(
        context,
        EncryptionManager(),
        DefaultDispatcherProvider(),
        NoopLogger,
    )

    @Test
    fun aSavedTokenIsReadBackByTheNextProcess() = runTest {
        repository().setGithubToken("ghp_secret")

        assertEquals("ghp_secret", repository().getGithubToken())
        assertEquals("ghp_secret", repository().currentToken())
    }

    @Test
    fun theTokenIsNeverStoredInPlaintext() = runTest {
        repository().setGithubToken("ghp_secret")

        val stored = prefs.all.values.joinToString()
        assertFalse(stored.contains("ghp_secret"))
    }

    @Test
    fun aBlankTokenClearsTheStoredOne() = runTest {
        repository().setGithubToken("ghp_secret")

        repository().setGithubToken("   ")

        assertNull(repository().getGithubToken())
        assertEquals(emptySet<String>(), prefs.all.keys)
    }

    /** The interceptor reads the token per request; a new one must apply without a restart. */
    @Test
    fun aChangedTokenReachesTheSynchronousViewImmediately() = runTest {
        val repository = repository()
        repository.setGithubToken("old")
        assertEquals("old", repository.currentToken())

        repository.setGithubToken("new")

        assertEquals("new", repository.currentToken())
    }

    /**
     * A Keystore key can vanish (device restore, lock-screen reset on some ROMs). The unreadable
     * ciphertext is dropped so the user can enter the token again, instead of failing forever.
     */
    @Test
    fun aTokenWhoseKeyWasLostIsDiscarded() = runTest {
        repository().setGithubToken("ghp_secret")
        FakeAndroidKeyStore.loseAllKeys()

        assertNull(repository().getGithubToken())
        assertEquals(emptySet<String>(), prefs.all.keys)
    }

    /** Losing this key signs the user out of Telegram, so it has to be the same one every time. */
    @Test
    fun theTelegramDatabaseKeyIsCreatedOnceAndThenReused() {
        val first = repository().getOrCreateTelegramDbKey()
        val second = repository().getOrCreateTelegramDbKey()

        assertEquals(32, first.size)
        assertArrayEquals(first, second)
    }

    /** Installs from before the refactor stored the key hex-encoded under other names. */
    @Test
    fun aLegacyHexEncodedDatabaseKeyIsMigratedNotReplaced() {
        val legacyKey = ByteArray(32) { it.toByte() }
        val sealed = EncryptionManager().seal(legacyKey)
        prefs.edit {
            putString("encrypted_db_key", sealed.ciphertext.toHex())
            putString("db_key_iv", sealed.iv.toHex())
        }

        val migrated = repository().getOrCreateTelegramDbKey()

        assertArrayEquals(legacyKey, migrated)
        assertEquals(setOf("tdlib_db_key_bytes", "tdlib_db_key_iv"), prefs.all.keys)
        assertArrayEquals(legacyKey, repository().getOrCreateTelegramDbKey())
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    @Test
    fun aiKeysAreSealedPerProvider() = runTest {
        repository().setAiApiKey(AiProvider.OpenAi, "sk-openai")
        repository().setAiApiKey(AiProvider.Gemini, "gm-key")

        assertEquals("sk-openai", repository().getAiApiKey(AiProvider.OpenAi))
        assertEquals("gm-key", repository().getAiApiKey(AiProvider.Gemini))
        assertNull(repository().getAiApiKey(AiProvider.Anthropic))
        assertFalse(prefs.all.values.joinToString().contains("sk-openai"))

        repository().setAiApiKey(AiProvider.OpenAi, "")
        assertNull(repository().getAiApiKey(AiProvider.OpenAi))
        assertEquals("gm-key", repository().getAiApiKey(AiProvider.Gemini))
    }
}
