package com.example.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.R
import com.example.data.auth.GoogleAuthManager
import com.example.data.preferences.TimePreferences
import com.example.data.remote.OnlineMediaHelper
import com.example.data.remote.OnlineRepository
import com.example.ui.components.AvatarCircle
import com.example.ui.screens.ActiveCallScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatDetailScreen
import com.example.ui.screens.ChatsScreen
import com.example.ui.screens.FirebaseGuideScreen
import com.example.ui.screens.PolicyScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SafetyScreen
import com.example.ui.screens.VideoStudioScreen
import com.example.ui.theme.OnlineGreen
import com.example.ui.viewmodel.OnlineViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

private data class TopTab(
    val route: Any,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
)

@Composable
fun TimeAppShell(
    preferences: TimePreferences
) {
    val context = LocalContext.current
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val repository = remember(context) { OnlineRepository(context) }
    val mediaHelper = remember(context) { OnlineMediaHelper(context) }
    val scope = rememberCoroutineScope()

    var currentUserId by remember { mutableStateOf(Firebase.auth.currentUser?.uid) }
    var authChecked by remember { mutableStateOf(Firebase.auth.currentUser != null) }
    var showUnauthGuide by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUserId = auth.currentUser?.uid
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    LaunchedEffect(Unit) {
        if (!authChecked) {
            GoogleAuthManager.attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                    currentUserId = Firebase.auth.currentUser?.uid
                    authChecked = true
                },
                onUnauthenticated = {
                    authChecked = true
                },
                scope = scope
            )
        }
    }

    if (!authChecked) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val uid = currentUserId
    if (uid == null) {
        if (showUnauthGuide) {
            FirebaseGuideScreen(onBack = { showUnauthGuide = false })
        } else {
            AuthScreen(
                credentialManager = credentialManager,
                onAuthenticated = {
                    currentUserId = Firebase.auth.currentUser?.uid
                },
                onOpenFirebaseGuide = { showUnauthGuide = true }
            )
        }
        return
    }

    key(uid) {
        val onlineViewModel: OnlineViewModel = viewModel(
            key = "online_vm_$uid",
            factory = OnlineViewModel.Factory(
                repository = repository,
                userId = uid,
                preferences = preferences,
                mediaHelper = mediaHelper
            )
        )
        AuthenticatedOnlineWorkspace(
            viewModel = onlineViewModel,
            onSignOutClick = {
                GoogleAuthManager.signOut(
                    credentialManager = credentialManager,
                    scope = scope,
                    onBeforeSignOut = {
                        repository.updateOnlineStatus(uid, false)
                    },
                    onSignOutComplete = {
                        currentUserId = null
                    }
                )
            }
        )
    }
}

@Composable
private fun AuthenticatedOnlineWorkspace(
    viewModel: OnlineViewModel,
    onSignOutClick: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.statusMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val myProfile by viewModel.myProfile.collectAsStateWithLifecycle()
    val myPrivateInfo by viewModel.myPrivateInfo.collectAsStateWithLifecycle()
    val onlineFriends by viewModel.onlineFriends.collectAsStateWithLifecycle()
    val activeChats by viewModel.activeChats.collectAsStateWithLifecycle()
    val blockedUsers by viewModel.blockedUsers.collectAsStateWithLifecycle()
    val safetyReports by viewModel.reports.collectAsStateWithLifecycle()
    val incomingCall by viewModel.incomingRingingCall.collectAsStateWithLifecycle()
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
                            currentUserId = viewModel.currentUserId,
                            onlineFriends = onlineFriends,
                            chats = activeChats,
                            searchQuery = searchQuery,
                            onSearchChange = viewModel::updateSearchQuery,
                            onOpenDirectChat = { friend ->
                                viewModel.openDirectChat(friend) { chatId ->
                                    navController.navigate(ChatDetailRoute(chatId))
                                }
                            },
                            onCreateGroupChat = { groupName, members ->
                                viewModel.createGroupChat(groupName, members) { chatId ->
                                    navController.navigate(ChatDetailRoute(chatId))
                                }
                            },
                            onChatClick = { chatId ->
                                navController.navigate(ChatDetailRoute(chatId))
                            },
                            onStartDirectCall = { friend, callType ->
                                viewModel.startCall(
                                    calleeId = friend.userId,
                                    calleeName = friend.displayName,
                                    chatId = null,
                                    callType = callType
                                ) { callId ->
                                    navController.navigate(ActiveCallRoute(callId))
                                }
                            },
                            onOpenVideoStudio = { navigateToTab(VideoStudioRoute) },
                            onOpenFirebaseGuide = { navController.navigate(FirebaseGuideRoute) }
                        )
                    }

                    composable<ChatDetailRoute> { entry ->
                        val args = entry.toRoute<ChatDetailRoute>()
                        val chat by viewModel.observeChat(args.chatId).collectAsStateWithLifecycle()
                        val messages by viewModel.observeMessages(args.chatId).collectAsStateWithLifecycle()

                        ChatDetailScreen(
                            currentUserId = viewModel.currentUserId,
                            chat = chat,
                            messages = messages,
                            onBack = { navController.popBackStack() },
                            onSendMessage = viewModel::sendMessage,
                            onDeleteMessage = { msgId -> viewModel.deleteMessage(args.chatId, msgId) },
                            onBlockUser = { targetUid -> viewModel.setUserBlocked(targetUid, true) },
                            onReportUser = viewModel::submitReport,
                            onDeleteChat = {
                                viewModel.deleteChat(args.chatId) {
                                    navController.popBackStack()
                                }
                            },
                            onStartAudioCall = { peerUid, peerName ->
                                viewModel.startCall(peerUid, peerName, args.chatId, "AUDIO") { callId ->
                                    navController.navigate(ActiveCallRoute(callId))
                                }
                            },
                            onStartVideoCall = { peerUid, peerName ->
                                viewModel.startCall(peerUid, peerName, args.chatId, "VIDEO") { callId ->
                                    navController.navigate(ActiveCallRoute(callId))
                                }
                            }
                        )
                    }

                    composable<ActiveCallRoute> { entry ->
                        val args = entry.toRoute<ActiveCallRoute>()
                        val callSession by viewModel.activeCallSession.collectAsStateWithLifecycle()
                        val candidates by viewModel.observeIceCandidates(args.callId).collectAsStateWithLifecycle()
                        val session = callSession

                        if (session == null) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            ActiveCallScreen(
                                callSession = session,
                                currentUserId = viewModel.currentUserId,
                                turnConfig = turnConfig,
                                remoteCandidates = candidates,
                                onPublishOffer = { sdp -> viewModel.updateCallOffer(session.id, sdp) },
                                onPublishAnswer = { sdp -> viewModel.answerCall(session.id, sdp) },
                                onPublishIceCandidate = { mid, idx, cand ->
                                    viewModel.sendIceCandidate(
                                        callId = session.id,
                                        participantUids = session.participantUids,
                                        sdpMid = mid,
                                        sdpMLineIndex = idx,
                                        candidate = cand
                                    )
                                },
                                onHangUp = {
                                    viewModel.endActiveCall(session.id)
                                    navController.popBackStack()
                                }
                            )
                        }
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
                            isAdmin = viewModel.isAdmin,
                            blockedUsers = blockedUsers,
                            safetyReports = safetyReports,
                            onUnblockUser = { targetUid -> viewModel.setUserBlocked(targetUid, false) },
                            onSubmitReport = viewModel::submitReport,
                            onUpdateReportStatus = viewModel::updateReportStatus,
                            onOpenPrivacyPolicy = { navController.navigate(PolicyRoute(isTerms = false)) },
                            onOpenTerms = { navController.navigate(PolicyRoute(isTerms = true)) }
                        )
                    }

                    composable<ProfileRoute> {
                        ProfileScreen(
                            profile = myProfile,
                            privateInfo = myPrivateInfo,
                            isDarkTheme = isDarkTheme,
                            onToggleDarkTheme = viewModel::toggleDarkTheme,
                            onSaveProfile = viewModel::saveProfile,
                            onOpenFirebaseGuide = { navController.navigate(FirebaseGuideRoute) },
                            onSignOut = onSignOutClick,
                            onDeleteAccount = {
                                viewModel.deleteAccount {
                                    onSignOutClick()
                                }
                            }
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

    val ringing = incomingCall
    if (ringing != null && currentDestination?.hasRoute<ActiveCallRoute>() != true) {
        AlertDialog(
            onDismissRequest = {},
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AvatarCircle(name = ringing.callerName, size = 44.dp, isOnline = true)
                    Column {
                        Text(
                            text = ringing.callerName,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (ringing.callType == "VIDEO") "Incoming Video Call" else "Incoming Audio Call",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            },
            text = {
                Text("Answer live WebRTC ${ringing.callType.lowercase()} call via TIME Cloud?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.acceptIncomingCall(ringing.id) {
                            navController.navigate(ActiveCallRoute(ringing.id))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OnlineGreen),
                    modifier = Modifier.testTag("accept_incoming_call_button")
                ) {
                    Icon(Icons.Filled.Call, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Accept", color = Color.Black)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { viewModel.declineIncomingCall(ringing.id) },
                    modifier = Modifier.testTag("decline_incoming_call_button")
                ) {
                    Icon(Icons.Filled.CallEnd, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Decline", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }
}
