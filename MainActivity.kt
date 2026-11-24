package com.agriturismo.santavittoria

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    private lateinit var tabOggiView: View
    private lateinit var tabTelefonoPdfView: View
    private lateinit var tabImpostazioniView: View
    private lateinit var btnTabOggi: Button
    private lateinit var btnTabTelefonoPdf: Button
    private lateinit var btnTabImpostazioni: Button

    private lateinit var cardAvvisiDisdette: LinearLayout
    private lateinit var tvAvvisiDisdette: TextView
    private lateinit var cardSetupRapido: LinearLayout
    private lateinit var tvSetupMessaggio: TextView

    private lateinit var tvStato: TextView
    private lateinit var btnTogglePieno: Button
    private lateinit var containerRichiesteAttesa: LinearLayout
    private lateinit var tvLogAttivita: TextView

    private lateinit var etTelNomeCognome: EditText
    private lateinit var etTelPersone: EditText
    private lateinit var etTelNumero: EditText
    private lateinit var etTelDomenica: EditText
    private lateinit var etTelBambini: EditText
    private lateinit var etTelIntolleranze: EditText

    private lateinit var etMsgPresaInCarico: EditText
    private lateinit var etMsgPieno: EditText
    private lateinit var cbFlussoVeloce: CheckBox
    private lateinit var cbAutoChiamataPersaWa: CheckBox
    private lateinit var cbSalvaRubrica: CheckBox
    private lateinit var etSogliaOre: EditText
    private lateinit var tvStatoPermessi: TextView

    private lateinit var etTestoOfferta: EditText
    private lateinit var ivAnteprimaOfferta: ImageView
    private lateinit var tvStatoOfferta: TextView

    private var currentTab = 0

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val savedPath = BotLogic.copiaImmagineOffertaInCache(this, uri)
            if (savedPath != null) {
                Toast.makeText(this, "✅ Foto allegata!", Toast.LENGTH_SHORT).show()
                aggiornaAnteprimaOfferta()
            } else {
                Toast.makeText(this, "⚠️ Impossibile leggere la foto.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tabOggiView = findViewById(R.id.tabOggiView)
        tabTelefonoPdfView = findViewById(R.id.tabTelefonoPdfView)
        tabImpostazioniView = findViewById(R.id.tabImpostazioniView)
        btnTabOggi = findViewById(R.id.btnTabOggi)
        btnTabTelefonoPdf = findViewById(R.id.btnTabTelefonoPdf)
        btnTabImpostazioni = findViewById(R.id.btnTabImpostazioni)

        cardAvvisiDisdette = findViewById(R.id.cardAvvisiDisdette)
        tvAvvisiDisdette = findViewById(R.id.tvAvvisiDisdette)
        cardSetupRapido = findViewById(R.id.cardSetupRapido)
        tvSetupMessaggio = findViewById(R.id.tvSetupMessaggio)

        tvStato = findViewById(R.id.tvStato)
        btnTogglePieno = findViewById(R.id.btnTogglePieno)
        containerRichiesteAttesa = findViewById(R.id.containerRichiesteAttesa)
        tvLogAttivita = findViewById(R.id.tvLogAttivita)

        etTelNomeCognome = findViewById(R.id.etTelNomeCognome)
        etTelPersone = findViewById(R.id.etTelPersone)
        etTelNumero = findViewById(R.id.etTelNumero)
        etTelDomenica = findViewById(R.id.etTelDomenica)
        etTelBambini = findViewById(R.id.etTelBambini)
        etTelIntolleranze = findViewById(R.id.etTelIntolleranze)

        etMsgPresaInCarico = findViewById(R.id.etMsgPresaInCarico)
        etMsgPieno = findViewById(R.id.etMsgPieno)
        cbFlussoVeloce = findViewById(R.id.cbFlussoVeloce)
        cbAutoChiamataPersaWa = findViewById(R.id.cbAutoChiamataPersaWa)
        cbSalvaRubrica = findViewById(R.id.cbSalvaRubrica)
        etSogliaOre = findViewById(R.id.etSogliaOre)
        tvStatoPermessi = findViewById(R.id.tvStatoPermessi)

        etTestoOfferta = findViewById(R.id.etTestoOfferta)
        ivAnteprimaOfferta = findViewById(R.id.ivAnteprimaOfferta)
        tvStatoOfferta = findViewById(R.id.tvStatoOfferta)

        val btnSegnaAvvisiLetti: Button = findViewById(R.id.btnSegnaAvvisiLetti)
        val btnConfigurazioneRapida: Button = findViewById(R.id.btnConfigurazioneRapida)
        val btnAggiungiPrenotazioneTel: Button = findViewById(R.id.btnAggiungiPrenotazioneTel)
        val btnEsportaPdfDomeniche: Button = findViewById(R.id.btnEsportaPdfDomeniche)
        val btnSalvaTesto: Button = findViewById(R.id.btnSalvaTesto)

        val btnScegliImmagineOfferta: Button = findViewById(R.id.btnScegliImmagineOfferta)
        val btnRimuoviImmagineOfferta: Button = findViewById(R.id.btnRimuoviImmagineOfferta)
        val btnInviaOffertaProssimo: Button = findViewById(R.id.btnInviaOffertaProssimo)
        val btnResetCodaOfferta: Button = findViewById(R.id.btnResetCodaOfferta)

        val btnPermessiNotifiche: Button = findViewById(R.id.btnPermessiNotifiche)
        val btnPermessiTelefonoCalendario: Button = findViewById(R.id.btnPermessiTelefonoCalendario)
        val btnPermessoOverlayChiamate: Button = findViewById(R.id.btnPermessoOverlayChiamate)
        val btnImpostazioniBatteria: Button = findViewById(R.id.btnImpostazioniBatteria)

        btnTabOggi.setOnClickListener { mostraScheda(0) }
        btnTabTelefonoPdf.setOnClickListener { mostraScheda(1) }
        btnTabImpostazioni.setOnClickListener { mostraScheda(2) }

        etMsgPieno.setText(BotLogic.getMessaggioSiamoPieni(this))
        etMsgPresaInCarico.setText(BotLogic.getMessaggioPresaInCarico(this))
        cbSalvaRubrica.isChecked = BotLogic.isSalvaInRubricaAttivo(this)
        cbFlussoVeloce.isChecked = BotLogic.isFlussoVeloceAttivo(this)
        cbAutoChiamataPersaWa.isChecked = BotLogic.isAutoChiamataPersaWaAttivo(this)
        etSogliaOre.setText(BotLogic.getSogliaOreChatEsistente(this).toString())
        etTestoOfferta.setText(BotLogic.getTestoOffertaMassiva(this))

        btnSegnaAvvisiLetti.setOnClickListener {
            BotLogic.azzeraAvvisiDisdette(this)
            aggiornaTutto()
        }

        btnConfigurazioneRapida.setOnClickListener {
            richiediPermessiBase()
            if (!isNotificationServiceEnabled()) {
                Toast.makeText(
                    this,
                    "👉 Attiva 'Segreteria Agriturismo Villaggio Santa Vittoria' e torna indietro.",
                    Toast.LENGTH_LONG
                ).show()
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } else {
                Toast.makeText(this, "✅ App già attiva e pronta!", Toast.LENGTH_SHORT).show()
            }
        }

        cbSalvaRubrica.setOnCheckedChangeListener { _, isChecked ->
            BotLogic.setSalvaInRubricaAttivo(this, isChecked)
        }

        cbFlussoVeloce.setOnCheckedChangeListener { _, isChecked ->
            BotLogic.setFlussoVeloceAttivo(this, isChecked)
        }

        cbAutoChiamataPersaWa.setOnCheckedChangeListener { _, isChecked ->
            BotLogic.setAutoChiamataPersaWaAttivo(this, isChecked)
        }

        btnTogglePieno.setOnClickListener {
            val nuovoStato = !BotLogic.isSiamoPieni(this)
            BotLogic.setSiamoPieni(this, nuovoStato)
            aggiornaTutto()
        }

        btnAggiungiPrenotazioneTel.setOnClickListener {
            val nome = etTelNomeCognome.text.toString().trim()
            val persone = etTelPersone.text.toString().trim()
            val tel = etTelNumero.text.toString().trim()
            val domenica = etTelDomenica.text.toString().trim()
            val bambini = etTelBambini.text.toString().trim()
            val intoll = etTelIntolleranze.text.toString().trim()

            if (nome.isEmpty()) {
                Toast.makeText(this, "⚠️ Scrivi almeno Nome e Cognome.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val ok = BotLogic.aggiungiPrenotazioneTelefonica(
                context = this,
                nomeCognome = nome,
                persone = persone,
                telefono = tel,
                domenicaInput = domenica,
                bambini = bambini,
                intolleranze = intoll
            )
            if (ok) {
                etTelNomeCognome.setText("")
                etTelPersone.setText("")
                etTelNumero.setText("")
                etTelDomenica.setText("")
                etTelBambini.setText("")
                etTelIntolleranze.setText("")
                Toast.makeText(this, "✅ Prenotazione salvata su Calendar e PDF!", Toast.LENGTH_LONG).show()
                aggiornaTutto()
            }
        }

        btnEsportaPdfDomeniche.setOnClickListener {
            esportaEStampaPdfDomeniche()
        }

        btnSalvaTesto.setOnClickListener {
            BotLogic.setMessaggioSiamoPieni(this, etMsgPieno.text.toString())
            BotLogic.setMessaggioPresaInCarico(this, etMsgPresaInCarico.text.toString())
            BotLogic.setTestoOffertaMassiva(this, etTestoOfferta.text.toString())
            val ore = etSogliaOre.text.toString().toIntOrNull() ?: 24
            BotLogic.setSogliaOreChatEsistente(this, ore)
            etSogliaOre.setText(BotLogic.getSogliaOreChatEsistente(this).toString())
            etMsgPresaInCarico.setText(BotLogic.getMessaggioPresaInCarico(this))
            Toast.makeText(this, "✅ Modifiche salvate!", Toast.LENGTH_SHORT).show()
            aggiornaAnteprimaOfferta()
        }

        btnScegliImmagineOfferta.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        btnRimuoviImmagineOfferta.setOnClickListener {
            BotLogic.rimuoviImmagineOfferta(this)
            aggiornaAnteprimaOfferta()
        }

        btnInviaOffertaProssimo.setOnClickListener {
            BotLogic.setTestoOffertaMassiva(this, etTestoOfferta.text.toString())
            inviaOffertaAlProssimoCliente()
        }

        btnResetCodaOfferta.setOnClickListener {
            BotLogic.setBroadcastIndex(this, 0)
            Toast.makeText(this, "🔄 Coda azzerata.", Toast.LENGTH_SHORT).show()
            aggiornaAnteprimaOfferta()
        }

        btnPermessiNotifiche.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        btnPermessiTelefonoCalendario.setOnClickListener {
            richiediPermessiBase()
        }

        btnPermessoOverlayChiamate.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                Toast.makeText(this, "✅ Banner chiamate già attivo!", Toast.LENGTH_SHORT).show()
            }
        }

        btnImpostazioniBatteria.setOnClickListener {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                } else {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                }
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
        }

        mostraScheda(0)
    }

    private fun mostraScheda(index: Int) {
        currentTab = index
        tabOggiView.visibility = if (index == 0) View.VISIBLE else View.GONE
        tabTelefonoPdfView.visibility = if (index == 1) View.VISIBLE else View.GONE
        tabImpostazioniView.visibility = if (index == 2) View.VISIBLE else View.GONE

        val activeBg = Color.parseColor("#064E3B")
        val inactiveBg = Color.parseColor("#E7E5E4")
        val activeTxt = Color.WHITE
        val inactiveTxt = Color.parseColor("#1C1917")

        btnTabOggi.setBackgroundColor(if (index == 0) activeBg else inactiveBg)
        btnTabOggi.setTextColor(if (index == 0) activeTxt else inactiveTxt)

        btnTabTelefonoPdf.setBackgroundColor(if (index == 1) activeBg else inactiveBg)
        btnTabTelefonoPdf.setTextColor(if (index == 1) activeTxt else inactiveTxt)

        btnTabImpostazioni.setBackgroundColor(if (index == 2) activeBg else inactiveBg)
        btnTabImpostazioni.setTextColor(if (index == 2) activeTxt else inactiveTxt)
    }

    private fun aggiornaRichiesteInAttesa() {
        containerRichiesteAttesa.removeAllViews()
        val lista = BotLogic.getRichiesteInAttesa(this)
        if (lista.isEmpty()) {
            val tvEmpty = TextView(this).apply {
                text = "Nessuna prenotazione da confermare al momento."
                textSize = 13f
                setTextColor(Color.parseColor("#57534E"))
            }
            containerRichiesteAttesa.addView(tvEmpty)
            return
        }

        for (req in lista) {
            val itemBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#F0FDF4"))
                setPadding(24, 20, 24, 20)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = 18
                layoutParams = lp
            }

            val tvInfo = TextView(this).apply {
                text = "👤 ${req.nomeCognome} • ${req.persone} persone\n📅 Domenica: ${req.domenica} • 📞 ${req.telefono}\n👶 Bimbi 0-10: ${req.bambini} • 🍽️ Intolleranze: ${req.intolleranze}"
                textSize = 14f
                setTextColor(Color.parseColor("#1C1917"))
            }

            val rowBtns = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                val rlp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                rlp.topMargin = 14
                layoutParams = rlp
            }

            val btnConferma = Button(this).apply {
                text = "✅ CONFERMA"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#15803D"))
                val blp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
                blp.marginEnd = 10
                layoutParams = blp
                setOnClickListener {
                    val ok = BotLogic.confermaPrenotazioneDaOperatore(this@MainActivity, req.id)
                    Toast.makeText(
                        this@MainActivity,
                        if (ok) "✅ Confermata e aggiunta su Google Calendar!" else "⚠️ Controlla permessi Google Calendar.",
                        Toast.LENGTH_LONG
                    ).show()
                    aggiornaTutto()
                }
            }

            val btnRifiuta = Button(this).apply {
                text = "✕ Rifiuta"
                setTextColor(Color.parseColor("#991B1B"))
                setBackgroundColor(Color.parseColor("#FEE2E2"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    BotLogic.scartaRichiestaInAttesa(this@MainActivity, req.id)
                    Toast.makeText(this@MainActivity, "Richiesta scartata.", Toast.LENGTH_SHORT).show()
                    aggiornaTutto()
                }
            }

            rowBtns.addView(btnConferma)
            rowBtns.addView(btnRifiuta)
            itemBox.addView(tvInfo)
            itemBox.addView(rowBtns)
            containerRichiesteAttesa.addView(itemBox)
        }
    }

    private fun richiediPermessiBase() {
        val perms = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
            perms.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        ActivityCompat.requestPermissions(this, perms.toTypedArray(), 101)
    }

    private fun esportaEStampaPdfDomeniche() {
        val pdfFile = BotLogic.generaPdfClientiPerDomenica(this)
        if (pdfFile == null || !pdfFile.exists()) {
            Toast.makeText(this, "⚠️ Impossibile creare il PDF. Controlla i permessi Calendario.", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val uri: Uri = FileProvider.getUriForFile(
                this,
                getString(R.string.file_provider_authority),
                pdfFile
            )
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(viewIntent, "Apri o stampa elenco clienti"))
            aggiornaTutto()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "✅ PDF creato: ${pdfFile.name}", Toast.LENGTH_LONG).show()
            aggiornaTutto()
        }
    }

    private fun inviaOffertaAlProssimoCliente() {
        val destinatari = BotLogic.getDestinatariMassivi(this)
        if (destinatari.isEmpty()) {
            Toast.makeText(this, "⚠️ Nessun cliente registrato ancora.", Toast.LENGTH_LONG).show()
            return
        }

        val idx = BotLogic.getBroadcastIndex(this)
        if (idx >= destinatari.size) {
            Toast.makeText(this, "✅ Invio completato per tutti i ${destinatari.size} clienti!", Toast.LENGTH_LONG).show()
            aggiornaAnteprimaOfferta()
            return
        }

        val (phone, nomeCliente) = destinatari[idx]
        val testoOfferta = BotLogic.getTestoOffertaMassiva(this)
        val imgPath = BotLogic.getImmagineOffertaPath(this)

        BotLogic.setBroadcastIndex(this, idx + 1)
        BotLogic.aggiungiLog(this, "📣 OFFERTA", "Inviata a $nomeCliente (+$phone) [${idx + 1}/${destinatari.size}]")

        try {
            if (imgPath != null) {
                val imgFile = File(imgPath)
                val contentUri: Uri = FileProvider.getUriForFile(
                    this,
                    getString(R.string.file_provider_authority),
                    imgFile
                )
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra(Intent.EXTRA_TEXT, testoOfferta)
                    putExtra("jid", "$phone@s.whatsapp.net")
                    setPackage("com.whatsapp.w4b")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(sendIntent)
            } else {
                val url = "https://api.whatsapp.com/send?phone=$phone&text=${URLEncoder.encode(testoOfferta, "UTF-8")}"
                val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    setPackage("com.whatsapp.w4b")
                }
                startActivity(waIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "⚠️ Verifica che WhatsApp Business sia installato.", Toast.LENGTH_LONG).show()
        }
    }

    private fun aggiornaAnteprimaOfferta() {
        val destinatari = BotLogic.getDestinatariMassivi(this)
        val idx = BotLogic.getBroadcastIndex(this).coerceAtMost(destinatari.size)
        val imgPath = BotLogic.getImmagineOffertaPath(this)

        if (imgPath != null) {
            val bmp = BitmapFactory.decodeFile(imgPath)
            if (bmp != null) {
                ivAnteprimaOfferta.setImageBitmap(bmp)
                ivAnteprimaOfferta.visibility = View.VISIBLE
            }
        } else {
            ivAnteprimaOfferta.setImageDrawable(null)
            ivAnteprimaOfferta.visibility = View.GONE
        }

        val statoImg = if (imgPath != null) "🖼️ Foto allegata" else "Solo testo"
        tvStatoOfferta.text = "$statoImg • Clienti: ${destinatari.size} • Inviati: $idx/${destinatari.size}"
    }

    override fun onResume() {
        super.onResume()
        aggiornaTutto()
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val cn = ComponentName(this, WhatsAppBotListenerService::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return !TextUtils.isEmpty(flat) && flat.contains(cn.flattenToString())
    }

    private fun hasPhoneCalendarContactsPermissions(): Boolean {
        val p1 = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val p2 = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
        val p3 = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED
        return p1 && p2 && p3
    }

    private fun hasOverlayPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
    }

    private fun aggiornaTutto() {
        val notifOk = isNotificationServiceEnabled()
        val permOk = hasPhoneCalendarContactsPermissions()

        if (notifOk && permOk) {
            cardSetupRapido.visibility = View.GONE
        } else {
            cardSetupRapido.visibility = View.VISIBLE
            tvSetupMessaggio.text = "Manca ancora un passaggio per attivare le risposte WhatsApp e Google Calendar:"
        }

        if (BotLogic.hasAvvisiDisdette(this)) {
            cardAvvisiDisdette.visibility = View.VISIBLE
            tvAvvisiDisdette.text = BotLogic.getAvvisiDisdetteTestuali(this)
        } else {
            cardAvvisiDisdette.visibility = View.GONE
        }

        if (BotLogic.isSiamoPieni(this)) {
            tvStato.text = "🔴 AL COMPLETO PROSSIMA DOMENICA\n(Aperto per le settimane seguenti)"
            tvStato.setTextColor(Color.parseColor("#B91C1C"))
            btnTogglePieno.text = "🟢 RIAPRI PRENOTAZIONI"
            btnTogglePieno.setBackgroundColor(Color.parseColor("#15803D"))
        } else {
            tvStato.text = "🟢 SEGRETERIA ATTIVA\nDomenica • Pranzo dalle 13:15"
            tvStato.setTextColor(Color.parseColor("#15803D"))
            btnTogglePieno.text = "🚫 SIAMO PIENI PROSSIMA DOMENICA"
            btnTogglePieno.setBackgroundColor(Color.parseColor("#DC2626"))
        }

        aggiornaRichiesteInAttesa()

        val overlayOk = hasOverlayPermission()
        tvStatoPermessi.text =
            (if (notifOk) "✅ Risposta WhatsApp: ATTIVA\n" else "⚠️ Risposta WhatsApp: DA ATTIVARE\n") +
            (if (permOk) "✅ Chiamate, Calendario & Rubrica: ATTIVI\n" else "⚠️ Chiamate, Calendario & Rubrica: DA ATTIVARE\n") +
            (if (overlayOk) "✅ Banner nome attività su chiamata: ATTIVO" else "ℹ️ Banner su chiamata: facoltativo")

        aggiornaAnteprimaOfferta()
        tvLogAttivita.text = BotLogic.getLogTestuale(this)
    }
}
