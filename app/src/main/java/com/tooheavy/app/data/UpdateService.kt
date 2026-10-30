package com.tooheavy.app.data

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Daily background update check, scheduled by Updater.scheduleDailyCheck. */
class UpdateJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Updater.checkInBackground(applicationContext) { jobFinished(params, false) }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        Updater.cancelBackgroundCheck()
        return true
    }
}

/** Results of silent install sessions, and the "app was updated" broadcast. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Updater.ACTION_INSTALL_STATUS -> Updater.onInstallStatus(context, intent)
            Intent.ACTION_MY_PACKAGE_REPLACED -> Updater.onPackageReplaced(context)
        }
    }
}
