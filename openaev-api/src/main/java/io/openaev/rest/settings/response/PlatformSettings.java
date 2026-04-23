package io.openaev.rest.settings.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.ee.License;
import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PlatformSettings extends PublicPlatformSettings {

  @JsonProperty("platform_id")
  @Schema(description = "id of the platform")
  private String platformId;

  @NotBlank
  @JsonProperty("platform_name")
  @Schema(description = "Name of the platform")
  private String platformName;

  @JsonProperty("platform_base_url")
  @Schema(description = "Base URL of the platform")
  private String platformBaseUrl;

  @JsonProperty("platform_agent_url")
  @Schema(description = "Agent URL of the platform")
  private String platformAgentUrl;

  @JsonProperty("platform_home_dashboard")
  @Schema(description = "Default home dashboard of the platform")
  private String platformHomeDashboard;

  @JsonProperty("platform_scenario_dashboard")
  @Schema(description = "Default scenario dashboard of the platform")
  private String platformScenarioDashboard;

  @JsonProperty("platform_simulation_dashboard")
  @Schema(description = "Default simulation dashboard of the platform")
  private String platformSimulationDashboard;

  @JsonProperty("map_tile_server_light")
  @Schema(description = "URL of the server containing the map tile with light theme")
  private String mapTileServerLight;

  @JsonProperty("map_tile_server_dark")
  @Schema(description = "URL of the server containing the map tile with dark theme")
  private String mapTileServerDark;

  @JsonProperty("xtm_opencti_enable")
  @Schema(description = "True if connection with OpenCTI is enabled")
  private Boolean xtmOpenctiEnable;

  @JsonProperty("xtm_opencti_url")
  @Schema(description = "Url of OpenCTI")
  private String xtmOpenctiUrl;

  @JsonProperty("telemetry_manager_enable")
  @Schema(description = "True if telemetry manager enable")
  private Boolean telemetryManagerEnable;

  @JsonProperty("platform_version")
  @Schema(description = "Current version of the platform")
  private String platformVersion;

  @JsonProperty("postgre_version")
  @Schema(description = "Current version of the PostgreSQL")
  private String postgreVersion;

  @JsonProperty("java_version")
  @Schema(description = "Current version of Java")
  private String javaVersion;

  @JsonProperty("rabbitmq_version")
  @Schema(description = "Current version of RabbitMQ")
  private String rabbitMQVersion;

  @JsonProperty("analytics_engine_type")
  @Schema(description = "Type of analytics engine")
  private String analyticsEngineType;

  @JsonProperty("analytics_engine_version")
  @Schema(description = "Current version of analytics engine")
  private String analyticsEngineVersion;

  @JsonProperty("platform_ai_enabled")
  @Schema(description = "True if AI is enabled for the platform")
  private Boolean aiEnabled;

  @JsonProperty("platform_ai_has_token")
  @Schema(description = "True if we have an AI token")
  private Boolean aiHasToken;

  @JsonProperty("platform_ai_type")
  @Schema(
      description = "Type of AI (mistralai or openai)",
      externalDocs =
          @ExternalDocumentation(
              description = "How to configure AI service",
              url =
                  "https://docs.openaev.io/latest/deployment/configuration/?h=ai+type#ai-service"))
  private String aiType;

  @JsonProperty("platform_ai_model")
  @Schema(description = "Chosen model of AI")
  private String aiModel;

  @JsonProperty("executor_tanium_enable")
  @Schema(description = "True if the Tanium Executor is enabled")
  private Boolean executorTaniumEnable;

  // EXPECTATION
  @NotNull
  @JsonProperty("expectation_detection_expiration_time")
  @Schema(description = "Time to wait before detection time has expired")
  private long detectionExpirationTime;

  @NotNull
  @JsonProperty("expectation_prevention_expiration_time")
  @Schema(description = "Time to wait before prevention time has expired")
  private long preventionExpirationTime;

  @NotNull
  @JsonProperty("expectation_vulnerability_expiration_time")
  @Schema(description = "Time to wait before vulnerability time has expired")
  private long vulnerabilityExpirationTime;

  @NotNull
  @JsonProperty("expectation_challenge_expiration_time")
  @Schema(description = "Time to wait before challenge time has expired")
  private long challengeExpirationTime;

  @NotNull
  @JsonProperty("expectation_article_expiration_time")
  @Schema(description = "Time to wait before article time has expired")
  private long articleExpirationTime;

  @NotNull
  @JsonProperty("expectation_manual_expiration_time")
  @Schema(description = "Time to wait before manual expectation time has expired")
  private long manualExpirationTime;

  @NotNull
  @JsonProperty("expectation_manual_default_score_value")
  @Schema(description = "Default score for manuel expectation")
  private int expectationDefaultScoreValue;

  // EMAIL CONFIG
  @JsonProperty("default_mailer")
  @Schema(description = "Sender mail to use by default for injects")
  private String defaultMailer;

  @JsonProperty("default_reply_to")
  @Schema(description = "Reply to mail to use by default for injects")
  private String defaultReplyTo;

  // XTM Hub
  @JsonProperty("xtm_hub_enable")
  @Schema(description = "True if connection with XTM Hub is enabled")
  private Boolean xtmHubEnable;

  @JsonProperty("xtm_hub_url")
  @Schema(description = "Url of XTM Hub")
  private String xtmHubUrl;

  @JsonProperty("xtm_hub_reachable")
  @Schema(description = "True if xtmhub backend is reachable")
  private Boolean xtmHubReachable;

  @JsonProperty("xtm_hub_token")
  @Schema(description = "XTM Hub token")
  private String xtmHubToken;

  @JsonProperty("xtm_hub_registration_status")
  @Schema(description = "XTM Hub registration status")
  private String xtmHubRegistrationStatus;

  @JsonProperty("xtm_hub_registration_date")
  @Schema(description = "XTM Hub registration date")
  private String xtmHubRegistrationDate;

  @JsonProperty("xtm_hub_registration_user_id")
  @Schema(description = "XTM Hub registration user id")
  private String xtmHubRegistrationUserId;

  @JsonProperty("xtm_hub_registration_user_name")
  @Schema(description = "XTM Hub registration user name")
  private String xtmHubRegistrationUserName;

  @JsonProperty("xtm_hub_last_connectivity_check")
  @Schema(description = "XTM Hub last connectivity check")
  private String xtmHubLastConnectivityCheck;

  @JsonProperty("xtm_hub_should_send_connectivity_email")
  @Schema(description = "XTM Hub should send connectivity email")
  private String xtmHubShouldSendConnectivityEmail;

  @JsonProperty("smtp_service_available")
  @Schema(description = "SMTP Service availability")
  private String smtpServiceAvailable;

  @JsonProperty("imap_service_available")
  @Schema(description = "IMAP Service availability")
  private String imapServiceAvailable;

  @JsonProperty("platform_license")
  @Schema(description = "Platform licensing information")
  private License platformLicense;
}
