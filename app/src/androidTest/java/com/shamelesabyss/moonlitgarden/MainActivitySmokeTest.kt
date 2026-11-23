package com.shamelesabyss.moonlitgarden

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun nativeGardenScreenLaunchesAndHasNoInternetPermission() {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("The cottage garden").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("The cottage garden").assertIsDisplayed()
        composeRule.onNodeWithText("Tend the nine beds").assertIsDisplayed()
        val context = composeRule.activity
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.INTERNET))
    }
}
