package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectAiModel extends OcsfObject {
  /**
   * AI service provider or organization name. For example: <code>OpenAI</code>, <code>Anthropic
   * </code>, <code>Google</code>, or <code>Internal</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "ai_provider")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT aiProviderField;

  /**
   * Human-readable model name. For example: <code>gpt-4o</code>, <code>claude-3-sonnet</code>, or
   * <code>text-embedding-ada-002</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  /** The unique identifier of the AI model. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;

  /**
   * Model version identifier. For example: <code>2024-05-13</code>, <code>v2.1.0</code>, or <code>
   * beta</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT versionField;
}
