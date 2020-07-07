package com.ar.smshub

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.Activity
import android.os.AsyncTask
import android.util.Log
import khttp.responses.Response
import org.jetbrains.anko.doAsync


class SMSSendIntent : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        var mainActivity: MainActivity = context as MainActivity

        var status: String
        var statusCode: Int

        var delivered = intent!!.getIntExtra("delivered", 0)
        if (delivered == 1) {
            status = "DELIVERED"
            statusCode = 1
        } else {
            statusCode = resultCode
            when (resultCode) {
                Activity.RESULT_OK -> {
                    status = "SENT"
                }
                else -> {
                    status = "FAILED"
                }
            }
        }

        // var statusUrl = intent!!.getStringExtra("statusURL") // ignore but rely on current settings
        var deviceId = intent!!.getStringExtra("deviceId")
        var messageId = intent!!.getStringExtra("messageId")
        var destMsisdn = intent!!.getStringExtra("destMsisdn")

        Log.d("----->", "async->" + messageId + "-" + status + "-sucker" + deviceId)


        doAsync {
            lateinit var res: Response
            while (true) {
                try {
                    val statusUrl = mainActivity.settingsManager.statusURL
                    Log.d("-->", "Post status to " + statusUrl)
                    res = khttp.post(
                        url = statusUrl,
                        data = mapOf(
                            "deviceId" to deviceId,
                            "messageId" to messageId,
                            "destMsisdn" to destMsisdn,
                            "status" to status,
                            "statusCode" to statusCode,
                            "action" to "STATUS_UPDATE"
                        )
                    )
                    Log.d("----->", res.text)
                    mainActivity.sendStillPending = false
                    mainActivity.runOnUiThread(Runnable {
                        mainActivity.logMain(
                            "OK status_api post: to=" + destMsisdn + ", id=" + messageId + ", status=" + status,
                            true
                        )
                    })
                    break;
                } catch (e: Exception) {
                    Log.d("-->", "Post status error: " + e.toString())
                    mainActivity.runOnUiThread(Runnable {
                        mainActivity.logMain(
                            "ERR status_api post: to=" + destMsisdn + ", id=" + messageId + ", status=" + status +" ... retrying",
                            true
                        )
                    })
                    Thread.sleep(4000)
                }
            }
        }
    }
}