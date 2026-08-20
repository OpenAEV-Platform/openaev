package io.openaev.ocsf.schema.v190.objects;

public class OcsfObjectEmail {
  @com.fasterxml.jackson.annotation.JsonProperty(value = "reply_to_list")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeEmailT replyToListField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "http_headers")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectHttpHeader httpHeadersField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "from_list")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeEmailT fromListField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "message_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT messageUidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "subject")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT subjectField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "smtp_to")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeEmailT smtpToField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "to")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeEmailT toField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "from_mailbox")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT fromMailboxField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "from_mailboxes")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeEmailT fromMailboxesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_read")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeBooleanT isReadField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "bcc_mailboxes")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT bccMailboxesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "return_path")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeEmailT returnPathField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "data_classification")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectDataClassification dataClassificationField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "from")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeEmailT fromField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "delivered_to_list")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeEmailT deliveredToListField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "cc")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeEmailT ccField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "delivered_to")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeEmailT deliveredToField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "raw_header")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT rawHeaderField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "sender_mailbox")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT senderMailboxField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "reply_to_mailboxes")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT replyToMailboxesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "smtp_from")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeEmailT smtpFromField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "reply_to")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeEmailT replyToField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "size")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeLongT sizeField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "files")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectFile filesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "to_mailboxes")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT toMailboxesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "x_originating_ip")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIpT xOriginatingIpField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "data_classifications")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectDataClassification dataClassificationsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "bcc")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeEmailT bccField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "urls")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectUrl urlsField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "cc_mailboxes")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT ccMailboxesField;

  @com.fasterxml.jackson.annotation.JsonProperty(value = "sender")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeEmailT senderField;
}
