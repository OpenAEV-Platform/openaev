package io.openaev.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configuration class for OpenAEV admin authentication.
 *
 * <p>This component holds the admin token used for API authentication in automated scenarios and
 * initial platform setup.
 *
 * <p><b>Security Note:</b> The admin token provides full platform access and should be kept secret.
 * It is excluded from JSON serialization to prevent accidental exposure.
 */
@Component
@Data
public class OpenAEVAdminConfig {

  /**
   * The admin authentication token.
   *
   * <p>This token grants administrative access to the platform API.
   */
  @JsonIgnore
  @Value("${openbas.admin.token:${openaev.admin.token:#{null}}}")
  private String token;

  @JsonIgnore
  @Value("${openaev.admin.encryption_key:#{null}}")
  private String encryptionKey;

  @JsonIgnore
  @Value("${openaev.admin.encryption_salt:#{null}}")
  private String encryptionSalt;
}
