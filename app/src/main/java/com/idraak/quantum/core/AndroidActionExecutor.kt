package com.idraak.quantum.core

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings

class AndroidActionExecutor(private val context: Context) {
    fun execute(action: CommandAction): String = try {
        when (action) {
            CommandAction.None -> "No phone action was needed."
            is CommandAction.OpenUrl -> {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(action.url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Opened the requested link."
            }
            CommandAction.OpenSettings -> {
                context.startActivity(Intent(Settings.ACTION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Phone settings opened."
            }
            CommandAction.OpenCamera -> {
                context.startActivity(Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Camera app opened."
            }
        }
    } catch (_: ActivityNotFoundException) {
        "No compatible app is installed to complete that action."
    } catch (_: SecurityException) {
        "Android blocked this action because the required permission is not available."
    } catch (error: Exception) {
        "The action could not be completed: " + (error.localizedMessage ?: "unknown error")
    }
}
