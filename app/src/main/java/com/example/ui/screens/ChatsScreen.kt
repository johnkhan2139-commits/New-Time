package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.remote.OnlineChat
import com.example.data.remote.UserDirectoryProfile
import com.example.ui.components.AvatarCircle
import com.example.ui.components.CloudStatusBanner
import com.example.ui.components.PlatformBadge
import com.example.ui.components.TimeBrandHeader
import com.example.ui.components.formatClockTime

@Composable
fun ChatsScreen(
    currentUserId: String,
    onlineFriends: List<UserDirectoryProfile>,
    chats: List<OnlineChat>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onOpenDirectChat: (UserDirectoryProfile) -> Unit,
    onCreateGroupChat: (groupName: String, members: List<UserDirectoryProfile>) -> Unit,
    onChatClick: (String) -> Unit,
    onStartDirectCall: (friend: UserDirectoryProfile, callType: String) -> Unit,
    onOpenVideoStudio: () -> Unit,
    onOpenFirebaseGuide: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showGroupDialog by remember { mutableStateOf(false) }

    val filteredFriends = remember(onlineFriends, searchQuery) {
        if (searchQuery.isBlank()) onlineFriends
        else {
            val q = searchQuery.trim().lowercase()
            onlineFriends.filter {
                it.displayName.lowercase().contains(q) || it.handle.lowercase().contains(q)
            }
        }
    }

    val filteredChats = remember(chats, searchQuery) {
        if (searchQuery.isBlank()) chats
        else {
            val q = searchQuery.trim().lowercase()
            chats.filter {
                it.name.lowercase().contains(q) ||
                    it.memberNames.any { m -> m.lowercase().contains(q) } ||
                    it.lastMessagePreview.lowercase().contains(q)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TimeBrandHeader()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showGroupDialog = true },
                        modifier = Modifier.testTag("header_new_group_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.GroupAdd,
                            contentDescription = "New Group Chat",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                    IconButton(
                        onClick = onOpenVideoStudio,
                        modifier = Modifier.testTag("header_video_studio_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VideoCall,
                            contentDescription = "Open Audio & Video Studio",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            CloudStatusBanner(
                onOpenGuide = onOpenFirebaseGuide,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Search online friends, chats or groups") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("chat_search_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Online Friends Directory Strip
            Text(
                text = "Online Directory (${filteredFriends.size})",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (filteredFriends.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No other signed-in users visible yet. Sign in on a second device with TIME to chat, share photos/videos, and place WebRTC calls live.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(filteredFriends, key = { it.userId }) { friend ->
                        DirectoryFriendCard(
                            friend = friend,
                            onChatClick = { onOpenDirectChat(friend) },
                            onAudioCall = { onStartDirectCall(friend, "AUDIO") },
                            onVideoCall = { onStartDirectCall(friend, "VIDEO") }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Messages & Groups (${filteredChats.size})",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (filteredChats.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_empty_chats),
                            contentDescription = "No conversations",
                            modifier = Modifier
                                .size(width = 200.dp, height = 148.dp)
                                .clip(RoundedCornerShape(18.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Text(
                            text = if (searchQuery.isBlank()) "No online chats yet" else "No matching chats",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Tap an online friend above to message or tap + to start a Group Chat.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredChats, key = { it.id }) { chat ->
                        OnlineChatCard(
                            chat = chat,
                            currentUserId = currentUserId,
                            onClick = { onChatClick(chat.id) }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showGroupDialog = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("new_chat_fab")
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Create Group Chat")
        }
    }

    if (showGroupDialog) {
        CreateOnlineGroupDialog(
            friends = onlineFriends,
            onDismiss = { showGroupDialog = false },
            onCreateGroup = { groupName, selected ->
                showGroupDialog = false
                onCreateGroupChat(groupName, selected)
            }
        )
    }
}

@Composable
private fun DirectoryFriendCard(
    friend: UserDirectoryProfile,
    onChatClick: () -> Unit,
    onAudioCall: () -> Unit,
    onVideoCall: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .width(156.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onChatClick)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
            .testTag("directory_friend_${friend.userId}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AvatarCircle(
                name = friend.displayName,
                avatarBase64 = friend.avatarBase64,
                isOnline = friend.online,
                size = 48.dp
            )
            Text(
                text = friend.displayName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            PlatformBadge(devicePlatform = friend.devicePlatform)
            Text(
                text = if (friend.online) "Online • Chat" else "@${friend.handle}",
                style = MaterialTheme.typography.labelSmall,
                color = if (friend.online) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onAudioCall,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Call,
                        contentDescription = "Audio Call ${friend.displayName}",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onVideoCall,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.VideoCall,
                        contentDescription = "Video Call ${friend.displayName}",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun OnlineChatCard(
    chat: OnlineChat,
    currentUserId: String,
    onClick: () -> Unit
) {
    val displayTitle = if (chat.groupChat) {
        chat.name
    } else {
        chat.memberNames.firstOrNull { it.isNotBlank() } ?: chat.name
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("chat_card_${chat.id}"),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AvatarCircle(name = displayTitle, size = 48.dp)
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (chat.groupChat) {
                            Icon(
                                imageVector = Icons.Filled.Group,
                                contentDescription = "Group chat",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = if (chat.groupChat) chat.name else chat.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = formatClockTime(chat.lastMessageAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = chat.lastMessagePreview.ifBlank {
                        if (chat.groupChat) "${chat.memberUids.size} members" else "Direct message"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CreateOnlineGroupDialog(
    friends: List<UserDirectoryProfile>,
    onDismiss: () -> Unit,
    onCreateGroup: (groupName: String, selectedFriends: List<UserDirectoryProfile>) -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    val selectedIds = remember { mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Online Group") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Group Name *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_group_name_input")
                )

                Text(
                    text = "Select Friends from Directory (${selectedIds.size} selected)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )

                if (friends.isEmpty()) {
                    Text(
                        text = "No other users in the online directory yet. You can still create the group room now.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    friends.forEach { friend ->
                        val checked = friend.userId in selectedIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (checked) selectedIds.remove(friend.userId)
                                    else selectedIds.add(friend.userId)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    if (isChecked) selectedIds.add(friend.userId)
                                    else selectedIds.remove(friend.userId)
                                }
                            )
                            AvatarCircle(
                                name = friend.displayName,
                                avatarBase64 = friend.avatarBase64,
                                isOnline = friend.online,
                                size = 34.dp
                            )
                            Column {
                                Text(friend.displayName, style = MaterialTheme.typography.bodyMedium)
                                Text("@${friend.handle}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val chosen = friends.filter { it.userId in selectedIds }
                    onCreateGroup(groupName, chosen)
                },
                enabled = groupName.isNotBlank(),
                modifier = Modifier.testTag("confirm_create_group_button")
            ) {
                Text("Create Group")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
