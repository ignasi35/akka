/*
 * Copyright (C) 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.stream.operators.converters

import java.util.stream
import java.util.stream.Collectors

import akka.NotUsed
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.RunnableGraph
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.StreamConverters
import akka.testkit.AkkaSpec


class StreamConvertersToJava extends AkkaSpec {

  "demonstrate materialization to Java8 streams" in {
    //#asJavaStream
    val source: Source[Int, NotUsed] = Source(0 to 9).filter(_ % 2 == 0)

    val sink: Sink[Int, stream.Stream[Int]] = StreamConverters.asJavaStream[Int]()

    val jStream: java.util.stream.Stream[Int] = source.runWith(sink)
    jStream.count should be(5)

    //#asJavaStream
  }

}
