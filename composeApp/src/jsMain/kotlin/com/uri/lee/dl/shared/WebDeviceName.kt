package com.uri.lee.dl.shared

import com.uri.lee.dl.core.training.describeBrowser
import kotlin.js.Promise
import kotlinx.browser.window

/**
 * The browser and machine for research runs, e.g. "Chrome 155 on macOS 26.1 (arm64)". Chrome's
 * user agent says "Intel Mac OS X 10_15_7" on every Mac, so Chromium's client hints are asked
 * for at start-up; until they answer, and in Safari and Firefox, it's the user agent.
 */
internal object WebDeviceName {
    var value: String = window.navigator.userAgent
        private set

    fun start() {
        val hints = window.navigator.asDynamic().userAgentData ?: return
        hints.getHighEntropyValues(arrayOf("platformVersion", "architecture", "bitness", "fullVersionList"))
            .unsafeCast<Promise<dynamic>>()
            .then({ data: dynamic ->
                val list = (data.fullVersionList ?: data.brands).unsafeCast<Array<dynamic>?>().orEmpty()
                describeBrowser(
                    brands = list.map { (it.brand as String) to ((it.version ?: "") as String) },
                    platform = data.platform as? String,
                    platformVersion = data.platformVersion as? String,
                    architecture = data.architecture as? String,
                    bitness = data.bitness as? String,
                )?.let { value = it }
            }, { _: dynamic -> })
    }
}
