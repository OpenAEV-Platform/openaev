package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectCloud {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "org")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectOrganization orgField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "zone")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT zoneField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "provider")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT providerField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "region")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT regionField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "cloud_partition")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT cloudPartitionField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "account")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectAccount accountField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "project_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT projectUidField;
}
