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

class MessageClassBuilder(
    rscDirs : List<File>,
    bundleName : String,
    keyPrefix:  String,
    mainLocale : Locale,
    locales : Set<Locale>,
    val destDir : File,
    val className : String,
    val classPackage : String)
        : BundleLoader(rscDirs, bundleName, keyPrefix, mainLocale, locales) {

    companion object {
        val LOG : Logger = LoggerFactory.getLogger(MessageClassBuilder::class.java.name)
    }

    private val keyPattern = Regex("[A-Z][A-Z1-9_]+")

    private val localeEvaluatorInterfaceName = "LocaleEvaluator"

    fun makeEnumFile(specs: MsgSpecs): JavaFile {
        val type = makeEnumContent(specs)
        return JavaFile.builder(classPackage, type)
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

        val builder: TypeSpec.Builder = TypeSpec.classBuilder(className)
            .addModifiers(Modifier.PUBLIC)

        specs.entries
            .filter { it.key.startsWith(keyPrefix) }
            .filter { it.key.replaceFirst(keyPrefix, "").matches(keyPattern) }
            .forEach { (key, spec) -> createKeyType(key, spec, builder) }

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

        builder.addType(TypeSpec
            .interfaceBuilder(localeEvaluatorInterfaceName)
            .addMethod(MethodSpec
                .methodBuilder("withLocale")
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .returns(String::class.java)
                .addParameter(Locale::class.java, "locale")
                .build())
            .build())

        return builder.build()
    }

    private fun createKeyType(key: String, spec: MsgSpec, builder: TypeSpec.Builder) {
        val keyName = key.replaceFirst(keyPrefix, "")
        val keyTypeName = createKeyTypeName(keyName)

        val formatMethodBuilder = makeFormatMethod(keyName, spec)
        val parseMethodBuilder = makeParseMethod(key, spec)

        val paramsStr = paramsStrings(spec)

        spec.mainParams()
            .windowed(size = 2, partialWindows = true)
            .forEach { ps ->
                val next : String =
                    if (ps.size == 2) { paramInterfaceName(keyName, ps.get(1)) }
                    else { localeEvaluatorInterfaceName }
                builder.addType(createInterfaceFromTo(keyName, ps.get(0), next))
            }

        builder.addType(
            TypeSpec
                .classBuilder(keyTypeName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .superclass(ClassName.get(classPackage, className))
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

    private fun createKeyTypeName(keyName: String) = camelCase(keyName) + "Type"

    private fun createInterfaceFromTo(
            key : String,
            param1: String,
            nextInterface: String): TypeSpec {
        return TypeSpec
            .interfaceBuilder(paramInterfaceName(key, param1))
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addMethod(MethodSpec
                .methodBuilder("with${param1.capitalize()}")
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .returns(ClassName.get("", nextInterface))
                .addParameter(Object::class.java, param1)
                .build())
            .build()
    }

    private fun paramInterfaceName(key: String, param1: String) =
            "${camelCase(key)}${param1.capitalize()}Setter"

    private fun makeFormatMethod(key: String, spec: MsgSpec): MethodSpec.Builder {
        val keyTypeName = createKeyTypeName(key)
        val params = spec.mainParams()
        val paramsCount = params.size
        val paramsList = params.fold(
            "",
            { acc, param -> "$acc, $param" })

        val returnTypes: List<String> = params.toList().drop(1)
            .map { paramInterfaceName(key, it) }
            .plus(localeEvaluatorInterfaceName)

        val methodNames : List<Pair<String, String>> = ArrayList<Pair<String,String>>()
            .plus(params.toList().map { Pair("with${it.capitalize()}", it) })

        val methodSpecs : List<Triple<String, String, String>> =
            returnTypes
                .zip(methodNames)
                .map { Triple(it.first, it.second.first, it.second.second) }

        val innerMostBlock: CodeBlock.Builder = CodeBlock.builder()
            .add("public String withLocale(Locale locale) {\n")
            .indent()
            .addStatement("String msg = BUNDLES.get(locale).getString($keyTypeName.this.key)")
            .addStatement("msg = replaceParamsByNumbers(msg, params)")
        if (paramsCount == 0) { innerMostBlock
            .addStatement("return msg") }
        else { innerMostBlock
            .addStatement("return \$T.format(msg$paramsList)", MessageFormat::class.java) }
        innerMostBlock
            .unindent()
            .add("}\n")

        val innerBlock = methodSpecs.foldRight(innerMostBlock.build())
            { (retType, methName, argName), acc ->
                CodeBlock.builder()
                    .add("public $retType $methName(${if(argName.isEmpty()) { "" } else { "Object $argName" }}) {\n")
                    .indent()
                    .add("return new $retType() {\n")
                    .indent()
                    .add(acc)
                    .unindent()
                    .add("};\n")
                    .unindent()
                    .add("}\n")
                    .build()
            }

        val firstInnerName =
            if (params.isEmpty()) { localeEvaluatorInterfaceName }
            else { paramInterfaceName(key, params.first()) }
        val firstInnerType = ClassName.get("", firstInnerName)

        return MethodSpec.methodBuilder("format")
            .addModifiers(Modifier.PUBLIC)
            .returns(firstInnerType)
            .addCode("return new $firstInnerName() {\n")
            .addCode(innerBlock)
            .addCode("};\n")
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
