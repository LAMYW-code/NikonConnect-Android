package com.nikonconnect.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nikonconnect.app.connection.NikonConnectRoot
import com.nikonconnect.app.connection.ConnectionViewModel
import com.nikonconnect.app.connection.ConnectionViewModelFactory
import com.nikonconnect.app.ui.theme.NikonConnectTheme

class MainActivity : ComponentActivity() {
    private var pendingConnect: (() -> Unit)? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val action = pendingConnect
        pendingConnect = null
        if (granted) action?.invoke() else currentViewModel?.permissionDenied()
    }

    private var pendingDownload: (() -> Unit)? = null
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        pendingDownload?.invoke()
        pendingDownload = null
    }

    private var currentViewModel: ConnectionViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as NikonConnectApplication).container

        setContent {
            NikonConnectTheme {
                val connectionViewModel: ConnectionViewModel = viewModel(
                    factory = ConnectionViewModelFactory(container),
                )
                currentViewModel = connectionViewModel
                val state by connectionViewModel.uiState.collectAsStateWithLifecycle()
                val gallery by connectionViewModel.galleryState.collectAsStateWithLifecycle()
                val preview by connectionViewModel.previewState.collectAsStateWithLifecycle()
                NikonConnectRoot(
                    connection = state,
                    gallery = gallery,
                    preview = preview,
                    onConnect = { connectWithPermission(connectionViewModel::connect) },
                    onDisconnect = connectionViewModel::disconnect,
                    onRefresh = { connectionViewModel.loadAssets(reset = true) },
                    onLoadMore = { connectionViewModel.loadAssets() },
                    onToggleSelection = connectionViewModel::toggleSelection,
                    onDownload = { downloadWithNotificationPermission(connectionViewModel::downloadSelected) },
                    onFilterChange = connectionViewModel::setGalleryFilter,
                    onRequestThumbnail = connectionViewModel::requestThumbnail,
                    onRequestPreview = connectionViewModel::requestPreview,
                    onDismissPreview = connectionViewModel::dismissPreview,
                )
            }
        }
    }

    private fun connectWithPermission(connect: () -> Unit) {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.NEARBY_WIFI_DEVICES
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            connect()
        } else {
            pendingConnect = connect
            permissionLauncher.launch(permission)
        }
    }

    private fun downloadWithNotificationPermission(download: () -> Unit) {
        if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            download()
        } else {
            pendingDownload = download
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
