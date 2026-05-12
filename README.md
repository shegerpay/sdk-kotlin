# ShegerPay Kotlin SDK

Official Kotlin SDK for [ShegerPay](https://shegerpay.com) — Ethiopian payment verification.

## Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.shegerpay:sdk-kotlin:2.2.0")
}
```

Or with Groovy `build.gradle`:

```groovy
dependencies {
    implementation 'com.shegerpay:sdk-kotlin:2.2.0'
}
```

## Quick Start

The SDK is built with Kotlin coroutines for non-blocking async operations:

```kotlin
import com.shegerpay.ShegerPay
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val client = ShegerPay("sk_live_...")

    val response = client.verify("txn_abc123")

    if (response.isSuccess) {
        println("Payment verified: ${response.transactionId}")
        println("Amount: ${response.amount}")
    } else {
        println("Verification failed: ${response.message}")
    }
}
```

In an existing coroutine scope (e.g. Android ViewModel):

```kotlin
viewModelScope.launch {
    val response = shegerPayClient.verify(transactionId)
    // handle response
}
```

## API Reference

### `ShegerPay(apiKey: String)`

Creates a new ShegerPay client.

| Parameter | Type   | Description        |
|-----------|--------|--------------------|
| `apiKey`  | String | Your secret API key |

### `suspend fun verify(transactionId: String): VerifyResponse`

Suspending function that verifies a payment transaction.

| Parameter       | Type   | Description              |
|-----------------|--------|--------------------------|
| `transactionId` | String | The transaction ID to verify |

Returns a `VerifyResponse` data class with:
- `isSuccess: Boolean` — whether the payment was successful
- `transactionId: String` — the transaction ID
- `amount: Long` — the verified amount in cents
- `message: String` — status message

## Requirements

- Kotlin 1.8+
- Kotlin Coroutines (`kotlinx-coroutines-core`)
- Android minSdk 21+ or JVM 11+

## License

MIT — see [LICENSE](LICENSE)
