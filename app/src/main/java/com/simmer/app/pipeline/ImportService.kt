package com.simmer.app.pipeline

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.simmer.app.data.ImportHost
import com.simmer.app.data.ImportJob
import com.simmer.app.domain.ImportState
import com.simmer.app.domain.PipelineStage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Starts [ImportService] as a foreground service. */
class AndroidImportHost(private val context: Context) : ImportHost {
    override fun startService() {
        ContextCompat.startForegroundService(context, Intent(context, ImportService::class.java))
    }
}

/**
 * Keeps the process alive while the [ImportJob] runs, shows the current stage in an ongoing
 * notification, then "Recipe ready to review" (or the error) and stops. START_STICKY: after process
 * death the system restarts it and the job resumes from its saved record.
 */
@AndroidEntryPoint
class ImportService : Service() {

    @Inject lateinit var job: ImportJob

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ImportNotifications.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!started) {
            started = true
            // startForeground must follow startForegroundService at once, whatever the job state.
            val stage = (job.state.value as? ImportState.Working)?.stage ?: PipelineStage.Ingredients
            ServiceCompat.startForeground(
                this,
                ImportNotifications.PROGRESS_ID,
                ImportNotifications.progress(this, stage),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
            // A restart after process death: pick up the saved job.
            job.restore()
            scope.launch { job.state.collect(::onState) }
        }
        return START_STICKY
    }

    private fun onState(state: ImportState) {
        val notifications = NotificationManagerCompat.from(this)
        when (state) {
            is ImportState.Working -> notifyIfAllowed(ImportNotifications.PROGRESS_ID) { ImportNotifications.progress(this, state.stage) }
            ImportState.Ready -> finish(ImportNotifications.DONE_ID) { ImportNotifications.ready(this) }
            ImportState.Failed -> finish(ImportNotifications.DONE_ID) { ImportNotifications.failed(this) }
            ImportState.Idle -> {
                notifications.cancel(ImportNotifications.DONE_ID)
                finish(null, null)
            }
        }
    }

    private fun notifyIfAllowed(id: Int, build: () -> android.app.Notification) {
        try {
            NotificationManagerCompat.from(this).notify(id, build())
        } catch (e: SecurityException) {
            // Notification permission denied: the service still keeps the job alive.
        }
    }

    private fun finish(id: Int?, build: (() -> android.app.Notification)?) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (id != null && build != null) notifyIfAllowed(id, build)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
