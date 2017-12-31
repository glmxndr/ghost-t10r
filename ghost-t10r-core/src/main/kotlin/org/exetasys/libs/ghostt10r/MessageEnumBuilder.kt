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
    val keyPrefix:  String,
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

        specs.entries
            .filter { it.key.startsWith(keyPrefix) }
            .forEach { (key, spec) ->
                val formatMethodBuilder = makeFormatMethod(key, spec)
                val parseMethodBuilder = makeParseMethod(key, spec)
                builder.addEnumConstant(
                    key.replaceFirst(keyPrefix, ""),
                    TypeSpec
                        .anonymousClassBuilder("\$S", key)
                        .addMethod(formatMethodBuilder.build())
                        .addMethod(parseMethodBuilder.build())
                        .build())
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
            populateBundlesBlock
                .addStatement("loadBundleWithLocale(\$S, new \$T(\$S, \$S, \$S))",
                    bundleName, Locale::class.java, loc.language, loc.country, loc.variant)
        }
        builder.addStaticBlock(populateBundlesBlock.build())

        builder.addMethod(MethodSpec.constructorBuilder()
            .addParameter(String::class.java, "key")
            .addStatement("this.\$N = \$N", "key", "key")
            .build())

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

        builder.addMethod(MethodSpec.methodBuilder("replaceParamsByNumbers")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .returns(String::class.java)
            .addParameter(String::class.java, "msg", Modifier.FINAL)
            .addParameter(ParameterizedTypeName.get(List::class.java, String::class.java), "params", Modifier.FINAL)
            .addStatement("String msg = BUNDLES.get(locale).getString(this.key)")
            .beginControlFlow("for (int i = 0; i < max; i++)")
            .addStatement("msg = replaceParamByNumber(msg, params.get(i), i)")
            .endControlFlow()
            .addStatement("return msg")
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
        val paramsStr = paramsStrings(spec)

        val formatBlock: CodeBlock.Builder = CodeBlock.builder()
            .beginControlFlow("return locale ->")
            .addStatement("List<String> params = \$T.asList(\$L)", Arrays::class.java, paramsStr)
            .addStatement("String msg = BUNDLES.get(locale).getString(this.key)")
            .addStatement("msg = replaceParamsByNumbers(msg, params)")

        if (paramsCount == 0) {
            formatBlock.addStatement("return msg")
        }
        else {
            formatBlock.addStatement("return \$T.format(msg$params)", MessageFormat::class.java)
        }
        formatBlock.endControlFlow()

        formatMethodBuilder.addCode(formatBlock.build())
        return formatMethodBuilder
    }


    private fun makeParseMethod(key: String, spec: MsgSpec): MethodSpec.Builder {
        val mapType : ParameterizedTypeName = ParameterizedTypeName
            .get(Map::class.java, String::class.java, Object::class.java)
        val formatMethodBuilder = MethodSpec.methodBuilder("parse")
            .addModifiers(Modifier.PUBLIC)
            .returns(mapType)
            .addParameter(Locale::class.java, "locale")
            .addParameter(String::class.java, "formattedMessage")

        val params = paramsStrings(spec)
        val counter = AtomicInteger()

        val formatBlock: CodeBlock.Builder = CodeBlock.builder()
            .addStatement("List<String> params = \$T.asList(\$L)", Arrays::class.java, params)
            .addStatement("String msg = BUNDLES.get(locale).getString(this.key)")
            .addStatement("msg = replaceParamsByNumbers(msg, params)")
            .addStatement("MessageFormat format = new MessageFormat(msg)")
            .addStatement("Object[] objs = format.parse(formattedMessage)")
            .addStatement("Map<String, Object> result = new HashMap<>()")
        spec.mainFormat()?.params?.forEach { param ->
            formatBlock.addStatement("result.put(\$S, objs[\$L])", param, counter.getAndIncrement())
        }
        formatBlock
            .addStatement("return result")

        formatMethodBuilder.addCode(formatBlock.build())

        return formatMethodBuilder
    }

    private fun paramsStrings(spec: MsgSpec): String {
        return spec.mainFormat()?.params
            ?.map { "\"$it\"" }
            ?.fold("") { acc, param -> if (acc.isBlank()) param else "$acc, $param" }
            ?: ""
    }
}
