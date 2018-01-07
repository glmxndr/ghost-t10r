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
        if (spec.containsKey(loc)) {
            MessageClassBuilder.LOG.warn("Duplicate definition for: {} in locale {}", key, loc)
        }
        spec.put(loc, format)
        return this
    }

    fun errors(): List<MsgSpecError> {
        return values
                .map { it.validate() }
                .reduce { acc, errs -> ListUtils.union(acc, errs) }
    }
}
