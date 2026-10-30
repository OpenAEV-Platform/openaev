package io.openaev.utils.fixtures;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A GCP service account key file that the Google Auth library can actually sign with.
 *
 * <p>The placeholder of {@link SecretStoreRequestFixture#GCP_PRIVATE_KEY_JSON} is enough for the
 * handlers, which only store it, but minting a token signs a JWT with the private key, so a probe
 * driven against a mocked token endpoint needs a real RSA key.
 */
public final class GcpServiceAccountKeyFixture {

  public static final String GCP_SERVICE_ACCOUNT_EMAIL =
      "openaev-probe@openaev-simulation.iam.gserviceaccount.com";
  public static final String GCP_KEY_FILE_TOKEN_URI = "https://oauth2.googleapis.com/token";

  private GcpServiceAccountKeyFixture() {}

  /**
   * Builds a key file signed by a freshly generated RSA key.
   *
   * @param tokenUri the {@code token_uri} the key file declares
   * @return the key file content
   */
  public static byte[] createServiceAccountKeyJson(String tokenUri) {
    Map<String, String> keyFile = new LinkedHashMap<>();
    keyFile.put("type", "service_account");
    keyFile.put("project_id", "openaev-simulation");
    keyFile.put("private_key_id", "openaev-probe-key");
    keyFile.put("private_key", generatePrivateKeyPem());
    keyFile.put("client_email", GCP_SERVICE_ACCOUNT_EMAIL);
    keyFile.put("client_id", "123456789012345678901");
    keyFile.put("token_uri", tokenUri);
    try {
      return new ObjectMapper().writeValueAsString(keyFile).getBytes(UTF_8);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String generatePrivateKeyPem() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      byte[] pkcs8 = generator.generateKeyPair().getPrivate().getEncoded();
      return "-----BEGIN PRIVATE KEY-----\n"
          + Base64.getMimeEncoder(64, "\n".getBytes(UTF_8)).encodeToString(pkcs8)
          + "\n-----END PRIVATE KEY-----\n";
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
