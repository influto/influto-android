# InfluTo Android SDK

Influencer attribution + store-direct purchase validation for Android. No third-party
dependencies beyond kotlinx-coroutines (the standard Kotlin async runtime) — networking is
plain HttpURLConnection + `org.json` + SharedPreferences. Mirrors the
[InfluTo React Native SDK](https://github.com/influto/influto-react-native) and the
[canonical contract](./CONTRACT.md).

- **minSdk** 24 · **Kotlin** 2.2 · coroutines (`suspend`) + `Result`-callback overloads
- **Maven coordinates:** `to.influ:android-sdk:1.0.0`

## Prerequisites

You need a free **InfluTo account** — sign up at [https://influ.to](https://influ.to), create your
app in the dashboard, and copy your API key (it starts with `it_`). For store-direct purchase
validation / auto-capture, also add your Google Play store credentials to the app in the dashboard.

## Install

```kotlin
// settings.gradle.kts → dependencyResolutionManagement { repositories { mavenCentral() } }
dependencies {
    implementation("to.influ:android-sdk:1.0.0")
}
```

### Optional artifacts

Two add-on modules ship as separate coordinates (each transitively pulls in the core SDK).
Add them only if you need them:

```kotlin
dependencies {
    // Compose UI components (e.g. the debounced InfluToReferralCodeInput):
    implementation("to.influ:android-sdk-ui:1.0.0")

    // Play Billing auto-capture (store-direct apps only). When this artifact is on the
    // classpath, `initialize` reflectively starts purchase auto-capture — no manual
    // reportPurchase calls needed. The core `:sdk` itself has NO Play Billing dependency.
    implementation("to.influ:android-sdk-billing:1.0.0")
}
```

### Auto-capture config (`InfluToConfig`)

For store-direct apps using `android-sdk-billing`, two `InfluToConfig` options control capture:

- `autoCapture: Boolean` (default `true`) — reflectively starts Play Billing purchase
  auto-capture when the `android-sdk-billing` artifact is present. It activates only when the
  backend reports the app is store-direct; RevenueCat apps stay silent / unaffected. Set
  `false` to manage purchase reporting yourself via `reportPurchase`.
- `oneTimeProductIds: Set<String>` (default empty) — declare your one-time / consumable Play
  SKUs. A Play `Purchase` carries no product TYPE, so listing the ids here lets auto-capture
  route those purchases to NON_RENEWING validation instead of subscription. Omit if you only
  sell subscriptions.

```kotlin
InfluTo.initialize(context, InfluToConfig(
    apiKey = "it_live_...",
    autoCapture = true,                          // default; store-direct apps only
    oneTimeProductIds = setOf("coins_pack_500"), // route one-time SKUs to NON_RENEWING
))
```

## Quick start

```kotlin
import to.influ.sdk.InfluTo
import to.influ.sdk.InfluToConfig

// 1. Initialize once (e.g. in Application.onCreate or your first screen).
lifecycleScope.launch {
    InfluTo.initialize(context, InfluToConfig(
        apiKey = "it_live_...",
        debug = BuildConfig.DEBUG,
        // OPTIONAL: wire RevenueCat (no hard dependency). The SDK calls this with
        // {influto_code, influto_referral:"true"} on attribution / setReferralCode.
        revenueCatAttributeSetter = { attrs -> Purchases.sharedInstance.setAttributes(attrs) },
    ))

    // 2. Resolve install attribution.
    val attr = InfluTo.checkAttribution()
    if (attr.attributed) Log.d("App", "Referred by ${attr.referralCode}")

    // 3. Identify + track.
    InfluTo.identifyUser("user_123")
    InfluTo.trackEvent(eventType = "paywall_viewed", appUserId = "user_123")
}

// 4. Manual promo-code entry.
val result = InfluTo.applyCode("FITGURU30", appUserId = "user_123")  // suspend

// 5. Store-direct purchase (only if NOT using RevenueCat). In your
//    PurchasesUpdatedListener, after acknowledging the purchase:
InfluTo.reportPurchase(
    purchaseToken = purchase.purchaseToken,   // Play Billing Purchase.getPurchaseToken()
    appUserId = "user_123",
)
```

> **Attribution binding:** when launching the Play Billing flow, set
> `BillingFlowParams.Builder().setObfuscatedAccountId(hashedAppUserId)` (a hashed/opaque id,
> never PII) so renewals/refunds stay linked to the user server-side. Always let the SDK send
> the stored `influto_code` (the default) on the first purchase so the conversion binds to the
> referral.

Every network method also has a `Result`-callback overload for Java / non-coroutine call sites:
`InfluTo.checkAttribution { result -> ... }`.

## Build & test (Windows host or WSL2 — no Mac needed)

Builds with JDK 17 + the Android SDK. The Gradle wrapper (Gradle 8.14.3) is already committed,
and `:sdk` pins `buildToolsVersion = "35.0.0"`. A `:sample` Compose app module is included.
Point `local.properties` at your Linux Android SDK (`sdk.dir=/opt/android-sdk`) if the
`ANDROID_HOME` env var points elsewhere.

### Configure your API key (to run the sample against the backend)

The sample reads its InfluTo API key from the **gitignored** `local.properties`, so a real
key is never committed (it defaults to a harmless `it_TEST_KEY`, which the backend rejects
with 401). To test for real, add your key (from the dashboard → your app → API key):

```properties
# sdks/android/local.properties  (gitignored — never committed)
sdk.dir=/opt/android-sdk
influto.apiKey=it_live_your_real_key_here
```

Then rebuild + reinstall: `./gradlew :sample:assembleDebug && adb install -r sample/build/outputs/apk/debug/sample-debug.apk`.

```bash
# Build the AAR + run the JVM unit tests:
./gradlew :sdk:assembleRelease :sdk:test

# (optional) publish the AAR to local Maven for external consumption:
./gradlew :sdk:publishToMavenLocal       # -> ~/.m2/.../to/influ/android-sdk/1.0.0/

# Build + install the included sample app on a device / AVD:
./gradlew :sample:assembleDebug
adb devices
adb install -r sample/build/outputs/apk/debug/sample-debug.apk
```

### Sample app

A runnable Compose sample lives in [`sample/`](sample) — buttons for
init → checkAttribution → validateCode → identify + trackEvent → reportPurchase. The screen
(`sample/src/main/kotlin/to/influ/sample/MainActivity.kt`):

```kotlin
@Composable
fun InfluToDemo() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var log by remember { mutableStateOf("") }
    fun line(s: String) { log += "$s\n" }

    Column(Modifier.padding(16.dp)) {
        Button(onClick = { scope.launch {
            InfluTo.initialize(ctx, InfluToConfig(apiKey = "it_TEST_KEY", debug = true))
            val a = InfluTo.checkAttribution()
            line("init ok · attributed=${a.attributed} code=${a.referralCode}")
        }}) { Text("1. init + checkAttribution") }

        Button(onClick = { scope.launch {
            val v = InfluTo.validateCode("FITGURU30"); line("valid=${v.valid} ${v.campaign?.name ?: v.error}")
        }}) { Text("2. validateCode") }

        Button(onClick = { scope.launch {
            InfluTo.identifyUser("user_123")
            InfluTo.trackEvent("paywall_viewed", "user_123", mapOf("screen" to "onboarding"))
            line("identify + trackEvent sent")
        }}) { Text("3. identify + trackEvent") }

        Button(onClick = { /* host BillingClient → on success: */ scope.launch {
            // val p = InfluTo.reportPurchase(purchaseToken = token, appUserId = "user_123")
            // line("purchase success=${p.success} validated=${p.validated} env=${p.environment}")
        } }) { Text("4. reportPurchase (Play Billing test)") }

        Spacer(Modifier.height(12.dp)); Text(log)
    }
}
```

### Play Billing test purchase (for step 4)

1. Add your Google account under **Play Console → Setup → License testing**.
2. Upload an internal-testing build + create a subscription product.
3. Complete a test purchase; pass `purchase.purchaseToken` to `reportPurchase`. The backend must
   have `android_validation_provider = "google"` + service-account creds for the app, else
   `/sdk/purchase` returns 400 (handled gracefully).

## Verify on the backend

After a sample run, `GET /api/apps/{id}/events/recent`:
- `sdk_events[]` shows each `trackEvent` **once** (dedup) with the right `referral_code` + `platform`;
- `webhooks[]` shows the purchase with `"attributed": true` + the matching `referral_code`.

## Publish (Maven Central via Central Portal)

OSSRH is dead — publishing targets the Sonatype **Central Portal** via the vanniktech plugin.
Put `mavenCentralUsername`/`mavenCentralPassword` (Portal token) + `signingInMemoryKey`
(GPG) in `~/.gradle/gradle.properties`, verify the `to.influ` namespace once, then:

```bash
./gradlew :sdk:publishToMavenCentral
```
