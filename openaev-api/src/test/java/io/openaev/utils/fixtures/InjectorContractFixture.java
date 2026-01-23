package io.openaev.utils.fixtures;

import static io.openaev.database.model.InjectorContract.CONTRACT_CONTENT_FIELDS;
import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_TARGETED_PROPERTY;
import static io.openaev.injector_contract.ContractCardinality.Multiple;
import static io.openaev.injector_contract.ContractDef.contractBuilder;
import static io.openaev.injector_contract.fields.ContractAsset.assetField;
import static io.openaev.injector_contract.fields.ContractAssetGroup.assetGroupField;
import static io.openaev.injector_contract.fields.ContractSelect.selectFieldWithDefault;
import static io.openaev.injectors.email.EmailContract.EMAIL_DEFAULT;
import static io.openaev.injectors.email.EmailContract.EMAIL_GLOBAL;
import static io.openaev.utils.fixtures.InjectorFixture.createDefaultPayloadInjector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Injector;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.injector_contract.ContractCardinality;
import io.openaev.injector_contract.ContractDef;
import io.openaev.injector_contract.ContractTargetedProperty;
import io.openaev.injector_contract.fields.*;
import io.openaev.injector_contract.fields.ContractElement;
import io.openaev.injector_contract.fields.ContractSelect;
import io.openaev.injector_contract.fields.ContractTargetedAsset;
import io.openaev.integration.Manager;
import io.openaev.integration.impl.injectors.email.EmailInjectorIntegrationFactory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.*;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component
public class InjectorContractFixture {

  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private EmailInjectorIntegrationFactory emailInjectorIntegrationFactory;

  public InjectorContract getWellKnownSingleEmailContract() {
    Optional<InjectorContract> injectorContract =
        injectorContractRepository.findById(EMAIL_DEFAULT);
    if (injectorContract.isPresent()) {
      return injectorContract.get();
    }
    try {
      Manager manager = new Manager(List.of(emailInjectorIntegrationFactory));
      manager.monitorIntegrations();
      return injectorContractRepository.findById(EMAIL_DEFAULT).orElseThrow();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public InjectorContract getWellKnownGlobalEmailContract() {
    Optional<InjectorContract> injectorContract = injectorContractRepository.findById(EMAIL_GLOBAL);
    if (injectorContract.isPresent()) {
      return injectorContract.get();
    }
    try {
      Manager manager = new Manager(List.of(emailInjectorIntegrationFactory));
      manager.monitorIntegrations();
      return injectorContractRepository.findById(EMAIL_GLOBAL).orElseThrow();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static ObjectNode createDefaultContent(ObjectMapper objectMapper) {
    ObjectNode node = objectMapper.createObjectNode();
    node.set(CONTRACT_CONTENT_FIELDS, objectMapper.valueToTree(new ArrayList<>()));
    return node;
  }

  public static InjectorContract createPayloadInjectorContractWithFieldsContent(
      List<ContractCardinalityElement> customFieldsContent) throws JsonProcessingException {
    InjectorContract injectorContract = new InjectorContract();
    injectorContract.setId(UUID.randomUUID().toString());

    ObjectMapper objectMapper = new ObjectMapper();
    ObjectNode content = createDefaultContent(objectMapper);
    content.set(CONTRACT_CONTENT_FIELDS, objectMapper.valueToTree(customFieldsContent));

    injectorContract.setContent(objectMapper.writeValueAsString(content));
    injectorContract.setConvertedContent(content);

    return injectorContract;
  }

  public static InjectorContract createPayloadInjectorContractWithFieldsContent(
      Injector injector,
      Payload payloadCommand,
      List<ContractCardinalityElement> customFieldsContent)
      throws JsonProcessingException {
    InjectorContract injectorContract =
        createPayloadInjectorContractWithFieldsContent(customFieldsContent);
    injectorContract.setInjector(injector);
    injectorContract.setPayload(payloadCommand);
    return injectorContract;
  }

  @SneakyThrows
  private static InjectorContract createDefaultInjectorContractInternal() {
    InjectorContract injectorContract = new InjectorContract();
    injectorContract.setInjector(createDefaultPayloadInjector());
    injectorContract.setId(UUID.randomUUID().toString());

    ObjectMapper objectMapper = new ObjectMapper();
    ObjectNode content = createDefaultContent(objectMapper);
    injectorContract.setContent(objectMapper.writeValueAsString(content));
    injectorContract.setConvertedContent(content);
    injectorContract.setDomains(new HashSet<>());
    return injectorContract;
  }

  public static InjectorContract createDefaultInjectorContract() {
    return createDefaultInjectorContractInternal();
  }

  public static InjectorContract createDefaultInjectorContractWithExternalId(String externalId) {
    InjectorContract injectorContract = createDefaultInjectorContractInternal();
    injectorContract.setExternalId(externalId);
    return injectorContract;
  }

  public static InjectorContract createInjectorContractWithPlatforms(
      Endpoint.PLATFORM_TYPE[] platforms) {
    InjectorContract injectorContract = createDefaultInjectorContract();
    injectorContract.setPlatforms(platforms);
    return injectorContract;
  }

  public static InjectorContract createPayloadInjectorContract(
      Injector injector, Payload payloadCommand) throws JsonProcessingException {
    return createPayloadInjectorContractWithFieldsContent(injector, payloadCommand, List.of());
  }

  public static InjectorContract createPayloadInjectorContractWithObfuscator()
      throws JsonProcessingException {
    ContractSelect obfuscatorSelect =
        new ContractSelect("obfuscator", "Obfuscators", ContractCardinality.One);
    obfuscatorSelect.setChoices(Map.of("plain-text", "plain-text", "base64", "base64"));

    return createPayloadInjectorContractWithFieldsContent(List.of(obfuscatorSelect));
  }

  public static InjectorContract createPayloadInjectorContractWithObfuscator(
      Injector injector, Payload payloadCommand) throws JsonProcessingException {
    ContractSelect obfuscatorSelect =
        new ContractSelect("obfuscator", "Obfuscators", ContractCardinality.One);
    obfuscatorSelect.setChoices(Map.of("plain-text", "plain-text", "base64", "base64"));

    return createPayloadInjectorContractWithFieldsContent(
        injector, payloadCommand, List.of(obfuscatorSelect));
  }

  public static InjectorContract createInjectorContract(Map<String, String> labels, String content)
      throws JsonProcessingException {
    InjectorContract injectorContract = new InjectorContract();
    injectorContract.setId(UUID.randomUUID().toString());
    injectorContract.setLabels(labels);
    injectorContract.setContent(content);
    injectorContract.setConvertedContent(new ObjectMapper().readValue(content, ObjectNode.class));
    injectorContract.setAtomicTesting(true);
    injectorContract.setCreatedAt(Instant.now());
    injectorContract.setUpdatedAt(Instant.now());
    return injectorContract;
  }

  public static InjectorContract createInjectorContract(ObjectNode convertedContent) {
    InjectorContract injectorContract = new InjectorContract();
    injectorContract.setId(UUID.randomUUID().toString());
    injectorContract.setConvertedContent(convertedContent);
    injectorContract.setContent(convertedContent.toString());
    injectorContract.setAtomicTesting(false);
    injectorContract.setCreatedAt(Instant.now());
    injectorContract.setUpdatedAt(Instant.now());
    return injectorContract;
  }

  public static InjectorContract createInjectorContract(Map<String, String> labels)
      throws JsonProcessingException {
    String content = "{\"fields\": []}";
    return createInjectorContract(labels, content);
  }

  // -- BUILDER --

  public static void addField(
      InjectorContract injectorContract,
      ObjectMapper mapper,
      List<ContractElement> contractElements)
      throws JsonProcessingException {
    ObjectNode content = mapper.readValue(injectorContract.getContent(), ObjectNode.class);
    List<ContractElement> elements =
        mapper.convertValue(content.get(CONTRACT_CONTENT_FIELDS), new TypeReference<>() {});
    if (CollectionUtils.isEmpty(elements)) {
      elements = new ArrayList<>();
    }

    elements.addAll(contractElements);

    content.set(CONTRACT_CONTENT_FIELDS, mapper.valueToTree(elements));
    injectorContract.setContent(mapper.writeValueAsString(content));
    injectorContract.setConvertedContent(content);
  }

  public static List<ContractElement> buildAssetField(final boolean mandatory) {
    ContractDef builder = contractBuilder();
    ContractAsset assetField = assetField(Multiple);
    if (mandatory) {
      builder.mandatory(assetField);
    } else {
      builder.optional(assetField);
    }
    return builder.build();
  }

  public static List<ContractElement> buildMandatoryGroup() {
    ContractAsset assetField = assetField(Multiple);
    ContractAssetGroup assetGroupField = assetGroupField(Multiple);
    ContractDef builder = contractBuilder();
    builder.mandatoryGroup(assetField, assetGroupField);
    return builder.build();
  }

  public static List<ContractElement> buildMandatoryOnCondition() {
    ContractAsset assetField = assetField(Multiple);
    ContractAssetGroup assetGroupField = assetGroupField(Multiple);
    ContractDef builder = contractBuilder();
    return builder
        .mandatoryOnCondition(assetField, assetGroupField)
        .optional(assetGroupField)
        .build();
  }

  public static List<ContractElement> buildMandatoryOnConditionValue(@NotBlank final String value) {
    ContractAsset assetField = assetField(Multiple);
    ContractAssetGroup assetGroupField = assetGroupField(Multiple);
    ContractDef builder = contractBuilder();
    return builder
        .mandatoryOnConditionValue(assetField, assetGroupField, value)
        .optional(assetGroupField)
        .build();
  }

  public static List<ContractElement> buildMandatoryOnConditionValue(
      @NotNull final List<String> values) {
    ContractAsset assetField = assetField(Multiple);
    ContractAssetGroup assetGroupField = assetGroupField(Multiple);
    ContractDef builder = contractBuilder();
    return builder
        .mandatoryOnConditionValue(assetField, assetGroupField, values)
        .optional(assetGroupField)
        .build();
  }

  public static void addTargetedAssetFields(
      InjectorContract injectorContract,
      String key,
      ContractTargetedProperty defaultTargetedProperty) {
    ContractElement targetedAssetField = new ContractTargetedAsset(key, "label-" + key);
    ContractElement targetPropertySelector =
        selectFieldWithDefault(
            CONTRACT_ELEMENT_CONTENT_KEY_TARGETED_PROPERTY + "-" + key,
            "Targeted Property",
            new HashMap<>(),
            defaultTargetedProperty.name());
    targetPropertySelector.setLinkedFields(List.of(targetedAssetField));

    JsonNode injectorContractFieldsNode =
        injectorContract.getConvertedContent().get(CONTRACT_CONTENT_FIELDS);

    if (!(injectorContractFieldsNode instanceof ArrayNode)) {
      throw new IllegalArgumentException("The fields node is not an ArrayNode");
    }

    ArrayNode arrayNode = (ArrayNode) injectorContractFieldsNode;
    ObjectMapper objectMapper = new ObjectMapper();

    arrayNode.add(objectMapper.valueToTree(targetedAssetField));
    arrayNode.add(objectMapper.valueToTree(targetPropertySelector));
    injectorContract.getConvertedContent().set(CONTRACT_CONTENT_FIELDS, arrayNode);
  }
}
