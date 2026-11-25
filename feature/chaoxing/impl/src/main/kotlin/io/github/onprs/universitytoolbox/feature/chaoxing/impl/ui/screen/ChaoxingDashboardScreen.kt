package io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.onprs.universitytoolbox.core.designsystem.component.ToolboxLinearProgressBar
import io.github.onprs.universitytoolbox.core.designsystem.component.ToolboxStatusBadge
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxRadius
import io.github.onprs.universitytoolbox.core.designsystem.theme.ToolboxSpacing
import io.github.onprs.universitytoolbox.core.license.api.AccessDecision
import io.github.onprs.universitytoolbox.core.license.api.LicenseTier
import io.github.onprs.universitytoolbox.core.ui.DevStubBanner
import io.github.onprs.universitytoolbox.core.ui.EmptyStateView
import io.github.onprs.universitytoolbox.core.ui.LockedToolCard
import io.github.onprs.universitytoolbox.feature.chaoxing.api.ChaoxingNavKey
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.R
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingAccountEntity
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local.ChaoxingCourseEntity
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.domain.coordinator.ChaoxingAccountManager
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.state.ChaoxingDashboardUiState
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.viewmodel.ChaoxingDashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChaoxingDashboardScreen(
    viewModel: ChaoxingDashboardViewModel,
    onNavigate: (ChaoxingNavKey) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.chaoxing_dashboard_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.chaoxing_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigate(ChaoxingNavKey.SignRecords()) }) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = stringResource(R.string.chaoxing_records_title),
                        )
                    }
                    IconButton(onClick = { onNavigate(ChaoxingNavKey.Accounts) }) {
                        Icon(
                            imageVector = Icons.Outlined.ManageAccounts,
                            contentDescription = stringResource(R.string.chaoxing_accounts_title),
                        )
                    }
                    if (state.selectedAccount != null && state.accessDecision is AccessDecision.Granted) {
                        IconButton(onClick = viewModel::refreshCourses) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = stringResource(R.string.chaoxing_refresh),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        DashboardBody(
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            onSelectAccount = viewModel::selectAccount,
            onSearchChange = viewModel::updateSearchQuery,
            onToggleArchived = viewModel::toggleShowArchived,
            onRefreshCourses = viewModel::refreshCourses,
            onNavigate = onNavigate,
        )
    }
}

@Composable
private fun DashboardBody(
    state: ChaoxingDashboardUiState,
    modifier: Modifier,
    onSelectAccount: (String) -> Unit,
    onSearchChange: (String) -> Unit,
    onToggleArchived: (Boolean) -> Unit,
    onRefreshCourses: () -> Unit,
    onNavigate: (ChaoxingNavKey) -> Unit,
) {
    Column(modifier = modifier) {
        DevStubBanner(licenseState = state.licenseState)

        val decision = state.accessDecision
        if (decision is AccessDecision.Denied) {
            LockedToolCard(
                toolTitle = stringResource(R.string.chaoxing_tool_title),
                toolDescription = stringResource(R.string.chaoxing_tool_description),
                requiredTier = LicenseTier.STANDARD,
                denialReason = decision.reason,
                isRemoteDisabled = state.isRemoteDisabled,
                modifier = Modifier.padding(ToolboxSpacing.medium),
            )
            return
        }

        if (state.accounts.isEmpty()) {
            EmptyStateView(
                title = stringResource(R.string.chaoxing_empty_accounts_title),
                description = stringResource(R.string.chaoxing_empty_accounts_desc),
                icon = Icons.Outlined.School,
                actionLabel = stringResource(R.string.chaoxing_add_account),
                onAction = { onNavigate(ChaoxingNavKey.Accounts) },
            )
            return
        }

        if (state.isLoadingCourses) {
            ToolboxLinearProgressBar()
        }

        AccountSelectorStrip(
            accounts = state.accounts,
            selectedAccountId = state.selectedAccount?.accountId,
            onSelect = onSelectAccount,
        )

        CourseFilterHeader(
            searchQuery = state.searchQuery,
            showArchived = state.showArchived,
            errorMessage = state.errorMessage,
            onSearchChange = onSearchChange,
            onToggleArchived = onToggleArchived,
        )

        CourseListSection(
            courses = state.filteredCourses,
            selectedAccount = state.selectedAccount,
            onRefreshCourses = onRefreshCourses,
            onNavigate = onNavigate,
        )
    }
}

@Composable
private fun AccountSelectorStrip(
    accounts: List<ChaoxingAccountEntity>,
    selectedAccountId: String?,
    onSelect: (String) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = ToolboxSpacing.medium, vertical = ToolboxSpacing.small),
        horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.small),
    ) {
        items(accounts, key = { it.accountId }) { account ->
            val label = buildString {
                append(account.displayName.ifBlank { account.maskedAccount })
                if (account.isPrimary) append(" · 主账号")
                if (account.sessionStatus == ChaoxingAccountManager.SESSION_EXPIRED) append(" (需重登)")
            }
            FilterChip(
                selected = account.accountId == selectedAccountId,
                onClick = { onSelect(account.accountId) },
                label = { Text(text = label) },
            )
        }
    }
}

@Composable
private fun CourseFilterHeader(
    searchQuery: String,
    showArchived: Boolean,
    errorMessage: String?,
    onSearchChange: (String) -> Unit,
    onToggleArchived: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ToolboxSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.extraSmall),
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            singleLine = true,
            leadingIcon = { Icon(imageVector = Icons.Outlined.Search, contentDescription = null) },
            placeholder = { Text(text = stringResource(R.string.chaoxing_search_courses_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.extraSmall),
        ) {
            Checkbox(checked = showArchived, onCheckedChange = onToggleArchived)
            Text(
                text = stringResource(R.string.chaoxing_show_archived),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (!errorMessage.isNullOrBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(ToolboxRadius.chip),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(ToolboxSpacing.small),
                )
            }
        }
    }
}

@Composable
private fun CourseListSection(
    courses: List<ChaoxingCourseEntity>,
    selectedAccount: ChaoxingAccountEntity?,
    onRefreshCourses: () -> Unit,
    onNavigate: (ChaoxingNavKey) -> Unit,
) {
    if (courses.isEmpty()) {
        EmptyStateView(
            title = stringResource(R.string.chaoxing_empty_courses_title),
            description = stringResource(R.string.chaoxing_empty_courses_desc),
            actionLabel = stringResource(R.string.chaoxing_refresh),
            onAction = onRefreshCourses,
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(ToolboxSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.mediumSmall),
    ) {
        items(courses, key = { "${it.accountId}_${it.courseId}_${it.clazzId}" }) { course ->
            CourseCard(
                course = course,
                onClick = {
                    val accountId = selectedAccount?.accountId ?: course.accountId
                    onNavigate(
                        ChaoxingNavKey.CourseActivities(
                            accountId = accountId,
                            courseId = course.courseId,
                            clazzId = course.clazzId,
                            cpi = course.cpi,
                            courseName = course.name,
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun CourseCard(course: ChaoxingCourseEntity, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(ToolboxSpacing.mediumSmall),
            horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.mediumSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = course.coverUrl,
                contentDescription = course.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 76.dp, height = 56.dp)
                    .clip(RoundedCornerShape(ToolboxRadius.chip)),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(ToolboxSpacing.extraSmall),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ToolboxSpacing.extraSmall),
                ) {
                    if (course.isPinned) {
                        Icon(
                            imageVector = Icons.Outlined.PushPin,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        text = course.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val meta = listOf(course.teacherName, course.className)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (course.isArchived) {
                    ToolboxStatusBadge(
                        text = "已结课",
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
