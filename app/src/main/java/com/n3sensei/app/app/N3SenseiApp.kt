package com.n3sensei.app.app

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.n3sensei.app.R
import com.n3sensei.app.app.navigation.AppScreen
import com.n3sensei.app.app.navigation.N3SenseiNavHost
import com.n3sensei.app.app.navigation.TopLevelDestination
import com.n3sensei.app.app.navigation.isInTab
import com.n3sensei.app.app.navigation.selectTab
import com.n3sensei.app.core.designsystem.icon.N3Icons
import com.n3sensei.app.core.designsystem.theme.N3Spacing

@Composable
fun N3SenseiApp(
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentScreen = AppScreen.from(currentDestination)

    Scaffold(
        topBar = {
            N3TopAppBar(
                titleRes = currentScreen?.titleRes ?: R.string.app_name,
                showNavigateUp = currentScreen?.isTabRoot == false,
                onNavigateUp = { navController.navigateUp() },
            )
        },
        bottomBar = {
            if (currentScreen?.isFocusSession != true) {
                N3NavigationBar(
                    currentDestination = currentDestination,
                    onSelect = navController::selectTab,
                )
            }
        },
    ) { innerPadding ->
        // Keep search fields and lists above the keyboard. On wide screens the content keeps a
        // readable width and is centred instead of stretching edge to edge.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            N3SenseiNavHost(
                navController = navController,
                modifier = Modifier.widthIn(max = N3Spacing.maxContentWidth).fillMaxSize(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun N3TopAppBar(
    @StringRes titleRes: Int,
    showNavigateUp: Boolean,
    onNavigateUp: () -> Unit,
) {
    CenterAlignedTopAppBar(
        title = {
            // Serif masthead, like the running head of a printed book.
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
        },
        navigationIcon = {
            if (showNavigateUp) {
                IconButton(onClick = onNavigateUp) {
                    Icon(N3Icons.NavigateUp, contentDescription = stringResource(R.string.common_navigate_up))
                }
            }
        },
    )
}

@Composable
private fun N3NavigationBar(
    currentDestination: NavDestination?,
    onSelect: (TopLevelDestination) -> Unit,
) {
    NavigationBar {
        TopLevelDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = currentDestination.isInTab(destination),
                onClick = { onSelect(destination) },
                // The label is announced by TalkBack, so the icon needs no description.
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { NavigationLabel(stringResource(destination.labelRes)) },
                modifier = Modifier.testTag(destination.testTag),
            )
        }
    }
}

/**
 * Five labels share the bar width. At large font scales they used to wrap inside words
 * ("Tran / g chủ") and the bar grew to a quarter of a small screen. The label grows with the
 * user's font scale up to [MAX_LABEL_SCALE], stays on one line, and TalkBack reads it in full.
 */
@Composable
private fun NavigationLabel(text: String) {
    val style = MaterialTheme.typography.labelMedium
    val fontScale = LocalDensity.current.fontScale
    val size = if (fontScale > MAX_LABEL_SCALE) style.fontSize * (MAX_LABEL_SCALE / fontScale) else style.fontSize
    Text(text, style = style.copy(fontSize = size), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

private const val MAX_LABEL_SCALE = 1.3f
