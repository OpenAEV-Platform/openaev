package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectDatabucket extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "agent_list")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectAgent> agentListField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "cloud_partition")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT cloudPartitionField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT createdTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "criticality")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT criticalityField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "data_classification")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectDataClassification dataClassificationField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "data_classifications")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectDataClassification>
      dataClassificationsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "data")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeJsonT dataField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "desc")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT descField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "encryption_details")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectEncryptionDetails encryptionDetailsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "file")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectFile fileField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "group")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectGroup groupField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "groups")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectGroup> groupsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "hostname")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeHostnameT hostnameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "ip")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIpT ipField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_backed_up")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isBackedUpField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_encrypted")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isEncryptedField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_public")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isPublicField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "labels")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT> labelsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT modifiedTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT modifiedTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "namespace")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT namespaceField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "owner")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectUser ownerField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "region")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT regionField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "resource_relationship")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectGraph resourceRelationshipField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "size")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeLongT sizeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "tags")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectKeyValueObject> tagsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT typeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT typeIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_alt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidAltField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT versionField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "zone")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT zoneField;
}
