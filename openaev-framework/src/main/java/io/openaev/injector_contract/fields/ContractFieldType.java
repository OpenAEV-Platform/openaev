package io.openaev.injector_contract.fields;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Enumeration of available contract field types.
 *
 * <p>Each field type corresponds to a specific UI input component and data format. Field types
 * determine how data is collected from users and validated.
 *
 * <p>The JSON property value of each type is used for serialization and must match the frontend
 * component expectations.
 *
 * @see ContractElement
 * @see ContractElement#getType()
 */
public enum ContractFieldType {
  /** Single-line text input field. */
  @JsonProperty("text")
  Text("text"),

  /** Numeric input field. */
  @JsonProperty("number")
  Number("number"),

  /** Boolean checkbox field. */
  @JsonProperty("checkbox")
  Checkbox("checkbox"),

  /** Multi-line text input field (supports rich text). */
  @JsonProperty("textarea")
  Textarea("textarea"),

  /** Dropdown selection field with predefined choices. */
  @JsonProperty("select")
  Select("select"),

  /** Choice field with additional information per option. */
  @JsonProperty("choice")
  Choice("choice"),

  /** Article/channel content selector. */
  @JsonProperty("article")
  Article("article"),

  /** Challenge selector. */
  @JsonProperty("challenge")
  Challenge("challenge"),

  /** Select field whose options depend on another field's value. */
  @JsonProperty("dependency-select")
  DependencySelect("dependency-select"),

  /** File attachment field. */
  @JsonProperty("attachment")
  Attachment("attachment"),

  /** Team selector field. */
  @JsonProperty("team")
  Team("team"),

  /** Expectation configuration field. */
  @JsonProperty("expectation")
  Expectation("expectation"),

  /** Asset selector field. */
  @JsonProperty("asset")
  Asset("asset"),

  /** Asset group selector field. */
  @JsonProperty("asset-group")
  AssetGroup("asset-group"),

  /** Payload selector field. */
  @JsonProperty("payload")
  Payload("payload"),

  /** Targeted asset selector with property selection. */
  @JsonProperty("targeted-asset")
  TargetedAsset("targeted-asset");

  /** The JSON/UI label for this field type. */
  public final String label;

  ContractFieldType(String label) {
    this.label = label;
  }
}
