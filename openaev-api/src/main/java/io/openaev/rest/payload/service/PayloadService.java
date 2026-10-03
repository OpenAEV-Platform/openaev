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
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.database.raw.RawPayloadRelatedIds;
import io.openaev.database.repository.*;
import io.openaev.database.specification.PayloadSpecification;
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
import io.openaev.rest.inject.service.InjectIndexCleanupService;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.rest.injector_contract.form.InjectorContractDomainDTO;
import io.openaev.rest.payload.PayloadUtils;
import io.openaev.rest.payload.output.PayloadOutput;
import io.openaev.rest.tag.TagService;
import io.openaev.service.UserService;
import io.openaev.service.chaining.ChainingStepCleanupService;
import io.openaev.telemetry.metric_collectors.ResultsMetricCollector;
import io.openaev.utils.mapper.PayloadMapper;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Slf4j
public class PayloadService {

  public static final String DYNAMIC_DNS_RESOLUTION_HOSTNAME_KEY = "dynamic_hostname_key";
  public static final String DYNAMIC_DNS_RESOLUTION_HOSTNAME_VARIABLE =
      "#{" + DYNAMIC_DNS_RESOLUTION_HOSTNAME_KEY + "}";
  private static final String DYNAMIC_DNS_RESOLUTION_UUID = "ff16dc60-ea6f-4925-8509-20557e09c676";

  // -- IOC VALIDATION DYNAMIC PAYLOADS --
  // Per-tenant, per-executor singleton Command payloads carrying the benign test of one IOC. Their
  // values are passed per inject through the inject content, keyed by these argument keys.
  public static final String IOC_VALIDATION_HOST_KEY = "ioc_validation_host";
  public static final String IOC_VALIDATION_PORT_KEY = "ioc_validation_port";
  public static final String IOC_VALIDATION_URL_KEY = "ioc_validation_url";
  public static final String IOC_VALIDATION_PROXY_KEY = "ioc_validation_proxy";
  public static final String IOC_VALIDATION_VALUE_KEY = "ioc_validation_value";
  public static final String IOC_VALIDATION_FILE_NAME_KEY = "ioc_validation_file_name";
  // Names the temporary directory the file-drop surrogate is written to, owned by one inject.
  public static final String IOC_VALIDATION_RUN_KEY = "ioc_validation_run";
  static final String IOC_VALIDATION_INVALID_FILE_DROP =
      "OpenAEV IOC validation: the run must be 32 lowercase hexadecimal characters and the"
          + " surrogate file name a plain file name";
  private static final Pattern IOC_VALIDATION_RUN_PATTERN = Pattern.compile("[0-9a-f]{32}");
  public static final String IOC_VALIDATION_WINDOWS_EXECUTOR = "psh";
  public static final String IOC_VALIDATION_POSIX_EXECUTOR = "sh";
  private static final String IOC_VALIDATION_PAYLOAD_NAMESPACE =
      "6f6d2a7b-6c90-4a3a-8d2b-2f2c9a0d7e10";

  @Resource protected ObjectMapper mapper;

  private final PayloadRepository payloadRepository;
  private final InjectorRepository injectorRepository;
  private final InjectorContractRepository injectorContractRepository;
  private final ExpectationBuilderService expectationBuilderService;
  private final UserService userService;
  private final DocumentService documentService;
  private final PayloadUtils payloadUtils;
  private final ResultsMetricCollector resultsMetricCollector;
  private final DomainService domainService;
  private final TagService tagService;
  private final InjectIndexCleanupService injectIndexCleanupService;
  private final ChainingStepCleanupService chainingStepCleanupService;
  private final InjectorContractService injectorContractService;
  private final TenantWriteScopeResolver writeScopeResolver;

  private final PayloadMapper payloadMapper;

  public InjectorContract synchroniseInjectorContractBasedOnPayload(
      Payload payload, List<AttackPattern> attackPatterns, Set<Domain> domains, Set<Tag> tags) {
    // A contract lives in its payload's tenant. findAllByPayloads is scoped only by the ambient
    // TxCtx, which for a global (non-selector) request authorizes every tenant the caller can see -
    // built-in payload injectors repeat across tenants, so the raw result can mix several tenants'
    // rows. Narrow to the payload's own tenant before picking the reference injector and building
    // the injector links, otherwise the contract (stamped on the payload's tenant) could reference
    // another tenant's injector or fail on the injectors_injector_contracts FK.
    String payloadTenantId = payload.getTenant().getId();
    List<Injector> injectors =
        this.injectorRepository.findAllByPayloads(true).stream()
            .filter(injector -> payloadTenantId.equals(injector.getTenantId()))
            .toList();

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
    targetedAssetField.setArgumentType(payloadArgument.getType());

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
    ContractExpectations expectationsField =
        createContractExpectationsBasedOnPayload(
            payload.getExpectations(), payload.getExpectedSecurityPlatforms());
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
                if (PrimitiveType.TargetedAsset == payloadArgument.getType()) {
                  List<ContractElement> targetedAssetsFields =
                      targetedAssetFields(payloadArgument.getKey(), payloadArgument);
                  targetedAssetsFields.forEach(builder::mandatory);
                } else {
                  ContractElement textField =
                      textField(
                          payloadArgument.getKey(),
                          payloadArgument.getKey(),
                          payloadArgument.getDefaultValue());
                  textField.setArgumentType(payloadArgument.getType());
                  builder.mandatory(textField);
                }
              });
    }
    // A payload with no platforms must never crash contract building: Arrays.asList(null) throws an
    // NPE, turning a bad/partial update into a 500. Treat a missing platform array as "no
    // platforms".
    Endpoint.PLATFORM_TYPE[] payloadPlatforms = payload.getPlatforms();
    return executableContract(
        contractConfig,
        contractId,
        Map.of(en, payload.getName(), fr, payload.getName()),
        builder.build(),
        payloadPlatforms == null ? List.of() : Arrays.asList(payloadPlatforms),
        true,
        domains);
  }

  private ContractExpectations createContractExpectationsBasedOnPayload(
      BaseInjectExpectation.EXPECTATION_TYPE[] expectationTypes,
      Map<BaseInjectExpectation.EXPECTATION_TYPE, List<SecurityPlatform.SECURITY_PLATFORM_TYPE>>
          expectedSecurityPlatforms) {
    Expectation preventionExpectation =
        withExpectedSecurityPlatforms(
            this.expectationBuilderService.buildPreventionExpectation(),
            BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION,
            expectedSecurityPlatforms);
    Expectation detectionExpectation =
        withExpectedSecurityPlatforms(
            this.expectationBuilderService.buildDetectionExpectation(),
            BaseInjectExpectation.EXPECTATION_TYPE.DETECTION,
            expectedSecurityPlatforms);
    Expectation vulnerableExpectation =
        withExpectedSecurityPlatforms(
            this.expectationBuilderService.buildVulnerabilityExpectation(),
            BaseInjectExpectation.EXPECTATION_TYPE.VULNERABILITY,
            expectedSecurityPlatforms);

    if (expectationTypes != null) {
      for (BaseInjectExpectation.EXPECTATION_TYPE type : expectationTypes) {
        switch (type) {
          case DETECTION -> detectionExpectation.setPredefined(true);
          case PREVENTION -> preventionExpectation.setPredefined(true);
          case VULNERABILITY -> vulnerableExpectation.setPredefined(true);
          default -> throw new IllegalArgumentException("Unsupported expectation type: " + type);
        }
      }
    }

    return expectationsField(
        List.of(detectionExpectation, preventionExpectation, vulnerableExpectation));
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

  /**
   * Applies the OpenAEV rule for available expectations: only MANUAL is multi-selectable, all other
   * types are single-select.
   */
  private Expectation withExpectedMultiSelectableFlag(Expectation expectation) {
    expectation.setMultiSelectable(
        BaseInjectExpectation.EXPECTATION_TYPE.MANUAL.equals(expectation.getType()));
    return expectation;
  }

  /**
   * Applies the payload's optional expected security platform types to the predefined expectation
   * of the given type. Absent / empty means "any platform" (legacy behaviour), so the expectation
   * keeps its empty list.
   */
  private Expectation withExpectedSecurityPlatforms(
      Expectation expectation,
      BaseInjectExpectation.EXPECTATION_TYPE type,
      Map<BaseInjectExpectation.EXPECTATION_TYPE, List<SecurityPlatform.SECURITY_PLATFORM_TYPE>>
          expectedSecurityPlatforms) {
    if (expectedSecurityPlatforms != null) {
      List<SecurityPlatform.SECURITY_PLATFORM_TYPE> expected = expectedSecurityPlatforms.get(type);
      if (expected != null && !expected.isEmpty()) {
        expectation.setExpectedSecurityPlatformTypes(new ArrayList<>(expected));
      }
    }
    return expectation;
  }

  public PayloadCreationService.PayloadInjectorContractCreationResult duplicate(
      @NotBlank final String payloadId) {
    Payload origin =
        this.payloadRepository
            .findById(payloadId)
            .orElseThrow(() -> new ElementNotFoundException("Payload not found: " + payloadId));
    // Telemetry: one payload duplicated (community payload customization signal),
    // counted only once the origin payload is known to exist.
    resultsMetricCollector.recordPayloadDuplicated();
    Optional<InjectorContract> originInjectorContract =
        injectorContractRepository.findInjectorContractByPayload(origin);

    Payload duplicatedPayload = generateDuplicatedPayload(origin);
    // A duplicate is a new manual payload: it is authored by the user performing the
    // duplication. System flows without an authenticated user keep the author copied
    // from the origin.
    User duplicatingUser = userService.currentUserOrNull();
    if (duplicatingUser != null) {
      duplicatedPayload.setAuthorUser(duplicatingUser);
      duplicatedPayload.setAuthorTeam(null);
      duplicatedPayload.setAuthorOrganization(null);
    }
    Payload duplicated = payloadRepository.save(duplicatedPayload);
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
      case AI_ATTACK -> {
        AiAttack originAiAttack = (AiAttack) Hibernate.unproxy(originalPayload);
        AiAttack duplicateAiAttack = new AiAttack();
        payloadUtils.duplicateCommonProperties(originAiAttack, duplicateAiAttack);
        // duplicateCommonProperties already copies the scalar AiAttack fields; re-copy the
        // mutable JSON-backed structures defensively so the duplicate never shares state with
        // the origin within the same persistence context.
        duplicateAiAttack.setMultiTurn(
            Optional.ofNullable(originAiAttack.getMultiTurn())
                .map(HashMap::new)
                .orElseGet(HashMap::new));
        duplicateAiAttack.setSuccessDetector(
            Optional.ofNullable(originAiAttack.getSuccessDetector())
                .map(HashMap::new)
                .orElseGet(HashMap::new));
        duplicateAiAttack.setConverters(
            Optional.ofNullable(originAiAttack.getConverters())
                .map(String[]::clone)
                .orElseGet(() -> new String[0]));
        yield duplicateAiAttack;
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
    BiFunction<Specification<Payload>, Pageable, Page<Payload>> grantFilteredFindAll =
        SpecificationUtils.withGrantFilter(
            this.payloadRepository,
            Grant.GRANT_TYPE.OBSERVER,
            currentUser.getId(),
            currentUser.isAdminOrBypass(),
            currentUser.getCapabilities().contains(Capability.ACCESS_PAYLOADS));
    return buildPaginationJPA(
        (spec, pageable) ->
            grantFilteredFindAll.apply(
                (spec == null ? Specification.<Payload>unrestricted() : spec)
                    .and(PayloadSpecification.withCollectorType()),
                pageable),
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
  public FileDrop getFileDropPayloadByDocument(TxCtx ctx, String documentId, Scenario scenario) {
    FileDrop fileDrop =
        payloadRepository
            .findByDocumentId(documentId)
            .orElseGet(() -> this.createFileDropPayload(ctx, documentId));
    Document document = this.documentService.document(documentId);
    document.getScenarios().add(scenario);
    this.documentService.save(document);
    return fileDrop;
  }

  /**
   * Create a FileDrop Payload with linked provided document id
   *
   * @param documentId to link to FileDrop Payload
   * @return created file drop payload
   */
  public FileDrop createFileDropPayload(TxCtx ctx, String documentId) {
    Document document = this.documentService.document(documentId);
    String writeTenant = writeScopeResolver.tenantForWrite(ctx, null);

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
        new BaseInjectExpectation.EXPECTATION_TYPE[] {
          BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION,
          BaseInjectExpectation.EXPECTATION_TYPE.DETECTION
        });
    fileDrop.setTenant(new Tenant(writeTenant));

    FileDrop saved = payloadRepository.save(fileDrop);
    synchroniseInjectorContractBasedOnPayload(
        saved,
        List.of(),
        domainService.upserts(
            Set.of(InjectorContractDomainDTO.fromDomain(PresetDomain.getEndpoint())), writeTenant),
        tagService.findOrCreateTagsFromNames(ctx, new HashSet<>(Set.of(OPENCTI_TAG_NAME))));
    return saved;
  }

  /**
   * Upsert for the Dynamic DNS Resolution payload, who run DNS Resolution by domain name given by
   * argument
   *
   * @return the Dynamic DNS Resolution payload
   */
  public DnsResolution getDynamicDnsResolutionPayload(TxCtx ctx) {
    String writeTenant = writeScopeResolver.tenantForWrite(ctx, null);
    String tenantScopedId = dynamicDnsResolutionIdFor(writeTenant);
    return payloadRepository
        .findById(tenantScopedId)
        .map(DnsResolution.class::cast)
        .orElseGet(() -> createDynamicDnsResolutionPayload(ctx, writeTenant, tenantScopedId));
  }

  /**
   * This built-in payload is a per-tenant singleton: the primary key used to be a single hardcoded
   * UUID shared by every tenant, which made a second tenant's creation collide on the row the first
   * tenant already owns (the v2 scope hides that row from the second tenant's read). Deriving the
   * id from the tenant keeps creation idempotent per tenant while giving each tenant its own row.
   *
   * <p>The default tenant keeps the legacy hardcoded id: any platform that ingested DNS-resolution
   * STIX data before this fix already holds a row there, with an injector contract and injects
   * pointing at it, and deriving a different id for the default tenant would make that existing row
   * invisible and grow a duplicate on every upgraded platform.
   */
  private String dynamicDnsResolutionIdFor(String tenantId) {
    if (Tenant.DEFAULT_TENANT_UUID.equals(tenantId)) {
      return DYNAMIC_DNS_RESOLUTION_UUID;
    }
    return UUID.nameUUIDFromBytes(
            (DYNAMIC_DNS_RESOLUTION_UUID + ":" + tenantId)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8))
        .toString();
  }

  /**
   * Create for the Dynamic DNS Resolution payload, who run DNS Resolution by domain name given by
   * argument
   *
   * @return the created Dynamic DNS Resolution payload
   */
  @Lock(type = LockResourceType.PAYLOAD, key = "#tenantId")
  private DnsResolution createDynamicDnsResolutionPayload(
      TxCtx ctx, String tenantId, String tenantScopedId) {
    DnsResolution dynamicDnsResolutionPayload = new DnsResolution();
    dynamicDnsResolutionPayload.setId(tenantScopedId);
    dynamicDnsResolutionPayload.setTenant(new Tenant(tenantId));
    dynamicDnsResolutionPayload.setHostname(DYNAMIC_DNS_RESOLUTION_HOSTNAME_VARIABLE);
    dynamicDnsResolutionPayload.setName("Dynamic DNS Resolution");
    dynamicDnsResolutionPayload.setDescription("Dynamic DNS Resolution by argument");
    dynamicDnsResolutionPayload.setStatus(Payload.PAYLOAD_STATUS.VERIFIED);
    dynamicDnsResolutionPayload.setSource(Payload.PAYLOAD_SOURCE.FILIGRAN);
    dynamicDnsResolutionPayload.setType(DnsResolution.DNS_RESOLUTION_TYPE);
    dynamicDnsResolutionPayload.setPlatforms(ALL_PLATFORMS);
    dynamicDnsResolutionPayload.setExecutionArch(Payload.PAYLOAD_EXECUTION_ARCH.ALL_ARCHITECTURES);

    PayloadArgument argument = new PayloadArgument();
    argument.setType(PrimitiveType.Text);
    argument.setKey(DYNAMIC_DNS_RESOLUTION_HOSTNAME_KEY);
    argument.setDefaultValue("filigran.io");
    dynamicDnsResolutionPayload.setArguments(new ArrayList<>(List.of(argument)));

    dynamicDnsResolutionPayload.setExpectations(
        new BaseInjectExpectation.EXPECTATION_TYPE[] {
          BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION,
          BaseInjectExpectation.EXPECTATION_TYPE.DETECTION
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
            tenantId),
        tagService.findOrCreateTagsFromNames(ctx, new HashSet<>(Set.of(OPENCTI_TAG_NAME))));
    return saved;
  }

  /**
   * Upserts the per-tenant, per-executor Command payload that runs the benign test of an IOC
   * validation kind (network connect, HTTP HEAD, log injection or file-drop surrogate). DNS
   * resolution is served by {@link #getDynamicDnsResolutionPayload} instead. The payload is a
   * singleton keyed by (kind, executor, tenant); callers pass the per-IOC values through the inject
   * content using the {@code IOC_VALIDATION_*_KEY} argument keys.
   *
   * @param executor the implant executor, {@code psh} (Windows) or {@code sh} (Linux/macOS)
   */
  public Command getIocValidationCommandPayload(
      TxCtx ctx, IocValidationTestKind kind, String executor) {
    String writeTenant = writeScopeResolver.tenantForWrite(ctx, null);
    String payloadId = iocValidationPayloadId(kind, executor, writeTenant);
    UUID payloadUuid = UUID.fromString(payloadId);
    payloadRepository.lockPayloadCreation(
        payloadUuid.getMostSignificantBits() ^ payloadUuid.getLeastSignificantBits());
    return payloadRepository
        .findById(payloadId)
        .map(Command.class::cast)
        .map(existing -> refreshIocValidationCommandPayload(ctx, existing, kind, executor))
        .orElseGet(() -> createIocValidationCommandPayload(ctx, kind, executor, writeTenant));
  }

  /**
   * A payload created by an earlier version keeps running its old command: it is brought back to
   * the current template (content, cleanup and arguments) the next time a validation uses it.
   */
  private Command refreshIocValidationCommandPayload(
      TxCtx ctx, Command existing, IocValidationTestKind kind, String executor) {
    boolean windows = IOC_VALIDATION_WINDOWS_EXECUTOR.equals(executor);
    List<String> arguments =
        iocValidationArguments(kind).stream().map(PayloadService::argumentSignature).toList();
    List<String> existingArguments =
        existing.getArguments() == null
            ? List.of()
            : existing.getArguments().stream().map(PayloadService::argumentSignature).toList();
    boolean upToDate =
        iocValidationCommandContent(kind, windows).equals(existing.getContent())
            && Objects.equals(
                iocValidationCleanupCommand(kind, windows), existing.getCleanupCommand())
            && arguments.equals(existingArguments);
    if (upToDate) {
      return existing;
    }
    applyIocValidationCommandTemplate(existing, kind, executor);
    return saveIocValidationCommandPayload(ctx, existing, existing.getTenant().getId());
  }

  /** The identity of the IOC validation payload of a kind, executor and tenant. */
  public static String iocValidationPayloadId(
      IocValidationTestKind kind, String executor, String tenantId) {
    return UUID.nameUUIDFromBytes(
            (IOC_VALIDATION_PAYLOAD_NAMESPACE + ":" + kind.name() + ":" + executor + ":" + tenantId)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8))
        .toString();
  }

  private Command createIocValidationCommandPayload(
      TxCtx ctx, IocValidationTestKind kind, String executor, String tenantId) {
    boolean windows = IOC_VALIDATION_WINDOWS_EXECUTOR.equals(executor);
    Command payload = new Command();
    payload.setId(iocValidationPayloadId(kind, executor, tenantId));
    payload.setTenant(new Tenant(tenantId));
    payload.setExecutor(executor);
    applyIocValidationCommandTemplate(payload, kind, executor);
    payload.setName(iocValidationPayloadName(kind, executor));
    payload.setDescription(
        "Benign OpenCTI IOC validation test (" + kind.toStix() + ") run via " + executor);
    payload.setStatus(Payload.PAYLOAD_STATUS.VERIFIED);
    payload.setSource(Payload.PAYLOAD_SOURCE.FILIGRAN);
    payload.setPlatforms(
        windows
            ? new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.Windows}
            : new Endpoint.PLATFORM_TYPE[] {
              Endpoint.PLATFORM_TYPE.Linux, Endpoint.PLATFORM_TYPE.MacOS
            });
    payload.setExecutionArch(Payload.PAYLOAD_EXECUTION_ARCH.ALL_ARCHITECTURES);
    payload.setExpectations(
        new BaseInjectExpectation.EXPECTATION_TYPE[] {
          BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION,
          BaseInjectExpectation.EXPECTATION_TYPE.DETECTION
        });
    return saveIocValidationCommandPayload(ctx, payload, tenantId);
  }

  private void applyIocValidationCommandTemplate(
      Command payload, IocValidationTestKind kind, String executor) {
    boolean windows = IOC_VALIDATION_WINDOWS_EXECUTOR.equals(executor);
    payload.setContent(iocValidationCommandContent(kind, windows));
    String cleanup = iocValidationCleanupCommand(kind, windows);
    payload.setCleanupExecutor(cleanup == null ? null : executor);
    payload.setCleanupCommand(cleanup);
    payload.setArguments(new ArrayList<>(iocValidationArguments(kind)));
  }

  private Command saveIocValidationCommandPayload(TxCtx ctx, Command payload, String tenantId) {
    Command saved = payloadRepository.save(payload);
    synchroniseInjectorContractBasedOnPayload(
        saved,
        List.of(),
        domainService.upsertDomainEntities(
            Set.of(PresetDomain.getEndpoint(), PresetDomain.getNetwork()), tenantId),
        tagService.findOrCreateTagsFromNames(ctx, new HashSet<>(Set.of(OPENCTI_TAG_NAME))));
    return saved;
  }

  private static String iocValidationPayloadName(IocValidationTestKind kind, String executor) {
    return switch (kind) {
      case NETWORK_TRAFFIC -> "IOC validation - network connect (" + executor + ")";
      case HTTP_HEAD -> "IOC validation - HTTP HEAD (" + executor + ")";
      case LOG_INJECTION -> "IOC validation - log injection (" + executor + ")";
      case FILE_DROP -> "IOC validation - file drop surrogate (" + executor + ")";
      case DNS_RESOLUTION ->
          throw new IllegalArgumentException("DNS resolution uses the dynamic DNS payload");
    };
  }

  static List<PayloadArgument> iocValidationArguments(IocValidationTestKind kind) {
    return switch (kind) {
      case NETWORK_TRAFFIC ->
          List.of(
              textArgument(IOC_VALIDATION_HOST_KEY, "127.0.0.1"),
              textArgument(IOC_VALIDATION_PORT_KEY, "443"));
      case HTTP_HEAD ->
          List.of(
              textArgument(IOC_VALIDATION_URL_KEY, "http://localhost"),
              textArgument(IOC_VALIDATION_PROXY_KEY, ""));
      case LOG_INJECTION -> List.of(textArgument(IOC_VALIDATION_VALUE_KEY, "benign"));
      // No default run: a shared directory would let two injects overwrite and clean up each
      // other's surrogate, so an inject without its own run is refused (mandatory argument).
      case FILE_DROP ->
          List.of(
              textArgument(IOC_VALIDATION_FILE_NAME_KEY, "benign.txt"),
              textArgument(IOC_VALIDATION_RUN_KEY, ""));
      case DNS_RESOLUTION ->
          throw new IllegalArgumentException("DNS resolution uses the dynamic DNS payload");
    };
  }

  /**
   * The inject content an IOC validation file drop is executed with, and displayed with (terminal
   * view, attack-path snapshot), so the audited command is the one that ran. The run stored on the
   * inject is only a seed: the run directory is named on the server after the seed and the inject
   * id, so an inject can never address the directory of another one, whatever its content says, and
   * its drop and its cleanup always meet in the same directory. A missing or malformed seed is left
   * as it is, so the endpoint refuses the inject before any file operation. Every other payload,
   * including a user payload with an argument of the same name, runs with the content unchanged.
   */
  public static ObjectNode iocValidationExecutionContent(
      ObjectNode content, Payload payload, String injectId) {
    if (!isIocValidationFileDropPayload(payload)) {
      return content;
    }
    String seed =
        content != null && content.hasNonNull(IOC_VALIDATION_RUN_KEY)
            ? content.get(IOC_VALIDATION_RUN_KEY).asText()
            : "";
    if (!IOC_VALIDATION_RUN_PATTERN.matcher(seed).matches()) {
      return content;
    }
    ObjectNode bound = content.deepCopy();
    bound.put(IOC_VALIDATION_RUN_KEY, iocValidationRunDirectory(injectId, seed));
    return bound;
  }

  /**
   * Whether the payload is the IOC validation file-drop singleton of its tenant, recognised by its
   * server-assigned identity and never by its argument names, which any payload author can choose.
   */
  static boolean isIocValidationFileDropPayload(Payload payload) {
    if (payload == null || payload.getId() == null || payload.getTenant() == null) {
      return false;
    }
    String tenantId = payload.getTenant().getId();
    return Stream.of(IOC_VALIDATION_WINDOWS_EXECUTOR, IOC_VALIDATION_POSIX_EXECUTOR)
        .anyMatch(
            executor ->
                iocValidationPayloadId(IocValidationTestKind.FILE_DROP, executor, tenantId)
                    .equals(payload.getId()));
  }

  /** 32 lowercase hexadecimal characters, distinct per inject. */
  static String iocValidationRunDirectory(String injectId, String seed) {
    return UUID.nameUUIDFromBytes(
            ("openaev-ioc-validation-run|" + injectId + "|" + seed)
                .getBytes(StandardCharsets.UTF_8))
        .toString()
        .replace("-", "");
  }

  private static String argumentSignature(PayloadArgument argument) {
    return argument.getKey() + "=" + Objects.toString(argument.getDefaultValue(), "");
  }

  private static PayloadArgument textArgument(String key, String defaultValue) {
    PayloadArgument argument = new PayloadArgument();
    argument.setType(PrimitiveType.Text);
    argument.setKey(key);
    argument.setDefaultValue(defaultValue);
    return argument;
  }

  /**
   * The benign command template per kind. Every action is read-only or writes a short marker: a TCP
   * connect-and-close (no payload), an HTTP HEAD through the configured egress proxy, one log line,
   * or a small text file named after the IOC. The file is written in a temporary directory owned by
   * the inject ({@code openaev-ioc-validation-<run>}), never directly in the temp directory, so it
   * can neither overwrite nor, at cleanup, delete a file of another application. Placeholders are
   * bound as shell variables by {@link io.openaev.utils.command.CommandArgumentBinder}, never
   * substituted verbatim.
   */
  static String iocValidationCommandContent(IocValidationTestKind kind, boolean windows) {
    String host = placeholder(IOC_VALIDATION_HOST_KEY);
    String port = placeholder(IOC_VALIDATION_PORT_KEY);
    String url = placeholder(IOC_VALIDATION_URL_KEY);
    String proxy = placeholder(IOC_VALIDATION_PROXY_KEY);
    String value = placeholder(IOC_VALIDATION_VALUE_KEY);
    if (windows) {
      return switch (kind) {
        case NETWORK_TRAFFIC ->
            "$client = New-Object System.Net.Sockets.TcpClient; try { [void]$client.ConnectAsync("
                + host
                + ", [int]"
                + port
                + ").Wait(5000) } catch { } finally { $client.Close() }";
        case HTTP_HEAD ->
            "try { Invoke-WebRequest -UseBasicParsing -Method Head -TimeoutSec 10 -Proxy "
                + proxy
                + " -Uri "
                + url
                + " | Out-Null } catch { }";
        case LOG_INJECTION ->
            "$message = 'OpenAEV IOC validation marker: ' + "
                + value
                + "; try { if (-not [System.Diagnostics.EventLog]::SourceExists('OpenAEV')) {"
                + " [System.Diagnostics.EventLog]::CreateEventSource('OpenAEV', 'Application') };"
                + " [System.Diagnostics.EventLog]::WriteEntry('OpenAEV', $message, 'Information',"
                + " 4242) } catch { Add-Content -Path (Join-Path ([System.IO.Path]::GetTempPath())"
                + " 'openaev-ioc-validation.log') -Value $message }";
        case FILE_DROP ->
            windowsRunDirectory()
                + "; [void][System.IO.Directory]::CreateDirectory($oaevIocDir);"
                + " Set-Content -LiteralPath (Join-Path $oaevIocDir $oaevIocFile)"
                + " -Value 'OpenAEV IOC validation benign surrogate'";
        case DNS_RESOLUTION ->
            throw new IllegalArgumentException("DNS resolution uses the dynamic DNS payload");
      };
    }
    return switch (kind) {
      case NETWORK_TRAFFIC ->
          "if command -v nc >/dev/null 2>&1; then nc -z -w 5 "
              + host
              + " "
              + port
              + "; else bash -c 'exec 3<>\"/dev/tcp/$1/$2\" && exec 3<&-' openaev "
              + host
              + " "
              + port
              + "; fi; true";
      case HTTP_HEAD ->
          // --noproxy '' overrides NO_PROXY / no_proxy: the request never bypasses the egress
          // proxy.
          "curl -sS -I -o /dev/null --connect-timeout 5 --max-time 10 --noproxy '' --proxy "
              + proxy
              + " "
              + url
              + "; true";
      case LOG_INJECTION ->
          "OAEV_IOC_MESSAGE=\"OpenAEV IOC validation marker: \""
              + value
              + "; logger -t openaev-ioc-validation -- \"$OAEV_IOC_MESSAGE\" 2>/dev/null"
              + " || printf '%s\\n' \"$OAEV_IOC_MESSAGE\""
              + " >> \"${TMPDIR:-/tmp}/openaev-ioc-validation.log\"; true";
      case FILE_DROP ->
          posixRunDirectory()
              + "; mkdir -p -m 700 \"$OAEV_IOC_DIR\""
              + " && printf 'OpenAEV IOC validation benign surrogate\\n'"
              + " > \"$OAEV_IOC_DIR/$OAEV_IOC_FILE\"; true";
      case DNS_RESOLUTION ->
          throw new IllegalArgumentException("DNS resolution uses the dynamic DNS payload");
    };
  }

  /**
   * The cleanup of a kind, {@code null} when the test leaves nothing behind. The file-drop cleanup
   * removes the surrogate, then the run directory only when it is empty: it never deletes anything
   * it did not create.
   */
  static String iocValidationCleanupCommand(IocValidationTestKind kind, boolean windows) {
    if (kind != IocValidationTestKind.FILE_DROP) {
      return null;
    }
    if (windows) {
      return windowsRunDirectory()
          + "; Remove-Item -LiteralPath (Join-Path $oaevIocDir $oaevIocFile)"
          + " -Force -ErrorAction SilentlyContinue;"
          + " try { [System.IO.Directory]::Delete($oaevIocDir) } catch { }";
    }
    return posixRunDirectory()
        + "; rm -f \"$OAEV_IOC_DIR/$OAEV_IOC_FILE\""
        + "; rmdir \"$OAEV_IOC_DIR\" 2>/dev/null; true";
  }

  /**
   * Sets {@code $oaevIocDir} and {@code $oaevIocFile} for the file drop and its cleanup. The run
   * and the file name are editable inject arguments, so they are checked on the endpoint before any
   * file operation: the run must be 32 lowercase hexadecimal characters and the file name a plain
   * name (no separator, no drive, not {@code .} or {@code ..}), otherwise the command stops with an
   * error and touches nothing, so neither can lead outside the run directory.
   */
  private static String windowsRunDirectory() {
    String run = placeholder(IOC_VALIDATION_RUN_KEY);
    String fileName = placeholder(IOC_VALIDATION_FILE_NAME_KEY);
    return "$oaevIocRun = "
        + run
        + "; $oaevIocFile = "
        + fileName
        + "; if ($oaevIocRun -cnotmatch '^[0-9a-f]{32}$' -or $oaevIocFile -notmatch"
        + " '^[^\\\\/:*?\"<>|]+$' -or $oaevIocFile -in @('.', '..')) { throw '"
        + IOC_VALIDATION_INVALID_FILE_DROP
        + "' }; $oaevIocDir = Join-Path ([System.IO.Path]::GetTempPath())"
        + " ('openaev-ioc-validation-' + $oaevIocRun)";
  }

  /**
   * The POSIX counterpart of {@link #windowsRunDirectory()}: {@code OAEV_IOC_DIR}, {@code
   * OAEV_IOC_FILE}.
   */
  private static String posixRunDirectory() {
    return "OAEV_IOC_RUN="
        + placeholder(IOC_VALIDATION_RUN_KEY)
        + "; OAEV_IOC_FILE="
        + placeholder(IOC_VALIDATION_FILE_NAME_KEY)
        + "; case \"$OAEV_IOC_RUN\" in *[!0123456789abcdef]*) OAEV_IOC_RUN= ;; esac;"
        + " case \"$OAEV_IOC_FILE\" in .|..|*/*) OAEV_IOC_FILE= ;; esac;"
        + " if [ \"${#OAEV_IOC_RUN}\" -ne 32 ] || [ -z \"$OAEV_IOC_FILE\" ]; then echo '"
        + IOC_VALIDATION_INVALID_FILE_DROP
        + "' >&2; exit 1; fi;"
        + " OAEV_IOC_DIR=\"${TMPDIR:-/tmp}/openaev-ioc-validation-$OAEV_IOC_RUN\"";
  }

  private static String placeholder(String argumentKey) {
    return "#{" + argumentKey + "}";
  }

  // Transactional so the payload delete and the chaining step sweep commit or roll back together:
  // without it the deleteById commits first and a sweep failure would leave the ghost steps this
  // cleanup exists to prevent. Same pattern as InjectorService.deleteInjector.
  @Transactional(rollbackFor = Exception.class)
  public void delete(String payloadId) {
    Payload payload =
        payloadRepository
            .findById(payloadId)
            .orElseThrow(() -> new ElementNotFoundException("Payload not found: " + payloadId));
    // Payload deletion cascades at the DB level to its injector contract and then to the injects
    // (both FKs are ON DELETE CASCADE): de-index the doomed injects explicitly, no JPA lifecycle
    // event fires for them. Chaining steps referencing that contract have no FK to cascade on
    // (the contract is only a JSON snapshot in step_data), so inventory the doomed contract rows
    // BEFORE the delete and sweep the orphaned step templates explicitly too. The inventory is
    // grouped by tenant and each sweep stays scoped to its own tenant: today a payload backs at
    // most one contract platform-wide (unique_injector_contract_payload), the grouping simply
    // keeps this path correct if that 1:1 constraint is ever relaxed.
    String tenantId = payload.getTenant().getId();
    Map<String, List<String>> cascadeDeletedContractIdsPerTenant =
        injectorContractService.findContractIdsByPayloadIdPerTenant(payloadId);
    List<String> cascadeDeletedInjectIds =
        injectIndexCleanupService.injectIdsByPayloadId(payloadId, tenantId);
    payloadRepository.deleteById(payloadId);
    injectIndexCleanupService.notifyEngineOfDeletedInjects(cascadeDeletedInjectIds);
    cascadeDeletedContractIdsPerTenant.forEach(
        (contractTenantId, contractIds) ->
            chainingStepCleanupService.deleteTemplateStepsByInjectorContractIds(
                contractIds, contractTenantId));
  }
}
