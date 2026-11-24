package com.agriturismo.santavittoria

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class WhatsAppBotListenerService : NotificationListenerService() {

    private val lastProcessedTimestamp = mutableMapOf<String, Long>()

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val pkg = sbn.packageName ?: return
        if (pkg != "com.whatsapp.w4b" && pkg != "com.whatsapp") return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return
        if (extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)) return

        val sender = extras.getString(Notification.EXTRA_TITLE) ?: return
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return

        val now = System.currentTimeMillis()
        val prev = lastProcessedTimestamp[sender] ?: 0L
        if (now - prev < 1500L) return
        lastProcessedTimestamp[sender] = now

        val replyAction = findReplyAction(notification) ?: return
        val responseText = BotLogic.processIncomingMessage(applicationContext, sender, text) ?: return

        sendDirectReply(replyAction, responseText)
    }

    private fun findReplyAction(notification: Notification): Notification.Action? {
        val actions = notification.actions ?: return null
        for (action in actions) {
            val remoteInputs = action.remoteInputs ?: continue
            for (ri in remoteInputs) {
                if (ri.allowFreeFormInput) return action
            }
        }
        return null
    }

    private fun sendDirectReply(action: Notification.Action, replyMessage: String) {
        try {
            val intent = Intent()
            val bundle = Bundle()
            val remoteInputs = action.remoteInputs ?: return
            for (ri in remoteInputs) {
                bundle.putCharSequence(ri.resultKey, replyMessage)
            }
            RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)
            action.actionIntent.send(applicationContext, 0, intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
