package com.example.lisa

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

import android.os.Build

class Notification_Channel: Application() {
    override fun onCreate() {
        super.onCreate()
        if(Build.VERSION.SDK_INT>= Build.VERSION_CODES.O){
            val channel= NotificationChannel(
                "running_channel",
                "SOS Notification",
                NotificationManager.IMPORTANCE_HIGH
            )
            val notificationManager= getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}