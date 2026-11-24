package com.agriturismo.santavittoria

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.telephony.TelephonyManager
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.net.URLEncoder

class MissedCallReceiver : BroadcastReceiver() {

    companion object {
        private var wasRinging = false
        private var lastCallerNumber: String? = null
        private var lastCheckTimestamp = 0L
        private var overlayView: TextView? = null
        private var windowManager: WindowManager? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        if (action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

            if (!incomingNumber.isNullOrBlank()) {
                lastCallerNumber = incomingNumber
            }

            when (state) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    wasRinging = true
                    mostraNomeAttivitaSuChiamata(context, incomingNumber)
                }
                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    wasRinging = false
                    nascondiNomeAttivitaSuChiamata()
                }
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    nascondiNomeAttivitaSuChiamata()
                    if (wasRinging && !lastCallerNumber.isNullOrBlank()) {
                        wasRinging = false
                        val number = lastCallerNumber!!.replace("[^0-9+]".toRegex(), "")
                        val giaCliente = BotLogic.isClienteGiaMemorizzato(context, number)
                        BotLogic.aggiungiLog(context, "📞 CHIAMATA PERSA", "Chiamata senza risposta da $number")
                        if (BotLogic.isAutoChiamataPersaWaAttivo(context)) {
                            val message = if (BotLogic.isSiamoPieni(context)) {
                                BotLogic.getMessaggioSiamoPieni(context)
                            } else {
                                BotLogic.getBenvenutoMenu(eraIrraggiungibile = false, giaCliente = giaCliente)
                            }
                            openWhatsAppBusinessWithMessage(context, number, message)
                        }
                    }
                }
            }
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastCheckTimestamp < 4000L) return
        lastCheckTimestamp = now
        controllaChiamateNonRaggiunte(context)
    }

    private fun mostraNomeAttivitaSuChiamata(context: Context, incomingNumber: String?) {
        val labelChiamata = if (!incomingNumber.isNullOrBlank()) {
            "📞 ${BotLogic.NOME_ATTIVITA}\nChiamata in arrivo da: $incomingNumber"
        } else {
            "📞 ${BotLogic.NOME_ATTIVITA}\nChiamata in arrivo"
        }

        try {
            Toast.makeText(context, "${BotLogic.NOME_ATTIVITA} - Chiamata in arrivo", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}

        mostraNotificaChiamataAttivita(context, labelChiamata)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(context)) {
            return
        }

        try {
            Handler(Looper.getMainLooper()).post {
                nascondiNomeAttivitaSuChiamata()
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post
                val tv = TextView(context).apply {
                    text = labelChiamata
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.parseColor("#064E3B"))
                    textSize = 15f
                    setPadding(36, 24, 36, 24)
                }
                val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    layoutFlag,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    y = 120
                }
                wm.addView(tv, params)
                windowManager = wm
                overlayView = tv
            }
        } catch (_: Exception) {}
    }

    private fun nascondiNomeAttivitaSuChiamata() {
        try {
            Handler(Looper.getMainLooper()).post {
                val v = overlayView
                val wm = windowManager
                if (v != null && wm != null) {
                    try {
                        wm.removeView(v)
                    } catch (_: Exception) {}
                }
                overlayView = null
                windowManager = null
            }
        } catch (_: Exception) {}
    }

    private fun mostraNotificaChiamataAttivita(context: Context, testo: String) {
        try {
            val channelId = "chiamate_santa_vittoria"
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    BotLogic.NOME_ATTIVITA,
                    NotificationManager.IMPORTANCE_HIGH
                )
                nm.createNotificationChannel(channel)
            }
            val notif = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.sym_call_incoming)
                .setContentTitle(BotLogic.NOME_ATTIVITA)
                .setContentText(testo.replace("\n", " • "))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            nm.notify(901, notif)
        } catch (_: Exception) {}
    }

    private fun controllaChiamateNonRaggiunte(context: Context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        try {
            val since = System.currentTimeMillis() - (15 * 60 * 1000L)
            val projection = arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE
            )
            val selection = "${CallLog.Calls.DATE} > ? AND (${CallLog.Calls.TYPE} = ? OR ${CallLog.Calls.TYPE} = ?)"
            val args = arrayOf(
                since.toString(),
                CallLog.Calls.MISSED_TYPE.toString(),
                CallLog.Calls.VOICEMAIL_TYPE.toString()
            )
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                selection,
                args,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val num = cursor.getString(0) ?: return
                    BotLogic.registraNumeroIrraggiungibile(context, num)
                }
            }
        } catch (_: Exception) {}
    }

    private fun openWhatsAppBusinessWithMessage(context: Context, phone: String, text: String) {
        try {
            val cleanPhone = when {
                phone.startsWith("+") -> phone.substring(1)
                phone.startsWith("39") -> phone
                else -> "39$phone"
            }
            val url = "https://api.whatsapp.com/send?phone=$cleanPhone&text=${URLEncoder.encode(text, "UTF-8")}"
            val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                setPackage("com.whatsapp.w4b")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(waIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
