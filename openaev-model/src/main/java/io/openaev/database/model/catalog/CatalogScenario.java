package io.openaev.database.model.catalog;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/** A scenario version of the catalogue. */
@Getter
@Setter
@Entity
@DiscriminatorValue(ContentType.SCENARIO_VALUE)
public class CatalogScenario extends CatalogItem {

  @Column(name = "catalog_item_severity")
  @JsonProperty("catalog_item_severity")
  private String severity;

  @Column(name = "catalog_item_category")
  @JsonProperty("catalog_item_category")
  private String category;

  @Column(name = "catalog_item_main_focus")
  @JsonProperty("catalog_item_main_focus")
  private String mainFocus;

  /** Time-based or chained. */
  @Column(name = "catalog_item_scenario_type")
  @JsonProperty("catalog_item_scenario_type")
  private String scenarioType;

  @Column(name = "catalog_item_injects_count")
  @JsonProperty("catalog_item_injects_count")
  private Integer injectsCount;

  /** What the scenario needs to run (injectors, executors), as JSON from the manifest. */
  @Column(name = "catalog_item_requires")
  @JsonIgnore
  private String requires;

  /** Payloads the scenario uses, as a JSON array of {external_id, version, sha256}. */
  @Column(name = "catalog_item_payload_refs")
  @JsonIgnore
  private String payloadRefs;
}
