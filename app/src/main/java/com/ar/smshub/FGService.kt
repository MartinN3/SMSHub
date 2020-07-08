package com.ar.smshub

import android.app.Service
import android.app.PendingIntent
import android.app.NotificationManager
import android.app.NotificationChannel
import android.content.Intent
import android.content.Context
import android.os.IBinder
import android.os.Build
import android.support.v4.content.ContextCompat
import android.support.v4.app.NotificationCompat



class FGService : Service() {
    private val CHANNEL_ID = "SMSHub_FGService"

    companion object {
        fun startService(context: Context, message: String) {
            val startIntent = Intent(context, FGService::class.java)
            startIntent.putExtra("inputExtra", message)
            ContextCompat.startForegroundService(context, startIntent)
        }
        fun stopService(context: Context) {
            val stopIntent = Intent(context, FGService::class.java)
            context.stopService(stopIntent)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        //do heavy work on a background thread
        val input = intent?.getStringExtra("inputExtra")
        createNotificationChannel()
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0, notificationIntent, 0
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SMSHub")
            .setContentText(input)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()
        startForeground(1, notification)
        //stopSelf();
        return START_NOT_STICKY
    }


    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(CHANNEL_ID, "Foreground Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT)
            val manager = getSystemService(NotificationManager::class.java)
            manager!!.createNotificationChannel(serviceChannel)
        }
    }
}
