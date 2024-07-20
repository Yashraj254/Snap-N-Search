package com.yashraj.snapnsearch.ui

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.SeekBar
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.yashraj.snapnsearch.R
import com.yashraj.snapnsearch.databinding.ActivityMainBinding
import com.yashraj.snapnsearch.services.FloatingTileService
import com.yashraj.snapnsearch.services.ScreenshotAccessibilityService
import com.yashraj.snapnsearch.services.SwitchListener
import com.yashraj.snapnsearch.services.assist.MyVoiceInteractionService
import com.yashraj.snapnsearch.utils.PrefManager
import com.yashraj.snapnsearch.utils.isNewAppInstallation
import com.yashraj.snapnsearch.utils.toastMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    companion object {
        const val TAG = "MainActivity.kt"

        /**
         * Start this activity from a service
         */
        fun startNewTask(ctx: Context) {
            ctx.startActivity(
                Intent(ctx, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
        }

        private var switchListener: SwitchListener? = null

        fun setSwitchListener(listener: SwitchListener) {
            switchListener = listener
        }

        fun updateSwitchState(isChecked: Boolean) {
            switchListener?.onSwitchUpdate(isChecked)
        }

        var accessibilityConsent = false
    }

    private lateinit var binding: ActivityMainBinding
    private var restrictedSettingsAlertDialog: AlertDialog? = null

    private var r = 0
    private var g = 0
    private var b = 0
    private var size = 0
    private var sbAlpha = 0

    // Define the listener
    private val listener = CompoundButton.OnCheckedChangeListener { _, _ ->
        // Only execute if this was a user interaction
        MyVoiceInteractionService.openVoiceInteractionSettings(this, TAG, prefManager)
    }

    @Inject
    lateinit var prefManager: PrefManager


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

        setSupportActionBar(binding.mainToolbar)

        if (!prefManager.floatingButton) {
            binding.floatingButtonSetting.visibility = View.GONE

        }
        // Set the switch listener in the companion object
        setSwitchListener(object : SwitchListener {
            override fun onSwitchUpdate(isChecked: Boolean) {
                binding.switchFloatingButton.isChecked = isChecked
            }
        })


        initListeners()

        val switchFloatingButton = binding.switchFloatingButton

        updateSwitches()

        switchFloatingButton.setOnCheckedChangeListener { _, isChecked ->
            prefManager.floatingButton = isChecked
            binding.buttonFloatingButtonSetting.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (!isChecked) binding.floatingButtonSetting.visibility = View.GONE
            updateViews()

            if (isChecked && ScreenshotAccessibilityService.instance == null) {
                if (accessibilityConsent) {
                    // Open Accessibility settings
                    ScreenshotAccessibilityService.openAccessibilitySettings(this, TAG, prefManager)
                } else {
                    askToEnableAccessibility()
                }
            } else if (ScreenshotAccessibilityService.instance != null) {
                ScreenshotAccessibilityService.instance!!.updateFloatingButton()
            }
        }

        // Show warning if app is installed on external storage
        try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0)
                ).flags
            } else {
                packageManager.getApplicationInfo(packageName, 0).flags
            }
            if (flags and ApplicationInfo.FLAG_EXTERNAL_STORAGE != 0
            ) {
                toastMessage(
                    "App is installed on external storage, this can cause problems  after a reboot with the floating button and the assistant function.",
                    Toast.LENGTH_LONG
                )
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, e.toString())
        }

        // On Android 13 Tiramisu we ask the user to add the tile to the quick settings
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            (isNewAppInstallation(this))
        ) {
            askToAddTiles()
        }

        updateViews()
        switchFloatingButton.isChecked = prefManager.floatingButton
        binding.buttonFloatingButtonSetting.visibility = if (prefManager.floatingButton) View.VISIBLE else View.GONE


    }

    private fun initListeners() {
        binding.apply {

            ivCloseButton.visibility = if (prefManager.floatingButtonShowClose) View.VISIBLE else View.GONE

            buttonFloatingButtonSetting.setOnClickListener {
                binding.floatingButtonSetting.visibility = View.VISIBLE
                it.visibility = View.GONE
            }

            btnUpdate.setOnClickListener {
                binding.floatingButtonSetting.visibility = View.GONE
                binding.buttonFloatingButtonSetting.visibility = View.VISIBLE
                prefManager.floatingButtonColor_R = r
                prefManager.floatingButtonColor_G = g
                prefManager.floatingButtonColor_B = b
                prefManager.floatingButtonSize = size
                prefManager.floatingButtonAlpha = sbAlpha
                prefManager.floatingButton = binding.switchFloatingButton.isChecked
                prefManager.floatingButtonShowClose = binding.switchClosingButton.isChecked

                ScreenshotAccessibilityService.instance?.updateFloatingButton(true)
            }

            seekBarFloatingButtonColorR.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    updateVectorDrawableColor(progress)
                    r = progress
                    updateVectorDrawableColor(red = r)
                    binding.tvR.text = "R($r)"
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                }
            })

            seekBarFloatingButtonColorG.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    updateVectorDrawableColor(progress)
                    g = progress
                    updateVectorDrawableColor(green = g)
                    binding.tvG.text = "G($g)"
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                }
            })

            seekBarFloatingButtonColorB.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    updateVectorDrawableColor(progress)
                    b = progress
                    updateVectorDrawableColor(blue = b)
                    binding.tvB.text = "B($b)"
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                }
            })

            seekBarFloatingButtonAlpha.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    sbAlpha = progress
                    binding.ivFloatButton.alpha = progress.toFloat() / 100
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                }
            })

            seekBarFloatingButtonScale.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    size = progress
                    binding.ivFloatButton.layoutParams = binding.ivFloatButton.layoutParams.apply {
                        width = progress
                        height = progress
                    }
                    binding.ivCloseButton.layoutParams = binding.ivCloseButton.layoutParams.apply {
                        width = progress / 3
                        height = progress / 3
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                }
            })

            switchClosingButton.setOnCheckedChangeListener { _, isChecked ->
                binding.ivCloseButton.visibility = if (isChecked) View.VISIBLE else View.GONE
            }

            textTitleAssist.setOnClickListener {
                switchAssist.toggle()
            }

            textTitleFloatingButton.setOnClickListener {
                switchFloatingButton.toggle()
            }
        }
    }

    private fun updateViews() {
        binding.apply {
            r = prefManager.floatingButtonColor_R
            g = prefManager.floatingButtonColor_G
            b = prefManager.floatingButtonColor_B
            size = prefManager.floatingButtonSize
            sbAlpha = prefManager.floatingButtonAlpha
            switchClosingButton.isChecked = prefManager.floatingButtonShowClose

            updateVectorDrawableColor(r, g, b)
            ivFloatButton.alpha = sbAlpha.toFloat() / 100

            ivFloatButton.layoutParams = ivFloatButton.layoutParams.apply {
                width = size
                height = size
            }
            seekBarFloatingButtonColorB.progress = b
            seekBarFloatingButtonColorG.progress = g
            seekBarFloatingButtonColorR.progress = r
            seekBarFloatingButtonAlpha.progress = sbAlpha
            seekBarFloatingButtonScale.progress = size
            tvR.text = "R($r)"
            tvG.text = "G($g)"
            tvB.text = "B($b)"

        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun askToAddTiles() {

        // ask for floating button tile
        if (FloatingTileService.instance == null) {
            val statusBarManager = getSystemService(StatusBarManager::class.java)
            statusBarManager.requestAddTileService(
                ComponentName(this, FloatingTileService::class.java),
                getString(R.string.tile_floating),
                Icon.createWithResource(this, R.drawable.ic_snap),
                {},
                {})
        }
    }


    private fun askToEnableAccessibility() {
        val builder = MaterialAlertDialogBuilder(this)
        builder.setTitle(R.string.googleplay_consent_title)
        builder.setMessage(
            getString(R.string.googleplay_consent_text)
        )
        builder.setOnDismissListener {
            if (ScreenshotAccessibilityService.instance == null)
                binding.switchFloatingButton.isChecked = false

        }
        builder.setPositiveButton(getString(R.string.googleplay_consent_yes)) { _, _ ->
            accessibilityConsent = true
            if (ScreenshotAccessibilityService.instance == null)
                ScreenshotAccessibilityService.openAccessibilitySettings(this, TAG, prefManager)
        }
        builder.setNegativeButton(getString(R.string.googleplay_consent_no)) { _, _ ->
            accessibilityConsent = false
            binding.switchFloatingButton.isChecked = false
        }
        builder.show()

    }

    private fun updateSwitches() {

        if (prefManager.floatingButton) {
            // User might have returned from accessibility settings without activating the service
            // Show dialog about restricted settings
            if (ScreenshotAccessibilityService.instance == null) {
                informAboutRestrictedSettings()
            }
            binding.switchFloatingButton.isChecked = ScreenshotAccessibilityService.instance != null

        }

    }

    private fun updateVectorDrawableColor(red: Int = r, green: Int = g, blue: Int = b) {
        val imageView = binding.ivFloatButton
        val drawable = imageView.drawable
        val color = Color.rgb(red, green, blue)

        // Change red component
        drawable.setTint(color)
        imageView.setImageDrawable(drawable)
    }


    private fun informAboutRestrictedSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }
        restrictedSettingsAlertDialog?.dismiss()

        val builder = MaterialAlertDialogBuilder(this)
        builder.setTitle(R.string.restricted_settings_title)
        builder.setMessage(R.string.restricted_settings_text)

        builder.setNeutralButton(R.string.restricted_settings_open_settings) { _, _ ->
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            }
        }
        builder.setPositiveButton(R.string.restricted_settings_open_accessibility) { _, _ ->
            ScreenshotAccessibilityService.openAccessibilitySettings(this, TAG, prefManager)
        }
        builder.setNegativeButton(android.R.string.cancel, null)
        restrictedSettingsAlertDialog = builder.show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_about, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_about -> {
                // Handle the About menu item click
                startActivity(Intent(this, AboutActivity::class.java))
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }


    override fun onResume() {
        super.onResume()
        updateSwitches()
        binding.switchAssist.isChecked =
            Settings.Secure.getString(contentResolver, "assistant") == "$packageName/.services.assist.MyVoiceInteractionService"
        if (binding.switchFloatingButton.isChecked) {
            prefManager.floatingButton = true
            ScreenshotAccessibilityService.instance!!.updateFloatingButton(true)
        }

        binding.switchAssist.setOnCheckedChangeListener(listener)
    }

    override fun onPause() {
        super.onPause()
        restrictedSettingsAlertDialog?.dismiss()
    }

    override fun onStop() {
        binding.switchAssist.setOnCheckedChangeListener(null)
        binding.switchAssist.isChecked = false
        super.onStop()
    }

    override fun onDestroy() {
        cacheDir.deleteRecursively()
        super.onDestroy()
    }

}