package io.openaev.rest.scenario.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;
import static io.openaev.helper.MailHelper.*;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Scenario.SEVERITY;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ScenarioInput {

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("scenario_name")
  private String name;

  @JsonProperty("scenario_description")
  private String description;

  @JsonProperty("scenario_subtitle")
  private String subtitle;

  @Nullable
  @JsonProperty("scenario_category")
  private String category;

  @Nullable
  @JsonProperty("scenario_main_focus")
  private String mainFocus;

  @Nullable
  @JsonProperty("scenario_severity")
  private SEVERITY severity;

  @Nullable
  @JsonProperty("scenario_external_reference")
  private String externalReference;

  @Nullable
  @JsonProperty("scenario_external_url")
  private String externalUrl;

  @JsonProperty("scenario_tags")
  private List<String> tagIds = new ArrayList<>();

  @Pattern(regexp = FROM_NAME_PATTERN, message = FROM_NAME_PATTERN_MESSAGE)
  @Size(max = FROM_NAME_MAX_LENGTH, message = FROM_NAME_SIZE_MESSAGE)
  @JsonProperty("scenario_mail_from_name")
  private String fromName;

  @JsonProperty("scenario_mails_reply_to")
  private List<String> replyTos = new ArrayList<>();

  @JsonProperty("scenario_message_header")
  private String header;

  @JsonProperty("scenario_message_footer")
  private String footer;

  @JsonProperty("scenario_custom_dashboard")
  private String customDashboard;

  /** Indicate if this scenario will use the chaining engine or the legacy one */
  @JsonProperty("scenario_is_chaining")
  private Boolean isChaining = false;
}
