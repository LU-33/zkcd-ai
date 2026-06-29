package com.example.aicreationassistant.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 双层密钥架构：
 * - 第一层：EncryptedSharedPreferences (Keystore 保护) 存储内容加密密钥
 * - 第二层：软件 AES-256-GCM 密钥加密实际用户内容（支持自定义 IV）
 */
class CryptoManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "ai_content_crypto_prefs"
        private const val KEY_CONTENT_AES = "content_aes_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE_BYTES = 12
        private const val GCM_TAG_LENGTH = 128
        private const val AES_KEY_SIZE = 256
    }

    // EncryptedSharedPreferences — 由 Keystore 保护
    private val securePrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * 获取或创建内容加密密钥（软件 AES 密钥，存在 EncryptedSharedPreferences 中）
     * 该密钥由 Keystore 间接保护（通过 EncryptedSP），同时允许自定义 IV
     */
    private fun getOrCreateContentKey(): SecretKeySpec {
        val existingKey = securePrefs.getString(KEY_CONTENT_AES, null)
        if (existingKey != null) {
            val keyBytes = Base64.decode(existingKey, Base64.NO_WRAP)
            return SecretKeySpec(keyBytes, "AES")
        }

        // 生成新密钥（纯软件密钥，不绑定 Keystore）
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(AES_KEY_SIZE)
        val key = keyGenerator.generateKey()

        // 将密钥存储到 EncryptedSharedPreferences（由 Keystore 加密保存）
        securePrefs.edit()
            .putString(KEY_CONTENT_AES, Base64.encodeToString(key.encoded, Base64.NO_WRAP))
            .apply()

        return SecretKeySpec(key.encoded, "AES")
    }

    /**
     * 加密明文，返回 Base64 编码的密文和 IV
     */
    fun encrypt(plaintext: String): EncryptionResult {
        val key = getOrCreateContentKey()

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = ByteArray(IV_SIZE_BYTES).also {
            SecureRandom().nextBytes(it)
        }
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)

        val plainBytes = plaintext.toByteArray(Charsets.UTF_8)
        val cipherBytes = cipher.doFinal(plainBytes)

        return EncryptionResult(
            ciphertext = Base64.encodeToString(cipherBytes, Base64.NO_WRAP),
            iv = Base64.encodeToString(iv, Base64.NO_WRAP)
        )
    }

    /**
     * 解密密文
     */
    fun decrypt(ciphertextBase64: String, ivBase64: String): String {
        val key = getOrCreateContentKey()

        val cipherBytes = Base64.decode(ciphertextBase64, Base64.NO_WRAP)
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        val plainBytes = cipher.doFinal(cipherBytes)
        return String(plainBytes, Charsets.UTF_8)
    }
}

data class EncryptionResult(
    val ciphertext: String,
    val iv: String
)
