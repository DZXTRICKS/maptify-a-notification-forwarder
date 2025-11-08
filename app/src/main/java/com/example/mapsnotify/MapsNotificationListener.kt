package com.example.mapsnotify

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class MapsNotificationListener : NotificationListenerService() {

    // Use a combined identifier for more robust state tracking
    private var lastRelayedIdentifier: String? = null
    private var lastRelayTimestamp: Long = 0

    companion object {
        private const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"
        private const val TAG = "MapsNotifyListener"
        private const val MIN_DISTANCE_METERS = 300
        private const val RATE_LIMIT_MS = 5000 // 5 seconds
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "🔗 Notification Listener connected.")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn ?: return

        val notification = sbn.notification
        if (sbn.packageName == GOOGLE_MAPS_PACKAGE || notification.category == Notification.CATEGORY_NAVIGATION) {
            val extras = notification.extras

            val directionText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            val distanceText = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val summaryText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()

            if (directionText.isNullOrBlank()) {
                return // Ignore notification if it has no direction
            }

            // --- Start Final Logic ---
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastRelayTimestamp < RATE_LIMIT_MS) {
                return // Rate limited
            }

            // Create a unique identifier for the current instruction
            val currentIdentifier = "$directionText|$distanceText"
            val isNewInstruction = currentIdentifier != lastRelayedIdentifier

            val distanceInMeters = parseDistance(distanceText)
            val isCloseToTurn = distanceInMeters != null && distanceInMeters <= MIN_DISTANCE_METERS

            // Trigger if it's a completely new instruction OR if we are close to the turn
            if (isNewInstruction || isCloseToTurn) {
                Log.i(TAG, "Relaying notification. Reason: ${if (isNewInstruction) "New Instruction" else "Close to Turn"}.")

                val directionWithSymbol = getDirectionSymbol(directionText)

                val intent = Intent("com.example.mapsnotify.NOTIFICATION_LISTENER").apply {
                    putExtra("Direction", "$directionWithSymbol $directionText")
                    putExtra("TimeDistInfo", "${distanceText ?: ""} • ${summaryText ?: ""}")
                }
                sendBroadcast(intent)

                // Update state
                lastRelayedIdentifier = currentIdentifier
                lastRelayTimestamp = currentTime
            }
            // --- End Final Logic ---
        }
    }

    private fun parseDistance(distanceText: String?): Int? {
        distanceText ?: return null
        return try {
            val cleanedText = distanceText.trim().replace(',', '.').replace(Regex("[^0-9.km]"), "").replace("km", " km")
            val value = cleanedText.split(" ")[0].toFloatOrNull() ?: return null

            when {
                cleanedText.contains("km") -> (value * 1000).toInt()
                cleanedText.contains("m") -> value.toInt()
                else -> value.toInt() // Assume meters if no unit is present
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not parse distance: '$distanceText'", e)
            null
        }
    }

    private fun getDirectionSymbol(text: String?): String {
        text ?: return "📍"
        val lowerText = text.lowercase()

        return when {
            "turn right" in lowerText || "exit right" in lowerText -> "➡️"
            "turn left" in lowerText || "exit left" in lowerText -> "⬅️"
            "keep right" in lowerText -> "↗️"
            "keep left" in lowerText -> "↖️"
            "make a u-turn" in lowerText -> "↩️"
            "roundabout" in lowerText -> "🔄"
            lowerText.startsWith("head") || "straight" in lowerText || "stay on" in lowerText -> "⬆️"
            "destination" in lowerText -> "🏁"
            else -> "📍" // Default symbol for unknown directions
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.i(TAG, "🔌 Notification Listener disconnected.")
    }
}
