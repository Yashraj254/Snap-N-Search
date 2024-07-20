package com.yashraj.snapnsearch.services

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.annotation.RequiresApi
import com.yashraj.snapnsearch.R
import com.yashraj.snapnsearch.services.ScreenshotAccessibilityService.Companion.openAccessibilitySettings
import com.yashraj.snapnsearch.ui.MainActivity
import com.yashraj.snapnsearch.ui.NoDisplayActivity
import com.yashraj.snapnsearch.utils.PrefManager
import com.yashraj.snapnsearch.utils.startActivityAndCollapseCustom
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject


@AndroidEntryPoint
class FloatingTileService : TileService() {

    @Inject
    lateinit var prefManager: PrefManager

    companion object {
        private const val TAG = "FloatingTileService"
        var instance: FloatingTileService? = null

    }

    private fun toggleFloatingButton(context: Context, prefManager: PrefManager) {
        if (ScreenshotAccessibilityService.instance == null) {
            // Always enable if accessibility service is not yet running
            prefManager.floatingButton = true
            if (context is Activity) {
                openAccessibilitySettings(context, TAG, prefManager)
            } else if (context is TileService) {
                openAccessibilitySettings(context, TAG, prefManager)
            }
        } else {
            // Toggle if accessibility service is running
            prefManager.floatingButton =
                !prefManager.floatingButton
            ScreenshotAccessibilityService.instance?.updateFloatingButton()
            val intent = Intent(context, NoDisplayActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            if (context is TileService) {
                context.startActivityAndCollapseCustom(intent)
            } else {
                context.startActivity(intent)
            }
        }
        MainActivity.updateSwitchState(prefManager.floatingButton)
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        instance = this
    }

    private fun setState(newState: Int) {
        try {
            qsTile?.run {
                state = newState
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    qsTile.label = getString(R.string.tile_floating_label)
                    qsTile.subtitle = getString(R.string.tile_floating_subtitle)
                }
                updateTile()
            }
        } catch (e: IllegalStateException) {
            Log.e(TAG, "setState: IllegalStateException", e)
        } catch (e: NullPointerException) {
            Log.e(TAG, "setState: NullPointerException", e)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "setState: IllegalArgumentException", e)
        }
    }

    override fun onTileAdded() {
        super.onTileAdded()
        updateTileState()
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }


    override fun onClick() {
        super.onClick()
        toggleFloatingButton(this, prefManager)
        updateTileState()
    }

    private fun updateTileState() {
        // Set tile state according to settings and check if accessibility service is running
        setState(
            if (prefManager.floatingButton && ScreenshotAccessibilityService.instance != null) {
                Tile.STATE_ACTIVE
            } else {
                Tile.STATE_INACTIVE
            }
        )
    }

}

interface SwitchListener {
    fun onSwitchUpdate(isChecked: Boolean)
}