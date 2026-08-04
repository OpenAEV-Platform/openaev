package io.openaev.injectors.email.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.execution.ExecutableInject;
import io.openaev.injectors.common.model.BaseInjectContent;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import org.springframework.util.StringUtils;

@Getter
@Setter
public class EmailContent extends BaseInjectContent {

  private static final String HEADER_DIV =
      "<div style=\"text-align: center; margin-bottom: 10px;\">";
  private static final String START_DIV = "<div>";
  private static final String END_DIV = "</div>";

  @JsonProperty("body")
  private String body;

  @JsonProperty("subject")
  private String subject;

  @JsonProperty("inReplyTo")
  private String inReplyTo;

  @JsonProperty("encrypted")
  private boolean encrypted;

  // Raw recipient addresses for finding-driven chaining: a chaining MAPPER can bind an "email"
  // finding into this content field so the inject delivers to that address without a team. Held as
  // a free-text field (a scalar finding value, or an operator-typed comma/semicolon/space separated
  // list); parsed via getParsedRecipients().
  @JsonProperty("recipients")
  private String recipients;

  public EmailContent() {
    // For mapper
  }

  /**
   * Parses {@link #recipients} into distinct, trimmed raw email addresses (split on comma,
   * semicolon or whitespace). Empty when no manual recipients were provided/mapped.
   */
  public List<String> getParsedRecipients() {
    if (!StringUtils.hasText(recipients)) {
      return List.of();
    }
    return Arrays.stream(recipients.split("[,;\\s]+"))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .distinct()
        .toList();
  }

  public String buildMessage(ExecutableInject injection, String baseUrl) {
    // String footer = inject.getFooter();
    String header = injection.getInjection().getInject().getHeader();
    StringBuilder data = new StringBuilder();
    if (StringUtils.hasLength(header)) {
      data.append(HEADER_DIV).append(header).append(END_DIV);
    }
    data.append(START_DIV).append(body).append(END_DIV);
    if (injection.isRuntime()) {
      data.append(START_DIV)
          .append("<br/><br/><br/><br/>")
          .append(
              "---------------------------------------------------------------------------------<br/>")
          .append("OpenAEV internal information, do not remove!<br/>")
          .append("[inject_id=")
          .append(injection.getInjection().getId())
          .append("]<br/>")
          .append("[base_url=")
          .append(baseUrl)
          .append("]<br/>")
          .append(
              "---------------------------------------------------------------------------------<br/>")
          .append(END_DIV);
    }
    return data.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    EmailContent that = (EmailContent) o;
    return encrypted == that.encrypted
        && Objects.equals(body, that.body)
        && Objects.equals(subject, that.subject);
  }

  @Override
  public int hashCode() {
    return Objects.hash(body, subject, encrypted);
  }
}
