package io.openaev.service.connector_instances;

import static io.openaev.database.model.SettingKeys.XTM_COMPOSER_PUBLIC_KEY;

import io.openaev.database.model.Setting;
import io.openaev.service.connectors.XtmComposerService;
import java.nio.ByteBuffer;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class XtmComposerEncryptionService implements EncryptionService {

  private String cachedRsaPublicKey;
  private Instant cacheExpiry;
  private static final Duration CACHE_TTL = Duration.ofHours(24);

  private final XtmComposerService xtmComposerService;

  /**
   * Function used to encrypt plain text
   *
   * @param plainText plain text to encrypt
   * @return plain text encrypted
   * @throws Exception
   */
  @Override
  public String encrypt(String plainText) throws Exception {
    String rsaPublicKey = getRsaPublicKey();
    return encryptValue(plainText, rsaPublicKey);
  }

  @Override
  public String decrypt(String encryptedText) {
    log.debug("XTM Composer handles the decryption part");
    return encryptedText;
  }

  private boolean isExpired() {
    return cacheExpiry == null || Instant.now().isAfter(cacheExpiry);
  }

  /**
   * Retrieves the RSA public key from cache or fetches a fresh one if the cache has expired.
   *
   * @return the RSA public key as a PEM-formatted string
   */
  private String getRsaPublicKey() {
    if (cachedRsaPublicKey == null || isExpired()) {
      Map<String, Setting> xtmComposerInformation = xtmComposerService.getXtmComposerSettings();
      cachedRsaPublicKey = xtmComposerInformation.get(XTM_COMPOSER_PUBLIC_KEY.key()).getValue();
      cacheExpiry = Instant.now().plus(CACHE_TTL);
    }
    return cachedRsaPublicKey;
  }

  /**
   * Parses a PEM-formatted RSA public key string into a PublicKey object.
   *
   * @param rsaPublicKeyPEM the RSA public key in PEM format
   * @return the parsed PublicKey object
   * @throws Exception if parsing fails
   */
  private PublicKey parsePublicKey(String rsaPublicKeyPEM) throws Exception {
    // Remove PEM headers/footers and whitespace
    String cleanedKey =
        rsaPublicKeyPEM
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("-----BEGIN RSA PUBLIC KEY-----", "")
            .replace("-----END RSA PUBLIC KEY-----", "")
            .replaceAll("\\s", ""); // Remove all whitespace

    X509EncodedKeySpec keySpecPublic =
        new X509EncodedKeySpec(Base64.getDecoder().decode(cleanedKey));
    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    return keyFactory.generatePublic(keySpecPublic);
  }

  private byte[] generateRandomBytes(int length) {
    SecureRandom secureRandom = new SecureRandom();
    byte[] bytes = new byte[length];
    secureRandom.nextBytes(bytes);
    return bytes;
  }

  /**
   * Encrypts text using AES-GCM cipher mode.
   *
   * @param text the plaintext to encrypt
   * @param key the AES encryption key
   * @param iv the initialization vector
   * @return the encrypted bytes with authentication tag
   * @throws Exception if encryption fails
   */
  private byte[] aesEncrypt(String text, byte[] key, byte[] iv) throws Exception {
    // Create AES key from bytes
    SecretKeySpec secretKey = new SecretKeySpec(key, "AES");

    // Initialize cipher in GCM mode
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

    // GCM parameters: 128-bit authentication tag, with the IV
    GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);

    // Initialize for encryption
    cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);

    // Encrypt and return (includes auth tag automatically)
    return cipher.doFinal(text.getBytes("UTF-8"));
  }

  /**
   * Concatenates multiple byte arrays into a single array.
   *
   * @param arrays the byte arrays to concatenate
   * @return a single byte array containing all input arrays
   */
  private byte[] concatenateBytes(byte[]... arrays) {
    // Calculate total length
    int totalLength = 0;
    for (byte[] array : arrays) {
      totalLength += array.length;
    }

    // Create buffer and copy all arrays
    ByteBuffer buffer = ByteBuffer.allocate(totalLength);
    for (byte[] array : arrays) {
      buffer.put(array);
    }

    return buffer.array();
  }

  /**
   * Encrypts a value using hybrid RSA/AES encryption.
   *
   * @param value the plaintext value to encrypt
   * @param rsaPublicKeyPEM the RSA public key in PEM format
   * @return Base64-encoded encrypted data with format: version|RSA(key+IV)|AES(data)
   * @throws Exception if encryption fails
   */
  private String encryptValue(String value, String rsaPublicKeyPEM) throws Exception {
    // 1. Parse the PEM string to PublicKey
    PublicKey publicKey = parsePublicKey(rsaPublicKeyPEM);

    // 2. Generate AES key and IV
    byte[] aesKey = generateRandomBytes(32); // AES-256 key
    byte[] aesIv = generateRandomBytes(12); // GCM IV

    // 3. Encrypt value with AES-GCM
    byte[] aesEncryptedValue = aesEncrypt(value, aesKey, aesIv);

    // 4. Concatenate AES key + IV
    byte[] aesKeyAndIv = concatenateBytes(aesKey, aesIv);

    // 5. Encrypt key+IV with RSA using PKCS1 padding (not OAEP!)
    Cipher rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
    OAEPParameterSpec oaepParams =
        new OAEPParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
    rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey, oaepParams);
    byte[] rsaEncryptedKeyIv = rsaCipher.doFinal(aesKeyAndIv);

    // 6. Build final structure: version + RSA(key+IV) + AES(data)
    byte[] version = new byte[] {0x02};
    byte[] result = concatenateBytes(version, rsaEncryptedKeyIv, aesEncryptedValue);

    // 7. Return as Base64
    return Base64.getEncoder().encodeToString(result);
  }
}
