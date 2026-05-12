/**
 * ShegerPay Kotlin SDK
 * Official Kotlin SDK for ShegerPay Payment Verification Gateway
 * 
 * Usage:
 *   val client = ShegerPay("sk_test_xxx")
 *   val result = client.verify("FT123456", 100.0, provider = "cbe")
 */

package com.shegerpay.sdk

import java.net.URL
import java.net.HttpURLConnection
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class ShegerPayException(message: String) : Exception(message)
class AuthenticationException(message: String) : ShegerPayException(message)

@Serializable
data class VerificationResult(
    val verified: Boolean = false,
    val valid: Boolean,
    val status: String,
    val provider: String? = null,
    val transactionId: String? = null,
    val amount: Double? = null,
    val reason: String? = null,
    val mode: String? = null,
    val payer: String? = null
)

class ShegerPay(
    private val apiKey: String,
    private val baseUrl: String = "https://api.shegerpay.com"
) {
    private val mode: String
    
    init {
        require(apiKey.isNotEmpty()) { "API key is required" }
        require(apiKey.startsWith("sk_test_") || apiKey.startsWith("sk_live_")) {
            "Invalid API key format"
        }
        mode = if (apiKey.startsWith("sk_test_")) "test" else "live"
    }
    
    /**
     * Verify a payment transaction
     */
    fun verify(
        transactionId: String,
        amount: Double,
        provider: String? = null,
        merchantName: String? = null,
        senderAccount: String? = null
    ): VerificationResult {
        val detectedProvider = provider
            ?: if (transactionId.lowercase().contains("cs.bankofabyssinia.com/slip/?trx=")) "boa" else null
        require(!detectedProvider.isNullOrEmpty()) {
            "provider is required for ambiguous transaction references. Pass provider explicitly or use quickVerify()."
        }
        
        val params = mutableMapOf(
            "provider" to detectedProvider,
            "transaction_id" to transactionId,
            "amount" to amount.toString(),
            "merchant_name" to (merchantName ?: "ShegerPay Verification")
        )
        if (!senderAccount.isNullOrEmpty()) {
            params["sender_account"] = senderAccount
        }
        
        return request("POST", "/api/v1/verify", params)
    }
    
    /**
     * Quick verification with auto-detected provider
     */
    fun quickVerify(
        transactionId: String,
        amount: Double,
        expectedProvider: String? = null,
        senderAccount: String? = null
    ): VerificationResult {
        val params = mutableMapOf(
            "transaction_id" to transactionId,
            "amount" to amount.toString()
        )
        if (!expectedProvider.isNullOrEmpty()) {
            params["expected_provider"] = expectedProvider
        }
        if (!senderAccount.isNullOrEmpty()) {
            params["sender_account"] = senderAccount
        }
        return request("POST", "/api/v1/quick-verify", params)
    }
    
    private fun request(method: String, path: String, params: Map<String, String>): VerificationResult {
        val url = URL("$baseUrl$path")
        val conn = url.openConnection() as HttpURLConnection
        
        conn.requestMethod = method
        conn.setRequestProperty("X-API-Key", apiKey)
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.setRequestProperty("User-Agent", "ShegerPay-Kotlin-SDK/1.0")
        conn.connectTimeout = 30000
        conn.readTimeout = 30000
        
        if (method == "POST") {
            conn.doOutput = true
            val postData = params.entries.joinToString("&") { 
                "${it.key}=${java.net.URLEncoder.encode(it.value, "UTF-8")}" 
            }
            conn.outputStream.write(postData.toByteArray())
        }
        
        val responseCode = conn.responseCode
        
        if (responseCode == 401) {
            throw AuthenticationException("Invalid API key")
        }
        
        val response = if (responseCode >= 400) {
            conn.errorStream.bufferedReader().readText()
        } else {
            conn.inputStream.bufferedReader().readText()
        }
        
        return Json.decodeFromString(response)
    }
    
    companion object {
        /**
         * Verify webhook signature
         */
        fun verifyWebhookSignature(payload: String, signature: String, secret: String): Boolean {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
            val hash = mac.doFinal(payload.toByteArray())
            val expected = "sha256=" + hash.joinToString("") { "%02x".format(it) }
            return expected == signature
        }
    }
}

// Extension for coroutines (optional)
suspend fun ShegerPay.verifyAsync(
    transactionId: String, 
    amount: Double
): VerificationResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    verify(transactionId, amount)
}
