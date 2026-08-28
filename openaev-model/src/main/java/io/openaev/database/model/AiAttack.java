package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.hypersistence.utils.hibernate.type.array.StringArrayType;
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
 * An {@code AiAttack} payload describes an adversarial action executed against an AI target {@link
 * Asset} ({@code category = AI_TARGET} - an LLM or AI agent) by the {@code ai-redteam} injector. It
 * is the AI counterpart of {@link Command} / {@link Executable} in the Threat Arsenal and is mapped
 * to MITRE ATLAS ({@code AML.Txxxx}) and OWASP (LLM / Agentic) via the surrounding {@link
 * InjectorContract}.
 *
 * <p>Execution is performed by an external injector calling the model/agent endpoint - never by the
 * implant - so there are no OS-level command fields here.
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Entity
@DiscriminatorValue(AiAttack.AI_ATTACK_TYPE)
@EntityListeners(ModelBaseListener.class)
public class AiAttack extends Payload {

  public static final String AI_ATTACK_TYPE = "AiAttack";

  /** Which engine runs the attack content. */
  public enum AI_ATTACK_ENGINE {
    @JsonProperty("native")
    NATIVE,
    @JsonProperty("garak")
    GARAK,
    @JsonProperty("pyrit")
    PYRIT,
    @JsonProperty("promptfoo")
    PROMPTFOO,
  }

  @JsonProperty("payload_type")
  private String type = AI_ATTACK_TYPE;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "ai_attack_engine")
  @JsonProperty("ai_attack_engine")
  @Enumerated(EnumType.STRING)
  @NotNull
  private AI_ATTACK_ENGINE engine = AI_ATTACK_ENGINE.NATIVE;

  /**
   * Attack technique category, e.g. {@code PROMPT_INJECTION}, {@code JAILBREAK}, {@code
   * SYSTEM_PROMPT_LEAK}, {@code DATA_EXFILTRATION}, {@code TOOL_ABUSE}, {@code MCP_TOOL_POISONING},
   * {@code UNBOUNDED_CONSUMPTION}. Kept as a free string so the curated pack can evolve without a
   * schema change.
   */
  @Queryable(filterable = true, searchable = true, sortable = true)
  @Column(name = "ai_attack_category")
  @JsonProperty("ai_attack_category")
  private String category;

  /**
   * The attack content: a prompt / template for the native engine, a probe/plugin selector for
   * Garak / Promptfoo, or a seed prompt for a PyRIT orchestrator.
   */
  @Column(name = "ai_attack_content")
  @JsonProperty("ai_attack_content")
  private String content;

  /**
   * Optional multi-turn orchestration strategy configuration (e.g. PyRIT Crescendo / TAP / PAIR).
   */
  @Type(JsonType.class)
  @Column(name = "ai_attack_multi_turn", columnDefinition = "jsonb")
  @JsonProperty("ai_attack_multi_turn")
  private Map<String, Object> multiTurn = new HashMap<>();

  /**
   * Obfuscation / evasion converters applied to the attack content (e.g. base64, rot13, leetspeak).
   */
  @Type(StringArrayType.class)
  @Column(name = "ai_attack_converters", columnDefinition = "text[]")
  @JsonProperty("ai_attack_converters")
  private String[] converters = new String[0];

  /**
   * Success-detection configuration used to decide whether the attack succeeded (the target is
   * vulnerable): refusal detection, canary/marker leakage, regex, or LLM-as-judge.
   */
  @Type(JsonType.class)
  @Column(name = "ai_attack_success_detector", columnDefinition = "jsonb")
  @JsonProperty("ai_attack_success_detector")
  private Map<String, Object> successDetector = new HashMap<>();

  public AiAttack() {}

  public AiAttack(String id, String type, String name) {
    super(id, type, name);
  }
}
