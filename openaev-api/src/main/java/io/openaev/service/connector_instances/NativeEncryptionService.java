package io.openaev.service.connector_instances;

import io.openaev.config.OpenAEVAdminConfig;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class NativeEncryptionService implements EncryptionService {
  private final TextEncryptor encryptor;

  /**
   * The very {@link BytesEncryptor} that {@link Encryptors#delux} wraps with a hex encoding: same
   * key, same salt, same algorithm. Binary secrets (the GCP service account key file) are stored
   * raw in a {@code BYTEA} column, so the hex round trip is pure overhead for them.
   */
  private final BytesEncryptor bytesEncryptor;

  public NativeEncryptionService(@Autowired OpenAEVAdminConfig config) {
    String hexSalt =
        Hex.encodeHexString(config.getEncryptionSalt().getBytes(StandardCharsets.UTF_8));
    encryptor = Encryptors.delux(config.getEncryptionKey(), hexSalt);
    bytesEncryptor = Encryptors.stronger(config.getEncryptionKey(), hexSalt);
  }

  /**
   * Function used to encrypt plain text
   *
   * @param plainText plain text to encrypt
   * @return plain text encrypted
   */
  @Override
  public String encrypt(String plainText) {
    return encryptor.encrypt(plainText);
  }

  /**
   * Function used to decrypt secret stored in DB
   *
   * @param encryptedText the encrypted text
   * @return the decrypted text
   */
  @Override
  public String decrypt(String encryptedText) {
    return encryptor.decrypt(encryptedText);
  }

  /**
   * Encrypts raw bytes, for the secrets stored in a binary column.
   *
   * <p>Deliberately absent from {@link EncryptionService}: only the secret handlers need it, and
   * they already depend on this concrete type. Adding it to the interface would force an
   * implementation onto {@code XtmComposerEncryptionService}, whose {@code decrypt} is a documented
   * no-op and which has no use for binary payloads.
   *
   * @param plainBytes the bytes to encrypt
   * @return the encrypted bytes
   */
  public byte[] encrypt(byte[] plainBytes) {
    return bytesEncryptor.encrypt(plainBytes);
  }

  /**
   * Decrypts bytes produced by {@link #encrypt(byte[])}.
   *
   * @param encryptedBytes the encrypted bytes
   * @return the decrypted bytes
   */
  public byte[] decrypt(byte[] encryptedBytes) {
    return bytesEncryptor.decrypt(encryptedBytes);
  }
}
