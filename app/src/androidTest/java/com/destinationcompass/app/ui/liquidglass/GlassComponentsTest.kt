package com.destinationcompass.app.ui.liquidglass

import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyant.backdrop.backdrops.emptyBackdrop
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GlassComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun disabledGlassButtonKeepsButtonSemantics() {
        composeRule.setContent {
            MaterialTheme {
                GlassButton(
                    onClick = {},
                    backdrop = emptyBackdrop(),
                    enabled = false
                ) {
                    Text("禁用操作")
                }
            }
        }

        composeRule.onNodeWithText("禁用操作")
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertIsNotEnabled()
    }

    @Test
    fun navigationItemsRemainReachableAboveGlassRendering() {
        var selectedIndex = 1
        composeRule.setContent {
            MaterialTheme {
                GlassNavigationBar(
                    backdrop = emptyBackdrop(),
                    selectedIndex = selectedIndex,
                    items = listOf(
                        GlassNavigationItem("地点", Icons.Filled.Map, Icons.Outlined.Map),
                        GlassNavigationItem("罗盘", Icons.Filled.Explore, Icons.Outlined.Explore)
                    ),
                    onItemSelected = { selectedIndex = it },
                    modifier = Modifier.height(72.dp)
                )
            }
        }

        composeRule.onNodeWithText("地点").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText("罗盘").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText("地点").performClick()
        composeRule.runOnIdle { assert(selectedIndex == 0) }
    }
}
