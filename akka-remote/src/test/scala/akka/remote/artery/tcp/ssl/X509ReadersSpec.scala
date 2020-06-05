/*
 * Copyright (C) 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.remote.artery.tcp.ssl

import java.security.cert.X509Certificate

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 *
 */
class X509ReadersSpec extends AnyWordSpec with Matchers {

  import TlsResourcesSpec._

  "X509Readers" must {
    "read a certificate's subject name from the CN" in {
      val island = loadCert("/ssl/island.example.com.crt")
      X509Readers.getAllSubjectNames(island) mustBe (Set("island.example.com"))
    }

    "read both the CN and the subject alternative names" in {
      val serverCert = loadCert("/domain.crt")
      X509Readers.getAllSubjectNames(serverCert) mustBe (Set("akka-remote", "localhost"))
    }

    "read a certificate that has no SAN extension" in {
      // a self-signed CA without SAN
      val selfSigned = loadCert("/ssl/pem/selfsigned-certificate.pem")
      X509Readers.getAllSubjectNames(selfSigned) mustBe (Set("0d207b68-9a20-4ee8-92cb-bf9699581cf8"))
    }

    import akka.util.ccompat.JavaConverters._

    val EMAIL = 1

    "read a certificate that has no DNS entry on the SAN extension" in {
      val noDns: X509Certificate = loadCert("/ssl/x509-spec-no-dns-san.example.com.crt")
      noDns.getSubjectAlternativeNames.asScala.headOption must not be (None)
      noDns.getSubjectAlternativeNames.asScala.size must be(1)
      noDns.getSubjectAlternativeNames.asScala.head.asScala.toList match {
        case EMAIL :: entryValue :: Nil => entryValue must be("foo@example.com")
        case _                          => fail("Expected a SAN of type EMAIL")
      }

      X509Readers.getAllSubjectNames(noDns) mustBe (Set("no-dns-san.example.com"))
    }

    "read a certificate that has no subjects at all" in {
      val noSubject = loadCert("/ssl/x509-spec-no-subjects.example.com.crt")
      X509Readers.getAllSubjectNames(noSubject) mustBe (Set())

      noSubject.getSubjectAlternativeNames.asScala.size must be(1)
      val list = noSubject.getSubjectAlternativeNames.asScala.head.asScala.toList
      list match {
        case EMAIL :: _ =>
        case _          => fail("Expected a SAN of type EMAIL")
      }
      noSubject.getSubjectX500Principal.getName.contains("CN") must be(false)

    }

  }

}
