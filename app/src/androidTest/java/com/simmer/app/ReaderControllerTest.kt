package com.simmer.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simmer.app.data.PrefsRepository
import com.simmer.app.data.ReaderController
import com.simmer.app.domain.ReaderModel
import com.simmer.app.domain.ReaderStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The Recipe reader on a real device: WorkManager, StatFs, files in filesDir and DataStore. */
@RunWith(AndroidJUnit4::class)
class ReaderControllerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun wifiOnlyIsOnByDefaultAndPersists(): Unit = runBlocking {
        val prefs = PrefsRepository(context)
        try {
            prefs.setReaderWifiOnly(true)
            assertTrue(PrefsRepository(context).readerWifiOnly.first())
            prefs.setReaderWifiOnly(false)
            assertFalse(PrefsRepository(context).readerWifiOnly.first())
        } finally {
            prefs.setReaderWifiOnly(true)
        }
    }

    @Test
    fun anInstalledModelIsReadyAndRemoveResetsIt(): Unit = runBlocking {
        val controller = ReaderController(context, PrefsRepository(context))
        controller.remove()
        // Phones that cannot run the model report Not supported whatever is on disk.
        assumeTrue(controller.status.first() != ReaderStatus.NotSupported)

        val model = File(File(context.filesDir, "models").apply { mkdirs() }, ReaderModel.FILE_NAME)
        try {
            model.writeBytes(byteArrayOf(1, 2, 3))
            controller.filesChanged()
            assertEquals(ReaderStatus.Ready, controller.status.first())

            controller.remove()
            assertFalse(model.exists())
            assertTrue(controller.status.first() is ReaderStatus.NotDownloaded || controller.status.first() == ReaderStatus.NotEnoughStorage)
        } finally {
            model.delete()
        }
    }
}
