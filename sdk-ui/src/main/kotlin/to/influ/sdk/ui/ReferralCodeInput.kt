package to.influ.sdk.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import to.influ.sdk.CodeErrorCode
import to.influ.sdk.CodeValidationResult
import to.influ.sdk.InfluTo

private enum class RefState { Idle, Validating, Valid, Invalid, Applied }

/**
 * Pre-built, configurable referral-code input (Jetpack Compose).
 *
 * Validates live (debounced) as the user types, applies on the button. By default it
 * shows ONLY the field + a valid/invalid state — the campaign name and the influencer's
 * personal name are hidden unless [showCampaignName] / [showReferrerName] are set (both
 * default `false`, consistent across the InfluTo SDKs). For full control, build your own
 * UI and call `InfluTo.validateCode` / `InfluTo.applyCode` directly.
 */
@Composable
fun InfluToReferralCodeInput(
    modifier: Modifier = Modifier,
    appUserId: String? = null,
    autoPrefill: Boolean = true,
    autoValidate: Boolean = false,
    showCampaignName: Boolean = false,
    showReferrerName: Boolean = false,
    title: String? = null,
    placeholder: String = "Referral code",
    applyLabel: String = "Apply",
    validMessage: String = "Code applied",
    invalidMessage: String = "This code isn't valid",
    onValidated: (CodeValidationResult) -> Unit = {},
    onApplied: (CodeValidationResult) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var state by remember { mutableStateOf(RefState.Idle) }
    var result by remember { mutableStateOf<CodeValidationResult?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(Unit) {
        if (autoPrefill) {
            InfluTo.getPrefilledCode()?.let { prefilled ->
                code = prefilled
                if (autoValidate && prefilled.length >= 3) {
                    state = RefState.Validating
                    val r = InfluTo.validateCode(prefilled)
                    result = r
                    onValidated(r)
                    state = if (r.valid) RefState.Valid else RefState.Invalid
                }
            }
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            value = code,
            onValueChange = { raw ->
                val normalized = raw.uppercase().filter { it.isLetterOrDigit() || it == '-' }
                code = normalized
                job?.cancel()
                if (normalized.length < 3) {
                    state = RefState.Idle
                } else {
                    state = RefState.Validating
                    job = scope.launch {
                        delay(450) // debounce
                        val r = InfluTo.validateCode(normalized)
                        result = r
                        onValidated(r)
                        state = if (r.valid) RefState.Valid else RefState.Invalid
                    }
                }
            },
            label = { Text(placeholder) },
            singleLine = true,
            enabled = state != RefState.Applied,
            isError = state == RefState.Invalid,
            supportingText = {
                when (state) {
                    RefState.Valid, RefState.Applied -> Text(validMessage)
                    RefState.Invalid -> Text(
                        if (result?.errorCode == CodeErrorCode.CODE_EXPIRED) "This code has expired"
                        else (result?.error ?: invalidMessage),
                    )
                    else -> {}
                }
            },
            trailingIcon = {
                if (state == RefState.Validating) CircularProgressIndicator(Modifier.size(18.dp))
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = {
                scope.launch {
                    val r = InfluTo.applyCode(code, appUserId)
                    result = r
                    if (r.applied == true || r.valid) {
                        onApplied(r)
                        state = RefState.Applied
                    } else {
                        state = RefState.Invalid
                    }
                }
            },
            enabled = state == RefState.Valid,
        ) { Text(applyLabel) }

        if (state == RefState.Applied && showCampaignName) {
            result?.campaign?.name?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
        if (state == RefState.Applied && showReferrerName) {
            result?.influencer?.let {
                Text("Referred by ${it.name}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
