package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectMessageContext extends OcsfObject {
  /** The normalized caption of the <code>ai_role_id</code>. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "ai_role")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT aiRoleField;

  /**
   * Specifies the functional role of the AI within the context of this message, such as retrieving
   * information, assisting reasoning, executing a tool, or generating content.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "ai_role_id")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT aiRoleIdField;

  /**
   * The initiating client application. In AI systems, this represents the client-side application
   * or framework that initiates requests (e.g., LangChain application, web browser, mobile app, SDK
   * implementation).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "application")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectApplication applicationField;

  /** Number of tokens in the model's response/completion for this message. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "completion_tokens")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT completionTokensField;

  /**
   * The name or identifier of the message context. In AI systems, this could be the conversation
   * ID, session name, thread identifier, or interaction name (e.g., 'user-session-123',
   * 'conversation-abc', 'chat-thread-456').
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT nameField;

  /**
   * The text of the input prompt provided to the model for this message. This is the verbatim
   * instruction, query, or context supplied to the AI system that drove the operation.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "prompt_text")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT promptTextField;

  /** Number of tokens in the input prompt for this message. */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "prompt_tokens")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT promptTokensField;

  /**
   * The text of the model's response generated for this message. For agent-mediated operations,
   * this is the content returned by the assistant, tool, or agent.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "response_text")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT responseTextField;

  /**
   * The server or service handling the request. In AI systems, this represents the AI service, API
   * endpoint, or agent that processes and responds to requests (e.g., OpenAI API service, Claude
   * API service, internal AI model service).
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "service")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectService serviceField;

  /** Total number of tokens used for this message (prompt + completion). */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "total_tokens")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeIntegerT totalTokensField;

  /**
   * The unique identifier of the message context. This could be a session ID, conversation ID, or
   * other unique identifier that allows correlation of messages within the same context.
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
}
