package io.openaev.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.*;
import io.openaev.database.repository.ConnectorInstanceRepository;
import io.openaev.database.repository.TokenRepository;
import io.openaev.rest.connector_instance.dto.ConnectorInstanceHealthInput;
import io.openaev.rest.connector_instance.dto.CreateConnectorInstanceInput;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import jakarta.persistence.EntityNotFoundException;

import java.nio.ByteBuffer;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import static io.openaev.config.SessionHelper.currentUser;
import static io.openaev.database.specification.TokenSpecification.fromUser;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectorInstanceService {

  private final ObjectMapper objectMapper;

  private final ConnectorInstanceRepository connectorInstanceRepository;
  private final TokenRepository tokenRepository;

  private final ConnectorInstanceLogService connectorInstanceLogService;
  private final CatalogConnectorService catalogConnectorService;

  public List<ConnectorInstance> connectorInstancesManagedByXtmComposer() {
    return connectorInstanceRepository.findAllManagedByXtmComposerAndConfiguration();
  }

  public List<ConnectorInstance> injectorConnectorInstances(){
    return connectorInstanceRepository.findAllByCatalogConnectorContainerType(CatalogConnector.CONNECTOR_TYPE.INJECTOR);
  }
  public List<ConnectorInstance> collectorConnectorInstances() {
    return connectorInstanceRepository.findAllByCatalogConnectorContainerType(CatalogConnector.CONNECTOR_TYPE.COLLECTOR);
  }

  public ConnectorInstance connectorInstanceById(String id) {
    return connectorInstanceRepository
        .findById(id)
        .orElseThrow(
            () -> new EntityNotFoundException("ConnectorInstance with id " + id + " not found"));
  }

  public ConnectorInstance updateCurrentStatus(
          String connectorInstanceId, ConnectorInstance.CURRENT_STATUS_TYPE newCurrentStatus) {
    ConnectorInstance instance = this.connectorInstanceById(connectorInstanceId);
    instance.setCurrentStatus(newCurrentStatus);
    return this.save(instance);
  }

  public ConnectorInstance updateRequestedStatus(
          String connectorInstanceId, ConnectorInstance.REQUESTED_STATUS_TYPE newRequestedStatus) {
    ConnectorInstance instance = this.connectorInstanceById(connectorInstanceId);
    instance.setRequestedStatus(newRequestedStatus);
    return this.save(instance);
  }

  public ConnectorInstance save(ConnectorInstance connectorInstance) {
    return connectorInstanceRepository.save(connectorInstance);
  }

  public void deleteById(String id) {
    if (!this.connectorInstanceRepository.existsById(id)) {
      throw new EntityNotFoundException("ConnectorInstance with id " + id + " not found");
    }
    connectorInstanceRepository.deleteById(id);
  }

  public List<ConnectorInstance> findAllByCatalogConnector(CatalogConnector connector) {
    return connectorInstanceRepository.findAllByCatalogConnectorId(connector.getId());
  }

  public void saveAll(Set<ConnectorInstance> instances) {
    connectorInstanceRepository.saveAll(instances);
  }

  private PublicKey parsePublicKey(String rsaPublicKeyPEM) throws Exception {
    // Remove PEM headers/footers and whitespace
    String cleanedKey = rsaPublicKeyPEM
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("-----BEGIN RSA PUBLIC KEY-----", "")
            .replace("-----END RSA PUBLIC KEY-----", "")
            .replaceAll("\\s", ""); // Remove all whitespace

    X509EncodedKeySpec keySpecPublic = new X509EncodedKeySpec(Base64.getDecoder().decode(cleanedKey));
    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    return keyFactory.generatePublic(keySpecPublic);
  }

  private byte[] generateRandomBytes(int length) {
    SecureRandom secureRandom = new SecureRandom();
    byte[] bytes = new byte[length];
    secureRandom.nextBytes(bytes);
    return bytes;
  }

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

  public String encryptValue(String value, String rsaPublicKeyPEM) throws Exception {
    // 1. Parse the PEM string to PublicKey
    PublicKey publicKey = parsePublicKey(rsaPublicKeyPEM);

    // 2. Generate AES key and IV
    byte[] aesKey = generateRandomBytes(32);  // AES-256 key
    byte[] aesIv = generateRandomBytes(12);   // GCM IV

    // 3. Encrypt value with AES-GCM
    byte[] aesEncryptedValue = aesEncrypt(value, aesKey, aesIv);

    // 4. Concatenate AES key + IV
    byte[] aesKeyAndIv = concatenateBytes(aesKey, aesIv);

    // 5. Encrypt key+IV with RSA using PKCS1 padding (not OAEP!)
    Cipher rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
    rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey);
    byte[] rsaEncryptedKeyIv = rsaCipher.doFinal(aesKeyAndIv);

    // 6. Build final structure: version + RSA(key+IV) + AES(data)
    byte[] version = new byte[] { 0x01 };
    byte[] result = concatenateBytes(version, rsaEncryptedKeyIv, aesEncryptedValue);

    // 7. Return as Base64
    return Base64.getEncoder().encodeToString(result);
  }

  private ConnectorInstanceConfiguration createConnectorInstanceConfiguration(
          String key, JsonNode value, boolean isEncrypted, ConnectorInstance connectorInstance) {
    ConnectorInstanceConfiguration confValue = new ConnectorInstanceConfiguration();
    confValue.setKey(key);
    confValue.setEncrypted(isEncrypted);
    confValue.setValue(value);
    confValue.setConnectorInstance(connectorInstance);
    return confValue;
  }

  public ConnectorInstance createConnectorInstance(CreateConnectorInstanceInput input, String rsaPublicKey) {
    Optional<CatalogConnector> catalogConnector = catalogConnectorService.findById(input.getCatalogConnectorId());
    if (catalogConnector.isEmpty()) {
      throw new EntityNotFoundException("CatalogConnector with id " + input.getCatalogConnectorId() + " not found");
    }

    List<ConnectorInstance> existingInstances = connectorInstanceRepository.findAllByCatalogConnectorId(input.getCatalogConnectorId());
    if (!existingInstances.isEmpty()) {
      throw new IllegalArgumentException("ConnectorInstance with CatalogConnector id " + input.getCatalogConnectorId() + " already exists");
    }

    ConnectorInstance newInstance = new ConnectorInstance();
    newInstance.setCatalogConnector(catalogConnector.get());
    newInstance.setRequestedStatus(ConnectorInstance.REQUESTED_STATUS_TYPE.stopping);
    newInstance.setCurrentStatus(ConnectorInstance.CURRENT_STATUS_TYPE.stopped);

    List<ConnectorInstanceConfiguration> configurationValues = new ArrayList<>();
    List<CatalogConnectorConfiguration> configurationDefinitions = new ArrayList<>(catalogConnector.get().getCatalogConnectorConfigurations());

    input.getConfigurations().forEach(confInput -> {
      CatalogConnectorConfiguration matchingConfDef = configurationDefinitions.stream()
          .filter(confDef -> confDef.getConnectorConfigurationKey().equals(confInput.getKey()))
          .findFirst()
          .orElseThrow(() -> new IllegalArgumentException("Configuration key " + confInput + " not found in CatalogConnector configurations"));
      boolean isEncrypted = CatalogConnectorConfiguration.CONNECTOR_CONFIGURATION_FORMAT.PASSWORD.equals(matchingConfDef.getConnectorConfigurationFormat());
      try {
          configurationValues.add(createConnectorInstanceConfiguration(
                  confInput.getKey(),
                  isEncrypted ?
                          objectMapper.getNodeFactory().textNode(encryptValue(confInput.getValue().asText(), rsaPublicKey))
                          : confInput.getValue(),
                  isEncrypted,
                  newInstance
          ));
      } catch (Exception e) {
          throw new RuntimeException(e);
      }
    });

    // TODO url automatically
    Token token = tokenRepository.findAll(fromUser(currentUser().getId())).stream().findFirst().orElseThrow(() -> new IllegalArgumentException("No token found for current user"));
    configurationValues.add(createConnectorInstanceConfiguration(
            "OPENAEV_TOKEN",
            objectMapper.getNodeFactory().textNode(token.getValue()),
            false,
            newInstance
    ));
    if (configurationValues.size() != configurationDefinitions.size()) {
      throw new IllegalArgumentException("Not all required configurations are provided for CatalogConnector id " + input.getCatalogConnectorId());
    }

    configurationValues.add(createConnectorInstanceConfiguration(
            catalogConnector.get().getContainerType().toString() + "_ID",
            objectMapper.getNodeFactory().textNode(UUID.randomUUID().toString()),
            false,
            newInstance
    ));

    newInstance.setConfigurations(Set.copyOf(configurationValues));
    return this.save(newInstance);
  }

  public void pushLogsByConnectorInstance(String connectorInstanceId, Set<String> logs) {
    if (logs.isEmpty()) {
      return;
    }
    ConnectorInstance instance = this.connectorInstanceById(connectorInstanceId);
    this.connectorInstanceLogService.pushLogByConnectorInstance(instance, connectorInstanceLogService.transformRawLogsLineToLog(logs));
  }

  public void patchConnectorInstanceHealthCheck(String connectorInstanceId, ConnectorInstanceHealthInput input){
    ConnectorInstance instance = this.connectorInstanceById(connectorInstanceId);

    instance.setInRebootLoop(input.isInRebootLoop());
    instance.setStartedAt(input.getStartedAt());
    instance.setRestartCount(input.getRestartCount());

    this.save(instance);
  }
}
