package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object ChatsRoute

@Serializable
data class ChatDetailRoute(val threadId: Long)

@Serializable
data object VideoStudioRoute

@Serializable
data object SafetyRoute

@Serializable
data object ProfileRoute

@Serializable
data class PolicyRoute(val isTerms: Boolean)

@Serializable
data object FirebaseGuideRoute
