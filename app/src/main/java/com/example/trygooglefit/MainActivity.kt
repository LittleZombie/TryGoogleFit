package com.example.trygooglefit

import android.app.Activity
import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private val TAG = this.javaClass.simpleName

    companion object {
        const val REQUEST_CODE_GOOGLE_FIT_PERMISSIONS = 115
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setViews()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.i(TAG, "onActivityResult( requestCode = $requestCode" +
                ", resultCode = $resultCode, data = ${Gson().toJson(data)}")
        when(requestCode) {
            REQUEST_CODE_GOOGLE_FIT_PERMISSIONS -> {
                if (Activity.RESULT_OK == resultCode) {
                    setViews()
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
                        button_request_data.isEnabled = true
                    } else {
                        it.setText(R.string.connect)
                        it.setOnClickListener {
                            onConnectClick()
                        }
                        button_request_data.isEnabled = false
                    }
                }
            }
        }

        button_request_data.setOnClickListener {
            baseContext?.run {
                GoogleFitController.INSTANCE.requestHistoryData(this) {
                    text.text = formatString(it)
                }
            }
        }
    }

    private fun onConnectClick() {
        GoogleFitController.INSTANCE.requestPermissions(this, REQUEST_CODE_GOOGLE_FIT_PERMISSIONS)
    }

    private fun onDisconnectClick() {
        baseContext?.run {
            GoogleFitController.INSTANCE.disconnect(this) {
                setViews()
            }
        }
    }


    private fun formatString(text: String): String {

        val json = StringBuilder()
        var indentString = ""

        for (element in text) {
            when (element) {
                '{', '[' -> {
                    json.append("\n" + indentString + element + "\n")
                    indentString += "\t"
                    json.append(indentString)
                }
                '}', ']' -> {
                    indentString = indentString.replaceFirst("\t".toRegex(), "")
                    json.append("\n" + indentString + element)
                }
                ',' -> json.append(element + "\n" + indentString)

                else -> json.append(element)
            }
        }

        return json.toString()
    }

}
