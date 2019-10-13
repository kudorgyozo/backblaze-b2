package com.gyozo.backblazeb2

import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.JsonRequest
import org.json.JSONObject

class PostFileRequest(
    method: Int,
    url: String?,
    requestBody: String?,
    listener: Response.Listener<JSONObject>?,
    errorListener: Response.ErrorListener?
) : JsonRequest<JSONObject>(method, url, requestBody, listener, errorListener) {


    override fun parseNetworkResponse(response: NetworkResponse?): Response<JSONObject> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}