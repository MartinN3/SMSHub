package com.ar.smshub

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log
import khttp.responses.Response
import org.jetbrains.anko.doAsync

class SMSDeliveryIntent : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        var mainActivity: MainActivity = context as MainActivity

        var status: String
        var statusDesc: String = "UNKNOWN_ERROR"

        /*mainActivity.runOnUiThread(Runnable {
            mainActivity.logMain("on Delivery ! resultCode $resultCode", newline = true);
        })
        mainActivity.runOnUiThread(Runnable {
            mainActivity.logMain("on Delivery ! resultData $resultData", newline = true);
        })*/


        when (resultCode) {
            Activity.RESULT_OK -> {
                status = "DELIVERED"
                statusDesc = "OK"
            }
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                status = "FAILED"
                statusDesc = "ERROR_GENERIC_FAILURE"
            }
            SmsManager.RESULT_ERROR_NO_SERVICE -> {
                status = "FAILED"
                statusDesc = "ERROR_NO_SERVICE"
            }
            SmsManager.RESULT_ERROR_NULL_PDU -> {
                status = "FAILED"
                statusDesc = "ERROR_NULL_PDU"
            }
            SmsManager.RESULT_ERROR_RADIO_OFF -> {
                status = "FAILED"
                statusDesc = "ERROR_RADIO_OFF"
            }
            else -> {
                status = "FAILED"
            }
        }



        // var statusUrl = intent!!.getStringExtra("statusURL") // ignore but rely on current settings
        var deviceId = intent!!.getStringExtra("deviceId")
        val messageId = intent.getStringExtra("messageId")
        val destMsisdn = intent.getStringExtra("destMsisdn")

        val send_sms_request = mainActivity.pendingSendSMS[messageId];

        if (send_sms_request == null){
            Log.d("send-multipart", "Ignore send intent with messageId : $messageId")
            return
        }

        send_sms_request.sendPartsDeliveryStatus.add(status)

        send_sms_request.sendPartsDeliveryRemaining--
        if (send_sms_request.sendPartsDeliveryRemaining > 0) {
            Log.d("send-multipart", "Got pdu status=" + status + ", statusDesc=" + statusDesc + ", messageId=" + messageId + ", destMsisdn=" + destMsisdn + ", waiting for remaining: " + send_sms_request.sendPartsRemaining)
            return;
        }

        Log.d("----->", "async status_api attempt messageId=" + messageId + ", status=" + status + ", deviceId=" + deviceId)

        // check that ALL PDUs were successfully sent ...
        var mpStatus = "DELIVERED"
        for (pstatus in send_sms_request.sendPartsDeliveryStatus) {
            if (pstatus != "DELIVERED") {
                mpStatus = "FAILED"
                break
            }
        }

        doAsync {
            lateinit var res: Response
            while (true) {
                try {
                    val statusUrl = mainActivity.settingsManager.statusURL
                    Log.d("-->", "Attempting status_api post: url=" + statusUrl)
                    res = khttp.post(
                        url = statusUrl,
                        data = mapOf(
                            "deviceId" to deviceId,
                            "messageId" to messageId,
                            "destMsisdn" to destMsisdn,
                            "status" to mpStatus,
                            "action" to "STATUS_UPDATE"
                        )
                    )
                    Log.d("----->", res.text)
                    send_sms_request.deliveryStillPending = false
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