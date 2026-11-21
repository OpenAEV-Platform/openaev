package io.openaev.rest.scenario.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Scenario.APPROVAL_STATUS;
import io.openaev.database.model.User;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Data;

/**
 * Approval state and asset group run order of a scenario, returned by the approval endpoints
 * instead of the entity so that no lazy association is serialised after the transaction commits.
 */
@Data
public class ScenarioApprovalOutput {

  @JsonProperty("scenario_id")
  private String id;

  @JsonProperty("scenario_safe_mode")
  private boolean safeMode;

  @JsonProperty("scenario_approval_status")
  private APPROVAL_STATUS approvalStatus;

  @JsonProperty("scenario_approved_by")
  private String approvedBy;

  @JsonProperty("scenario_approved_at")
  private Instant approvedAt;

  @JsonProperty("scenario_asset_group_order")
  private String[] assetGroupOrder;

  /** Must be called inside the transaction that loaded the scenario. */
  public static ScenarioApprovalOutput fromScenario(@NotNull final Scenario scenario) {
    ScenarioApprovalOutput output = new ScenarioApprovalOutput();
    output.setId(scenario.getId());
    output.setSafeMode(scenario.isSafeMode());
    output.setApprovalStatus(scenario.getApprovalStatus());
    User approver = scenario.getApprovedBy();
    output.setApprovedBy(approver != null ? approver.getId() : null);
    output.setApprovedAt(scenario.getApprovedAt());
    output.setAssetGroupOrder(
        scenario.getAssetGroupOrder() == null
            ? new String[0]
            : scenario.getAssetGroupOrder().clone());
    return output;
  }
}
