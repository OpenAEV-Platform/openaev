package io.openaev.service.connector_instances;

public interface EncryptionService {
  String encrypt(String plainText) throws Exception;

  String decrypt(String encryptedText);
}
