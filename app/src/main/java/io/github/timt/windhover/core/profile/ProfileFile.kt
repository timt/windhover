package io.github.timt.windhover.core.profile

import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.Success
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class ProfileFile(
    private val file: File,
) {
    fun load(): Result4k<Profiles, ProfileError> =
        if (file.exists()) profilesFromJson(file.readText()) else Success(Profiles())

    fun write(profiles: Profiles) {
        val temp = File(file.parentFile, "${file.name}.tmp").apply { writeText(profiles.toJson()) }
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
