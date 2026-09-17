package io.openaev.api.finding;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.FindingAggregationCategory;
import io.openaev.database.model.FindingLocationType;
import io.openaev.database.model.FindingTriageStatus;
import io.openaev.database.model.StableFindingCategory;
import io.openaev.database.model.StableFindingLifecycle;
import io.openaev.rest.asset.endpoint.form.EndpointSimple;
import io.openaev.rest.asset_group.form.AssetGroupSimple;
import io.openaev.rest.atomic_testing.form.TargetSimple;
import io.openaev.rest.exercise.form.ExerciseSimple;
import io.openaev.rest.inject.output.InjectSimple;
import io.openaev.rest.injector.output.InjectorSimple;
import io.openaev.rest.scenario.form.ScenarioSimple;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.Builder;

@Builder
@JsonInclude(NON_NULL)
public record StableFindingOutput(
    @JsonProperty("finding_id") String id,
    @JsonProperty("finding_legacy_id") String legacyId,
    @JsonProperty("finding_field") String field,
    @JsonProperty("finding_type") ContractOutputType type,
    @JsonProperty("finding_value") String value,
    @JsonProperty("finding_location") String location,
    @JsonProperty("finding_location_type") FindingLocationType locationType,
    @JsonProperty("finding_location_key") String locationKey,
    @JsonProperty("finding_name") String name,
    @JsonProperty("finding_title") String title,
    @JsonProperty("finding_tags") Set<String> tags,
    @JsonProperty("finding_created_at") Instant createdAt,
    @JsonProperty("finding_updated_at") Instant updatedAt,
    @JsonProperty("finding_human_updated_at") Instant humanUpdatedAt,
    @JsonProperty("finding_archived_at") Instant archivedAt,
    @JsonProperty("finding_source") InjectorSimple source,
    @JsonProperty("finding_inject") InjectSimple inject,
    @JsonProperty("finding_inject_id") String injectId,
    @JsonProperty("finding_simulation") ExerciseSimple simulation,
    @JsonProperty("finding_scenario") ScenarioSimple scenario,
    @JsonProperty("finding_asset_groups") Set<AssetGroupSimple> assetGroups,
    @JsonProperty("finding_assets") Set<EndpointSimple> assets,
    @JsonProperty("finding_users") Set<TargetSimple> users,
    @JsonProperty("finding_teams") Set<TargetSimple> teams,
    @JsonProperty("finding_occurrences") long occurrences,
    @JsonProperty("finding_assets_count") long assetsCount,
    @JsonProperty("finding_users_count") long usersCount,
    @JsonProperty("finding_teams_count") long teamsCount,
    @JsonProperty("finding_asset_groups_count") long assetGroupsCount,
    @JsonProperty("finding_triage_status") FindingTriageStatus triageStatus,
    @JsonProperty("finding_category") StableFindingCategory category,
    @JsonProperty("finding_aggregation_category") FindingAggregationCategory aggregationCategory,
    @JsonProperty("finding_lifecycle") StableFindingLifecycle lifecycle,
    @JsonProperty("finding_outcome") String outcome,
    @JsonProperty("finding_severity") String severity,
    @JsonProperty("finding_severity_id") Integer severityId,
    @JsonProperty("finding_source_finding_id") String sourceUid,
    @JsonProperty("finding_resource") String resource,
    @JsonProperty("finding_resource_uid") String resourceUid,
    @JsonProperty("finding_resource_name") String resourceName,
    @JsonProperty("finding_resource_type") String resourceType,
    @JsonProperty("finding_resource_service") String resourceService,
    @JsonProperty("finding_cloud_provider") @JsonInclude(ALWAYS) String cloudProvider,
    @JsonProperty("finding_cloud_account") String cloudAccount,
    @JsonProperty("finding_cloud_region") String cloudRegion,
    @JsonProperty("finding_description") String description,
    @JsonProperty("finding_evidence") String evidenceDetail,
    @JsonProperty("finding_status_detail") String statusDetail,
    @JsonProperty("finding_risk_details") String risk,
    @JsonProperty("finding_categories") List<String> categories,
    @JsonProperty("finding_mitre_attack") List<String> attackPatterns,
    @JsonProperty("finding_remediation") String remediation,
    @JsonProperty("finding_compliance") String compliance,
    @JsonProperty("finding_raw_data") String rawData) {}
