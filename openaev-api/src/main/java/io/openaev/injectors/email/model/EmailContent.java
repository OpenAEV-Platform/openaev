package io.openaev.injectors.email.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.execution.ExecutableInject;
import io.openaev.model.inject.form.Expectation;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import org.springframework.util.StringUtils;

@Getter
@Setter
public class EmailContent {

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

  @JsonProperty("expectations")
  private List<Expectation> expectations = new ArrayList<>();

  public EmailContent() {
    // For mapper
  }

  public String buildMessage(ExecutableInject injection, boolean imapEnabled) {
    // String footer = inject.getFooter();
    String header = injection.getInjection().getInject().getHeader();
    StringBuilder data = new StringBuilder();
    if (StringUtils.hasLength(header)) {
      data.append(HEADER_DIV).append(header).append(END_DIV);
    }
    data.append(START_DIV).append(body).append(END_DIV);
    // If imap is enable we need to inject the id marker
    if (injection.isRuntime() && imapEnabled) {
      data.append(START_DIV)
          .append("<br/><br/><br/><br/>")
          .append(
              "---------------------------------------------------------------------------------<br/>")
          .append("OpenAEV internal information, do not remove!<br/>")
          .append("[inject_id=")
          .append(injection.getInjection().getId())
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
