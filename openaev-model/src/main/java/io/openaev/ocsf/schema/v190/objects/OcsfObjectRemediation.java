package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

public class OcsfObjectRemediation extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "kb_article_list")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectKbArticle kbArticleListField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "kb_articles")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT kbArticlesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "references")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT referencesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "cis_controls")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectCisControl cisControlsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "desc")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT descField;
}
