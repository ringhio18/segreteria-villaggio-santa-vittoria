package com.agriturismo.santavittoria

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews

class SiamoPieniWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE = "com.agriturismo.santavittoria.TOGGLE_PIENO"

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, SiamoPieniWidget::class.java))
            for (id in ids) {
                updateAppWidget(context, manager, id)
            }
        }

        fun updateAppWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_siamo_pieni)
            val pieno = BotLogic.isSiamoPieni(context)

            if (pieno) {
                views.setTextViewText(R.id.widgetTitle, "🔴 PIENO PROSSIMA DOMENICA")
                views.setTextViewText(R.id.widgetSubtitle, "${BotLogic.NOME_ATTIVITA} • Settimane seguenti aperte")
                views.setInt(R.id.widgetRoot, "setBackgroundColor", Color.parseColor("#B91C1C"))
            } else {
                views.setTextViewText(R.id.widgetTitle, "🟢 PRENOTAZIONI ATTIVE")
                views.setTextViewText(R.id.widgetSubtitle, "${BotLogic.NOME_ATTIVITA} • Tocca per SIAMO PIENI")
                views.setInt(R.id.widgetRoot, "setBackgroundColor", Color.parseColor("#15803D"))
            }

            val intent = Intent(context, SiamoPieniWidget::class.java).apply {
                action = ACTION_TOGGLE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, pendingIntent)
            manager.updateAppWidget(widgetId, views)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE) {
            val nuovo = !BotLogic.isSiamoPieni(context)
            BotLogic.setSiamoPieni(context, nuovo)
        }
    }
}
