package dev.re7gog.b_sideloader.testing

import android.security.keystore.KeyGenParameterSpec
import java.io.InputStream
import java.io.OutputStream
import java.security.Key
import java.security.KeyStoreSpi
import java.security.Provider
import java.security.SecureRandom
import java.security.Security
import java.security.cert.Certificate
import java.security.spec.AlgorithmParameterSpec
import java.util.Collections
import java.util.Date
import java.util.Enumeration
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.KeyGeneratorSpi
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * An in-memory "AndroidKeyStore" JCA provider, because Robolectric does not have one.
 *
 * Covers exactly what `EncryptionManager` uses: a key store to look a secret key up by alias, and
 * an AES key generator that accepts a [KeyGenParameterSpec] and files the new key under its alias.
 * Encryption itself runs on the JVM's own AES-GCM, so the ciphertext is real — only the
 * "the key never leaves secure hardware" part is simulated.
 */
object FakeAndroidKeyStore {

    private const val NAME = "AndroidKeyStore"
    private val keys = ConcurrentHashMap<String, SecretKey>()

    fun install() {
        if (Security.getProvider(NAME) == null) Security.insertProviderAt(KeyStoreProvider(), 1)
    }

    fun uninstall() {
        Security.removeProvider(NAME)
        keys.clear()
    }

    /** What a device restore or a lock-screen reset does to Keystore keys on some ROMs. */
    fun loseAllKeys() = keys.clear()

    // The `double` version is the only constructor android.jar declares.
    @Suppress("DEPRECATION")
    private class KeyStoreProvider : Provider(NAME, 1.0, "In-memory AndroidKeyStore for tests") {
        init {
            put("KeyStore.$NAME", InMemoryKeyStore::class.java.name)
            put("KeyGenerator.AES", AesKeyGenerator::class.java.name)
        }
    }

    class InMemoryKeyStore : KeyStoreSpi() {
        override fun engineLoad(stream: InputStream?, password: CharArray?) = Unit
        override fun engineGetKey(alias: String, password: CharArray?): Key? = keys[alias]
        override fun engineContainsAlias(alias: String): Boolean = keys.containsKey(alias)
        override fun engineIsKeyEntry(alias: String): Boolean = keys.containsKey(alias)
        override fun engineDeleteEntry(alias: String) {
            keys.remove(alias)
        }

        override fun engineAliases(): Enumeration<String> = Collections.enumeration(keys.keys.toList())
        override fun engineSize(): Int = keys.size
        override fun engineIsCertificateEntry(alias: String): Boolean = false
        override fun engineGetCertificate(alias: String): Certificate? = null
        override fun engineGetCertificateChain(alias: String): Array<Certificate>? = null
        override fun engineGetCertificateAlias(cert: Certificate): String? = null
        override fun engineGetCreationDate(alias: String): Date? = null

        override fun engineSetKeyEntry(
            alias: String,
            key: Key,
            password: CharArray?,
            chain: Array<out Certificate>?,
        ) = throw UnsupportedOperationException("Keys are only created through the KeyGenerator")

        override fun engineSetKeyEntry(alias: String, key: ByteArray, chain: Array<out Certificate>?) =
            throw UnsupportedOperationException("Keys are only created through the KeyGenerator")

        override fun engineSetCertificateEntry(alias: String, cert: Certificate) =
            throw UnsupportedOperationException()

        override fun engineStore(stream: OutputStream?, password: CharArray?) =
            throw UnsupportedOperationException()
    }

    class AesKeyGenerator : KeyGeneratorSpi() {
        private var spec: KeyGenParameterSpec? = null
        private var random: SecureRandom = SecureRandom()

        override fun engineInit(params: AlgorithmParameterSpec, random: SecureRandom?) {
            spec = params as? KeyGenParameterSpec
                ?: throw IllegalArgumentException("AndroidKeyStore keys need a KeyGenParameterSpec")
            random?.let { this.random = it }
        }

        override fun engineInit(random: SecureRandom?) =
            throw UnsupportedOperationException("AndroidKeyStore keys need a KeyGenParameterSpec")

        override fun engineInit(keysize: Int, random: SecureRandom?) =
            throw UnsupportedOperationException("AndroidKeyStore keys need a KeyGenParameterSpec")

        override fun engineGenerateKey(): SecretKey {
            val spec = checkNotNull(spec) { "Not initialised" }
            val bytes = ByteArray(spec.keySize / Byte.SIZE_BITS).also(random::nextBytes)
            return SecretKeySpec(bytes, "AES").also { keys[spec.keystoreAlias] = it }
        }
    }
}
