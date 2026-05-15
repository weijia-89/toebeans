package app.toebeans.android.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.toebeans.android.ui.home.HomeScreen
import app.toebeans.android.ui.nav.BottomNavItem
import app.toebeans.android.ui.nav.Destinations
import app.toebeans.android.ui.pets.PetsScreen
import app.toebeans.android.ui.settings.SettingsScreen

/**
 * Top-level app shell: a [Scaffold] hosting the bottom navigation bar and the [NavHost].
 *
 * State save/restore is handled by androidx.navigation's `saveState`/`restoreState` on
 * bottom-nav re-selection: switching tabs preserves each tab's back stack, and returning
 * to a previously-visited tab restores its scroll position. This is the standard pattern
 * from the official navigation samples.
 *
 * Stacked destinations (PetDetail, PetEdit, ScheduleCreate) are added in the next commit.
 * For this commit, taps that would navigate to those routes are no-ops.
 */
@Composable
public fun ToebeansAppShell() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()

    Scaffold(
        bottomBar = {
            NavigationBar {
                BottomNavItem.entries.forEach { item ->
                    val selected =
                        backStack?.destination?.hierarchy?.any { it.route == item.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(iconFor(item), contentDescription = null) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destinations.HOME,
        ) {
            composable(Destinations.HOME) {
                HomeScreen(
                    onAddPet = { navController.navigate(Destinations.PETS) },
                    contentPadding = innerPadding,
                )
            }
            composable(Destinations.PETS) {
                PetsScreen(
                    onPetClick = { /* milestone 1: navigate to Destinations.petDetail(petId) */ },
                    onAddPet = { /* milestone 1: navigate to a new-pet route */ },
                    contentPadding = innerPadding,
                )
            }
            composable(Destinations.SETTINGS) {
                SettingsScreen(contentPadding = innerPadding)
            }
        }
    }
}

// Using Icons.Filled.* from material-icons-core (always shipped with material3). The
// "Pets" icon (paw print) lives in material-icons-extended which would be a ~5 MB
// vibe-dangerous Gradle dep add. Favorite (heart) is a stand-in until we ship a custom
// paw-print vector asset in milestone 2.
private fun iconFor(item: BottomNavItem): ImageVector =
    when (item) {
        BottomNavItem.HOME -> Icons.Filled.Home
        BottomNavItem.PETS -> Icons.Filled.Favorite
        BottomNavItem.SETTINGS -> Icons.Filled.Settings
    }
