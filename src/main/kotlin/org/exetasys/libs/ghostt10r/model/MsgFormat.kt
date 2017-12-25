package org.exetasys.libs.ghostt10r.model

import java.util.*

class MsgFormat (val format: String) {
    val params: Set<String> = run {
        val params = TreeSet<String>()
        println(format)
        Regex("\\{[a-zA-Z-]+?[},]")
            .findAll(format)
            .toList()
            .map { it.value.replace(Regex("^\\{|[},].*$"), "") }
            .forEach { params.add(it) }
        params
    }
}