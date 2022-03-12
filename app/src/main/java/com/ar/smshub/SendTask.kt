package com.ar.smshub

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log
import com.beust.klaxon.Klaxon
import java.util.*
import khttp.responses.Response

class SMS(val message: String, val number: String, val messageId: String){
    var sendStillPending = false
    var deliveryStillPending = false
    var sendPartsRemaining = 0
    var sendPartsStatus = ArrayList<String>()
    var sendPartsDeliveryRemaining = 0
    var sendPartsDeliveryStatus = ArrayList<String>()
    
    fun send(mainActivity: MainActivity, settings: SettingsManager){
        Log.d("sms", "add pendding SMS $this with id ${this.messageId}")
        mainActivity.pendingSendSMS[this.messageId] = this;
        // mutex, flag to limit sending out to just one message unit at a time ...
        // this flag will be cleared by SMSSendIntent later as soon as the delivery/sent status intent is received
        this.sendStillPending = true
        this.deliveryStillPending = true


        // refactored based on: https://stackoverflow.com/questions/16643391/how-to-check-for-successful-multi-part-sms-send

        val smsManager = SmsManager.getDefault() as SmsManager

        val messageParts = smsManager.divideMessage(this.message)
        val pendingIntents = ArrayList<PendingIntent>(messageParts.size)
        val deliveryIntents = ArrayList<PendingIntent>(messageParts.size)

        this.sendPartsRemaining = messageParts.size
        this.sendPartsDeliveryRemaining = messageParts.size

        mainActivity.runOnUiThread(Runnable {
            mainActivity.logMain("Send multi part SMS id = $messageId", true)
        })


        for (i in 0 until messageParts.size) {
            val sentIn = Intent(mainActivity.SENT_SMS_FLAG)
            settings.updateSettings()
            sentIn.putExtra("messageId", this.messageId)
            Log.d("sms", "add extra messageID to : ${this.messageId}")
            sentIn.putExtra("statusURL", settings.statusURL)
            sentIn.putExtra("deviceId", settings.deviceId)
            sentIn.putExtra("destMsisdn", this.number)
            val sentPIn = PendingIntent.getBroadcast(mainActivity, mainActivity.nextRequestCode(), sentIn,0)
            pendingIntents.add(sentPIn)

            val delivInt = Intent(mainActivity.DELIVER_SMS_FLAG)
            delivInt.putExtra("messageId", this.messageId)
            delivInt.putExtra("statusURL", settings.statusURL)
            delivInt.putExtra("deviceId", settings.deviceId)
            delivInt.putExtra("destMsisdn", this.number)
            val delivPIn = PendingIntent.getBroadcast(mainActivity, mainActivity.nextRequestCode(), delivInt,0)
            deliveryIntents.add(delivPIn)
        }
        smsManager.sendMultipartTextMessage(
            this.number,
            null,
            messageParts,
            pendingIntents,
            deliveryIntents
        )
        mainActivity.runOnUiThread(Runnable {
            mainActivity.logMain(
                "OK sms send attempt: parts=" + messageParts.size + ", to=" + this.number + ", id=" + this.messageId,
                true
            )
        })
        Log.d("-->", "Sent!")
    }
}

class SendTask constructor(_settings: SettingsManager, _context: Context) : TimerTask() {
    var settings = _settings
    var mainActivity: MainActivity = _context as MainActivity

    override fun run() {
        if (mainActivity.sendStillPending) {
            Log.d("-->", "SendTask.run deferred, waiting sendStillPending")
            return
        }
        lateinit var apiResponse : Response
        try {
            apiResponse = khttp.post(
                url = settings.sendURL,
                data = mapOf(
                    "deviceId" to settings.deviceId,
                    "action" to "SEND"
                )
            )
        } catch (e: Exception) {
            Log.e("-->", "Cannot connect to URL: " + e.message)
            mainActivity.runOnUiThread(Runnable {
                mainActivity.logMain("E", false)
            })
            return
        }
        var smsList: List<SMS>? = null;
        var canSend: Boolean = false
        try {
            smsList = Klaxon().parseArray(apiResponse.text)
            Log.d("send", "smsList = $smsList")
            canSend = true
        } catch (e: com.beust.klaxon.KlaxonException) {
            // NOTE: The http response body MUST be an empty string
            if (apiResponse.text == "") {
                mainActivity.runOnUiThread(Runnable {
                    mainActivity.logMain(".", false)
                })
                Log.d("-->", "Nothing")
            } else {
                mainActivity.runOnUiThread(Runnable {
                    mainActivity.logMain("ERR send_api: " + apiResponse.text)
                })
                Log.e("error", "Error while parsing send_api response" + apiResponse.text)
            }
        } finally {
            // optional finally block
        }
        if (canSend && smsList != null) {
            for(sms in smsList) {
                sms.send(mainActivity, settings);
            }
        }


    }

}
