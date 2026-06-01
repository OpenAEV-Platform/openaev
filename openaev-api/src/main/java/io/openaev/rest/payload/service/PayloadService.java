package io.openaev.rest.payload.service;

import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_TARGETED_ASSET_SEPARATOR;
import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_TARGETED_PROPERTY;
import static io.openaev.database.model.Tag.OPENCTI_TAG_NAME;
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
import static io.openaev.service.stix.SecurityCoverageInjectService.ALL_PLATFORMS;
import static io.openaev.utils.ArchitectureFilterUtils.handleArchitectureFilter;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.aop.lock.Lock;
import io.openaev.aop.lock.LockResourceType;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.raw.RawPayloadRelatedIds;
import io.openaev.database.repository.*;
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
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.domain.DomainService;
import io.openaev.rest.domain.enums.PresetDomain;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.injector_contract.form.InjectorContractDomainDTO;
import io.openaev.rest.payload.PayloadUtils;
import io.openaev.rest.payload.output.PayloadOutput;
import io.openaev.rest.tag.TagService;
import io.openaev.service.UserService;
import io.openaev.utils.mapper.PayloadMapper;
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

  public static final String DYNAMIC_DNS_RESOLUTION_HOSTNAME_KEY = "dynamic_hostname_key";
  public static final String DYNAMIC_DNS_RESOLUTION_HOSTNAME_VARIABLE =
      "#{" + DYNAMIC_DNS_RESOLUTION_HOSTNAME_KEY + "}";
  private static final String DYNAMIC_DNS_RESOLUTION_UUID = "ff16dc60-ea6f-4925-8509-20557e09c676";

  @Resource protected ObjectMapper mapper;

  private final PayloadRepository payloadRepository;
  private final InjectorRepository injectorRepository;
  private final InjectorContractRepository injectorContractRepository;
  private final ExpectationBuilderService expectationBuilderService;
  private final UserService userService;
  private final DocumentService documentService;
  private final PayloadUtils payloadUtils;
  private final DomainService domainService;
  private final TagService tagService;

  private final PayloadMapper payloadMapper;

  public InjectorContract synchroniseInjectorContractBasedOnPayload(
      Payload payload, List<AttackPattern> attackPatterns, Set<Domain> domains, Set<Tag> tags) {
    List<Injector> injectors =
        this.injectorRepository.findAllByPayloadsAndTenantId(true, payload.getTenant().getId());

    Injector referenceInjector = injectors.isEmpty() ? null : injectors.getFirst();
    if (referenceInjector == null) {
      return null;
    }

    InjectorContract injectorContractToUpdate =
        injectorContractRepository
            .findInjectorContractByPayload(payload)
            .orElseGet(
                () -> {
                  String contractId = String.valueOf(UUID.randomUUID());
                  InjectorContract newContract = new InjectorContract();
                  newContract.setId(contractId);
                  return newContract;
                });

    setInjectorContractPropertyBasedOnPayload(
        injectorContractToUpdate, payload, attackPatterns, domains, tags, referenceInjector);
    InjectorContract injectorContractSaved =
        injectorContractRepository.save(injectorContractToUpdate);

    // Link contract to all payload-supporting injectors via the owning side
    Set<String> injectorIds = injectors.stream().map(Injector::getId).collect(Collectors.toSet());
    injectorContractRepository.addContractToPayloadsInjectors(
        injectorIds, injectorContractSaved.getCompositeId().getId());

    return injectorContractSaved;
  }

  private void setInjectorContractPropertyBasedOnPayload(
      @NotNull InjectorContract injectorContract,
      @NotNull Payload payload,
      List<AttackPattern> attackPatterns,
      Set<Domain> domains,
      Set<Tag> tags,
      Injector injector) {
    Map<String, String> labels = Map.of("en", payload.getName(), "fr", payload.getName());
    injectorContract.setLabels(labels);
    injectorContract.setNeedsExecutor(true);
    injectorContract.setManual(false);
    injectorContract.addInjector(injector);
    injectorContract.setPayload(payload);
    injectorContract.setPlatforms(payload.getPlatforms());
    injectorContract.setDomains(new HashSet<>(domains));
    injectorContract.setTags(new HashSet<>(tags));
    injectorContract.setAttackPatterns(new ArrayList<>(attackPatterns));
    injectorContract.setAtomicTesting(true);

    try {
      Contract contract =
          buildContract(injectorContract.getId(), injector, payload, new HashSet<>(domains));
      String content = mapper.writeValueAsString(contract);
      injectorContract.setContent(content);
      injectorContract.setConvertedContent(mapper.readValue(content, ObjectNode.class));
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private ContractChoiceInformation obfuscatorField(String executor) {
    OpenAEVObfuscationMap obfuscationMap = new OpenAEVObfuscationMap(executor);
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
      @NotNull final Payload payload,
      final Set<Domain> domains) {
    Map<SupportedLanguage, String> labels = Map.of(en, injector.getName(), fr, injector.getName());
    ContractConfig contractConfig =
        new ContractConfig(
            injector.getType(),
            labels,
            "#000000",
            "#000000",
            "/img/icon-" + injector.getType() + ".png");
    ContractAsset assetField = assetField(Multiple);
    ContractAssetGroup assetGroupField = assetGroupField(Multiple);
    ContractExpectations expectationsField = expectations(payload.getExpectations());
    ContractDef builder = contractBuilder();
    builder.mandatoryGroup(assetField, assetGroupField);

    if (Objects.equals(payload.getType(), Command.COMMAND_TYPE)) {
      builder.optional(obfuscatorField(((Command) payload).getExecutor()));
    }

    builder.optional(expectationsField);
    if (payload.getArguments() != null) {
      payload
          .getArguments()
          .forEach(
              payloadArgument -> {
                if (ArgumentType.TargetedAsset == payloadArgument.getType()) {
                  List<ContractElement> targetedAssetsFields =
                      targetedAssetFields(payloadArgument.getKey(), payloadArgument);
                  targetedAssetsFields.forEach(builder::mandatory);
                } else {
                  builder.mandatory(
                      textField(
                          payloadArgument.getKey(),
                          payloadArgument.getKey(),
                          payloadArgument.getDefaultValue()));
                }
              });
    }
    return executableContract(
        contractConfig,
        contractId,
        Map.of(en, payload.getName(), fr, payload.getName()),
        builder.build(),
        Arrays.asList(payload.getPlatforms()),
        true,
        domains);
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

  public PayloadOutput convertPayloadInjectorContractCreationToPayloadOutput(
      PayloadCreationService.PayloadInjectorContractCreationResult result) {
    return payloadMapper.toPayloadOutput(
        result.payload(),
        result.injectorContract().getAttackPatterns().stream()
            .map(AttackPattern::getId)
            .collect(Collectors.toList()),
        result.injectorContract().getDomains().stream()
            .map(Domain::getId)
            .collect(Collectors.toList()),
        result.injectorContract().getTags().stream().map(Tag::getId).collect(Collectors.toList()));
  }

  public record PayloadWithRelatedEntities(
      Payload payload,
      List<String> attackPatternIds,
      List<String> domainIds,
      List<String> tagIds) {}

  public PayloadWithRelatedEntities findPayloadWithRelatedEntities(String payloadId) {
    Payload payload =
        payloadRepository.findById(payloadId).orElseThrow(ElementNotFoundException::new);
    RawPayloadRelatedIds relatedIds =
        injectorContractRepository.findRelatedIdsByPayloadId(payloadId).orElse(null);

    List<String> attackPatternIds =
        relatedIds != null ? relatedIds.getAttack_pattern_ids() : List.of();
    List<String> domainIds = relatedIds != null ? relatedIds.getDomain_ids() : List.of();
    List<String> tagIds = relatedIds != null ? relatedIds.getTag_ids() : List.of();

    return new PayloadWithRelatedEntities(payload, attackPatternIds, domainIds, tagIds);
  }

  public PayloadCreationService.PayloadInjectorContractCreationResult duplicate(
      @NotBlank final String payloadId) {
    Payload origin = this.payloadRepository.findById(payloadId).orElseThrow();
    Optional<InjectorContract> originInjectorContract =
        injectorContractRepository.findInjectorContractByPayload(origin);

    Payload duplicated = payloadRepository.save(generateDuplicatedPayload(origin));
    InjectorContract injectorContract =
        this.synchroniseInjectorContractBasedOnPayload(
            duplicated,
            originInjectorContract.isPresent()
                ? originInjectorContract.get().getAttackPatterns()
                : List.of(),
            originInjectorContract.isPresent()
                ? originInjectorContract.get().getDomains()
                : Set.of(),
            originInjectorContract.isPresent() ? originInjectorContract.get().getTags() : Set.of());
    return new PayloadCreationService.PayloadInjectorContractCreationResult(
        duplicated, injectorContract);
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
        .toList();
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

  /**
   * Retrieve the existing FileDrop Payload linked to the document id, or create a new one if it
   * doesn't exist
   *
   * @param documentId to filter
   * @param scenario to add to document if file drop is created
   * @return retrieved or created FileDrop
   */
  public FileDrop getFileDropPayloadByDocument(String documentId, Scenario scenario) {
    FileDrop fileDrop =
        payloadRepository
            .findByDocumentId(documentId)
            .orElseGet(() -> this.createFileDropPayload(documentId));
    fileDrop.getFileDropFile().getScenarios().add(scenario);
    this.documentService.save(fileDrop.getFileDropFile());
    return fileDrop;
  }

  /**
   * Create a FileDrop Payload with linked provided document id
   *
   * @param documentId to link to FileDrop Payload
   * @return created file drop payload
   */
  public FileDrop createFileDropPayload(String documentId) {
    Document document = this.documentService.document(documentId);

    FileDrop fileDrop = new FileDrop();
    fileDrop.setFileDropFile(document);
    fileDrop.setName(String.format("Drop %s file", document.getName()));
    fileDrop.setDescription(
        String.format("Drop of %s file into the specified endpoint", document.getName()));
    fileDrop.setStatus(Payload.PAYLOAD_STATUS.VERIFIED);
    fileDrop.setSource(Payload.PAYLOAD_SOURCE.FILIGRAN);
    fileDrop.setType(FileDrop.FILE_DROP_TYPE);
    fileDrop.setPlatforms(ALL_PLATFORMS);
    fileDrop.setExecutionArch(Payload.PAYLOAD_EXECUTION_ARCH.ALL_ARCHITECTURES);
    fileDrop.setExpectations(
        new InjectExpectation.EXPECTATION_TYPE[] {
          InjectExpectation.EXPECTATION_TYPE.PREVENTION,
          InjectExpectation.EXPECTATION_TYPE.DETECTION
        });

    FileDrop saved = payloadRepository.save(fileDrop);
    synchroniseInjectorContractBasedOnPayload(
        saved,
        List.of(),
        domainService.upserts(
            Set.of(InjectorContractDomainDTO.fromDomain(PresetDomain.getEndpoint())),
            TenantContext.getCurrentTenant()),
        tagService.findOrCreateTagsFromNames(new HashSet<>(Set.of(OPENCTI_TAG_NAME))));
    return saved;
  }

  /**
   * Upsert for the Dynamic DNS Resolution payload, who run DNS Resolution by domain name given by
   * argument
   *
   * @return the Dynamic DNS Resolution payload
   */
  public DnsResolution getDynamicDnsResolutionPayload() {
    return payloadRepository
        .findById(DYNAMIC_DNS_RESOLUTION_UUID)
        .map(DnsResolution.class::cast)
        .orElseGet(this::createDynamicDnsResolutionPayload);
  }

  /**
   * Create for the Dynamic DNS Resolution payload, who run DNS Resolution by domain name given by
   * argument
   *
   * @return the created Dynamic DNS Resolution payload
   */
  @Lock(type = LockResourceType.PAYLOAD, key = DYNAMIC_DNS_RESOLUTION_UUID)
  private DnsResolution createDynamicDnsResolutionPayload() {
    DnsResolution dynamicDnsResolutionPayload = new DnsResolution();
    dynamicDnsResolutionPayload.setId(DYNAMIC_DNS_RESOLUTION_UUID);
    dynamicDnsResolutionPayload.setHostname(DYNAMIC_DNS_RESOLUTION_HOSTNAME_VARIABLE);
    dynamicDnsResolutionPayload.setName("Dynamic DNS Resolution");
    dynamicDnsResolutionPayload.setDescription("Dynamic DNS Resolution by argument");
    dynamicDnsResolutionPayload.setStatus(Payload.PAYLOAD_STATUS.VERIFIED);
    dynamicDnsResolutionPayload.setSource(Payload.PAYLOAD_SOURCE.FILIGRAN);
    dynamicDnsResolutionPayload.setType(DnsResolution.DNS_RESOLUTION_TYPE);
    dynamicDnsResolutionPayload.setPlatforms(ALL_PLATFORMS);
    dynamicDnsResolutionPayload.setExecutionArch(Payload.PAYLOAD_EXECUTION_ARCH.ALL_ARCHITECTURES);

    PayloadArgument argument = new PayloadArgument();
    argument.setType(ArgumentType.Text);
    argument.setKey(DYNAMIC_DNS_RESOLUTION_HOSTNAME_KEY);
    argument.setDefaultValue("filigran.io");
    dynamicDnsResolutionPayload.setArguments(new ArrayList<>(List.of(argument)));

    dynamicDnsResolutionPayload.setExpectations(
        new InjectExpectation.EXPECTATION_TYPE[] {
          InjectExpectation.EXPECTATION_TYPE.PREVENTION,
          InjectExpectation.EXPECTATION_TYPE.DETECTION
        });

    DnsResolution saved = payloadRepository.save(dynamicDnsResolutionPayload);
    synchroniseInjectorContractBasedOnPayload(
        saved,
        List.of(),
        domainService.upsertDomainEntities(
            Set.of(
                PresetDomain.getEndpoint(),
                PresetDomain.getNetwork(),
                PresetDomain.getUrlFiltering()),
            TenantContext.getCurrentTenant()),
        tagService.findOrCreateTagsFromNames(new HashSet<>(Set.of(OPENCTI_TAG_NAME))));
    return saved;
  }
}
