package com.safetrack.watch.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.KeyStore
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on the watch: the device token must survive the real Android Keystore. */
@RunWith(AndroidJUnit4::class)
class TokenCipherTest {

    @Test
    fun encryptsAndDecryptsDeviceToken() {
        val cipher = TokenCipher()
        val token = "a".repeat(64)
        assertEquals(token, cipher.decrypt(cipher.encrypt(token)))
    }

    @Test
    fun recoversWhenTheKeyIsMissing() {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry("safetrack_device_token")
        val cipher = TokenCipher()
        val token = "b".repeat(64)
        assertEquals(token, cipher.decrypt(cipher.encrypt(token)))
    }
}
