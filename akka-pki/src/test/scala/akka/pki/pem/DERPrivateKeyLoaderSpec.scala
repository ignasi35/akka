/*
 * Copyright (C) 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.pki.pem

import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.security.PrivateKey

import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class DERPrivateKeyLoaderSpec extends AnyWordSpec with Matchers with EitherValues {

  // more info at https://docs.oracle.com/javase/7/docs/technotes/tools/windows/keytool.html

  "The DER Private Key loader" should {
    "decode the same key in PKCS#1 and PKCS#8 formats" in {
      val pkcs1 = load("pkcs1.pem")
      val pkcs8 = load("RSA-pkcs8.pem")
      pkcs1 should ===(pkcs8)
      pkcs8.getAlgorithm should be("RSA")
    }

    "decode EC keys on PKCS#1" ignore {
      val privateKey = load("EC-pkcs1.pem")
      privateKey.getAlgorithm should be("EC")
    }

    "decode EC keys on PKCS#8" in {
      val privateKey = load("EC-pkcs8.pem")
      privateKey.getAlgorithm should be("EC")
    }

    "parse multi primes" in {
      val pk = load("multi-prime-pkcs1.pem")
      pk.getAlgorithm should be("RSA")
      // Not much we can verify here - I actually think the default JDK security implementation ignores the extra
      // primes, and it fails to parse a multi-prime PKCS#8 key
    }

    "fail on unsupported PEM contents (Certificates are not private keys)" in {
      assertThrows[PEMLoadingException] {
        load("certificate.pem")
      }
    }

  }

  private def load(resource: String): PrivateKey = {
    val derData: PEMDecoder.DERData = loadDerData(resource)
    DERPrivateKeyLoader.load(derData)
  }

  private def loadDerData(resource: String) = {
    val resourceUrl = getClass.getClassLoader.getResource(resource)
    resourceUrl.getProtocol should ===("file")
    val path = new File(resourceUrl.toURI).toPath
    val bytes = Files.readAllBytes(path)
    val str = new String(bytes, Charset.forName("UTF-8"))
    val derData = PEMDecoder.decode(str)
    derData
  }

}
