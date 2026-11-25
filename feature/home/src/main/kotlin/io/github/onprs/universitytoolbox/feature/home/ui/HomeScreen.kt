package io.github.onprs.universitytoolbox.feature.home.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import io.github.onprs.universitytoolbox.core.designsystem.component.ToolboxSectionHeader
import io.github.onprs.universitytoolbox.core.designsystem.component.ToolboxStatusBadge
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxSizes
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxSpacing
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxTheme
import io.github.onprs.universitytoolbox.core.tool.api.ToolCategory
import io.github.onprs.universitytoolbox.core.ui.DevStubBanner
import io.github.onprs.universitytoolbox.core.ui.EmptyStateView
import io.github.onprs.universitytoolbox.core.ui.LockedToolCard
import io.github.onprs.universitytoolbox.core.ui.labelRes
import io.github.onprs.universitytoolbox.core.ui.toImageVector
import io.github.onprs.universitytoolbox.feature.home.R
import io.github.onprs.universitytoolbox.feature.home.domain.HomeToolItem
import io.github.onprs.universitytoolbox.feature.home.domain.ToolAccessStatus
import kotlinx.coroutines.launch

/**
 * 工具箱首页与自适应双栏导航壳主路由。
 */
@Composable
fun HomeRoute(
    onOpenTool: (NavKey) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenUnlock: (initialCode: String?, manageMode: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HomeScreen(
        uiState = uiState,
        onSearchQueryChange = viewModel::updateSearchQuery,
        onSelectCategory = viewModel::selectCategory,
        onSelectTool = viewModel::selectTool,
        onToggleFavorite = viewModel::toggleFavorite,
        onLaunchTool = { item ->
            viewModel.onToolOpened(item)
            onOpenTool(item.descriptor.entryKey)
        },
        onOpenSettings = onOpenSettings,
        onOpenUnlock = onOpenUnlock,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun HomeScreen(
    uiState: HomeUiState,
    onSearchQueryChange: (String) -> Unit,
    onSelectCategory: (ToolCategory?) -> Unit,
    onSelectTool: (String?) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onLaunchTool: (HomeToolItem) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenUnlock: (initialCode: String?, manageMode: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val scope = rememberCoroutineScope()
    val isDualPane = navigator.scaffoldDirective.maxHorizontalPartitions > 1

    BackHandler(enabled = navigator.canNavigateBack()) {
        scope.launch {
            navigator.navigateBack()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            HomeTopBar(
                onOpenLicense = { onOpenUnlock(null, true) },
                onOpenSettings = onOpenSettings,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            DevStubBanner(licenseState = uiState.licenseState)

            ListDetailPaneScaffold(
                directive = navigator.scaffoldDirective,
                value = navigator.scaffoldValue,
                modifier = Modifier.weight(1f),
                listPane = {
                    AnimatedPane {
                        HomeCatalogListPane(
                            uiState = uiState,
                            onSearchQueryChange = onSearchQueryChange,
                            onSelectCategory = onSelectCategory,
                            onToggleFavorite = onToggleFavorite,
                            onItemClick = { item ->
                                onSelectTool(item.descriptor.id)
                                if (!isDualPane && item.isAvailable) {
                                    onLaunchTool(item)
                                } else {
                                    scope.launch {
                                        navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, item.descriptor.id)
                                    }
                                }
                            },
                        )
                    }
                },
                detailPane = {
                    AnimatedPane {
                        val activeKey = navigator.currentDestination?.contentKey
                        val detailItem = uiState.filteredTools.firstOrNull { it.descriptor.id == activeKey }
                            ?: uiState.pinnedTools.firstOrNull { it.descriptor.id == activeKey }
                            ?: uiState.selectedTool
                        HomeToolDetailPane(
                            item = detailItem,
                            showBackButton = navigator.canNavigateBack(),
                            onBackClick = { scope.launch { navigator.navigateBack() } },
                            onLaunchTool = onLaunchTool,
                            onToggleFavorite = onToggleFavorite,
                            onOpenUnlock = { onOpenUnlock(null, false) },
                        )
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(onOpenLicense: () -> Unit, onOpenSettings: () -> Unit) {
    TopAppBar(
        title = { Text(text = stringResource(R.string.feature_home_title)) },
        actions = {
            IconButton(onClick = onOpenLicense) {
                Icon(
                    imageVector = Icons.Outlined.VerifiedUser,
                    contentDescription = stringResource(R.string.feature_home_open_license),
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.feature_home_open_settings),
                )
            }
        },
    )
}

internal const val HOME_SEARCH_FIELD_TEST_TAG = "home_search_field"

@Composable
private fun HomeCatalogListPane(
    uiState: HomeUiState,
    onSearchQueryChange: (String) -> Unit,
    onSelectCategory: (ToolCategory?) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onItemClick: (HomeToolItem) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val selectWithClearFocus: (HomeToolItem) -> Unit = { item ->
        focusManager.clearFocus()
        onItemClick(item)
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) focusManager.clearFocus()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 承接 ListDetailPaneScaffold / AnimatedPane 首次挂载时的默认面板焦点请求，防止自动选中搜索框
        Box(modifier = Modifier.size(1.dp).focusable())
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(ToolboxSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.mediumSmall),
        ) {
            item(key = "search_bar") {
                HomeSearchField(
                    query = uiState.searchQuery,
                    onQueryChange = onSearchQueryChange,
                    onClearFocus = { focusManager.clearFocus() },
                )
            }
            item(key = "category_chips") {
                CategoryFilterRow(
                    selectedCategory = uiState.selectedCategory,
                    onSelectCategory = { category ->
                        focusManager.clearFocus()
                        onSelectCategory(category)
                    },
                )
            }
            if (uiState.searchQuery.isBlank() && uiState.selectedCategory == null) {
                if (uiState.pinnedTools.isNotEmpty()) {
                    item(key = "pinned_section") {
                        HomePinnedSection(pinnedTools = uiState.pinnedTools, onItemClick = selectWithClearFocus)
                    }
                }
                if (uiState.recentTools.isNotEmpty()) {
                    item(key = "recent_section") {
                        HomeRecentSection(recentTools = uiState.recentTools, onItemClick = selectWithClearFocus)
                    }
                }
            }
            item(key = "all_header") {
                ToolboxSectionHeader(title = stringResource(R.string.feature_home_section_all))
            }
            if (uiState.filteredTools.isEmpty()) {
                item(key = "empty_tools") {
                    EmptyStateView(
                        title = stringResource(R.string.feature_home_empty_title),
                        description = stringResource(R.string.feature_home_empty_desc),
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                    )
                }
            } else {
                items(items = uiState.filteredTools, key = { it.descriptor.id }) { item ->
                    ToolCatalogCard(
                        item = item,
                        isSelected = item.descriptor.id == uiState.selectedToolId,
                        onClick = { selectWithClearFocus(item) },
                        onTogglePin = { onToggleFavorite(item.descriptor.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeSearchField(query: String, onQueryChange: (String) -> Unit, onClearFocus: () -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(text = stringResource(R.string.feature_home_search_hint)) },
        leadingIcon = { Icon(imageVector = Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = {
                        onQueryChange("")
                        onClearFocus()
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.feature_home_clear_search),
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onClearFocus() }),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(HOME_SEARCH_FIELD_TEST_TAG),
    )
}

@Composable
private fun HomePinnedSection(pinnedTools: List<HomeToolItem>, onItemClick: (HomeToolItem) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.small)) {
        ToolboxSectionHeader(title = stringResource(R.string.feature_home_section_pinned))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.small)) {
            items(items = pinnedTools, key = { "pinned_${it.descriptor.id}" }) { item ->
                PinnedToolChipCard(item = item, onClick = { onItemClick(item) })
            }
        }
    }
}

@Composable
private fun HomeRecentSection(recentTools: List<HomeToolItem>, onItemClick: (HomeToolItem) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.small)) {
        ToolboxSectionHeader(title = stringResource(R.string.feature_home_section_recent))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.small)) {
            items(items = recentTools, key = { "recent_${it.descriptor.id}" }) { item ->
                AssistChip(
                    onClick = { onItemClick(item) },
                    label = { Text(text = stringResource(item.descriptor.titleRes)) },
                    leadingIcon = {
                        Icon(
                            imageVector = item.descriptor.iconKey.toImageVector(),
                            contentDescription = null,
                            modifier = Modifier.size(ToolboxSizes.iconSmall),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun CategoryFilterRow(selectedCategory: ToolCategory?, onSelectCategory: (ToolCategory?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
    ) {
        FilterChip(
            selected = selectedCategory == null,
            onClick = { onSelectCategory(null) },
            label = { Text(text = stringResource(R.string.feature_home_category_all)) },
        )
        ToolCategory.entries.sortedBy { it.sortOrder }.forEach { category ->
            FilterChip(
                selected = selectedCategory == category,
                onClick = { onSelectCategory(category) },
                label = { Text(text = categoryLabel(category)) },
            )
        }
    }
}

@Composable
private fun PinnedToolChipCard(item: HomeToolItem, onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.width(164.dp),
    ) {
        Column(
            modifier = Modifier.padding(ToolboxSpacing.mediumSmall),
            verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = item.descriptor.iconKey.toImageVector(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                AccessStatusPill(
                    accessStatus = item.accessStatus,
                    requiredTierRes = item.descriptor.requiredTier.labelRes(),
                )
            }
            Text(
                text = stringResource(item.descriptor.titleRes),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ToolCatalogCard(item: HomeToolItem, isSelected: Boolean, onClick: () -> Unit, onTogglePin: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ToolboxSpacing.medium),
            horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.mediumSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolIconBadge(iconKey = item.descriptor.iconKey, isSelected = isSelected)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.extraSmall),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(item.descriptor.titleRes),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(ToolboxSpacing.small))
                    AccessStatusPill(
                        accessStatus = item.accessStatus,
                        requiredTierRes = item.descriptor.requiredTier.labelRes(),
                    )
                }
                Text(
                    text = stringResource(item.descriptor.descriptionRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onTogglePin) {
                Icon(
                    imageVector = Icons.Outlined.PushPin,
                    contentDescription = stringResource(
                        if (item.isPinned) R.string.feature_home_action_unpin else R.string.feature_home_action_pin,
                    ),
                    tint = if (item.isPinned) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun ToolIconBadge(iconKey: io.github.onprs.universitytoolbox.core.tool.api.ToolIconKey, isSelected: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Icon(
            imageVector = iconKey.toImageVector(),
            contentDescription = null,
            tint = if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(ToolboxSpacing.mediumSmall),
        )
    }
}

@Composable
private fun HomeToolDetailPane(
    item: HomeToolItem?,
    showBackButton: Boolean,
    onBackClick: () -> Unit,
    onLaunchTool: (HomeToolItem) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onOpenUnlock: () -> Unit,
) {
    if (item == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(ToolboxSpacing.large),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.feature_home_detail_placeholder),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val title = stringResource(item.descriptor.titleRes)
    val description = stringResource(item.descriptor.descriptionRes)
    val tierLabel = stringResource(item.descriptor.requiredTier.labelRes())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ToolboxSpacing.large),
        verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.medium),
    ) {
        if (showBackButton) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.feature_home_detail_back),
                )
            }
        }

        when (val status = item.accessStatus) {
            ToolAccessStatus.Available -> {
                AvailableToolDetailCard(
                    item = item,
                    title = title,
                    description = description,
                    tierLabel = tierLabel,
                    onLaunchTool = onLaunchTool,
                    onToggleFavorite = onToggleFavorite,
                )
            }
            is ToolAccessStatus.Locked -> {
                LockedToolCard(
                    toolTitle = title,
                    toolDescription = description,
                    requiredTier = item.descriptor.requiredTier,
                    denialReason = status.reason,
                    isRemoteDisabled = false,
                    actionLabel = stringResource(R.string.feature_home_action_unlock),
                    onUnlockClick = onOpenUnlock,
                )
            }
            is ToolAccessStatus.RemotelyDisabled -> {
                LockedToolCard(
                    toolTitle = title,
                    toolDescription = status.noticeMessage
                        ?: stringResource(R.string.feature_home_detail_disabled_default),
                    requiredTier = item.descriptor.requiredTier,
                    denialReason = null,
                    isRemoteDisabled = true,
                )
            }
        }
    }
}

@Composable
private fun AvailableToolDetailCard(
    item: HomeToolItem,
    title: String,
    description: String,
    tierLabel: String,
    onLaunchTool: (HomeToolItem) -> Unit,
    onToggleFavorite: (String) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(ToolboxSpacing.large),
            verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.mediumSmall),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.mediumSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = item.descriptor.iconKey.toImageVector(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(ToolboxSizes.iconLarge),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = stringResource(R.string.feature_home_detail_required_tier, tierLabel),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (item.lastUsedAtEpochMs != null) {
                ToolboxStatusBadge(
                    text = stringResource(R.string.feature_home_detail_last_used),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
            ) {
                AssistChip(
                    onClick = { onToggleFavorite(item.descriptor.id) },
                    label = {
                        Text(
                            text = stringResource(
                                if (item.isPinned) {
                                    R.string.feature_home_action_unpin
                                } else {
                                    R.string.feature_home_action_pin
                                },
                            ),
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.PushPin,
                            contentDescription = null,
                            modifier = Modifier.size(ToolboxSizes.iconSmall),
                        )
                    },
                )
                Spacer(modifier = Modifier.weight(1f))
                Button(onClick = { onLaunchTool(item) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.padding(end = ToolboxSpacing.extraSmall),
                    )
                    Text(text = stringResource(R.string.feature_home_action_open))
                }
            }
        }
    }
}

@Composable
private fun AccessStatusPill(accessStatus: ToolAccessStatus, requiredTierRes: Int) {
    when (accessStatus) {
        ToolAccessStatus.Available -> {
            ToolboxStatusBadge(
                text = stringResource(R.string.feature_home_status_ready),
                containerColor = ToolboxTheme.extendedColors.successContainer,
                contentColor = ToolboxTheme.extendedColors.onSuccessContainer,
            )
        }
        is ToolAccessStatus.Locked -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(14.dp),
                )
                ToolboxStatusBadge(
                    text = stringResource(R.string.feature_home_status_locked, stringResource(requiredTierRes)),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        is ToolAccessStatus.RemotelyDisabled -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp),
                )
                ToolboxStatusBadge(
                    text = stringResource(R.string.feature_home_status_disabled),
                    containerColor = ToolboxTheme.extendedColors.warningContainer,
                    contentColor = ToolboxTheme.extendedColors.onWarningContainer,
                )
            }
        }
    }
}

@Composable
private fun categoryLabel(category: ToolCategory): String = when (category) {
    ToolCategory.ACADEMIC -> stringResource(R.string.feature_home_category_academic)
    ToolCategory.EFFICIENCY -> stringResource(R.string.feature_home_category_efficiency)
    ToolCategory.MEDIA_UTILITY -> stringResource(R.string.feature_home_category_media)
    ToolCategory.SYSTEM -> stringResource(R.string.feature_home_category_system)
}
