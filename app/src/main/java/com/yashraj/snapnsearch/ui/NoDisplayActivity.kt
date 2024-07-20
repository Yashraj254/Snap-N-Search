package com.yashraj.snapnsearch.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

// To dismiss the quick settings panel when the user taps the tile
class NoDisplayActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}