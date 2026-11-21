package io.openaev.service.scenario;

import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Scenario.APPROVAL_STATUS;
import io.openaev.database.model.User;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Safe mode, approval and asset group run order of a scenario built from a security coverage.
 *
 * <p>Rules, in order:
 *
 * <ol>
 *   <li>Safe mode: an inject whose contract and payload are not marked read-only is held (disabled)
 *       and the scenario waits for an approval; read-only injects run without one. A safe-mode
 *       scenario with only read-only injects never waits.
 *   <li>Approval required (outside safe mode): the scenario waits for an approval before its first
 *       run, whatever its injects.
 *   <li>Otherwise nothing waits.
 * </ol>
 *
 * <p>Approving records the author and the time, and turns the held injects back on.
 *
 * <p>The asset group order moves the injects targeting the first groups first: each group rank adds
 * {@link #ORDER_STEP_SECONDS} to the inject's start delay.
 */
@Service
public class ScenarioApprovalService {

  /** Delay added between two consecutive asset groups of the run order. */
  public static final long ORDER_STEP_SECONDS = 60L;

  /** What applying the gate to a scenario decided. */
  public record GateOutcome(boolean waits, List<Inject> heldInjects) {}

  /** True when the inject only reads its targets: its contract or its payload is read-only. */
  public static boolean isReadOnly(@NotNull Inject inject) {
    return inject.getInjectorContract().map(InjectorContract::isReadOnlyEffective).orElse(false);
  }

  /**
   * Applies safe mode and the approval setting to the scenario and its injects. A scenario already
   * approved keeps its approval for the injects it already had; a new inject that is not read-only
   * in safe mode makes it wait again.
   *
   * @param safeMode whether the scenario runs in safe mode
   * @param approvalRequired whether the tenant asks for an approval outside safe mode
   */
  public GateOutcome applyGate(
      @NotNull Scenario scenario,
      @NotNull Collection<Inject> injects,
      boolean safeMode,
      boolean approvalRequired) {
    scenario.setSafeMode(safeMode);
    List<Inject> held = new ArrayList<>();
    if (safeMode) {
      boolean alreadyApproved = APPROVAL_STATUS.APPROVED.equals(scenario.getApprovalStatus());
      for (Inject inject : injects) {
        if (!isReadOnly(inject) && !(alreadyApproved && inject.isEnabled())) {
          inject.setEnabled(false);
          held.add(inject);
        }
      }
      if (!held.isEmpty()) {
        markPending(scenario);
        return new GateOutcome(true, held);
      }
      if (!alreadyApproved) {
        scenario.setApprovalStatus(APPROVAL_STATUS.NOT_REQUIRED);
      }
      return new GateOutcome(false, held);
    }
    if (approvalRequired) {
      if (APPROVAL_STATUS.APPROVED.equals(scenario.getApprovalStatus())) {
        return new GateOutcome(false, held);
      }
      markPending(scenario);
      return new GateOutcome(true, held);
    }
    if (!APPROVAL_STATUS.APPROVED.equals(scenario.getApprovalStatus())) {
      scenario.setApprovalStatus(APPROVAL_STATUS.NOT_REQUIRED);
    }
    return new GateOutcome(false, held);
  }

  /**
   * Records the approval and turns the held injects back on.
   *
   * @return the injects turned back on
   */
  public List<Inject> approve(
      @NotNull Scenario scenario, @NotNull Collection<Inject> injects, @NotNull User approver) {
    if (!scenario.isWaitingForApproval()) {
      throw new IllegalStateException(
          "Scenario " + scenario.getId() + " is not waiting for approval");
    }
    List<Inject> released = new ArrayList<>();
    if (scenario.isSafeMode()) {
      for (Inject inject : injects) {
        if (!inject.isEnabled() && !isReadOnly(inject)) {
          inject.setEnabled(true);
          released.add(inject);
        }
      }
    }
    scenario.setApprovalStatus(APPROVAL_STATUS.APPROVED);
    scenario.setApprovedBy(approver);
    scenario.setApprovedAt(Instant.now());
    return released;
  }

  /**
   * Sets the order of the target asset groups and shifts the injects' start delay so that the
   * injects targeting the first groups start first. Groups not named in the order keep the current
   * delay (rank 0). Asset group ids that no inject of the scenario targets are refused.
   */
  public void applyAssetGroupOrder(
      @NotNull Scenario scenario,
      @NotNull List<String> order,
      @NotNull Collection<Inject> injects) {
    List<String> distinct = new ArrayList<>(new LinkedHashSet<>(order));
    Set<String> targeted = new LinkedHashSet<>();
    injects.forEach(i -> i.getAssetGroups().forEach(g -> targeted.add(g.getId())));
    List<String> unknown = distinct.stream().filter(id -> !targeted.contains(id)).toList();
    if (!unknown.isEmpty()) {
      throw new IllegalArgumentException(
          "Asset groups not targeted by this scenario: " + String.join(", ", unknown));
    }
    String[] previous =
        scenario.getAssetGroupOrder() == null ? new String[0] : scenario.getAssetGroupOrder();
    Map<String, Integer> previousRank = rankOf(List.of(previous));
    Map<String, Integer> rank = rankOf(distinct);
    for (Inject inject : injects) {
      long base = inject.getDependsDuration() == null ? 0L : inject.getDependsDuration();
      base -= ORDER_STEP_SECONDS * firstRank(inject, previousRank);
      inject.setDependsDuration(Math.max(0L, base) + ORDER_STEP_SECONDS * firstRank(inject, rank));
    }
    scenario.setAssetGroupOrder(distinct.toArray(String[]::new));
  }

  private static Map<String, Integer> rankOf(List<String> order) {
    Map<String, Integer> rank = new HashMap<>();
    for (int i = 0; i < order.size(); i++) {
      rank.putIfAbsent(order.get(i), i);
    }
    return rank;
  }

  /** The rank of the earliest ordered group the inject targets, 0 when none is ordered. */
  private static int firstRank(Inject inject, Map<String, Integer> rank) {
    return inject.getAssetGroups().stream()
        .map(AssetGroup::getId)
        .filter(rank::containsKey)
        .map(rank::get)
        .min(Comparator.naturalOrder())
        .orElse(0);
  }

  private static void markPending(Scenario scenario) {
    scenario.setApprovalStatus(APPROVAL_STATUS.PENDING);
    scenario.setApprovedBy(null);
    scenario.setApprovedAt(null);
  }
}
