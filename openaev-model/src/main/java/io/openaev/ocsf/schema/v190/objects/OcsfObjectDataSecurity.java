package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectDataSecurity extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "category")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT categoryField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "category_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT categoryIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "classifier_details")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectClassifierDetails classifierDetailsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "confidentiality")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT confidentialityField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "confidentiality_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT confidentialityIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "data_lifecycle_state")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT dataLifecycleStateField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "data_lifecycle_state_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT dataLifecycleStateIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "detection_pattern")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT detectionPatternField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "detection_system")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT detectionSystemField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "detection_system_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT detectionSystemIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "discovery_details")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectDiscoveryDetails>
      discoveryDetailsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "pattern_match")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT patternMatchField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "policy")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectPolicy policyField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "size")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT sizeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "src_url")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeUrlT srcUrlField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "status_details")
  private java.util.List<io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT>
      statusDetailsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "status")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT statusField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "status_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT statusIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "total")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT totalField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;
}
