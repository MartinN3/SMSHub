package com.ar.smshub

import android.os.AsyncTask
import android.util.Log
import khttp.responses.Response

class PostReceivedMessage : AsyncTask<String, Void, String>() {

    override fun doInBackground(vararg params: String): String {
        var receiveURL = params[0]
        var deviceId = params[1]
        var smsBody = params[2]
        var smsSender = params[3]

        // TODO: refactor this to send from a queue ...
        lateinit var apiResponse : Response
        try {
            apiResponse = khttp.post(
                url = receiveURL,
                data = mapOf("deviceId" to deviceId, "message" to smsBody, "number" to smsSender, "action" to "RECEIVED")
            )
            Log.d("-->", "OK receive_api: from=" + smsSender)
            return "great!"
        } catch (e: Exception) {
            Log.d("-->", "ERR receive_api post: " + e.message)
            return "error"
        }

    }

    override fun onPostExecute(result: String) {

    }

    override fun onPreExecute() {}

    override fun onProgressUpdate(vararg values: Void) {}
}