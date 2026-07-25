package dev.re7gog.b_sideloader.data.encrypt

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.re7gog.b_sideloader.core.coroutines.DispatcherProvider
import dev.re7gog.b_sideloader.core.coroutines.runCatchingCancellable
import dev.re7gog.b_sideloader.core.log.Logger
import dev.re7gog.b_sideloader.data.remote.interceptor.AuthTokenSource
import dev.re7gog.b_sideloader.domain.repository.SecretsRepository
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Private data at rest: the TDLib database key and the GitHub token, sealed with a Keystore-backed
 * master key and stored as ciphertext in `SharedPreferences`.
 *
 * Also implements [AuthTokenSource] so the OkHttp auth interceptor can read the token without
 * blocking: the plaintext token is cached in memory after the first read and invalidated on write.
 * Nothing here is ever logged.
 */
@Singleton
class SecureSecretsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val encryption: EncryptionManager,
    private val dispatchers: DispatcherProvider,
    private val logger: Logger,
) : SecretsRepository, AuthTokenSource {

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    /**
     * `null` = not loaded yet, `Absent` = loaded and there is no token.
     * A plain `String?` cannot tell those apart, which would mean decrypting on every request.
     */
    @Volatile
    private var cachedToken: CachedToken? = null

    override suspend fun getGithubToken(): String? = withContext(dispatchers.io) { currentToken() }

    override suspend fun setGithubToken(token: String) = withContext(dispatchers.io) {
        if (token.isBlank()) {
            prefs.edit { remove(KEY_TOKEN_DATA).remove(KEY_TOKEN_IV) }
            cachedToken = CachedToken.Absent
            return@withContext
        }
        val sealed = encryption.seal(token.toByteArray(Charsets.UTF_8))
        prefs.edit {
            putString(KEY_TOKEN_DATA, sealed.ciphertext.encode())
            putString(KEY_TOKEN_IV, sealed.iv.encode())
        }
        cachedToken = CachedToken.Present(token)
    }

    override fun currentToken(): String? {
        cachedToken?.let { return it.value }
        val loaded = readToken()
        cachedToken = loaded?.let(CachedToken::Present) ?: CachedToken.Absent
        return loaded
    }

    /**
     * The 32-byte key TDLib encrypts its own database with. Generated once and then only ever
     * decrypted; losing it means TDLib cannot open its database and the user has to sign in again.
     */
    fun getOrCreateTelegramDbKey(): ByteArray {
        readTelegramDbKey()?.let { return it }
        // Installs made before this refactor stored the same key hex-encoded under different
        // names. Reuse it rather than minting a new one, which would leave TDLib unable to open
        // its database and silently sign the user out.
        readLegacyTelegramDbKey()?.let { legacy ->
            val resealed = encryption.seal(legacy)
            prefs.edit {
                putString(KEY_TDLIB_DATA, resealed.ciphertext.encode())
                putString(KEY_TDLIB_IV, resealed.iv.encode())
                remove(LEGACY_KEY_TDLIB_DATA)
                remove(LEGACY_KEY_TDLIB_IV)
            }
            return legacy
        }
        val key = ByteArray(TDLIB_KEY_BYTES).also { SecureRandom().nextBytes(it) }
        val sealed = encryption.seal(key)
        prefs.edit {
            putString(KEY_TDLIB_DATA, sealed.ciphertext.encode())
            putString(KEY_TDLIB_IV, sealed.iv.encode())
        }
        return key
    }

    private fun readToken(): String? =
        readSealed(KEY_TOKEN_DATA, KEY_TOKEN_IV)?.toString(Charsets.UTF_8)

    private fun readTelegramDbKey(): ByteArray? = readSealed(KEY_TDLIB_DATA, KEY_TDLIB_IV)

    private fun readLegacyTelegramDbKey(): ByteArray? {
        val data = prefs.getString(LEGACY_KEY_TDLIB_DATA, null) ?: return null
        val iv = prefs.getString(LEGACY_KEY_TDLIB_IV, null) ?: return null
        return runCatchingCancellable {
            encryption.open(Sealed(data.decodeHex(), iv.decodeHex()))
        }.getOrNull()
    }

    private fun String.decodeHex(): ByteArray =
        chunked(2).map { it.toInt(radix = 16).toByte() }.toByteArray()

    private fun readSealed(dataKey: String, ivKey: String): ByteArray? {
        val data = prefs.getString(dataKey, null) ?: return null
        val iv = prefs.getString(ivKey, null) ?: return null
        return runCatchingCancellable { encryption.open(Sealed(data.decode(), iv.decode())) }
            .onFailure {
                // A Keystore key can disappear (device restore, lock-screen change on some ROMs).
                // Dropping the unreadable ciphertext lets the user re-enter the value instead of
                // hitting the same failure on every launch.
                logger.w(TAG, it) { "Stored secret could not be decrypted; discarding it" }
                prefs.edit { remove(dataKey).remove(ivKey) }
            }
            .getOrNull()
    }

    private fun ByteArray.encode(): String = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.decode(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    private sealed interface CachedToken {
        val value: String?

        data class Present(override val value: String) : CachedToken
        data object Absent : CachedToken {
            override val value: String? get() = null
        }
    }

    private companion object {
        const val TAG = "Secrets"
        const val PREFS_NAME = "secure_prefs"
        const val KEY_TOKEN_DATA = "gh_token_bytes"
        const val KEY_TOKEN_IV = "gh_token_iv"
        const val KEY_TDLIB_DATA = "tdlib_db_key_bytes"
        const val KEY_TDLIB_IV = "tdlib_db_key_iv"

        /** Hex-encoded names used before this refactor; read once, then migrated. */
        const val LEGACY_KEY_TDLIB_DATA = "encrypted_db_key"
        const val LEGACY_KEY_TDLIB_IV = "db_key_iv"

        const val TDLIB_KEY_BYTES = 32
    }
}
