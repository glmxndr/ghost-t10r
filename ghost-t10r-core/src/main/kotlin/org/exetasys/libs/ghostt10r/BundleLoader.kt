package org.exetasys.libs.ghostt10r

import org.exetasys.libs.ghostt10r.model.MsgSpecs
import java.io.File
import java.net.URI
import java.net.URLClassLoader
import java.util.*

open class BundleLoader(
    rscDirs : List<File>,
    val bundleName : String,
    val keyPrefix:  String,
    val mainLocale : Locale,
    val locales : Set<Locale>
) {

    private val classLoader = URLClassLoader(rscDirs
            .map(File::toURI)
            .map(URI::toURL)
            .toTypedArray())

    fun loadSpecsForLocale(specs: MsgSpecs, locale: Locale): MsgSpecs {
        val bundle: ResourceBundle = ResourceBundle.getBundle(bundleName, locale, classLoader)
        return bundle.keys.toList().fold(specs) {
            specs, key -> specs.add(key, locale, bundle.getString(key))
        }
    }

    fun loadSpecs(): MsgSpecs =
            locales.fold(MsgSpecs(mainLocale, locales), this::loadSpecsForLocale)

}
