package io.openaev.utils;

import static io.openaev.utils.ExecutionTraceUtils.completeTraces;

import io.openaev.database.model.ExecutionStatus;
import io.openaev.database.model.ExecutionTrace;
import io.openaev.database.model.ExecutionTraceStatus;
import io.openaev.database.model.InjectStatus;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Objects;

public class InjectStatusUtils {

  private InjectStatusUtils() {}

  /**
   * Compute the global execution status from a list of COMPLETE traces (one per agent).
   *
   * <p>Filters out informational/non-actionable traces (AGENT_INACTIVE, ASSET_AGENTLESS, INFO). If
   * none is left, nothing was executed: ERROR.
   */
  public static ExecutionStatus computeStatus(List<ExecutionTrace> traces) {
    return computeStatusFromTraceStatuses(traces.stream().map(ExecutionTrace::getStatus).toList());
  }

  /** {@link #computeStatus} on the statuses of the COMPLETE traces. */
  public static ExecutionStatus computeStatusFromTraceStatuses(
      List<ExecutionTraceStatus> statuses) {
    List<ExecutionTraceStatus> activeStatuses =
        statuses.stream().filter(status -> !status.isNotCounted()).toList();

    if (activeStatuses.isEmpty()) {
      return ExecutionStatus.ERROR;
    }

    boolean hasError = false;
    boolean hasSuccess = false;

    for (ExecutionTraceStatus status : activeStatuses) {
      if (status.isError()) {
        hasError = true;
      } else if (status.isSuccess()) {
        hasSuccess = true;
      }
    }

    if (hasError && !hasSuccess) {
      return ExecutionStatus.ERROR;
    }
    if (hasSuccess && !hasError) {
      return ExecutionStatus.EXECUTED;
    }
    return ExecutionStatus.PARTIAL;
  }

  /** A COMPLETE trace as the status rule needs it: its status and its agent, null for none. */
  public record CompleteTrace(ExecutionTraceStatus status, @Nullable String agentId) {}

  /**
   * The status shown to a reader who may not see the trace of every agent: the marking filter hides
   * the traces of an agent whose asset is above the reader's clearance, without saying it did.
   *
   * <p>The stored status is compared with what the reader sees through the agents the inject was
   * dispatched to, a count the marking filter does not hide: each of them ends with exactly one
   * COMPLETE trace of its own (its result, a timeout, a clearance refusal or a dispatch error).
   *
   * <ul>
   *   <li>In flight, or dispatched to no agent: the stored status, nothing can be hidden.
   *   <li>Exactly the dispatched agents visible: the stored status, as the admin sees it, including
   *       a status set without traces.
   *   <li>No counted trace visible: {@link ExecutionStatus#NOT_VISIBLE}, which tells nothing about
   *       the hidden agents.
   *   <li>Otherwise: recomputed from the visible COMPLETE traces, those with no agent included (the
   *       platform's own, which the marking filter never hides).
   * </ul>
   *
   * <p>No other shortcut returns the stored status: a COMPLETE trace of the platform can close an
   * inject whose stored verdict also counts hidden agents, and more visible agents than dispatched
   * ones means an agent reported without being dispatched, so the count no longer proves that none
   * is hidden.
   *
   * @param expectedAgentCount the agents the inject was dispatched to; null when it was dispatched
   *     to none, or launched before the count existed and still in flight (the closed ones are
   *     backfilled by {@code V6_20261120120000000})
   * @param visibleCompleteTraces the COMPLETE traces the reader can see
   */
  public static ExecutionStatus visibleStatus(
      ExecutionStatus stored,
      @Nullable Integer expectedAgentCount,
      List<CompleteTrace> visibleCompleteTraces) {
    if (!stored.isClosed() || expectedAgentCount == null || expectedAgentCount == 0) {
      return stored;
    }
    long visibleAgents =
        visibleCompleteTraces.stream()
            .map(CompleteTrace::agentId)
            .filter(Objects::nonNull)
            .distinct()
            .count();
    if (visibleAgents == expectedAgentCount) {
      return stored;
    }
    List<ExecutionTraceStatus> visibleStatuses =
        visibleCompleteTraces.stream().map(CompleteTrace::status).toList();
    if (visibleStatuses.stream().allMatch(ExecutionTraceStatus::isNotCounted)) {
      return ExecutionStatus.NOT_VISIBLE;
    }
    return computeStatusFromTraceStatuses(visibleStatuses);
  }

  /**
   * {@link #visibleStatus(ExecutionStatus, Integer, List)} of a status whose traces were loaded in
   * the reader's transaction, so without the traces their clearance hides.
   */
  public static ExecutionStatus visibleStatus(InjectStatus status) {
    return visibleStatus(
        status.getName(),
        status.getExpectedAgentCount(),
        completeTraces(status.getTraces())
            .map(
                trace ->
                    new CompleteTrace(
                        trace.getStatus(),
                        trace.getAgent() == null ? null : trace.getAgent().getId()))
            .toList());
  }
}
