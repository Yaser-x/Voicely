package com.yaserx.voicely

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat

/**
 * Watches USB attach/detach events and asks UsbAudioManager to refresh.
 * The app never assumes that every USB device is an audio device.
 */
class UsbAutoConnectReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED,
            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                UsbAudioManager.refresh(context.applicationContext)
            }
        }
    }
}
