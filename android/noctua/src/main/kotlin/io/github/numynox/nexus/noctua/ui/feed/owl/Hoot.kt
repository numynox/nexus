package io.github.numynox.nexus.noctua.ui.feed.owl

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val Rate = 22_050

/**
 * Two soft hoots, made up on the spot rather than shipped as a sound file:
 * a low sine sliding down, the second a little lower and longer. Only with
 * the ringer on — a phone set to silent or vibrate stays quiet.
 */
suspend fun hoot(context: Context) = withContext(Dispatchers.Default) {
    val audio = context.getSystemService(AudioManager::class.java) ?: return@withContext
    if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return@withContext
    val samples = ShortArray((Rate * 1.1).toInt())
    note(samples, start = 0.0, base = 360.0, length = 0.30)
    note(samples, start = 0.42, base = 330.0, length = 0.60)
    val track = runCatching {
        AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(Rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 2)
            .build()
    }.getOrNull() ?: return@withContext
    try {
        track.write(samples, 0, samples.size)
        track.play()
        delay(1_200)
    } finally {
        track.release()
    }
}

/** One hoot into [out]: a quick swell, a slide from a little above [base] to a little below, a long fade. */
private fun note(out: ShortArray, start: Double, base: Double, length: Double) {
    val from = (start * Rate).toInt()
    val count = (length * Rate).toInt()
    var phase = 0.0
    for (i in 0 until count) {
        val t = i.toDouble() / Rate
        val frequency = when {
            t < 0.12 -> base * (1.08 - 0.08 * t / 0.12)
            else -> base * (1.0 - 0.1 * (t - 0.12) / (length - 0.12))
        }
        phase += 2 * PI * frequency / Rate
        val envelope = (1 - exp(-t / 0.02)) * exp(-3.0 * t / length)
        val index = from + i
        if (index < out.size) out[index] = (sin(phase) * envelope * 0.32 * Short.MAX_VALUE).toInt().toShort()
    }
}
