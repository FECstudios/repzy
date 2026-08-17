package com.repzy.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.repzy.app.R
import com.repzy.app.ui.home.HomeScreen
import com.repzy.app.ui.library.ExerciseDetailScreen
import com.repzy.app.ui.library.ExerciseLibraryScreen
import com.repzy.app.ui.nutrition.NutritionScreen
import com.repzy.app.ui.settings.SettingsScreen
import com.repzy.app.ui.workout.WorkoutScreen
import com.repzy.app.ui.workout.WorkoutViewModel
import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
object NutritionRoute

@Serializable
object LibraryRoute

@Serializable
object SettingsRoute

@Serializable
data class ExerciseDetailRoute(val id: String)

/** Antrenman sekmesi kendi alt grafiği: seans ekranı ile egzersiz seçici ViewModel'i paylaşır. */
@Serializable
object WorkoutGraph

@Serializable
object WorkoutRoute

@Serializable
object WorkoutPickerRoute

private data class TabItem(val route: Any, val labelRes: Int, val icon: ImageVector)

private val TABS = listOf(
    TabItem(HomeRoute, R.string.tab_home, Icons.Default.Home),
    TabItem(WorkoutGraph, R.string.tab_workout, Icons.Default.PlayCircleOutline),
    TabItem(NutritionRoute, R.string.tab_nutrition, Icons.Default.RestaurantMenu),
    TabItem(LibraryRoute, R.string.tab_library, Icons.Default.FitnessCenter),
    TabItem(SettingsRoute, R.string.tab_settings, Icons.Default.Settings),
)

/** Oturum açıldıktan sonraki alan. Onboarding ve auth bu grafiğin dışında. */
@Composable
fun AppNavHost(
    onSignOut: () -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // Detay ve seçici tam ekran açılır — alt çubuk gizlenir.
    val showBottomBar = currentDestination?.hasRoute<ExerciseDetailRoute>() != true &&
        currentDestination?.hasRoute<WorkoutPickerRoute>() != true

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TABS.forEach { tab ->
                        val routeName = tab.route::class.qualifiedName
                        NavigationBarItem(
                            selected = currentDestination
                                ?.selfAndAncestors()
                                ?.any { it.route == routeName } == true,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(HomeRoute) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { insets ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.padding(insets),
        ) {
            composable<HomeRoute> {
                HomeScreen()
            }

            composable<SettingsRoute> {
                // Çıkış Ana sayfa'dan buraya taşındı: beş sekmede Home'un altındaki
                // "Çıkış yap" bağlantısı yanlış yere düşüyordu.
                SettingsScreen(onSignOut = onSignOut)
            }

            navigation<WorkoutGraph>(startDestination = WorkoutRoute) {
                composable<WorkoutRoute> { entry ->
                    val graphEntry = remember(entry) {
                        navController.getBackStackEntry(WorkoutGraph)
                    }
                    val viewModel: WorkoutViewModel = hiltViewModel(graphEntry)
                    WorkoutScreen(
                        viewModel = viewModel,
                        onAddExerciseClick = { navController.navigate(WorkoutPickerRoute) },
                    )
                }
                composable<WorkoutPickerRoute> { entry ->
                    val graphEntry = remember(entry) {
                        navController.getBackStackEntry(WorkoutGraph)
                    }
                    val viewModel: WorkoutViewModel = hiltViewModel(graphEntry)
                    // Kütüphane ekranı seçici olarak yeniden kullanılıyor — arama ve filtreler bedava geliyor.
                    ExerciseLibraryScreen(
                        titleRes = R.string.workout_pick_exercise,
                        onExerciseClick = { id ->
                            viewModel.addExercise(id)
                            navController.popBackStack()
                        },
                    )
                }
            }

            composable<NutritionRoute> {
                NutritionScreen()
            }

            composable<LibraryRoute> {
                ExerciseLibraryScreen(
                    onExerciseClick = { id -> navController.navigate(ExerciseDetailRoute(id)) },
                )
            }
            composable<ExerciseDetailRoute> {
                ExerciseDetailScreen(
                    onBack = { navController.popBackStack() },
                    onExerciseClick = { id -> navController.navigate(ExerciseDetailRoute(id)) },
                )
            }
        }
    }
}

/** Hedefin kendisi ve üst grafikleri — sekme seçimi alt grafikte de doğru kalsın. */
private fun NavDestination.selfAndAncestors(): Sequence<NavDestination> =
    generateSequence(this) { it.parent }
