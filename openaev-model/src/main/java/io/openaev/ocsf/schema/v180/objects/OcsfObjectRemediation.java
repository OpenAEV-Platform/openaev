package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectRemediation extends OcsfObject {
  /**
   * An array of Center for Internet Security (CIS) Controls that can be optionally mapped to
   * provide additional remediation details.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cis_controls")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectCisControl> cisControlsField;

  /** The description of the remediation strategy. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "desc")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT descField;

  /**
   * A list of KB articles or patches related to an endpoint. A KB Article contains metadata that
   * describes the patch or an update.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "kb_article_list")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectKbArticle>
      kbArticleListField;

  /**
   * The KB article/s related to the entity. A KB Article contains metadata that describes the patch
   * or an update.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "kb_articles")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT> kbArticlesField;

  /** A list of supporting URL/s, references that help describe the remediation strategy. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "references")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT> referencesField;
}
