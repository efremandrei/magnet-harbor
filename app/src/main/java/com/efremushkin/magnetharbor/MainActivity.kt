package com.efremushkin.magnetharbor

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.efremushkin.magnetharbor.ui.MagnetHarborApp
import com.efremushkin.magnetharbor.ui.MagnetHarborViewModel
import com.efremushkin.magnetharbor.ui.theme.MagnetHarborTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as MagnetHarborApplication).container

        setContent {
            val viewModel: MagnetHarborViewModel = viewModel(
                factory = MagnetHarborViewModel.Factory(
                    repository = container.torrentRepository,
                    settingsRepository = container.settingsRepository,
                    enabledSourceIds = container.sourceIds,
                ),
            )
            val settings by viewModel.settings.collectAsStateWithLifecycle()

            MagnetHarborTheme(darkTheme = settings.darkTheme) {
                Surface {
                    MagnetHarborApp(
                        viewModel = viewModel,
                        onOpenMagnet = ::openMagnet,
                        onCopyMagnet = ::copyMagnet,
                        onShareMagnet = ::shareMagnet,
                    )
                }
            }
        }
    }

    private fun openMagnet(magnetUri: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(magnetUri)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Install a torrent client to open magnet links.", Toast.LENGTH_LONG).show()
        }
    }

    private fun copyMagnet(magnetUri: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Magnet link", magnetUri))
        Toast.makeText(this, "Magnet link copied", Toast.LENGTH_SHORT).show()
    }

    private fun shareMagnet(magnetUri: String) {
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, magnetUri)
        }
        startActivity(Intent.createChooser(share, "Share magnet link"))
    }
}
