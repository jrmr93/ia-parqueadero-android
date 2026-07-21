package com.example

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.os.SystemClock
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ParkingNotificationService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false

    companion object {
        const val CHANNEL_ID = "parking_saldo_channel"
        const val NOTIFICATION_ID = 1001
        
        const val ACTION_START = "com.example.ACTION_START"
        const val ACTION_STOP = "com.example.ACTION_STOP"
        const val ACTION_TEST = "com.example.ACTION_TEST"
        
        fun startService(context: Context) {
            val intent = Intent(context, ParkingNotificationService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, ParkingNotificationService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun testNotification(context: Context) {
            val intent = Intent(context, ParkingNotificationService::class.java).apply {
                action = ACTION_TEST
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (intent == null || action == null || action == ACTION_START) {
            if (!isRunning) {
                isRunning = true
                startForegroundNotification()
                startPolling()
            }
        } else if (action == ACTION_TEST) {
            if (!isRunning) {
                isRunning = true
                startForegroundNotification()
                startPolling()
            }
            serviceScope.launch {
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val loadingNotification = buildNotification(
                    "ia-parqueadero - Probando...",
                    "Obteniendo saldo actual de la API..."
                )
                notificationManager.notify(NOTIFICATION_ID, loadingNotification)
                
                val testSaldo = fetchSaldo()
                val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                
                val sharedPref = getSharedPreferences("ParkingPrefs", Context.MODE_PRIVATE)
                sharedPref.edit().apply {
                    putString("last_fetched_saldo", testSaldo)
                    putLong("last_fetched_time", System.currentTimeMillis())
                    apply()
                }
                
                val testNotification = buildNotification(
                    "ia-parqueadero - Prueba Exitosa ✅",
                    "Saldo: $testSaldo (Actualizado a las $currentTime)"
                )
                notificationManager.notify(NOTIFICATION_ID, testNotification)
                
                val updateIntent = Intent("com.example.UPDATE_SALDO").apply {
                    putExtra("saldo", testSaldo)
                    setPackage(packageName)
                }
                sendBroadcast(updateIntent)
            }
        } else if (action == ACTION_STOP) {
            stopForegroundService()
        }
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val notification = buildNotification("Iniciando servicio...", "Cargando saldo de Parking...")
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID, 
                    notification, 
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Saldo de Parking"
            val descriptionText = "Notificaciones de saldo del portal de Parking"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent to stop the service directly from the notification
        val stopIntent = Intent(this, ParkingNotificationService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_car)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setSound(null)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Detener",
                stopPendingIntent
            )
            .build()
    }

    private fun startPolling() {
        serviceScope.launch {
            while (isActive) {
                val saldoResult = fetchSaldo()
                val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                
                // Save fetched value to preferences so UI can also display it
                val sharedPref = getSharedPreferences("ParkingPrefs", Context.MODE_PRIVATE)
                sharedPref.edit().apply {
                    putString("last_fetched_saldo", saldoResult)
                    putLong("last_fetched_time", System.currentTimeMillis())
                    apply()
                }

                // Update notification
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val updatedNotification = buildNotification(
                    "ia-parqueadero - Saldo",
                    "Saldo: $saldoResult (Actualizado a las $currentTime)"
                )
                notificationManager.notify(NOTIFICATION_ID, updatedNotification)

                val updateIntent = Intent("com.example.UPDATE_SALDO").apply {
                    putExtra("saldo", saldoResult)
                    setPackage(packageName)
                }
                sendBroadcast(updateIntent)

                // Wait 1 minute
                delay(60_000)
            }
        }
    }

    private fun fetchSaldo(): String {
        var connection: HttpURLConnection? = null
        try {
            val url = URL("https://parking.maldo.uk/saldo")
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("Accept", "application/json, text/plain, */*")
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val text = connection.inputStream.bufferedReader().use { it.readText() }.trim()
                if (text.isEmpty()) {
                    return "Sin datos"
                }
                
                // Check if JSON format
                if (text.startsWith("{")) {
                    try {
                        val json = JSONObject(text)
                        val keys = listOf("saldo", "balance", "amount", "value", "credit")
                        for (key in keys) {
                            if (json.has(key)) {
                                val value = json.optString(key)
                                return formatSaldo(value)
                            }
                        }
                        return text
                    } catch (e: Exception) {
                        return text
                    }
                }
                return formatSaldo(text)
            } else {
                return "Error ($responseCode)"
            }
        } catch (e: Exception) {
            return "Sin conexión"
        } finally {
            connection?.disconnect()
        }
    }

    private fun formatSaldo(raw: String): String {
        // If it's a number, let's append currency sign if not present
        val clean = raw.trim()
        if (clean.matches(Regex("^[0-9]+([.,][0-9]+)?$"))) {
            return "\$$clean"
        }
        return clean
    }

    private fun stopForegroundService() {
        isRunning = false
        stopForeground(true)
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val restartServiceIntent = Intent(applicationContext, this.javaClass).apply {
            action = ACTION_START
        }
        val restartServicePendingIntent = PendingIntent.getService(
            applicationContext,
            2,
            restartServiceIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmService = applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            alarmService.set(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 1000,
                restartServicePendingIntent
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        isRunning = false
        super.onDestroy()
    }
}
