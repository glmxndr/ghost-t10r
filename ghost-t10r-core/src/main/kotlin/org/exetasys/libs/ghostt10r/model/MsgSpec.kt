package org.exetasys.libs.ghostt10r.model

import java.util.*
import java.util.stream.Collectors
import java.util.stream.Stream

class MsgSpec (
    val key: String,
    val mainLocale: Locale,
    val locales: Set<Locale>
) : HashMap<Locale, String>() {

    /**
     * Check this message spec for errors: format not defined for a certain bundle,
     * incoherent parameter definitions, etc.
     */
    fun validate(): List<MsgSpecError> =
        Stream.of(
            missingParamsInFormats(mainFormat()),
            unknownParamsInFormats(mainFormat()),
            missingFormatsInLocale())
            .flatMap { errs -> errs.stream() }
            .collect(Collectors.toList<MsgSpecError>())

    fun mainFormat(): MsgFormat? = get(mainLocale)?.let { MsgFormat(it) }

    fun mainParams() : Set<String> = mainFormat()?.params ?: Collections.emptySet()

    private fun unknownParamsInFormats(mainFormat: MsgFormat?): List<MsgSpecError> =
        mainFormat?.let{ mf ->
            entries.flatMap { (loc, format) -> missingFrom(MsgFormat(format).params, mf.params)
                    .map { param -> MsgSpecError(key, MsgSpecErrorType.UNKNOWN_PARAM_IN_LOCALE, param, loc) }}}
                ?: ArrayList()

    private fun missingParamsInFormats(mainFormat: MsgFormat?): List<MsgSpecError> =
        mainFormat?.let { mf ->
            entries.flatMap { (loc, format) -> missingFrom(mf.params, MsgFormat(format).params)
                    .map { param -> MsgSpecError(key, MsgSpecErrorType.MISSING_PARAM_IN_LOCALE, param, loc) }}}
                ?: ArrayList()

    private fun missingFormatsInLocale(): List<MsgSpecError> =
        locales
            .filter { !containsKey(it) }
            .map { MsgSpecError(key, MsgSpecErrorType.MISSING_KEY_FOR_LOCALE, key, it) }

    private fun missingFrom (main: Set<String>, toTest: Set<String>): Set<String> {
        val missing: HashSet<String> = HashSet(main)
        toTest.stream().forEach { missing.remove(it) }
        return missing
    }
}