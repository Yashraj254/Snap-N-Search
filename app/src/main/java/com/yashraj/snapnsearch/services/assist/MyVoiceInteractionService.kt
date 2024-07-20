package com.yashraj.snapnsearch.services.assist

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.service.voice.VoiceInteractionService
import com.yashraj.snapnsearch.R
import com.yashraj.snapnsearch.ui.MainActivity
import com.yashraj.snapnsearch.utils.PrefManager
import com.yashraj.snapnsearch.utils.toastMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Service that is started when the assist app is selected
 */
@AndroidEntryPoint
class MyVoiceInteractionService : VoiceInteractionService() {

    @Inject
    lateinit var prefManager: PrefManager

    companion object {
        var instance: MyVoiceInteractionService? = null

        /**
         * Open assistant settings from activity
         */
        fun openVoiceInteractionSettings(context: Activity, returnTo: String? = null, prefManager: PrefManager) {
            if (instance == null) {
                context.toastMessage(context.getString(R.string.setting_enable_assist))
            } else
                context.toastMessage(context.getString(R.string.setting_disable_assist))
            Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
                if (resolveActivity(context.packageManager) != null) {

                    if (returnTo != null) {

                        prefManager.returnIfVoiceInteractionServiceEnabled =
                            returnTo
                    }
                    context.startActivity(this)
                }
            }
        }
    }

    override fun onReady() {
        super.onReady()
        instance = this
        try {
            if (prefManager.returnIfVoiceInteractionServiceEnabled == MainActivity.TAG) {
                prefManager.returnIfVoiceInteractionServiceEnabled = null
                MainActivity.startNewTask(this)
            }

        } catch (e: ActivityNotFoundException) {
            // This seems to happen after booting
        }

    }

}
