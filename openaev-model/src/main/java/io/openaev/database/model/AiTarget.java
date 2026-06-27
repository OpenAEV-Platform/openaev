package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.ModelBaseListener;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Type;

/**
 * An {@code AiTarget} is the AI system under adversarial test: an LLM endpoint or an AI agent
 * (OpenAI-compatible API, Anthropic, Azure OpenAI, AWS Bedrock, Google Vertex, HuggingFace, a local
 * Ollama runtime, a custom HTTP endpoint, an MCP server, or an agent HTTP entrypoint).
 *
 * <p>It mirrors {@link Endpoint} in the asset model but represents an attack target rather than a
 * managed host. Secrets are never stored here: {@link #apiKeyVariable} only names the configuration
 * key (environment variable) that the executing injector resolves the credential from.
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Entity
@DiscriminatorValue(AssetType.Values.AI_TARGET_TYPE)
@EntityListeners(ModelBaseListener.class)
public class AiTarget extends Asset {

  public enum AI_TARGET_PROVIDER {
    @JsonProperty("OPENAI_COMPATIBLE")
    OPENAI_COMPATIBLE,
    @JsonProperty("ANTHROPIC")
    ANTHROPIC,
    @JsonProperty("AZURE_OPENAI")
    AZURE_OPENAI,
    @JsonProperty("AWS_BEDROCK")
    AWS_BEDROCK,
    @JsonProperty("GOOGLE_VERTEX")
    GOOGLE_VERTEX,
    @JsonProperty("HUGGINGFACE")
    HUGGINGFACE,
    @JsonProperty("OLLAMA")
    OLLAMA,
    @JsonProperty("CUSTOM_HTTP")
    CUSTOM_HTTP,
    @JsonProperty("MCP_SERVER")
    MCP_SERVER,
    @JsonProperty("AGENT_HTTP")
    AGENT_HTTP,
  }

  public enum AI_TARGET_MODALITY {
    @JsonProperty("TEXT")
    TEXT,
    @JsonProperty("VISION")
    VISION,
    @JsonProperty("AUDIO")
    AUDIO,
    @JsonProperty("MULTIMODAL")
    MULTIMODAL,
  }

  @Queryable(filterable = true, sortable = true)
  @Column(name = "ai_target_provider")
  @JsonProperty("ai_target_provider")
  @Enumerated(EnumType.STRING)
  @NotNull
  private AI_TARGET_PROVIDER provider;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "ai_target_endpoint")
  @JsonProperty("ai_target_endpoint")
  private String endpoint;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "ai_target_model")
  @JsonProperty("ai_target_model")
  private String model;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "ai_target_modality")
  @JsonProperty("ai_target_modality")
  @Enumerated(EnumType.STRING)
  @NotNull
  private AI_TARGET_MODALITY modality = AI_TARGET_MODALITY.TEXT;

  @Column(name = "ai_target_system_prompt")
  @JsonProperty("ai_target_system_prompt")
  private String systemPrompt;

  /**
   * Free-form, provider-specific configuration (extra generation parameters, custom headers, tool /
   * MCP definitions, agent routing, ...). Never put secrets here - use {@link #apiKeyVariable}.
   */
  @Type(JsonType.class)
  @Column(name = "ai_target_configuration", columnDefinition = "jsonb")
  @JsonProperty("ai_target_configuration")
  private Map<String, Object> configuration = new HashMap<>();

  /**
   * Name of the injector configuration key / environment variable that holds the credential used to
   * call this target. The secret value itself is resolved by the injector at execution time and is
   * never persisted by the platform.
   */
  @Column(name = "ai_target_api_key_variable")
  @JsonProperty("ai_target_api_key_variable")
  private String apiKeyVariable;

  public AiTarget() {}

  public AiTarget(String id, String type, String name, AI_TARGET_PROVIDER provider) {
    super(id, type, name);
    this.provider = provider;
  }
}
