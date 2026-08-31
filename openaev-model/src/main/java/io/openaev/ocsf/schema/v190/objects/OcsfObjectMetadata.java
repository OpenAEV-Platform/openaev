package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectMetadata extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "correlation_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT correlationUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "data_classification")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectDataClassification dataClassificationField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "data_classifications")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectDataClassification>
      dataClassificationsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "debug")
  private java.util.List<io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT> debugField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "event_code")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT eventCodeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "extension")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectExtension extensionField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "extensions")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectExtension> extensionsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_truncated")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isTruncatedField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "labels")
  private java.util.List<io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT> labelsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "log_format")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT logFormatField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "log_level")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT logLevelField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "log_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT logNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "log_provider")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT logProviderField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "log_source")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT logSourceField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "log_version")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT logVersionField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "logged_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT loggedTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "logged_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT loggedTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "loggers")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectLogger> loggersField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT modifiedTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT modifiedTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "original_event_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT originalEventUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "original_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT originalTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "processed_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT processedTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "processed_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT processedTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "product")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectProduct productField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "profiles")
  private java.util.List<io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT> profilesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "reporter")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectReporter reporterField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "sequence")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT sequenceField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "source")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT sourceField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "tags")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectKeyValueObject> tagsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "tenant_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT tenantUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "total_queued_duration")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectTimespan totalQueuedDurationField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "transformation_info_list")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectTransformationInfo>
      transformationInfoListField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "transmit_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT transmitTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "transmit_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT transmitTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "untruncated_size")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT untruncatedSizeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT versionField;
}
