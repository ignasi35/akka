/*
 * Copyright (C) 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.stream.operators.converters

import java.util.stream.Collectors

import akka.NotUsed
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.RunnableGraph
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.StreamConverters
import akka.testkit.AkkaSpec


class StreamConvertersToJava extends AkkaSpec {

  "demonstrate to Java8 streams" in {
    val source: Source[Int, NotUsed] = Source(0 to 9).filter(_ % 2 == 0)
    val akkaStream: RunnableGraph[java.util.stream.Stream[Int]] =
      source.toMat(StreamConverters.asJavaStream())(Keep.right)
    val jStream: java.util.stream.Stream[Int] = akkaStream.run()
    val result:java.lang.Long = jStream.collect(Collectors.counting())

    result should be(5)
  }

}
