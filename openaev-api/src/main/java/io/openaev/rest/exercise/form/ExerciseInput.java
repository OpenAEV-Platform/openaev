package io.openaev.rest.exercise.form;

import static io.openaev.config.AppConfig.EMAIL_FORMAT;
import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Data
public class ExerciseInput {

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("exercise_name")
  private String name;

  @JsonProperty("exercise_subtitle")
  private String subtitle;

  @Nullable
  @JsonProperty("exercise_category")
  private String category;

  @Nullable
  @JsonProperty("exercise_main_focus")
  private String mainFocus;

  @Nullable
  @JsonProperty("exercise_severity")
  private String severity;

  @Nullable
  @JsonProperty("exercise_description")
  private String description;

  @JsonProperty("exercise_tags")
  private List<String> tagIds = new ArrayList<>();

  @Email(message = EMAIL_FORMAT)
  @JsonProperty("exercise_mail_from")
  private String from;

  @JsonProperty("exercise_mails_reply_to")
  private List<String> replyTos;

  @JsonProperty("exercise_message_header")
  private String header;

  @JsonProperty("exercise_message_footer")
  private String footer;
}
