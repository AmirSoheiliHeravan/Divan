package com.divan.app.core.security

import android.util.Base64
import com.divan.app.data.SettingsRepo
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PIN hashing (PBKDF2) + optional biometric unlock support. */
object PinLock {

    fun randomSalt(): String {
        val b = ByteArray(16)
        SecureRandom().nextBytes(b)
        return Base64.encodeToString(b, Base64.NO_WRAP)
    }

    fun hash(pin: String, salt: String): String {
        val spec = PBEKeySpec(pin.toCharArray(), Base64.decode(salt, Base64.NO_WRAP), 60_000, 256)
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return Base64.encodeToString(f.generateSecret(spec).encoded, Base64.NO_WRAP)
    }

    suspend fun setPin(settings: SettingsRepo, pin: String) {
        val salt = randomSalt()
        settings.setPin(hash(pin, salt), salt)
    }

    suspend fun verify(settings: SettingsRepo, pin: String): Boolean {
        val saved = settings.pinHashNow() ?: return false
        return hash(pin, saved.second) == saved.first
    }

    suspend fun isSet(settings: SettingsRepo): Boolean = settings.pinHashNow() != null

    suspend fun clear(settings: SettingsRepo) = settings.clearPin()
}
