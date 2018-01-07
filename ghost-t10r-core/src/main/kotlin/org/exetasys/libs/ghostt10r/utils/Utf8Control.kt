package org.exetasys.libs.ghostt10r.utils

import java.io.InputStreamReader
import java.util.PropertyResourceBundle
import java.util.ResourceBundle
import java.io.IOException
import java.io.InputStream
import java.util.Locale


class UTF8Control : ResourceBundle.Control() {

    @Throws(IllegalAccessException::class, InstantiationException::class, IOException::class)
    override fun newBundle(
            baseName: String,
            locale: Locale,
            format: String,
            loader: ClassLoader,
            reload: Boolean): ResourceBundle? {
        val bundleName = toBundleName(baseName, locale)
        val resourceName = toResourceName(bundleName, "properties")

        var stream: InputStream? =
            if (reload) {
                loader.getResource(resourceName)
                    ?.openConnection()
                    ?.let { it.useCaches = false ; it }
                    ?.getInputStream()
            } else {
                loader.getResourceAsStream(resourceName)
            }

        try {
            return stream
                ?.let { PropertyResourceBundle(InputStreamReader(it, "UTF-8")) }
        }
        finally {
            stream?.let { stream::close }
        }
    }

}