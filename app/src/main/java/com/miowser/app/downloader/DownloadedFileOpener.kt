package com.miowser.app.downloader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

enum class FileOpenResult { STARTED, INSTALL_PERMISSION_REQUIRED, FILE_UNAVAILABLE, NO_HANDLER }

object DownloadedFileOpener {
    fun installApk(context: Context, download: Download): FileOpenResult {
        val uri = download.savedUri ?: return FileOpenResult.FILE_UNAVAILABLE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            return runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                FileOpenResult.INSTALL_PERMISSION_REQUIRED
            }.getOrDefault(FileOpenResult.NO_HANDLER)
        }
        return launchViewIntent(context, uri, "application/vnd.android.package-archive")
    }

    fun openWithSystem(context: Context, download: Download): FileOpenResult {
        val uri = download.savedUri ?: return FileOpenResult.FILE_UNAVAILABLE
        return launchViewIntent(context, uri, mimeTypeForDownloadedFile(download.displayName))
    }

    private fun launchViewIntent(context: Context, sourceUri: Uri, mimeType: String): FileOpenResult {
        val shareableUri = if (sourceUri.scheme == "file") {
            val path = sourceUri.path ?: return FileOpenResult.FILE_UNAVAILABLE
            runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))
            }.getOrNull() ?: return FileOpenResult.FILE_UNAVAILABLE
        } else {
            sourceUri
        }
        return runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(shareableUri, mimeType)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
            FileOpenResult.STARTED
        }.getOrDefault(FileOpenResult.NO_HANDLER)
    }
}
