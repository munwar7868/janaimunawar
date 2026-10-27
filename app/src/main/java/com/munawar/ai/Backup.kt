package com.munawar.ai

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Passphrase-encrypted backup: "MNW1" + salt(16) + iv(12) + AES-256-GCM ciphertext. */
object Backup {
    private val magic = "MNW1".toByteArray()

    private fun key(pass: CharArray, salt: ByteArray): SecretKeySpec {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(f.generateSecret(PBEKeySpec(pass, salt, 120_000, 256)).encoded, "AES")
    }

    fun encrypt(pass: CharArray, plain: ByteArray): ByteArray {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(pass, salt))
        return magic + salt + c.iv + c.doFinal(plain)
    }

    /** Returns null if the passphrase is wrong or the file is not a Munawar backup. */
    fun decrypt(pass: CharArray, data: ByteArray): ByteArray? {
        if (data.size < 4 + 16 + 12 + 16 || !data.copyOfRange(0, 4).contentEquals(magic)) return null
        return try {
            val salt = data.copyOfRange(4, 20)
            val iv = data.copyOfRange(20, 32)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(pass, salt), GCMParameterSpec(128, iv))
            c.doFinal(data, 32, data.size - 32)
        } catch (e: Exception) { null }
    }
}
