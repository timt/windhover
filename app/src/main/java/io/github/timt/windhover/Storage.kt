package io.github.timt.windhover

import android.content.Context
import io.github.timt.windhover.core.profile.ProfileFile
import java.io.File

fun Context.profileFile() = ProfileFile(File(filesDir, "profiles.json"))

fun Context.sessionsRoot() = getExternalFilesDir("sessions") ?: File(filesDir, "sessions")

fun Context.exportsDir() = File(cacheDir, "exports")

val Context.fileProviderAuthority get() = "$packageName.files"
