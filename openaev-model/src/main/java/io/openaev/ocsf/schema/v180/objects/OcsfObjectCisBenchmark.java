package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectCisBenchmark extends OcsfObject {
  /**
   * The CIS Critical Security Controls is a prioritized set of actions to protect your organization
   * and data from cyber-attack vectors.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cis_controls")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectCisControl> cisControlsField;

  /**
   * The CIS Benchmark description. For example: <i>The cramfs filesystem type is a compressed
   * read-only Linux filesystem embedded in small footprint systems. A cramfs image can be used
   * without having to first decompress the image.</i>
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "desc")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT descField;

  /**
   * The CIS Benchmark name. For example: <i>Ensure mounting of cramfs filesystems is disabled.</i>
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;
}
