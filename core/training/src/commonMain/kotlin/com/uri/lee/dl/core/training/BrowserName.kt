package com.uri.lee.dl.core.training

/**
 * A browser's client hints (navigator.userAgentData) as a short device name for research runs,
 * e.g. "Chrome 155 on macOS 26.1 (arm64)"; null if they don't name a browser.
 */
fun describeBrowser(
    brands: List<Pair<String, String>>,
    platform: String?,
    platformVersion: String?,
    architecture: String?,
    bitness: String?,
): String? {
    // Chromium lists itself, the browser built on it and a made-up brand (e.g. "Not.A/Brand")
    val real = brands.filterNot { (name, _) -> name.contains("Not") && name.any { !it.isLetterOrDigit() && it != ' ' } }
    val (name, version) = real.firstOrNull { it.first != "Chromium" } ?: real.firstOrNull() ?: return null
    val browser = "${name.removePrefix("Google ").removePrefix("Microsoft ")} ${version.substringBefore('.')}".trim()
    val system = platform?.takeIf { it.isNotBlank() }?.let { "$it ${systemVersion(it, platformVersion)}".trim() }
    val arch = when {
        architecture.isNullOrBlank() -> ""
        architecture == "x86" && bitness == "64" -> "x86_64"
        bitness == "64" -> "${architecture}64"
        else -> architecture
    }
    return buildString {
        append(browser)
        if (system != null) append(" on ").append(system)
        if (arch.isNotEmpty()) append(" (").append(arch).append(')')
    }
}

/** e.g. "26.1.0" → "26.1"; on Windows the hint is a build number (13 and up is Windows 11). */
private fun systemVersion(platform: String, version: String?): String {
    if (version.isNullOrBlank()) return ""
    if (platform == "Windows") {
        val major = version.substringBefore('.').toIntOrNull() ?: return ""
        return when { major >= 13 -> "11"; major > 0 -> "10"; else -> "" }
    }
    return version.split('.').dropLastWhile { it == "0" }.ifEmpty { listOf("0") }.joinToString(".")
}
