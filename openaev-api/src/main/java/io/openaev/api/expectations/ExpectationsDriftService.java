package io.openaev.api.expectations;

import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS;
import static io.openaev.utils.mapper.InjectExpectationMapper.NODE_EXPECTATION_TYPE;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.api.expectations.dto.ExpectationsDriftOutput;
import io.openaev.api.expectations.dto.ExpectationsRealignOutput;
import io.openaev.context.BulkOperationContext;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.repository.InjectRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.utils.BulkDeleteChunkRunner;
import io.openaev.service.utils.BulkOperationMonitor;
import io.openaev.utils.injector_contract.InjectorContractContentUtils;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Detects and repairs "expectation drift": the divergence between the predefined expectations
 * currently exposed by an inject's injector contract (the security posture template) and the
 * expectations stored inside the inject content (inherited at creation time and deliberately kept
 * as-is afterwards, even when the contract evolves).
 *
 * <p>Drift is evaluated on the validation-defining attributes of an expectation: its type, its
 * group semantics and the security platform types expected to fulfil it. Tuning attributes (score,
 * name, description, expiration time) are deliberately ignored - users legitimately adjust those
 * per inject, and such adjustments are not a posture change.
 *
 * <p>Injects whose content carries no expectations are never drifted: they inherit the contract's
 * predefined expectations dynamically at execution time (see {@link
 * InjectorContractContentUtils#setExpectations}), so they always follow the current template.
 *
 * <p>Realignment overwrites the drifted injects' stored expectations with the contract's current
 * predefined expectations, chunk by chunk in short independent transactions, tracked as a massive
 * operation (header progress indicator, per-entity stream events suppressed).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExpectationsDriftService {

  /** Small chunks keep each realign transaction - and the row locks it holds - short. */
  private static final int CHUNK_SIZE = 25;

  private static final String NODE_EXPECTATION_GROUP = "expectation_expectation_group";
  private static final String NODE_EXPECTATION_SCORE = "expectation_score";

  private final InjectRepository injectRepository;
  private final InjectorContractContentUtils injectorContractContentUtils;
  private final BulkOperationMonitor bulkOperationMonitor;
  private final BulkDeleteChunkRunner chunkRunner;
  private final ObjectMapper mapper;

  // -- DETECTION --

  /** Computes the expectation drift report for all injects of a scenario. */
  public ExpectationsDriftOutput scenarioDrift(String scenarioId) {
    return computeDrift(injectRepository.findByScenarioId(scenarioId));
  }

  /** Computes the expectation drift report for all injects of a simulation. */
  public ExpectationsDriftOutput exerciseDrift(String exerciseId) {
    return computeDrift(injectRepository.findByExerciseId(exerciseId));
  }

  /** Computes the expectation drift report for a single inject (atomic testing). */
  public ExpectationsDriftOutput injectDrift(String injectId) {
    return computeDrift(List.of(resolveInject(injectId)));
  }

  private ExpectationsDriftOutput computeDrift(Collection<Inject> injects) {
    Map<String, List<String>> contractSignatureCache = new HashMap<>();
    int total = 0;
    int drifted = 0;
    for (Inject inject : injects) {
      if (!hasExpectationsContract(inject)) {
        continue;
      }
      total++;
      if (hasDrift(inject, contractSignatureCache)) {
        drifted++;
      }
    }
    return new ExpectationsDriftOutput(drifted > 0, drifted, total);
  }

  /**
   * Whether the stored expectations of the inject diverge from the predefined expectations
   * currently exposed by its injector contract.
   */
  public boolean hasDrift(Inject inject) {
    return hasDrift(inject, new HashMap<>());
  }

  /**
   * Cached variant for whole-scope walks: injects of a scenario or simulation massively share
   * contracts, and the contract-side signatures only depend on the contract, so re-parsing the
   * contract content per inject would be pure redundant work. The cache is scoped to one request:
   * contract content cannot change mid-walk.
   */
  private boolean hasDrift(Inject inject, Map<String, List<String>> contractSignatureCache) {
    InjectorContract contract = inject.getInjectorContract().orElse(null);
    if (contract == null
        || !injectorContractContentUtils.hasField(
            contract, CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS)) {
      return false;
    }
    List<String> injectSignatures = injectExpectationSignatures(inject);
    if (injectSignatures == null) {
      // No stored expectations: the inject inherits the contract's predefined expectations
      // dynamically at execution time, so it always follows the current template.
      return false;
    }
    List<String> contractSignatures =
        contractSignatureCache.computeIfAbsent(
            contract.getId(), id -> contractExpectationSignatures(contract));
    return !contractSignatures.equals(injectSignatures);
  }

  private boolean hasExpectationsContract(Inject inject) {
    return inject
        .getInjectorContract()
        .map(
            contract ->
                injectorContractContentUtils.hasField(
                    contract, CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS))
        .orElse(false);
  }

  private List<String> contractExpectationSignatures(InjectorContract contract) {
    return injectorContractContentUtils.getPredefinedExpectationNodes(contract).stream()
        .map(this::signature)
        .sorted()
        .toList();
  }

  /**
   * Canonical signatures of the expectations stored in the inject content, or {@code null} when the
   * content carries no expectations (the inject then follows the contract dynamically).
   */
  private List<String> injectExpectationSignatures(Inject inject) {
    ObjectNode content = inject.getContent();
    if (content == null) {
      return null;
    }
    JsonNode expectations = content.get(CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS);
    if (expectations == null || !expectations.isArray() || expectations.isEmpty()) {
      return null;
    }
    List<String> signatures = new ArrayList<>();
    expectations.forEach(expectation -> signatures.add(signature(expectation)));
    Collections.sort(signatures);
    return signatures;
  }

  /**
   * Canonical, order-insensitive signature of the validation-defining attributes of an expectation:
   * type, group semantics and expected security platform types. Tuning attributes (score, name,
   * description, expiration time) are excluded on purpose.
   */
  private String signature(JsonNode expectation) {
    String type = expectation.path(NODE_EXPECTATION_TYPE).asText("");
    boolean group = expectation.path(NODE_EXPECTATION_GROUP).asBoolean(false);
    List<String> platforms = new ArrayList<>();
    JsonNode platformsNode =
        expectation.get(InjectorContractContentUtils.NODE_EXPECTED_SECURITY_PLATFORM_TYPES);
    if (platformsNode != null && platformsNode.isArray()) {
      platformsNode.forEach(platform -> platforms.add(platform.asText()));
    }
    Collections.sort(platforms);
    return type + "|" + group + "|" + String.join(",", platforms);
  }

  // -- REALIGNMENT --

  /** Realigns all drifted injects of a scenario onto their contract templates. */
  public ExpectationsRealignOutput realignScenario(String scenarioId) {
    return realign(
        chunkRunner.call(() -> List.copyOf(injectRepository.findByScenarioId(scenarioId))));
  }

  /** Realigns all drifted injects of a simulation onto their contract templates. */
  public ExpectationsRealignOutput realignExercise(String exerciseId) {
    return realign(chunkRunner.call(() -> injectRepository.findByExerciseId(exerciseId)));
  }

  /** Realigns a single inject (atomic testing) onto its contract template. */
  public ExpectationsRealignOutput realignInject(String injectId) {
    return realign(chunkRunner.call(() -> List.of(resolveInject(injectId))));
  }

  /**
   * Overwrites the stored expectations of every drifted inject with the predefined expectations of
   * its injector contract. Callers must NOT be transactional: each chunk commits in its own short
   * transaction (through {@link BulkDeleteChunkRunner}, which also enables the tenant filter),
   * while the whole operation is tracked as a massive operation with aggregated progress events.
   */
  private ExpectationsRealignOutput realign(List<Inject> injects) {
    Map<String, List<String>> contractSignatureCache = new HashMap<>();
    List<Inject> drifted =
        injects.stream().filter(inject -> hasDrift(inject, contractSignatureCache)).toList();
    if (drifted.isEmpty()) {
      return new ExpectationsRealignOutput(0);
    }
    String operationId =
        bulkOperationMonitor.start("realign", "inject expectations", drifted.size());
    try {
      for (int start = 0; start < drifted.size(); start += CHUNK_SIZE) {
        List<Inject> chunk = drifted.subList(start, Math.min(start + CHUNK_SIZE, drifted.size()));
        // The suppression scope wraps the transaction proxy call (not just the work): lifecycle
        // events also fire during the commit-time flush, inside chunkRunner.call.
        BulkOperationContext.runSuppressed(
            () ->
                chunkRunner.call(
                    () -> {
                      chunk.forEach(this::applyContractExpectations);
                      return injectRepository.saveAll(chunk);
                    }));
        bulkOperationMonitor.progress(operationId, chunk.size());
      }
      bulkOperationMonitor.complete(operationId);
    } catch (RuntimeException e) {
      bulkOperationMonitor.fail(operationId);
      throw e;
    }
    log.info("Realigned expectations of {} injects onto their contract templates", drifted.size());
    return new ExpectationsRealignOutput(drifted.size());
  }

  private void applyContractExpectations(Inject inject) {
    InjectorContract contract =
        inject
            .getInjectorContract()
            .orElseThrow(() -> new IllegalStateException("Drifted inject without contract"));
    ArrayNode expectations = mapper.createArrayNode();
    List<JsonNode> predefinedNodes =
        injectorContractContentUtils.getPredefinedExpectationNodes(contract);
    for (JsonNode predefined : predefinedNodes) {
      ObjectNode aligned = predefined.deepCopy();
      // Same normalization as InjectorContractContentUtils.setExpectations: expectations
      // inherited from the contract always start with a full score.
      aligned.put(NODE_EXPECTATION_SCORE, 100);
      expectations.add(aligned);
    }
    ObjectNode content =
        inject.getContent() != null ? inject.getContent() : mapper.createObjectNode();
    content.set(CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS, expectations);
    inject.setContent(content);
    inject.setUpdatedAt(Instant.now());
  }

  private Inject resolveInject(String injectId) {
    return injectRepository
        .findById(injectId)
        .orElseThrow(() -> new ElementNotFoundException("Inject not found: " + injectId));
  }
}
