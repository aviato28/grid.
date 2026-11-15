package com.nbansal.grid.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed interface DownloadEvent {
    data class Progress(val fraction: Float) : DownloadEvent
    data class Done(val file: File) : DownloadEvent
}

/** Install outcomes reported by [InstallResultReceiver]. */
object InstallEvents {
    private val _failures = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val failures: SharedFlow<String> = _failures
    internal fun fail(msg: String) { _failures.tryEmit(msg) }
}

class UpdateInstaller(private val context: Context) {

    /** Downloads the APK into cacheDir/updates, emitting progress 0..1 and finally [DownloadEvent.Done]. */
    fun download(url: String, expectedSize: Long = -1): Flow<DownloadEvent> = flow {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "update.apk")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Grid-Android")
        }
        try {
            if (conn.responseCode != 200) throw IOException("Download failed: HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: expectedSize
            var read = 0L
            var lastEmit = -1
            emit(DownloadEvent.Progress(0f))
            conn.inputStream.use { input ->
                out.outputStream().use { os ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        os.write(buf, 0, n)
                        read += n
                        if (total > 0) {
                            val pct = (read * 100 / total).toInt()
                            if (pct != lastEmit) {
                                lastEmit = pct
                                emit(DownloadEvent.Progress((read.toFloat() / total).coerceIn(0f, 1f)))
                            }
                        }
                    }
                }
            }
            emit(DownloadEvent.Progress(1f))
            emit(DownloadEvent.Done(out))
        } finally {
            conn.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Opens the "install unknown apps" settings page for this app. */
    fun openInstallPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Streams [apk] into a PackageInstaller session and commits it. */
    suspend fun install(apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setSize(apk.length())
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("grid-update", 0, apk.length()).use { os ->
                    input.copyTo(os)
                    session.fsync(os)
                }
            }
            val intent = Intent(context, InstallResultReceiver::class.java)
                .setAction(InstallResultReceiver.ACTION)
                .setPackage(context.packageName)
            // Must be mutable: the system adds EXTRA_STATUS / EXTRA_INTENT (API 31+).
            val pi = PendingIntent.getBroadcast(
                context, sessionId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pi.intentSender)
        }
    }
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (android.os.Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirm?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_FAILURE_ABORTED -> Unit
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "status $status"
                InstallEvents.fail("Install failed: $msg")
            }
        }
    }

    companion object {
        const val ACTION = "com.nbansal.grid.INSTALL_RESULT"
    }
}
