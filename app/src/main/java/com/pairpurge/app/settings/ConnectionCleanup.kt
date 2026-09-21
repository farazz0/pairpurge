package com.pairpurge.app.settings

import android.Manifest
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.pairpurge.app.bluetooth.BluetoothStatus
import com.pairpurge.app.bluetooth.SystemBluetoothDeviceSource
import com.pairpurge.app.whitelist.SharedPreferencesWhitelistStore

object ConnectionCleanup {
    private const val JOB_ID = 1001

    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (DeletionSettingsStore(context).policy().days == null) {
            scheduler.cancel(JOB_ID)
        } else {
            if (scheduler.getPendingJob(JOB_ID) != null) return
            scheduler.schedule(
                JobInfo.Builder(JOB_ID, ComponentName(context, ConnectionCleanupService::class.java))
                    .setPeriodic(6 * 60 * 60 * 1000L)
                    .setPersisted(true)
                    .build(),
            )
        }
    }

    fun run(context: Context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED) return
        val source = SystemBluetoothDeviceSource(context)
        try {
            if (source.status() != BluetoothStatus.READY) return
            val store = DeletionSettingsStore(context)
            val now = System.currentTimeMillis()
            val observed = store.observe(source.bondedDevices().mapTo(mutableSetOf()) { it.address }, now)
            val protected = SharedPreferencesWhitelistStore(context).addresses()
            val policy = store.policy()
            observed.forEach { (address, lastConnection) ->
                val connected = source.isConnected(address)
                if (connected == true) store.recordConnection(address, now)
                if (policy.shouldDelete(lastConnection, now, address in protected, connected)) {
                    source.unpair(address)
                }
            }
        } catch (_: SecurityException) {
            // Permission may be revoked between the check and the Bluetooth call.
        }
    }
}

class ConnectionCleanupService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        ConnectionCleanup.run(this)
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = true
}
