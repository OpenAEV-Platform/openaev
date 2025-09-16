package io.openbas.config;

import static org.springframework.util.StringUtils.hasText;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "openbas")
@Data
public class OpenBASConfig {

  @JsonProperty("parameters_id")
  private String id = "global";

  @JsonProperty("application_name")
  private String name = "OpenBAS";

  @JsonProperty("application_license")
  private String applicationLicense = "";

  @JsonProperty("application_base_url")
  private String baseUrl;

  @JsonProperty("application_version")
  private String version;

  @JsonProperty("map_tile_server_light")
  private String mapTileServerLight;

  @JsonProperty("map_tile_server_dark")
  private String mapTileServerDark;

  @JsonProperty("auth_local_enable")
  private boolean authLocalEnable;

  @JsonProperty("auth_openid_enable")
  private boolean authOpenidEnable;

  @JsonProperty("auth_saml2_enable")
  private boolean authSaml2Enable;

  @JsonProperty("auth_kerberos_enable")
  private boolean authKerberosEnable;

  @JsonProperty("default_mailer")
  private String defaultMailer;

  @JsonProperty("default_reply_to")
  private String defaultReplyTo;

  @JsonProperty("admin_token")
  @Value("${openbas.admin.token:#{null}}")
  private String adminToken;

  @JsonProperty("enabled_dev_features")
  private String enabledDevFeatures = "";

  @JsonProperty("instance_id")
  private String instanceId;

  @JsonIgnore private String cookieName = "openbas_token";

  @JsonIgnore private String cookieDuration = "P1D";

  @JsonIgnore private boolean cookieSecure = false;

  public String getBaseUrl() {
    return url(baseUrl);
  }

  @JsonProperty("application_agent_url")
  private String agentUrl;

  @JsonProperty("unsecured_certificate")
  private boolean unsecuredCertificate;

  @JsonProperty("with_proxy")
  private boolean withProxy;

  @JsonProperty("extra_trusted_certs_dir")
  private String extraTrustedCertsDir;

  private Map<String, QueueConfig> queueConfig;

  public String getBaseUrlForAgent() {
    return hasText(agentUrl) ? url(agentUrl) : url(baseUrl);
  }

  // -- PRIVATE --

  private String url(@NotBlank final String url) {
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
