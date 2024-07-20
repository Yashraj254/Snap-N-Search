package com.yashraj.snapnsearch.services.assist

import android.content.Context
import android.graphics.Bitmap
import android.service.voice.VoiceInteractionSession
import com.yashraj.snapnsearch.R
import com.yashraj.snapnsearch.utils.isDeviceLocked
import com.yashraj.snapnsearch.utils.shareToGoogleLens
import com.yashraj.snapnsearch.utils.toastMessage


/**
 * A session is started when the home button is long pressed
 */
class MyVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    override fun onHandleScreenshot(bitmap: Bitmap?) {

        if (context?.let { isDeviceLocked(it) } == true) {
            context.toastMessage(context.getString(R.string.screenshot_prevented))
            return
        }

        if (bitmap == null) {
            hide()
        } else {
            shareToGoogleLens(bitmap, context)
            onHide()
            onBackPressed()
        }
    }

}
