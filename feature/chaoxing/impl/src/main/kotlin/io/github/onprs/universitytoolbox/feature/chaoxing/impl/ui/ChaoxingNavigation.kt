package io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.onprs.universitytoolbox.feature.chaoxing.api.ChaoxingNavKey
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.screen.ChaoxingAccountsScreen
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.screen.ChaoxingActivitiesScreen
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.screen.ChaoxingDashboardScreen
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.screen.ChaoxingSignExecutionScreen
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.screen.ChaoxingSignRecordsScreen
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.viewmodel.ChaoxingAccountsViewModel
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.viewmodel.ChaoxingActivitiesViewModel
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.viewmodel.ChaoxingDashboardViewModel
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.viewmodel.ChaoxingSignExecutionViewModel
import io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui.viewmodel.ChaoxingSignRecordsViewModel
import kotlinx.serialization.json.Json

/**
 * 学习通业务内部自包含 Navigation 3 子路由容器，支持与宿主单栈或独立深链并存。
 */
@Composable
fun ChaoxingRootFlow(initialRoute: ChaoxingNavKey = ChaoxingNavKey.Dashboard, onExitToHost: () -> Unit) {
    ChaoxingSubNavigationContainer(
        initialRoute = initialRoute,
        onExitToHost = onExitToHost,
    ) { onNavigate, onBack, onFirstAccountBound ->
        registerChaoxingSubEntries(
            onNavigate = onNavigate,
            onBack = onBack,
            onFirstAccountBound = onFirstAccountBound,
        )
    }
}

/**
 * 学习通子路由栈核心调度容器：负责横竖屏配置变更恢复、系统返回手势拦截与重复入栈去重。
 */
@Composable
internal fun ChaoxingSubNavigationContainer(
    initialRoute: ChaoxingNavKey,
    onExitToHost: () -> Unit,
    registerEntries: EntryProviderScope<NavKey>.(
        onNavigate: (ChaoxingNavKey) -> Unit,
        onBack: () -> Unit,
        onFirstAccountBound: (String) -> Unit,
    ) -> Unit,
) {
    val currentOnExitToHost by rememberUpdatedState(onExitToHost)
    val subBackStack = rememberSaveable(initialRoute, saver = chaoxingSubBackStackSaver()) {
        mutableStateListOf<NavKey>(initialRoute)
    }
    val popSubRoute = remember(subBackStack) {
        {
            if (subBackStack.size > 1) {
                subBackStack.removeAt(subBackStack.lastIndex)
                Unit
            } else {
                currentOnExitToHost()
            }
        }
    }
    val provider = remember(subBackStack, registerEntries) {
        entryProvider {
            registerEntries(
                { target -> subBackStack.pushChaoxingRoute(target) },
                popSubRoute,
                { subBackStack.popOrResetToDashboard() },
            )
        }
    }

    // 在 NavDisplay 之前注册子路由级返回拦截：
    // 优先级高于外层宿主路由（防止二级/三级页面侧滑返回直接退出整个学习通模块），
    // 同时低于子页面内部覆盖层（如全屏地图选点或弹窗）注册的 BackHandler。
    BackHandler(enabled = subBackStack.size > 1, onBack = popSubRoute)

    NavDisplay(
        backStack = subBackStack,
        onBack = popSubRoute,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = provider,
    )
}

internal fun SnapshotStateList<NavKey>.pushChaoxingRoute(target: ChaoxingNavKey) {
    val currentTop = lastOrNull()
    if (currentTop == target) {
        return
    }
    if (currentTop is ChaoxingNavKey.Accounts && target is ChaoxingNavKey.Accounts) {
        this[lastIndex] = target
    } else {
        add(target)
    }
}

internal fun SnapshotStateList<NavKey>.popOrResetToDashboard() {
    val dashboardIndex = indexOfFirst { it is ChaoxingNavKey.Dashboard }
    if (dashboardIndex >= 0) {
        while (lastIndex > dashboardIndex) {
            removeAt(lastIndex)
        }
    } else {
        clear()
        add(ChaoxingNavKey.Dashboard)
    }
}

internal fun chaoxingSubBackStackSaver(): Saver<SnapshotStateList<NavKey>, ArrayList<String>> = Saver(
    save = { stateList ->
        val encoded = ArrayList<String>(stateList.size)
        for (entry in stateList) {
            if (entry is ChaoxingNavKey) {
                encoded.add(Json.encodeToString(ChaoxingNavKey.serializer(), entry))
            }
        }
        encoded
    },
    restore = { savedList ->
        val decoded = savedList.mapNotNull { raw ->
            runCatching { Json.decodeFromString(ChaoxingNavKey.serializer(), raw) }.getOrNull()
        }
        if (decoded.isEmpty()) {
            null
        } else {
            mutableStateListOf<NavKey>().apply { addAll(decoded) }
        }
    },
)

/**
 * 向宿主 `EntryProviderScope<NavKey>` 注册学习通全量路由。
 */
fun EntryProviderScope<NavKey>.installChaoxingEntries() {
    entry<ChaoxingNavKey.Dashboard> {
        val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
        ChaoxingRootFlow(
            initialRoute = ChaoxingNavKey.Dashboard,
            onExitToHost = { backDispatcher?.onBackPressed() },
        )
    }

    entry<ChaoxingNavKey.Accounts> { key ->
        val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
        ChaoxingRootFlow(
            initialRoute = key,
            onExitToHost = { backDispatcher?.onBackPressed() },
        )
    }

    entry<ChaoxingNavKey.CourseActivities> { key ->
        val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
        ChaoxingRootFlow(
            initialRoute = key,
            onExitToHost = { backDispatcher?.onBackPressed() },
        )
    }

    entry<ChaoxingNavKey.SignExecution> { key ->
        val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
        ChaoxingRootFlow(
            initialRoute = key,
            onExitToHost = { backDispatcher?.onBackPressed() },
        )
    }

    entry<ChaoxingNavKey.SignRecords> { key ->
        val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
        ChaoxingRootFlow(
            initialRoute = key,
            onExitToHost = { backDispatcher?.onBackPressed() },
        )
    }
}

private fun EntryProviderScope<NavKey>.registerChaoxingSubEntries(
    onNavigate: (ChaoxingNavKey) -> Unit,
    onBack: () -> Unit,
    onFirstAccountBound: (String) -> Unit = {},
) {
    entry<ChaoxingNavKey.Dashboard> {
        val viewModel: ChaoxingDashboardViewModel = hiltViewModel()
        ChaoxingDashboardScreen(
            viewModel = viewModel,
            onNavigate = onNavigate,
            onBack = onBack,
        )
    }

    entry<ChaoxingNavKey.Accounts> { key ->
        val viewModel: ChaoxingAccountsViewModel = hiltViewModel()
        ChaoxingAccountsScreen(
            viewModel = viewModel,
            initialSharePayload = key.initialSharePayload,
            onBack = onBack,
            onFirstAccountBound = onFirstAccountBound,
        )
    }

    entry<ChaoxingNavKey.CourseActivities> { key ->
        val viewModel = hiltViewModel<ChaoxingActivitiesViewModel, ChaoxingActivitiesViewModel.Factory>(
            key = "cx_activities_${key.accountId}_${key.courseId}_${key.clazzId}",
            creationCallback = { factory -> factory.create(key) },
        )
        ChaoxingActivitiesScreen(
            viewModel = viewModel,
            onOpenSignExecution = onNavigate,
            onBack = onBack,
        )
    }

    entry<ChaoxingNavKey.SignExecution> { key ->
        val viewModel = hiltViewModel<ChaoxingSignExecutionViewModel, ChaoxingSignExecutionViewModel.Factory>(
            key = "cx_sign_${key.accountId}_${key.activeId}",
            creationCallback = { factory -> factory.create(key) },
        )
        ChaoxingSignExecutionScreen(
            viewModel = viewModel,
            onBack = onBack,
            courseName = key.courseName,
        )
    }

    entry<ChaoxingNavKey.SignRecords> { key ->
        val viewModel = hiltViewModel<ChaoxingSignRecordsViewModel, ChaoxingSignRecordsViewModel.Factory>(
            key = "cx_records_${key.accountId ?: "all"}",
            creationCallback = { factory -> factory.create(key) },
        )
        ChaoxingSignRecordsScreen(
            viewModel = viewModel,
            onBack = onBack,
        )
    }
}
