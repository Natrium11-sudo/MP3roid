package com.mp3roid.player.launcher

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.mp3roid.player.playback.MP3roidNotificationListener

/** Startbildschirm-Rolle anfragen und prüfen. */
object HomeRole {

    fun isHeld(context: Context): Boolean {
        val manager = context.getSystemService(RoleManager::class.java) ?: return false
        return manager.isRoleAvailable(RoleManager.ROLE_HOME) && manager.isRoleHeld(RoleManager.ROLE_HOME)
    }

    fun requestIntent(context: Context): Intent? {
        val manager = context.getSystemService(RoleManager::class.java) ?: return null
        if (!manager.isRoleAvailable(RoleManager.ROLE_HOME)) return null
        if (manager.isRoleHeld(RoleManager.ROLE_HOME)) return null
        return manager.createRequestRoleIntent(RoleManager.ROLE_HOME)
    }

    /** Fallback, wenn das Gerät die Rolle nicht kennt. */
    fun openDefaultAppsSettings(context: Context) {
        val intent = Intent("android.settings.HOME_SETTINGS")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}

/**
 * Benachrichtigungszugriff – ohne dieses Recht ist keine fremde
 * Wiedergabe sichtbar (Spotify, YouTube-Music-Clients, VLC …).
 */
object NotifAccess {

    private const val EXTRA_COMPONENT = "android.provider.extra.NOTIFICATION_LISTENER_COMPONENT_NAME"

    fun isGranted(context: Context): Boolean = try {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    } catch (t: Throwable) {
        false
    }

    fun requestIntent(context: Context): Intent {
        val component = ComponentName(context, MP3roidNotificationListener::class.java)
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(EXTRA_COMPONENT, component.flattenToString())
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

/** Leserecht auf die Musiksammlung. */
object AudioPermission {

    fun required(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    fun isGranted(context: Context): Boolean = try {
        ContextCompat.checkSelfPermission(context, required()) == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        false
    }
}

/** Vollbild ohne Status- und Navigationsleiste. */
class SystemBarsController(private val activity: Activity) {

    private var hidden = false

    fun setHidden(value: Boolean) {
        hidden = value
        apply()
    }

    fun onWindowFocusChanged(hasFocus: Boolean) {
        if (hasFocus) apply()
    }

    private fun apply() {
        val view = activity.window.decorView ?: return
        val controller = WindowCompat.getInsetsController(activity.window, view)
        if (hidden) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
