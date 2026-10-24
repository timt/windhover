package io.github.timt.windhover.core.profile

import dev.forkhandles.result4k.Failure
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.Success
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class Transport { SPP, BLE }

@Serializable
data class DeviceProfile(
    val id: String,
    val name: String,
    val glider: String,
    val lxModel: String,
    val address: String,
    val transport: Transport,
)

data class ProfileDraft(
    val name: String,
    val glider: String,
    val lxModel: String,
    val address: String,
    val transport: Transport,
)

sealed interface ProfileError {
    data object BlankName : ProfileError

    data class InvalidAddress(
        val address: String,
    ) : ProfileError

    data class Corrupt(
        val reason: String,
    ) : ProfileError
}

private val bluetoothAddress = Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$")

fun ProfileDraft.toProfile(id: String): Result4k<DeviceProfile, ProfileError> {
    val normalisedAddress = address.trim().uppercase()
    return when {
        name.isBlank() -> Failure(ProfileError.BlankName)
        !bluetoothAddress.matches(normalisedAddress) -> Failure(ProfileError.InvalidAddress(address))
        else -> Success(DeviceProfile(id, name.trim(), glider.trim(), lxModel.trim(), normalisedAddress, transport))
    }
}

@Serializable
data class Profiles(
    val all: List<DeviceProfile> = emptyList(),
    val lastUsedId: String? = null,
) {
    val lastUsed: DeviceProfile? get() = all.find { it.id == lastUsedId }

    fun save(profile: DeviceProfile) =
        copy(
            all =
                if (all.any { it.id == profile.id }) {
                    all.map { if (it.id == profile.id) profile else it }
                } else {
                    all + profile
                },
        )

    fun remove(id: String) = copy(all = all.filterNot { it.id == id }, lastUsedId = lastUsedId.takeUnless { it == id })

    fun markUsed(id: String) = if (all.any { it.id == id }) copy(lastUsedId = id) else this
}

private val json = Json { prettyPrint = true }

fun Profiles.toJson(): String = json.encodeToString(Profiles.serializer(), this)

fun profilesFromJson(text: String): Result4k<Profiles, ProfileError> =
    try {
        Success(json.decodeFromString(Profiles.serializer(), text))
    } catch (e: IllegalArgumentException) {
        Failure(ProfileError.Corrupt(e.message ?: "unreadable"))
    }
