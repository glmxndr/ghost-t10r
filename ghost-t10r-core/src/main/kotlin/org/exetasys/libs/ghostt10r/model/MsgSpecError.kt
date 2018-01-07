package org.exetasys.libs.ghostt10r.model

import java.util.*

data class MsgSpecError (
        val key: String,
        val type: MsgSpecErrorType,
        val desc: String,
        val locale: Locale)