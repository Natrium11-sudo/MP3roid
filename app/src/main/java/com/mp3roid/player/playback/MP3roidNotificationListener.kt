package com.mp3roid.player.playback

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sammelstelle für alle fremden Media-Sessions.
 *
 * Nur ein aktivierter [MP3roidNotificationListener] darf
 * [MediaSessionManager.getActiveSessions] aufrufen. Er legt das Ergebnis hier
 * ab, und die Oberfläche beobachtet diesen Zustand.
 */
object ExternalSessions {

    private val _controllers = MutableStateFlow<List<MediaController>>(emptyList())
    val controllers: StateFlow<List<MediaController>> = _controllers.asStateFlow()

    fun update(list: List<MediaController>) {
        _controllers.value = list.distinctBy { it.sessionToken.toString() }
    }
}

/**
 * Der Benachrichtigungszugriff ist der einzige zuverlässige Weg, an fremde
 * Wiedergabe-Informationen zu kommen – Spotify, YouTube-Music-Clients, VLC
 * und andere veröffentlichen dort ihre Media-Session.
 *
 * Dieser Dienst selbst macht nichts mit Benachrichtigungen. Er existiert, um
 * das Recht zu bekommen; die eigentliche Arbeit passiert über den
 * [MediaSessionManager].
 */
class MP3roidNotificationListener : NotificationListenerService() {

    private val sessionManager: MediaSessionManager?
        get() = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager

    private val componentName: ComponentName
        get() = ComponentName(this, MP3roidNotificationListener::class.java)

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        ExternalSessions.update(controllers.orEmpty())
    }

    private var registered = false

    override fun onListenerConnected() {
        super.onListenerConnected()
        register()
        refresh()
    }

    override fun onListenerDisconnected() {
        unregister()
        ExternalSessions.update(emptyList())
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        unregister()
        ExternalSessions.update(emptyList())
        super.onDestroy()
    }

    private fun register() {
        if (registered) return
        try {
            sessionManager?.addOnActiveSessionsChangedListener(
                sessionsChanged,
                componentName,
                Handler(Looper.getMainLooper()),
            )
            registered = true
        } catch (t: Throwable) {
            registered = false
        }
    }

    private fun unregister() {
        if (!registered) return
        try {
            sessionManager?.removeOnActiveSessionsChangedListener(sessionsChanged)
        } catch (t: Throwable) {
            // ignorieren
        }
        registered = false
    }

    private fun refresh() {
        val controllers = try {
            sessionManager?.getActiveSessions(componentName)
        } catch (t: Throwable) {
            null
        }
        ExternalSessions.update(controllers.orEmpty())
    }
}
