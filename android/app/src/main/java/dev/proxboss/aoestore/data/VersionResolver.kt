package dev.proxboss.aoestore.data

object VersionResolver {

    private val semverRegex = Regex("^[vV]?(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$")

    fun compareVersionNames(left: String, right: String): Int {
        val a = parse(left)
        val b = parse(right)
        if (a == null && b == null) return left.compareTo(right)
        if (a == null) return -1
        if (b == null) return 1
        for (index in 0..2) {
            if (a.numbers[index] != b.numbers[index]) {
                return a.numbers[index].compareTo(b.numbers[index])
            }
        }
        if (a.prerelease == b.prerelease) return 0
        if (a.prerelease.isEmpty()) return 1
        if (b.prerelease.isEmpty()) return -1
        return a.prerelease.compareTo(b.prerelease)
    }

    fun sortDescending(versions: List<VersionInfo>): List<VersionInfo> {
        return versions.sortedWith(
            Comparator { left, right ->
                if (left.versionCode != right.versionCode) {
                    right.versionCode.compareTo(left.versionCode)
                } else {
                    compareVersionNames(right.version, left.version)
                }
            },
        )
    }

    fun latest(versions: List<VersionInfo>): VersionInfo? = sortDescending(versions).firstOrNull()

    fun latest(detail: AppDetail): VersionInfo? = latest(detail.versions)

    fun updateAvailable(installedVersionCode: Int?, latest: VersionInfo?): Boolean {
        return installedVersionCode != null && latest != null && latest.versionCode > installedVersionCode
    }

    fun isUpToDate(installedVersionCode: Int?, latest: VersionInfo?): Boolean {
        return installedVersionCode != null && latest != null && installedVersionCode >= latest.versionCode
    }

    fun findVersion(versions: List<VersionInfo>, versionOrCode: String): VersionInfo? {
        return versions.firstOrNull { it.version == versionOrCode || it.versionCode.toString() == versionOrCode }
    }

    private fun parse(value: String): ParsedVersion? {
        val match = semverRegex.matchEntire(value.trim()) ?: return null
        return ParsedVersion(
            numbers = intArrayOf(
                match.groupValues[1].toIntOrNull() ?: 0,
                match.groupValues[2].toIntOrNull() ?: 0,
                match.groupValues[3].toIntOrNull() ?: 0,
            ),
            prerelease = match.groupValues[4],
        )
    }

    private data class ParsedVersion(val numbers: IntArray, val prerelease: String)
}
