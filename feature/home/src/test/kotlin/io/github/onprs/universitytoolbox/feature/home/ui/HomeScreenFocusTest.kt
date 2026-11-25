package io.github.onprs.universitytoolbox.feature.home.ui

import android.app.Application
import android.content.ComponentName
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import io.github.onprs.universitytoolbox.core.designsystem.theme.UniversityToolboxTheme
import io.github.onprs.universitytoolbox.core.license.api.LicenseEntitlement
import io.github.onprs.universitytoolbox.core.license.api.LicenseState
import io.github.onprs.universitytoolbox.core.license.api.LicenseTier
import io.github.onprs.universitytoolbox.core.tool.api.ToolCategory
import io.github.onprs.universitytoolbox.feature.home.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

@RunWith(RobolectricTestRunner::class)
class HomeScreenFocusTest {
    private val composeTestRule = createComposeRule()

    private val ensureActivityRegisteredRule = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                val app = ApplicationProvider.getApplicationContext<Application>()
                val component = ComponentName(app.packageName, ComponentActivity::class.java.name)
                val shadowPm = Shadows.shadowOf(app.packageManager)
                val activityInfo = ActivityInfo().apply {
                    packageName = component.packageName
                    name = component.className
                    exported = true
                }
                shadowPm.addOrUpdateActivity(activityInfo)
                base.evaluate()
            }
        }
    }

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(ensureActivityRegisteredRule).around(composeTestRule)

    @Test
    fun `home screen does not auto focus search field on initial launch until user taps it`() {
        val state = HomeUiState(
            licenseState = LicenseState.Unlocked(
                LicenseEntitlement(
                    serialId = "SN-TEST",
                    tier = LicenseTier.FREE,
                    grantedScopes = setOf("*"),
                    maxSubAccounts = 0,
                    expiresAtEpochSec = null,
                    isOfficialEngine = true,
                ),
            ),
        )

        composeTestRule.setContent {
            UniversityToolboxTheme {
                HomeScreen(
                    uiState = state,
                    onSearchQueryChange = {},
                    onSelectCategory = {},
                    onSelectTool = {},
                    onToggleFavorite = {},
                    onLaunchTool = {},
                    onOpenSettings = {},
                    onOpenUnlock = { _, _ -> },
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(HOME_SEARCH_FIELD_TEST_TAG).assertIsNotFocused()

        composeTestRule.onNodeWithTag(HOME_SEARCH_FIELD_TEST_TAG).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(HOME_SEARCH_FIELD_TEST_TAG).assertIsFocused()
    }

    @Test
    fun `empty search result displays quick reset button that clears query and category`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val resetLabel = app.getString(R.string.feature_home_action_reset_filter)
        var latestQuery = "unknown_keyword"
        var latestCategory: ToolCategory? = ToolCategory.ACADEMIC

        val state = HomeUiState(
            searchQuery = latestQuery,
            selectedCategory = latestCategory,
            filteredTools = emptyList(),
            licenseState = LicenseState.Unlocked(
                LicenseEntitlement(
                    serialId = "SN-EMPTY",
                    tier = LicenseTier.FREE,
                    grantedScopes = setOf("*"),
                    maxSubAccounts = 0,
                    expiresAtEpochSec = null,
                    isOfficialEngine = true,
                ),
            ),
        )

        composeTestRule.setContent {
            UniversityToolboxTheme {
                HomeScreen(
                    uiState = state,
                    onSearchQueryChange = { latestQuery = it },
                    onSelectCategory = { latestCategory = it },
                    onSelectTool = {},
                    onToggleFavorite = {},
                    onLaunchTool = {},
                    onOpenSettings = {},
                    onOpenUnlock = { _, _ -> },
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(resetLabel))
        composeTestRule.onNodeWithText(resetLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText(resetLabel).performClick()
        composeTestRule.waitForIdle()

        assertEquals("", latestQuery)
        assertNull(latestCategory)
    }
}
