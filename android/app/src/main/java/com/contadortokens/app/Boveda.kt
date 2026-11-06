package com.contadortokens.app

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cifra con AES-GCM usando una clave que vive en el Android Keystore (no sale del chip/TEE).
 * Así los tokens de sesión no quedan en texto plano en SharedPreferences.
 * Si no se puede descifrar (clave perdida, valor antiguo en claro), devuelve null y la app pide entrar otra vez.
 */
internal object Boveda {
    private const val ALIAS = "contador-tokens-sesion"
    private const val PREFIJO = "v1:"

    private fun clave(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    fun cifrar(texto: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, clave()) }
        val datos = c.doFinal(texto.toByteArray())
        return PREFIJO + Base64.encodeToString(c.iv + datos, Base64.NO_WRAP)
    }

    fun descifrar(guardado: String?): String? {
        if (guardado.isNullOrEmpty() || !guardado.startsWith(PREFIJO)) return null
        return runCatching {
            val b = Base64.decode(guardado.removePrefix(PREFIJO), Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, clave(), GCMParameterSpec(128, b.copyOfRange(0, 12)))
            String(c.doFinal(b.copyOfRange(12, b.size)))
        }.getOrNull()
    }
}
