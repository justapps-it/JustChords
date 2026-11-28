package it.justapps.justchords.audio

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.Process
import androidx.core.app.NotificationCompat
import it.justapps.justchords.MainActivity
import it.justapps.justchords.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

/**
 * A recording kept in memory at 22 050 Hz (enough for chords, tempo and
 * Whisper), up to [maxSeconds]. Fed by the microphone or by the app's own
 * audio playback (the video playing in Just Chords). Never written to disk.
 */
class Recording(private val rec: AudioRecord, private val inRate: Int, val maxSeconds: Int = 360) {
    val sampleRate = RATE
    private val blocks = ArrayList<ShortArray>()
    private var fill = BLOCK
    @Volatile var length = 0
        private set
    @Volatile var level = 0f
        private set
    @Volatile private var running = true
    val full get() = length >= maxSeconds * RATE

    private val thread = Thread {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val buf = ShortArray(2048)
        val step = inRate.toDouble() / RATE
        var acc = 0.0
        var cnt = 0
        var pos = 0.0
        rec.startRecording()
        while (running) {
            val n = rec.read(buf, 0, buf.size)
            if (n <= 0) continue
            var peak = 0f
            for (i in 0 until n) {
                val x = buf[i].toDouble()
                if (kotlin.math.abs(x) > peak) peak = kotlin.math.abs(x).toFloat()
                // Box filter + decimation to 22 050 Hz.
                acc += x; cnt++
                pos += 1.0
                if (pos >= step) {
                    pos -= step
                    push((acc / cnt).toInt().toShort())
                    acc = 0.0; cnt = 0
                }
            }
            level = peak / 32768f
            if (full) break
        }
    }.apply { start() }

    @Synchronized
    private fun push(s: Short) {
        if (full) return
        if (fill == BLOCK) {
            blocks += ShortArray(BLOCK)
            fill = 0
        }
        blocks.last()[fill++] = s
        length++
    }

    /** The last n samples, for the live chord. */
    @Synchronized
    fun tail(n: Int): FloatArray {
        val out = FloatArray(n)
        val start = max(0, length - n)
        for (i in start until length) out[i - start + (n - (length - start))] = blocks[i / BLOCK][i % BLOCK] / 32768f
        return out
    }

    @Synchronized
    fun samples(): FloatArray = FloatArray(length) { i -> blocks[i / BLOCK][i % BLOCK] / 32768f }

    val seconds get() = length.toDouble() / RATE

    fun stop() {
        running = false
        runCatching { thread.join(500) }
        runCatching { rec.stop() }
        rec.release()
    }

    companion object {
        const val RATE = 22050
        private const val BLOCK = 1 shl 16

        @SuppressLint("MissingPermission")
        fun microphone(): Recording {
            for (sr in intArrayOf(44100, 48000)) {
                val min = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                if (min <= 0) continue
                val rec = AudioRecord(MediaRecorder.AudioSource.MIC, sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(min, 16384))
                if (rec.state == AudioRecord.STATE_INITIALIZED) return Recording(rec, sr)
                rec.release()
            }
            error("microphone not available")
        }

        /** What this app is playing (the embedded video), through Android's playback capture. */
        @SuppressLint("MissingPermission")
        fun playback(projection: MediaProjection): Recording {
            val config = AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUid(Process.myUid())
                .build()
            val sr = 44100
            val min = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val rec = AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(config)
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                .setBufferSizeInBytes(max(min, 32768))
                .build()
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                rec.release()
                error("playback capture not available")
            }
            return Recording(rec, sr)
        }
    }
}

/**
 * Foreground service that holds the media projection while a video playing in
 * the app is analysed (Android requires one for playback capture).
 */
class CaptureService : Service() {
    private var projection: MediaProjection? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopCapture()
            return START_NOT_STICKY
        }
        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        @Suppress("DEPRECATION")
        val data: Intent? = intent?.getParcelableExtra(EXTRA_DATA)
        startInForeground()
        try {
            val mpm = getSystemService(MediaProjectionManager::class.java)
            val p = mpm.getMediaProjection(code, data ?: error("no consent")) ?: error("no projection")
            p.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() = stopCapture()
            }, null)
            projection = p
            _recording.value = Recording.playback(p)
            _error.value = null
        } catch (e: Exception) {
            _error.value = e.message ?: e.javaClass.simpleName
            stopCapture()
        }
        return START_NOT_STICKY
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.capture_channel), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, CaptureService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.capture_title))
            .setContentText(getString(R.string.capture_text))
            .setContentIntent(open)
            .addAction(0, getString(R.string.capture_stop), stop)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(1, n)
    }

    private fun stopCapture() {
        _recording.value?.let { if (_stopped.value == null) _stopped.value = it }
        _recording.value?.stop()
        _recording.value = null
        projection?.stop()
        projection = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (_recording.value != null) stopCapture()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "capture"
        private const val ACTION_STOP = "stop"
        private const val EXTRA_CODE = "code"
        private const val EXTRA_DATA = "data"
        private val _recording = MutableStateFlow<Recording?>(null)
        private val _error = MutableStateFlow<String?>(null)
        private val _stopped = MutableStateFlow<Recording?>(null)
        val recording: StateFlow<Recording?> = _recording.asStateFlow()
        val error: StateFlow<String?> = _error.asStateFlow()
        /** A recording ended from the notification, for the screen to analyse. */
        val stopped: StateFlow<Recording?> = _stopped.asStateFlow()

        fun consumeStopped(): Recording? = _stopped.value.also { _stopped.value = null }

        fun start(ctx: Context, code: Int, data: Intent) {
            _stopped.value = null
            ctx.startForegroundService(Intent(ctx, CaptureService::class.java).putExtra(EXTRA_CODE, code).putExtra(EXTRA_DATA, data))
        }

        /** Stops capturing; the recording stays readable. */
        fun stop(ctx: Context): Recording? {
            val r = _recording.value
            ctx.startService(Intent(ctx, CaptureService::class.java).setAction(ACTION_STOP))
            return r
        }
    }
}
