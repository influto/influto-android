package to.influ.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import to.influ.sdk.CodeErrorCode
import to.influ.sdk.InfluTo

/**
 * Best-practice referral-code input (per the cross-platform UX spec): a collapsed
 * "Have a referral code?" disclosure → field (auto-uppercase, no autocorrect,
 * charset-filtered) → debounced live validation → explicit Apply → applied chip.
 * Never blocks organic users.
 */
@Composable
fun ReferralCodeField(appUserId: String, onApplied: (String?) -> Unit) {
    val scope = rememberCoroutineScope()
    var applied by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("idle") } // idle | validating | valid | invalid
    var info by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }

    when {
        applied != null -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text("✅ Code $applied applied")
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                InfluTo.clearAttribution()
                applied = null; onApplied(null); text = ""; state = "idle"; info = ""; expanded = false
            }) { Text("Remove") }
        }

        !expanded -> TextButton(onClick = { expanded = true }) { Text("Have a referral code?") }

        else -> Column(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = text,
                onValueChange = { raw ->
                    val normalized = raw.uppercase().filter { it.isLetterOrDigit() || it == '-' }
                    text = normalized
                    job?.cancel()
                    info = ""
                    if (normalized.length < 3) {
                        state = "idle"
                    } else {
                        state = "validating"
                        job = scope.launch {
                            delay(450) // debounce
                            val res = InfluTo.validateCode(normalized)
                            if (res.valid) {
                                state = "valid"
                                info = res.campaign?.name?.let { "Valid — $it" } ?: "Valid code"
                            } else {
                                state = "invalid"
                                info = if (res.errorCode == CodeErrorCode.CODE_EXPIRED) {
                                    "This code has expired"
                                } else {
                                    res.error ?: "This code isn't valid"
                                }
                            }
                        }
                    }
                },
                label = { Text("REFERRAL CODE") },
                singleLine = true,
                isError = state == "invalid",
                supportingText = { if (info.isNotEmpty()) Text(info) },
                trailingIcon = {
                    if (state == "validating") CircularProgressIndicator(Modifier.size(18.dp))
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    val code = text
                    scope.launch {
                        val res = InfluTo.applyCode(code, appUserId)
                        if (res.applied == true || res.valid) {
                            applied = code; onApplied(code); expanded = false
                        } else {
                            state = "invalid"; info = res.error ?: "Could not apply code"
                        }
                    }
                },
                enabled = state == "valid",
            ) { Text("Apply") }
        }
    }
}
