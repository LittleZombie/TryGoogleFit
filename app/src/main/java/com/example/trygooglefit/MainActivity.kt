package com.example.trygooglefit

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private val TAG = this.javaClass.simpleName

    companion object {
        const val REQUEST_CODE_GOOGLE_FIT_PERMISSIONS = 115
        const val REQUEST_ACTIVITY_RECOGNITION_PERMISSIONS = 116
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setViews()
        setButtons()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.i(TAG, "onActivityResult( requestCode = $requestCode" + ", resultCode = $resultCode, data = ${Gson().toJson(data)}")
        when(requestCode) {
            REQUEST_CODE_GOOGLE_FIT_PERMISSIONS -> {
                if (Activity.RESULT_OK == resultCode) {
                    setViews()
                }
            }
            REQUEST_ACTIVITY_RECOGNITION_PERMISSIONS -> {
                if (hasActivityPermission()) {
                    requestGoogleFitPermission()
                }
            }
        }
    }

    private fun setViews() {
        baseContext?.run {
            GoogleFitController.INSTANCE.hasPermissions(this) { hasPermissions ->
                button.let {
                    if (hasPermissions) {
                        it.setText(R.string.disconnect)
                        it.setOnClickListener {
                            onDisconnectClick()
                        }
                        button_request_step.isEnabled = true
                        button_request_sleep.isEnabled = true
                    } else {
                        it.setText(R.string.connect)
                        it.setOnClickListener {
                            onConnectClick()
                        }
                        button_request_step.isEnabled = false
                        button_request_sleep.isEnabled = false
                    }
                }

                if (hasPermissions && !hasActivityPermission()) {
                    layout_permission_info.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun setButtons() {
        button_request_step.setOnClickListener {
            baseContext?.run {
                GoogleFitController.INSTANCE.requestHistoryData(this) {
                    text_result.text = it
                }
            }
        }

        button_request_sleep.setOnClickListener {
            baseContext?.run {
                GoogleFitController.INSTANCE.requestSessionData(this) {
                    text_result.text = it
                }
            }
        }

        button_clear.setOnClickListener {
            text_result.text = "資料清除！"
        }

        text_update_permission.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun onConnectClick() {
        if (hasActivityPermission()) {
            requestGoogleFitPermission()
        } else {
            requestActivityPermission()
        }
    }

    private fun onDisconnectClick() {
        baseContext?.run {
            GoogleFitController.INSTANCE.disconnect(this) {
                setViews()
            }
        }
    }

    private fun requestGoogleFitPermission() {
        GoogleFitController.INSTANCE.requestPermissions(
            this,
            REQUEST_CODE_GOOGLE_FIT_PERMISSIONS
        )
    }

    private fun hasActivityPermission(): Boolean {
         return checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestActivityPermission() {
        requestPermissions(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), REQUEST_ACTIVITY_RECOGNITION_PERMISSIONS)
    }

}
