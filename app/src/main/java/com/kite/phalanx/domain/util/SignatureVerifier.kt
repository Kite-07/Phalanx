package com.kite.phalanx.domain.util

import android.util.Base64
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Ed25519 signature verification for sender pack authenticity.
 *
 * Uses Ed25519 (EdDSA) digital signature algorithm to verify that sender packs
 * have not been tampered with and come from a trusted source.
 *
 * Security properties:
 * - 128-bit security level
 * - Fast verification (~50μs on modern devices)
 * - Small signatures (64 bytes)
 * - Deterministic signatures (no random number generation needed)
 */
object SignatureVerifier {

    /**
     * Public key for verifying sender pack signatures (Ed25519)
     * This is the public key corresponding to the private key used to sign packs.
     *
     * Format: Base64-encoded X.509 SubjectPublicKeyInfo
     *
     * Generated: 2025-01-09
     * Keep corresponding private key secure (ed25519_private_key.pem)
     */
    private const val PUBLIC_KEY_BASE64 = "MCowBQYDK2VwAyEAP4B9tb00dolTFCGx4v83vAjcVGXZKOfMKLqSbpv0yEI="

    /**
     * Verifies the Ed25519 signature of a message.
     *
     * @param message The message bytes that were signed
     * @param signatureHex The signature in hexadecimal format
     * @return true if signature is valid, false otherwise
     */
    fun verify(message: ByteArray, signatureHex: String): Boolean {
        return try {
            val publicKey = loadPublicKey()
            val signatureBytes = hexToBytes(signatureHex)

            val signature = Signature.getInstance("Ed25519")
            signature.initVerify(publicKey)
            signature.update(message)
            signature.verify(signatureBytes)
        } catch (e: Exception) {
            // Log error in production
            android.util.Log.e("SignatureVerifier", "Signature verification failed", e)
            false
        }
    }

    /**
     * Convenience method to verify a string message
     */
    fun verify(message: String, signatureHex: String): Boolean {
        return verify(message.toByteArray(Charsets.UTF_8), signatureHex)
    }

    /**
     * Loads the Ed25519 public key from base64 encoding
     */
    private fun loadPublicKey(): PublicKey {
        val keyBytes = Base64.decode(PUBLIC_KEY_BASE64, Base64.NO_WRAP)
        val keySpec = X509EncodedKeySpec(keyBytes)

        return try {
            // Try Ed25519 key factory (available on Android API 26+)
            val keyFactory = KeyFactory.getInstance("Ed25519")
            keyFactory.generatePublic(keySpec)
        } catch (e: Exception) {
            // Fallback for older Android versions or missing provider
            throw UnsupportedOperationException(
                "Ed25519 signatures require Android API 26+",
                e
            )
        }
    }

    /**
     * Converts hexadecimal string to byte array
     */
    private fun hexToBytes(hex: String): ByteArray {
        val cleaned = hex.replace(" ", "").replace(":", "")
        require(cleaned.length % 2 == 0) { "Hex string must have even length" }

        return ByteArray(cleaned.length / 2) { i ->
            cleaned.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    /**
     * Converts byte array to hexadecimal string
     */
    fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

/**
 * Extension function for easy verification
 */
fun String.verifySignature(signatureHex: String): Boolean {
    return SignatureVerifier.verify(this, signatureHex)
}
