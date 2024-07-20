package com.yashraj.snapnsearch.utils

import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.VersionedPackage
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.media.Image
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.TileService
import android.util.Log
import android.view.View
import android.view.ViewManager
import android.widget.Toast
import androidx.core.content.FileProvider
import com.yashraj.snapnsearch.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

const val UTILSKT = "Utils.kt"


/**
 * Was the app updated or newly installed
 */
fun isNewAppInstallation(context: Context): Boolean {
    return try {
        return context.packageManager.getPackageInfo(context.packageName)?.run {
            firstInstallTime == lastUpdateTime
        } ?: true
    } catch (e: PackageManager.NameNotFoundException) {
        Log.e(UTILSKT, "Package not found", e)
        true
    } catch (e: java.lang.Exception) {
        Log.e(UTILSKT, "Unexpected error in isNewAppInstallation()", e)
        false
    }
}

/**
 * Retrieve overall information about highest version of an application package that is installed
 * on the system
 */
fun PackageManager.getPackageInfo(packageName: String): PackageInfo? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageInfo(
            VersionedPackage(packageName, PackageManager.VERSION_CODE_HIGHEST),
            PackageManager.PackageInfoFlags.of(0)
        )
    } else {
        getPackageInfo(packageName, 0)
    }
}

/**
 * Show a string as a Toast message
 */
fun Context?.toastMessage(text: String, duration: Int = Toast.LENGTH_LONG) {
    this?.run {
        Toast.makeText(this, text, duration).show()
    }
}

/**
 * Call removeView() and catch Exceptions
 */
fun ViewManager.safeRemoveView(view: View, tag: String = UTILSKT) {
    try {
        this.removeView(view)
    } catch (e: Exception) {
        Log.e(tag, "removeView() of $this threw e: $e")
    }
}

/**
 * Check if the phone is currently locked
 */
fun isDeviceLocked(context: Context): Boolean {
    return (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceLocked
}

fun TileService.startActivityAndCollapseCustom(intent: Intent) {
    return if (Build.VERSION.SDK_INT > Build.VERSION_CODES.TIRAMISU) {
        this.startActivityAndCollapse(
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE
            )
        )
    } else {
        @Suppress("DEPRECATION")
        this.startActivityAndCollapse(intent)
    }
}

fun imageToBitmap(image: Image): Bitmap {
    if (image.format == ImageFormat.JPEG) {
        return imageJPEGToBitmap(image)
    }
    val offset =
        (image.planes[0].rowStride - image.planes[0].pixelStride * image.width) / image.planes[0].pixelStride
    val w = image.width + offset
    val h = image.height
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    bitmap.copyPixelsFromBuffer(image.planes[0].buffer)
    return Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)

}

fun imageJPEGToBitmap(image: Image): Bitmap {
    val w = image.width
    val h = image.height

    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    image.planes[0].buffer.let {
        it.rewind()
        bitmap.copyPixelsFromBuffer(it)
    }
    return Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)

}

fun shareToGoogleLens(bitmap: Bitmap, context: Context) {
    val cachePath = File(context.cacheDir, "images")
    cachePath.mkdirs()
    val file = File(cachePath, "screenshot.png")
    try {
        val outputStream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        outputStream.close()
    } catch (e: IOException) {
        e.printStackTrace()
        return
    }

    val contentUri: Uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, contentUri)
        setPackage("com.google.ar.lens")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    } else {
        CoroutineScope(Dispatchers.Main).launch {
            context.toastMessage(context.resources.getString(R.string.lens_not_installed), Toast.LENGTH_LONG)

            // Redirect to Google Play Store or Settings
            try {
                val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.ar.lens")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(marketIntent)
            } catch (e: Exception) {
                val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:com.google.ar.lens")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
            }
        }
    }
}
