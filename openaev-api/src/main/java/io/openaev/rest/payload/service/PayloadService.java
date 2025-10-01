package io.openaev.rest.payload.service;

import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_TARGETED_ASSET_SEPARATOR;
import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_TARGETED_PROPERTY;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.helper.SupportedLanguage.en;
import static io.openaev.helper.SupportedLanguage.fr;
import static io.openaev.injector_contract.Contract.executableContract;
import static io.openaev.injector_contract.ContractCardinality.Multiple;
import static io.openaev.injector_contract.ContractDef.contractBuilder;
import static io.openaev.injector_contract.fields.ContractAsset.assetField;
import static io.openaev.injector_contract.fields.ContractAssetGroup.assetGroupField;
import static io.openaev.injector_contract.fields.ContractExpectations.expectationsField;
import static io.openaev.injector_contract.fields.ContractSelect.selectFieldWithDefault;
import static io.openaev.injector_contract.fields.ContractText.textField;
import static io.openaev.utils.ArchitectureFilterUtils.handleArchitectureFilter;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.*;
import io.openaev.database.repository.AttackPatternRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.database.specification.SpecificationUtils;
import io.openaev.expectation.ExpectationBuilderService;
import io.openaev.helper.SupportedLanguage;
import io.openaev.injector_contract.Contract;
import io.openaev.injector_contract.ContractConfig;
import io.openaev.injector_contract.ContractDef;
import io.openaev.injector_contract.ContractTargetedProperty;
import io.openaev.injector_contract.fields.*;
import io.openaev.injectors.openaev.util.OpenAEVObfuscationMap;
import io.openaev.model.inject.form.Expectation;
import io.openaev.rest.payload.PayloadUtils;
import io.openaev.service.UserService;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class PayloadService {

  @Resource protected ObjectMapper mapper;

  private final PayloadRepository payloadRepository;
  private final InjectorRepository injectorRepository;
  private final InjectorContractRepository injectorContractRepository;
  private final AttackPatternRepository attackPatternRepository;
  private final ExpectationBuilderService expectationBuilderService;
  private final UserService userService;
  private final PayloadUtils payloadUtils;

  public void updateInjectorContractsForPayload(Payload payload) {
    List<Injector> injectors = this.injectorRepository.findAllByPayloads(true);
    injectors.forEach(injector -> updateInjectorContract(injector, payload));
  }

  private void setInjectorContractPropertyBasedOnPayload(
      InjectorContract injectorContract, Payload payload, Injector injector) {
    Map<String, String> labels = Map.of("en", payload.getName(), "fr", payload.getName());
    injectorContract.setLabels(labels);
    injectorContract.setNeedsExecutor(true);
    injectorContract.setManual(false);
    injectorContract.setInjector(injector);
    injectorContract.setPayload(payload);
    injectorContract.setPlatforms(payload.getPlatforms());
    injectorContract.setAttackPatterns(
        fromIterable(
            attackPatternRepository.findAllById(
                payload.getAttackPatterns().stream().map(AttackPattern::getId).toList())));
    injectorContract.setAtomicTesting(true);

    try {
      Contract contract = buildContract(injectorContract.getId(), injector, payload);
      injectorContract.setContent(mapper.writeValueAsString(contract));
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private void updateInjectorContract(Injector injector, Payload payload) {
    Optional<InjectorContract> injectorContract =
        injectorContractRepository.findInjectorContractByInjectorAndPayload(injector, payload);

    InjectorContract injectorContractToUpdate;
    if (injectorContract.isPresent()) {
      injectorContractToUpdate = injectorContract.get();
    } else {
      String contractId = String.valueOf(UUID.randomUUID());
      injectorContractToUpdate = new InjectorContract();
      injectorContractToUpdate.setId(contractId);
    }

    setInjectorContractPropertyBasedOnPayload(injectorContractToUpdate, payload, injector);
    injectorContractRepository.save(injectorContractToUpdate);
  }

  private ContractChoiceInformation obfuscatorField() {
    OpenAEVObfuscationMap obfuscationMap = new OpenAEVObfuscationMap();
    Map<String, String> obfuscationInfo = obfuscationMap.getAllObfuscationInfo();
    return ContractChoiceInformation.choiceInformationField(
        "obfuscator", "Obfuscators", obfuscationInfo, obfuscationMap.getDefaultObfuscator());
  }

  private List<ContractElement> targetedAssetFields(String key, PayloadArgument payloadArgument) {
    ContractElement targetedAssetField = new ContractTargetedAsset(key, key);

    Map<String, String> targetPropertySelectorMap = new HashMap<>();
    for (ContractTargetedProperty property : ContractTargetedProperty.values()) {
      targetPropertySelectorMap.put(property.name(), property.label);
    }
    ContractElement targetPropertySelector =
        selectFieldWithDefault(
            CONTRACT_ELEMENT_CONTENT_KEY_TARGETED_PROPERTY + "-" + key,
            "Targeted Property",
            targetPropertySelectorMap,
            payloadArgument.getDefaultValue());
    targetPropertySelector.setLinkedFields(List.of(targetedAssetField));

    ContractElement separatorField =
        textField(
            CONTRACT_ELEMENT_CONTENT_KEY_TARGETED_ASSET_SEPARATOR + "-" + key,
            "Separator",
            payloadArgument.getSeparator());
    separatorField.setLinkedFields(List.of(targetedAssetField));

    return List.of(targetedAssetField, targetPropertySelector, separatorField);
  }

  private Contract buildContract(
      @NotNull final String contractId,
      @NotNull final Injector injector,
      @NotNull final Payload payload) {
    Map<SupportedLanguage, String> labels = Map.of(en, injector.getName(), fr, injector.getName());
    ContractConfig contractConfig =
        new ContractConfig(
            injector.getType(),
            labels,
            "#000000",
            "#000000",
            "/img/icon-" + injector.getType() + ".png",
            true);
    ContractAsset assetField = assetField(Multiple);
    ContractAssetGroup assetGroupField = assetGroupField(Multiple);
    ContractExpectations expectationsField = expectations(payload.getExpectations());
    ContractDef builder = contractBuilder();
    builder.mandatoryGroup(assetField, assetGroupField);

    if (Objects.equals(payload.getType(), Command.COMMAND_TYPE)) {
      builder.optional(obfuscatorField());
    }

    builder.optional(expectationsField);
    if (payload.getArguments() != null) {
      payload
          .getArguments()
          .forEach(
              payloadArgument -> {
                if (ContractFieldType.Text.label.equals(payloadArgument.getType())) {
                  builder.mandatory(
                      textField(
                          payloadArgument.getKey(),
                          payloadArgument.getKey(),
                          payloadArgument.getDefaultValue()));

                } else if (ContractFieldType.TargetedAsset.label.equals(
                    payloadArgument.getType())) {
                  List<ContractElement> targetedAssetsFields =
                      targetedAssetFields(payloadArgument.getKey(), payloadArgument);
                  targetedAssetsFields.forEach(builder::mandatory);
                }
              });
    }
    return executableContract(
        contractConfig,
        contractId,
        Map.of(en, payload.getName(), fr, payload.getName()),
        builder.build(),
        Arrays.asList(payload.getPlatforms()),
        true);
  }

  private ContractExpectations expectations(InjectExpectation.EXPECTATION_TYPE[] expectationTypes) {
    List<Expectation> expectations = new ArrayList<>();
    if (expectationTypes != null) {
      for (InjectExpectation.EXPECTATION_TYPE type : expectationTypes) {
        switch (type) {
          case TEXT -> expectations.add(this.expectationBuilderService.buildTextExpectation());
          case DOCUMENT ->
              expectations.add(this.expectationBuilderService.buildDocumentExpectation());
          case ARTICLE ->
              expectations.add(this.expectationBuilderService.buildArticleExpectation());
          case CHALLENGE ->
              expectations.add(this.expectationBuilderService.buildChallengeExpectation());
          case MANUAL -> expectations.add(this.expectationBuilderService.buildManualExpectation());
          case PREVENTION ->
              expectations.add(this.expectationBuilderService.buildPreventionExpectation());
          case DETECTION ->
              expectations.add(this.expectationBuilderService.buildDetectionExpectation());
          case VULNERABILITY ->
              expectations.add(this.expectationBuilderService.buildVulnerabilityExpectation());
          default -> throw new IllegalArgumentException("Unsupported expectation type: " + type);
        }
      }
    }
    return expectationsField(expectations);
  }

  public Payload duplicate(@NotBlank final String payloadId) {
    Payload origin = this.payloadRepository.findById(payloadId).orElseThrow();
    Payload duplicated = payloadRepository.save(generateDuplicatedPayload(origin));
    this.updateInjectorContractsForPayload(duplicated);
    return duplicated;
  }

  public Payload generateDuplicatedPayload(Payload originalPayload) {
    return switch (originalPayload.getTypeEnum()) {
      case COMMAND -> {
        Command originCommand = (Command) Hibernate.unproxy(originalPayload);
        Command duplicateCommand = new Command();
        payloadUtils.duplicateCommonProperties(originCommand, duplicateCommand);
        yield duplicateCommand;
      }
      case EXECUTABLE -> {
        Executable originExecutable = (Executable) Hibernate.unproxy(originalPayload);
        Executable duplicateExecutable = new Executable();
        payloadUtils.duplicateCommonProperties(originExecutable, duplicateExecutable);
        duplicateExecutable.setExecutableFile(originExecutable.getExecutableFile());
        yield duplicateExecutable;
      }
      case FILE_DROP -> {
        FileDrop originFileDrop = (FileDrop) Hibernate.unproxy(originalPayload);
        FileDrop duplicateFileDrop = new FileDrop();
        payloadUtils.duplicateCommonProperties(originFileDrop, duplicateFileDrop);
        duplicateFileDrop.setFileDropFile(originFileDrop.getFileDropFile());
        yield duplicateFileDrop;
      }
      case DNS_RESOLUTION -> {
        DnsResolution originDnsResolution = (DnsResolution) Hibernate.unproxy(originalPayload);
        DnsResolution duplicateDnsResolution = new DnsResolution();
        payloadUtils.duplicateCommonProperties(originDnsResolution, duplicateDnsResolution);
        yield duplicateDnsResolution;
      }
      case NETWORK_TRAFFIC -> {
        NetworkTraffic originNetworkTraffic = (NetworkTraffic) Hibernate.unproxy(originalPayload);
        NetworkTraffic duplicateNetworkTraffic = new NetworkTraffic();
        payloadUtils.duplicateCommonProperties(originNetworkTraffic, duplicateNetworkTraffic);
        yield duplicateNetworkTraffic;
      }
    };
  }

  public void deprecateNonProcessedPayloadsByCollector(
      String collectorId, List<String> processedPayloadExternalIds) {
    List<String> payloadExternalIds =
        payloadRepository.findAllExternalIdsByCollectorId(collectorId);
    List<String> payloadExternalIdsToDeprecate =
        getExternalIdsToDeprecate(payloadExternalIds, processedPayloadExternalIds);
    payloadRepository.setPayloadStatusByExternalIds(
        String.valueOf(Payload.PAYLOAD_STATUS.DEPRECATED), payloadExternalIdsToDeprecate);
    log.info("Number of deprecated Payloads: {}", payloadExternalIdsToDeprecate.size());
  }

  private static List<String> getExternalIdsToDeprecate(
      List<String> payloadExternalIds, List<String> processedPayloadExternalIds) {
    return payloadExternalIds.stream()
        .filter(externalId -> !processedPayloadExternalIds.contains(externalId))
        .collect(Collectors.toList());
  }

  /**
   * Search payloads with pagination and architecture filter, where the user is granted. The user
   * must have at least OBSERVER grant on the payloads to see them OR have the access capability on
   * payloads.
   *
   * @param searchPaginationInput the input containing pagination and search criteria
   * @return a paginated list of Payloads
   */
  public Page<Payload> searchPayloads(@NotNull final SearchPaginationInput searchPaginationInput) {
    User currentUser = userService.currentUser();
    return buildPaginationJPA(
        SpecificationUtils.withGrantFilter(
            this.payloadRepository,
            Grant.GRANT_TYPE.OBSERVER,
            currentUser.getId(),
            currentUser.isAdminOrBypass(),
            currentUser.getCapabilities().contains(Capability.ACCESS_PAYLOADS)),
        handleArchitectureFilter(searchPaginationInput),
        Payload.class);
  }
}
