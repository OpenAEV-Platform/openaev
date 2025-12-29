package io.openaev.config;

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
@Data
@ConfigurationProperties(prefix = "openaev")
public class OpenAEVConfig {

  @JsonProperty("parameters_id")
  @Value("${openbas.id:${openaev.id:global}}")
  private String id;

  @JsonProperty("application_name")
  @Value("${openbas.name:${openaev.name:OpenAEV}}")
  private String name;

  @JsonProperty("application_license")
  @Value("${openbas.application-license:${openaev.application-license:}}")
  private String applicationLicense;

  @JsonProperty("application_base_url")
  @Value("${openbas.base-url:${openaev.base-url:#{null}}}")
  private String baseUrl;

  @JsonProperty("application_version")
  @Value("${openbas.version:${openaev.version:#{null}}}")
  private String version;

  @JsonProperty("map_tile_server_light")
  @Value("${openbas.map-tile-server-light:${openaev.map-tile-server-light:#{null}}}")
  private String mapTileServerLight;

  @JsonProperty("map_tile_server_dark")
  @Value("${openbas.map-tile-server-dark:${openaev.map-tile-server-dark:#{null}}}")
  private String mapTileServerDark;

  @JsonProperty("auth_local_enable")
  @Value("${openbas.auth-local-enable:${openaev.auth-local-enable:false}}")
  private boolean authLocalEnable;

  @JsonProperty("auth_openid_enable")
  @Value("${openbas.auth-openid-enable:${openaev.auth-openid-enable:false}}")
  private boolean authOpenidEnable;

  @JsonProperty("auth_saml2_enable")
  @Value("${openbas.auth-saml2-enable:${openaev.auth-saml2-enable:false}}")
  private boolean authSaml2Enable;

  @JsonProperty("auth_kerberos_enable")
  @Value("${openbas.auth-kerberos-enable:${openaev.auth-kerberos-enable:false}}")
  private boolean authKerberosEnable;

  @JsonProperty("default_mailer")
  @Value("${openbas.default-mailer:${openaev.default-mailer:#{null}}}")
  private String defaultMailer;

  @JsonProperty("default_reply_to")
  @Value("${openbas.default-reply-to:${openaev.default-reply-to:#{null}}}")
  private String defaultReplyTo;

  @JsonProperty("admin_token")
  @Value("${openbas.admin.token:${openaev.admin.token:#{null}}}")
  private String adminToken;

  @JsonProperty("enabled_dev_features")
  @Value("${openbas.enabled-dev-features:${openaev.enabled-dev-features:}}")
  private String enabledDevFeatures;

  @JsonProperty("instance_id")
  @Value("${openbas.instance-id:${openaev.instance-id:#{null}}}")
  private String instanceId;

  @JsonIgnore
  @Value("${openbas.cookie-name:${openaev.cookie-name:openaev_token}}")
  private String cookieName;

  @JsonIgnore
  @Value("${openbas.cookie-duration:${openaev.cookie-duration:P1D}}")
  private String cookieDuration;

  @JsonIgnore
  @Value("${openbas.cookie-secure:${openaev.cookie-secure:false}}")
  private boolean cookieSecure;

  @JsonProperty("application_agent_url")
  @Value("${openbas.agent-url:${openaev.agent-url:#{null}}}")
  private String agentUrl;

  @JsonProperty("unsecured_certificate")
  @Value("${openbas.unsecured-certificate:${openaev.unsecured-certificate:false}}")
  private boolean unsecuredCertificate;

  @JsonProperty("with_proxy")
  @Value("${openbas.with-proxy:${openaev.with-proxy:false}}")
  private boolean withProxy;

  @JsonProperty("extra_trusted_certs_dir")
  @Value("${openbas.extra-trusted-certs-dir:${openaev.extra-trusted-certs-dir:#{null}}}")
  private String extraTrustedCertsDir;

  @JsonProperty("queue-config")
  @Value("${openbas.queue-config:${openaev.queue-config:#{null}}}")
  private Map<String, QueueConfig> queueConfig;

  @JsonProperty("logout_success_url")
  @Value("${openbas.logout-success-url:${openaev.logout-success-url:/}}")
  private String logoutSuccessUrl;

  @JsonProperty("frontend_url")
  @Value("${openbas.frontend-url:${openaev.frontend-url:}}")
  private String frontendUrl;

  public String getBaseUrl() {
    return url(baseUrl);
  }

  public String getBaseUrlForAgent() {
    return hasText(agentUrl) ? url(agentUrl) : url(baseUrl);
  }

  // -- PRIVATE --

  private String url(@NotBlank final String url) {
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
