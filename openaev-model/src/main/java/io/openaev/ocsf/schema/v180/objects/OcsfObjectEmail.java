package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectEmail extends OcsfObject {

  @com.fasterxml.jackson.annotation.JsonProperty(value = "cc")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeEmailT> ccField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "cc_mailboxes")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT>
      ccMailboxesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "data_classification")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectDataClassification dataClassificationField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "data_classifications")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectDataClassification>
      dataClassificationsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "delivered_to")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeEmailT deliveredToField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "delivered_to_list")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeEmailT>
      deliveredToListField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "files")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectFile> filesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "from")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeEmailT fromField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "from_list")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeEmailT> fromListField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "from_mailbox")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT fromMailboxField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "from_mailboxes")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeEmailT>
      fromMailboxesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "http_headers")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectHttpHeader> httpHeadersField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_read")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isReadField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "message_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT messageUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "raw_header")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT rawHeaderField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "reply_to")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeEmailT replyToField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "reply_to_list")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeEmailT> replyToListField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "reply_to_mailboxes")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT>
      replyToMailboxesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "return_path")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeEmailT returnPathField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "sender")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeEmailT senderField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "sender_mailbox")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT senderMailboxField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "size")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeLongT sizeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "smtp_from")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeEmailT smtpFromField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "smtp_to")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeEmailT> smtpToField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "subject")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT subjectField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "to")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeEmailT> toField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "to_mailboxes")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT>
      toMailboxesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "urls")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectUrl> urlsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "x_originating_ip")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIpT> xOriginatingIpField;
}
