package com.mp3roid.player.data

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.provider.Settings
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

data class AppEntry(
    val label: String,
    val packageName: String,
    val activity: ComponentName,
    val icon: ImageBitmap?,
)

/**
 * Liste der installierten Apps über die offizielle Launcher-API
 * ([LauncherApps]). Damit werden auch nachträglich installierte Apps erkannt.
 */
class AppsRepository(private val context: Context) {

    private val icons = LruCache<String, ImageBitmap>(96)

    private var callback: LauncherApps.Callback? = null
    private var receiver: BroadcastReceiver? = null
    private var reloadJob: Job? = null

    /**
     * Meldet Installationen und Deinstallationen. Zwei Wege doppelt: die
     * Launcher-API (das offizielle Mittel eines Startbildschirms) und die
     * Paket-Meldungen des Systems. Kurze Sammelpause, weil ein Update als
     * Entfernen und Hinzufügen in Folge eintrifft.
     */
    fun startObserving(scope: CoroutineScope, onChanged: () -> Unit) {
        if (callback != null || receiver != null) return

        val notify: (List<String>) -> Unit = { packages ->
            packages.forEach { icons.remove(it) }
            reloadJob?.cancel()
            reloadJob = scope.launch {
                delay(600)
                onChanged()
            }
        }

        context.getSystemService(LauncherApps::class.java)?.let { launcherApps ->
            val created = object : LauncherApps.Callback() {
                override fun onPackageAdded(packageName: String?, user: UserHandle?) =
                    notify(listOfNotNull(packageName))

                override fun onPackageRemoved(packageName: String?, user: UserHandle?) =
                    notify(listOfNotNull(packageName))

                override fun onPackageChanged(packageName: String?, user: UserHandle?) =
                    notify(listOfNotNull(packageName))

                override fun onPackagesAvailable(
                    packageNames: Array<out String>?,
                    user: UserHandle?,
                    replacing: Boolean,
                ) = notify(packageNames?.toList().orEmpty())

                override fun onPackagesUnavailable(
                    packageNames: Array<out String>?,
                    user: UserHandle?,
                    replacing: Boolean,
                ) = notify(packageNames?.toList().orEmpty())
            }
            callback = created
            try {
                launcherApps.registerCallback(created, Handler(Looper.getMainLooper()))
            } catch (t: Throwable) {
                callback = null
            }
        }

        val created = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                notify(listOfNotNull(intent?.data?.schemeSpecificPart))
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_DATA_CLEARED)
            addDataScheme("package")
        }
        receiver = created
        try {
            ContextCompat.registerReceiver(
                context,
                created,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        } catch (t: Throwable) {
            receiver = null
        }

        scope.coroutineContext[Job]?.invokeOnCompletion { stopObserving() }
    }

    fun stopObserving() {
        callback?.let { created ->
            try {
                context.getSystemService(LauncherApps::class.java)?.unregisterCallback(created)
            } catch (t: Throwable) {
                // egal
            }
        }
        callback = null
        receiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (t: Throwable) {
                // egal
            }
        }
        receiver = null
        reloadJob?.cancel()
        reloadJob = null
    }

    suspend fun load(): List<AppEntry> = withContext(Dispatchers.IO) {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
            ?: return@withContext emptyList()
        val user = Process.myUserHandle()
        val activities = try {
            launcherApps.getActivityList(null, user)
        } catch (t: Throwable) {
            return@withContext emptyList()
        }

        val collator = Collator.getInstance(Locale.GERMAN).apply { strength = Collator.PRIMARY }
        val seen = HashSet<String>()
        val entries = ArrayList<AppEntry>(activities.size)

        for (info in activities) {
            val pkg = info.applicationInfo?.packageName ?: continue
            if (pkg == context.packageName) continue
            if (!seen.add(pkg)) continue
            val label = try {
                info.label?.toString()?.trim().orEmpty()
            } catch (t: Throwable) {
                ""
            }
            entries += AppEntry(
                label = label.ifEmpty { pkg },
                packageName = pkg,
                activity = info.componentName,
                icon = iconFor(pkg, info),
            )
        }

        entries.sortWith { a, b -> collator.compare(a.label, b.label) }
        entries
    }

    private fun iconFor(packageName: String, info: android.content.pm.LauncherActivityInfo): ImageBitmap? {
        icons.get(packageName)?.let { return it }
        return try {
            val density = context.resources.displayMetrics.density
            val size = (48 * density).toInt().coerceIn(48, 128)
            val drawable = info.getBadgedIcon(0)
            val bitmap = drawable.toBitmap(size, size, Bitmap.Config.ARGB_8888)
            bitmap.asImageBitmap().also { icons.put(packageName, it) }
        } catch (t: Throwable) {
            null
        }
    }

    fun launch(entry: AppEntry) {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val intent = try {
            context.packageManager.getLaunchIntentForPackage(entry.packageName)
        } catch (t: Throwable) {
            null
        }
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            if (runCatching { context.startActivity(intent) }.isSuccess) return
        }
        if (launcherApps != null) {
            runCatching {
                launcherApps.startMainActivity(entry.activity, Process.myUserHandle(), null, null)
            }
        }
    }

    fun openInfo(entry: AppEntry): Boolean {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", entry.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /**
     * Öffnet die System-Rückfrage zum Deinstallieren.
     *
     * Dafür braucht die App `REQUEST_DELETE_PACKAGES` (siehe Manifest): seit
     * Android 10 wirft `startActivity` sonst eine SecurityException. Der
     * Rückgabewert meldet, ob die Rückfrage wirklich aufgegangen ist – vorher
     * verschwand der Fehler in einem `runCatching`, und für den Nutzer passierte
     * beim Tippen einfach nichts.
     */
    fun uninstall(entry: AppEntry): Boolean {
        val intent = Intent(Intent.ACTION_DELETE)
            .setData(Uri.fromParts("package", entry.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}
