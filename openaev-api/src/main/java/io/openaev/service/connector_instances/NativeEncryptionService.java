package io.openaev.service.connector_instances;

import io.openaev.config.OpenAEVAdminConfig;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class NativeEncryptionService implements EncryptionService {
  private final TextEncryptor encryptor;

  public NativeEncryptionService(@Autowired OpenAEVAdminConfig config) {
    encryptor =
        Encryptors.delux(
            config.getEncryptionKey(),
            Hex.encodeHexString(config.getEncryptionSalt().getBytes(StandardCharsets.UTF_8)));
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
}
