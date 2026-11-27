package io.openaev.utils;

import io.openaev.database.model.*;
import io.openaev.rest.inject.form.InjectExecutionAction;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utility class for execution trace conversions.
 *
 * <p>Provides methods for converting between different execution status and action enumerations
 * used in inject execution tracking.
 *
 * <p>This is a utility class and cannot be instantiated.
 *
 * @see io.openaev.database.model.ExecutionTraceStatus
 * @see io.openaev.database.model.ExecutionTraceAction
 */
public class ExecutionTraceUtils {

  private ExecutionTraceUtils() {}

  static final String SIMULATION_TIMEOUT_MESSAGE = "Inject stopped due to simulation timeout.";

  /**
   * The messages of the COMPLETE/TIMEOUT traces the platform writes itself (threshold reaper and
   * simulation timeout), as opposed to a TIMEOUT completion an agent or an injector reports. A late
   * report only makes the platform's TIMEOUT incomplete. Matched on the start of the message, so a
   * message already completed with a late-report suffix still matches, and so do the traces stored
   * before this matcher existed (no schema change).
   */
  private static final Pattern PLATFORM_TIMEOUT_MESSAGE =
      Pattern.compile(
          "^(Agent .* did not respond within the \\d+ minutes threshold\\."
              + "|The inject did not complete within the \\d+ minutes threshold"
              + " and was marked as timed out\\."
              + "|"
              + Pattern.quote(SIMULATION_TIMEOUT_MESSAGE)
              + ")");

  // -- CONVERSION --

  /**
   * Converts an inject execution action to its corresponding trace action.
   *
   * @param action the inject execution action to convert
   * @return the corresponding execution trace action, defaults to EXECUTION for unmapped actions
   */
  public static ExecutionTraceAction convertExecutionAction(InjectExecutionAction action) {
    return switch (action) {
      case prerequisite_check -> ExecutionTraceAction.PREREQUISITE_CHECK;
      case prerequisite_execution -> ExecutionTraceAction.PREREQUISITE_EXECUTION;
      case cleanup_execution -> ExecutionTraceAction.CLEANUP_EXECUTION;
      case complete -> ExecutionTraceAction.COMPLETE;
      default -> ExecutionTraceAction.EXECUTION;
    };
  }

  // -- QUERY --

  /** Returns the COMPLETE traces, which close the execution of an agent or of the inject. */
  public static Stream<ExecutionTrace> completeTraces(List<ExecutionTrace> traces) {
    return traces.stream().filter(t -> ExecutionTraceAction.COMPLETE.equals(t.getAction()));
  }

  /** Returns the traces of the given agent, agent-less traces excluded. */
  public static Stream<ExecutionTrace> agentTraces(List<ExecutionTrace> traces, String agentId) {
    return traces.stream().filter(t -> isAgentTrace(t, agentId));
  }

  /**
   * Whether the trace is a COMPLETE/TIMEOUT the platform wrote (threshold reaper or simulation
   * timeout), never a TIMEOUT completion reported by an agent or an injector.
   */
  public static boolean isPlatformTimeoutTrace(ExecutionTrace trace) {
    return ExecutionTraceStatus.TIMEOUT.equals(trace.getStatus())
        && ExecutionTraceAction.COMPLETE.equals(trace.getAction())
        && trace.getMessage() != null
        && PLATFORM_TIMEOUT_MESSAGE.matcher(trace.getMessage()).lookingAt();
  }

  public static boolean isAgentTrace(ExecutionTrace trace, String agentId) {
    return trace.getAgent() != null && agentId.equals(trace.getAgent().getId());
  }

  /** Returns the IDs of agents that already have a COMPLETE trace. */
  public static Set<String> getCompletedAgentIds(List<ExecutionTrace> traces) {
    return completeTraces(traces)
        .filter(t -> t.getAgent() != null)
        .map(t -> t.getAgent().getId())
        .collect(Collectors.toSet());
  }

  // -- TRACE BUILDERS --

  /**
   * Adds a COMPLETE/TIMEOUT trace to the given inject status for an agent that did not respond
   * within the configured threshold.
   */
  public static void addTimeoutTrace(InjectStatus status, Agent agent, int thresholdMinutes) {
    status.addTrace(
        ExecutionTraceStatus.TIMEOUT,
        agentTimeoutMessage(agent.getExecutedByUser(), thresholdMinutes),
        ExecutionTraceAction.COMPLETE,
        agent);
  }

  /** The message of the TIMEOUT trace the reaper writes for an agent that did not respond. */
  public static String agentTimeoutMessage(String executedByUser, int thresholdMinutes) {
    return "Agent "
        + executedByUser
        + " did not respond within the "
        + thresholdMinutes
        + " minutes threshold.";
  }

  public static void addSimulationTimeoutTrace(InjectStatus status) {
    status.addTrace(
        ExecutionTraceStatus.TIMEOUT,
        SIMULATION_TIMEOUT_MESSAGE,
        ExecutionTraceAction.COMPLETE,
        null);
  }

  public static void addSimulationInterruptedTrace(InjectStatus status) {
    status.addTrace(
        ExecutionTraceStatus.INTERRUPTED,
        "Inject stopped due to simulation interruption.",
        ExecutionTraceAction.COMPLETE,
        null);
  }

  /**
   * Adds an agentless COMPLETE/INTERRUPTED trace recording that a user stopped a running atomic
   * testing, and who did.
   *
   * @param status the status of the atomic testing being stopped
   * @param stoppedBy the name (or email) of the user who stopped it
   */
  public static void addAtomicTestingStoppedTrace(InjectStatus status, String stoppedBy) {
    status.addTrace(
        ExecutionTraceStatus.INTERRUPTED,
        "Atomic testing stopped by " + stoppedBy + ".",
        ExecutionTraceAction.COMPLETE,
        null);
  }

  public static void addSimulationNoMoreProgressTrace(InjectStatus status) {
    status.addTrace(
        ExecutionTraceStatus.INTERRUPTED,
        "Inject stopped due to simulation reaching no more progress.",
        ExecutionTraceAction.COMPLETE,
        null);
  }

  /**
   * Adds an agentless COMPLETE/TIMEOUT trace for an inject that has no agent to attribute a timeout
   * to (network scanners such as Nuclei target assets without an agent). Without it, a
   * stuck-PENDING agentless inject is finalized ERROR from an empty COMPLETE-trace list and the UI
   * shows a red inject with no explanation beyond the initial "waiting to be consumed" trace.
   */
  public static void addAgentlessTimeoutTrace(InjectStatus status, int thresholdMinutes) {
    status.addTrace(
        ExecutionTraceStatus.TIMEOUT,
        agentlessTimeoutMessage(thresholdMinutes),
        ExecutionTraceAction.COMPLETE,
        null);
  }

  /** The message of the TIMEOUT trace the reaper writes for an agent-less inject. */
  public static String agentlessTimeoutMessage(int thresholdMinutes) {
    return "The inject did not complete within the "
        + thresholdMinutes
        + " minutes threshold and was marked as timed out.";
  }

  /**
   * Adds a START/INFO trace to the given inject status indicating that an implant was spawned by
   * the agent.
   */
  public static void addJobRetrievalTrace(InjectStatus status, Agent agent) {
    status.addTrace(
        ExecutionTraceStatus.INFO, "Implant spawn by the agent", ExecutionTraceAction.START, agent);
  }

  // -- STATUS COMPUTATION --

  /**
   * Compute the aggregated status from all traces of a single agent. The logic is:
   *
   * <ul>
   *   <li>Non-cleanup errors take priority → return the granular error status
   *   <li>Prerequisite failed → PREREQUISITE_FAILED
   *   <li>If execution succeeded but cleanup failed → EXECUTED_WITH_CLEANUP_FAILURE
   *   <li>If all succeeded → EXECUTED
   * </ul>
   */
  public static ExecutionTraceStatus computeAgentTraceStatus(List<ExecutionTrace> agentTraces) {
    boolean hasSuccess = false;
    boolean hasCleanupError = false;
    boolean hasPrerequisiteError = false;
    Set<ExecutionTraceStatus> executionErrors =
        java.util.EnumSet.noneOf(ExecutionTraceStatus.class);

    for (ExecutionTrace trace : agentTraces) {
      ExecutionTraceStatus status = trace.getStatus();
      if (status.isSuccess()) {
        hasSuccess = true;
      } else if (status.isError()) {
        switch (trace.getAction()) {
          case CLEANUP_EXECUTION -> hasCleanupError = true;
          case PREREQUISITE_EXECUTION, PREREQUISITE_CHECK -> hasPrerequisiteError = true;
          default -> executionErrors.add(status);
        }
      }
    }

    // Non-cleanup, non-prerequisite error takes priority
    if (!executionErrors.isEmpty()) {
      return executionErrors.size() == 1
          ? executionErrors.iterator().next()
          : ExecutionTraceStatus.ERROR;
    }
    if (hasPrerequisiteError) {
      return ExecutionTraceStatus.PREREQUISITE_FAILED;
    }
    if (hasSuccess && hasCleanupError) {
      return ExecutionTraceStatus.EXECUTED_WITH_CLEANUP_FAILURE;
    }
    if (hasSuccess) {
      return ExecutionTraceStatus.EXECUTED;
    }
    return null;
  }
}
