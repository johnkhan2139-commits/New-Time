package com.example.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.R
import com.example.data.local.UserProfileEntity
import com.example.ui.screens.ChatDetailScreen
import com.example.ui.screens.ChatsScreen
import com.example.ui.screens.FirebaseGuideScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PolicyScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SafetyScreen
import com.example.ui.screens.VideoStudioScreen
import com.example.ui.viewmodel.SessionUiState
import com.example.ui.viewmodel.TimeViewModel

private data class TopTab(
    val route: Any,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
)

@Composable
fun TimeAppShell(viewModel: TimeViewModel) {
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showUnauthGuide by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.statusMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    when (val state = sessionState) {
        is SessionUiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        is SessionUiState.Unauthenticated -> {
            if (showUnauthGuide) {
                FirebaseGuideScreen(onBack = { showUnauthGuide = false })
            } else {
                OnboardingScreen(
                    existingProfile = state.existingProfile,
                    onCreateProfile = viewModel::createProfile,
                    onResumeProfile = viewModel::resumeExistingProfile,
                    onOpenFirebaseGuide = { showUnauthGuide = true }
                )
            }
        }

        is SessionUiState.Authenticated -> {
            AuthenticatedWorkspace(
                profile = state.profile,
                viewModel = viewModel,
                snackbarHostState = snackbarHostState
            )
        }
    }
}

@Composable
private fun AuthenticatedWorkspace(
    profile: UserProfileEntity,
    viewModel: TimeViewModel,
    snackbarHostState: SnackbarHostState
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val activeThreads by viewModel.activeThreads.collectAsStateWithLifecycle()
    val blockedThreads by viewModel.blockedThreads.collectAsStateWithLifecycle()
    val safetyReports by viewModel.safetyReports.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    val turnConfig by viewModel.turnConfig.collectAsStateWithLifecycle()

    val tabs = remember {
        listOf(
            TopTab(ChatsRoute, R.string.nav_chats, Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline, "tab_chats"),
            TopTab(VideoStudioRoute, R.string.nav_video, Icons.Filled.Videocam, Icons.Outlined.Videocam, "tab_video"),
            TopTab(SafetyRoute, R.string.nav_safety, Icons.Filled.Security, Icons.Outlined.Security, "tab_safety"),
            TopTab(ProfileRoute, R.string.nav_profile, Icons.Filled.Person, Icons.Outlined.Person, "tab_profile")
        )
    }

    val showTopLevelChrome = currentDestination?.let { dest ->
        dest.hasRoute<ChatsRoute>() ||
            dest.hasRoute<VideoStudioRoute>() ||
            dest.hasRoute<SafetyRoute>() ||
            dest.hasRoute<ProfileRoute>()
    } ?: true

    fun navigateToTab(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (showTopLevelChrome && !useNavigationRail) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        tabs.forEach { tab ->
                            val selected = when (tab.route) {
                                is ChatsRoute -> currentDestination?.hasRoute<ChatsRoute>() == true
                                is VideoStudioRoute -> currentDestination?.hasRoute<VideoStudioRoute>() == true
                                is SafetyRoute -> currentDestination?.hasRoute<SafetyRoute>() == true
                                is ProfileRoute -> currentDestination?.hasRoute<ProfileRoute>() == true
                                else -> false
                            }
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.tag)
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
                if (showTopLevelChrome && useNavigationRail) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                        tabs.forEach { tab ->
                            val selected = when (tab.route) {
                                is ChatsRoute -> currentDestination?.hasRoute<ChatsRoute>() == true
                                is VideoStudioRoute -> currentDestination?.hasRoute<VideoStudioRoute>() == true
                                is SafetyRoute -> currentDestination?.hasRoute<SafetyRoute>() == true
                                is ProfileRoute -> currentDestination?.hasRoute<ProfileRoute>() == true
                                else -> false
                            }
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ChatsRoute,
                    modifier = Modifier.weight(1f)
                ) {
                    composable<ChatsRoute> {
                        ChatsScreen(
                            threads = activeThreads,
                            searchQuery = searchQuery,
                            onSearchChange = viewModel::updateSearchQuery,
                            onThreadClick = { threadId ->
                                navController.navigate(ChatDetailRoute(threadId))
                            },
                            onCreateThread = { name, handle ->
                                viewModel.createThread(name, handle) { createdId ->
                                    navController.navigate(ChatDetailRoute(createdId))
                                }
                            },
                            onOpenVideoStudio = { navigateToTab(VideoStudioRoute) },
                            onOpenFirebaseGuide = { navController.navigate(FirebaseGuideRoute) }
                        )
                    }

                    composable<ChatDetailRoute> { entry ->
                        val args = entry.toRoute<ChatDetailRoute>()
                        val thread by viewModel.observeThread(args.threadId).collectAsStateWithLifecycle()
                        val messages by viewModel.observeMessages(args.threadId).collectAsStateWithLifecycle()

                        ChatDetailScreen(
                            thread = thread,
                            messages = messages,
                            onBack = { navController.popBackStack() },
                            onSendMessage = { body, mediaUri ->
                                viewModel.sendMessage(args.threadId, body, mediaUri)
                            },
                            onDeleteMessage = viewModel::deleteMessage,
                            onBlockUser = { viewModel.blockThread(args.threadId, true) },
                            onReportUser = { category, details ->
                                val target = thread
                                viewModel.submitReport(
                                    targetName = target?.contactName ?: "Unknown",
                                    targetHandle = target?.contactHandle ?: "",
                                    category = category,
                                    details = details
                                )
                            },
                            onDeleteThread = {
                                viewModel.deleteThread(args.threadId) {
                                    navController.popBackStack()
                                }
                            },
                            onOpenVideoStudio = { navigateToTab(VideoStudioRoute) }
                        )
                    }

                    composable<VideoStudioRoute> {
                        VideoStudioScreen(
                            turnConfig = turnConfig,
                            onSaveTurnConfig = viewModel::saveTurnConfig,
                            onOpenFirebaseGuide = { navController.navigate(FirebaseGuideRoute) }
                        )
                    }

                    composable<SafetyRoute> {
                        SafetyScreen(
                            blockedThreads = blockedThreads,
                            safetyReports = safetyReports,
                            onUnblockThread = { threadId -> viewModel.blockThread(threadId, false) },
                            onSubmitReport = viewModel::submitReport,
                            onUpdateReportStatus = viewModel::updateReportStatus,
                            onOpenPrivacyPolicy = { navController.navigate(PolicyRoute(isTerms = false)) },
                            onOpenTerms = { navController.navigate(PolicyRoute(isTerms = true)) }
                        )
                    }

                    composable<ProfileRoute> {
                        ProfileScreen(
                            profile = profile,
                            isDarkTheme = isDarkTheme,
                            onToggleDarkTheme = viewModel::toggleDarkTheme,
                            onSaveProfile = viewModel::updateProfile,
                            onOpenFirebaseGuide = { navController.navigate(FirebaseGuideRoute) },
                            onSignOut = viewModel::signOut,
                            onDeleteAccount = viewModel::deleteAccountAndAllData
                        )
                    }

                    composable<PolicyRoute> { entry ->
                        val args = entry.toRoute<PolicyRoute>()
                        PolicyScreen(
                            isTerms = args.isTerms,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable<FirebaseGuideRoute> {
                        FirebaseGuideScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
