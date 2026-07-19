package com.example.fakenews.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URI

enum class OpenUrlResult {
    Opened,
    InvalidUrl,
    NoHandler
}

object OpenUrl {
    fun isValidHttpUrl(url: String): Boolean =
        runCatching {
            val uri = URI(url.trim())
            (uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()
        }.getOrDefault(false)

    fun openExternalBrowser(context: Context, url: String): OpenUrlResult {
        val trimmedUrl = url.trim()
        if (!isValidHttpUrl(trimmedUrl)) {
            return OpenUrlResult.InvalidUrl
        }

        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(trimmedUrl))
            context.startActivity(intent)
            OpenUrlResult.Opened
        } catch (_: ActivityNotFoundException) {
            OpenUrlResult.NoHandler
        } catch (_: SecurityException) {
            OpenUrlResult.NoHandler
        }
    }
}
