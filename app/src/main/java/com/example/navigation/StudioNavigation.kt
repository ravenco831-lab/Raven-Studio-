package com.example.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.core.StudioPreferences
import com.example.core.rememberMediaPlaybackState
import com.example.data.remote.BackendMediaProviderClient
import com.example.data.repository.GeminiLyricsRepository
import com.example.data.repository.StudioRepository
import com.example.ui.screens.AiMusicScreen
import com.example.ui.screens.AudioEditorScreen
import com.example.ui.screens.CreateHubScreen
import com.example.ui.screens.DuetModeScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ImageToVideoScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.LyricsStudioScreen
import com.example.ui.screens.PhotoSingScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.StudioEditorScreen
import com.example.ui.theme.DeepGoldContainer
import com.example.ui.theme.MetallicGold
import com.example.ui.theme.MidnightBlack
import com.example.ui.theme.MutedSilver
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.SoftWhite
import com.example.viewmodel.DuetModeViewModel
import com.example.viewmodel.StudioViewModel

const val ROUTE_HOME = "home"
const val ROUTE_CREATE = "create"
const val ROUTE_STUDIO = "studio"
const val ROUTE_LIBRARY = "library"
const val ROUTE_PROFILE = "profile"

const val ROUTE_DUET_MODE = "duet_mode"
const val ROUTE_AI_MUSIC = "ai_music"
const val ROUTE_LYRICS_STUDIO = "lyrics_studio"
const val ROUTE_PHOTO_SING = "photo_sing"
const val ROUTE_IMAGE_TO_VIDEO = "image_to_video"
const val ROUTE_AUDIO_EDITOR = "audio_editor"

private data class StudioNavDestination(
    val route: String,
    val labelResId: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val TOP_LEVEL_DESTINATIONS = listOf(
    StudioNavDestination(
        route = ROUTE_HOME,
        labelResId = R.string.nav_home,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        testTag = "nav_home"
    ),
    StudioNavDestination(
        route = ROUTE_CREATE,
        labelResId = R.string.nav_create,
        selectedIcon = Icons.Filled.AddCircle,
        unselectedIcon = Icons.Outlined.AddCircleOutline,
        testTag = "nav_create"
    ),
    StudioNavDestination(
        route = ROUTE_STUDIO,
        labelResId = R.string.nav_studio,
        selectedIcon = Icons.Filled.Movie,
        unselectedIcon = Icons.Outlined.Movie,
        testTag = "nav_studio"
    ),
    StudioNavDestination(
        route = ROUTE_LIBRARY,
        labelResId = R.string.nav_library,
        selectedIcon = Icons.Filled.Folder,
        unselectedIcon = Icons.Outlined.FolderOpen,
        testTag = "nav_library"
    ),
    StudioNavDestination(
        route = ROUTE_PROFILE,
        labelResId = R.string.nav_profile,
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person,
        testTag = "nav_profile"
    )
)

@Composable
fun StudioRootNavigation() {
    val appContext = LocalContext.current.applicationContext
    val repository = remember(appContext) { StudioRepository(appContext) }
    val geminiRepo = remember { GeminiLyricsRepository() }
    val backendClient = remember(appContext) { BackendMediaProviderClient(appContext) }
    val preferences = remember(appContext) { StudioPreferences(appContext) }

    val studioViewModel: StudioViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                StudioViewModel(
                    repository = repository,
                    geminiRepo = geminiRepo,
                    backendClient = backendClient,
                    preferences = preferences
                )
            }
        }
    )

    val duetViewModel: DuetModeViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                DuetModeViewModel(
                    repository = repository,
                    geminiRepo = geminiRepo,
                    backendClient = backendClient
                )
            }
        }
    )

    val playbackState = rememberMediaPlaybackState()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route

    val showTopLevelChrome = currentRoute == null || TOP_LEVEL_DESTINATIONS.any { it.route == currentRoute }

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBlack)
    ) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MidnightBlack,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (showTopLevelChrome && !useNavRail) {
                    NavigationBar(
                        containerColor = ObsidianSurface,
                        contentColor = SoftWhite,
                        tonalElevation = 0.dp
                    ) {
                        TOP_LEVEL_DESTINATIONS.forEach { dest ->
                            val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                            val label = stringResource(dest.labelResId)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MetallicGold,
                                    selectedTextColor = MetallicGold,
                                    indicatorColor = DeepGoldContainer,
                                    unselectedIconColor = MutedSilver,
                                    unselectedTextColor = MutedSilver
                                ),
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showTopLevelChrome && useNavRail) {
                    NavigationRail(
                        containerColor = ObsidianSurface,
                        contentColor = SoftWhite,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        TOP_LEVEL_DESTINATIONS.forEach { dest ->
                            val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                            val label = stringResource(dest.labelResId)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MetallicGold,
                                    selectedTextColor = MetallicGold,
                                    indicatorColor = DeepGoldContainer,
                                    unselectedIconColor = MutedSilver,
                                    unselectedTextColor = MutedSilver
                                ),
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_HOME,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        composable(ROUTE_HOME) {
                            HomeScreen(
                                viewModel = studioViewModel,
                                onNavigateToDuetRavenBreeze = {
                                    duetViewModel.updateState {
                                        it.copy(
                                            singer1Name = "Raven",
                                            singer2Name = "Breeze",
                                            currentStep = 1
                                        )
                                    }
                                    navController.navigate(ROUTE_DUET_MODE)
                                },
                                onNavigateToCreateSong = { navController.navigate(ROUTE_AI_MUSIC) },
                                onNavigateToWriteLyrics = { navController.navigate(ROUTE_LYRICS_STUDIO) },
                                onNavigateToPhotoSing = { navController.navigate(ROUTE_PHOTO_SING) },
                                onNavigateToImageToVideo = { navController.navigate(ROUTE_IMAGE_TO_VIDEO) },
                                onNavigateToCreateDuet = { navController.navigate(ROUTE_DUET_MODE) },
                                onNavigateToUploadAudio = { navController.navigate(ROUTE_AUDIO_EDITOR) }
                            )
                        }

                        composable(ROUTE_CREATE) {
                            CreateHubScreen(
                                backendConfigured = studioViewModel.backendConfigured,
                                onOpenRavenBreezeDuet = { navController.navigate(ROUTE_DUET_MODE) },
                                onOpenAiMusic = { navController.navigate(ROUTE_AI_MUSIC) },
                                onOpenLyricsStudio = { navController.navigate(ROUTE_LYRICS_STUDIO) },
                                onOpenPhotoSing = { navController.navigate(ROUTE_PHOTO_SING) },
                                onOpenImageToVideo = { navController.navigate(ROUTE_IMAGE_TO_VIDEO) },
                                onOpenAudioEditor = { navController.navigate(ROUTE_AUDIO_EDITOR) },
                                onOpenVideoTimeline = { navigateTopLevel(ROUTE_STUDIO) }
                            )
                        }

                        composable(ROUTE_STUDIO) {
                            StudioEditorScreen(
                                viewModel = studioViewModel,
                                playbackState = playbackState,
                                onOpenDuetMode = { navController.navigate(ROUTE_DUET_MODE) }
                            )
                        }

                        composable(ROUTE_LIBRARY) {
                            LibraryScreen(
                                viewModel = studioViewModel,
                                onReopenProject = { project ->
                                    if (project.projectType == "DUET_RAVEN_BREEZE") {
                                        duetViewModel.loadDuetProject(project)
                                        navController.navigate(ROUTE_DUET_MODE)
                                    } else {
                                        studioViewModel.loadProjectIntoTimeline(project)
                                        navigateTopLevel(ROUTE_STUDIO)
                                    }
                                },
                                onReopenLyrics = { sheet ->
                                    studioViewModel.loadLyricSheet(sheet)
                                    navController.navigate(ROUTE_LYRICS_STUDIO)
                                },
                                onReopenAudio = { asset ->
                                    studioViewModel.loadAudioAssetForEditing(asset)
                                    navController.navigate(ROUTE_AUDIO_EDITOR)
                                }
                            )
                        }

                        composable(ROUTE_PROFILE) {
                            ProfileScreen(
                                viewModel = studioViewModel
                            )
                        }

                        composable(ROUTE_DUET_MODE) {
                            DuetModeScreen(
                                duetViewModel = duetViewModel,
                                studioViewModel = studioViewModel,
                                playbackState = playbackState,
                                onBack = { navController.popBackStack() },
                                onNavigateToAiSong = { navController.navigate(ROUTE_AI_MUSIC) }
                            )
                        }

                        composable(ROUTE_AI_MUSIC) {
                            AiMusicScreen(
                                viewModel = studioViewModel,
                                playbackState = playbackState,
                                onBack = { navController.popBackStack() },
                                onUseSongInDuet = { song ->
                                    duetViewModel.selectSongFromLibrary(song)
                                    navController.navigate(ROUTE_DUET_MODE)
                                }
                            )
                        }

                        composable(ROUTE_LYRICS_STUDIO) {
                            LyricsStudioScreen(
                                viewModel = studioViewModel,
                                onBack = { navController.popBackStack() },
                                onSendToMusicGenerator = {
                                    studioViewModel.sendLyricsToMusicGenerator()
                                    navController.navigate(ROUTE_AI_MUSIC)
                                }
                            )
                        }

                        composable(ROUTE_PHOTO_SING) {
                            PhotoSingScreen(
                                viewModel = studioViewModel,
                                playbackState = playbackState,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(ROUTE_IMAGE_TO_VIDEO) {
                            ImageToVideoScreen(
                                viewModel = studioViewModel,
                                playbackState = playbackState,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(ROUTE_AUDIO_EDITOR) {
                            AudioEditorScreen(
                                viewModel = studioViewModel,
                                playbackState = playbackState,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
