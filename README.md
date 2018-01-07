# Ghost Translator (ghost-t10r)

## The problem(s)

You have a Java/JVM-based language using resource bundles for i18n.

But resource bundles are hard to maintain, even with IDE editors.

Often, you'll miss a locale when updating a message format, or you won't have 
the same parameters in the messages. You also may fail to keep the bundle 
messages and the code. You may also have inconsistencies between the number
of parameters in your messages and the number of parameters passed when
interpolating the message in the code, because MessageFormat#format is not
arity-safe.

It is also not great to refer to the message keys directly in the
code, so you generally end up creating an enum or set of constants to hold
the name of the keys, and now have to keep that enum/constants synced with
the bundles too.

Oh, and resource bundles are ISO-8859-1 by default, and you need to insert
\uXXXX literals in your bundles for all non-ASCII chars, and not all IDEs
help you with that.

## The solution (?)

This library provides the following things:

* resource bundles in UTF-8
* MessageFormat improved by using named params instead of numbers
* generation of a class with one constant per key, providing an arity-safe
  format method
* maven plugin goal to generate this automatically at generate-sources phase
* maven plugin goal to check consistency of the bundle messages between all 
  locales

## Constraints

* JRE/JDK 8+ required

## Example

Please brows the code of the [ghost-t10r-sample](ghost-t10r-sample) module, especially:
* [pom.xml](ghost-t10r-sample/pom.xml) to see sample use of code generation and bundle validation goals
* [src/main/resources](ghost-t10r-sample/src/main/resources) for sample bundles
* [src/test/java](ghost-t10r-sample/src/test/java) for sample use of the generated class
