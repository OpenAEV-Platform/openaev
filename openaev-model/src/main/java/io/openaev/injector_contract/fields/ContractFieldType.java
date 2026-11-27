package io.openaev.injector_contract.fields;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

/**
 * Enumeration of available contract field types.
 *
 * <p>Each field type corresponds to a specific UI input component and data format. Field types
 * determine how data is collected from users and validated.
 *
 * <p>The JSON property value of each type is used for serialization and must match the frontend
 * component expectations.
 *
 * @see ContractElement
 * @see ContractElement#getType()
 */
public enum ContractFieldType {
  /** Single-line text input field. */
  @JsonProperty("text")
  Text("text", ValueShape.TEXT),

  /** Numeric input field. */
  @JsonProperty("number")
  Number("number", ValueShape.NUMBER),

  /** Boolean checkbox field. */
  @JsonProperty("checkbox")
  Checkbox("checkbox", ValueShape.BOOLEAN),

  /** Multi-line text input field (supports rich text). */
  @JsonProperty("textarea")
  Textarea("textarea", ValueShape.TEXT),

  /** Dropdown selection field with predefined choices. */
  @JsonProperty("select")
  Select("select", ValueShape.PICKED),

  /** Choice field with additional information per option. */
  @JsonProperty("choice")
  Choice("choice", ValueShape.PICKED),

  /** Article/channel content selector. */
  @JsonProperty("article")
  Article("article", ValueShape.UNCHECKED),

  /** Challenge selector. */
  @JsonProperty("challenge")
  Challenge("challenge", ValueShape.UNCHECKED),

  /** Select field whose options depend on another field's value. */
  @JsonProperty("dependency-select")
  DependencySelect("dependency-select", ValueShape.PICKED),

  /** File attachment field. */
  @JsonProperty("attachment")
  Attachment("attachment", ValueShape.UNCHECKED),

  /** Team selector field. */
  @JsonProperty("team")
  Team("team", ValueShape.UNCHECKED),

  /** Expectation configuration field. */
  @JsonProperty("expectation")
  Expectation("expectation", ValueShape.OBJECTS),

  /** Asset selector field. */
  @JsonProperty("asset")
  Asset("asset", ValueShape.UNCHECKED),

  /** Asset group selector field. */
  @JsonProperty("asset-group")
  AssetGroup("asset-group", ValueShape.UNCHECKED),

  /**
   * An asset group picked by id, stored in the content; unlike {@link #AssetGroup} not a target.
   */
  @JsonProperty("asset-group-reference")
  AssetGroupReference("asset-group-reference", ValueShape.UNCHECKED),

  /** Tag selector field, storing the selected tag ids in the inject content. */
  @JsonProperty("tags")
  Tags("tags", ValueShape.UNCHECKED),

  /** AI target selector field (LLM endpoint / AI agent under adversarial test). */
  @JsonProperty("ai-target")
  AiTarget("ai-target", ValueShape.UNCHECKED),

  /** Payload selector field. */
  @JsonProperty("payload")
  Payload("payload", ValueShape.UNCHECKED),

  /** Targeted asset selector with property selection. */
  @JsonProperty("targeted-asset")
  TargetedAsset("targeted-asset", ValueShape.PICKED),

  /** Document selector field (a payload argument of type document), storing the document id. */
  @JsonProperty("document")
  Document("document", ValueShape.TEXT),

  /** Credential reference field. */
  @JsonProperty("credential-reference")
  CredentialReference("credential-reference", ValueShape.UNCHECKED);

  /** The JSON/UI label for this field type. */
  public final String label;

  private final ValueShape shape;

  ContractFieldType(String label, ValueShape shape) {
    this.label = label;
    this.shape = shape;
  }

  public static Optional<ContractFieldType> fromLabel(String label) {
    return Arrays.stream(values()).filter(type -> type.label.equals(label)).findFirst();
  }

  /** True when an inject content value of this field has the shape the execution expects. */
  public boolean accepts(JsonNode value, boolean single) {
    return shape.accepts(value, single);
  }

  /** True when the execution cannot read the value at all, so saving it is refused. */
  public boolean refuses(JsonNode value, boolean single) {
    return shape.refuses(value, single);
  }

  private enum ValueShape {
    TEXT {
      @Override
      boolean accepts(JsonNode value, boolean single) {
        return value.isTextual();
      }
    },
    NUMBER {
      @Override
      boolean accepts(JsonNode value, boolean single) {
        return value.isNumber();
      }
    },
    BOOLEAN {
      @Override
      boolean accepts(JsonNode value, boolean single) {
        return value.isBoolean();
      }
    },
    PICKED {
      @Override
      boolean accepts(JsonNode value, boolean single) {
        return single ? value.isTextual() : isListOf(value, JsonNode::isTextual);
      }

      @Override
      boolean refuses(JsonNode value, boolean single) {
        return single && value.isArray();
      }
    },
    OBJECTS {
      @Override
      boolean accepts(JsonNode value, boolean single) {
        return isListOf(value, JsonNode::isObject);
      }
    },
    /** Kept outside the inject content (teams, assets...) or not checked yet. */
    UNCHECKED {
      @Override
      boolean accepts(JsonNode value, boolean single) {
        return true;
      }
    };

    abstract boolean accepts(JsonNode value, boolean single);

    boolean refuses(JsonNode value, boolean single) {
      return false;
    }

    private static boolean isListOf(JsonNode value, Predicate<JsonNode> item) {
      return value.isArray() && StreamSupport.stream(value.spliterator(), false).allMatch(item);
    }
  }
}
