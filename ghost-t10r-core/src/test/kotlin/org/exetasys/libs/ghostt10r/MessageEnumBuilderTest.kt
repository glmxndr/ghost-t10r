package org.exetasys.libs.ghostt10r

import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.TypeSpec
import java.io.File
import java.util.*

class MessageEnumBuilderTest {

    @org.junit.Test
    fun makeEnumContent() {
        val builder: MessageEnumBuilder = MessageEnumBuilder(
                File("."),
                "MyTestEnumName",
                "org.exetasys.libs.ghost10r.messages",
                "test-ghost-t10r",
                Locale("fr", "FR"),
                "",
                Arrays.asList(
                    Locale("fr", "FR"),
                    Locale("en", "UK"))
        )
        val specs = builder.loadSpecs()
        println(specs)
        val type: TypeSpec = builder.makeEnumContent(specs)
        val content = JavaFile.builder("org.exetasys.msg", type).build().toString()
        println(content)
    }

}