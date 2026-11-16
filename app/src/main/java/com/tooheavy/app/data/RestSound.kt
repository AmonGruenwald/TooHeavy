package com.tooheavy.app.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Three short beeps in your headphones when a rest is over, like a navigation app's voice over music: played as
 * navigation guidance (on the media volume), so the phone's silent mode doesn't mute it, with music ducked for a
 * moment. Only while headphones are connected, so the phone's speaker stays quiet in the gym.
 */
object RestSound {
    private const val RATE = 44_100

    /** Wired, USB or Bluetooth headphones or a headset are connected. */
    fun headphonesConnected(context: Context): Boolean {
        val am = context.getSystemService(AudioManager::class.java) ?: return false
        return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { d ->
            when (d.type) {
                AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_USB_HEADSET,
                -> true
                // BLE audio headsets (LE Audio), Android 12 and later.
                BLE_HEADSET -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                else -> false
            }
        }
    }

    /** AudioDeviceInfo.TYPE_BLE_HEADSET, which older SDKs don't know. */
    private const val BLE_HEADSET = 26

    /** Beeps if the setting is on and headphones are connected; returns whether it did. */
    fun playIfHeadphones(context: Context): Boolean {
        if (!Repo.settings.restSound || !headphonesConnected(context)) return false
        return runCatching { play(context.applicationContext) }.isSuccess
    }

    private fun play(context: Context) {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .build()
        am.requestAudioFocus(focus)
        val pcm = beeps()
        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        track.write(pcm, 0, pcm.size)
        track.play()
        // Done after the sound plus a little: release the track and give the music its volume back.
        val ms = pcm.size * 1000L / RATE + 300
        Handler(Looper.getMainLooper()).postDelayed({
            runCatching { track.stop() }
            track.release()
            am.abandonAudioFocusRequest(focus)
        }, ms)
    }

    /**
     * Silence first (Bluetooth headphones can swallow the start of a sound while their link wakes up), then three
     * 880 Hz beeps of 150 ms, 120 ms apart, each faded in and out so they don't click.
     */
    private fun beeps(): ShortArray {
        fun samples(ms: Int) = RATE * ms / 1000
        val lead = samples(250)
        val beep = samples(150)
        val gap = samples(120)
        val fade = samples(10)
        val out = ShortArray(lead + 3 * beep + 2 * gap)
        for (b in 0 until 3) {
            val start = lead + b * (beep + gap)
            for (i in 0 until beep) {
                val envelope = min(1.0, min(i, beep - 1 - i).toDouble() / fade)
                out[start + i] = (sin(2 * PI * 880 * i / RATE) * envelope * 0.6 * Short.MAX_VALUE).toInt().toShort()
            }
        }
        return out
    }
}
