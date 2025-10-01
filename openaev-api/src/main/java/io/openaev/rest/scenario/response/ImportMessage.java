package io.openaev.rest.scenario.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ImportMessage {
  public enum MessageLevel {
    CRITICAL,
    ERROR,
    WARN,
    INFO
  }

  public enum ErrorCode {
    NO_POTENTIAL_MATCH_FOUND("no_potential_match_found"),
    SEVERAL_MATCHES("several_matches"),
    ABSOLUTE_TIME_WITHOUT_START_DATE("absolute_time_without_start_date"),
    DATE_SET_IN_PAST("date_set_in_past"),
    DATE_SET_IN_FUTURE("date_set_in_future"),
    NO_TEAM_FOUND("no_team_found"),
    EXPECTATION_SCORE_UNDEFINED("expectation_score_undefined");

    public final String code;

    ErrorCode(String code) {
      this.code = code;
    }
  }

  @JsonProperty("message_level")
  private MessageLevel messageLevel;

  @JsonProperty("message_code")
  private ErrorCode errorCode;

  @JsonProperty("message_params")
  private Map<String, String> params = null;

  public ImportMessage(MessageLevel level, ErrorCode errorCode, Map<String, String> params) {
    this.messageLevel = level;
    this.errorCode = errorCode;
    this.params = params;
  }

  public ImportMessage(MessageLevel level, ErrorCode errorCode) {
    this.messageLevel = level;
    this.errorCode = errorCode;
  }
}
