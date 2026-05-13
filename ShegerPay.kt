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
import java.security.MessageDigest
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

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

    fun createPromoCode(params: Map<String, Any?>): Map<String, JsonElement> {
        return requestJson("POST", "/api/v1/promo-codes/", promoPayload(params))
    }

    fun listPromoCodes(): JsonElement {
        return requestJsonElement("GET", "/api/v1/promo-codes/", emptyMap())
    }

    fun updatePromoCode(codeId: String, params: Map<String, Any?>): Map<String, JsonElement> {
        return requestJson("PATCH", "/api/v1/promo-codes/$codeId", promoPayload(params))
    }

    fun deletePromoCode(codeId: String): Map<String, JsonElement> {
        return requestJson("DELETE", "/api/v1/promo-codes/$codeId", emptyMap())
    }

    fun validatePromoCode(code: String, amount: Double, options: Map<String, Any?> = emptyMap()): Map<String, JsonElement> {
        val payload = mutableMapOf<String, Any?>("code" to code, "amount" to amount)
        payload.putAll(options)
        return requestJson("POST", "/api/v1/promo-codes/validate", payload)
    }

    fun redeemPromoCode(code: String, amount: Double, transactionId: String, options: Map<String, Any?> = emptyMap()): Map<String, JsonElement> {
        val payload = mutableMapOf<String, Any?>(
            "code" to code,
            "amount" to amount,
            "transaction_id" to transactionId
        )
        payload.putAll(options)
        return requestJson("POST", "/api/v1/promo-codes/redeem", payload)
    }

    fun applyPaymentLinkCoupon(shortCode: String, code: String, amount: Double? = null, quantity: Int = 1, provider: String? = null, customerIdentifier: String? = null): Map<String, JsonElement> {
        val payload = mutableMapOf<String, Any?>("code" to code, "quantity" to quantity)
        if (amount != null) payload["amount"] = amount
        if (provider != null) payload["provider"] = provider
        if (customerIdentifier != null) payload["customer_identifier"] = customerIdentifier
        return requestJson("POST", "/api/v1/payment-links/$shortCode/apply-coupon", payload)
    }

    fun getPaymentLinkOrderStatus(shortCode: String, orderId: String): Map<String, JsonElement> {
        require(shortCode.isNotBlank()) { "shortCode is required" }
        require(orderId.isNotBlank()) { "orderId is required" }
        return requestJson("GET", "/api/v1/payment-links/$shortCode/orders/$orderId/status", emptyMap())
    }

    private fun requestJson(method: String, path: String, payload: Map<String, Any?>): Map<String, JsonElement> {
        return requestJsonElement(method, path, payload).jsonObject
    }

    private fun requestJsonElement(method: String, path: String, payload: Map<String, Any?>): JsonElement {
        val url = URL("$baseUrl$path")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.setRequestProperty("X-API-Key", apiKey)
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("User-Agent", "ShegerPay-Kotlin-SDK/1.0")
        conn.connectTimeout = 30000
        conn.readTimeout = 30000

        if (method != "GET" && method != "DELETE") {
            conn.doOutput = true
            conn.outputStream.write(toJson(payload).toByteArray())
        }

        val responseCode = conn.responseCode
        if (responseCode == 204) return buildJsonObject {}
        if (responseCode == 401) throw AuthenticationException("Invalid API key")

        val response = if (responseCode >= 400) {
            conn.errorStream.bufferedReader().readText()
        } else {
            conn.inputStream.bufferedReader().readText()
        }
        if (responseCode >= 400) throw ShegerPayException(response)
        return Json.parseToJsonElement(response)
    }

    private fun promoPayload(params: Map<String, Any?>): Map<String, Any?> {
        return params.mapKeys { toSnakeCase(it.key) }
    }

    private fun toJson(params: Map<String, Any?>): String {
        return buildJsonObject {
            params.forEach { (key, value) ->
                when (value) {
                    null -> {}
                    is Number -> put(key, value.toDouble())
                    is Boolean -> put(key, value)
                    else -> put(key, value.toString())
                }
            }
        }.toString()
    }

    private fun toSnakeCase(value: String): String {
        return value.fold(StringBuilder()) { acc, ch ->
            if (ch.isUpperCase()) acc.append('_').append(ch.lowercaseChar()) else acc.append(ch)
        }.toString()
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
            return MessageDigest.isEqual(expected.toByteArray(), signature.toByteArray())
        }

        fun verifyRedirectSignature(params: Map<String, Any?>, signature: String, secret: String): Boolean {
            val amount = (params["amount"]?.toString()?.toDoubleOrNull() ?: 0.0).let { String.format(Locale.US, "%.2f", it) }
            val payload = listOf(
                params["checkout_session_id"] ?: params["checkoutSessionId"] ?: "",
                params["order_id"] ?: params["orderId"] ?: "",
                params["short_code"] ?: params["shortCode"] ?: "",
                amount,
                params["currency"] ?: "ETB",
                params["status"] ?: "paid"
            ).joinToString("|") { it.toString() }
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
            val expected = mac.doFinal(payload.toByteArray()).joinToString("") { "%02x".format(it) }
            val normalized = signature.removePrefix("sha256=")
            return MessageDigest.isEqual(expected.toByteArray(), normalized.toByteArray())
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
