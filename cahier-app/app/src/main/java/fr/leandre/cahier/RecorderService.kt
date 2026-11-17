package fr.leandre.cahier

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import java.io.File

/**
 * Enregistre le micro dans un service au premier plan : ça continue écran éteint
 * et quand l'appli est en arrière-plan.
 */
class RecorderService : Service() {

    enum class Etat { ARRET, CAPTE, PAUSE }

    companion object {
        const val START = "start"; const val PAUSE = "pause"; const val RESUME = "resume"; const val STOP = "stop"
        private const val CANAL = "capture"; private const val NOTIF = 1

        @Volatile var etat = Etat.ARRET; private set
        @Volatile var fichier: File? = null; private set
        private var cumulMs = 0L
        private var depuis = 0L
        var ecouteur: (() -> Unit)? = null

        fun dureeMs(): Long = cumulMs + if (etat == Etat.CAPTE) SystemClock.elapsedRealtime() - depuis else 0

        fun envoyer(c: Context, action: String) {
            val i = Intent(c, RecorderService::class.java).setAction(action)
            if (action == START) c.startForegroundService(i) else c.startService(i)
        }
    }

    private var recorder: MediaRecorder? = null
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            START -> demarrer()
            PAUSE -> if (etat == Etat.CAPTE) {
                recorder?.pause(); cumulMs += SystemClock.elapsedRealtime() - depuis; etat = Etat.PAUSE; maj()
            }
            RESUME -> if (etat == Etat.PAUSE) {
                recorder?.resume(); depuis = SystemClock.elapsedRealtime(); etat = Etat.CAPTE; maj()
            }
            STOP -> arreter()
        }
        return START_NOT_STICKY
    }

    private fun demarrer() {
        if (etat != Etat.ARRET) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANAL, "Capture du cours", NotificationManager.IMPORTANCE_LOW))
        startForeground(NOTIF, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)

        val f = File(getExternalFilesDir(null) ?: filesDir, "capture-${System.currentTimeMillis()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(16000)
            r.setAudioEncodingBitRate(32000) // ~14 Mo par heure
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
        } catch (e: Exception) {
            r.release(); f.delete()
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
            return
        }
        recorder = r; fichier = f; cumulMs = 0; depuis = SystemClock.elapsedRealtime(); etat = Etat.CAPTE
        wake = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "cahier:capture").apply { acquire(6 * 60 * 60 * 1000L) }
        maj()
    }

    private fun arreter() {
        if (etat == Etat.ARRET) { stopSelf(); return }
        if (etat == Etat.CAPTE) cumulMs += SystemClock.elapsedRealtime() - depuis
        try { recorder?.stop() } catch (_: Exception) {}
        recorder?.release(); recorder = null
        try { wake?.release() } catch (_: Exception) {}
        val f = fichier
        if (f != null && f.exists() && f.length() > 0) Store.setEnAttente(this, f.absolutePath, cumulMs) else f?.delete()
        etat = Etat.ARRET; fichier = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        ecouteur?.invoke()
        if (ecouteur == null) {
            // Appli fermée : on la rouvre sur le choix de la matière.
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    override fun onDestroy() {
        if (etat != Etat.ARRET) arreter()
        super.onDestroy()
    }

    private fun maj() {
        getSystemService(NotificationManager::class.java).notify(NOTIF, notification())
        ecouteur?.invoke()
    }

    private fun notification(): Notification {
        val ouvrir = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        fun action(a: String, req: Int) = PendingIntent.getService(this, req, Intent(this, RecorderService::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE)
        val b = Notification.Builder(this, CANAL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(if (etat == Etat.PAUSE) "Capture en pause" else "Je capte le cours")
            .setContentText("Tu peux éteindre l'écran.")
            .setContentIntent(ouvrir)
            .setOngoing(true)
            .setUsesChronometer(etat == Etat.CAPTE)
            .setWhen(System.currentTimeMillis() - dureeMs())
        if (etat == Etat.CAPTE) b.addAction(Notification.Action.Builder(null, "Pause", action(PAUSE, 1)).build())
        if (etat == Etat.PAUSE) b.addAction(Notification.Action.Builder(null, "Reprendre", action(RESUME, 2)).build())
        b.addAction(Notification.Action.Builder(null, "Terminer", action(STOP, 3)).build())
        return b.build()
    }
}
