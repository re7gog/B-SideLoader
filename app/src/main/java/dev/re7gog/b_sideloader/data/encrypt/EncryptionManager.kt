package dev.re7gog.b_sideloader.data.encrypt

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Ciphertext plus the IV it was produced with. Both are needed to decrypt. */
data class Sealed(val ciphertext: ByteArray, val iv: ByteArray) {
    // Generated equals/hashCode compare identity for arrays, which makes cached values look
    // different every read; content comparison is what callers actually mean.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Sealed) return false
        return ciphertext.contentEquals(other.ciphertext) && iv.contentEquals(other.iv)
    }

    override fun hashCode(): Int = 31 * ciphertext.contentHashCode() + iv.contentHashCode()
}

/**
 * AES-256-GCM using a key that never leaves the hardware-backed Android Keystore.
 *
 * The key is created on first use rather than in a class initializer: an `object` that talked to
 * the Keystore while loading turned any Keystore hiccup into a crash at an unpredictable point,
 * with a stack trace pointing at whoever happened to touch the class first.
 */
@Singleton
class EncryptionManager @Inject constructor() {

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    fun seal(data: ByteArray): Sealed {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        return Sealed(ciphertext = cipher.doFinal(data), iv = cipher.iv)
    }

    fun open(sealed: Sealed): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(AUTH_TAG_BITS, sealed.iv))
        return cipher.doFinal(sealed.ciphertext)
    }

    @Synchronized
    private fun masterKey(): SecretKey {
        keyStore.getKey(MASTER_KEY_ALIAS, null)?.let { return it as SecretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                MASTER_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val MASTER_KEY_ALIAS = "bside_master_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val AUTH_TAG_BITS = 128
    }
}
