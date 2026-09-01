package io.openaev.ocsf.schema.v180.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectEmail extends OcsfObject {
  /**
   * The machine-readable email header Cc values, as defined by RFC 5322. For example <code>
   * example.user@usersdomain.com</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cc")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeEmailT> ccField;

  /**
   * The human-readable email header Cc Mailbox values. For example <code>
   * 'Example User &lt;example.user@usersdomain.com&gt;'</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "cc_mailboxes")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT>
      ccMailboxesField;

  /**
   * The Data Classification object includes information about data classification levels and data
   * category types.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "data_classification")
  private io.openaev.ocsf.schema.v180.objects.OcsfObjectDataClassification dataClassificationField;

  /**
   * A list of Data Classification objects, that include information about data classification
   * levels and data category types, identified by a classifier.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "data_classifications")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectDataClassification>
      dataClassificationsField;

  /**
   * The machine-readable <strong>Delivered-To</strong> email header field. For example <code>
   * example.user@usersdomain.com</code>
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "delivered_to")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeEmailT deliveredToField;

  /**
   * The machine-readable <strong>Delivered-To</strong> email header values. For example <code>
   * example.user@usersdomain.com</code>
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "delivered_to_list")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeEmailT>
      deliveredToListField;

  /** The files embedded or attached to the email. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "files")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectFile> filesField;

  /**
   * The machine-readable email header From value, as defined by RFC 5322. For example <code>
   * example.user@usersdomain.com</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "from")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeEmailT fromField;

  /**
   * The machine-readable email header From values. This array should contain the value in <code>
   * from</code>. For example <code>example.user@usersdomain.com</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "from_list")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeEmailT> fromListField;

  /**
   * The human-readable email header From Mailbox value. For example <code>
   * 'Example User &lt;example.user@usersdomain.com&gt;'</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "from_mailbox")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT fromMailboxField;

  /**
   * The human-readable email header From Mailbox values. This array should contain the value in
   * <code>from_mailbox</code>. For example <code>
   * 'Example User &lt;example.user@usersdomain.com&gt;'</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "from_mailboxes")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeEmailT>
      fromMailboxesField;

  /** Additional HTTP headers of an HTTP request or response. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "http_headers")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectHttpHeader> httpHeadersField;

  /** The indication of whether the email has been read. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "is_read")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeBooleanT isReadField;

  /** The email header Message-ID value, as defined by RFC 5322. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "message_uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT messageUidField;

  /** The email authentication header. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "raw_header")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT rawHeaderField;

  /**
   * The machine-readable email header Reply-To value, as defined by RFC 5322. For example <code>
   * example.user@usersdomain.com</code>
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "reply_to")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeEmailT replyToField;

  /**
   * The machine-readable email header Reply-To values, as defined by RFC 5322. For example <code>
   * example.user@usersdomain.com</code>
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "reply_to_list")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeEmailT> replyToListField;

  /**
   * The human-readable email header Reply To Mailbox values. For example <code>
   * 'Example User &lt;example.user@usersdomain.com&gt;'</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "reply_to_mailboxes")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT>
      replyToMailboxesField;

  /**
   * The address found in the 'Return-Path' header, which indicates where bounce messages
   * (non-delivery reports) should be sent. This address is often set by the sending system and may
   * differ from the 'From' or 'Sender' addresses. For example, <code>mailer-daemon@senderserver.com
   * </code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "return_path")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeEmailT returnPathField;

  /**
   * The machine readable email address of the system or server that actually transmitted the email
   * message, extracted from the email headers per RFC 5322. This differs from the <code>from</code>
   * field, which shows the message author. The sender field is most commonly used when multiple
   * addresses appear in the <code> from_list </code> field, or when the transmitting system is
   * different from the message author (such as when sending on behalf of someone else).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "sender")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeEmailT senderField;

  /**
   * The human readable email address of the system or server that actually transmitted the email
   * message, extracted from the email headers per RFC 5322. This differs from the <code>
   * from_mailbox</code> field, which shows the message author. The sender mailbox field is most
   * commonly used when multiple addresses appear in the <code> from_mailboxes </code> field, or
   * when the transmitting system is different from the message author (such as when sending on
   * behalf of someone else).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "sender_mailbox")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT senderMailboxField;

  /** The size in bytes of the email, including attachments. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "size")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeLongT sizeField;

  /** The value of the SMTP MAIL FROM command. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "smtp_from")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeEmailT smtpFromField;

  /** The value of the SMTP envelope RCPT TO command. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "smtp_to")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeEmailT> smtpToField;

  /** The email header Subject value, as defined by RFC 5322. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "subject")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT subjectField;

  /**
   * The machine-readable email header To values, as defined by RFC 5322. For example <code>
   * example.user@usersdomain.com</code>
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "to")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeEmailT> toField;

  /**
   * The human-readable email header To Mailbox values. For example <code>
   * 'Example User &lt;example.user@usersdomain.com&gt;'</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "to_mailboxes")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT>
      toMailboxesField;

  /** The unique identifier of the email thread. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeStringT uidField;

  /** The URLs embedded in the email. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "urls")
  private java.util.List<io.openaev.ocsf.schema.v180.objects.OcsfObjectUrl> urlsField;

  /** The X-Originating-IP header identifying the emails originating IP address(es). */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "x_originating_ip")
  private java.util.List<io.openaev.ocsf.schema.v180.datatypes.OcsfDatatypeIpT> xOriginatingIpField;
}
