package dev.proxboss.aoestore.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.proxboss.aoestore.data.AppDetail
import dev.proxboss.aoestore.data.UrlMapper
import dev.proxboss.aoestore.data.VersionInfo
import dev.proxboss.aoestore.ui.DetailUiState
import dev.proxboss.aoestore.ui.InstallUiState
import dev.proxboss.aoestore.ui.components.RemoteImage
import dev.proxboss.aoestore.ui.components.SkeletonBox
import dev.proxboss.aoestore.ui.components.formatBytes
import dev.proxboss.aoestore.ui.components.formatDate
import dev.proxboss.aoestore.ui.components.toAccentColor
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    state: DetailUiState,
    onBack: () -> Unit,
    onInstall: (VersionInfo) -> Unit,
    onOpenApp: () -> Unit,
    onOpenUnknownSources: () -> Unit,
    onCheckPermission: () -> Unit,
    onToggleHistory: () -> Unit,
    onRetry: () -> Unit,
    onMessageConsumed: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            onMessageConsumed()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val detail = state.detail
            when {
                state.isLoading -> DetailSkeleton(onBack = onBack)
                state.error != null -> DetailError(
                    message = state.error,
                    onBack = onBack,
                    onRetry = onRetry,
                )
                detail != null -> DetailContent(
                    state = state,
                    detail = detail,
                    onBack = onBack,
                    onInstall = onInstall,
                    onOpenApp = onOpenApp,
                    onOpenUnknownSources = onOpenUnknownSources,
                    onCheckPermission = onCheckPermission,
                    onToggleHistory = onToggleHistory,
                )
                else -> DetailError(
                    message = "Fiche indisponible",
                    onBack = onBack,
                    onRetry = onRetry,
                )
            }
        }
    }
}

@Composable
private fun DetailContent(
    state: DetailUiState,
    detail: AppDetail,
    onBack: () -> Unit,
    onInstall: (VersionInfo) -> Unit,
    onOpenApp: () -> Unit,
    onOpenUnknownSources: () -> Unit,
    onCheckPermission: () -> Unit,
    onToggleHistory: () -> Unit,
) {
    val latest = state.latestVersion
    val installed = state.installedVersionCode
    val upToDate = installed != null && latest != null && installed >= latest.versionCode
    val accent = detail.accentColor.toAccentColor(MaterialTheme.colorScheme.primary)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        item {
            DetailHeader(detail = detail, serverUrl = state.serverUrl, accent = accent, onBack = onBack)
        }
        item {
            AppIdentity(detail = detail, serverUrl = state.serverUrl, accent = accent)
        }
        item {
            InstallCard(
                latest = latest,
                installedVersionCode = installed,
                installState = state.installState,
                onInstall = onInstall,
                onOpenApp = onOpenApp,
            )
        }
        if (state.needsUnknownSources && !upToDate) {
            item {
                UnknownSourcesCard(
                    onOpenSettings = onOpenUnknownSources,
                    onCheckPermission = onCheckPermission,
                )
            }
        }
        if (detail.coverUrls.isNotEmpty()) {
            item {
                ScreenshotsSection(detail = detail, serverUrl = state.serverUrl)
            }
        }
        item {
            DescriptionSection(detail.description)
        }
        item {
            WhatsNewSection(latest)
        }
        item {
            InfoSection(detail = detail, latest = latest)
        }
        item {
            VersionHistorySection(
                versions = detail.versions,
                expanded = state.historyExpanded,
                activeVersion = state.activeVersion,
                installState = state.installState,
                onToggle = onToggleHistory,
                onInstall = onInstall,
            )
        }
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "AoE Store • ${detail.developer}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                )
            }
        }
    }
}

@Composable
private fun DetailHeader(
    detail: AppDetail,
    serverUrl: String,
    accent: Color,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp),
    ) {
        RemoteImage(
            url = UrlMapper.resolve(
                serverUrl,
                detail.coverUrls.firstOrNull() ?: detail.iconUrl,
            ),
            contentDescription = detail.name,
            modifier = Modifier.fillMaxSize(),
            alignment = Alignment.TopCenter,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.55f),
                        0.35f to Color.Transparent,
                        0.75f to MaterialTheme.colorScheme.background.copy(alpha = 0.35f),
                        1f to MaterialTheme.colorScheme.background,
                    ),
                ),
        )
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(12.dp)
                .size(46.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f)),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Retour",
                tint = Color.White,
            )
        }
        Surface(
            shape = RoundedCornerShape(50),
            color = accent.copy(alpha = 0.28f),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(14.dp),
        ) {
            Text(
                text = detail.category,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun AppIdentity(detail: AppDetail, serverUrl: String, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(23.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            RemoteImage(
                url = UrlMapper.resolve(serverUrl, detail.iconUrl),
                contentDescription = detail.name,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = detail.name,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = detail.developer,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = accent.copy(alpha = 0.15f),
                ) {
                    Text(
                        text = detail.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${detail.downloads} téléchargement${if (detail.downloads > 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InstallCard(
    latest: VersionInfo?,
    installedVersionCode: Int?,
    installState: InstallUiState,
    onInstall: (VersionInfo) -> Unit,
    onOpenApp: () -> Unit,
) {
    val upToDate = installedVersionCode != null && latest != null &&
        installedVersionCode >= latest.versionCode
    val busy = installState is InstallUiState.Downloading || installState is InstallUiState.Verifying

    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = latest?.let { "Version ${it.version}" } ?: "Version indisponible",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (latest != null) {
                            "${formatBytes(latest.sizeBytes)}  •  ${formatDate(latest.publishedAt)}"
                        } else {
                            "Aucune version publiée"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when {
                    upToDate -> StatusPill("À jour", MaterialTheme.colorScheme.tertiary)
                    installedVersionCode != null -> StatusPill("Mise à jour", MaterialTheme.colorScheme.secondary)
                }
            }

            Spacer(Modifier.height(14.dp))

            val buttonLabel = when {
                installState is InstallUiState.Downloading -> "Téléchargement…"
                installState is InstallUiState.Verifying -> "Vérification…"
                upToDate -> "Ouvrir"
                installedVersionCode != null -> "Mettre à jour"
                else -> "Installer"
            }
            Button(
                onClick = {
                    if (upToDate) onOpenApp()
                    else latest?.let(onInstall)
                },
                enabled = latest != null && !busy,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                when {
                    busy -> CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    upToDate -> Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, modifier = Modifier.size(18.dp))
                    else -> Icon(Icons.Outlined.Download, null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(buttonLabel, style = MaterialTheme.typography.labelLarge)
            }

            when (val current = installState) {
                is InstallUiState.Downloading -> {
                    Spacer(Modifier.height(12.dp))
                    val fraction = if (current.totalBytes > 0) {
                        (current.bytesRead.toFloat() / current.totalBytes).coerceIn(0f, 1f)
                    } else {
                        null
                    }
                    if (fraction != null) {
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (fraction != null) {
                            "Téléchargement ${(fraction * 100).roundToInt()} %  •  " +
                                "${formatBytes(current.bytesRead)} / ${formatBytes(current.totalBytes)}"
                        } else {
                            "Téléchargement ${formatBytes(current.bytesRead)}…"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is InstallUiState.Verifying -> {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Vérification de la somme SHA-256…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is InstallUiState.Failed -> {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = current.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                else -> Unit
            }

            if (installedVersionCode != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Version installée : v$installedVersionCode",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusPill(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.14f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun UnknownSourcesCard(
    onOpenSettings: () -> Unit,
    onCheckPermission: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Autorisation d’installation requise",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Android demande d’autoriser AoE Store à installer des applications. " +
                    "Ouvrez les réglages, activez l’autorisation, puis revenez dans la boutique.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenSettings, shape = RoundedCornerShape(12.dp)) {
                    Text("Ouvrir les réglages")
                }
                TextButton(onClick = onCheckPermission) {
                    Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("J’ai autorisé")
                }
            }
        }
    }
}

@Composable
private fun ScreenshotsSection(detail: AppDetail, serverUrl: String) {
    val pagerState = rememberPagerState(pageCount = { detail.coverUrls.size })
    Column(modifier = Modifier.padding(top = 24.dp)) {
        SectionTitle("Aperçus", Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(12.dp))
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 48.dp),
            pageSpacing = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(430.dp),
        ) { page ->
            RemoteImage(
                url = UrlMapper.resolve(serverUrl, detail.coverUrls[page]),
                contentDescription = "${detail.name} — aperçu ${page + 1}",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp)),
                contentScale = ContentScale.Fit,
            )
        }
        if (detail.coverUrls.size > 1) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(detail.coverUrls.size) { index ->
                    val selected = index == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (selected) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                },
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun DescriptionSection(description: String) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
        SectionTitle("À propos de cette application")
        Spacer(Modifier.height(10.dp))
        Text(
            text = description.ifBlank { "Aucune description fournie." },
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 23.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WhatsNewSection(latest: VersionInfo?) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        SectionTitle("Nouveautés de cette version")
        Spacer(Modifier.height(10.dp))
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (latest != null) {
                    StatusPill("v${latest.version}", MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(10.dp))
                }
                Text(
                    text = latest?.changelog?.takeIf { it.isNotBlank() }
                        ?: "Aucune note de version fournie.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InfoSection(detail: AppDetail, latest: VersionInfo?) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
        SectionTitle("Informations")
        Spacer(Modifier.height(10.dp))
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                InfoRow("Package", detail.packageName.ifBlank { "—" })
                InfoRow("Version", latest?.let { "${it.version} (${it.versionCode})" } ?: "—")
                InfoRow("Taille", formatBytes(latest?.sizeBytes ?: 0L))
                InfoRow("Publiée le", formatDate(latest?.publishedAt ?: ""))
                InfoRow("Téléchargements", detail.downloads.toString())
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                )
                Text(
                    text = "SHA-256",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                SelectionContainer {
                    Text(
                        text = latest?.sha256?.takeIf { it.isNotBlank() } ?: "—",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(126.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun VersionHistorySection(
    versions: List<VersionInfo>,
    expanded: Boolean,
    activeVersion: VersionInfo?,
    installState: InstallUiState,
    onToggle: () -> Unit,
    onInstall: (VersionInfo) -> Unit,
) {
    val sorted = remember(versions) { versions.sortedByDescending { it.versionCode } }
    val busy = installState is InstallUiState.Downloading || installState is InstallUiState.Verifying

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Historique des versions",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${versions.size} version${if (versions.size > 1) "s" else ""} publiée${if (versions.size > 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Replier" else "Déplier",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    if (sorted.isEmpty()) {
                        Text(
                            text = "Aucune version publiée.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    sorted.forEachIndexed { index, version ->
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                        )
                        VersionHistoryRow(
                            version = version,
                            busy = busy && activeVersion?.version == version.version,
                            enabled = !busy,
                            onInstall = { onInstall(version) },
                        )
                        if (index == sorted.lastIndex) {
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionHistoryRow(
    version: VersionInfo,
    busy: Boolean,
    enabled: Boolean,
    onInstall: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "v${version.version}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${formatDate(version.publishedAt)}  •  ${formatBytes(version.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onInstall, enabled = enabled) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Outlined.Download, null, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(if (busy) "En cours…" else "Télécharger")
            }
        }
        if (version.changelog.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = version.changelog,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier,
    )
}

@Composable
private fun DetailSkeleton(onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column {
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
                shape = RoundedCornerShape(0.dp),
            )
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonBox(
                    modifier = Modifier.size(84.dp),
                    shape = RoundedCornerShape(23.dp),
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    SkeletonBox(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(26.dp),
                        shape = RoundedCornerShape(8.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    SkeletonBox(
                        modifier = Modifier
                            .fillMaxWidth(0.45f)
                            .height(16.dp),
                        shape = RoundedCornerShape(6.dp),
                    )
                }
            }
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .height(150.dp),
                shape = RoundedCornerShape(26.dp),
            )
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .height(430.dp),
                shape = RoundedCornerShape(24.dp),
            )
        }
        DetailBackButton(onBack = onBack)
    }
}

@Composable
private fun DetailError(message: String, onBack: () -> Unit, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(52.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Fiche indisponible",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            Button(onClick = onRetry) {
                Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Réessayer")
            }
        }
        DetailBackButton(onBack = onBack)
    }
}

@Composable
private fun DetailBackButton(onBack: () -> Unit) {
    IconButton(
        onClick = onBack,
        modifier = Modifier
            .statusBarsPadding()
            .padding(12.dp)
            .size(46.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f)),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = "Retour",
            tint = Color.White,
        )
    }
}
