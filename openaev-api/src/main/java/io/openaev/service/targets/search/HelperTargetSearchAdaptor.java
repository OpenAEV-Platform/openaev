package io.openaev.service.targets.search;

import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.ExecutionTrace;
import io.openaev.database.model.ExecutionTraceStatus;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectTarget;
import io.openaev.database.repository.ExecutionTraceRepository;
import io.openaev.service.InjectExpectationService;
import io.openaev.utils.InjectExpectationResultUtils;
import io.openaev.utils.InjectExpectationResultUtils.ExpectationResultsByType;
import io.openaev.utils.TargetType;
import io.openaev.utils.mapper.InjectExpectationMapper;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class HelperTargetSearchAdaptor {

  private final InjectExpectationService injectExpectationService;
  private final InjectExpectationMapper injectExpectationMapper;
  private final ExecutionTraceRepository executionTraceRepository;

  public InjectTarget buildTargetWithExpectations(
      Inject inject, Supplier<InjectTarget> targetSupplier, boolean allowVulnerability) {
    InjectTarget target = targetSupplier.get();

    List<BaseInjectExpectation> mergedExpectationsByInjectAndTargetAndTargetType =
        injectExpectationService.findMergedExpectationsByInjectAndTargetAndTargetType(
            inject.getId(), target.getId(), target.getTargetType());

    List<ExpectationResultsByType> results =
        injectExpectationMapper.extractExpectationResults(
            inject.getContent(),
            mergedExpectationsByInjectAndTargetAndTargetType,
            InjectExpectationResultUtils::getScores);

    for (ExpectationResultsByType result : results) {
      switch (result.type()) {
        case DETECTION -> target.setTargetDetectionStatus(result.avgResult());
        case PREVENTION -> target.setTargetPreventionStatus(result.avgResult());
        case VULNERABILITY -> {
          if (allowVulnerability) {
            target.setTargetVulnerabilityStatus(result.avgResult());
          }
        }
        case HUMAN_RESPONSE -> target.setTargetHumanResponseStatus(result.avgResult());
      }
    }

    applyExecutionStatus(inject, target);

    return target;
  }

  /**
   * Resolve the per-target EXECUTION outcome from that target's own execution traces so the "Attack
   * ended" beacon reflects the real result: green when the target succeeded, red when it failed,
   * orange only when the target's own traces mix success and failure (e.g. a team whose members had
   * different outcomes). Left as UNKNOWN when the target produced no counted trace, so the frontend
   * falls back to the inject-level status.
   */
  private void applyExecutionStatus(Inject inject, InjectTarget target) {
    List<ExecutionTrace> traces =
        findTargetTraces(inject.getId(), target.getId(), target.getTargetType());
    if (traces.isEmpty()) {
      return;
    }
    boolean hasSuccess = false;
    boolean hasError = false;
    for (ExecutionTrace trace : traces) {
      ExecutionTraceStatus status = trace.getStatus();
      if (status == null) {
        continue;
      }
      if (status.isSuccess()) {
        hasSuccess = true;
      } else if (status.isError()) {
        hasError = true;
      }
    }
    if (hasSuccess && hasError) {
      target.setTargetExecutionStatus(BaseInjectExpectation.EXPECTATION_STATUS.PARTIAL);
    } else if (hasError) {
      target.setTargetExecutionStatus(BaseInjectExpectation.EXPECTATION_STATUS.FAILED);
    } else if (hasSuccess) {
      target.setTargetExecutionStatus(BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS);
    }
  }

  private List<ExecutionTrace> findTargetTraces(
      String injectId, String targetId, String targetType) {
    try {
      return switch (TargetType.valueOf(targetType)) {
        case AGENT -> executionTraceRepository.findByInjectIdAndAgentId(injectId, targetId);
        // AI targets are plain assets, so their traces are asset-scoped.
        case ASSETS, AI_TARGETS ->
            executionTraceRepository.findByInjectIdAndAssetId(injectId, targetId);
        case TEAMS -> executionTraceRepository.findByInjectIdAndTeamId(injectId, targetId);
        case PLAYERS -> executionTraceRepository.findByInjectIdAndPlayerId(injectId, targetId);
        // Asset groups are aggregates with no direct traces of their own.
        default -> Collections.emptyList();
      };
    } catch (IllegalArgumentException e) {
      return Collections.emptyList();
    }
  }
}
