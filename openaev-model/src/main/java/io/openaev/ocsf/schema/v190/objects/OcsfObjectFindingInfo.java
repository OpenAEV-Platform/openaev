package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectFindingInfo extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "analytic")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectAnalytic analyticField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "attack_graph")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectGraph attackGraphField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "attacks")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectAttack> attacksField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT createdTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "data_sources")
  private java.util.List<io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT>
      dataSourcesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "desc")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT descField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "first_seen_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT firstSeenTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "first_seen_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT firstSeenTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "kill_chain")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectKillChainPhase>
      killChainField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_seen_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT lastSeenTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "last_seen_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT lastSeenTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT modifiedTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT modifiedTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "product")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectProduct productField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "product_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT productUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "related_analytics")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectAnalytic>
      relatedAnalyticsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "related_events_count")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT relatedEventsCountField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "related_events")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectRelatedEvent>
      relatedEventsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "src_url")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeUrlT srcUrlField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "tags")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectKeyValueObject> tagsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "title")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT titleField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "traits")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectTrait> traitsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "types")
  private java.util.List<io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT> typesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_alt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidAltField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;
}
