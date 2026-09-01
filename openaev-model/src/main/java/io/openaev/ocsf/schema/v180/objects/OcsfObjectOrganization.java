package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectOrganization extends OcsfObject {
  /**
   * The name of the organization, Oracle Cloud Tenancy, Google Cloud Organization, or AWS
   * Organization. For example, <code> Widget, Inc. </code> or the <code> AWS Organization name
   * </code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  /**
   * The name of an organizational unit, Google Cloud Folder, or AWS Org Unit. For example, the
   * <code> GCP Project Name </code>, or <code> Dev_Prod_OU </code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "ou_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT ouNameField;

  /**
   * The unique identifier of an organizational unit, Google Cloud Folder, or AWS Org Unit. For
   * example, an <code> Oracle Cloud Tenancy ID </code>, <code> AWS OU ID </code>, or <code>
   *  GCP Folder ID </code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "ou_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT ouUidField;

  /**
   * The unique identifier of the organization, Oracle Cloud Tenancy, Google Cloud Organization, or
   * AWS Organization. For example, an <code> AWS Org ID </code> or <code> Oracle Cloud Domain ID
   * </code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;
}
