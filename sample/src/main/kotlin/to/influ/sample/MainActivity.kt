package to.influ.sample

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import to.influ.sdk.InfluTo
import to.influ.sdk.InfluToConfig
import to.influ.sdk.billing.InfluToBilling
import to.influ.sdk.ui.InfluToReferralCodeInput

/**
 * The full InfluTo Android flow, top to bottom — a copy-paste reference:
 * 1 configure → 2 attribution → 3 referral input → 4 paywall (Play Billing v9
 * purchase → reportPurchase) → 5 confirm it landed in InfluTo.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { InfluToSampleScreen() } }
    }
}

@Composable
fun InfluToSampleScreen() {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    val baseUrl = "https://influ.to/api"

    // API key defaults to the gitignored BuildConfig value (local.properties), but is
    // editable at runtime — nothing real is committed.
    var apiKey by remember { mutableStateOf(BuildConfig.INFLUTO_API_KEY) }
    var productId by remember { mutableStateOf("to.influ.sample.pro.monthly") }
    var appUserId by remember { mutableStateOf("sample-android") }
    var initialized by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Not initialized") }
    var attribution by remember { mutableStateOf("—") }
    var appliedCode by remember { mutableStateOf<String?>(null) }
    var purchaseResult by remember { mutableStateOf("") }
    var landed by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var autoSync by remember { mutableStateOf("") }

    val billing = remember {
        BillingManager(
            context = context,
            scope = scope,
            onPurchaseToken = { token ->
                try {
                    val p = InfluTo.reportPurchase(
                        purchaseToken = token, appUserId = appUserId, referralCode = appliedCode
                    )
                    purchaseResult = if (p.success) {
                        "✅ reported · validated=${p.validated} env=${p.environment}"
                    } else {
                        "⚠️ reportPurchase returned success=false"
                    }
                } catch (e: Throwable) {
                    purchaseResult = "❌ ${e.message}"
                }
            },
            onLog = { purchaseResult = it },
        )
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("InfluTo Sample", style = MaterialTheme.typography.headlineSmall)

        SectionTitle("1 · Configuration")
        OutlinedTextField(apiKey, { apiKey = it }, label = { Text("InfluTo API key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(productId, { productId = it }, label = { Text("Product ID (SKU)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(appUserId, { appUserId = it }, label = { Text("App user ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            scope.launch {
                try {
                    InfluTo.initialize(
                        context,
                        InfluToConfig(apiKey = apiKey, debug = true, apiUrl = baseUrl, appVersion = "sample-android-1.0"),
                    )
                    initialized = true
                    status = "✅ Initialized as $appUserId"
                    InfluTo.identifyUser(appUserId)
                    val a = InfluTo.checkAttribution()
                    attribution = if (a.attributed) "Attributed → ${a.referralCode}" else "Organic (no attribution link)"
                    appliedCode = InfluTo.getReferralCode()
                } catch (e: Throwable) {
                    initialized = false
                    status = "❌ Init failed: ${e.message}"
                }
            }
        }) { Text(if (initialized) "Re-initialize" else "Initialize SDK") }
        Text(status, style = MaterialTheme.typography.bodySmall)

        if (initialized) {
            SectionTitle("2 · Attribution")
            Text("Attribution: $attribution")
            appliedCode?.let { Text("Stored code: $it") }

            SectionTitle("3 · Referral code (test attribution)")
            Text("A · Prebuilt SDK component", style = MaterialTheme.typography.labelMedium)
            InfluToReferralCodeInput(
                appUserId = appUserId,
                onApplied = { appliedCode = InfluTo.getReferralCode() },
            )
            Spacer(Modifier.height(8.dp))
            Text("B · Custom (build your own UI)", style = MaterialTheme.typography.labelMedium)
            ReferralCodeField(appUserId) { appliedCode = it }

            SectionTitle("4 · Paywall")
            Text(
                "Buys via Play Billing v9, then calls InfluTo.reportPurchase with the purchaseToken. " +
                    "Real purchases need a Play Console test track + license tester.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = {
                purchaseResult = "Connecting to Play Billing…"
                scope.launch {
                    if (billing.connect()) billing.launchPurchase(activity, productId)
                    else purchaseResult = "Billing connect failed (Play services / test track?)."
                }
            }) { Text("Buy $productId") }

            SectionTitle("5 · Result")
            if (purchaseResult.isNotEmpty()) Text(purchaseResult)
            Button(
                onClick = {
                    checking = true
                    scope.launch {
                        landed = SampleBackend.recentConversionsSummary(baseUrl, apiKey, appUserId)
                        checking = false
                    }
                },
                enabled = !checking,
            ) { Text("Did it land in InfluTo?") }
            if (checking) CircularProgressIndicator(Modifier.size(20.dp))
            if (landed.isNotEmpty()) Text(landed, style = MaterialTheme.typography.bodySmall)

            SectionTitle("6 · Auto-capture (default)")
            Text(
                "For store-direct apps, adding the to.influ:android-sdk-billing artifact makes the " +
                    "SDK auto-report purchases on init (no manual reportPurchase). This button runs " +
                    "an on-demand back-sync.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = {
                autoSync = "Syncing…"
                scope.launch {
                    val r = InfluToBilling.syncExistingPurchases(context)
                    autoSync = "fetched=${r.fetched} · sent=${r.sent} · failed=${r.failed}"
                }
            }) { Text("Back-sync existing purchases") }
            if (autoSync.isNotEmpty()) Text(autoSync, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(text, style = MaterialTheme.typography.titleMedium)
}
