<p align="center"><img src="logo.png" alt="ShegerPay" width="200" /></p>

# ShegerPay Kotlin SDK

[![Version](https://img.shields.io/badge/version-2.2.0-blue)](https://search.maven.org/artifact/com.shegerpay/sdk-kotlin)
[![License](https://img.shields.io/badge/license-MIT-green)](LICENSE)

Official Kotlin SDK for ShegerPay — verify Ethiopian bank payments (CBE, Telebirr, BOA, Awash).

## Install

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.shegerpay:sdk-kotlin:2.2.0")
}
```

## Quick Start

```kotlin
import com.shegerpay.ShegerPay

val client = ShegerPay("sk_live_YOUR_API_KEY")

// In a coroutine or suspend function:

// Verify a payment
val result = client.verify("FT26062K7WMY", provider = "cbe", amount = 1000.0)
println(result["verified"]) // true/false

// Verify without amount (lookup only)
val result2 = client.verify("FT26062K7WMY", provider = "telebirr")
println(result2["status"])

// Verify from receipt screenshot
val imageBase64 = Base64.getEncoder().encodeToString(File("receipt.png").readBytes())
val imgResult = client.verifyImage(imageBase64, provider = "cbe")
println(imgResult["verified"])

// Create payment link
val link = client.createPaymentLink(mapOf(
    "title" to "Order #1234",
    "amount" to 1500,
    "currency" to "ETB"
))
println(link["url"])
```

**In an Android ViewModel:**
```kotlin
viewModelScope.launch {
    val result = client.verify(transactionId, provider = "cbe", amount = amount)
    if (result["verified"] == true) { /* success */ }
}
```

## Supported Providers
`cbe` · `telebirr` · `boa` · `awash` · `ebirr_kaafi` · `ebirr_coop`

## Requirements
- Kotlin 1.8+
- Kotlin Coroutines


## Support
- 📚 Docs: https://shegerpay.com/docs
- 💬 Telegram: [@shegerpay_0](https://t.me/shegerpay_0)
- 📧 Email: support@shegerpay.com
