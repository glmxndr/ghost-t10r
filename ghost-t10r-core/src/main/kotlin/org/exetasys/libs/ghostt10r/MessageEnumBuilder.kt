package org.exetasys.libs.ghostt10r

import com.squareup.javapoet.*
import org.exetasys.libs.ghostt10r.model.MsgSpec
import org.exetasys.libs.ghostt10r.model.MsgSpecs
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.net.URLClassLoader
import java.text.MessageFormat
import java.text.ParseException
import java.util.*
import java.util.function.Function
import javax.lang.model.element.Modifier

class MessageEnumBuilder(
    val rscDirs : List<File>,
    val bundleName : String,
    val keyPrefix:  String,
    val mainLocale : Locale,
    val locales : Set<Locale>,
    val destDir : File,
    val enumName : String,
    val enumPackage : String
) {

    companion object {
        val LOG : Logger = LoggerFactory.getLogger(MessageEnumBuilder::class.java.name)
    }

    private val keyPattern = Regex("[A-Z][A-Z1-9_]+")
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

    fun makeEnumFile(specs: MsgSpecs): JavaFile {
        val type = makeEnumContent(specs)
        return JavaFile.builder(enumPackage, type)
            .skipJavaLangImports(true)
            .build()
    }

    fun writeEnumFile(specs: MsgSpecs) {
        makeEnumFile(specs).writeTo(destDir)
    }

    fun camelCase(key: String): String = key
        .split("_")
        .map { it.toLowerCase() }
        .map { it.capitalize() }
        .fold("") { a, b -> a + b }

    fun makeEnumContent(specs: MsgSpecs): TypeSpec {

        val builder: TypeSpec.Builder = TypeSpec.classBuilder(enumName)
            .addModifiers(Modifier.PUBLIC)

        println("makeEnumContent: ${specs.keys}")

        specs.entries
            .filter { it.key.startsWith(keyPrefix) }
            .filter { it.key.replaceFirst(keyPrefix, "").matches(keyPattern) }
            .forEach { (key, spec) ->
                val formatMethodBuilder = makeFormatMethod(key, spec)
                val parseMethodBuilder = makeParseMethod(key, spec)
                val keyName = key.replaceFirst(keyPrefix, "")
                val keyTypeName = camelCase(keyName) + "Type"

                val paramsStr = paramsStrings(spec)

                builder.addType(
                    TypeSpec
                        .classBuilder(keyTypeName)
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .superclass(ClassName.get(enumPackage, enumName))
                        .addField(FieldSpec.builder(String::class.java, "key")
                            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                            .initializer("\$S", key)
                            .build())
                        .addField(FieldSpec.builder(ParameterizedTypeName.get(
                                    List::class.java,
                                    String::class.javaObjectType),
                                "params")
                            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                            .initializer("\$T.asList(\$L)", Arrays::class.java, paramsStr)
                            .build())
                        .addMethod(formatMethodBuilder.build())
                        .addMethod(parseMethodBuilder.build())
                        .build())

                builder.addField(FieldSpec
                    .builder(ClassName.get("", keyTypeName), keyName)
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                    .initializer("new \$L()", keyTypeName)
                    .build())
            }

        val bundlesTypeName = ParameterizedTypeName.get(
                Map::class.java,
                Locale::class.java,
                ResourceBundle::class.java)
        builder.addField(FieldSpec
                .builder(bundlesTypeName,"BUNDLES")
                .addModifiers(Modifier.PROTECTED, Modifier.STATIC, Modifier.FINAL)
                .initializer("new \$T()", HashMap::class.java)
                .build())

        val populateBundlesBlock = CodeBlock.builder()
        //, $T.getBundle($S, new $T($S, $S))
        locales.forEach { loc ->
            populateBundlesBlock
                .addStatement("loadBundleWithLocale(\$S, new \$T(\$S, \$S, \$S))",
                    bundleName, Locale::class.java, loc.language, loc.country, loc.variant)
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

        builder.addMethod(MethodSpec.methodBuilder("replaceParamsByNumbers")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .returns(String::class.java)
            .addParameter(String::class.java, "msg", Modifier.FINAL)
            .addParameter(ParameterizedTypeName.get(List::class.java, String::class.java), "params", Modifier.FINAL)
            .addStatement("String result = msg")
            .addStatement("int max = params.size()")
            .beginControlFlow("for (int i = 0; i < max; i++)")
            .addStatement("result = replaceParamByNumber(result, params.get(i), i)")
            .endControlFlow()
            .addStatement("return result")
            .build())

        builder.addMethod(makeBaseParseMethod().build())

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
            .addStatement("String msg = BUNDLES.get(locale).getString(this.key)")
            .addStatement("msg = replaceParamsByNumbers(msg, params)")

        if (paramsCount == 0) {
            formatBlock.addStatement("return msg")
        }
        else {
            formatBlock.addStatement("return \$T.format(msg$params)", MessageFormat::class.java)
        }

        formatBlock
            .unindent()
            .add("};\n")

        formatMethodBuilder.addCode(formatBlock.build())
        return formatMethodBuilder
    }

    private fun makeBaseParseMethod(): MethodSpec.Builder {
        val mapType : ParameterizedTypeName = ParameterizedTypeName
                .get(Map::class.java, String::class.java, Object::class.java)
        val formatMethodBuilder = MethodSpec.methodBuilder("parse")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .returns(mapType)
            .addException(ParseException::class.java)
            .addParameter(String::class.java, "key")
            .addParameter(ParameterizedTypeName.get(List::class.java, String::class.java), "params")
            .addParameter(Locale::class.java, "locale")
            .addParameter(String::class.java, "formattedMessage")

        val formatBlock: CodeBlock.Builder = CodeBlock.builder()
                .addStatement("String msg = BUNDLES.get(locale).getString(key)")
                .addStatement("msg = replaceParamsByNumbers(msg, params)")
                .addStatement("MessageFormat format = new MessageFormat(msg)")
                .addStatement("Object[] objs = format.parse(formattedMessage)")
                .addStatement("Map<String, Object> result = new HashMap<>()")
                .beginControlFlow("for (int i = 0; i < params.size(); i++)")
                .addStatement("result.put(params.get(i), objs[i])")
                .endControlFlow()
                .addStatement("return result")

        formatMethodBuilder.addCode(formatBlock.build())

        return formatMethodBuilder
    }


    private fun makeParseMethod(key: String, spec: MsgSpec): MethodSpec.Builder {
        val mapType : ParameterizedTypeName = ParameterizedTypeName
            .get(Map::class.java, String::class.java, Object::class.java)
        return MethodSpec.methodBuilder("parse")
            .addModifiers(Modifier.PUBLIC)
            .returns(mapType)
            .addException(ParseException::class.java)
            .addParameter(Locale::class.java, "locale")
            .addParameter(String::class.java, "formattedMessage")
            .addStatement("return parse(key, params, locale, formattedMessage)")
    }

    private fun paramsStrings(spec: MsgSpec): String {
        return spec.mainFormat()?.params
            ?.map { "\"$it\"" }
            ?.fold("") { acc, param -> if (acc.isBlank()) param else "$acc, $param" }
            ?: ""
    }
}
