package com.safetrack.watch.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts the device token with an AES key that never leaves the Android
 * Keystore, so the token is never stored in plain text.
 */
class TokenCipher {

    /** Throws only if the Keystore cannot be used at all, even with a fresh key. */
    fun encrypt(plain: String): String = try {
        seal(plain)
    } catch (e: Exception) {
        // A key that exists but cannot be used (seen on Android 11 after reinstalls):
        // replace it. Nothing encrypted with it is readable anyway.
        Log.w(TAG, "Device token key unusable; creating a new one", e)
        deleteKey()
        seal(plain)
    }

    private fun seal(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    /** Null when the value cannot be decrypted (e.g. the Keystore key was reset). */
    fun decrypt(encoded: String): String? = try {
        val sealed = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES),
        )
        String(cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES), Charsets.UTF_8)
    } catch (e: Exception) {
        Log.w(TAG, "Stored device token could not be decrypted", e)
        null
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val existing = try {
            keyStore.getKey(ALIAS, null) as? SecretKey
        } catch (e: Exception) {
            // Alias present but key material missing ("Key not found"): start over.
            Log.w(TAG, "Device token key could not be loaded; replacing it", e)
            runCatching { keyStore.deleteEntry(ALIAS) }
            null
        }
        existing?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun deleteKey() {
        runCatching { KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(ALIAS) }
    }

    private companion object {
        const val TAG = "TokenCipher"
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "safetrack_device_token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
