package org.exetasys.libs.ghostt10r

import com.squareup.javapoet.*
import org.exetasys.libs.ghostt10r.model.MsgSpec
import org.exetasys.libs.ghostt10r.model.MsgSpecs
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.text.MessageFormat
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Function
import javax.lang.model.element.Modifier

class MessageEnumBuilder(
    val destDir : File,
    val enumName : String,
    val enumPackage : String,
    val bundleName : String,
    val mainLocale : Locale,
    val locales : List<Locale>
) {

    companion object {
        val LOG : Logger = LoggerFactory.getLogger(MessageEnumBuilder::class.java.name)
    }

    val DOLLAR = "\$"

    fun loadSpecsForLocale(specs: MsgSpecs, locale: Locale): MsgSpecs {
        val bundle: ResourceBundle = ResourceBundle.getBundle(bundleName, locale)
        return bundle.keys.toList().fold(specs) {
            specs, key -> specs.add(key, locale, bundle.getString(key))
        }
    }

    fun loadSpecs(): MsgSpecs =
            locales.fold(MsgSpecs(mainLocale, locales), this::loadSpecsForLocale)

    fun makeEnumContent(specs: MsgSpecs): TypeSpec {
        val builder: TypeSpec.Builder = TypeSpec.enumBuilder(enumName)
            .addModifiers(Modifier.PUBLIC)

        println("makeEnumContent: ${specs.keys}")

        specs.entries.forEach { (key, spec) ->
            val emptyBlock: CodeBlock = CodeBlock.builder().build()

            val formatMethodBuilder = makeFormatMethod(key, spec)

            builder.addEnumConstant(key,
                TypeSpec
                    .anonymousClassBuilder(emptyBlock)
                    .addMethod(formatMethodBuilder.build())
                    .build()
            )
        }

        val bundlesTypeName = ParameterizedTypeName.get(
                Map::class.java,
                Locale::class.java,
                ResourceBundle::class.java)
        val bundlesField = FieldSpec
                .builder(bundlesTypeName,"BUNDLES", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer("new \$T()", HashMap::class.java)
                .build()
        builder.addField(bundlesField)

        val populateBundlesBlock = CodeBlock.builder()
        //, $T.getBundle($S, new $T($S, $S))
        locales.forEach { loc ->
            populateBundlesBlock.add("loadBundleWithLocale(\$S, new \$T(\$S, \$S, \$S));\n", bundleName, Locale::class.java, loc.language, loc.country, loc.variant)
        }
        builder.addStaticBlock(populateBundlesBlock.build())

        builder.addMethod(MethodSpec.methodBuilder("loadBundleWithLocale")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .addParameter(String::class.java, "bundleName", Modifier.FINAL)
                .addParameter(Locale::class.java, "locale", Modifier.FINAL)
                .addCode("BUNDLES.put(locale, ResourceBundle.getBundle(bundleName, locale));\n")
                .build())

        builder.addMethod(MethodSpec.methodBuilder("replaceParamByNumber")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .returns(String::class.java)
                .addParameter(String::class.java, "message", Modifier.FINAL)
                .addParameter(String::class.java, "param", Modifier.FINAL)
                .addParameter(Integer::class.java, "number", Modifier.FINAL)
                .addCode("""return message.replaceAll("\\{" + param + "([},])", "{" + number + "$$1");""")
                .build())

        return builder.build()
    }

    private fun makeFormatMethod(key: String, spec: MsgSpec): MethodSpec.Builder {
        val formatMethodBuilder = MethodSpec.methodBuilder("format")
                .addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(Function::class.java, Locale::class.java, String::class.java))

        spec.mainFormat()?.let { mf ->
            mf.params.forEach { param -> formatMethodBuilder.addParameter(Object::class.java, param) }
        }

        val paramsCount = spec.mainFormat()?.params?.size ?: 0

        val params = spec.mainFormat()?.params?.fold("") { acc, param -> "$acc, $param" } ?: ""

        val formatBlock: CodeBlock.Builder = CodeBlock.builder()
            .add("return locale -> {\n")
            .indent()
            .add("String msg = BUNDLES.get(locale).getString(\"\$L\");\n", key)


        val counter = AtomicInteger()
        spec.mainFormat()?.params?.forEach { param ->
            formatBlock.add("msg = replaceParamByNumber(msg, \$S, \$L);\n", param, counter.getAndIncrement())
        }

        if (paramsCount == 0) {
            formatBlock.add("return msg;\n")
        }
        else {
            formatBlock.add("return \$T.format(msg$params);\n", MessageFormat::class.java)
        }
        formatBlock
            .unindent()
            .add("};\n")

        formatMethodBuilder.addCode(formatBlock.build())

        return formatMethodBuilder
    }

}
