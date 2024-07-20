package com.yashraj.snapnsearch.services

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.TileService
import android.util.Log
import android.view.Display.DEFAULT_DISPLAY
import android.view.DragEvent
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.yashraj.snapnsearch.R
import com.yashraj.snapnsearch.databinding.AccessibilityBarBinding
import com.yashraj.snapnsearch.ui.MainActivity
import com.yashraj.snapnsearch.ui.TakeScreenshotActivity
import com.yashraj.snapnsearch.utils.PrefManager
import com.yashraj.snapnsearch.utils.isDeviceLocked
import com.yashraj.snapnsearch.utils.safeRemoveView
import com.yashraj.snapnsearch.utils.shareToGoogleLens
import com.yashraj.snapnsearch.utils.startActivityAndCollapseCustom
import com.yashraj.snapnsearch.utils.toastMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject


@AndroidEntryPoint
class ScreenshotAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var prefManager: PrefManager

    companion object {
        var instance: ScreenshotAccessibilityService? = null
        private const val TAG = "ScreenshotAccessService"

        /**
         * Open accessibility settings from activity
         */
        fun openAccessibilitySettings(context: Activity, returnTo: String? = null, prefManager: PrefManager) {
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                if (resolveActivity(context.packageManager) != null) {
                    if (returnTo != null) {
                        prefManager.returnIfAccessibilityServiceEnabled = returnTo
                    }
                    context.startActivity(this)
                    Handler(Looper.getMainLooper()).postDelayed({
                        context.toastMessageAccessibility()
                    }, 2000)
                }
            }
        }

        /**
         * Open accessibility settings and collapse quick settings panel
         */
        fun openAccessibilitySettings(tileService: TileService, returnTo: String? = null, prefManager: PrefManager) {
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (resolveActivity(tileService.packageManager) != null) {
                    if (returnTo != null) {
                        prefManager.returnIfAccessibilityServiceEnabled = returnTo
                    }
                    tileService.toastMessageAccessibility()
                    tileService.startActivityAndCollapseCustom(this)
                    Handler(Looper.getMainLooper()).postDelayed({
                        tileService.toastMessageAccessibility()
                    }, 2000)

                }
            }
        }

        /**
         * Inform user that they should enable the accessibility service
         */
        private fun Context.toastMessageAccessibility() {
            if (instance == null) {
                toastMessage(
                    getString(
                        R.string.toast_open_accessibility_settings,
                        getString(R.string.app_name)
                    ), Toast.LENGTH_LONG
                )
            }
        }

        fun setShutterDrawable(context: Context, button: ImageView, res: Int) {
            button.setImageDrawable(
                ContextCompat.getDrawable(
                    context,
                    res
                )
            )
        }
    }

    private var floatingButtonShown = false
    private var binding: AccessibilityBarBinding? = null
    private var useThis = false

    override fun onServiceConnected() {
        instance = this
        try {
            if (prefManager.returnIfAccessibilityServiceEnabled == MainActivity.TAG) {
                prefManager.returnIfAccessibilityServiceEnabled = null
                MainActivity.startNewTask(this)
            }

        } catch (e: ActivityNotFoundException) {
            // This seems to happen after booting
            Log.e(
                TAG,
                "Could not start Activity for return to '${prefManager.returnIfAccessibilityServiceEnabled}'",
                e
            )

        }

        updateFloatingButton()
    }

    private fun getWinContext(): Context {
        var windowContext: Context = this
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !useThis) {
            val dm: DisplayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val defaultDisplay = dm.getDisplay(DEFAULT_DISPLAY)
            windowContext = createDisplayContext(defaultDisplay)
        }
        return windowContext
    }

    private fun getWinManager(): WindowManager {
        return getWinContext().getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    /**
     * Toggle the floating button according to the current settings
     */
    fun updateFloatingButton(forceRedraw: Boolean = false) {
        val prefValue = prefManager.floatingButton
        if (prefValue && !floatingButtonShown) {
            showFloatingButton()
        } else if (!prefValue && floatingButtonShown) {
            hideFloatingButton()
        } else if (prefValue && forceRedraw) {
            hideFloatingButton()
            showFloatingButton()
        }
    }

    private fun showFloatingButton() {
        floatingButtonShown = true

        binding =
            AccessibilityBarBinding.inflate(getWinContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater)

        binding?.root?.let { root ->
            configureFloatingButton(root)
        }
    }

    private fun configureFloatingButton(root: ViewGroup) {
        val position = prefManager.floatingButtonPosition

        addWindowViewAt(root, position.x, position.y)

        val buttonScreenshot = root.findViewById<ImageView>(R.id.buttonScreenshot)
        setShutterDrawable(this, buttonScreenshot, R.drawable.ic_snap)
        val buttonClose = root.findViewById<ImageView>(R.id.button_close)

        if (prefManager.floatingButtonShowClose) {
            buttonClose.setOnClickListener {
                prefManager.floatingButton = false
                hideFloatingButton()
                MainActivity.updateSwitchState(prefManager.floatingButton)
            }

            buttonClose.visibility = View.VISIBLE
        }

        binding?.let { updateViews(it) }
        buttonScreenshot.setOnClickListener {

            if (isDeviceLocked(this)) {
                toastMessage(getString(R.string.screenshot_prevented))
                return@setOnClickListener
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                root.visibility = View.GONE
                root.invalidate()

                root.postDelayed({
                    takeScreenshot()
                    root.visibility = View.VISIBLE
                }, 1000)
            } else {
                val intent = Intent(this, TakeScreenshotActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        }

        var dragDone = false
        buttonScreenshot.setOnDragListener { v, event ->
            when (event.action) {
                DragEvent.ACTION_DROP, DragEvent.ACTION_DRAG_ENDED -> {

                    root.let {
                        if (!dragDone) {
                            val x: Int
                            val y: Int
                            if (event.action == DragEvent.ACTION_DROP) {
                                // x and y are relative to the inside of the view's bounding box
                                val old = prefManager.floatingButtonPosition

                                x = (old.x - v.measuredWidth / 2.0 + event.x).toInt()

                                y = (old.y - v.measuredHeight / 2.0 + event.y).toInt()

                            } else {
                                val parent = v.parent as View
                                x = (event.x - parent.measuredWidth / 2).toInt()
                                y = (event.y - parent.measuredHeight / 2).toInt()
                            }
                            dragDone = true
                            getWinManager().updateViewLayout(
                                it,
                                windowViewAbsoluteLayoutParams(x, y)
                            )
                            prefManager.floatingButtonPosition =
                                Point(x, y)
                        }
                        setShutterDrawable(
                            this,
                            buttonScreenshot,
                            R.drawable.ic_snap
                        )

                        binding?.let { it1 -> updateViews(it1) }
                    }

                    v.alpha = 1f
                    true
                }

                else -> true
            }
        }

        buttonScreenshot.setOnLongClickListener {
            dragDone = false
            buttonScreenshot.alpha = 0.5f
            it.startDragAndDrop(null, View.DragShadowBuilder(root), null, 0)
            it.alpha = 0f
            true
        }
    }

    /**
     * Temporary hide floating button to take a screenshot
     */
    fun temporaryHideFloatingButton(maxHiddenTime: Long = 10000L) {
        binding?.root?.apply {
            visibility = View.GONE
            invalidate()
            Handler(Looper.getMainLooper()).postDelayed({
                binding?.root?.apply {
                    visibility = View.VISIBLE
                }
            }, maxHiddenTime)
        }
    }

    private fun updateViews(binding: AccessibilityBarBinding) {
        binding.apply {
            val colorR = prefManager.floatingButtonColor_R
            val colorG = prefManager.floatingButtonColor_G
            val colorB = prefManager.floatingButtonColor_B
            val size = prefManager.floatingButtonSize
            val sb_alpha = prefManager.floatingButtonAlpha
            buttonScreenshot.apply {
                post {
                    drawable.setTint(Color.rgb(colorR, colorG, colorB))
                    alpha = sb_alpha.toFloat() / 100
                    layoutParams = layoutParams.apply {
                        width = size
                        height = size
                    }
                }
            }
            buttonClose.apply {
                post {
                    alpha = sb_alpha.toFloat() / 100
                    layoutParams = layoutParams.apply {
                        width = size / 3
                        height = size / 3
                    }
                }
            }

        }
    }

    /**
     * Remove view if it exists
     */
    private fun hideFloatingButton() {
        floatingButtonShown = false
        binding?.root?.let {
            getWinManager().safeRemoveView(it, TAG)
        }
        binding = null
    }

    private fun addWindowViewAt(
        view: View,
        x: Int = 0,
        y: Int = 0,
        tryAgainOnFailure: Boolean = true
    ) {
        try {
            getWinManager().addView(
                view,
                windowViewAbsoluteLayoutParams(x, y)
            )
        } catch (e: WindowManager.BadTokenException) {
            Log.e(TAG, "windowManager.addView failed for invalid token:", e)
            if (tryAgainOnFailure) {
                try {
                    getWinManager().removeView(view)
                } catch (e: Exception) {
                    Log.e(TAG, "windowManager.removeView failed as well:", e)
                }
                useThis = true
                addWindowViewAt(view, x, y, false)
            }
        }
    }

    private fun windowViewAbsoluteLayoutParams(x: Int, y: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams().apply {
            type = TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            @SuppressLint("RtlHardcoded")
            gravity = Gravity.TOP or Gravity.LEFT
            this.x = x
            this.y = y
            // Allow the floating button to cover the camera notch/cutout
            flags = flags or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }


    @RequiresApi(Build.VERSION_CODES.R)
    private fun takeScreenshot() {
        super.takeScreenshot(
            DEFAULT_DISPLAY,
            { r -> Thread(r).start() },
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val bitmap = Bitmap.wrapHardwareBuffer(
                        screenshot.hardwareBuffer,
                        screenshot.colorSpace
                    )?.copy(Bitmap.Config.ARGB_8888, false)
                    screenshot.hardwareBuffer.close()
                    if (bitmap != null) {
                        shareToGoogleLens(bitmap, this@ScreenshotAccessibilityService)
                        stopSelf()

                    }
                }

                override fun onFailure(errorCode: Int) {
                    Log.e(
                        TAG,
                        "takeScreenshot() -> onFailure($errorCode), falling back to GLOBAL_ACTION_TAKE_SCREENSHOT"
                    )

                }
            })
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // No op
    }

    override fun onInterrupt() {
        // No op
    }
}

