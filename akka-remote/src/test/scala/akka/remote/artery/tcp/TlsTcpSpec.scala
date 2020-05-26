/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.remote.artery
package tcp

import java.security.NoSuchAlgorithmException

import akka.actor.ActorIdentity
import akka.actor.ActorPath
import akka.actor.ActorRef
import akka.actor.Identify
import akka.actor.RootActorPath
import akka.actor.setup.ActorSystemSetup
import akka.remote.artery.tcp.ssl.CipherSuiteSupportCheck
import akka.testkit.EventFilter
import akka.testkit.ImplicitSender
import akka.testkit.TestActors
import akka.testkit.TestProbe
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSession

import scala.concurrent.duration._

class TlsTcpWithDefaultConfigSpec extends TlsTcpSpec(ConfigFactory.empty())

class TlsTcpWithSHA1PRNGSpec
    extends TlsTcpSpec(ConfigFactory.parseString("""
    akka.remote.artery.ssl.config-ssl-engine {
      random-number-generator = "SHA1PRNG"
      enabled-algorithms = ["TLS_DHE_RSA_WITH_AES_256_GCM_SHA384"]
    }
    """))

class TlsTcpWithDefaultRNGSecureSpec
    extends TlsTcpSpec(ConfigFactory.parseString("""
    akka.remote.artery.ssl.config-ssl-engine {
      random-number-generator = ""
      enabled-algorithms = ["TLS_DHE_RSA_WITH_AES_256_GCM_SHA384"]
    }
    """))

class TlsTcpWithCrappyRSAWithMD5OnlyHereToMakeSureThingsWorkSpec
    extends TlsTcpSpec(ConfigFactory.parseString("""
    akka.remote.artery.ssl.config-ssl-engine {
      random-number-generator = ""
      enabled-algorithms = [""SSL_RSA_WITH_NULL_MD5""]
    }
    """))

class TlsTcpWithCloudNativeSslEngineSpec
    extends TlsTcpSpec(ConfigFactory.parseString(s"""
    akka.remote.artery.ssl {
       ssl-engine-provider = akka.remote.artery.tcp.ssl.CloudNativeSSLEngineProvider
       cloud-native-ssl-engine {
         key-file = ${getClass.getClassLoader.getResource("ssl/pem/pkcs1.pem").getPath}
         cert-file = ${getClass.getClassLoader.getResource("ssl/pem/certificate.pem").getPath}
         ca-cert-file = ${getClass.getClassLoader.getResource("ssl/pem/certificate.pem").getPath}
       }
    }
    """))

object TlsTcpSpec {

  lazy val config: Config = {
    ConfigFactory.parseString(s"""
      akka.remote.artery {
        transport = tls-tcp
        large-message-destinations = [ "/user/large" ]
      }
    """)
  }

}

abstract class TlsTcpSpec(config: Config)
    extends ArteryMultiNodeSpec(config.withFallback(TlsTcpSpec.config))
    with ImplicitSender {

  val systemB = newRemoteSystem(name = Some("systemB"))
  val addressB = address(systemB)
  val rootB = RootActorPath(addressB)

  def isSupported: Boolean = {
    try {
      CipherSuiteSupportCheck.isSupported(system, "akka.remote.artery.ssl.cloud-native-ssl-engine").map(_ => true).get
      CipherSuiteSupportCheck.isSupported(system, "akka.remote.artery.ssl.config-ssl-engine").map(_ => true).get
    } catch {
      case e @ (_: IllegalArgumentException | _: NoSuchAlgorithmException) =>
        info(e.toString)
        false
    }
  }

  def identify(path: ActorPath): ActorRef = {
    system.actorSelection(path) ! Identify(path.name)
    expectMsgType[ActorIdentity].ref.get
  }

  def testDelivery(echoRef: ActorRef): Unit = {
    echoRef ! "ping-1"
    expectMsg("ping-1")

    // and some more
    (2 to 10).foreach { n =>
      echoRef ! s"ping-$n"
    }
    receiveN(9) should equal((2 to 10).map(n => s"ping-$n"))
  }

  "Artery with TLS/TCP" must {

    if (isSupported) {

      "deliver messages" in {
        systemB.actorOf(TestActors.echoActorProps, "echo")
        val echoRef = identify(rootB / "user" / "echo")
        testDelivery(echoRef)
      }

      "deliver messages over large messages stream" in {
        systemB.actorOf(TestActors.echoActorProps, "large")
        val echoRef = identify(rootB / "user" / "large")
        testDelivery(echoRef)
      }

    } else {
      "not be run when the cipher is not supported by the platform this test is currently being executed on" in {
        pending
      }
    }
  }

}

class TlsTcpWithHostnameVerificationSpec
    extends ArteryMultiNodeSpec(ConfigFactory.parseString("""
    akka.remote.artery.ssl.config-ssl-engine {
      hostname-verification = on
    }
    akka.remote.use-unsafe-remote-features-outside-cluster = on

    akka.loggers = ["akka.testkit.TestEventListener"]
    """).withFallback(TlsTcpSpec.config))
    with ImplicitSender {

  "Artery with TLS/TCP and hostname-verification=on" must {
    "fail when the name in the server certificate does not match" in {
      // this test only makes sense with tls-tcp transport
      if (!arteryTcpTlsEnabled())
        pending

      val systemB = newRemoteSystem(
        // The subjectAltName is 'localhost', so connecting to '127.0.0.1' should not
        // work when using hostname verification:
        extraConfig = Some("""akka.remote.artery.canonical.hostname = "127.0.0.1""""),
        name = Some("systemB"))

      val addressB = address(systemB)
      val rootB = RootActorPath(addressB)

      systemB.actorOf(TestActors.echoActorProps, "echo")
      // The detailed warning message is either 'General SSLEngine problem'
      // or 'No subject alternative names matching IP address 127.0.0.1 found'
      // depending on JRE version.
      EventFilter
        .warning(
          pattern =
            "outbound connection to \\[akka://systemB@127.0.0.1:.*" +
            "Upstream failed, cause: SSLHandshakeException: .*",
          occurrences = 3)
        .intercept {
          system.actorSelection(rootB / "user" / "echo") ! Identify("echo")
        }
      expectNoMessage(2.seconds)
      systemB.terminate()
    }
    "succeed when the name in the server certificate matches" in {
      if (!arteryTcpTlsEnabled())
        pending

      val systemB = newRemoteSystem(
        extraConfig = Some("""
          // The subjectAltName is 'localhost', so this is how we want to be known:
          akka.remote.artery.canonical.hostname = "localhost"

          // Though we will still bind to 127.0.0.1 (make sure it's not ipv6)
          akka.remote.artery.bind.hostname = "127.0.0.1"
        """),
        name = Some("systemB"))

      val addressB = address(systemB)
      val rootB = RootActorPath(addressB)

      systemB.actorOf(TestActors.echoActorProps, "echo")
      system.actorSelection(rootB / "user" / "echo") ! Identify("echo")
      val id = expectMsgType[ActorIdentity]

      id.ref.get ! "42"
      expectMsg("42")

      systemB.terminate()
    }
  }
}

class TlsTcpWithActorSystemSetupSpec extends ArteryMultiNodeSpec(TlsTcpSpec.config) with ImplicitSender {

  val sslProviderServerProbe = TestProbe()
  val sslProviderClientProbe = TestProbe()

  val sslProviderSetup = SSLEngineProviderSetup(sys =>
    new SSLEngineProvider {
      val delegate = new ssl.ConfigSSLEngineProvider(sys)
      override def createServerSSLEngine(hostname: String, port: Int): SSLEngine = {
        sslProviderServerProbe.ref ! "createServerSSLEngine"
        delegate.createServerSSLEngine(hostname, port)
      }

      override def createClientSSLEngine(hostname: String, port: Int): SSLEngine = {
        sslProviderClientProbe.ref ! "createClientSSLEngine"
        delegate.createClientSSLEngine(hostname, port)
      }
      override def verifyClientSession(hostname: String, session: SSLSession): Option[Throwable] = delegate.verifyClientSession(hostname, session)
      override def verifyServerSession(hostname: String, session: SSLSession): Option[Throwable] =delegate.verifyServerSession(hostname, session)
    })

  val systemB = newRemoteSystem(name = Some("systemB"), setup = Some(ActorSystemSetup(sslProviderSetup)))
  val addressB = address(systemB)
  val rootB = RootActorPath(addressB)

  "Artery with TLS/TCP with SSLEngineProvider defined via Setup" must {
    "use the right SSLEngineProvider" in {
      if (!arteryTcpTlsEnabled())
        pending

      systemB.actorOf(TestActors.echoActorProps, "echo")
      val path = rootB / "user" / "echo"
      system.actorSelection(path) ! Identify(path.name)
      val echoRef = expectMsgType[ActorIdentity].ref.get
      echoRef ! "ping-1"
      expectMsg("ping-1")

      sslProviderServerProbe.expectMsg("createServerSSLEngine")
      sslProviderClientProbe.expectMsg("createClientSSLEngine")
    }
  }
}
