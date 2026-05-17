package app.toebeans.android.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.toebeans.android.ui.home.HomeScreen
import app.toebeans.android.ui.icons.PawIcon
import app.toebeans.android.ui.medications.MedicationEditScreen
import app.toebeans.android.ui.nav.BottomNavItem
import app.toebeans.android.ui.nav.Destinations
import app.toebeans.android.ui.pets.PetDetailScreen
import app.toebeans.android.ui.pets.PetEditScreen
import app.toebeans.android.ui.pets.PetsScreen
import app.toebeans.android.ui.reminders.ReminderListScreen
import app.toebeans.android.ui.schedule.ScheduleCreateScreen
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

    // Bottom NavigationBar shows only on the three top-level tab destinations. Detail and
    // edit screens hide it so their own bottomBar (e.g. Save button) sits at the true
    // bottom of the viewport and isn't occluded by the tab strip. Standard Android pattern.
    val currentRoute = backStack?.destination?.route
    val showBottomNav = currentRoute in TOP_LEVEL_ROUTES

    Scaffold(
        bottomBar = {
            if (showBottomNav) {
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
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destinations.HOME,
        ) {
            composable(Destinations.HOME) {
                HomeScreen(
                    onAddPet = { navController.navigate(Destinations.PET_NEW_ROUTE) },
                    onPetClick = { petId -> navController.navigate(Destinations.petDetail(petId)) },
                    contentPadding = innerPadding,
                )
            }
            composable(Destinations.REMINDERS) {
                ReminderListScreen(
                    // Stub navigation: tap target is Schedule Detail (B7). Until that
                    // screen ships, taps are intentionally no-ops so the row stays
                    // visually tappable and the future wiring is a one-line change.
                    onScheduleClick = { /* no-op; B7 wires this through */ },
                    contentPadding = innerPadding,
                )
            }
            composable(Destinations.PETS) {
                PetsScreen(
                    onPetClick = { petId -> navController.navigate(Destinations.petDetail(petId)) },
                    onAddPet = { navController.navigate(Destinations.PET_NEW_ROUTE) },
                    contentPadding = innerPadding,
                )
            }
            composable(Destinations.SETTINGS) {
                SettingsScreen(contentPadding = innerPadding)
            }
            // ----- Stacked destinations. ContentPadding intentionally NOT forwarded; these
            // screens host their own TopAppBar in a Scaffold and consume insets themselves.
            composable(Destinations.PET_NEW_ROUTE) {
                PetEditScreen(
                    petId = null,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
            composable(
                route = Destinations.PET_DETAIL_ROUTE,
                arguments = listOf(navArgument(Destinations.Args.PET_ID) { type = NavType.StringType }),
            ) { entry ->
                val petId = entry.arguments?.getString(Destinations.Args.PET_ID) ?: return@composable
                PetDetailScreen(
                    petId = petId,
                    onBack = { navController.popBackStack() },
                    onEditPet = { navController.navigate(Destinations.petEdit(petId)) },
                    onAddMedication = { navController.navigate(Destinations.medicationNew(petId)) },
                    onMedicationClick = { medId ->
                        navController.navigate(Destinations.scheduleCreate(petId, medId))
                    },
                )
            }
            composable(
                route = Destinations.PET_EDIT_ROUTE,
                arguments = listOf(navArgument(Destinations.Args.PET_ID) { type = NavType.StringType }),
            ) { entry ->
                val petId = entry.arguments?.getString(Destinations.Args.PET_ID) ?: return@composable
                PetEditScreen(
                    petId = petId,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
            composable(
                route = Destinations.MEDICATION_NEW_ROUTE,
                arguments = listOf(navArgument(Destinations.Args.PET_ID) { type = NavType.StringType }),
            ) { entry ->
                val petId = entry.arguments?.getString(Destinations.Args.PET_ID) ?: return@composable
                MedicationEditScreen(
                    petId = petId,
                    medicationId = null,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
            composable(
                route = Destinations.MEDICATION_EDIT_ROUTE,
                arguments =
                    listOf(
                        navArgument(Destinations.Args.PET_ID) { type = NavType.StringType },
                        navArgument(Destinations.Args.MEDICATION_ID) { type = NavType.StringType },
                    ),
            ) { entry ->
                val petId = entry.arguments?.getString(Destinations.Args.PET_ID) ?: return@composable
                val medId = entry.arguments?.getString(Destinations.Args.MEDICATION_ID) ?: return@composable
                MedicationEditScreen(
                    petId = petId,
                    medicationId = medId,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
            composable(
                route = Destinations.SCHEDULE_CREATE_ROUTE,
                arguments =
                    listOf(
                        navArgument(Destinations.Args.PET_ID) { type = NavType.StringType },
                        navArgument(Destinations.Args.MEDICATION_ID) { type = NavType.StringType },
                    ),
            ) { entry ->
                val petId = entry.arguments?.getString(Destinations.Args.PET_ID) ?: return@composable
                val medId = entry.arguments?.getString(Destinations.Args.MEDICATION_ID) ?: return@composable
                ScheduleCreateScreen(
                    petId = petId,
                    medicationId = medId,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
        }
    }
}

// Tab destinations where the bottom NavigationBar should be visible. Anything else
// (detail/edit screens) hides the tab strip so the screen's own bottomBar reaches the
// bottom of the viewport.
private val TOP_LEVEL_ROUTES =
    setOf(Destinations.HOME, Destinations.REMINDERS, Destinations.PETS, Destinations.SETTINGS)

// Pets tab uses a hand-authored paw vector (see PawIcon.kt) so we don't have to pull in
// the ~5 MB material-icons-extended dep just for one glyph. Home and Settings stay on
// material-icons-core which is bundled with material3.
private fun iconFor(item: BottomNavItem): ImageVector =
    when (item) {
        BottomNavItem.HOME -> Icons.Filled.Home
        BottomNavItem.REMINDERS -> Icons.Filled.Notifications
        BottomNavItem.PETS -> PawIcon
        BottomNavItem.SETTINGS -> Icons.Filled.Settings
    }
