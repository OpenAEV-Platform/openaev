package io.openaev.service;

import static io.openaev.config.SessionHelper.currentUser;
import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS;
import static io.openaev.database.model.InjectorContract.PREDEFINED_EXPECTATIONS;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.helper.StreamHelper.iterableToSet;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationCriteriaBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.*;
import io.openaev.database.repository.*;
import io.openaev.database.specification.InjectSpecification;
import io.openaev.database.specification.SpecificationUtils;
import io.openaev.injector_contract.fields.ContractFieldType;
import io.openaev.rest.atomic_testing.form.*;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.telemetry.metric_collectors.ActionMetricCollector;
import io.openaev.utils.mapper.InjectMapper;
import io.openaev.utils.mapper.PayloadMapper;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Join;
import jakarta.transaction.Transactional;
import java.util.*;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class AtomicTestingService {

  @Resource protected ObjectMapper mapper;
  private final InjectMapper injectMapper;
  private final ActionMetricCollector actionMetricCollector;

  private final AssetGroupRepository assetGroupRepository;
  private final AssetRepository assetRepository;
  private final PayloadMapper payloadMapper;
  private final InjectRepository injectRepository;
  private final InjectorContractRepository injectorContractRepository;
  private final UserRepository userRepository;
  private final TeamRepository teamRepository;
  private final TagRepository tagRepository;
  private final DocumentRepository documentRepository;
  private final AssetGroupService assetGroupService;
  private final UserService userService;
  private final InjectSearchService injectSearchService;
  private final InjectService injectService;
  private final GrantService grantService;

  // -- CRUD --

  public InjectResultOverviewOutput findById(String injectId) {
    Optional<Inject> inject = injectRepository.findWithStatusById(injectId);

    if (inject.isPresent()) {
      List<AssetGroup> computedAssetGroup =
          inject.get().getAssetGroups().stream()
              .map(assetGroupService::computeDynamicAssets)
              .toList();
      inject.get().getAssetGroups().clear();
      inject.get().getAssetGroups().addAll(computedAssetGroup);
    }
    return inject
        .map(injectMapper::toInjectResultOverviewOutput)
        .orElseThrow(ElementNotFoundException::new);
  }

  public StatusPayloadOutput findPayloadOutputByInjectId(String injectId) {
    Optional<Inject> inject = injectRepository.findById(injectId);
    return payloadMapper.getStatusPayloadOutputFromInject(inject);
  }

  @Transactional
  public InjectResultOverviewOutput createOrUpdate(AtomicTestingInput input, String injectId) {
    Inject injectToSave = new Inject();
    if (injectId != null) {
      injectToSave = injectRepository.findById(injectId).orElseThrow();
    }

    InjectorContract injectorContract =
        injectorContractRepository
            .findById(input.getInjectorContract())
            .orElseThrow(ElementNotFoundException::new);
    ObjectNode finalContent = input.getContent();
    // Set expectations
    if (injectId == null) {
      finalContent = setExpectations(input, injectorContract, finalContent);
    }
    injectToSave.setTitle(input.getTitle());
    injectToSave.setContent(finalContent);
    injectToSave.setInjectorContract(injectorContract);
    injectToSave.setAllTeams(input.isAllTeams());
    injectToSave.setDescription(input.getDescription());
    injectToSave.setDependsDuration(0L);
    injectToSave.setUser(
        userRepository
            .findById(currentUser().getId())
            .orElseThrow(() -> new ElementNotFoundException("Current user not found")));
    injectToSave.setExercise(null);

    // Set dependencies
    injectToSave.setTeams(fromIterable(teamRepository.findAllById(input.getTeams())));
    injectToSave.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));
    injectToSave.setAssets(fromIterable(this.assetRepository.findAllById(input.getAssets())));
    injectToSave.setAssetGroups(
        fromIterable(this.assetGroupRepository.findAllById(input.getAssetGroups())));

    List<String> previousDocumentIds =
        injectToSave.getDocuments().stream()
            .map(InjectDocument::getDocument)
            .map(Document::getId)
            .toList();

    Inject finalInjectToSave = injectToSave;
    List<InjectDocument> injectDocuments =
        input.getDocuments().stream()
            .map(
                i -> {
                  if (!previousDocumentIds.contains(i.getDocumentId())) {
                    InjectDocument injectDocument = new InjectDocument();
                    injectDocument.setInject(finalInjectToSave);
                    injectDocument.setDocument(
                        documentRepository.findById(i.getDocumentId()).orElseThrow());
                    injectDocument.setAttached(i.isAttached());
                    return injectDocument;
                  }
                  return null;
                })
            .filter(Objects::nonNull)
            .toList();
    injectToSave.getDocuments().addAll(injectDocuments);
    if (injectId == null) {
      actionMetricCollector.addAtomicTestingCreatedCount();
    }
    Inject inject = injectRepository.save(injectToSave);
    return injectMapper.toInjectResultOverviewOutput(inject);
  }

  private ObjectNode setExpectations(
      AtomicTestingInput input, InjectorContract injectorContract, ObjectNode finalContent) {
    if (input.getContent() == null
        || input.getContent().get(CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS) == null
        || input.getContent().get(CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS).isEmpty()) {
      try {
        JsonNode jsonNode = mapper.readTree(injectorContract.getContent());
        List<JsonNode> contractElements =
            StreamSupport.stream(jsonNode.get("fields").spliterator(), false)
                .filter(
                    contractElement ->
                        contractElement
                            .get("type")
                            .asText()
                            .equals(ContractFieldType.Expectation.name().toLowerCase()))
                .toList();
        if (!contractElements.isEmpty()) {
          JsonNode contractElement = contractElements.getFirst();
          if (!contractElement.get(PREDEFINED_EXPECTATIONS).isNull()
              && !contractElement.get(PREDEFINED_EXPECTATIONS).isEmpty()) {
            finalContent = finalContent != null ? finalContent : mapper.createObjectNode();
            ArrayNode predefinedExpectations = mapper.createArrayNode();
            StreamSupport.stream(contractElement.get(PREDEFINED_EXPECTATIONS).spliterator(), false)
                .forEach(
                    predefinedExpectation -> {
                      ObjectNode newExpectation = predefinedExpectation.deepCopy();
                      newExpectation.put("expectation_score", 100);
                      predefinedExpectations.add(newExpectation);
                    });
            // We need the remove in case there are empty expectations because put is deprecated and
            // putifabsent doesn't replace empty expectations
            if (finalContent.has(CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS)
                && finalContent.get(CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS).isEmpty()) {
              finalContent.remove(CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS);
            }
            finalContent.putIfAbsent(
                CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS, predefinedExpectations);
          }
        }
      } catch (JsonProcessingException e) {
        log.error("Cannot open injector contract", e);
      }
    }
    return finalContent;
  }

  @Transactional
  public InjectResultOverviewOutput updateAtomicTestingTags(
      String injectId, AtomicTestingUpdateTagsInput input) {

    Inject inject = injectRepository.findById(injectId).orElseThrow();
    inject.setTags(iterableToSet(this.tagRepository.findAllById(input.getTagIds())));

    Inject saved = injectRepository.save(inject);
    return injectMapper.toInjectResultOverviewOutput(saved);
  }

  public void deleteAtomicTesting(String injectId) {
    injectService.delete(injectId);
  }

  // -- ACTIONS --

  public InjectResultOverviewOutput duplicate(String id) {
    this.actionMetricCollector.addAtomicTestingCreatedCount();
    return injectService.duplicate(id);
  }

  public InjectResultOverviewOutput launch(String id) {
    return injectService.launch(id);
  }

  @Transactional
  public InjectResultOverviewOutput relaunch(String id) {
    // Relaunching an atomic testing is considered as creating a new one.
    // Therefore, any grants created on the current atomic testing will have to be updated with the
    // new ID
    InjectResultOverviewOutput relaunched = injectService.relaunch(id);
    grantService.updateGrantsForNewResource(
        id, relaunched.getId(), Grant.GRANT_RESOURCE_TYPE.ATOMIC_TESTING);
    return relaunched;
  }

  // -- PAGINATION --

  /**
   * Search atomic testings with pagination and filtering. Atomic testings are injects that are not
   * part of any scenario or exercise (both fields are null). The search only fetches data according
   * to user permissions via the grant system.
   *
   * @param searchPaginationInput Pagination and filtering parameters
   * @return A paginated list of atomic testing results
   */
  public Page<InjectResultOutput> searchAtomicTestingsForCurrentUser(
      @NotNull final SearchPaginationInput searchPaginationInput) {
    Map<String, Join<Base, Base>> joinMap = new HashMap<>();

    // Atomic testings are injects where scenario and exercise are null. They are also subject to
    // the grant system.
    User currentUser = userService.currentUser();

    Specification<Inject> customSpec =
        Specification.where(InjectSpecification.isAtomicTesting())
            .and(
                SpecificationUtils.hasGrantAccess(
                    currentUser.getId(),
                    currentUser.isAdminOrBypass(),
                    currentUser.getCapabilities().contains(Capability.ACCESS_ASSESSMENT),
                    Grant.GRANT_TYPE.OBSERVER));

    return buildPaginationCriteriaBuilder(
        (Specification<Inject> specification,
            Specification<Inject> specificationCount,
            Pageable pageable) ->
            injectSearchService.injectResults(
                customSpec.and(specification),
                customSpec.and(specificationCount),
                pageable,
                joinMap),
        searchPaginationInput,
        Inject.class,
        joinMap);
  }
}
