package io.openaev.api.chaining;

import io.openaev.api.chaining.dto.ChainingConfigurationOutput;
import io.openaev.api.chaining.dto.ChainingScopeInput;
import io.openaev.api.chaining.dto.ChainingScopeRuleInput;
import io.openaev.database.model.ChainingConfiguration;
import io.openaev.database.model.Scope;
import io.openaev.database.model.ScopeRule;
import io.openaev.database.model.ScopeRuleValueType;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ChainingConfigurationMapper {

  private static final Pattern IPV4_PATTERN =
      Pattern.compile(
          "^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$");

  private static final Pattern IPV4_SUBNET_PATTERN =
      Pattern.compile(
          "^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}/(3[0-2]|[12]?\\d)$");

  private static final Pattern DOMAIN_PATTERN = Pattern.compile(".*[A-Za-z].*");

  // -- Input → Entity --

  /**
   * Applies a {@link ChainingConfigurationInput} DTO onto an existing {@link ChainingConfiguration}
   * entity by copying each flat field directly.
   *
   * @param input the input DTO to read from
   * @param configuration the entity to update in place
   */
  public void applyInput(ChainingConfigurationInput input, ChainingConfiguration configuration) {
    // Rate limit
    configuration.setRateLimitEnabled(input.isRateLimitEnabled());
    configuration.setMaxAttempts(input.getMaxAttempts());
    configuration.setMaxTemporalRateSeconds(input.getMaxTemporalRateSeconds());
    // Timeout
    configuration.setTimeoutEnabled(input.isTimeoutEnabled());
    configuration.setTimeoutSeconds(input.getTimeoutSeconds());
    // Safe mode
    configuration.setSafeModeEnabled(input.isSafeModeEnabled());
    // Scope
    configuration.setScope(toScope(input.getScope()));
  }

  /**
   * Maps a {@link ChainingScopeInput} DTO to a {@link Scope} entity.
   *
   * @param input the scope input, may be {@code null}
   * @return the mapped scope entity, or {@code null} if input is {@code null}
   */
  public Scope toScope(ChainingScopeInput input) {
    if (input == null) {
      return null;
    }
    Scope scope = new Scope();

    List<ScopeRule> scopeRules = new ArrayList<>();
    if (input.getScopeRules() != null) {
      for (ChainingScopeRuleInput ruleInput : input.getScopeRules()) {
        scopeRules.add(toScopeRule(ruleInput, scope));
      }
    }

    scope.setScopeRules(scopeRules);
    return scope;
  }

  /**
   * Maps a {@link ChainingConfiguration} entity to its {@link ChainingConfigurationOutput} DTO.
   *
   * @param configuration the entity to map
   * @return the mapped output DTO
   */
  public ChainingConfigurationOutput toOutput(ChainingConfiguration configuration) {
    return ChainingConfigurationOutput.builder()
        .rateLimitEnabled(configuration.isRateLimitEnabled())
        .maxAttempts(configuration.getMaxAttempts())
        .maxTemporalRateSeconds(configuration.getMaxTemporalRateSeconds())
        .timeoutEnabled(configuration.isTimeoutEnabled())
        .timeoutSeconds(configuration.getTimeoutSeconds())
        .safeModeEnabled(configuration.isSafeModeEnabled())
        .scope(toScopeOutput(configuration.getScope()))
        .build();
  }

  private ScopeRule toScopeRule(ChainingScopeRuleInput input, Scope scope) {
    ScopeRule rule = new ScopeRule();
    rule.setSelectedMode(input.getSelectedMode());
    rule.setRuleSource(input.getRuleSource());
    rule.setRuleValue(input.getRuleValue());
    rule.setScope(scope);
    rule.setValueType(detectValueType(input));
    return rule;
  }

  private ScopeRuleValueType detectValueType(ChainingScopeRuleInput input) {
    if (input.getRuleSource() != null) {
      switch (input.getRuleSource()) {
        case ASSET:
          return ScopeRuleValueType.ASSET_ID;
        case ASSET_GROUP:
          return ScopeRuleValueType.ASSET_GROUP_ID;
        default:
          break;
      }
    }

    String value = input.getRuleValue() != null ? input.getRuleValue().trim() : "";
    if (IPV4_SUBNET_PATTERN.matcher(value).matches()) {
      return ScopeRuleValueType.IP_SUBNET;
    }
    if (IPV4_PATTERN.matcher(value).matches()) {
      return ScopeRuleValueType.IP;
    }
    if (DOMAIN_PATTERN.matcher(value).matches()) {
      return ScopeRuleValueType.DOMAIN;
    }

    return ScopeRuleValueType.DOMAIN;
  }
}
