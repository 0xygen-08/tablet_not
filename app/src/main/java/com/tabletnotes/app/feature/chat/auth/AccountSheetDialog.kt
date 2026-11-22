package com.tabletnotes.app.feature.chat.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.tabletnotes.app.R
import com.tabletnotes.app.core.designsystem.theme.TabletNotesTheme
import com.tabletnotes.app.domain.model.AccountAuthState
import com.tabletnotes.app.domain.model.AvatarPreset
import com.tabletnotes.app.domain.model.UsernameValidator

@Composable
fun UserAvatarBadge(
    displayName: String,
    avatarPresetId: String,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    val preset = AvatarPreset.fromId(avatarPresetId)
    val bgColor = Color(preset.colorArgb)
    val initials = remember(displayName) {
        displayName.trim()
            .split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifBlank { "?" }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        when (preset.iconKey) {
            "star" -> Icon(
                imageVector = Icons.Default.Star,
                contentDescription = displayName,
                tint = Color.White,
                modifier = Modifier.size(size * 0.55f)
            )
            "bolt" -> Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = displayName,
                tint = Color.White,
                modifier = Modifier.size(size * 0.55f)
            )
            else -> Text(
                text = initials,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
        }
    }
}

@Composable
fun AccountSheetDialog(
    authState: AccountAuthState,
    isBusy: Boolean,
    errorMessageRes: Int?,
    infoMessageRes: Int?,
    onDismiss: () -> Unit,
    onSignInEmail: (String, String) -> Unit,
    onSignUpEmail: (email: String, password: String, displayName: String, username: String, avatarPreset: String) -> Unit,
    onGoogleIdToken: (String) -> Unit,
    onGoogleSignInError: (Int) -> Unit,
    onCompleteProfileSetup: (displayName: String, username: String, avatarPreset: String) -> Unit,
    onUpdateProfile: (displayName: String, username: String, avatarPreset: String) -> Unit,
    onSendPasswordReset: (String) -> Unit,
    onSignOut: () -> Unit,
    onClearFeedback: () -> Unit
) {
    val palette = TabletNotesTheme.palette
    val context = LocalContext.current

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val token = account.idToken
                if (!token.isNullOrBlank()) {
                    onGoogleIdToken(token)
                } else {
                    onGoogleSignInError(R.string.auth_error_google_not_configured)
                }
            } catch (_: Exception) {
                onGoogleSignInError(R.string.auth_error_google_not_configured)
            }
        }
    }

    fun launchGoogleSignIn() {
        onClearFeedback()
        val clientIdRes = context.resources.getIdentifier(
            "default_web_client_id",
            "string",
            context.packageName
        )
        if (clientIdRes == 0) {
            onGoogleSignInError(R.string.auth_error_google_not_configured)
            return
        }
        val webClientId = context.getString(clientIdRes)
        if (webClientId.isBlank()) {
            onGoogleSignInError(R.string.auth_error_google_not_configured)
            return
        }
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        val client = GoogleSignIn.getClient(context, gso)
        googleSignInLauncher.launch(client.signInIntent)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = palette.surfaceElevated,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val titleRes = when (authState) {
                    is AccountAuthState.SignedIn -> R.string.auth_title_profile
                    is AccountAuthState.NeedsProfileSetup -> R.string.auth_title_complete_profile
                    else -> R.string.auth_title_sign_in
                }
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = palette.textPrimary
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.editor_json_debug_close),
                        tint = palette.textMuted
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .widthIn(min = 380.dp, max = 500.dp)
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (errorMessageRes != null) {
                    FeedbackBanner(
                        message = stringResource(errorMessageRes),
                        isError = true
                    )
                }
                if (infoMessageRes != null) {
                    FeedbackBanner(
                        message = stringResource(infoMessageRes),
                        isError = false
                    )
                }

                when (authState) {
                    AccountAuthState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = palette.primaryAccent)
                        }
                    }

                    AccountAuthState.SignedOut -> {
                        SignedOutAuthForm(
                            isBusy = isBusy,
                            onSignInEmail = onSignInEmail,
                            onSignUpEmail = onSignUpEmail,
                            onGoogleClick = ::launchGoogleSignIn,
                            onForgotPassword = onSendPasswordReset,
                            onClearFeedback = onClearFeedback
                        )
                    }

                    is AccountAuthState.NeedsProfileSetup -> {
                        ProfileEditorSection(
                            initialDisplayName = authState.suggestedDisplayName,
                            initialUsername = "",
                            initialAvatarPreset = AvatarPreset.COBALT_INITIALS.id,
                            authEmail = authState.authEmail,
                            isBusy = isBusy,
                            submitLabelRes = R.string.auth_btn_create_account,
                            onSubmit = onCompleteProfileSetup
                        )
                    }

                    is AccountAuthState.SignedIn -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            UserAvatarBadge(
                                displayName = authState.profile.displayName,
                                avatarPresetId = authState.profile.avatarPreset,
                                size = 50.dp
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = authState.profile.displayName,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = palette.textPrimary
                                )
                                Text(
                                    text = "@${authState.profile.username}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.primaryAccent
                                )
                                if (!authState.authEmail.isNullOrBlank()) {
                                    Text(
                                        text = authState.authEmail,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = palette.textMuted
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = palette.borderSubtle)

                        ProfileEditorSection(
                            initialDisplayName = authState.profile.displayName,
                            initialUsername = authState.profile.username,
                            initialAvatarPreset = authState.profile.avatarPreset,
                            authEmail = null,
                            isBusy = isBusy,
                            submitLabelRes = R.string.auth_btn_save_profile,
                            onSubmit = onUpdateProfile
                        )

                        // Faz 2 Test Sürümü Bildirimi
                        Surface(
                            color = palette.surface,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, palette.borderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = palette.primaryAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(
                                        text = stringResource(R.string.auth_test_version_notice_title),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = palette.textPrimary
                                    )
                                    Text(
                                        text = stringResource(R.string.auth_test_version_notice_body),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = palette.textMuted
                                    )
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = onSignOut,
                            enabled = !isBusy,
                            shape = CircleShape,
                            border = BorderStroke(1.dp, palette.secondaryAccent),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.auth_btn_sign_out),
                                color = palette.secondaryAccent
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (authState is AccountAuthState.NeedsProfileSetup) {
                TextButton(onClick = onSignOut) {
                    Text(stringResource(R.string.auth_btn_sign_out))
                }
            }
        }
    )
}

@Composable
private fun SignedOutAuthForm(
    isBusy: Boolean,
    onSignInEmail: (String, String) -> Unit,
    onSignUpEmail: (String, String, String, String, String) -> Unit,
    onGoogleClick: () -> Unit,
    onForgotPassword: (String) -> Unit,
    onClearFeedback: () -> Unit
) {
    val palette = TabletNotesTheme.palette
    var isSignUpMode by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var usernameInput by rememberSaveable { mutableStateOf("") }
    var selectedAvatarId by rememberSaveable { mutableStateOf(AvatarPreset.COBALT_INITIALS.id) }

    val normalizedUsername = UsernameValidator.normalize(usernameInput)
    val isUsernameFormatOk = UsernameValidator.isValid(normalizedUsername)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Giriş Yap / Kayıt Ol Pill Sekmesi
        Surface(
            shape = CircleShape,
            color = palette.surface,
            border = BorderStroke(1.dp, palette.borderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(false to R.string.auth_tab_sign_in, true to R.string.auth_tab_sign_up).forEach { (signUpTab, labelRes) ->
                    val selected = isSignUpMode == signUpTab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(if (selected) palette.primaryAccent else Color.Transparent)
                            .clickable {
                                isSignUpMode = signUpTab
                                onClearFeedback()
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (selected) palette.onPrimaryAccent else palette.textSecondary
                        )
                    }
                }
            }
        }

        if (isSignUpMode) {
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text(stringResource(R.string.auth_display_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = usernameInput,
                onValueChange = { usernameInput = UsernameValidator.normalize(it).take(20) },
                label = { Text(stringResource(R.string.auth_username_label)) },
                supportingText = { Text(stringResource(R.string.auth_username_hint)) },
                prefix = { Text("@") },
                isError = usernameInput.isNotEmpty() && !isUsernameFormatOk,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            AvatarPresetSelector(
                displayName = displayName,
                selectedAvatarId = selectedAvatarId,
                onSelect = { selectedAvatarId = it }
            )
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(R.string.auth_email_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.auth_password_label)) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (!isSignUpMode) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = { onForgotPassword(email) },
                    enabled = !isBusy
                ) {
                    Text(stringResource(R.string.auth_btn_forgot_password), style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        Button(
            onClick = {
                if (isSignUpMode) {
                    onSignUpEmail(email, password, displayName, normalizedUsername, selectedAvatarId)
                } else {
                    onSignInEmail(email, password)
                }
            },
            enabled = !isBusy && email.isNotBlank() && password.length >= 6 &&
                (!isSignUpMode || (displayName.isNotBlank() && isUsernameFormatOk)),
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        ) {
            if (isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = palette.onPrimaryAccent,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    stringResource(
                        if (isSignUpMode) R.string.auth_btn_create_account else R.string.auth_btn_sign_in
                    )
                )
            }
        }

        HorizontalDivider(color = palette.borderSubtle, modifier = Modifier.padding(vertical = 2.dp))

        OutlinedButton(
            onClick = onGoogleClick,
            enabled = !isBusy,
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(19.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.auth_btn_google_sign_in))
        }
    }
}

@Composable
private fun ProfileEditorSection(
    initialDisplayName: String,
    initialUsername: String,
    initialAvatarPreset: String,
    authEmail: String?,
    isBusy: Boolean,
    submitLabelRes: Int,
    onSubmit: (displayName: String, username: String, avatarPreset: String) -> Unit
) {
    val palette = TabletNotesTheme.palette
    var displayName by rememberSaveable(initialDisplayName) { mutableStateOf(initialDisplayName) }
    var usernameInput by rememberSaveable(initialUsername) { mutableStateOf(initialUsername) }
    var selectedAvatarId by rememberSaveable(initialAvatarPreset) { mutableStateOf(initialAvatarPreset) }

    val normalizedUsername = UsernameValidator.normalize(usernameInput)
    val isUsernameFormatOk = UsernameValidator.isValid(normalizedUsername)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!authEmail.isNullOrBlank()) {
            Text(
                text = authEmail,
                style = MaterialTheme.typography.labelSmall,
                color = palette.textMuted
            )
        }

        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text(stringResource(R.string.auth_display_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = usernameInput,
            onValueChange = { usernameInput = UsernameValidator.normalize(it).take(20) },
            label = { Text(stringResource(R.string.auth_username_label)) },
            supportingText = { Text(stringResource(R.string.auth_username_hint)) },
            prefix = { Text("@") },
            isError = usernameInput.isNotEmpty() && !isUsernameFormatOk,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        AvatarPresetSelector(
            displayName = displayName,
            selectedAvatarId = selectedAvatarId,
            onSelect = { selectedAvatarId = it }
        )

        Button(
            onClick = { onSubmit(displayName, normalizedUsername, selectedAvatarId) },
            enabled = !isBusy && displayName.isNotBlank() && isUsernameFormatOk,
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
        ) {
            if (isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = palette.onPrimaryAccent,
                    strokeWidth = 2.dp
                )
            } else {
                Text(stringResource(submitLabelRes))
            }
        }
    }
}

@Composable
private fun AvatarPresetSelector(
    displayName: String,
    selectedAvatarId: String,
    onSelect: (String) -> Unit
) {
    val palette = TabletNotesTheme.palette
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.auth_avatar_picker_label),
            style = MaterialTheme.typography.labelMedium,
            color = palette.textSecondary
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AvatarPreset.entries.forEach { preset ->
                val isSelected = preset.id == selectedAvatarId
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .border(
                            width = if (isSelected) 3.dp else 0.dp,
                            color = if (isSelected) palette.primaryAccent else Color.Transparent,
                            shape = CircleShape
                        )
                        .padding(3.dp)
                        .clickable { onSelect(preset.id) },
                    contentAlignment = Alignment.Center
                ) {
                    UserAvatarBadge(
                        displayName = displayName,
                        avatarPresetId = preset.id,
                        size = 38.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedbackBanner(
    message: String,
    isError: Boolean
) {
    val palette = TabletNotesTheme.palette
    val accent = if (isError) palette.secondaryAccent else palette.statusReadyEmerald
    Surface(
        color = accent.copy(alpha = 0.14f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, accent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = palette.textPrimary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        )
    }
}

@Composable
fun SignedOutChatGateCard(
    onOpenAuthDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = TabletNotesTheme.palette
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = palette.surfaceElevated,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, palette.borderSubtle),
            modifier = Modifier
                .widthIn(max = 460.dp)
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(palette.primaryAccent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = palette.primaryAccent,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Text(
                    text = stringResource(R.string.auth_signed_out_gate_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = palette.textPrimary,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = stringResource(R.string.auth_signed_out_gate_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary,
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = onOpenAuthDialog,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.primaryAccent,
                        contentColor = palette.onPrimaryAccent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Text(stringResource(R.string.auth_signed_out_gate_cta))
                }
            }
        }
    }
}

