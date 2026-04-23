package dev.re7gog.b_sideloader.data.encrypt

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureStorage @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE)

    fun getOrGenerateDbKey(): ByteArray {
        val encryptedHex = prefs.getString("encrypted_db_key", null)
        val ivHex = prefs.getString("db_key_iv", null)

        return if (encryptedHex != null && ivHex != null) {
            EncryptionManager.decrypt(
                encryptedData = hexToBytes(encryptedHex),
                iv = hexToBytes(ivHex)
            )
        } else {
            val newKey = ByteArray(32).apply { SecureRandom().nextBytes(this) }
            val (encrypted, iv) = EncryptionManager.encrypt(newKey)

            prefs.edit {
                putString("encrypted_db_key", bytesToHex(encrypted))
                    .putString("db_key_iv", bytesToHex(iv))
            }

            newKey
        }
    }

    fun saveGithubToken(token: String) {
        val (encrypted, iv) = EncryptionManager.encrypt(token.toByteArray(Charsets.UTF_8))
        prefs.edit {
            putString("gh_token_bytes", Base64.encodeToString(encrypted, Base64.DEFAULT))
                .putString("gh_token_iv", Base64.encodeToString(iv, Base64.DEFAULT))
        }
    }

    fun getGithubToken(): String? {
        val dataStr = prefs.getString("gh_token_bytes", null) ?: return null
        val ivStr = prefs.getString("gh_token_iv", null) ?: return null
        val decryptedBytes = EncryptionManager.decrypt(
            encryptedData = Base64.decode(dataStr, Base64.DEFAULT),
            iv = Base64.decode(ivStr, Base64.DEFAULT)
        )
        return String(decryptedBytes, Charsets.UTF_8)
    }

    private fun bytesToHex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    private fun hexToBytes(hex: String) = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}