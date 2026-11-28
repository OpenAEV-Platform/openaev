package io.openaev.ocsf.schema.v1100.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectWifi extends OcsfObject {
  /**
   * The access point classification, normalized to the caption of the <code>access_point_status_id
   * </code> value. In the case of 'Other', it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "access_point_status")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT accessPointStatusField;

  /**
   * The classification assigned to an observed access point by a wireless intrusion detection or
   * prevention system.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "access_point_status_id")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeIntegerT accessPointStatusIdField;

  /**
   * The radio frequency band that the wireless link operates in, normalized to the caption of the
   * <code>band_id</code> value. In the case of <code>Other</code>, it is defined by the event
   * source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "band")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT bandField;

  /** The radio frequency band that the wireless link operates in. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "band_id")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeIntegerT bandIdField;

  /**
   * The Basic Service Set Identifier, the address of the access point radio that serves a wireless
   * network.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "bssid")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeMacT bssidField;

  /** The channel number that the wireless link operates on. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "channel_number")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeIntegerT channelNumberField;

  /**
   * The negotiated encryption cipher. For example: <code>AES-CCMP</code>, <code>GCMP-256</code> or
   * <code>TKIP</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cipher")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT cipherField;

  /**
   * The negotiated physical layer data rate, in megabits per second. For example: <code>5.5</code>
   * or <code>866.7</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "data_rate")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeFloatT dataRateField;

  /**
   * Indicates that the observed access point was also detected on the monitored wired network, for
   * example by a wireless intrusion detection or prevention system. It applies to an observed
   * access point, not to a client, and distinguishes an access point attached to the monitored
   * infrastructure from a neighboring one.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_access_point_on_wired_network")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeBooleanT
      isAccessPointOnWiredNetworkField;

  /** The measured background noise power, in decibel-milliwatts. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "noise_floor")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeIntegerT noiseFloorField;

  /**
   * The Received Signal Strength Indicator reported for the wireless link, on the reporting
   * vendor's own scale, which may be a relative index or a power level. For the received power in
   * decibel-milliwatts, see <code>signal_strength</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "rssi")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeIntegerT rssiField;

  /**
   * The Wi-Fi security protocol, normalized to the caption of the <code>security_protocol_id</code>
   * value. In the case of <code>Other</code>, it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "security_protocol")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT securityProtocolField;

  /** The authentication and key management suite that protects the wireless link. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "security_protocol_id")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeIntegerT securityProtocolIdField;

  /** The power of the received radio signal relative to the noise floor, in decibels (dB). */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "signal_noise_ratio")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeIntegerT signalNoiseRatioField;

  /** The measured power of the received radio signal, in decibel-milliwatts. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "signal_strength")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeIntegerT signalStrengthField;

  /** The Service Set Identifier, the advertised name of a wireless network. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "ssid")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT ssidField;

  /**
   * The IEEE 802.11 amendment that the link operates under. For example: <code>802.11ax</code> or
   * <code>802.11ac</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "standard")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT standardField;
}
