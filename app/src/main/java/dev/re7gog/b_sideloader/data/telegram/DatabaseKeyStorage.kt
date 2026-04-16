package dev.re7gog.b_sideloader.data.telegram

import android.content.Context
import androidx.core.content.edit

class DatabaseKeyStorage(context: Context) {
    private val prefs = context.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE)

    fun getOrGenerateKey(): ByteArray {
        val encryptedHex = prefs.getString("encrypted_db_key", null)
        val ivHex = prefs.getString("db_key_iv", null)

        return if (encryptedHex != null && ivHex != null) {
            EncryptionManager.decrypt(
                encryptedData = hexToBytes(encryptedHex),
                iv = hexToBytes(ivHex)
            )
        } else {
            val newKey = ByteArray(32).apply { java.security.SecureRandom().nextBytes(this) }
            val (encrypted, iv) = EncryptionManager.encrypt(newKey)

            prefs.edit {
                putString("encrypted_db_key", bytesToHex(encrypted))
                    .putString("db_key_iv", bytesToHex(iv))
            }

            newKey
        }
    }

    private fun bytesToHex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    private fun hexToBytes(hex: String) = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}