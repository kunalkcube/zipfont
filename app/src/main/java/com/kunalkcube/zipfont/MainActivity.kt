package com.kunalkcube.zipfont

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.kunalkcube.zipfont.processor.ApkProcessor
import com.kunalkcube.zipfont.ui.theme.ZipFontTheme
import com.kunalkcube.zipfont.viewmodel.MainViewModel
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZipFontTheme {
                ZipFontScreen(viewModel = viewModel)
            }
        }
    }
}

private object UiPalette {
    val Background = Color(0xFF0F1218)
    val Panel = Color(0xFF1A1F2A)
    val PanelMuted = Color(0xFF232A38)
    val Grid = Color(0xFF364156)
    val Border = Color(0xFF313B50)
    val TextPrimary = Color(0xFFF4F7FF)
    val TextSecondary = Color(0xFFADB9D0)
    val Accent = Color(0xFFFFB34D)
    val AccentInk = Color(0xFF2F1D00)
    val Positive = Color(0xFF5CD39B)
    val Warning = Color(0xFFFF7D7D)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZipFontScreen(
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val uiState by viewModel.state.collectAsState()
    val generatedApk by viewModel.generatedApk.collectAsState()

    val fontPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Some providers do not support persistable permissions.
            }
            viewModel.selectFont(uri)
        }
    }

    val selectAction = { fontPicker.launch(arrayOf("font/ttf", "application/x-font-ttf", "application/octet-stream")) }
    val isReady = uiState is MainViewModel.UiState.Ready && generatedApk != null

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        SolidStudioBackground()

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = UiPalette.TextPrimary
                    ),
                    title = {
                        Text(
                            text = "ZIPFONT // Builder",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
            },
            bottomBar = {
                HomeFooter()
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                HeroPanel()

                if (!isReady) {
                    Button(
                        onClick = selectAction,
                        enabled = uiState !is MainViewModel.UiState.Processing,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = UiPalette.Accent,
                            contentColor = UiPalette.AccentInk,
                            disabledContainerColor = UiPalette.PanelMuted,
                            disabledContentColor = UiPalette.TextSecondary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Bolt,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pick Font File")
                    }
                }

                StatusCard(uiState = uiState)

                if (isReady) {
                    OutputCard(
                        path = ApkProcessor.OUTPUT_DISPLAY_PATH,
                        cachePath = generatedApk!!.absolutePath
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { installApk(context, generatedApk!!) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = UiPalette.Accent,
                                contentColor = UiPalette.AccentInk,
                                disabledContainerColor = UiPalette.PanelMuted,
                                disabledContentColor = UiPalette.TextSecondary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.InstallMobile,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Install")
                        }

                        Button(
                            onClick = { openStepsScreen(context) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = UiPalette.PanelMuted,
                                contentColor = UiPalette.TextPrimary,
                                disabledContainerColor = UiPalette.PanelMuted,
                                disabledContentColor = UiPalette.TextSecondary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FormatListNumbered,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Steps")
                        }
                    }

                    Button(
                        onClick = {
                            viewModel.reset()
                            selectAction()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = UiPalette.PanelMuted,
                            contentColor = UiPalette.TextPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate Another")
                    }
                } else if (uiState is MainViewModel.UiState.Error) {
                    Button(
                        onClick = {
                            viewModel.reset()
                            selectAction()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = UiPalette.Warning,
                            contentColor = Color(0xFF2B0505)
                        )
                    ) {
                        Text("Try Again")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun HomeFooter() {
    val uriHandler = LocalUriHandler.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = { uriHandler.openUri("https://github.com/kunalkcube") },
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.textButtonColors(
                contentColor = UiPalette.Accent
            )
        ) {
            Text(
                text = "Built by kunalkcube",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SolidStudioBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(UiPalette.Background)
            .drawBehind {
                val gap = 88f
                var x = 0f
                while (x < size.width) {
                    drawLine(
                        color = UiPalette.Grid.copy(alpha = 0.16f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1f
                    )
                    x += gap
                }
                var y = 0f
                while (y < size.height) {
                    drawLine(
                        color = UiPalette.Grid.copy(alpha = 0.14f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f
                    )
                    y += gap
                }
            }
    )
}

@Composable
private fun HeroPanel() {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(UiPalette.Accent, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bolt,
                        contentDescription = null,
                        tint = UiPalette.AccentInk,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = "APK FONT FORGE",
                    style = MaterialTheme.typography.labelLarge,
                    color = UiPalette.Accent,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Build FlipFont APK",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = UiPalette.TextPrimary
            )
            Text(
                text = "Import a font, inject it into the skeleton package, auto-align, sign, and deploy from one place.",
                style = MaterialTheme.typography.bodyMedium,
                color = UiPalette.TextSecondary
            )
        }
    }
}

@Composable
private fun StatusCard(uiState: MainViewModel.UiState) {
    val statusTitle: String
    val statusText: String
    val statusColor: Color
    val statusIcon: ImageVector

    when (uiState) {
        is MainViewModel.UiState.Idle -> {
            statusTitle = "Standby"
            statusText = "Pick a font file to start the pipeline."
            statusColor = UiPalette.TextPrimary
            statusIcon = Icons.Filled.Bolt
        }
        is MainViewModel.UiState.Processing -> {
            statusTitle = "Pipeline Running"
            statusText = statusLabel(uiState.processState)
            statusColor = UiPalette.Accent
            statusIcon = Icons.Filled.Bolt
        }
        is MainViewModel.UiState.Ready -> {
            statusTitle = "Output Ready"
            statusText = "Font APK built and saved successfully."
            statusColor = UiPalette.Positive
            statusIcon = Icons.Filled.CheckCircle
        }
        is MainViewModel.UiState.Error -> {
            statusTitle = "Build Failed"
            statusText = uiState.message
            statusColor = UiPalette.Warning
            statusIcon = Icons.Filled.ErrorOutline
        }
    }

    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = statusColor
                )
                Text(
                    text = statusTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = UiPalette.TextPrimary
                )
            }

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = statusColor
            )

            if (uiState is MainViewModel.UiState.Processing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(UiPalette.PanelMuted, RoundedCornerShape(8.dp)),
                    color = UiPalette.Accent,
                    trackColor = UiPalette.PanelMuted
                )

                RowProgress()
            }
        }
    }
}

@Composable
private fun RowProgress() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            color = UiPalette.Accent
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Compiling font package...",
            style = MaterialTheme.typography.bodySmall,
            color = UiPalette.TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun OutputCard(path: String, cachePath: String) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Output",
                style = MaterialTheme.typography.titleMedium,
                color = UiPalette.TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = path,
                style = MaterialTheme.typography.bodyLarge,
                color = UiPalette.Positive
            )
            Text(
                text = "Install source: $cachePath",
                style = MaterialTheme.typography.bodySmall,
                color = UiPalette.TextSecondary
            )
        }
    }
}

@Composable
private fun PanelCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = UiPalette.Panel
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, UiPalette.Border)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(0.dp, Color.Transparent)
        ) {
            content()
        }
    }
}

private fun statusLabel(processState: ApkProcessor.ProcessState): String {
    return when (processState) {
        ApkProcessor.ProcessState.Idle -> "Idle"
        ApkProcessor.ProcessState.ExtractingSkeleton -> "Extracting Skeleton..."
        ApkProcessor.ProcessState.CopyingFont -> "Copying Font..."
        ApkProcessor.ProcessState.InjectingFont -> "Injecting Font..."
        ApkProcessor.ProcessState.ZipAligning -> "ZipAligning APK..."
        ApkProcessor.ProcessState.SigningApk -> "Signing APK..."
        ApkProcessor.ProcessState.Ready -> "Ready!"
        is ApkProcessor.ProcessState.Error -> "Error: ${processState.message}"
    }
}

private fun installApk(context: android.content.Context, apkFile: File) {
    try {
        if (!apkFile.exists()) {
            Toast.makeText(context, "Generated APK not found", Toast.LENGTH_LONG).show()
            return
        }

        val archiveInfo = readArchivePackageInfo(context.packageManager, apkFile)
            ?: run {
                Toast.makeText(context, "Generated APK is invalid", Toast.LENGTH_LONG).show()
                return
            }

        val installedInfo = readInstalledPackageInfo(
            packageManager = context.packageManager,
            packageName = archiveInfo.packageName
        )

        if (installedInfo != null) {
            val archiveSigners = archiveInfo.signingInfo?.apkContentsSigners
                ?.map { it.toCharsString() }
                ?.toSet()
                .orEmpty()
            val installedSigners = installedInfo.signingInfo?.apkContentsSigners
                ?.map { it.toCharsString() }
                ?.toSet()
                .orEmpty()

            if (archiveSigners.isNotEmpty() && installedSigners.isNotEmpty() && archiveSigners != installedSigners) {
                Toast.makeText(
                    context,
                    "A different-signed version is already installed. Uninstall it first.",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            if (installedInfo.longVersionCode > archiveInfo.longVersionCode) {
                Toast.makeText(
                    context,
                    "Installed version is newer. Uninstall existing app first.",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
        }

        if (!context.packageManager.canRequestPackageInstalls()) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            )
            settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settingsIntent)
            Toast.makeText(
                context,
                "Enable \"Install unknown apps\" for this app, then tap Install again.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            clipData = ClipData.newRawUri("generated-apk", uri)
        }

        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No installer available", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to install: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

private fun readArchivePackageInfo(packageManager: PackageManager, apkFile: File): PackageInfo? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val flags = PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
        packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags)
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageArchiveInfo(
            apkFile.absolutePath,
            PackageManager.GET_SIGNING_CERTIFICATES
        )
    }
}

private fun readInstalledPackageInfo(packageManager: PackageManager, packageName: String): PackageInfo? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val flags = PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            packageManager.getPackageInfo(packageName, flags)
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}

private fun openStepsScreen(context: android.content.Context) {
    try {
        context.startActivity(Intent(context, ExploitStepsActivity::class.java))
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to open steps: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
