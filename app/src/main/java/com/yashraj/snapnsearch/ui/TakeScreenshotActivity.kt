package com.yashraj.snapnsearch.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.lifecycle.lifecycleScope
import com.yashraj.snapnsearch.R
import com.yashraj.snapnsearch.services.BasicForegroundService
import com.yashraj.snapnsearch.services.ScreenshotAccessibilityService
import com.yashraj.snapnsearch.utils.PrefManager
import com.yashraj.snapnsearch.utils.imageToBitmap
import com.yashraj.snapnsearch.utils.shareToGoogleLens
import com.yashraj.snapnsearch.utils.toastMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TakeScreenshotActivity : ComponentActivity(), OnAcquireScreenshotPermissionListener {


    private val SCREENSHOT_REQUEST_CODE = 4552
    private val TAG = "TakeScreenshotActivity"

    private var instance: TakeScreenshotActivity? = null
    private var mediaProjection: MediaProjection? = null
    private var mediaProjectionManager: MediaProjectionManager? = null
    private var onAcquireScreenshotPermissionListener: OnAcquireScreenshotPermissionListener? = null
    private var screenshotPermission: Intent? = null

    private var screenDensity: Int = 0
    private var screenHeight: Int = 0
    private var screenWidth: Int = 0
    private var screenSharing: Boolean = false
    private var surface: Surface? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    @Inject
    lateinit var prefManager: PrefManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BasicForegroundService.instance?.foreground()

        if (prefManager.floatingButton && ScreenshotAccessibilityService.instance != null) {
            ScreenshotAccessibilityService.instance?.temporaryHideFloatingButton()
        }

        screenDensity = resources.configuration.densityDpi
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        Point().apply {
            windowManager.defaultDisplay.getRealSize(this)
            screenWidth = x
            screenHeight = y
        }

        initializeScreenCapture()
    }

    private fun initializeScreenCapture() {
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 1)
        surface = imageReader?.surface
        if (screenshotPermission == null) {
            acquireScreenshotPermission(this, this)
        } else {
            prepareForScreenSharing()
        }
    }

    private fun acquireScreenshotPermission(context: Context?, myOnAcquireScreenshotPermissionListener: OnAcquireScreenshotPermissionListener) {
        onAcquireScreenshotPermissionListener = myOnAcquireScreenshotPermissionListener
        if (context == null) {
            Log.d(TAG, "Context is null. Cannot acquire screenshot permission.")
            return
        }
        BasicForegroundService.startForegroundService(context)
        if (screenshotPermission == null) {
            openScreenshotPermissionRequester(context)
        } else {
            instance?.prepareForScreenSharing()
            Log.d(TAG, "acquireScreenshotPermission: prepareForScreenSharing()")
        }
    }

    private fun openScreenshotPermissionRequester(context: Context) {

        val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
        if (context is Activity) {
            context.startActivityForResult(captureIntent, SCREENSHOT_REQUEST_CODE)
        } else {
            val intent = Intent(context, TakeScreenshotActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == SCREENSHOT_REQUEST_CODE) {
            when (resultCode) {
                RESULT_OK -> {
                    data?.let {
                        setScreenshotPermission(it.clone() as Intent)
                    }
                }

                RESULT_CANCELED -> {
                    setScreenshotPermission(null)
                    finish()
                }

                else -> {
                    setScreenshotPermission(null)
                    finish()
                }
            }
        }
    }

    private fun setScreenshotPermission(permissionIntent: Intent?) {
        screenshotPermission = permissionIntent
        onAcquireScreenshotPermissionListener?.onAcquireScreenshotPermission(permissionIntent != null)
        onAcquireScreenshotPermissionListener = null
    }

    private fun prepareForScreenSharing() {
        if (surface == null) {
            finish()
            return
        }
        screenSharing = true
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = try {
            mediaProjectionManager?.getMediaProjection(RESULT_OK, screenshotPermission?.clone() as Intent)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: ${e.localizedMessage}")
            lifecycleScope.launch(Dispatchers.Main) {
                delay(500)
                finish()

            }
            return
        }

        if (mediaProjection == null) {
            acquireScreenshotPermission(this, this)
            return
        }

        try {
            startVirtualDisplay()
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: ${e.localizedMessage}")
            setScreenshotPermission(null)
            stopScreenSharing()

            finish()
        }
    }

    private fun startVirtualDisplay() {
        surface = imageReader?.surface ?: run {
            toastMessage(getString(R.string.failed_to_create_image_reader))
            return
        }
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                if (screenSharing) {
                    stopScreenSharing()
                }
            }
        }, null)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenshotTaker",
            screenWidth,
            screenHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface,
            null,
            null
        )


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            setScreenshotPermission(null)
        }
        imageReader?.setOnImageAvailableListener({
            it.setOnImageAvailableListener(null, null)
            shareToLens()
        }, null)
    }

    private fun shareToLens() {
        val image = imageReader?.acquireNextImage() ?: run {
            toastMessage(getString(R.string.failed_to_acquire_image))
            return
        }
        stopScreenSharing()

        shareToGoogleLens(imageToBitmap(image), this)
        image.close()
        BasicForegroundService.instance?.background()
        finish()

    }

    override fun onAcquireScreenshotPermission(isNewPermission: Boolean) {
        if (screenshotPermission == null) {
            toastMessage(getString(R.string.grant_screenshot_permission))
            return
        }
        if (isNewPermission) {
            lifecycleScope.launch(Dispatchers.Main) {
                delay(500)
                prepareForScreenSharing()
            }
        } else {
            prepareForScreenSharing()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mediaProjection != null) {
            mediaProjection?.stop()
            mediaProjection = null
            BasicForegroundService.instance?.background()
        }
    }

    private fun stopScreenSharing() {
        screenSharing = false
        mediaProjection?.stop()
        virtualDisplay?.release()
        surface?.release()
        virtualDisplay = null
        surface = null
        imageReader = null
    }

}

