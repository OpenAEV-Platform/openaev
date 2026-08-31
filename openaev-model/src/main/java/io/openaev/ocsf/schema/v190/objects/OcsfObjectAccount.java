package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectAccount extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_disabled")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isDisabledField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_locked")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isLockedField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_on_premises_sync_enabled")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isOnPremisesSyncEnabledField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "labels")
  private java.util.List<io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT> labelsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "tags")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectKeyValueObject> tagsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT typeIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_numeric")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT uidNumericField;
}
