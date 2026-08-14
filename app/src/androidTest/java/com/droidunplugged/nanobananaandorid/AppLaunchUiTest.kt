package com.droidunplugged.nanobananaandorid

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.droidunplugged.nanobananaandorid.feature.chat.ChatScreen
import com.droidunplugged.nanobananaandorid.ui.theme.NanoBananaAndoridTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AppLaunchUiTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<TestActivity>()

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun appLaunchesAndDisplaysMainScreen() {
        composeTestRule.setContent {
            NanoBananaAndoridTheme {
                ChatScreen(
                    onShowAgentObservability = {}
                )
            }
        }

        // Assert top app bar title is visible
        composeTestRule
            .onNodeWithText("Commercial Killer", substring = true)
            .assertIsDisplayed()

        // Assert bottom panel header is visible
        composeTestRule
            .onNodeWithText("Agent Output & IR Signals", substring = true)
            .assertIsDisplayed()
    }
}
