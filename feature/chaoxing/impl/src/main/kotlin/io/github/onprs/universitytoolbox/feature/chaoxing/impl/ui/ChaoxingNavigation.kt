package io.github.onprs.universitytoolbox.feature.chaoxing.impl.ui

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
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

/**
 * 学习通业务内部自包含 Navigation 3 子路由容器，支持与宿主单栈或独立深链并存。
 */
@Composable
fun ChaoxingRootFlow(initialRoute: ChaoxingNavKey = ChaoxingNavKey.Dashboard, onExitToHost: () -> Unit) {
    val subBackStack = remember(initialRoute) { mutableStateListOf<NavKey>(initialRoute) }
    val popSubRoute: () -> Unit = {
        if (subBackStack.size > 1) {
            subBackStack.removeAt(subBackStack.lastIndex)
        } else {
            onExitToHost()
        }
    }

    NavDisplay(
        backStack = subBackStack,
        onBack = popSubRoute,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            registerChaoxingSubEntries(
                onNavigate = { target -> subBackStack += target },
                onBack = popSubRoute,
            )
        },
    )
}

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

    registerChaoxingSubEntries(
        onNavigate = { /* 深链直达子路由时由当前窗口承接或通过系统返回退出 */ },
        onBack = { /* 子路由直达时由宿主 NavDisplay 统一处理返回 */ },
        includeDashboard = false,
    )
}

private fun EntryProviderScope<NavKey>.registerChaoxingSubEntries(
    onNavigate: (ChaoxingNavKey) -> Unit,
    onBack: () -> Unit,
    includeDashboard: Boolean = true,
) {
    if (includeDashboard) {
        entry<ChaoxingNavKey.Dashboard> {
            val viewModel: ChaoxingDashboardViewModel = hiltViewModel()
            ChaoxingDashboardScreen(
                viewModel = viewModel,
                onNavigate = onNavigate,
                onBack = onBack,
            )
        }
    }

    entry<ChaoxingNavKey.Accounts> {
        val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
        val viewModel: ChaoxingAccountsViewModel = hiltViewModel()
        ChaoxingAccountsScreen(
            viewModel = viewModel,
            onBack = { if (includeDashboard) onBack() else backDispatcher?.onBackPressed() },
        )
    }

    entry<ChaoxingNavKey.CourseActivities> { key ->
        val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
        val viewModel = hiltViewModel<ChaoxingActivitiesViewModel, ChaoxingActivitiesViewModel.Factory>(
            key = "cx_activities_${key.accountId}_${key.courseId}_${key.clazzId}",
            creationCallback = { factory -> factory.create(key) },
        )
        ChaoxingActivitiesScreen(
            viewModel = viewModel,
            onOpenSignExecution = onNavigate,
            onBack = { if (includeDashboard) onBack() else backDispatcher?.onBackPressed() },
        )
    }

    entry<ChaoxingNavKey.SignExecution> { key ->
        val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
        val viewModel = hiltViewModel<ChaoxingSignExecutionViewModel, ChaoxingSignExecutionViewModel.Factory>(
            key = "cx_sign_${key.accountId}_${key.activeId}",
            creationCallback = { factory -> factory.create(key) },
        )
        ChaoxingSignExecutionScreen(
            viewModel = viewModel,
            onBack = { if (includeDashboard) onBack() else backDispatcher?.onBackPressed() },
        )
    }

    entry<ChaoxingNavKey.SignRecords> { key ->
        val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
        val viewModel = hiltViewModel<ChaoxingSignRecordsViewModel, ChaoxingSignRecordsViewModel.Factory>(
            key = "cx_records_${key.accountId ?: "all"}",
            creationCallback = { factory -> factory.create(key) },
        )
        ChaoxingSignRecordsScreen(
            viewModel = viewModel,
            onBack = { if (includeDashboard) onBack() else backDispatcher?.onBackPressed() },
        )
    }
}
