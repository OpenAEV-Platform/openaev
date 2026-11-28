package io.openaev.ocsf.schema.v1100.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectPhysicalSite extends OcsfObject {
  /** The building within the physical site. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "building")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT buildingField;

  /** The floor or level within the building. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "floor")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT floorField;

  /** The name of the physical site, as defined by the reporting organization. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT nameField;

  /** The site zone within the physical site where the activity occurred. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "site_zone")
  private io.openaev.ocsf.schema.v1100.objects.OcsfObjectSiteZone siteZoneField;

  /** The unique identifier of the physical site, as defined by the reporting organization. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeStringT uidField;

  /**
   * The <code>uid</code> attribute in numeric form where applicable.<br>
   * <strong>Note:</strong> Producers may populate <code>uid_numeric</code> only in addition to
   * <code>uid</code> and not as an alternative to it.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_numeric")
  private io.openaev.ocsf.schema.v1100.datatypes.OcsfDatatypeLongT uidNumericField;
}
