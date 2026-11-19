package io.openaev.processor.core;

import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS;
import static io.openaev.utils.mapper.InjectExpectationMapper.NODE_EXPECTATION_TYPE;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.InjectRepository;
import io.openaev.rest.payload.service.PayloadService;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.DataPackService;
import io.openaev.service.PreviewFeatureService;
import io.openaev.utils.injector_contract.InjectorContractContentUtils;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.StreamSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * One-shot migration giving the Logging expectation to the payloads created by hand before it
 * existed, and to their injects, so that they behave like the payloads created since.
 *
 * <p>A manual payload expecting Detection or Prevention gets Logging, with the security platforms
 * of its Detection (as the payload form does), and its contract is rebuilt: Logging becomes one of
 * its predefined expectations. Each inject of that contract which stores its own expectations and
 * expects Detection or Prevention then gets the contract's Logging expectation, so it does not
 * drift from its contract. An inject storing no expectations follows its contract already. Past
 * results are untouched: only the inject definitions change.
 *
 * <p>Imported payloads and external injectors are left out: their collectors and injectors rewrite
 * their contracts, and add Logging themselves once they support it.
 *
 * <p>While the Logging preview feature is off, the migration does nothing and is not recorded, so
 * it runs at the first startup with the feature on.
 */
@Component
@Slf4j
public class V20261119_Add_logging_expectation_to_manual_payloads extends RuntimeMigration {

  private static final int INJECT_CHUNK_SIZE = 100;
  private static final String NODE_EXPECTATION_SCORE = "expectation_score";
  private static final Set<EXPECTATION_TYPE> LOGGING_TRIGGERS =
      Set.of(EXPECTATION_TYPE.DETECTION, EXPECTATION_TYPE.PREVENTION);

  private final PreviewFeatureService previewFeatureService;
  private final InjectRepository injectRepository;
  private final PayloadService payloadService;
  private final InjectorContractContentUtils injectorContractContentUtils;

  public V20261119_Add_logging_expectation_to_manual_payloads(
      DataPackService dataPackService,
      PreviewFeatureService previewFeatureService,
      InjectRepository injectRepository,
      PayloadService payloadService,
      InjectorContractContentUtils injectorContractContentUtils) {
    super(dataPackService);
    this.previewFeatureService = previewFeatureService;
    this.injectRepository = injectRepository;
    this.payloadService = payloadService;
    this.injectorContractContentUtils = injectorContractContentUtils;
  }

  @Override
  protected boolean doMigrate(Tenant tenant) {
    if (!previewFeatureService.isFeatureEnabled(PreviewFeature.LOGGING_EXPECTATION)) {
      return false;
    }
    // Injects carry the v1 filter: the inject reads below are keyed by ids already scoped to the
    // tenant, the filter is armed for consistency with the other runtime migrations.
    enableV1TenantFilter(tenant);
    String tenantId = tenant.getId();
    List<String> payloadIds =
        payloadService.findAllBySource(tenantId, Payload.PAYLOAD_SOURCE.MANUAL).stream()
            .filter(this::needsLogging)
            .map(Payload::getId)
            .toList();

    int payloads = 0;
    int injects = 0;
    for (String payloadId : payloadIds) {
      // Re-fetch inside the loop: synchroniseInjectorContractBasedOnPayload clears the
      // persistence context, which detaches the entities loaded before.
      Optional<InjectorContract> loggingContract =
          addLogging(payloadService.payload(tenantId, payloadId));
      payloads++;
      if (loggingContract.isPresent()) {
        injects += addLoggingToInjects(tenantId, loggingContract.get());
      }
    }

    if (payloads > 0) {
      log.info(
          "Added Logging to {} manual payload(s) and {} inject(s) for tenant {}.",
          payloads,
          injects,
          tenantId);
    }
    return true;
  }

  private boolean needsLogging(Payload payload) {
    List<EXPECTATION_TYPE> expectations =
        payload.getExpectations() == null ? List.of() : Arrays.asList(payload.getExpectations());
    return !expectations.contains(EXPECTATION_TYPE.LOGGING)
        && expectations.stream().anyMatch(LOGGING_TRIGGERS::contains);
  }

  /**
   * Adds Logging to the payload and rebuilds its contract. Returns the rebuilt contract when it
   * offers Logging as a predefined expectation, empty when the payload has no contract: no inject
   * can use it then.
   */
  private Optional<InjectorContract> addLogging(Payload payload) {
    List<EXPECTATION_TYPE> expectations = new ArrayList<>(Arrays.asList(payload.getExpectations()));
    expectations.add(EXPECTATION_TYPE.LOGGING);
    payload.setExpectations(expectations.toArray(EXPECTATION_TYPE[]::new));
    Map<EXPECTATION_TYPE, List<SecurityPlatform.SECURITY_PLATFORM_TYPE>> platforms =
        new HashMap<>(
            payload.getExpectedSecurityPlatforms() == null
                ? Map.of()
                : payload.getExpectedSecurityPlatforms());
    List<SecurityPlatform.SECURITY_PLATFORM_TYPE> detectionPlatforms =
        platforms.get(EXPECTATION_TYPE.DETECTION);
    if (detectionPlatforms != null && !detectionPlatforms.isEmpty()) {
      platforms.put(EXPECTATION_TYPE.LOGGING, new ArrayList<>(detectionPlatforms));
    }
    payload.setExpectedSecurityPlatforms(platforms);
    return payloadService
        .saveAndRebuildContract(payload)
        .filter(contract -> loggingExpectation(contract).isPresent());
  }

  private int addLoggingToInjects(String tenantId, InjectorContract contract) {
    JsonNode logging = loggingExpectation(contract).orElseThrow();
    List<String> injectIds =
        injectRepository.findInjectIdsByInjectorContractIds(List.of(contract.getId()), tenantId);
    int updated = 0;
    for (int start = 0; start < injectIds.size(); start += INJECT_CHUNK_SIZE) {
      List<String> chunk =
          injectIds.subList(start, Math.min(start + INJECT_CHUNK_SIZE, injectIds.size()));
      for (Inject inject : injectRepository.findAllById(chunk)) {
        Optional<ObjectNode> content = withLogging(inject.getContent(), logging);
        if (content.isPresent()) {
          // Content-only update, as the expectations realignment does: it neither merges the
          // inject's associations nor touches its past expectations.
          injectRepository.updateContent(inject.getId(), content.get(), Instant.now());
          updated++;
        }
      }
    }
    return updated;
  }

  private Optional<JsonNode> loggingExpectation(InjectorContract contract) {
    return injectorContractContentUtils.getPredefinedExpectationNodes(contract).stream()
        .filter(node -> isType(node, EXPECTATION_TYPE.LOGGING))
        .findFirst();
  }

  /**
   * The inject content with the contract's Logging expectation added, or empty when the inject
   * stores no expectations (it follows its contract), already expects Logging, or expects neither
   * Detection nor Prevention. The inject's own content is left as it is.
   */
  private Optional<ObjectNode> withLogging(ObjectNode content, JsonNode logging) {
    JsonNode stored =
        content == null ? null : content.get(CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS);
    if (stored == null || !stored.isArray()) {
      return Optional.empty();
    }
    List<JsonNode> expectations = StreamSupport.stream(stored.spliterator(), false).toList();
    boolean hasLogging = expectations.stream().anyMatch(e -> isType(e, EXPECTATION_TYPE.LOGGING));
    boolean hasTrigger =
        expectations.stream()
            .anyMatch(e -> LOGGING_TRIGGERS.stream().anyMatch(type -> isType(e, type)));
    if (hasLogging || !hasTrigger) {
      return Optional.empty();
    }
    ObjectNode added = logging.deepCopy();
    // Same normalization as the contract expectations an inject inherits: a full score.
    added.put(NODE_EXPECTATION_SCORE, 100);
    ObjectNode updated = content.deepCopy();
    ((ArrayNode) updated.get(CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS)).add(added);
    return Optional.of(updated);
  }

  private static boolean isType(JsonNode expectation, EXPECTATION_TYPE type) {
    return type.name().equals(expectation.path(NODE_EXPECTATION_TYPE).asText());
  }
}
