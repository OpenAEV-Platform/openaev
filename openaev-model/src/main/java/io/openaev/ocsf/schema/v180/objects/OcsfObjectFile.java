package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectFile extends OcsfObject {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "accessed_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT accessedTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "accessed_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT accessedTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "accessor")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectUser accessorField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "attributes")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT attributesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "company_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT companyNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "confidentiality")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT confidentialityField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "confidentiality_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT confidentialityIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT createdTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "creator")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectUser creatorField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "data_classification")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectDataClassification dataClassificationField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "data_classifications")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectDataClassification>
      dataClassificationsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "desc")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT descField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "drive_type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT driveTypeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "drive_type_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT driveTypeIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "encryption_details")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectEncryptionDetails encryptionDetailsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "ext")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT extField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "hashes")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectFingerprint> hashesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "imported_symbols")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT>
      importedSymbolsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "internal_name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT internalNameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_deleted")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isDeletedField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_encrypted")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isEncryptedField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_public")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isPublicField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_readonly")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isReadonlyField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_system")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isSystemField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "mime_type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT mimeTypeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time_dt")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeDatetimeT modifiedTimeDtField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeTimestampT modifiedTimeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "modifier")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectUser modifierField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT nameField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "owner")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectUser ownerField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "parent_folder")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT parentFolderField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "path")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT pathField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "product")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectProduct productField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "security_descriptor")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT securityDescriptorField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "signature")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectDigitalSignature signatureField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "signatures")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectDigitalSignature>
      signaturesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "size")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeLongT sizeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "storage_class")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT storageClassField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "tags")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectKeyValueObject> tagsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT typeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIntegerT typeIdField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uri")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeUrlT uriField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "url")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectUrl urlField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT versionField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "volume")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT volumeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "xattributes")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectObject xattributesField;
}
