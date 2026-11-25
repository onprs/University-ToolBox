package io.github.onprs.universitytoolbox

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dagger.hilt.android.AndroidEntryPoint
import io.github.onprs.universitytoolbox.core.datastore.ThemeMode
import io.github.onprs.universitytoolbox.core.designsystem.theme.UniversityToolboxTheme
import io.github.onprs.universitytoolbox.core.tool.api.EntryProviderInstaller
import io.github.onprs.universitytoolbox.core.tool.api.installAll
import io.github.onprs.universitytoolbox.core.ui.LoadingStateView
import io.github.onprs.universitytoolbox.feature.home.navigation.HomeNavKey
import io.github.onprs.universitytoolbox.feature.home.ui.HomeRoute
import io.github.onprs.universitytoolbox.feature.settings.navigation.SettingsNavKey
import io.github.onprs.universitytoolbox.feature.settings.ui.SettingsRoute
import io.github.onprs.universitytoolbox.feature.unlock.navigation.UnlockNavKey
import io.github.onprs.universitytoolbox.feature.unlock.ui.UnlockRoute
import io.github.onprs.universitytoolbox.navigation.GateEffect
import io.github.onprs.universitytoolbox.navigation.GateUiPhase
import javax.inject.Inject

/**
 * 宿主单 Activity 容器：接入 SplashScreen、Edge-to-Edge、全局主题及响应式 Navigation 3 授权门禁。
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var entryInstallers: Set<@JvmSuppressWildcards EntryProviderInstaller>

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            consumeIncomingIntent(intent)
        }

        setContent {
            val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
            val snackbarHostState = remember { SnackbarHostState() }
            val context = LocalContext.current

            LaunchedEffect(Unit) {
                mainViewModel.notices.collect { effect ->
                    val message = when (effect) {
                        is GateEffect.ShowToolLockedNotice ->
                            context.getString(R.string.app_tool_locked_notice)
                        is GateEffect.ShowToolDisabledNotice ->
                            effect.message?.takeIf { it.isNotBlank() }
                                ?: context.getString(R.string.app_tool_remotely_disabled_default)
                        is GateEffect.ShowToolNotFoundNotice ->
                            context.getString(R.string.app_tool_not_found_notice)
                        is GateEffect.RecordToolOpened -> null
                    }
                    if (!message.isNullOrBlank()) {
                        snackbarHostState.showSnackbar(message)
                    }
                }
            }

            val isDarkTheme = when (uiState.userPreferences.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            DisposableEffect(isDarkTheme) {
                val statusBarStyle = if (isDarkTheme) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                val navigationBarStyle = if (isDarkTheme) {
                    SystemBarStyle.dark(defaultDarkScrim)
                } else {
                    SystemBarStyle.light(defaultLightScrim, defaultDarkScrim)
                }
                enableEdgeToEdge(
                    statusBarStyle = statusBarStyle,
                    navigationBarStyle = navigationBarStyle,
                )
                onDispose {}
            }

            UniversityToolboxTheme(
                darkTheme = isDarkTheme,
                dynamicColor = uiState.userPreferences.useDynamicColor,
            ) {
                ToolboxHostContent(
                    phase = uiState.gatePhase,
                    snackbarHostState = snackbarHostState,
                    entryInstallers = entryInstallers,
                    onOpenTool = mainViewModel::openTool,
                    onOpenSettings = mainViewModel::openSettings,
                    onOpenUnlock = mainViewModel::openUnlock,
                    onPopBack = {
                        if (!mainViewModel.popBackIfPossible()) {
                            finish()
                        }
                    },
                    onExitApp = ::finish,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val rawUri = intent.dataString
        val sanitizedIntent = Intent(intent).apply {
            data = null
        }
        setIntent(sanitizedIntent)
        mainViewModel.onIncomingUri(rawUri)
    }

    private fun consumeIncomingIntent(incomingIntent: Intent?) {
        val rawUri = incomingIntent?.dataString ?: return
        incomingIntent.data = null
        mainViewModel.onIncomingUri(rawUri)
    }

    private companion object {
        val defaultLightScrim = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
        val defaultDarkScrim = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
    }
}

@Composable
private fun ToolboxHostContent(
    phase: GateUiPhase,
    snackbarHostState: SnackbarHostState,
    entryInstallers: Set<EntryProviderInstaller>,
    onOpenTool: (NavKey) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenUnlock: (initialCode: String?, manageMode: Boolean) -> Unit,
    onPopBack: () -> Unit,
    onExitApp: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (phase) {
                GateUiPhase.Initializing -> {
                    LoadingStateView(
                        message = stringResource(R.string.app_initializing_message),
                    )
                }

                is GateUiPhase.Locked -> {
                    LockedNavigationHost(
                        phase = phase,
                        onExitApp = onExitApp,
                    )
                }

                is GateUiPhase.Unlocked -> {
                    UnlockedNavigationHost(
                        backStack = phase.backStack,
                        entryInstallers = entryInstallers,
                        onOpenTool = onOpenTool,
                        onOpenSettings = onOpenSettings,
                        onOpenUnlock = onOpenUnlock,
                        onPopBack = onPopBack,
                    )
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
            )
        }
    }
}

@Composable
private fun LockedNavigationHost(phase: GateUiPhase.Locked, onExitApp: () -> Unit) {
    val lockedBackStack = remember(phase.initialActivationCode) {
        listOf<NavKey>(
            UnlockNavKey(
                initialActivationCode = phase.initialActivationCode,
                manageMode = false,
            ),
        )
    }

    NavDisplay(
        backStack = lockedBackStack,
        onBack = onExitApp,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<UnlockNavKey> { key ->
                UnlockRoute(
                    initialActivationCode = key.initialActivationCode,
                    manageMode = false,
                )
            }
        },
    )
}

@Composable
private fun UnlockedNavigationHost(
    backStack: List<NavKey>,
    entryInstallers: Set<EntryProviderInstaller>,
    onOpenTool: (NavKey) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenUnlock: (initialCode: String?, manageMode: Boolean) -> Unit,
    onPopBack: () -> Unit,
) {
    NavDisplay(
        backStack = backStack,
        onBack = onPopBack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<HomeNavKey> {
                HomeRoute(
                    onOpenTool = onOpenTool,
                    onOpenSettings = onOpenSettings,
                    onOpenUnlock = { initialCode, _ ->
                        onOpenUnlock(initialCode, true)
                    },
                )
            }
            entry<SettingsNavKey> {
                SettingsRoute(
                    onOpenUnlockManage = { onOpenUnlock(null, true) },
                    onBack = onPopBack,
                )
            }
            entry<UnlockNavKey> { key ->
                UnlockRoute(
                    initialActivationCode = key.initialActivationCode,
                    manageMode = true,
                    onBack = onPopBack,
                )
            }
            installAll(entryInstallers)
        },
    )
}
