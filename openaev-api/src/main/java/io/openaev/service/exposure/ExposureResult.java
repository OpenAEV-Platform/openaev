package io.openaev.service.exposure;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import java.time.Instant;
import java.util.List;

/**
 * Exposure result of one simulation run (a simulation or a run built from a security coverage): per
 * vulnerability or technique tested, the assets tested with their verdict, the conditions checked
 * and a confidence, plus the assets the attack path reached beyond the entry points.
 *
 * <p>Versioned contract read by external consumers (API and the notification webhook): a field is
 * only ever added in a version; a rename or a removal raises {@link #SCHEMA_VERSION}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExposureResult(
    @JsonProperty("schema_version") String schemaVersion,
    @JsonProperty("simulation_id") String simulationId,
    @JsonProperty("simulation_name") String simulationName,
    @JsonProperty("coverage_external_id") String coverageExternalId,
    @JsonProperty("computed_at") Instant computedAt,
    @JsonProperty("summary") Summary summary,
    @JsonProperty("subjects") List<Subject> subjects,
    @JsonProperty("reached_assets") List<ReachedAsset> reachedAssets) {

  public static final String SCHEMA_VERSION = "1";

  /** Verdict of one asset for one subject, declared from the most to the least severe. */
  public enum Verdict {
    EXPOSED,
    DETECTED,
    PREVENTED,
    NOT_EXPOSED,
    NOT_TESTED;

    @JsonValue
    public String value() {
      return name().toLowerCase();
    }

    /** The more severe of two verdicts. */
    public Verdict worst(Verdict other) {
      return other == null || ordinal() <= other.ordinal() ? this : other;
    }
  }

  /** How sure the verdict is: every condition scored, some, or none. */
  public enum Confidence {
    HIGH,
    MEDIUM,
    LOW;

    @JsonValue
    public String value() {
      return name().toLowerCase();
    }
  }

  /** Distinct assets per overall verdict (an asset counts once, under its most severe verdict). */
  public record Summary(
      @JsonProperty("assets_tested") int assetsTested,
      @JsonProperty("assets_exposed") int assetsExposed,
      @JsonProperty("assets_detected") int assetsDetected,
      @JsonProperty("assets_prevented") int assetsPrevented,
      @JsonProperty("assets_not_exposed") int assetsNotExposed,
      @JsonProperty("assets_not_tested") int assetsNotTested,
      @JsonProperty("critical_assets_reached") int criticalAssetsReached) {}

  /** A vulnerability (CVE external id), a technique (ATT&CK external id) or an inject. */
  public record Subject(
      @JsonProperty("type") String type,
      @JsonProperty("external_id") String externalId,
      @JsonProperty("name") String name,
      @JsonProperty("assets") List<AssetVerdict> assets) {}

  public record AssetVerdict(
      @JsonProperty("asset_id") String assetId,
      @JsonProperty("asset_name") String assetName,
      @JsonProperty("criticality") String criticality,
      @JsonProperty("verdict") Verdict verdict,
      @JsonProperty("confidence") Confidence confidence,
      @JsonProperty("conditions") List<Condition> conditions) {}

  /** One expectation checked on the asset, with its scores and computed status. */
  public record Condition(
      @JsonProperty("inject_id") String injectId,
      @JsonProperty("inject_title") String injectTitle,
      @JsonProperty("expectation_type") String expectationType,
      @JsonProperty("expected_score") Double expectedScore,
      @JsonProperty("score") Double score,
      @JsonProperty("status") String status) {}

  /** An asset of the attack path that no inject targeted directly. */
  public record ReachedAsset(
      @JsonProperty("asset_id") String assetId,
      @JsonProperty("asset_name") String assetName,
      @JsonProperty("criticality") String criticality,
      @JsonProperty("critical") boolean critical) {}
}
