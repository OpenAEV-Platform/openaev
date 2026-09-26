/*
Copyright (c) 2021-2024 Filigran SAS

This file is part of the OpenAEV Enterprise Edition ("EE") and is
licensed under the OpenAEV Enterprise Edition License (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

https://github.com/OpenAEV-Platform/openaev/blob/main/LICENSE

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*/

package io.openaev.ee;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.x509.Certificate;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class Pem {

  static {
    // Register Bouncy Castle provider just once
    if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
      Security.addProvider(new BouncyCastleProvider());
    }
  }

  private static final String OPENAEV_CA_PEM =
      "-----BEGIN CERTIFICATE-----\n"
          + "MIIFZjCCA06gAwIBAgIBADANBgkqhkiG9w0BAQsFADBeMRkwFwYDVQQDExBGaWxp\n"
          + "Z3JhbiBDQSBDRVJUMQswCQYDVQQGEwJGUjEOMAwGA1UEBxMFUGFyaXMxETAPBgNV\n"
          + "BAoTCEZpbGlncmFuMREwDwYDVQQLEwhGaWxpZ3JhbjAeFw0yNTAxMTMyMDMxMDNa\n"
          + "Fw0zNTAxMTMyMDMxMDNaMF4xGTAXBgNVBAMTEEZpbGlncmFuIENBIENFUlQxCzAJ\n"
          + "BgNVBAYTAkZSMQ4wDAYDVQQHEwVQYXJpczERMA8GA1UEChMIRmlsaWdyYW4xETAP\n"
          + "BgNVBAsTCEZpbGlncmFuMIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEA\n"
          + "thsWRiSc9uzqlUteifNB8YQQejpLCGhJdHlEI2BJIG62tJz302zm+fzCA5mmDzKB\n"
          + "vWiJelvBHw1ily9T1cRreGGy48frTWZ+/jNI/MqTCXppIfXGX9lQmUm+gHJsFlqJ\n"
          + "uHZYgMJCSpNlTzxuMyk33EpY/RvIl6X4tVJwkl5Ati3coEOnNZlxqXnjG6DJrM0K\n"
          + "zAgQyjQwJKfpG36kHQVfDZc/ae37PIYQM+GyDJQ8wOQWolYWJzM+FxprDZu2ko/R\n"
          + "7+Qqyl0lUIdNfBQyaiPCIGjguIOAlwkHaDjBlGqjkLwJ6f1i3i4lbMQhMqGxpktC\n"
          + "nYwv+Bc7+d9MtpCb935oR76yqR/JRaazP+Q0NGS00OphZu8Dy6oo+NxzAVLIPPSH\n"
          + "vR2sABIkTCwFvZyiy8O6mG5gCzvfzFsCHXGhrmCt9xbEDQscgJJoh35zVa4A1MGa\n"
          + "JQEN3D03ZPNFScKgcY03z4CSyrRxbENp+0zlgmwDse6OLpL/GCjRu6iDuscYQ3A9\n"
          + "KAKFpMRKfxzT9lmdb9U06/nqzE0TrKVlk/0xCuTBAELA6lGFT3+o2ZyVCNmS41Rd\n"
          + "MwZUzcifYMQxxDKoTzHDX8ZqviZSMkDVz+aVCO31PlbYkye97gHPlaGZ9XRvv4Z5\n"
          + "W+MxLqnHFN695rimam9y7uRq9wZqmSstQde3YvwdxG0CAwEAAaMvMC0wDAYDVR0T\n"
          + "BAUwAwEB/zAdBgNVHQ4EFgQU1vCtWXyHnsGgeIoU8K9Ge4hR9lkwDQYJKoZIhvcN\n"
          + "AQELBQADggIBAByvipR+vPP9i55KDgWPoq5BYrWpqcskOyqqwPQVSOZQG+A/QZtP\n"
          + "qmI/2CvJHjpGOd87jCUdEy0hnHomN7DV1flzToU2n8WWJdGmH417CEmbcf0cNAY/\n"
          + "dha911Yg2MAKIzgB9Sh1NqOFR4gKiUQLGC+XFSM3J+YuJk4dusb6sbjRXX7Ijqop\n"
          + "nwvqMjrGXY8u0Wghjb5/M44SmR/Ca2IgWaTZg14nrwiM+d8SlZr3BVCzRC8ps/3g\n"
          + "GqIQhhA3ESdezp0rQ/kRCg5aRgawUj+GGSoO/Y10GT8R4aj1QVOLVK+uqMXUcbzf\n"
          + "wAJssjSZ44avm8JOid5pcQshj7iWZlVJoci0N8559cG8zJ4T4y4KDkf3jFnhhnv+\n"
          + "hQ1EJsD9eVXAuBBEqA27rPDJ5TfQUW6YAUlyf/WVYf8csJAoATgrZjpLj3lPsUDU\n"
          + "npmD5KmwHYpRhGsJDccm9IQ/y6ObyZijcVQPXaoiVZ+9yIA7za2SesPtAwhQjEkc\n"
          + "SuDGh/vlvR5WSN6mhi1lhP8VGnaNyfyD8hADwHofqn5dqx2K8jro/+OhjYCQjE5J\n"
          + "HEqI3OSIHbc81C7AUD16IOWBgkU1V2cwDc0JSmy1XXd0S/kJdBhHv8OwOuAkjSJ6\n"
          + "2mA7AWnryoOIwEo8Yjyag44PYuI05mGnqle01Isb06IGChLIc1rFtvih\n"
          + "-----END CERTIFICATE-----\n";

  /**
   * Trust anchor of the Filigran XTM licenses, pinned in the build and never read from
   * configuration: XTM One returns an XTM license certificate at registration, and whoever controls
   * the XTM One URL could otherwise replace the anchor along with the answer. Copy of {@code
   * XTM_CA_PEM} in XTM One's {@code backend/app/enterprise/xtm_ca.py}; certificate SHA-256
   * 43:6E:0B:82:14:0F:43:CA:0E:15:3F:1B:27:AC:E3:B3:88:F1:9F:24:BC:6C:36:6E:C1:24:91:87:45:BC:40:64,
   * SubjectPublicKeyInfo SHA-256 9ef09cc6ab116344afd1a47ae5b777842b8c44fae78d223440d19bb7bc7d84ec.
   * Only its RSA public key is used, its own validity dates included.
   */
  private static final String XTM_CA_PEM =
      "-----BEGIN CERTIFICATE-----\n"
          + "MIIFZjCCA06gAwIBAgIBADANBgkqhkiG9w0BAQsFADBeMRkwFwYDVQQDExBGaWxp\n"
          + "Z3JhbiBDQSBDRVJUMQswCQYDVQQGEwJGUjEOMAwGA1UEBxMFUGFyaXMxETAPBgNV\n"
          + "BAoTCEZpbGlncmFuMREwDwYDVQQLEwhGaWxpZ3JhbjAeFw0yNjAyMjIyMDIyMTJa\n"
          + "Fw0zNjAyMjIyMDIyMTJaMF4xGTAXBgNVBAMTEEZpbGlncmFuIENBIENFUlQxCzAJ\n"
          + "BgNVBAYTAkZSMQ4wDAYDVQQHEwVQYXJpczERMA8GA1UEChMIRmlsaWdyYW4xETAP\n"
          + "BgNVBAsTCEZpbGlncmFuMIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEA\n"
          + "y3yxjCuiO3RgzKkH0RAbNyo6QcEvr+BSH34VXQVQ7UMyb+02H36lqZYRB7/luCuZ\n"
          + "aEnpc7VmHFH5EU5YuWhcQwfegSJIMVWjbmqnaf07BKnBB7/91qzRr0yxLZGQOm8E\n"
          + "30/frFt6HnRHZ1dGo2uctp2bLDs6D08HKLpplovQlQwOc9JP16geohHACHuXOZZz\n"
          + "AIAVgfrKCV2iGq2I2pApLRirLChTs2Na78Zh+HtfFgq7ZPiDi3nlhsi7f25EDxWW\n"
          + "SdwHKjm0rsHlGatVwvjytX28ROt+OzCteQkjAY69CipLRnZ/hi16hrnrVk2BkZBD\n"
          + "0fn63MTO+rtBKoBIPdK4DumB61VMM1Ea469CXBABZNx+ToEorPKkB/ml04nOHPsj\n"
          + "F7fyxCaCGoKKcaJrWghtxdjDj7P/dkiUjw3qwRdnYAGcUFNVTVdWc5bP0tBgzx6W\n"
          + "98cak1PmpYYh2AMUbcpUT31LDH5zEaaEiAeekLeFHst52OV2XudlGe/5ZUjEK/nw\n"
          + "vlGVuuv80MOk+M1AvuODblkecb2fuRjuI5BxF2xBbuLwACCeSmthviqH5zopnE/e\n"
          + "O6udrbxArClFok270R0nIDavk/0b/Pso/IfptZ0EAJVk/sGscZbIdPqub6xbFBDY\n"
          + "xBFfz7DPoBVVUpwNAbGIoych1f2iOYPx6JxVCsPw4gECAwEAAaMvMC0wDAYDVR0T\n"
          + "BAUwAwEB/zAdBgNVHQ4EFgQUd10XnilRkm1bYCMb+I9FQO0qZ9owDQYJKoZIhvcN\n"
          + "AQELBQADggIBALxeFU1PuiCmIdmBYOzmS+RDPfPxf3rEvEEHJ+LCcflTagldK3Bj\n"
          + "gxSNZ3hDHQ0rxwZx442X0gJMMOv3YwN0jhOHS4qsaTzkKmJt9AflhjoBQZWT7B0/\n"
          + "gVpcBXMVeuzcDZq69s4o6oDfaHhsG24R7MKp12Z0t5P29CW3oKpkDwjXOx2iD625\n"
          + "bkReyydELEPO+qpUbmVelTJ1V/EQSg7PTx9pPFnyxkyiQcKPEWc/+fW5gjDXMXsy\n"
          + "ESn/A9OJxnFU6t84dFbXYLpZhuCn+JlADKqL84+J4JQSf7LU3sTSqLs7a1OyN1F8\n"
          + "TlkBbkSZbk3/t752LTIeoCLjXf81WSlYofvvtNhZX8J4GSqNV8LwlFS/Bfz/nARH\n"
          + "VzfRzLN/2vzbR7RGHBAf6cC8ASkabpw5nSfRmKZMVmR/EIj1A+wk0eMUM1+Lg4Bw\n"
          + "jEs4JyR8nUTF8hXpMxHDkWlAuSd5k2o9nwvB18zjq9eNfVX1H1aUsLnKo+nGym7H\n"
          + "AINFdEbvh4OJixwtX//V3Y24r7Je5eRJ5kTII+t+yEo1HHMOzM6wh1JK6ifbPdNU\n"
          + "mmixNqeEkJwBYtzbwaLOdxEUtv9BMjMJJVGvXB4vcxF+L6XXnFvnKo4JDct1+Ej9\n"
          + "VogsmSWr3DLQJn5bQkOBvWg/7bPPGLXPtZHCwuVFsXZnTGKCWwBOX00B\n"
          + "-----END CERTIFICATE-----\n";

  private static final Pattern SINGLE_CERTIFICATE_PEM =
      Pattern.compile(
          "\\s*-----BEGIN CERTIFICATE-----([A-Za-z0-9+/=\\s]+)-----END CERTIFICATE-----\\s*");

  /** DER string tags Filigran extension values may be wrapped in, as XTM One reads them. */
  private static final byte[] DER_STRING_TAGS = {0x0C, 0x13, 0x16, 0x04};

  public static X509Certificate parseCert(String pem) throws Exception {
    String base64Encoded =
        pem.replace("-----BEGIN CERTIFICATE-----", "")
            .replace("-----END CERTIFICATE-----", "")
            .replaceAll("\\s", ""); // Remove all whitespace and newlines
    byte[] decodedBytes = Base64.getDecoder().decode(base64Encoded);
    try (InputStream is = new ByteArrayInputStream(decodedBytes)) {
      CertificateFactory cf =
          CertificateFactory.getInstance("X.509", BouncyCastleProvider.PROVIDER_NAME);
      return (X509Certificate) cf.generateCertificate(is);
    }
  }

  /**
   * Parses a value that must be exactly one PEM {@code CERTIFICATE} block, surrounded by nothing
   * but whitespace, holding one DER X.509 certificate and nothing after it. Anything else (a second
   * block, a key pasted along, trailing bytes, a PKCS#7 bundle) is refused.
   */
  public static X509Certificate parseSingleCert(String pem) throws Exception {
    Matcher matcher = pem == null ? null : SINGLE_CERTIFICATE_PEM.matcher(pem);
    if (matcher == null || !matcher.matches()) {
      throw new CertificateException("Not exactly one PEM CERTIFICATE block");
    }
    // The certificate factory silently ignores any bytes after the first certificate, and returns
    // the first certificate of a PKCS#7 bundle as well.
    Certificate.getInstance(
        ASN1Primitive.fromByteArray(
            Base64.getDecoder().decode(matcher.group(1).replaceAll("\\s", ""))));
    return parseCert(pem);
  }

  public static X509Certificate getCaCert() throws Exception {
    return parseCert(OPENAEV_CA_PEM);
  }

  public static X509Certificate getXtmCaCert() throws Exception {
    return parseCert(XTM_CA_PEM);
  }

  public static String getExtension(X509Certificate cert, String key) {
    byte[] extensionBytes = cert.getExtensionValue(key);
    byte[] ofRange = Arrays.copyOfRange(extensionBytes, 2, extensionBytes.length);
    return new String(ofRange, StandardCharsets.UTF_8);
  }

  /**
   * Reads a Filigran custom extension as text the way XTM One does: the content of {@code
   * extnValue} is the UTF-8 text itself, possibly wrapped in a DER string (UTF8String,
   * PrintableString, IA5String or OCTET STRING) whose one-byte length covers every byte after it.
   * Any other content, a wrapper with a long-form length included, is read whole, header and all,
   * exactly as XTM One reads it: Filigran writes the text itself, unwrapped. Unlike {@link
   * #getExtension}, the {@code extnValue} OCTET STRING is decoded properly, so a value longer than
   * 127 bytes (a sub-license list, for instance) reads correctly.
   *
   * @return the trimmed text, or {@code null} when the certificate has no such extension
   */
  public static String getExtensionText(X509Certificate cert, String oid) {
    byte[] extensionValue = cert.getExtensionValue(oid);
    if (extensionValue == null) {
      return null;
    }
    byte[] content = ASN1OctetString.getInstance(extensionValue).getOctets();
    if (content.length > 2
        && isDerStringTag(content[0])
        && (content[1] & 0xFF) == content.length - 2) {
      content = Arrays.copyOfRange(content, 2, content.length);
    }
    return new String(content, StandardCharsets.UTF_8).strip();
  }

  private static boolean isDerStringTag(byte tag) {
    for (byte derStringTag : DER_STRING_TAGS) {
      if (derStringTag == tag) {
        return true;
      }
    }
    return false;
  }
}
