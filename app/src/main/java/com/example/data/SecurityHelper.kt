package com.example.data

import java.security.MessageDigest
import java.security.SecureRandom

object SecurityHelper {
    private val random = SecureRandom()

    fun generateSalt(): String {
        val saltBytes = ByteArray(16)
        random.nextBytes(saltBytes)
        return saltBytes.joinToString("") { "%02x".format(it) }
    }

    fun hashPin(pin: String, salt: String): String {
        val input = "$salt:$pin"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun verifyPin(pin: String, salt: String, expectedHash: String): Boolean {
        if (expectedHash.isBlank()) return false
        // Direct plain text PIN match (e.g. from web app)
        if (pin == expectedHash) return true

        // Unsalted SHA-256 comparison
        val unsaltedHash = MessageDigest.getInstance("SHA-256")
            .digest(pin.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        if (unsaltedHash.equals(expectedHash, ignoreCase = true)) return true

        // Salted hash comparison
        val computed = hashPin(pin, salt)
        return computed.equals(expectedHash, ignoreCase = true)
    }
}
