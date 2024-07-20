package com.yashraj.snapnsearch.services.assist

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import dagger.hilt.android.AndroidEntryPoint

/**
 * Service that starts the sessions
 */
@AndroidEntryPoint
class MyVoiceInteractionSessionService : VoiceInteractionSessionService() {

    override fun onNewSession(bundle: Bundle?): VoiceInteractionSession {
        return MyVoiceInteractionSession(this)
    }
}
