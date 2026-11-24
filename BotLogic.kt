package com.agriturismo.santavittoria

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.TreeMap
import java.util.concurrent.ConcurrentHashMap

object BotLogic {

    const val NOME_ATTIVITA = "Agriturismo Villaggio Santa Vittoria"
    const val NOME_APP = "Segreteria Agriturismo Villaggio Santa Vittoria"

    private const val PREFS_NAME = "AgriturismoVillaggioSantaVittoriaPrefs"
    private const val KEY_SIAMO_PIENI = "siamo_pieni"
    private const val KEY_MSG_PIENI = "msg_pieni"
    private const val KEY_MSG_PRESA_IN_CARICO = "msg_presa_in_carico"
    private const val KEY_LOG_EVENTI = "log_eventi"
    private const val KEY_AVVISI_DISDETTE = "avvisi_disdette_recenti"
    private const val KEY_PENDING_UNREACHABLE = "pending_unreachable_numbers"
    private const val KEY_CLIENTI_RUBRICA = "clienti_rubrica_map"
    private const val KEY_LAST_MSG_TIME = "last_msg_time_map"
    private const val KEY_SALVA_RUBRICA = "salva_cliente_rubrica"
    private const val KEY_SOGLIA_ORE_CHAT = "soglia_ore_chat_esistente"
    private const val KEY_OFFERTA_TESTO = "offerta_massiva_testo"
    private const val KEY_OFFERTA_IMG_PATH = "offerta_massiva_img_path"
    private const val KEY_BROADCAST_INDEX = "broadcast_index"
    private const val KEY_PRENOTAZIONI_TELEFONICHE = "prenotazioni_telefoniche_manuali"
    private const val KEY_RICHIESTE_IN_ATTESA = "richieste_prenotazione_in_attesa"
    private const val KEY_FLUSSO_VELOCE = "flusso_prenotazione_veloce"
    private const val KEY_AUTO_CHIAMATA_PERSA_WA = "auto_chiamata_persa_wa"
    private const val GOOGLE_MAPS_LINK = "https://www.google.com/maps/search/?api=1&query=Agriturismo+Villaggio+Santa+Vittoria+Serri"

    enum class Step {
        MENU,
        PIENO_SCELTA_SETTIMANE,
        PRENOTA_PERSONE,
        PRENOTA_BAMBINI,
        PRENOTA_INTOLLERANZE,
        PRENOTA_DOMENICA,
        PRENOTA_NOME_COGNOME,
        PRENOTA_TELEFONO,
        PRENOTA_UNICO_MESSAGGIO,
        DISDETTA_CHI,
        OPERATORE_ATTIVO,
        DONE
    }

    data class BookingDraft(
        var persone: String = "",
        var bambini: String = "",
        var intolleranze: String = "",
        var domenica: String = "",
        var nomeCognome: String = "",
        var telefono: String = ""
    )

    data class PendingBookingRequest(
        val id: String,
        val senderKey: String,
        val nomeCognome: String,
        val telefono: String,
        val persone: String,
        val bambini: String,
        val intolleranze: String,
        val domenica: String
    )

    data class Session(
        var step: Step = Step.MENU,
        val draft: BookingDraft = BookingDraft()
    )

    data class ClienteDomenica(
        val nomeCognome: String,
        val persone: String,
        val telefono: String,
        val bambini: String,
        val intolleranze: String,
        val oraEvento: String,
        val origine: String = "WhatsApp / Calendar"
    )

    private val sessions = ConcurrentHashMap<String, Session>()

    fun isSiamoPieni(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SIAMO_PIENI, false)
    }

    fun setSiamoPieni(context: Context, attivo: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SIAMO_PIENI, attivo).apply()
        SiamoPieniWidget.updateAllWidgets(context)
    }

    fun getMessaggioPresaInCarico(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(
            KEY_MSG_PRESA_IN_CARICO,
            "Abbiamo preso in carico la prenotazione, sarà nostra cura risponderle al più presto."
        ) ?: "Abbiamo preso in carico la prenotazione, sarà nostra cura risponderle al più presto."
    }

    fun setMessaggioPresaInCarico(context: Context, msg: String) {
        val clean = msg.trim().ifEmpty {
            "Abbiamo preso in carico la prenotazione, sarà nostra cura risponderle al più presto."
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_MSG_PRESA_IN_CARICO, clean).apply()
    }

    fun registraAvvisoDisdetta(context: Context, chiText: String, senderKey: String, rimossiCalendar: Int) {
        val ora = SimpleDateFormat("dd/MM HH:mm", Locale.ITALY).format(Date())
        val calInfo = if (rimossiCalendar > 0) "rimossa da Google Calendar" else "annotata in calendario"
        val messaggioAvviso = "⚠️ $ora — '$chiText' ($senderKey) ha disdetto ($calInfo)."

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY_AVVISI_DISDETTE, "[]"))
        val nuovo = JSONArray()
        nuovo.put(messaggioAvviso)
        for (i in 0 until minOf(arr.length(), 14)) {
            nuovo.put(arr.getString(i))
        }
        prefs.edit().putString(KEY_AVVISI_DISDETTE, nuovo.toString()).apply()

        mostraNotificaDisdetta(context, chiText, messaggioAvviso)
    }

    fun hasAvvisiDisdette(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY_AVVISI_DISDETTE, "[]"))
        return arr.length() > 0
    }

    fun getAvvisiDisdetteTestuali(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY_AVVISI_DISDETTE, "[]"))
        if (arr.length() == 0) return ""
        val sb = StringBuilder()
        for (i in 0 until arr.length()) {
            sb.append(arr.optString(i)).append("\n")
        }
        return sb.toString().trim()
    }

    fun azzeraAvvisiDisdette(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_AVVISI_DISDETTE, "[]").apply()
    }

    private fun mostraNotificaDisdetta(context: Context, chiText: String, messaggioDettaglio: String) {
        try {
            val channelId = "avvisi_disdette_santa_vittoria"
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Avvisi Disdette $NOME_ATTIVITA",
                    NotificationManager.IMPORTANCE_HIGH
                )
                nm.createNotificationChannel(channel)
            }
            val notif = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("⚠️ Disdetta cliente: $chiText")
                .setContentText(messaggioDettaglio)
                .setStyle(NotificationCompat.BigTextStyle().bigText(messaggioDettaglio))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            nm.notify((System.currentTimeMillis() % 100000).toInt().coerceAtLeast(1000), notif)
        } catch (_: Exception) {}
    }

    fun getRichiesteInAttesa(context: Context): List<PendingBookingRequest> {
        val out = mutableListOf<PendingBookingRequest>()
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val arr = JSONArray(prefs.getString(KEY_RICHIESTE_IN_ATTESA, "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(
                    PendingBookingRequest(
                        id = o.optString("id", ""),
                        senderKey = o.optString("senderKey", ""),
                        nomeCognome = o.optString("nomeCognome", "Cliente"),
                        telefono = o.optString("telefono", "-"),
                        persone = o.optString("persone", "-"),
                        bambini = o.optString("bambini", "No"),
                        intolleranze = o.optString("intolleranze", "No"),
                        domenica = o.optString("domenica", "Questa domenica")
                    )
                )
            }
        } catch (_: Exception) {}
        return out
    }

    private fun salvaRichiestaInAttesaConfermaOperatore(
        context: Context,
        senderKey: String,
        draft: BookingDraft,
        telRiferimento: String
    ) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val arr = JSONArray(prefs.getString(KEY_RICHIESTE_IN_ATTESA, "[]"))
            val obj = JSONObject().apply {
                put("id", System.currentTimeMillis().toString() + "_" + (100..999).random())
                put("senderKey", senderKey)
                put("nomeCognome", draft.nomeCognome)
                put("telefono", telRiferimento)
                put("persone", draft.persone)
                put("bambini", draft.bambini)
                put("intolleranze", draft.intolleranze)
                put("domenica", draft.domenica)
            }
            arr.put(obj)
            prefs.edit().putString(KEY_RICHIESTE_IN_ATTESA, arr.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun scartaRichiestaInAttesa(context: Context, requestId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY_RICHIESTE_IN_ATTESA, "[]"))
        val remaining = JSONArray()
        var rimossoNome = ""
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (rimossoNome.isEmpty() && o.optString("id") == requestId) {
                rimossoNome = o.optString("nomeCognome", "Richiesta")
            } else {
                remaining.put(o)
            }
        }
        prefs.edit().putString(KEY_RICHIESTE_IN_ATTESA, remaining.toString()).apply()
        if (rimossoNome.isNotEmpty()) {
            aggiungiLog(context, "🗑️ SCARTATA", "Richiesta di $rimossoNome rimossa")
        }
    }

    fun confermaPrenotazioneDaOperatore(context: Context, requestId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY_RICHIESTE_IN_ATTESA, "[]"))
        var targetObj: JSONObject? = null
        val remaining = JSONArray()

        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (targetObj == null && o.optString("id") == requestId) {
                targetObj = o
            } else {
                remaining.put(o)
            }
        }

        val reqJson = targetObj ?: return false
        prefs.edit().putString(KEY_RICHIESTE_IN_ATTESA, remaining.toString()).apply()

        val draft = BookingDraft(
            persone = reqJson.optString("persone", "-"),
            bambini = reqJson.optString("bambini", "No"),
            intolleranze = reqJson.optString("intolleranze", "No"),
            domenica = reqJson.optString("domenica", "Questa domenica"),
            nomeCognome = reqJson.optString("nomeCognome", "Cliente"),
            telefono = reqJson.optString("telefono", "-")
        )
        val senderKey = reqJson.optString("senderKey", draft.telefono)

        val okCal = saveBookingToGoogleCalendar(context, senderKey, draft)
        val esitoRubrica = aggiungiClienteAllaRubricaSePrimaPrenotazione(
            context,
            senderKey,
            draft.nomeCognome,
            draft.telefono
        )

        aggiungiLog(
            context,
            "✅ CONFERMATA",
            "${draft.nomeCognome} (${draft.persone} pers., Tel: ${draft.telefono}) • ${draft.domenica} [$esitoRubrica]"
        )
        return okCal
    }

    fun isSalvaInRubricaAttivo(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SALVA_RUBRICA, true)
    }

    fun setSalvaInRubricaAttivo(context: Context, attivo: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SALVA_RUBRICA, attivo).apply()
    }

    fun isFlussoVeloceAttivo(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_FLUSSO_VELOCE, true)
    }

    fun setFlussoVeloceAttivo(context: Context, attivo: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_FLUSSO_VELOCE, attivo).apply()
    }

    fun isAutoChiamataPersaWaAttivo(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_CHIAMATA_PERSA_WA, true)
    }

    fun setAutoChiamataPersaWaAttivo(context: Context, attivo: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTO_CHIAMATA_PERSA_WA, attivo).apply()
    }

    fun getSogliaOreChatEsistente(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_SOGLIA_ORE_CHAT, 24)
    }

    fun setSogliaOreChatEsistente(context: Context, ore: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_SOGLIA_ORE_CHAT, ore.coerceAtLeast(1)).apply()
    }

    fun getMessaggioSiamoPieni(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(
            KEY_MSG_PIENI,
            "Salve, grazie di aver contattato $NOME_ATTIVITA! Al momento siamo al completo per la prossima domenica, ma può comunque prenotare per le domeniche delle settimane a seguire.\n\n" +
            "Risponda con un numero:\n" +
            "1 - Per prenotare nelle settimane a seguire\n" +
            "2 - Per disdire una prenotazione\n" +
            "3 - Per parlare con un operatore"
        ) ?: ""
    }

    fun setMessaggioSiamoPieni(context: Context, msg: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_MSG_PIENI, msg).apply()
    }

    fun getTestoOffertaMassiva(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(
            KEY_OFFERTA_TESTO,
            "🌿 Offerta speciale da $NOME_ATTIVITA!\nVi aspettiamo domenica con pranzo servito dalle 13:15. Per prenotare risponda a questo messaggio!"
        ) ?: ""
    }

    fun setTestoOffertaMassiva(context: Context, testo: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_OFFERTA_TESTO, testo).apply()
    }

    fun getImmagineOffertaPath(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val path = prefs.getString(KEY_OFFERTA_IMG_PATH, null)
        return if (path != null && File(path).exists()) path else null
    }

    fun copiaImmagineOffertaInCache(context: Context, sourceUri: Uri): String? {
        return try {
            val dir = File(context.cacheDir, "offerte").apply { mkdirs() }
            val dest = File(dir, "offerta_corrente.jpg")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
            if (dest.exists() && dest.length() > 0L) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putString(KEY_OFFERTA_IMG_PATH, dest.absolutePath).apply()
                dest.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun rimuoviImmagineOfferta(context: Context) {
        getImmagineOffertaPath(context)?.let { path ->
            try { File(path).delete() } catch (_: Exception) {}
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_OFFERTA_IMG_PATH).apply()
    }

    fun aggiungiPrenotazioneTelefonica(
        context: Context,
        nomeCognome: String,
        persone: String,
        telefono: String,
        domenicaInput: String,
        bambini: String = "No",
        intolleranze: String = "No"
    ): Boolean {
        val cleanNome = nomeCognome.trim()
        val cleanPersone = persone.trim().ifEmpty { "-" }
        val cleanTelefono = telefono.trim().ifEmpty { "-" }
        val cleanDomenica = domenicaInput.trim()
        val cleanBambini = bambini.trim().ifEmpty { "No" }
        val cleanIntoll = intolleranze.trim().ifEmpty { "No" }

        if (cleanNome.isEmpty()) return false

        val cal = parseDomenicaCalendar(cleanDomenica)
        val dateKeyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.ITALY)
        val dateLabelFmt = SimpleDateFormat("EEEE dd/MM/yyyy", Locale.ITALY)
        val sortKey = dateKeyFmt.format(cal.time)
        val giornoFormattato = dateLabelFmt.format(cal.time).replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.ITALY) else it.toString()
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY_PRENOTAZIONI_TELEFONICHE, "[]"))
        val obj = JSONObject().apply {
            put("sortKey", sortKey)
            put("giornoLabel", giornoFormattato)
            put("nomeCognome", cleanNome)
            put("persone", cleanPersone)
            put("telefono", cleanTelefono)
            put("bambini", cleanBambini)
            put("intolleranze", cleanIntoll)
            put("oraEvento", "13:15")
            put("origine", "Telefonica")
        }
        arr.put(obj)
        prefs.edit().putString(KEY_PRENOTAZIONI_TELEFONICHE, arr.toString()).apply()

        if (cleanTelefono.isNotEmpty() && cleanTelefono != "-") {
            aggiungiClienteAllaRubricaSePrimaPrenotazione(context, cleanTelefono, cleanNome, cleanTelefono)
        }

        saveBookingToGoogleCalendar(
            context,
            "Chiamata telefonica",
            BookingDraft(
                persone = cleanPersone,
                bambini = cleanBambini,
                intolleranze = cleanIntoll,
                domenica = if (cleanDomenica.isNotEmpty()) cleanDomenica else giornoFormattato,
                nomeCognome = cleanNome,
                telefono = cleanTelefono
            ),
            cal
        )

        aggiungiLog(
            context,
            "☎️ TELEFONICA",
            "$cleanNome ($cleanPersone pers., Tel: $cleanTelefono) • $giornoFormattato"
        )
        return true
    }

    private fun getPrenotazioniTelefonicheSalvate(context: Context): List<Pair<String, ClienteDomenica>> {
        val out = mutableListOf<Pair<String, ClienteDomenica>>()
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val arr = JSONArray(prefs.getString(KEY_PRENOTAZIONI_TELEFONICHE, "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val sortKey = o.optString("sortKey", "")
                val giornoLabel = o.optString("giornoLabel", "")
                val groupKey = if (sortKey.isNotEmpty()) "$sortKey|$giornoLabel" else "9999-12-31|Domenica da definire"
                val item = ClienteDomenica(
                    nomeCognome = o.optString("nomeCognome", "Cliente"),
                    persone = o.optString("persone", "-"),
                    telefono = o.optString("telefono", "-"),
                    bambini = o.optString("bambini", "No"),
                    intolleranze = o.optString("intolleranze", "No"),
                    oraEvento = o.optString("oraEvento", "13:15"),
                    origine = o.optString("origine", "Telefonica")
                )
                out.add(groupKey to item)
            }
        } catch (_: Exception) {}
        return out
    }

    private fun parseDomenicaCalendar(domenicaStr: String): Calendar {
        val cal = getNextSunday(Calendar.getInstance())
        cal.set(Calendar.HOUR_OF_DAY, 13)
        cal.set(Calendar.MINUTE, 15)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        val dateRegex = Regex("(\\d{1,2})[/\\-](\\d{1,2})")
        dateRegex.find(domenicaStr)?.let {
            val targetDay = it.groupValues[1].toInt()
            val targetMonth = it.groupValues[2].toInt() - 1
            cal.set(Calendar.DAY_OF_MONTH, targetDay)
            cal.set(Calendar.MONTH, targetMonth)
            if (cal.timeInMillis < System.currentTimeMillis() - (24 * 60 * 60 * 1000L)) {
                cal.add(Calendar.YEAR, 1)
            }
        }
        return cal
    }

    fun getPrenotazioniPerDomenicaDaCalendar(context: Context): TreeMap<String, MutableList<ClienteDomenica>> {
        val result = TreeMap<String, MutableList<ClienteDomenica>>()
        try {
            val now = System.currentTimeMillis()
            val startRange = now - (30L * 24 * 60 * 60 * 1000L)
            val endRange = now + (180L * 24 * 60 * 60 * 1000L)

            val projection = arrayOf(
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.DTSTART
            )
            val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
            val args = arrayOf(startRange.toString(), endRange.toString())

            val dateKeyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.ITALY)
            val dateLabelFmt = SimpleDateFormat("EEEE dd/MM/yyyy", Locale.ITALY)
            val timeFmt = SimpleDateFormat("HH:mm", Locale.ITALY)

            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                args,
                "${CalendarContract.Events.DTSTART} ASC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val title = cursor.getString(0) ?: ""
                    val desc = cursor.getString(1) ?: ""
                    val dtStart = cursor.getLong(2)

                    if (title.startsWith("❌")) continue
                    val isPrenotazione = title.contains(NOME_ATTIVITA, ignoreCase = true) ||
                        title.contains("Prenotazione", ignoreCase = true) ||
                        desc.contains("Nome e Cognome:", ignoreCase = true)
                    if (!isPrenotazione) continue

                    val cal = Calendar.getInstance().apply { timeInMillis = dtStart }
                    val sortKey = dateKeyFmt.format(cal.time)
                    val giornoFormattato = dateLabelFmt.format(cal.time).replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(Locale.ITALY) else it.toString()
                    }
                    val groupKey = "$sortKey|$giornoFormattato"

                    val nomeEstratto = extractField(desc, "Nome e Cognome:")
                        ?: extractNomeFromTitle(title)
                        ?: "Cliente"
                    val personeEstratte = extractField(desc, "Persone totali:")
                        ?: extractPersoneFromTitle(title)
                        ?: "-"
                    val telefonoEstratto = extractField(desc, "Telefono lasciato:")
                        ?: extractTelefonoFromTitle(title)
                        ?: extractField(desc, "Contatto WhatsApp:")
                        ?: "-"
                    val bambiniEstratti = extractField(desc, "Bambini 0-10 (pagano la metà):") ?: "-"
                    val intollEstratte = extractField(desc, "Intolleranze:") ?: "-"
                    val origine = if (desc.contains("Chiamata telefonica", ignoreCase = true)) "Telefonica" else "WhatsApp / Calendar"

                    val item = ClienteDomenica(
                        nomeCognome = nomeEstratto,
                        persone = personeEstratte,
                        telefono = telefonoEstratto,
                        bambini = bambiniEstratti,
                        intolleranze = intollEstratte,
                        oraEvento = timeFmt.format(cal.time),
                        origine = origine
                    )
                    result.computeIfAbsent(groupKey) { mutableListOf() }.add(item)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        for ((groupKey, telItem) in getPrenotazioniTelefonicheSalvate(context)) {
            val list = result.computeIfAbsent(groupKey) { mutableListOf() }
            val alreadyPresent = list.any {
                it.nomeCognome.equals(telItem.nomeCognome, ignoreCase = true) &&
                it.telefono.equals(telItem.telefono, ignoreCase = true) &&
                it.persone.equals(telItem.persone, ignoreCase = true)
            }
            if (!alreadyPresent) {
                list.add(telItem)
            }
        }

        return result
    }

    private fun extractField(desc: String, prefix: String): String? {
        for (line in desc.lines()) {
            if (line.startsWith(prefix, ignoreCase = true)) {
                val valStr = line.substring(prefix.length).trim()
                if (valStr.isNotEmpty()) return valStr
            }
        }
        return null
    }

    private fun extractNomeFromTitle(title: String): String? {
        val afterBracket = if (title.contains("]")) title.substringAfter("]").trim() else title
        val beforeParen = afterBracket.substringBefore("(").trim()
        return beforeParen.takeIf { it.isNotBlank() }
    }

    private fun extractPersoneFromTitle(title: String): String? {
        val m = Regex("\\(([^)]+pers\\.?)\\)").find(title)
        return m?.groupValues?.get(1)?.replace("pers.", "", ignoreCase = true)?.trim()
    }

    private fun extractTelefonoFromTitle(title: String): String? {
        val m = Regex("Tel:\\s*([+0-9\\s]+)").find(title)
        return m?.groupValues?.get(1)?.trim()
    }

    fun generaPdfClientiPerDomenica(context: Context): File? {
        val grouped = getPrenotazioniPerDomenicaDaCalendar(context)
        val pdfDocument = PdfDocument()

        val pageWidth = 595
        val pageHeight = 842
        val margin = 36
        var pageNumber = 1

        fun newPage(): Pair<PdfDocument.Page, Canvas> {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            val page = pdfDocument.startPage(pageInfo)
            return page to page.canvas
        }

        var (currentPage, canvas) = newPage()
        var y = 48f

        val titlePaint = Paint().apply {
            color = Color.parseColor("#064E3B")
            textSize = 17f
            isFakeBoldText = true
        }
        val subtitlePaint = Paint().apply {
            color = Color.parseColor("#57534E")
            textSize = 10.5f
        }
        val dateHeaderBgPaint = Paint().apply {
            color = Color.parseColor("#D1FAE5")
        }
        val dateHeaderPaint = Paint().apply {
            color = Color.parseColor("#064E3B")
            textSize = 12.5f
            isFakeBoldText = true
        }
        val colHeaderPaint = Paint().apply {
            color = Color.parseColor("#1C1917")
            textSize = 10.5f
            isFakeBoldText = true
        }
        val rowPaint = Paint().apply {
            color = Color.parseColor("#1C1917")
            textSize = 10.5f
        }
        val detailPaint = Paint().apply {
            color = Color.parseColor("#44403C")
            textSize = 9.5f
        }
        val linePaint = Paint().apply {
            color = Color.parseColor("#D6D3D1")
            strokeWidth = 1f
        }

        fun drawHeader() {
            canvas.drawText(NOME_ATTIVITA, margin.toFloat(), y, titlePaint)
            y += 18f
            val stamp = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY).format(Date())
            canvas.drawText("Elenco clienti per domenica • Generato il $stamp", margin.toFloat(), y, subtitlePaint)
            y += 12f
            canvas.drawLine(margin.toFloat(), y, (pageWidth - margin).toFloat(), y, linePaint)
            y += 18f
        }

        fun ensureSpace(neededHeight: Float) {
            if (y + neededHeight > pageHeight - 40f) {
                pdfDocument.finishPage(currentPage)
                pageNumber += 1
                val next = newPage()
                currentPage = next.first
                canvas = next.second
                y = 48f
                drawHeader()
            }
        }

        drawHeader()

        if (grouped.isEmpty()) {
            canvas.drawText(
                "Nessuna prenotazione confermata trovata per le domeniche.",
                margin.toFloat(),
                y + 10f,
                rowPaint
            )
        } else {
            for ((compoundKey, clienti) in grouped) {
                val etichettaData = compoundKey.substringAfter("|", compoundKey)
                ensureSpace(56f)

                canvas.drawRect(
                    margin.toFloat(),
                    y - 14f,
                    (pageWidth - margin).toFloat(),
                    y + 8f,
                    dateHeaderBgPaint
                )
                canvas.drawText("📅 $etichettaData  —  Totale prenotazioni: ${clienti.size}", (margin + 8).toFloat(), y, dateHeaderPaint)
                y += 22f

                canvas.drawText("Nome e Cognome", (margin + 8).toFloat(), y, colHeaderPaint)
                canvas.drawText("Persone", 315f, y, colHeaderPaint)
                canvas.drawText("Telefono", 405f, y, colHeaderPaint)
                y += 6f
                canvas.drawLine(margin.toFloat(), y, (pageWidth - margin).toFloat(), y, linePaint)
                y += 15f

                for ((idx, cli) in clienti.withIndex()) {
                    ensureSpace(34f)
                    canvas.drawText("${idx + 1}. ${cli.nomeCognome}", (margin + 8).toFloat(), y, rowPaint)
                    canvas.drawText(cli.persone, 315f, y, rowPaint)
                    canvas.drawText(cli.telefono, 405f, y, rowPaint)
                    y += 13f
                    canvas.drawText(
                        "Via: ${cli.origine} | Ora: ${cli.oraEvento} | Bambini 0-10: ${cli.bambini} | Intolleranze: ${cli.intolleranze}",
                        (margin + 20).toFloat(),
                        y,
                        detailPaint
                    )
                    y += 16f
                }
                y += 10f
            }
        }

        pdfDocument.finishPage(currentPage)

        return try {
            val dir = File(context.cacheDir, "pdf").apply { mkdirs() }
            val fileName = "Clienti-Domeniche-Santa-Vittoria.pdf"
            val outFile = File(dir, fileName)
            FileOutputStream(outFile).use { fos ->
                pdfDocument.writeTo(fos)
            }
            pdfDocument.close()
            aggiungiLog(context, "📄 PDF DOMENICHE", "Generato PDF elenco clienti per domenica ($fileName)")
            outFile
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }

    fun getDestinatariMassivi(context: Context): List<Pair<String, String>> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val map = JSONObject(prefs.getString(KEY_CLIENTI_RUBRICA, "{}"))
        val byDigits = linkedMapOf<String, String>()

        val keys = map.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val digits = normalizePhoneDigits(k)
            if (digits.length >= 6) {
                val fullNum = if (digits.startsWith("39")) digits else "39$digits"
                val obj = map.optJSONObject(k)
                val nome = obj?.optString("nome")?.takeIf { it.isNotBlank() } ?: "Cliente"
                if (!byDigits.containsKey(fullNum)) {
                    byDigits[fullNum] = nome
                }
            }
        }
        return byDigits.entries.map { it.key to it.value }
    }

    fun getBroadcastIndex(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_BROADCAST_INDEX, 0)
    }

    fun setBroadcastIndex(context: Context, idx: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_BROADCAST_INDEX, idx.coerceAtLeast(0)).apply()
    }

    private fun normalizePhoneDigits(raw: String): String {
        val d = raw.replace("[^0-9]".toRegex(), "")
        return if (d.length > 10 && d.startsWith("39")) d.substring(2) else d
    }

    fun isClienteGiaMemorizzato(context: Context, senderKey: String): Boolean {
        val digits = normalizePhoneDigits(senderKey)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val map = JSONObject(prefs.getString(KEY_CLIENTI_RUBRICA, "{}"))
        if (digits.isNotEmpty() && map.has(digits)) return true
        if (map.has(senderKey.trim().lowercase(Locale.ITALY))) return true
        return esisteInRubricaTelefono(context, senderKey, digits)
    }

    private fun segnaClienteMemorizzato(context: Context, senderKey: String, phoneInput: String, nomeCognome: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val map = JSONObject(prefs.getString(KEY_CLIENTI_RUBRICA, "{}"))
        val digitsSender = normalizePhoneDigits(senderKey)
        val digitsInput = normalizePhoneDigits(phoneInput)
        val entry = JSONObject().apply {
            put("nome", nomeCognome)
            put("dataRegistrazione", SimpleDateFormat("dd/MM/yyyy", Locale.ITALY).format(Date()))
        }
        if (digitsSender.isNotEmpty()) map.put(digitsSender, entry)
        if (digitsInput.isNotEmpty()) map.put(digitsInput, entry)
        map.put(senderKey.trim().lowercase(Locale.ITALY), entry)
        prefs.edit().putString(KEY_CLIENTI_RUBRICA, map.toString()).apply()
    }

    private fun esisteInRubricaTelefono(context: Context, senderKey: String, digits: String): Boolean {
        try {
            if (digits.length >= 6) {
                val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(digits))
                context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)?.use { c ->
                    if (c.moveToFirst()) return true
                }
            }
            val cleanName = senderKey.trim()
            if (cleanName.isNotEmpty() && digits.isEmpty()) {
                context.contentResolver.query(
                    ContactsContract.Contacts.CONTENT_URI,
                    arrayOf(ContactsContract.Contacts._ID),
                    "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?",
                    arrayOf("%$cleanName%"),
                    null
                )?.use { c ->
                    if (c.moveToFirst()) return true
                }
            }
        } catch (_: Exception) {}
        return false
    }

    private fun aggiungiClienteAllaRubricaSePrimaPrenotazione(
        context: Context,
        senderKey: String,
        nomeCognome: String,
        telefonoInput: String
    ): String {
        if (!isSalvaInRubricaAttivo(context)) {
            segnaClienteMemorizzato(context, senderKey, telefonoInput, nomeCognome)
            return "Salvataggio in rubrica disattivato"
        }
        if (isClienteGiaMemorizzato(context, senderKey) ||
            (normalizePhoneDigits(telefonoInput).length >= 6 && isClienteGiaMemorizzato(context, telefonoInput))) {
            segnaClienteMemorizzato(context, senderKey, telefonoInput, nomeCognome)
            return "Già presente in rubrica"
        }

        val digits = normalizePhoneDigits(telefonoInput).ifEmpty { normalizePhoneDigits(senderKey) }
        val formattedPhone = if (digits.isNotEmpty()) {
            if (digits.startsWith("39")) "+$digits" else "+39$digits"
        } else {
            ""
        }
        val displayName = "${nomeCognome.trim()} (Cliente)"

        return try {
            val rawContactValues = ContentValues().apply {
                put(ContactsContract.RawContacts.ACCOUNT_TYPE, null as String?)
                put(ContactsContract.RawContacts.ACCOUNT_NAME, null as String?)
            }
            val rawContactUri = context.contentResolver.insert(ContactsContract.RawContacts.CONTENT_URI, rawContactValues)
            val rawContactId = rawContactUri?.lastPathSegment?.toLongOrNull()

            if (rawContactId != null) {
                val nameValues = ContentValues().apply {
                    put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, displayName)
                    put(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, nomeCognome.trim())
                }
                context.contentResolver.insert(ContactsContract.Data.CONTENT_URI, nameValues)

                if (formattedPhone.isNotEmpty()) {
                    val phoneValues = ContentValues().apply {
                        put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                        put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                        put(ContactsContract.CommonDataKinds.Phone.NUMBER, formattedPhone)
                        put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                    }
                    context.contentResolver.insert(ContactsContract.Data.CONTENT_URI, phoneValues)
                }

                val noteValues = ContentValues().apply {
                    put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                    put(ContactsContract.CommonDataKinds.Note.NOTE, "Cliente $NOME_ATTIVITA (registrato alla prima prenotazione)")
                }
                context.contentResolver.insert(ContactsContract.Data.CONTENT_URI, noteValues)
            }

            segnaClienteMemorizzato(context, senderKey, telefonoInput, nomeCognome)
            aggiungiLog(context, "📇 NUOVO CLIENTE", "$displayName ($formattedPhone) aggiunto in rubrica")
            "Aggiunto in rubrica"
        } catch (e: Exception) {
            e.printStackTrace()
            segnaClienteMemorizzato(context, senderKey, telefonoInput, nomeCognome)
            "Memorizzato"
        }
    }

    private fun controllaEAggiornaUltimoMessaggio(context: Context, senderKey: String): Boolean {
        val key = senderKey.trim().lowercase(Locale.ITALY)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val map = JSONObject(prefs.getString(KEY_LAST_MSG_TIME, "{}"))
        val now = System.currentTimeMillis()
        val prevTime = map.optLong(key, 0L)
        map.put(key, now)
        prefs.edit().putString(KEY_LAST_MSG_TIME, map.toString()).apply()

        if (prevTime == 0L) return true
        val sogliaMillis = getSogliaOreChatEsistente(context) * 60 * 60 * 1000L
        return (now - prevTime) >= sogliaMillis
    }

    fun registraNumeroIrraggiungibile(context: Context, rawNumber: String) {
        val digits = normalizePhoneDigits(rawNumber)
        if (digits.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY_PENDING_UNREACHABLE, "[]"))
        for (i in 0 until arr.length()) {
            if (arr.optString(i) == digits) return
        }
        arr.put(digits)
        prefs.edit().putString(KEY_PENDING_UNREACHABLE, arr.toString()).apply()
        aggiungiLog(context, "📵 NON RAGGIUNGIBILE", "Chiamata verso $NOME_ATTIVITA mentre non raggiungibile: +$digits")
    }

    fun consumeUnreachableMatch(context: Context, senderKey: String): Boolean {
        val senderDigits = normalizePhoneDigits(senderKey)
        if (senderDigits.length < 6) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY_PENDING_UNREACHABLE, "[]"))
        var found = false
        val remaining = JSONArray()
        for (i in 0 until arr.length()) {
            val saved = arr.optString(i)
            if (!found && (senderDigits.endsWith(saved) || saved.endsWith(senderDigits))) {
                found = true
            } else {
                remaining.put(saved)
            }
        }
        if (found) {
            prefs.edit().putString(KEY_PENDING_UNREACHABLE, remaining.toString()).apply()
        }
        return found
    }

    fun getPendingUnreachableCount(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY_PENDING_UNREACHABLE, "[]"))
        return arr.length()
    }

    fun getBenvenutoMenu(eraIrraggiungibile: Boolean = false, giaCliente: Boolean = false): String {
        val salutoBase = when {
            eraIrraggiungibile && giaCliente ->
                "Salve, bentornato/a! Ci scusiamo se al momento del suo tentativo di chiamata verso $NOME_ATTIVITA il telefono non risultava raggiungibile."
            eraIrraggiungibile ->
                "Salve, ci scusiamo, al momento del suo tentativo di chiamata verso $NOME_ATTIVITA il telefono non risultava raggiungibile."
            giaCliente ->
                "Salve, bentornato/a all'$NOME_ATTIVITA! Grazie di averci ricontattato."
            else ->
                "Salve, grazie di aver contattato $NOME_ATTIVITA!"
        }
        return "$salutoBase\n\n" +
               "Come possiamo aiutarla? Risponda con un numero:\n" +
               "1 - Per prenotare\n" +
               "2 - Per disdire una prenotazione\n" +
               "3 - Per parlare con un operatore"
    }

    private fun isInfoOrariRequest(text: String): Boolean {
        return text.contains("orari", ignoreCase = true) ||
               text.contains("orario", ignoreCase = true) ||
               text.contains("aperto", ignoreCase = true) ||
               text.contains("aperti", ignoreCase = true) ||
               text.contains("quando", ignoreCase = true) ||
               text.contains("che ora", ignoreCase = true) ||
               text.contains("giorni", ignoreCase = true)
    }

    private fun avviaFlussoPrenotazione(context: Context, session: Session, localePieno: Boolean): String {
        val notaPieno = if (localePieno) {
            "ℹ️ La prossima domenica siamo al completo, ma può prenotare per le domeniche delle settimane a seguire!\n"
        } else {
            ""
        }
        val infoRegole = "ℹ️ Aperti solo la domenica, pranzo dalle 13:15.\n" +
                         "⚠️ Non è consentito l'accesso ai cani (nemmeno di piccola taglia).\n" +
                         "👶 Bambini 0-10 anni a metà prezzo.\n\n"

        if (isFlussoVeloceAttivo(context)) {
            session.step = Step.PRENOTA_UNICO_MESSAGGIO
            return notaPieno +
                   infoRegole +
                   "Ci scriva in un unico messaggio:\n" +
                   "• Nome e Cognome\n" +
                   "• Numero di persone (ed eventuali bimbi 0-10)\n" +
                   "• Per quale domenica (es. Questa domenica o 29/11)\n" +
                   "• Eventuali intolleranze (o No)"
        }

        session.step = Step.PRENOTA_PERSONE
        return notaPieno +
               "Perfetto, procediamo con la prenotazione per $NOME_ATTIVITA!\n" +
               infoRegole +
               "Per quante persone desidera prenotare?"
    }

    private fun chiudiRichiestaPrenotazioneVersoOperatore(
        context: Context,
        senderKey: String,
        session: Session
    ): String {
        session.step = Step.OPERATORE_ATTIVO
        val telRiferimento = session.draft.telefono.ifBlank { senderKey }
        salvaRichiestaInAttesaConfermaOperatore(context, senderKey, session.draft, telRiferimento)

        aggiungiLog(
            context,
            "⏳ DA CONFERMARE",
            "${session.draft.nomeCognome} (Tel: $telRiferimento) • ${session.draft.persone} pers., bimbi 0-10: ${session.draft.bambini}, intoll.: ${session.draft.intolleranze} - ${session.draft.domenica}"
        )

        val testoPresaInCarico = getMessaggioPresaInCarico(context)

        return "$testoPresaInCarico\n\n" +
               "Riepilogo richiesta per $NOME_ATTIVITA:\n" +
               "• Nome e Cognome: ${session.draft.nomeCognome}\n" +
               "• Telefono: $telRiferimento\n" +
               "• Persone: ${session.draft.persone}\n" +
               "• Bambini (0-10 anni, pagano la metà): ${session.draft.bambini}\n" +
               "• Intolleranze: ${session.draft.intolleranze}\n" +
               "• Giorno: Domenica ${session.draft.domenica}\n" +
               "• Regola: Non è consentito l'accesso ai cani.\n\n" +
               "La prenotazione verrà inserita su Google Calendar non appena confermata dall'operatore. Un cordiale saluto!"
    }

    fun processIncomingMessage(context: Context, senderKey: String, incomingText: String): String? {
        val pieno = isSiamoPieni(context)
        val text = incomingText.trim()
        val session = sessions[senderKey]
        val eraIrraggiungibile = consumeUnreachableMatch(context, senderKey)
        val giaCliente = isClienteGiaMemorizzato(context, senderKey)
        val superaSogliaMessaggioPrecedente = controllaEAggiornaUltimoMessaggio(context, senderKey)

        if (session != null && session.step == Step.OPERATORE_ATTIVO) {
            if (text.equals("menu", ignoreCase = true)) {
                sessions[senderKey] = Session(Step.MENU)
                return if (pieno) getMessaggioSiamoPieni(context) else getBenvenutoMenu(eraIrraggiungibile, giaCliente)
            }
            return null
        }

        if (session != null && session.step != Step.DONE && !superaSogliaMessaggioPrecedente && !eraIrraggiungibile) {
            return gestisciStepAttivo(context, senderKey, session, text, pieno)
        }

        if (session == null || session.step == Step.DONE || superaSogliaMessaggioPrecedente || eraIrraggiungibile) {
            if (pieno) {
                if (text == "1" || text == "2" || text == "3") {
                    val newSession = Session(Step.PIENO_SCELTA_SETTIMANE)
                    sessions[senderKey] = newSession
                    val risp = gestisciStepAttivo(context, senderKey, newSession, text, pieno = true)
                    val saluto = if (giaCliente) {
                        "Salve, bentornato/a all'$NOME_ATTIVITA!"
                    } else {
                        "Salve, grazie di aver contattato $NOME_ATTIVITA!"
                    }
                    return "$saluto\n\n" + (risp ?: "")
                }
                sessions[senderKey] = Session(Step.PIENO_SCELTA_SETTIMANE)
                val prefisso = when {
                    eraIrraggiungibile && giaCliente ->
                        "Salve, bentornato/a! Ci scusiamo se al momento della chiamata verso $NOME_ATTIVITA il telefono non risultava raggiungibile. "
                    eraIrraggiungibile ->
                        "Salve, ci scusiamo se al momento della chiamata verso $NOME_ATTIVITA il telefono non risultava raggiungibile. "
                    giaCliente ->
                        "Salve, bentornato/a all'$NOME_ATTIVITA! "
                    else ->
                        ""
                }
                return prefisso + getMessaggioSiamoPieni(context)
            }

            if (text == "1" || text == "2" || text == "3") {
                val newSession = Session(Step.MENU)
                sessions[senderKey] = newSession
                val risp = gestisciStepAttivo(context, senderKey, newSession, text, pieno = false)
                val saluto = if (giaCliente) {
                    "Salve, bentornato/a all'$NOME_ATTIVITA!"
                } else {
                    "Salve, grazie di aver contattato $NOME_ATTIVITA!"
                }
                return "$saluto\n\n" + (risp ?: "")
            }

            sessions[senderKey] = Session(Step.MENU)
            if (isInfoOrariRequest(text)) {
                val saluto = if (giaCliente) {
                    "Salve, bentornato/a all'$NOME_ATTIVITA!"
                } else {
                    "Salve, grazie di aver contattato $NOME_ATTIVITA!"
                }
                return "$saluto\n" +
                       "ℹ️ Siamo aperti solo la domenica e il pranzo viene servito dalle 13:15.\n\n" +
                       "Come possiamo aiutarla? Risponda con un numero:\n" +
                       "1 - Per prenotare\n" +
                       "2 - Per disdire una prenotazione\n" +
                       "3 - Per parlare con un operatore"
            }
            return getBenvenutoMenu(eraIrraggiungibile, giaCliente)
        }

        return gestisciStepAttivo(context, senderKey, session, text, pieno)
    }

    private fun gestisciStepAttivo(
        context: Context,
        senderKey: String,
        session: Session,
        text: String,
        pieno: Boolean
    ): String? {
        return when (session.step) {
            Step.PIENO_SCELTA_SETTIMANE -> {
                when {
                    text == "1" || text.contains("prenot", ignoreCase = true) || text.contains("settiman", ignoreCase = true) -> {
                        avviaFlussoPrenotazione(context, session, localePieno = true)
                    }
                    text == "2" || text.contains("disd", ignoreCase = true) -> {
                        session.step = Step.DISDETTA_CHI
                        "Ci dispiace che debba disdire. Ci indichi Nome e Cognome della prenotazione (e la domenica):"
                    }
                    text == "3" || text.contains("operatore", ignoreCase = true) -> {
                        session.step = Step.OPERATORE_ATTIVO
                        aggiungiLog(context, "👤 OPERATORE", "$senderKey ha chiesto di parlare con un operatore")
                        "Richiesta trasferita a un operatore. Un nostro responsabile dell'$NOME_ATTIVITA le risponderà qui su WhatsApp il prima possibile. Un cordiale saluto!"
                    }
                    else -> {
                        "Salve! Al momento siamo al completo per la prossima domenica, ma può prenotare nelle settimane a seguire.\n" +
                        "Risponda con:\n1 - Per prenotare nelle prossime settimane\n2 - Per disdire\n3 - Per parlare con un operatore"
                    }
                }
            }

            Step.MENU -> {
                when {
                    text == "1" || text.contains("prenot", ignoreCase = true) -> {
                        avviaFlussoPrenotazione(context, session, localePieno = false)
                    }
                    text == "2" || text.contains("disd", ignoreCase = true) -> {
                        session.step = Step.DISDETTA_CHI
                        "Ci dispiace che debba disdire. Ci indichi Nome e Cognome della prenotazione (e la domenica):"
                    }
                    text == "3" || text.contains("operatore", ignoreCase = true) -> {
                        session.step = Step.OPERATORE_ATTIVO
                        aggiungiLog(context, "👤 OPERATORE", "$senderKey ha chiesto di parlare con un operatore")
                        "Richiesta trasferita a un operatore. Un nostro responsabile dell'$NOME_ATTIVITA le risponderà qui su WhatsApp il prima possibile. Un cordiale saluto!"
                    }
                    isInfoOrariRequest(text) -> {
                        "ℹ️ Siamo aperti solo la domenica e il pranzo viene servito dalle 13:15.\n\n" +
                        "Risponda con un numero per continuare:\n" +
                        "1 - Per prenotare\n" +
                        "2 - Per disdire una prenotazione\n" +
                        "3 - Per parlare con un operatore"
                    }
                    else -> {
                        "Salve! Per favore risponda solo con:\n1 - Per prenotare\n2 - Per disdire\n3 - Per parlare con un operatore"
                    }
                }
            }

            Step.PRENOTA_UNICO_MESSAGGIO -> {
                val righe = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
                val primaRiga = righe.firstOrNull() ?: text
                val numMatch = Regex("\\b\\d{1,2}\\b").find(text)
                val dataMatch = Regex("(\\d{1,2}[/\\-]\\d{1,2})").find(text)
                val telMatch = Regex("([+0-9][0-9\\s]{6,})").find(text)

                session.draft.nomeCognome = primaRiga.take(60)
                session.draft.persone = numMatch?.value ?: "Da verificare"
                session.draft.domenica = dataMatch?.value ?: if (pieno) "Settimana seguente" else "Questa domenica"
                session.draft.bambini = if (text.contains("bambin", ignoreCase = true) || text.contains("bimbo", ignoreCase = true) || text.contains("bimba", ignoreCase = true)) {
                    "Vedi dettaglio nel testo"
                } else {
                    "No / Vedi dettaglio"
                }
                session.draft.intolleranze = if (text.contains("intoller", ignoreCase = true) || text.contains("celiac", ignoreCase = true) || text.contains("lattos", ignoreCase = true) || text.contains("allerg", ignoreCase = true)) {
                    "Vedi dettaglio nel testo"
                } else {
                    "No"
                }
                session.draft.telefono = telMatch?.value?.trim() ?: senderKey

                if (righe.size == 1 && numMatch == null) {
                    session.draft.persone = text
                }

                chiudiRichiestaPrenotazioneVersoOperatore(context, senderKey, session)
            }

            Step.PRENOTA_PERSONE -> {
                session.draft.persone = text
                session.step = Step.PRENOTA_BAMBINI
                "Grazie! Ci sono bambini dai 0 ai 10 anni?\n(Specifichiamo che i bambini dai 0 ai 10 anni pagano la metà)"
            }

            Step.PRENOTA_BAMBINI -> {
                session.draft.bambini = text
                session.step = Step.PRENOTA_INTOLLERANZE
                "Ci sono persone con intolleranze alimentari?\n(Se sì, ci indichi quali; altrimenti scriva No)"
            }

            Step.PRENOTA_INTOLLERANZE -> {
                session.draft.intolleranze = text
                session.step = Step.PRENOTA_DOMENICA
                if (pieno) {
                    "Per quale domenica delle prossime settimane desidera prenotare? (es. Domenica 29/11 oppure 06/12)"
                } else {
                    "Per quale domenica desidera prenotare? (es. Questa domenica oppure 29/11)"
                }
            }

            Step.PRENOTA_DOMENICA -> {
                session.draft.domenica = text
                session.step = Step.PRENOTA_NOME_COGNOME
                "Ci indichi per favore Nome e Cognome per la prenotazione:"
            }

            Step.PRENOTA_NOME_COGNOME -> {
                session.draft.nomeCognome = text
                session.step = Step.PRENOTA_TELEFONO
                "Perfetto! Ci lasci infine un numero di telefono di riferimento:"
            }

            Step.PRENOTA_TELEFONO -> {
                session.draft.telefono = text
                chiudiRichiestaPrenotazioneVersoOperatore(context, senderKey, session)
            }

            Step.DISDETTA_CHI -> {
                session.step = Step.DONE
                val rimossi = rimuoviPrenotazioneDaGoogleCalendarAutomatica(context, text)
                saveCancellationToCalendarNote(context, senderKey, text)
                registraAvvisoDisdetta(context, text, senderKey, rimossi)
                val dettaglioCal = if (rimossi > 0) "rimossa da Google Calendar ($rimossi)" else "annotata disdetta"
                aggiungiLog(context, "❌ DISDETTA", "Disdetta: $text (da $senderKey) • $dettaglioCal")
                "✅ Abbiamo registrato la disdetta a nome di $text e aggiornato il calendario in automatico. Grazie per averci avvisato, un cordiale saluto!"
            }

            else -> null
        }
    }

    private fun rimuoviPrenotazioneDaGoogleCalendarAutomatica(context: Context, chiText: String): Int {
        var deletedCount = 0
        try {
            val cleaned = chiText.trim()
            if (cleaned.isEmpty()) return 0
            val nomeCercato = cleaned.substringBefore(",").substringBefore("-").trim().lowercase(Locale.ITALY)
            if (nomeCercato.length < 2) return 0

            val projection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DESCRIPTION
            )
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val eventId = cursor.getLong(0)
                    val title = cursor.getString(1) ?: ""
                    val desc = cursor.getString(2) ?: ""
                    if (title.startsWith("❌")) continue
                    val isPrenotazione = title.contains(NOME_ATTIVITA, ignoreCase = true) ||
                        title.contains("Prenotazione", ignoreCase = true) ||
                        desc.contains("Nome e Cognome:", ignoreCase = true)
                    if (!isPrenotazione) continue

                    if (title.lowercase(Locale.ITALY).contains(nomeCercato) ||
                        desc.lowercase(Locale.ITALY).contains("nome e cognome: $nomeCercato") ||
                        desc.lowercase(Locale.ITALY).contains(nomeCercato)) {
                        val deleteUri = Uri.withAppendedPath(CalendarContract.Events.CONTENT_URI, eventId.toString())
                        deletedCount += context.contentResolver.delete(deleteUri, null, null)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return deletedCount
    }

    private fun getPrimaryCalendarId(context: Context): Long {
        try {
            val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY)
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                "${CalendarContract.Calendars.VISIBLE} = 1",
                null,
                null
            )?.use { cursor ->
                var fallbackId = -1L
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val isPrimary = cursor.getInt(1)
                    if (fallbackId == -1L) fallbackId = id
                    if (isPrimary == 1) return id
                }
                if (fallbackId != -1L) return fallbackId
            }
        } catch (_: Exception) {}
        return 1L
    }

    private fun getNextSunday(from: Calendar): Calendar {
        val cal = from.clone() as Calendar
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val daysUntilSunday = if (dayOfWeek == Calendar.SUNDAY) 0 else (Calendar.SUNDAY - dayOfWeek + 7) % 7
        cal.add(Calendar.DAY_OF_YEAR, daysUntilSunday)
        return cal
    }

    private fun saveBookingToGoogleCalendar(
        context: Context,
        sender: String,
        draft: BookingDraft,
        customCal: Calendar? = null
    ): Boolean {
        return try {
            val cal = customCal ?: parseDomenicaCalendar(draft.domenica)
            val startMillis = cal.timeInMillis
            val endMillis = startMillis + (2 * 60 * 60 * 1000)
            val calendarId = getPrimaryCalendarId(context)

            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, endMillis)
                put(CalendarContract.Events.TITLE, "[$NOME_ATTIVITA] ${draft.nomeCognome} (${draft.persone} pers.) - Tel: ${draft.telefono}")
                put(CalendarContract.Events.EVENT_LOCATION, "$NOME_ATTIVITA ($GOOGLE_MAPS_LINK)")
                put(
                    CalendarContract.Events.DESCRIPTION,
                    "Attività: $NOME_ATTIVITA\n" +
                    "Stato: Confermata dall'operatore\n" +
                    "Nome e Cognome: ${draft.nomeCognome}\n" +
                    "Telefono lasciato: ${draft.telefono}\n" +
                    "Contatto / Origine: $sender\n" +
                    "Persone totali: ${draft.persone}\n" +
                    "Bambini 0-10 (pagano la metà): ${draft.bambini}\n" +
                    "Intolleranze: ${draft.intolleranze}\n" +
                    "Domenica richiesta: ${draft.domenica} (Pranzo dalle 13:15)\n" +
                    "Regola: Non è consentito l'accesso ai cani.\n" +
                    "Google Maps: $GOOGLE_MAPS_LINK"
                )
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }
            context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) != null
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun saveCancellationToCalendarNote(context: Context, sender: String, chi: String) {
        try {
            val now = System.currentTimeMillis()
            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, now)
                put(CalendarContract.Events.DTEND, now + 1800000)
                put(CalendarContract.Events.TITLE, "❌ [DISDETTA $NOME_ATTIVITA] $chi")
                put(CalendarContract.Events.DESCRIPTION, "Attività: $NOME_ATTIVITA\nDisdetta ricevuta su WhatsApp da: $sender\nPrenotazione a nome di: $chi")
                put(CalendarContract.Events.CALENDAR_ID, getPrimaryCalendarId(context))
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }
            context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        } catch (_: Exception) {}
    }

    fun aggiungiLog(context: Context, tipo: String, dettaglio: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY_LOG_EVENTI, "[]"))
        val ora = SimpleDateFormat("dd/MM HH:mm", Locale.ITALY).format(Date())
        val obj = JSONObject().apply {
            put("ora", ora)
            put("tipo", tipo)
            put("dettaglio", dettaglio)
        }
        val nuovo = JSONArray()
        nuovo.put(obj)
        for (i in 0 until minOf(arr.length(), 25)) {
            nuovo.put(arr.getJSONObject(i))
        }
        prefs.edit().putString(KEY_LOG_EVENTI, nuovo.toString()).apply()
    }

    fun getLogTestuale(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY_LOG_EVENTI, "[]"))
        if (arr.length() == 0) return "Nessuna attività recente."
        val sb = StringBuilder()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            sb.append("${o.getString("ora")} • ${o.getString("tipo")}\n${o.getString("dettaglio")}\n\n")
        }
        return sb.toString().trim()
    }
}
