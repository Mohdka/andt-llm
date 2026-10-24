package com.andet.llm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class LlmService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val notification = NotificationCompat.Builder(this, "llm")
            .setContentTitle("AndtLLM")
            .setContentText("Running local inference")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setOngoing(true)
            .build()
        startForeground(1, notification)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("llm", "LLM Inference", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
