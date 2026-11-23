package com.yourname.colornest

import android.accounts.AccountManager
import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.services.drive.DriveScopes
import com.yourname.colornest.data.ColorNestDatabase
import com.yourname.colornest.data.drive.DriveSyncManager
import kotlinx.coroutines.launch

/**
 * Handles account selection and Google's separate OAuth permission prompt.
 * Selecting an account does not itself grant Drive API access; the first API
 * request can return a recoverable authorization intent that must be shown.
 */
class DriveConnectActivity : ComponentActivity() {
    companion object {
        const val EXTRA_PICK_ACCOUNT = "com.yourname.colornest.extra.PICK_DRIVE_ACCOUNT"
        private val DRIVE_SCOPES = listOf(DriveScopes.DRIVE_FILE, DriveScopes.DRIVE_READONLY)
    }

    private lateinit var statusView: TextView
    private lateinit var driveManager: DriveSyncManager

    private val accountPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            finishWithMessage("Google Drive account selection was cancelled.", false)
            return@registerForActivityResult
        }

        val accountName = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        if (accountName.isNullOrBlank()) {
            finishWithMessage("Google did not return an account. Try connecting again.", false)
            return@registerForActivityResult
        }

        lifecycleScope.launch {
            try {
                driveManager.connect(accountName)
                syncAndFinish()
            } catch (error: Exception) {
                finishWithMessage(
                    "Could not connect Google Drive: ${error.localizedMessage ?: "Unknown error"}",
                    false
                )
            }
        }
    }

    private val authorizationResolution = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            lifecycleScope.launch { syncAndFinish() }
        } else {
            finishWithMessage(
                "Google Drive permission was not granted. You can try connecting again in Settings.",
                false
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        statusView = TextView(this).apply {
            text = "Preparing Google Drive…"
            textSize = 16f
            setPadding(32, 48, 32, 48)
        }
        setContentView(statusView)

        val database = ColorNestDatabase.getDatabase(applicationContext)
        driveManager = DriveSyncManager(
            applicationContext,
            database.pageDao(),
            database.categoryDao(),
            database.settingDao()
        )

        lifecycleScope.launch {
            if (intent.getBooleanExtra(EXTRA_PICK_ACCOUNT, false) || !driveManager.isConnected()) {
                launchAccountPicker()
            } else {
                syncAndFinish()
            }
        }
    }

    private fun launchAccountPicker() {
        statusView.text = "Choose the Google account for ColorNest."
        val credential = GoogleAccountCredential.usingOAuth2(this, DRIVE_SCOPES)
        accountPicker.launch(credential.newChooseAccountIntent())
    }

    private suspend fun syncAndFinish() {
        statusView.text = "Syncing your ColorNest library with Google Drive…"
        try {
            val result = driveManager.sync()
            finishWithMessage(result.summary(), !result.notConnected)
        } catch (error: UserRecoverableAuthIOException) {
            // Google requires a separate consent/re-authentication step after
            // account selection. Launch its resolution UI, then retry sync.
            statusView.text = "Approve ColorNest's Google Drive access to continue."
            authorizationResolution.launch(error.intent)
        } catch (error: Exception) {
            finishWithMessage(
                "Google Drive sync failed: ${error.localizedMessage ?: "Check your connection and Drive permissions."}",
                false
            )
        }
    }

    private fun finishWithMessage(message: String, success: Boolean) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        setResult(if (success) Activity.RESULT_OK else Activity.RESULT_CANCELED)
        finish()
    }
}
