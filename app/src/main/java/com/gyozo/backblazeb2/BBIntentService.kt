package com.gyozo.backblazeb2

import android.app.*
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ResultReceiver
import android.provider.OpenableColumns
import android.util.Base64
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.*

/**
 * An [IntentService] subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * TODO: Customize class - update intent actions, extra parameters and static
 * helper methods.
 */
class BBIntentService : IntentService("BBIntentService") {

    //parameters, received once
    private lateinit var appKeyId : String
    private lateinit var appKey : String
    private lateinit var bucketName : String
    private lateinit var filePath: String
    private lateinit var fileUrls : ArrayList<Uri>

    //variables calculated once
    private lateinit var authToken : String
    private lateinit var apiUrl : String
    private lateinit var accountId: String
    private lateinit var bucketId : String
    private var uploadToken: String? = null
    private var uploadUrl: String? = null

    //file variables calculated for each file
    private lateinit var fileName: String
    private lateinit var fileUrl : Uri
    private var fileSize : Int = 0
    private lateinit var fileHash : String

    private lateinit var resultReceiver : ResultReceiver
    private lateinit var notifBuilder: NotificationCompat.Builder

    override fun onHandleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_UPLOAD -> {
                fileUrls = intent.getParcelableArrayListExtra<Uri>(FileUrl)
                appKeyId = intent.getStringExtra(AppKeyId)
                appKey = intent.getStringExtra(AppKey)
                bucketName = intent.getStringExtra(BucketNameKey)
                filePath = intent.getStringExtra(FilePath)
                resultReceiver = intent.getParcelableExtra(ReceiverKey) as ResultReceiver;
                handleUpload();
            }

        }
    }

    private fun putForegroundNotification() {
        val mNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel("ChannelId",
                "YOUR_CHANNEL_NAME",
                NotificationManager.IMPORTANCE_DEFAULT)
            channel.description = "YOUR_NOTIFICATION_CHANNEL_DESCRIPTION"
            mNotificationManager.createNotificationChannel(channel)
        }

        notifBuilder = NotificationCompat.Builder(this, "ChannelId")

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, 0)
        val notification: Notification = notifBuilder
            .setContentTitle("Uploading file(s)")
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setContentIntent(pendingIntent)
            .build()
        startForeground(NotificationId, notification)
    }

    private fun handleUpload() {
        putForegroundNotification()

        sendResult("Authenticating")
        b2Auth()

        bucketId = getBucketId(bucketName)

        var allSuccess = true;
        for (file in fileUrls) {
            fileUrl = file;
            getFileName()
            calculateHash()

            var success = false
            for (i in 1..5) {
                sendResult("Uploading: $fileName try $i/5")

                if (uploadUrl == null) {
                    if (b2GetUploadUrl()) success = b2UploadFile()
                } else
                    success = b2UploadFile()

                if (success) break
            }
            if (!success) {
                sendResult("Failed: $fileName")
                allSuccess = false;
                break;
            }
        }

        if (allSuccess)
            sendResult("Done")
    }

    private fun sendResult(message: String) {
        val bundle = Bundle()
        bundle.putString(ResultStatusKey, message )
        resultReceiver.send(ResultCode, bundle)

        notifBuilder.setContentTitle(message);
        val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NotificationId, notifBuilder.build());

    }

    private fun getFileName() {
        val cursor: Cursor? = contentResolver.query( fileUrl, null, null, null, null, null)

        cursor?.use {
            // moveToFirst() returns false if the cursor has 0 rows.  Very handy for
            // "if there's anything to look at, look at it" conditionals.
            if (it.moveToFirst()) {

                // Note it's called "Display Name".  This is
                // provider-specific, and might not necessarily be the file name.
                val displayName: String =
                    it.getString(it.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                fileName = displayName;
            }
        }
    }

    private fun calculateHash() {
        fileSize = 0;

        BufferedInputStream(getContentResolver().openInputStream(fileUrl)).use {
            val messageDigest = MessageDigest.getInstance("SHA1")
            val buffer = ByteArray(1024)

            var read = 0
            while ( true ) {
                read = it.read(buffer)

                if (read != -1) fileSize += read
                else break

                messageDigest.update(buffer, 0, read)
            }

            val formatter : Formatter = Formatter()
            for (b in messageDigest.digest()) {
                formatter.format("%02x", b)
            }
            fileHash = formatter.toString()
        }

    }

    fun b2Auth() {
        val context = this.applicationContext;
        val url = URL("https://api.backblazeb2.com/b2api/v2/b2_authorize_account")
        val urlConnection = url.openConnection() as HttpURLConnection
        val headerForAuthorizeAccount = "Basic " + Base64.encodeToString( (appKeyId + ":" + appKey).toByteArray(), Base64.NO_WRAP );
        urlConnection.setRequestProperty("Authorization", headerForAuthorizeAccount)

        try {
            urlConnection.inputStream.use { inputStream ->
                val respStr = ReadInputStream(inputStream)
                val response = JSONObject(respStr)
                authToken = response.getString("authorizationToken")
                apiUrl = response.getString("apiUrl")
                accountId = response.getString("accountId")
            }
        } finally {
            urlConnection.disconnect()
        }
    }

    fun b2GetUploadUrl() : Boolean {
        val postParams = "{\"bucketId\":\"$bucketId\"}"
        val postData = postParams.toByteArray(StandardCharsets.UTF_8)

        val url = URL(apiUrl + "/b2api/v2/b2_get_upload_url")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Authorization", authToken!!)
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("charset", "utf-8");
        conn.setRequestProperty("Content-Length", postData.size.toString());
        conn.doOutput = true

        try {
            // ------------------------------------
            BufferedOutputStream(conn.outputStream).use {
                it.write(postData)
                it.flush()
            }

            conn.inputStream.use {
                val respStr = ReadInputStream(it);
                val response = JSONObject(respStr);
                uploadUrl = response.getString("uploadUrl")
                uploadToken = response.getString("authorizationToken")

                if (conn.responseCode < 200 || conn.responseCode > 299)
                    throw Exception("Error getting file url");
            }
        } finally {
            conn.disconnect()
        }

        return true;
    }

    fun getBucketId(name: String) : String {

        val postJson = JSONObject()
        postJson.put("accountId", accountId)
        postJson.put("bucketName", name);
        var postDataBytes =  postJson.toString().toByteArray();

        var connection: HttpURLConnection? = null
        try {
            val url = URL("$apiUrl/b2api/v2/b2_list_buckets")
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection!!.setRequestProperty("Authorization", authToken)
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            //connection.setRequestProperty("charset", "utf-8")
            connection.setRequestProperty("Content-Length", postDataBytes.size.toString())
            connection.doOutput = true

            connection.outputStream.write(postDataBytes)

            val jsonResponse: String = ReadInputStream(connection.inputStream)
            val json = JSONObject(jsonResponse)
            val bucket =  json.getJSONArray("buckets").get(0) as JSONObject;
            return bucket.getString("bucketId")
        } catch (e: Exception) {
            e.printStackTrace()
            val jsonResponse: String = ReadInputStream(connection!!.errorStream)
            println(jsonResponse)
            return ""
        } finally {
            connection!!.disconnect()
        }
    }

    fun b2UploadFile() : Boolean {

        var basePath = File("/")
        var folder  = File(basePath, if (filePath?.isNullOrBlank()) "/mobile-uploads" else filePath)
        var fileInFolder = File(folder, fileName)

        val url = URL(uploadUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST";
        conn.setRequestProperty("Authorization", uploadToken!!)
        conn.setRequestProperty("Content-Type", "b2/auto")
        conn.setRequestProperty("Content-Length", fileSize.toString())
        conn.setRequestProperty("X-Bz-File-Name", URLEncoder.encode( fileInFolder.path.substring(1), "utf-8"))
        conn.setRequestProperty("X-Bz-Content-Sha1", fileHash)
        conn.doOutput = true
        try {
            BufferedInputStream(getContentResolver().openInputStream(fileUrl)).use { bis ->
                BufferedOutputStream(conn.outputStream).use { bos ->
                    bis.copyTo(bos, DEFAULT_BUFFER_SIZE)
                }
            }
            if (conn.responseCode < 200 || conn.responseCode > 299){
                conn.errorStream.use { inputStream ->
                    val respStr = ReadInputStream(inputStream);
                    val response = JSONObject(respStr);
                }
                throw Exception("Error uploading file");
            } else {
                conn.inputStream.use { inputStream ->
                    val respStr = ReadInputStream(inputStream);
                    val response = JSONObject(respStr);
                }
            }

        } catch (e: Exception) {
            uploadUrl = null
            uploadToken = null
            return false
        }
        finally {
            conn.disconnect()
        }
        return true;
    }


    @Throws(IOException::class)
    fun ReadInputStream(inputStream: InputStream): String {

        val stringBuilder = StringBuilder()
        var line: String? = null

        BufferedReader(InputStreamReader(inputStream, Charset.forName("utf-8"))).use({
            while ( true ) {
                line = it.readLine();
                if (line == null) break;
                stringBuilder.append(line)
            }
        })

        return stringBuilder.toString()
    }

    companion object {

        const val ACTION_UPLOAD = "com.gyozo.backblazeb2.action.Upload"

        const val FileUrl = "com.gyozo.backblazeb2.extra.FileUrl"
        const val AppKeyId = "com.gyozo.backblazeb2.extra.AppKeyId"
        const val AppKey = "com.gyozo.backblazeb2.extra.AppKey"
        const val BucketNameKey = "com.gyozo.backblazeb2.extra.BucketNameKey"
        const val FilePath = "com.gyozo.backblazeb2.extra.FilePath"

        const val ResultCode = 1
        const val ResultStatusKey = "status"
        const val ReceiverKey = "com.gyozo.backblazeb2.extra.ReceiverKey,receiver"

        const val NotificationId = 1

        fun StartService() {

        }

    }
}
