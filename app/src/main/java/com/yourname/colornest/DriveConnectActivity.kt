package com.yourname.colornest

import android.accounts.AccountManager
import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.services.drive.DriveScopes
import com.yourname.colornest.data.ColorNestDatabase
import com.yourname.colornest.data.drive.DriveSyncManager
import com.yourname.colornest.ui.theme.ColorNestTheme
import com.yourname.colornest.ui.theme.PurplePrimary
import kotlinx.coroutines.launch

/**
 * Shows an explicit Google Drive sign-in page. No Google account picker,
 * OAuth consent, or Drive sync is started until the user taps the button.
 */
class DriveConnectActivity : ComponentActivity() {
    companion object {
        const val EXTRA_PICK_ACCOUNT = "com.yourname.colornest.extra.PICK_DRIVE_ACCOUNT"
        private val DRIVE_SCOPES = listOf(DriveScopes.DRIVE_FILE, DriveScopes.DRIVE_READONLY)
    }

    private lateinit var driveManager: DriveSyncManager
    private var connectedAccount by mutableStateOf<String?>(null)
    private var busy by mutableStateOf(false)
    private var status by mutableStateOf("")
    private var shouldPickAccount by mutableStateOf(false)

    private val accountPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            busy = false
            status = "Sign-in cancelled. Google Drive has not been changed."
            return@registerForActivityResult
        }

        val accountName = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        if (accountName.isNullOrBlank()) {
            busy = false
            status = "Google did not return an account. Please try again."
            return@registerForActivityResult
        }

        lifecycleScope.launch {
            try {
                driveManager.connect(accountName)
                connectedAccount = accountName
                syncAndFinish()
            } catch (error: Exception) {
                busy = false
                status = "Could not connect Google Drive: ${error.localizedMessage ?: "Unknown error"}"
            }
        }
    }

    private val authorizationResolution = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            lifecycleScope.launch { syncAndFinish() }
        } else {
            busy = false
            status = "Google Drive permission was not granted. You can try again."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = ColorNestDatabase.getDatabase(applicationContext)
        driveManager = DriveSyncManager(
            applicationContext,
            database.pageDao(),
            database.categoryDao(),
            database.settingDao(),
            database.tagDao()
        )
        shouldPickAccount = intent.getBooleanExtra(EXTRA_PICK_ACCOUNT, false)

        setContent {
            ColorNestTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "Google Drive",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = PurplePrimary
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Connect ColorNest to Google Drive only when you choose. Your library stays on this device unless you sign in and use Drive.",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!connectedAccount.isNullOrBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Currently connected: $connectedAccount",
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = { beginUserRequestedConnection() },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                        ) {
                            if (busy) CircularProgressIndicator()
                            else Text(
                                if (connectedAccount.isNullOrBlank()) "Sign in with Google" else "Continue to Google Drive",
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (status.isNotBlank()) {
                            Spacer(Modifier.height(14.dp))
                            Text(status, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = { finish() }, enabled = !busy) { Text("Not now") }
                    }
                }
            }
        }

        lifecycleScope.launch {
            connectedAccount = driveManager.getAccountName()
        }
    }

    private fun beginUserRequestedConnection() {
        if (busy) return
        busy = true
        status = if (connectedAccount.isNullOrBlank()) {
            "Choose the Google account you want to use."
        } else {
            "Connecting to Google Drive…"
        }
        lifecycleScope.launch {
            if (shouldPickAccount || !driveManager.isConnected()) {
                launchAccountPicker()
            } else {
                syncAndFinish()
            }
        }
    }

    private fun launchAccountPicker() {
        val credential = GoogleAccountCredential.usingOAuth2(this, DRIVE_SCOPES)
        accountPicker.launch(credential.newChooseAccountIntent())
    }

    private suspend fun syncAndFinish() {
        status = "Syncing your ColorNest library with Google Drive…"
        try {
            val result = driveManager.sync()
            finishWithMessage(result.summary(), !result.notConnected)
        } catch (error: UserRecoverableAuthIOException) {
            status = "Approve ColorNest's Google Drive access to continue."
            authorizationResolution.launch(error.intent)
        } catch (error: Exception) {
            busy = false
            status = "Google Drive sync failed: ${error.localizedMessage ?: "Check your connection and Drive permissions."}"
        }
    }

    private fun finishWithMessage(message: String, success: Boolean) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
        setResult(if (success) Activity.RESULT_OK else Activity.RESULT_CANCELED)
        finish()
    }
}
