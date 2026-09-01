package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectOs extends OcsfObject {
  /** The operating system build number. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "build")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT buildField;

  /**
   * The operating system country code, as defined by the ISO 3166-1 standard (Alpha-2 code).
   *
   * <p><b>Note:</b> The two letter country code should be capitalized. For example: <code>US</code>
   * or <code>CA</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "country")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT countryField;

  /**
   * The Common Platform Enumeration (CPE) name. For example: <code>cpe:/a:apple:safari:16.2</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cpe_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT cpeNameField;

  /**
   * The cpu architecture, the number of bits used for addressing in memory. For example: <code>32
   * </code> or <code>64</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cpu_bits")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT cpuBitsField;

  /** The operating system edition. For example: <code>Professional</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "edition")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT editionField;

  /**
   * The kernel release of the operating system. On Unix-based systems, this is determined from the
   * <code>uname -r</code> command output, for example "5.15.0-122-generic".
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "kernel_release")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT kernelReleaseField;

  /**
   * The two letter lower case language codes, as defined by ISO 639-1. For example: <code>en</code>
   * (English), <code>de</code> (German), or <code>fr</code> (French).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "lang")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT langField;

  /** The operating system name. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  /** The name of the latest Service Pack. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "sp_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT spNameField;

  /** The version number of the latest Service Pack. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "sp_ver")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT spVerField;

  /** The type of the operating system. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;

  /** The type identifier of the operating system. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT typeIdField;

  /**
   * The version of the OS running on the device that originated the event. For example: "Windows
   * 10", "OS X 10.7", or "iOS 9".
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT versionField;
}
