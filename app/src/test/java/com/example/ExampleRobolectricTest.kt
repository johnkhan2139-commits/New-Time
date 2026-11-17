package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.TimeDatabase
import com.example.data.media.MediaStorageHelper
import com.example.data.preferences.TimePreferences
import com.example.data.preferences.TurnServerConfig
import com.example.data.repository.TimeRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
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
    fun `app name matches TIME branding`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("TIME", appName)
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
}
