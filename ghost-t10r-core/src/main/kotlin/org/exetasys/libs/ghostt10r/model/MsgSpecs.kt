package org.exetasys.libs.ghostt10r.model

import org.apache.commons.collections4.ListUtils
import org.exetasys.libs.ghostt10r.MessageClassBuilder
import java.util.*

class MsgSpecs(
    val mainLocale: Locale,
    val locales: Set<Locale>
) : HashMap<String, MsgSpec>() {

    fun add(key: String, loc: Locale, format: String): MsgSpecs {
        val spec : MsgSpec = get(key) ?: MsgSpec(key, mainLocale, locales)
        put(key, spec)
        println("BEFORE add: key=$key ||| loc=$loc ||| spec=$spec ||| specs=$this")
        if (spec.containsKey(loc)) {
            MessageClassBuilder.LOG.warn("Duplicate definition for: {} in locale {}", key, loc)
        }
        spec.put(loc, format)
        println("AFTER  add: key=$key ||| loc=$loc ||| spec=$spec ||| specs=$this")
        return this
    }

    fun errors(): List<MsgSpecError> {
        return values
                .map { it.validate() }
                .reduce { acc, errs -> ListUtils.union(acc, errs) }
    }
}
