package org.exetasys.libs.ghostt10r

import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.TypeSpec
import java.io.File
import java.util.*
import kotlin.collections.HashSet

class MessageClassBuilderTest {

    @org.junit.Test
    fun makeEnumContent() {
        val builder: MessageClassBuilder = MessageClassBuilder(
            Arrays.asList(File("src/test/resources")),
            "test-ghost-t10r",
            "",
            Locale("fr", "FR"),
            HashSet(Arrays.asList(
                    Locale("fr", "FR"),
                    Locale("en", "UK"))),
            File("."),
            "MyTestEnumName",
            "org.exetasys.libs.ghost10r.messages")
        val specs = builder.loadSpecs()
        println(specs)
        val type: TypeSpec = builder.makeEnumContent(specs)
        val content = JavaFile.builder("org.exetasys.msg", type).build().toString()
        println(content)
    }

}