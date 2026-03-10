package dev.re7gog.b_sideloader.data.logic

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "ACTION_START_UPDATE") {
            val url = intent.getStringExtra("APK_URL") ?: return

            val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(workDataOf("URL" to url))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "download_${url.hashCode()}",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }
}