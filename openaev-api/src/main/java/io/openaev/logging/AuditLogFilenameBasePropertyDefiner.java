package io.openaev.logging;

import ch.qos.logback.core.PropertyDefinerBase;
import org.apache.commons.lang3.StringUtils;

/**
 * Normalizes audit log filename input into a basename without ".log" so both legacy values
 * ("audit.log") and new values ("audit") resolve to the same rollover naming.
 */
public class AuditLogFilenameBasePropertyDefiner extends PropertyDefinerBase {

  private static final String DEFAULT_BASENAME = "audit";

  private String value;
  private String fromProperties;
  private String fromEnvVariable;

  public void setValue(String value) {
    this.value = value;
  }

  public void setFromProperties(String fromProperties) {
    this.fromProperties = fromProperties;
  }

  public void setFromEnvVariable(String fromEnvVariable) {
    this.fromEnvVariable = fromEnvVariable;
  }

  @Override
  public String getPropertyValue() {
    String normalized =
        StringUtils.firstNonBlank(fromProperties, fromEnvVariable, value, DEFAULT_BASENAME).trim();
    if (normalized.toLowerCase().endsWith(".log")) {
      normalized = normalized.substring(0, normalized.length() - 4);
    }
    return StringUtils.isBlank(normalized) ? DEFAULT_BASENAME : normalized;
  }
}
