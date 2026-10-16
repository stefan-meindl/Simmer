package com.simmer.app.listen

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.simmer.app.MainActivity
import com.simmer.app.data.ListenSession
import com.simmer.app.domain.ListenState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Holds the MediaProjection and keeps the process alive while Listen captures and transcribes, with an ongoing
 * notification (timer and Stop). It starts from the consent result ([start]); Stop on the notification reaches
 * the [ListenSession] with the app in the background. Not sticky: a killed process loses the audio, and the
 * session then reads as interrupted.
 */
@AndroidEntryPoint
class ListenService : Service() {

    @Inject lateinit var session: ListenSession
    @Inject lateinit var audio: PlaybackAudioSource

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var projection: MediaProjection? = null
    private var started = false
    private val floatingStop by lazy { FloatingStop(this) { openApp(); session.stop() } }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ListenNotifications.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                if (!started) stopSelf() else session.stop()
            }
            ACTION_START -> if (!started) begin(intent) else Unit
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    /** The overlay permission lets a service start an activity from the background. */
    private fun openApp() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_ADD_RECIPE, true),
        )
    }

    private fun begin(intent: Intent) {
        started = true
        // startForeground must follow startForegroundService at once, and precede getMediaProjection (Android 14+).
        ServiceCompat.startForeground(
            this,
            ListenNotifications.CAPTURE_ID,
            ListenNotifications.capturing(this, System.currentTimeMillis()),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
        )
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)
        val manager = getSystemService(MediaProjectionManager::class.java)
        val granted = try {
            data?.let { manager.getMediaProjection(resultCode, it) }
        } catch (e: SecurityException) {
            null
        }
        if (granted == null) {
            session.consentDenied()
            finish()
            return
        }
        projection = granted
        granted.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() = session.projectionEnded()
            },
            Handler(Looper.getMainLooper()),
        )
        audio.attach(granted)
        session.consentGranted()
        if (session.state.value !is ListenState.Capturing) {
            // The session was not waiting for this consent (e.g. the process was recreated while the dialog was up).
            finish()
            return
        }
        scope.launch { session.state.collect(::onState) }
    }

    private fun onState(state: ListenState) {
        when (state) {
            is ListenState.Capturing -> floatingStop.show()
            ListenState.Transcribing -> {
                releaseProjection()
                notifyIfAllowed(ListenNotifications.CAPTURE_ID) { ListenNotifications.transcribing(this) }
            }
            is ListenState.Done -> finish(ListenNotifications.DONE_ID) { ListenNotifications.ready(this) }
            ListenState.Silent -> finish(ListenNotifications.DONE_ID) { ListenNotifications.silent(this) }
            else -> finish()
        }
    }

    private fun releaseProjection() {
        floatingStop.hide()
        projection?.stop()
        projection = null
        audio.detach()
    }

    private fun finish(id: Int? = null, build: (() -> Notification)? = null) {
        releaseProjection()
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (id != null && build != null) notifyIfAllowed(id, build)
        stopSelf()
    }

    private fun notifyIfAllowed(id: Int, build: () -> Notification) {
        try {
            NotificationManagerCompat.from(this).notify(id, build())
        } catch (e: SecurityException) {
            // Notification permission denied: the service still keeps the process alive.
        }
    }

    override fun onDestroy() {
        scope.cancel()
        projection?.stop()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.simmer.app.listen.START"
        const val ACTION_STOP = "com.simmer.app.listen.STOP"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_DATA = "data"

        /** Stops the capture and moves on to transcribing. */
        fun stop(context: Context) {
            context.startService(Intent(context, ListenService::class.java).setAction(ACTION_STOP))
        }

        /** Starts the service with the screen-capture consent result. */
        fun start(context: Context, resultCode: Int, data: Intent) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ListenService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_RESULT_CODE, resultCode)
                    .putExtra(EXTRA_DATA, data),
            )
        }
    }
}
