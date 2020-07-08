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

class SMS(val message: String, val number: String, val messageId: String)

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
        var sms: SMS? = SMS("", "", "")
        var canSend: Boolean = false
        try {
            sms = Klaxon().parse<SMS>(apiResponse.text)
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
        if (canSend) {

            // mutex, flag to limit sending out to just one message unit at a time ...
            // this flag will be cleared by SMSSendIntent later as soon as the delivery/sent status intent is received
            mainActivity.sendStillPending = true


            // refactored based on: https://stackoverflow.com/questions/16643391/how-to-check-for-successful-multi-part-sms-send

            val smsManager = SmsManager.getDefault() as SmsManager

            val messageParts = smsManager.divideMessage(sms!!.message)
            val pendingIntents = ArrayList<PendingIntent>(messageParts.size)

            mainActivity.sendPartsRemaining = messageParts.size
            mainActivity.sendPartsStatus.clear()

            for (i in 0 until messageParts.size) {
                val sentIn = Intent(mainActivity.SENT_SMS_FLAG)
                settings.updateSettings()
                sentIn.putExtra("messageId", sms!!.messageId)
                sentIn.putExtra("statusURL", settings.statusURL)
                sentIn.putExtra("deviceId", settings.deviceId)
                sentIn.putExtra("destMsisdn", sms!!.number)
                val sentPIn = PendingIntent.getBroadcast(mainActivity, mainActivity.nextRequestCode(), sentIn,0)
                pendingIntents.add(sentPIn)
            }
            smsManager.sendMultipartTextMessage(
                sms!!.number,
                null,
                messageParts,
                pendingIntents,
                null
            )
            mainActivity.runOnUiThread(Runnable {
                mainActivity.logMain(
                    "OK sms send attempt: parts=" + messageParts.size + ", to=" + sms!!.number + ", id=" + sms!!.messageId,
                    true
                )
            })
            Log.d("-->", "Sent!")

        }


    }

}
