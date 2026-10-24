package io.github.timt.windhover.core.scan

data class NearbyDevice(
    val name: String?,
    val address: String,
    val paired: Boolean,
)

private val lxnavName = Regex("(?i)(lxnav|^lx\\d|^s\\d)")

fun String.looksLikeLxnav() = lxnavName.containsMatchIn(this)

data class NearbyDevices(
    val scanning: Boolean = false,
    private val seen: List<NearbyDevice> = emptyList(),
) {
    val ordered: List<NearbyDevice>
        get() =
            seen.sortedBy {
                when {
                    it.name == null -> 2
                    it.name.looksLikeLxnav() -> 0
                    else -> 1
                }
            }

    fun found(device: NearbyDevice) =
        copy(
            seen =
                seen.find { it.address == device.address }?.let { known ->
                    seen.map { if (it == known) device.copy(name = device.name ?: known.name) else it }
                } ?: (seen + device),
        )

    fun restarted() = NearbyDevices(scanning = true)

    fun finished() = copy(scanning = false)
}
