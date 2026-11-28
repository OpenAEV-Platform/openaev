package io.openaev.ocsf.schema.v1100.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectDnsSoa extends OcsfObject {
  /**
   * The DNS SOA EXPIRE field: the time interval after which a secondary name server that has been
   * unable to refresh the zone stops answering authoritatively for it. Populate <code>duration_secs
   * </code>, as the wire value is always a count of seconds.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "expire_interval")
  private io.openaev.ocsf.schema.v1100.objects.OcsfObjectTimespan expireIntervalField;

  /**
   * The DNS SOA MINIMUM field: the time interval that defines the TTL for negative caching of
   * records from this zone. Populate <code>duration_secs</code>, as the wire value is always a
   * count of seconds.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "minimum_ttl")
  private io.openaev.ocsf.schema.v1100.objects.OcsfObjectTimespan minimumTtlField;

  /**
   * The DNS SOA MNAME field: the fully qualified domain name of the primary name server that is the
   * original source of data for the zone. For example: <code>ns1.example.com</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "primary_server")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT primaryServerField;

  /**
   * The DNS SOA REFRESH field: the time interval before a secondary name server should query the
   * primary to check for zone updates. Populate <code>duration_secs</code>, as the wire value is
   * always a count of seconds.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "refresh_interval")
  private io.openaev.ocsf.schema.v1100.objects.OcsfObjectTimespan refreshIntervalField;

  /**
   * The DNS SOA RNAME field: the email address of the party responsible for the zone, encoded as a
   * domain name (the first label is the local part of the address). For example: <code>
   * hostmaster.example.com</code> represents <code>hostmaster@example.com</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "responsible_party")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT responsiblePartyField;

  /**
   * The DNS SOA RETRY field: the time interval before a secondary name server should retry a failed
   * zone refresh. Populate <code>duration_secs</code>, as the wire value is always a count of
   * seconds.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "retry_interval")
  private io.openaev.ocsf.schema.v1100.objects.OcsfObjectTimespan retryIntervalField;

  /**
   * The DNS SOA SERIAL field: the unsigned 32-bit version number of the zone, incremented when the
   * zone contents change. Secondary name servers compare this value to decide whether a zone
   * transfer is needed.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "sequence_number")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeLongT sequenceNumberField;
}
