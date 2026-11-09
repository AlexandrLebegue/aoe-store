package dev.proxboss.aoestore.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.proxboss.aoestore.data.AppSummary
import dev.proxboss.aoestore.data.UrlMapper
import dev.proxboss.aoestore.ui.HomeUiState
import dev.proxboss.aoestore.ui.components.RemoteImage
import dev.proxboss.aoestore.ui.components.SkeletonBox
import dev.proxboss.aoestore.ui.components.formatBytes
import dev.proxboss.aoestore.ui.components.relativeDate
import dev.proxboss.aoestore.ui.components.toAccentColor
import java.util.Locale
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onQueryChange: (String) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onRefresh: () -> Unit,
    onOpenApp: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val categories = remember(state.apps) {
        state.apps.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val filtered = remember(state.apps, state.query, state.category) {
        val query = state.query.trim().lowercase(Locale.FRENCH)
        state.apps.filter { app ->
            (state.category == null || app.category == state.category) &&
                (query.isEmpty() || listOf(app.name, app.tagline, app.developer, app.category)
                    .any { it.lowercase(Locale.FRENCH).contains(query) })
        }
    }
    val banners = remember(state.apps, filtered, state.query) {
        if (state.query.isBlank()) state.apps.take(5) else filtered.take(5)
    }
    val updates = remember(state.apps) {
        state.apps.sortedByDescending { it.updatedAt }.take(8)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HomeHeader(
            query = state.query,
            onQueryChange = onQueryChange,
            onOpenSettings = onOpenSettings,
        )
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(top = 6.dp, bottom = 36.dp),
            ) {
                if (state.offline) {
                    item { OfflineBanner() }
                }
                if (state.isLoading && state.apps.isEmpty()) {
                    item { HomeSkeleton() }
                } else if (state.error != null && state.apps.isEmpty()) {
                    item { ErrorState(state.error, onRefresh) }
                } else {
                    if (banners.isNotEmpty()) {
                        item {
                            BannerCarousel(
                                apps = banners,
                                serverUrl = state.serverUrl,
                                onOpenApp = onOpenApp,
                            )
                        }
                    }
                    if (updates.isNotEmpty()) {
                        item {
                            Column {
                                Spacer(Modifier.height(24.dp))
                                SectionHeader(
                                    title = "Nouveautés et mises à jour",
                                    subtitle = "Les dernières publications",
                                )
                                Spacer(Modifier.height(12.dp))
                                UpdatesRow(updates, state.serverUrl, onOpenApp)
                            }
                        }
                    }
                    item {
                        Column {
                            Spacer(Modifier.height(26.dp))
                            CategoryChips(categories, state.category, onCategorySelected)
                            Spacer(Modifier.height(24.dp))
                            SectionHeader(
                                title = if (state.query.isBlank()) "Toutes les applications" else "Résultats",
                                subtitle = "${filtered.size} application${if (filtered.size > 1) "s" else ""}",
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                    items(
                        items = filtered.chunked(2),
                        key = { row -> row.joinToString("|") { it.slug } },
                    ) { row ->
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                row.forEach { app ->
                                    AppCard(
                                        app = app,
                                        serverUrl = state.serverUrl,
                                        onOpenApp = onOpenApp,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                    if (filtered.isEmpty()) {
                        item { EmptyState(hasQuery = state.query.isNotBlank()) }
                    }
                }
                item { Footer() }
            }
        }
    }
}

@Composable
private fun HomeHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary,
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Storefront,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AoE Store",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Votre boutique privée",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalIconButton(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(15.dp),
                modifier = Modifier.size(46.dp),
            ) {
                Icon(Icons.Outlined.Settings, contentDescription = "Réglages")
            }
        }
        Spacer(Modifier.height(12.dp))
        SearchPill(query = query, onQueryChange = onQueryChange)
    }
}

@Composable
private fun SearchPill(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(21.dp),
            )
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = "Rechercher une app, un éditeur…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Effacer la recherche",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BannerCarousel(
    apps: List<AppSummary>,
    serverUrl: String,
    onOpenApp: (String) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { apps.size })

    LaunchedEffect(apps.size) {
        if (apps.size <= 1) return@LaunchedEffect
        while (true) {
            delay(6500)
            if (!pagerState.isScrollInProgress) {
                runCatching {
                    pagerState.animateScrollToPage((pagerState.currentPage + 1) % apps.size)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 20.dp),
            pageSpacing = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(238.dp),
        ) { page ->
            val app = apps[page]
            val accent = app.accentColor.toAccentColor(MaterialTheme.colorScheme.primary)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(30.dp))
                    .clickable { onOpenApp(app.slug) },
            ) {
                RemoteImage(
                    url = UrlMapper.resolve(serverUrl, app.coverUrls.firstOrNull() ?: app.iconUrl),
                    contentDescription = app.name,
                    modifier = Modifier.fillMaxSize(),
                    alignment = Alignment.TopCenter,
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.45f to Color.Black.copy(alpha = 0.12f),
                                1f to Color.Black.copy(alpha = 0.9f),
                            ),
                        ),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 20.dp, end = 20.dp, bottom = 26.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = accent.copy(alpha = 0.24f),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
                    ) {
                        Text(
                            text = app.category.uppercase(Locale.FRENCH),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = app.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = app.tagline,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.PhoneAndroid,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "  v${app.latestVersion?.version ?: "—"}  •  " +
                                formatBytes(app.latestVersion?.sizeBytes ?: 0L),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(apps.size) { index ->
                val selected = index == pagerState.currentPage
                val width by animateDpAsState(
                    targetValue = if (selected) 18.dp else 6.dp,
                    label = "banner-dot",
                )
                Box(
                    modifier = Modifier
                        .height(6.dp)
                        .width(width)
                        .clip(CircleShape)
                        .background(
                            if (selected) Color.White else Color.White.copy(alpha = 0.42f),
                        ),
                )
            }
        }
    }
}

@Composable
private fun UpdatesRow(
    apps: List<AppSummary>,
    serverUrl: String,
    onOpenApp: (String) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(apps, key = { "update-${it.slug}" }) { app ->
            UpdateCard(app, serverUrl, onOpenApp)
        }
    }
}

@Composable
private fun UpdateCard(
    app: AppSummary,
    serverUrl: String,
    onOpenApp: (String) -> Unit,
) {
    Card(
        onClick = { onOpenApp(app.slug) },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.32f)),
        modifier = Modifier
            .width(286.dp)
            .height(112.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RemoteImage(
                url = UrlMapper.resolve(serverUrl, app.iconUrl),
                contentDescription = app.name,
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(16.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "v${app.latestVersion?.version ?: "—"}  •  ${relativeDate(app.updatedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = app.tagline,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CategoryChips(
    categories: List<String>,
    selected: String?,
    onCategorySelected: (String?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            CategoryChip(
                label = "Toutes",
                selected = selected == null,
                onClick = { onCategorySelected(null) },
            )
        }
        items(categories, key = { "cat-$it" }) { category ->
            CategoryChip(
                label = category,
                selected = selected == category,
                onClick = { onCategorySelected(category) },
            )
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            selectedLabelColor = MaterialTheme.colorScheme.primary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
        ),
    )
}

@Composable
private fun SectionHeader(title: String, subtitle: String? = null) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AppCard(
    app: AppSummary,
    serverUrl: String,
    onOpenApp: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = app.accentColor.toAccentColor(MaterialTheme.colorScheme.primary)
    Card(
        onClick = { onOpenApp(app.slug) },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        modifier = modifier.height(176.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
        ) {
            RemoteImage(
                url = UrlMapper.resolve(serverUrl, app.iconUrl),
                contentDescription = app.name,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(17.dp)),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = app.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = app.developer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "v${app.latestVersion?.version ?: "—"}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                    ),
                    color = accent,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatBytes(app.latestVersion?.sizeBytes ?: 0L),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OfflineBanner() {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.28f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.WifiOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "Mode hors ligne",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Catalogue en cache — tirez vers le bas pour réessayer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ErrorState(message: String, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(52.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Catalogue indisponible",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRefresh) {
            Text("Réessayer")
        }
    }
}

@Composable
private fun EmptyState(hasQuery: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.SearchOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (hasQuery) "Aucun résultat" else "Catalogue vide",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = if (hasQuery) {
                "Essayez un autre nom, éditeur ou catégorie."
            } else {
                "Publiez une application avec store-publish pour la voir apparaître."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HomeSkeleton() {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(238.dp),
            shape = RoundedCornerShape(30.dp),
        )
        Spacer(Modifier.height(24.dp))
        SkeletonBox(
            modifier = Modifier
                .width(200.dp)
                .height(20.dp),
            shape = RoundedCornerShape(8.dp),
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SkeletonBox(
                modifier = Modifier
                    .weight(1f)
                    .height(112.dp),
                shape = RoundedCornerShape(22.dp),
            )
            SkeletonBox(
                modifier = Modifier
                    .weight(1f)
                    .height(112.dp),
                shape = RoundedCornerShape(22.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) {
                SkeletonBox(
                    modifier = Modifier
                        .width(84.dp)
                        .height(34.dp),
                    shape = RoundedCornerShape(17.dp),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        repeat(3) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SkeletonBox(
                    modifier = Modifier
                        .weight(1f)
                        .height(176.dp),
                    shape = RoundedCornerShape(24.dp),
                )
                SkeletonBox(
                    modifier = Modifier
                        .weight(1f)
                        .height(176.dp),
                    shape = RoundedCornerShape(24.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun Footer() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "AoE Store • catalogue privé",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}
