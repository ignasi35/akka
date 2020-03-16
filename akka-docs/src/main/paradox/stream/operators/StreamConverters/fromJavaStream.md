# StreamConverters.fromJavaStream

Create a source that wraps a Java 8 `Stream`.

@ref[Additional Sink and Source converters](../index.md#additional-sink-and-source-converters)

@@@div { .group-scala }

## Signature

@@signature [Source.scala](/akka-stream/src/main/scala/akka/stream/scaladsl/StreamConverters.scala) { #fromJavaStream }

@@@

## Description

TODO: We would welcome help on contributing descriptions and examples, see: https://github.com/akka/akka/issues/25646

## Example

Here is an example of the creation of a `Source` created from a `java.util.stream.Stream`. 

Scala
:   @@snip [StreamConvertersToJava.scala](/akka-docs/src/test/scala/docs/stream/operators/converters/StreamConvertersToJava.scala) { #import #fromJavaStream }

Java
:   @@snip [StreamConvertersToJava.java](/akka-docs/src/test/java/jdocs/stream/operators/converters/StreamConvertersToJava.java) { #import #fromJavaStream }

