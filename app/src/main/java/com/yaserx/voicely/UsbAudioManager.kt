package com.yaserx.voicely

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/** Read-only USB audio capability layer plus automatic route refresh. */
object UsbAudioManager {
    data class Device(
        val id: Int,
        val name: String,
        val isInput: Boolean,
        val isOutput: Boolean,
        val channelCounts: List<Int>,
        val sampleRates: List<Int>
    )

    fun isUsbAudioDevice(info: AudioDeviceInfo): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            info.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                info.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                info.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
        } else false
    }

    fun connectedDevices(context: Context): List<Device> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return emptyList()
        val audioManager = context.getSystemService(AudioManager::class.java)
        return audioManager.getDevices(AudioManager.GET_DEVICES_ALL)
            .filter(::isUsbAudioDevice)
            .map { info ->
                Device(
                    id = info.id,
                    name = info.productName?.toString()?.takeIf { it.isNotBlank() } ?: "USB Audio Device",
                    isInput = info.isSource,
                    isOutput = info.isSink,
                    channelCounts = info.channelCounts.toList(),
                    sampleRates = info.sampleRates.toList()
                )
            }
            .distinctBy { it.id }
    }

    fun refresh(context: Context) {
        // Device attach/detach changes Android's available audio devices.
        // Reading the list again is enough to make the next playback/recording
        // operation use the current Android routing state.
        connectedDevices(context)
    }

    fun hasUsbInput(context: Context): Boolean = connectedDevices(context).any { it.isInput }
    fun hasUsbOutput(context: Context): Boolean = connectedDevices(context).any { it.isOutput }
}
