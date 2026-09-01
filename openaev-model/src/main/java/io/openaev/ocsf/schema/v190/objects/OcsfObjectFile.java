package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectFile extends OcsfObject {
  /** The time when the file was last accessed. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "accessed_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT accessedTimeDtField;

  /** The time when the file was last accessed. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "accessed_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT accessedTimeField;

  /** The name of the user who last accessed the object. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "accessor")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectUser accessorField;

  /** The bitmask value that represents the file attributes. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "attributes")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT attributesField;

  /**
   * The name of the company that published the file. For example: <code>Microsoft Corporation
   * </code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "company_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT companyNameField;

  /**
   * The file content confidentiality, normalized to the confidentiality_id value. In the case of
   * 'Other', it is defined by the event source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "confidentiality")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT confidentialityField;

  /** The normalized identifier of the file content confidentiality indicator. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "confidentiality_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT confidentialityIdField;

  /** The time when the file was created. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT createdTimeDtField;

  /** The time when the file was created. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "created_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT createdTimeField;

  /** The user that created the file. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "creator")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectUser creatorField;

  /**
   * The Data Classification object includes information about data classification levels and data
   * category types.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "data_classification")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectDataClassification dataClassificationField;

  /**
   * A list of Data Classification objects, that include information about data classification
   * levels and data category types, identified by a classifier.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "data_classifications")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectDataClassification>
      dataClassificationsField;

  /**
   * The description of the file, as returned by file system. For example: the description as
   * returned by the Unix file command or the Windows file type.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "desc")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT descField;

  /** Information pertaining to a downloaded file. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "download_info")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectDownloadInfo downloadInfoField;

  /**
   * The drive type, normalized to the caption of the <code>drive_type_id</code> value. In the case
   * of <code>Other</code>, it is defined by the source.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "drive_type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT driveTypeField;

  /** Identifies the type of a disk drive, i.e. fixed, removable, etc. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "drive_type_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT driveTypeIdField;

  /** The encryption details of the file. Should be populated if the file is encrypted. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "encryption_details")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectEncryptionDetails encryptionDetailsField;

  /**
   * The extension of the file, excluding the leading dot. For example: <code>exe</code> from <code>
   * svchost.exe</code>, or <code>gz</code> from <code>export.tar.gz</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "ext")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT extField;

  /** An array of hash attributes. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "hashes")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectFingerprint> hashesField;

  /** A list of symbols imported by the executable file. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "imported_symbols")
  private java.util.List<io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT>
      importedSymbolsField;

  /**
   * The name of the file as identified within the file itself. This contrasts with the name by
   * which the file is known on disk. Where available, the internal name is widely used by security
   * practitioners and detection content because the on-disk file name is not reliable. On the
   * Windows OS, most PE files contain a <code>VERSIONINFO</code> resource from which the internal
   * name can be obtained. On macOS, binaries can optionally embed a copy of the application's
   * <code>Info.plist</code> file which in turn contains the name of the executable.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "internal_name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT internalNameField;

  /** Indicates if the file was deleted from the filesystem. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_deleted")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isDeletedField;

  /** Indicates if the file is encrypted. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_encrypted")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isEncryptedField;

  /**
   * Indicates if the file is publicly accessible. For example in an object's public access in AWS
   * S3
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_public")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isPublicField;

  /** Indicates that the file cannot be modified. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_readonly")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isReadonlyField;

  /** The indication of whether the object is part of the operating system. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_system")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isSystemField;

  /** The Multipurpose Internet Mail Extensions (MIME) type of the file, if applicable. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "mime_type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT mimeTypeField;

  /** The time when the file was last modified. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time_dt")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeDatetimeT modifiedTimeDtField;

  /** The time when the file was last modified. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "modified_time")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeTimestampT modifiedTimeField;

  /** The user that last modified the file. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "modifier")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectUser modifierField;

  /** The name of the file. For example: <code>svchost.exe</code> */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  /** The user that owns the file/object. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "owner")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectUser ownerField;

  /** The parent folder in which the file resides. For example: <code>c:\windows\system32</code> */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "parent_folder")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT parentFolderField;

  /** The full path to the file. For example: <code>c:\windows\system32\svchost.exe</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "path")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT pathField;

  /** The product that created or installed the file. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "product")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectProduct productField;

  /** The object security descriptor. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "security_descriptor")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT securityDescriptorField;

  /** The digital signature of the file. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "signature")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectDigitalSignature signatureField;

  /** A collection of <code>Digital Signature</code> objects. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "signatures")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectDigitalSignature>
      signaturesField;

  /** The size of data, in bytes. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "size")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT sizeField;

  /**
   * The storage class of the file. For example in AWS S3: <code>STANDARD, STANDARD_IA, GLACIER
   * </code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "storage_class")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT storageClassField;

  /** The list of tags; <code>{key:value}</code> pairs associated to the file. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "tags")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectKeyValueObject> tagsField;

  /** The file type. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT typeField;

  /**
   * The file type ID. Note the distinction between a <code>Regular File</code> and an <code>
   * Executable File</code>. If the distinction is not known, or not indicated by the log, use
   * <code>Regular File</code>. In this case, it should not be assumed that a Regular File is not
   * executable.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "type_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT typeIdField;

  /**
   * The unique identifier of the file as defined by the storage system, such the file system file
   * ID.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;

  /**
   * The <code>uid</code> attribute in numeric form where applicable.<br>
   * <strong>Note:</strong> Producers may populate <code>uid_numeric</code> only in addition to
   * <code>uid</code> and not as an alternative to it.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid_numeric")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT uidNumericField;

  /**
   * The file URI, such as those reporting by static analysis tools. E.g., <code>
   * file:///C:/dev/sarif/sarif-tutorials/samples/Introduction/simple-example.js</code>
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uri")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeUrlT uriField;

  /** The URL of the file, when applicable. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "url")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectUrl urlField;

  /** The file version. For example: <code>8.0.7601.17514</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "version")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT versionField;

  /** The volume on the storage device where the file is located. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "volume")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT volumeField;

  /**
   * An unordered collection of zero or more name/value pairs where each pair represents a file or
   * folder extended attribute.
   *
   * <p>For example: Windows alternate data stream attributes (ADS stream name, ADS size, etc.),
   * user-defined or application-defined attributes, ACL, owner, primary group, etc. Examples from
   * DCS:
   *
   * <ul>
   *   <li><strong>ads_name</strong>
   *   <li><strong>ads_size</strong>
   *   <li><strong>dacl</strong>
   *   <li><strong>owner</strong>
   *   <li><strong>primary_group</strong>
   *   <li><strong>link_name</strong> - name of the link associated to the file.
   *   <li><strong>hard_link_count</strong> - the number of links that are associated to the file.
   * </ul>
   */
  @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
      using = io.openaev.ocsf.schema.v190.ObjectNodeDeserialiser.class)
  @com.fasterxml.jackson.annotation.JsonProperty(value = "xattributes")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeJsonT xattributesField;
}
