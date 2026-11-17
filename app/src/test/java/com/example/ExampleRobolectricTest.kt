package com.example

import android.content.Context
import android.util.Base64
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.TimeDatabase
import com.example.data.media.MediaStorageHelper
import com.example.data.preferences.TimePreferences
import com.example.data.preferences.TurnServerConfig
import com.example.data.remote.CallSession
import com.example.data.remote.OnlineChat
import com.example.data.remote.OnlineMediaHelper
import com.example.data.remote.OnlineMessage
import com.example.data.remote.OnlineSafetyReport
import com.example.data.remote.UserDirectoryProfile
import com.example.data.repository.TimeRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: TimeDatabase
    private lateinit var repository: TimeRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, TimeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TimeRepository(
            dao = db.timeDao(),
            preferences = TimePreferences(context),
            mediaHelper = MediaStorageHelper(context)
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `app name and firestore database id match TIME configuration`() {
        val appName = context.getString(R.string.app_name)
        val dbId = context.getString(R.string.firestore_database_id)
        assertEquals("TIME", appName)
        assertEquals("ai-studio-android-time-96f54aac-a0c4-4798-a38b-c20dc1a3ccc5", dbId)
    }

    @Test
    fun `online models provide valid defaults matching firestore schema`() {
        val profile = UserDirectoryProfile()
        assertEquals("", profile.userId)
        assertEquals("ANDROID", profile.devicePlatform)
        assertTrue(profile.online)

        val chat = OnlineChat()
        assertFalse(chat.groupChat)
        assertTrue(chat.memberUids.isEmpty())

        val message = OnlineMessage()
        assertEquals("TEXT", message.mediaType)

        val call = CallSession()
        assertEquals("VIDEO", call.callType)
        assertEquals("RINGING", call.status)

        val report = OnlineSafetyReport()
        assertEquals("OPEN", report.status)
    }

    @Test
    fun `online media helper decodes valid base64 and rejects blank strings`() {
        val raw = byteArrayOf(1, 2, 3, 4, 5)
        val encoded = Base64.encodeToString(raw, Base64.NO_WRAP)
        val decoded = OnlineMediaHelper.decodeBase64Bytes(encoded)
        assertNotNull(decoded)
        assertArrayEquals(raw, decoded)
        assertNull(OnlineMediaHelper.decodeBase64Bytes(""))
        assertNull(OnlineMediaHelper.decodeBase64Bytes(null))
    }

    @Test
    fun `create profile seeds saved notes thread and supports messaging`() = runTest {
        val createResult = repository.signInWithSavedNotesSeed(
            displayName = "Aarav",
            phoneNumber = "+919876543210",
            bio = "Testing TIME",
            avatarUri = null
        )
        assertTrue(createResult.isSuccess)

        val profile = repository.profileFlow.first()
        assertNotNull(profile)
        assertEquals("Aarav", profile?.displayName)
        assertTrue(profile?.isSignedIn == true)

        val threads = repository.activeThreadsFlow.first()
        assertEquals(1, threads.size)
        val threadId = threads.first().id

        val sendResult = repository.sendMessage(threadId, "Aarav", "Hello TIME!", null)
        assertTrue(sendResult.isSuccess)

        val messages = repository.observeMessages(threadId).first()
        assertEquals(2, messages.size)
        assertEquals("Hello TIME!", messages.last().body)
    }

    @Test
    fun `blocking user moves thread to blocked list and safety report logs cleanly`() = runTest {
        val threadId = repository.createThread("Spammer", "spam99").getOrThrow()
        assertEquals(1, repository.activeThreadsFlow.first().size)

        repository.setThreadBlocked(threadId, true)
        assertEquals(0, repository.activeThreadsFlow.first().size)
        assertEquals(1, repository.blockedThreadsFlow.first().size)

        val reportResult = repository.submitSafetyReport("Spammer", "spam99", "Spam", "Repeated links")
        assertTrue(reportResult.isSuccess)
        val reports = repository.safetyReportsFlow.first()
        assertEquals(1, reports.size)
        assertEquals("OPEN", reports.first().status)

        repository.updateReportStatus(reports.first().id, "RESOLVED")
        assertEquals("RESOLVED", repository.safetyReportsFlow.first().first().status)
    }

    @Test
    fun `turn config validation rejects invalid scheme and delete account wipes data`() = runTest {
        val invalid = repository.saveTurnConfig(TurnServerConfig(turnUrl = "http://bad.url"))
        assertTrue(invalid.isFailure)

        val valid = repository.saveTurnConfig(
            TurnServerConfig(
                stunUrl = "stun:stun.l.google.com:19302",
                turnUrl = "turn:turn.example.com:3478",
                username = "timeuser",
                credential = "secret"
            )
        )
        assertTrue(valid.isSuccess)
        assertTrue(repository.turnConfigFlow.first().isTurnConfigured)

        repository.signInWithSavedNotesSeed("User", "", "", null)
        val erased = repository.deleteAccountAndAllData()
        assertTrue(erased.isSuccess)
        assertNull(repository.profileFlow.first())
        assertFalse(repository.turnConfigFlow.first().isTurnConfigured)
    }

    @Test
    fun `create group chat stores members and accepts messages`() = runTest {
        val groupId = repository.createGroupThread("College Friends", "Aman, Priya, Rahul").getOrThrow()
        val threads = repository.activeThreadsFlow.first()
        val group = threads.firstOrNull { it.id == groupId }
        assertNotNull(group)
        assertTrue(group?.isGroup == true)
        assertEquals("Aman, Priya, Rahul", group?.membersCsv)

        val msgResult = repository.sendMessage(groupId, "You", "Group call at 8 PM?", null)
        assertTrue(msgResult.isSuccess)
        val messages = repository.observeMessages(groupId).first()
        assertEquals(2, messages.size)
        assertEquals("Group call at 8 PM?", messages.last().body)
    }

    @Test
    fun `main activity cold starts cleanly without crashing`() {
        val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        assertNotNull(activity)
        controller.pause().stop().destroy()
    }
}
