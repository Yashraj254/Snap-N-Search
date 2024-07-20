package com.yashraj.snapnsearch.utils

import android.content.SharedPreferences
import android.graphics.Point
import javax.inject.Inject

class PrefManager @Inject constructor(private val pref: SharedPreferences) {

    var floatingButton: Boolean
        get() = pref.getBoolean("floating_button", false)
        set(value) = pref.edit().putBoolean(
            "floating_button",
            value
        ).apply()

    var returnIfAccessibilityServiceEnabled: String?
        get() = pref.getString(
            "return_if_accessibility",
            null
        )
        set(value) = pref.edit().putString(
            "return_if_accessibility",
            value
        ).apply()

    var returnIfVoiceInteractionServiceEnabled: String?
        get() = pref.getString(
            "return_if_voice_interaction",
            null
        )
        set(value) = pref.edit().putString(
            "return_if_voice_interaction",
            value
        ).apply()

    var floatingButtonShowClose: Boolean
        get() = pref.getBoolean(
            "floating_button_show_close",
            false
        )
        set(value) = pref.edit().putBoolean(
            "floating_button_show_close",
            value
        ).apply()

    var floatingButtonColor_R: Int
        get() = pref.getInt(
            "floating_button_color_r",
            0
        )
        set(value) = pref.edit().putInt(
            "floating_button_color_r",
            value
        ).apply()

    var floatingButtonColor_G: Int
        get() =
            pref.getInt(
                "floating_button_color_g",
                100
            )
        set(value) = pref.edit().putInt(
            "floating_button_color_g",
            value
        ).apply()

    var floatingButtonColor_B: Int
        get() = pref.getInt(
            "floating_button_color_b",
            147
        )
        set(value) = pref.edit().putInt(
            "floating_button_color_b",
            value
        ).apply()

    var floatingButtonSize: Int
        get() = pref.getInt(
            "floating_button_size",
            200
        )
        set(value) = pref.edit().putInt(
            "floating_button_size",
            value
        ).apply()

    var floatingButtonAlpha: Int
        get() = pref.getInt(
            "floating_button_alpha",
            100
        )
        set(value) = pref.edit().putInt(
            "floating_button_alpha",
            value
        ).apply()


    /**
     * Default to (50, 150) to avoid having the floating button
     * stuck on the camera notch when it is added the first time
     */
    var floatingButtonPosition: Point
        get() {
            val s = pref.getString(
                "floating_button_position",
                "50,150"
            ) ?: "50,150"
            val parts = s.split(",")
            if (parts.size != 2) {
                return Point(50, 150)
            }
            val x = parts[0].toIntOrNull() ?: 50
            val y = parts[1].toIntOrNull() ?: 150
            return Point(x, y)
        }
        set(point) = pref.edit().putString(
            "floating_button_position",
            "${point.x},${point.y}"
        ).apply()


}
