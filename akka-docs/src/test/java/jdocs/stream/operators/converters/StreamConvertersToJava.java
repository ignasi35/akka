/*
 * Copyright (C) 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.stream.operators.converters;

import akka.NotUsed;
import akka.actor.ActorSystem;
// #import
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.StreamConverters;
import java.util.stream.Stream;
// #import
import akka.testkit.javadsl.TestKit;
import jdocs.AbstractJavaTest;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import akka.stream.javadsl.Source;

import static org.junit.Assert.assertEquals;

/** */
public class StreamConvertersToJava extends AbstractJavaTest {

  static ActorSystem system;
  static Materializer mat;

  @BeforeClass
  public static void setup() {
    system = ActorSystem.create("StreamComvertersToJava");
    mat = Materializer.matFromSystem(system);
  }

  @AfterClass
  public static void tearDown() {
    TestKit.shutdownActorSystem(system);
    system = null;
  }

  @Test
  public void demonstrateConverterToJava8Stream() {
    // #asJavaStream

    Source<Integer, NotUsed> source = Source.range(0, 9).filter(i -> i % 2 == 0);

    Sink<Integer, Stream<Integer>> sink = StreamConverters.<Integer>asJavaStream();

    Stream<Integer> jStream = source.runWith(sink, mat);
    assertEquals(5, jStream.count());

    // #asJavaStream
  }
}
