package com.aiassistant

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.AtomicFile
import com.aiassistant.utils.AvatarManager
import com.aiassistant.utils.BackgroundImageManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, manifest = Config.NONE, shadows = [PosixAtomicFileShadow::class])
// Legacy Canvas records drawColor only as a description, producing identical transparent PNGs.
// Real rasterization is required to exercise pixel preservation and content-addressed deduplication.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MediaHistoryTest {
    private fun bitmap(color: Int) = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    @Test fun deletingAvatarHistoryPreservesActiveAvatarAndDoesNotReturnAfterMigrationOrReset() {
        val context = RuntimeEnvironment.getApplication()
        val image = bitmap(Color.rgb(30, 80, 150))
        assertTrue(AvatarManager.saveAvatarBitmap(context, image))
        val active = AvatarManager.getAvatar(context)
        val entry = AvatarManager.listAvatarHistory(context).single()
        assertTrue(AvatarManager.deleteAvatarHistory(context, entry))
        assertEquals(active, AvatarManager.getAvatar(context))
        repeat(2) { assertTrue(AvatarManager.listAvatarHistory(context).isEmpty()) }
        AvatarManager.deleteAvatar(context)
        assertTrue(AvatarManager.listAvatarHistory(context).isEmpty())
        assertTrue(AvatarManager.saveAvatarBitmap(context, image))
        assertEquals(entry.id, AvatarManager.listAvatarHistory(context).single().id)
    }

    @Test fun deletingBackgroundHistoryPreservesActiveImageAndDoesNotReturnAfterThemeChange() {
        val context = RuntimeEnvironment.getApplication()
        val color = Color.rgb(36, 55, 77)
        assertTrue(BackgroundImageManager.saveHomeBackgroundSolidColor(context, color))
        val entry = BackgroundImageManager.listBackgroundHistory(context).single()
        assertTrue(BackgroundImageManager.deleteBackgroundHistory(context, entry))
        assertTrue(BackgroundImageManager.hasHomeBackground(context))
        assertEquals(color, BackgroundImageManager.getHomeBackgroundBitmap(context)!!.getPixel(0, 0))
        repeat(2) { assertTrue(BackgroundImageManager.listBackgroundHistory(context).isEmpty()) }
        assertTrue(BackgroundImageManager.applyThemeBackground(context))
        assertTrue(BackgroundImageManager.listBackgroundHistory(context).isEmpty())
        assertTrue(BackgroundImageManager.saveHomeBackgroundSolidColor(context, color))
        assertEquals(entry.id, BackgroundImageManager.listBackgroundHistory(context).single().id)
    }

    @Test fun deletingOneHistoryEntryKeepsOtherImagesRestorable() {
        val context = RuntimeEnvironment.getApplication()
        assertTrue(BackgroundImageManager.saveHomeBackgroundSolidColor(context, Color.RED))
        val red = BackgroundImageManager.listBackgroundHistory(context).single()
        assertTrue(BackgroundImageManager.saveHomeBackgroundSolidColor(context, Color.BLUE))
        val blue = BackgroundImageManager.listBackgroundHistory(context).first { it.id != red.id }
        assertTrue(BackgroundImageManager.deleteBackgroundHistory(context, red))
        assertEquals(listOf(blue.id), BackgroundImageManager.listBackgroundHistory(context).map { it.id })
        assertTrue(BackgroundImageManager.restoreChatBackground(context, blue))
        assertEquals(Color.BLUE, BackgroundImageManager.getChatBackgroundBitmap(context)!!.getPixel(0, 0))
    }

    @Test fun backupRestoresDeletionTombstonesWithoutRevivingStaleHistoryFiles() {
        val context = RuntimeEnvironment.getApplication()
        assertTrue(BackgroundImageManager.saveHomeBackgroundSolidColor(context, Color.MAGENTA))
        val entry = BackgroundImageManager.listBackgroundHistory(context).single()
        val bytes = File(context.filesDir, entry.id).readBytes()
        assertTrue(BackgroundImageManager.deleteBackgroundHistory(context, entry))
        val metadata = com.aiassistant.utils.MediaHistoryBackup.exportDeleted(context)
        context.getSharedPreferences("background_history", Context.MODE_PRIVATE).edit().clear().commit()
        File(context.filesDir, entry.id).writeBytes(bytes)
        com.aiassistant.utils.MediaHistoryBackup.importDeleted(context, metadata)
        assertTrue(BackgroundImageManager.listBackgroundHistory(context).isEmpty())
        com.aiassistant.utils.MediaHistoryBackup.importDeleted(context, com.google.gson.JsonObject())
        assertTrue(BackgroundImageManager.listBackgroundHistory(context).isEmpty())
        assertTrue(com.aiassistant.utils.MediaHistoryBackup.isMediaFile(entry.id))
        assertFalse(com.aiassistant.utils.MediaHistoryBackup.isMediaFile("../user_avatar.dat"))
        assertFalse(com.aiassistant.utils.MediaHistoryBackup.isMediaFile("private-notes.txt"))
    }

    @Test fun deletionRejectsPathsOutsideHistoryAndDoesNotTouchActiveFiles() {
        val context = RuntimeEnvironment.getApplication()
        val unrelated = File(context.filesDir, "unrelated.txt").apply { writeText("synthetic fixture") }
        assertFalse(AvatarManager.deleteAvatarHistory(context, AvatarManager.AvatarHistoryEntry("../unrelated.txt", 0)))
        assertFalse(AvatarManager.deleteAvatarHistory(context, AvatarManager.AvatarHistoryEntry("user_avatar.dat", 0)))
        assertFalse(BackgroundImageManager.deleteBackgroundHistory(context, BackgroundImageManager.BackgroundHistoryEntry("../unrelated.txt", 0)))
        assertFalse(BackgroundImageManager.deleteBackgroundHistory(context, BackgroundImageManager.BackgroundHistoryEntry("home_background.jpg", 0)))
        assertEquals("synthetic fixture", unrelated.readText())
        assertFalse(context.getSharedPreferences("avatar_history", Context.MODE_PRIVATE).contains("../unrelated.txt"))
    }
}

// Android's AtomicFile relies on POSIX rename replacing the existing destination atomically.
// Java File.renameTo does not provide that behavior on the Windows Robolectric host. Keep the
// real write/read paths and PNG contents; adapt only this platform-dependent filesystem step.
@Implements(AtomicFile::class)
class PosixAtomicFileShadow {
    companion object {
        @JvmStatic
        @Implementation
        fun rename(source: File, target: File) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }
    }
}
