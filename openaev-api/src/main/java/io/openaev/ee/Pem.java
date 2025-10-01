/*
Copyright (c) 2021-2024 Filigran SAS

This file is part of the OpenAEV Enterprise Edition ("EE") and is
licensed under the OpenAEV Enterprise Edition License (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

https://github.com/OpenAEV-Platform/openaev/blob/master/LICENSE

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*/

package io.openaev.ee;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Base64;
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

  public static X509Certificate getCaCert() throws Exception {
    return parseCert(OPENAEV_CA_PEM);
  }

  public static String getExtension(X509Certificate cert, String key) {
    byte[] extensionBytes = cert.getExtensionValue(key);
    byte[] ofRange = Arrays.copyOfRange(extensionBytes, 2, extensionBytes.length);
    return new String(ofRange, StandardCharsets.UTF_8);
  }
}
