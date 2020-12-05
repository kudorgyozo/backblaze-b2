package com.gyozo.backblazeb2

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.Intent.*
import android.net.Uri
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.gyozo.backblazeb2.BBIntentService.Companion.ACTION_UPLOAD
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.*
import android.widget.Button

class MainActivity : AppCompatActivity() {

    private lateinit var textAppKeyId : TextView
    private lateinit var textAppKey : TextView
    private lateinit var textBucketName : TextView
    private lateinit var txtFilePath : TextView
    private lateinit var txtResult : TextView
    private lateinit var btnUpload : Button

    private lateinit var appKeyId : String
    private lateinit var appKey : String
    private lateinit var bucketName : String
    private lateinit var filePath : String
    private lateinit var fileUrls: ArrayList<Uri>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textAppKeyId = findViewById<TextView>(R.id.txtAppId)
        textAppKey = findViewById<TextView>(R.id.txtAppKey)
        textBucketName = findViewById<TextView>(R.id.txtBucketName)
        txtFilePath = findViewById<TextView>(R.id.txtFilePath)
        txtResult = findViewById<TextView>(R.id.txtResult)
        btnUpload = findViewById<Button>(R.id.btnUpload)
        readSettings()

        if (intent.action == ACTION_MAIN)
        {
            btnUpload.isEnabled = false
        } else if (intent.action == ACTION_SEND) {
            val uri = intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri

            if (uri != null && !appKeyId.isNullOrEmpty() && !appKey.isNullOrEmpty()) {
                var uriArray = ArrayList<Uri>()
                uriArray.add(uri)
                fileUrls = uriArray
                txtResult.text = "Press upload to start uploading ${fileUrls.size} files"
                btnUpload.isEnabled = true
            }

        } else if (intent.action == ACTION_SEND_MULTIPLE) {
            val urls = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM);

            if (urls != null && !appKeyId.isNullOrEmpty() && !appKey.isNullOrEmpty()) {
                fileUrls = urls
                txtResult.text = "Press upload to start uploading ${fileUrls.size} files"
                btnUpload.isEnabled = true
            }

        }

    }

    fun startBBService(view: View) {

        if (!isInternetAvailable()) {
            AlertDialog.Builder(this)
                .setTitle("No internet found!")
                .setMessage(" You need internet to upload files")
                .setPositiveButton(android.R.string.yes, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show()
            return
        }

        val intent : Intent = Intent(this, BBIntentService::class.java)
        intent.action = ACTION_UPLOAD
        intent.putExtra(BBIntentService.AppKeyId, appKeyId)
        intent.putExtra(BBIntentService.AppKey, appKey)
        intent.putExtra(BBIntentService.BucketNameKey, bucketName)
        intent.putExtra(BBIntentService.FilePath, filePath)

        intent.putParcelableArrayListExtra(BBIntentService.FileUrl, fileUrls)

        val rr = MyResultReceiver(null)
        intent.putExtra(BBIntentService.ReceiverKey, rr)
        startService(intent)
    }

    @Suppress("DEPRECATION")
    fun isInternetAvailable(): Boolean {
        var result = false
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            cm?.run {
                cm.getNetworkCapabilities(cm.activeNetwork)?.run {
                    result = when {
                        hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                        hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                        hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                        else -> false
                    }
                }
            }
        } else {
            cm?.run {
                cm.activeNetworkInfo?.run {
                    if (type == ConnectivityManager.TYPE_WIFI) {
                        result = true
                    } else if (type == ConnectivityManager.TYPE_MOBILE) {
                        result = true
                    }
                }
            }
        }
        return result
    }

    fun saveSettings(view: View) {
        appKeyId = textAppKeyId!!.text.toString().trim()
        appKey = textAppKey!!.text.toString().trim()
        bucketName = textBucketName!!.text.toString().trim()
        filePath = txtFilePath.text.toString().trim()

        val sharedPref = getSharedPreferences(getString(R.string.prefs_key), Context.MODE_PRIVATE);
        with (sharedPref.edit()) {
            putString(Pref_ApplicationKeyId, appKeyId)
            putString(Pref_ApplicationKey, appKey)
            putString(Pref_BucketName, bucketName)
            putString(Pref_FilePath, filePath)
            commit()
        }
    }

    fun readSettings() {
        val sharedPref = getSharedPreferences(getString(R.string.prefs_key), Context.MODE_PRIVATE);
        textAppKeyId.text = sharedPref.getString(Pref_ApplicationKeyId, "")
        textAppKey.text = sharedPref.getString(Pref_ApplicationKey, "")
        textBucketName.text = sharedPref.getString(Pref_BucketName, "")
        txtFilePath.text = sharedPref.getString(Pref_FilePath, "")

        appKeyId = textAppKeyId.text.toString()
        appKey = textAppKey.text.toString()
        bucketName = textBucketName.text.toString()
        filePath = txtFilePath.text.toString()
    }

    inner class MyResultReceiver(handler: Handler?) : ResultReceiver(handler) {

        override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
            super.onReceiveResult(resultCode, resultData)
            if (resultCode == BBIntentService.ResultCode && resultData != null) {
                val resultMessage = resultData.getString(BBIntentService.ResultStatusKey, "")
                txtResult.post({
                    txtResult.text = resultMessage;
                })

            }
        }
    }



    companion object {
//        <string name="Pref_ApplicationKeyId">Pref_ApplicationKeyId</string>
//        <string name="Pref_ApplicationKey">Pref_ApplicationKey</string>

        const val Pref_ApplicationKeyId = "Pref_ApplicationKeyId"
        const val Pref_ApplicationKey = "Pref_ApplicationKey"
        const val Pref_BucketName = "Pref_BucketName"
        const val Pref_FilePath = "Pref_FilePath"

    }
}
