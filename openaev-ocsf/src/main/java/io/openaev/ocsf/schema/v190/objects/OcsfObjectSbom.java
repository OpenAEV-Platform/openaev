package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectSbom extends OcsfObject {
  /** The time when the SBOM was created. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;

  /** The time when the SBOM was created. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT createdTimeField;

  /** The software package or library that is being discovered or inventoried by an SBOM. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "package")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectPackage packageField;

  /**
   * Details about the upstream product that generated the SBOM e.g. <code>cdxgen</code> or <code>
   * Syft</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "product")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectProduct productField;

  /** The list of software components used in the software package. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "software_components")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectSoftwareComponent>
      softwareComponentsField;

  /**
   * The type of SBOM, normalized to the caption of the <code>type_id</code> value. In the case of
   * 'Other', it is defined by the source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;

  /** The type of SBOM. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT typeIdField;

  /**
   * A unique identifier for the SBOM or the SBOM generation by a source tool, such as the SPDX
   * <code>metadata.component.bom-ref</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;

  /** The specification (spec) version of the particular SBOM, e.g., <code>1.6</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT versionField;
}
