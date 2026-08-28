package io.openaev.injectors.ovh.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.injectors.common.model.BaseInjectContent;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import org.springframework.util.StringUtils;

@Setter
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class OvhSmsContent extends BaseInjectContent {

  @JsonProperty("message")
  private String message;

  public String buildMessage(String footer, String header) {
    StringBuilder data = new StringBuilder();
    if (StringUtils.hasLength(header)) {
      data.append(header).append("\r\n");
    }
    data.append(message);
    if (StringUtils.hasLength(footer)) {
      data.append("\r\n").append(footer);
    }
    return data.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    OvhSmsContent that = (OvhSmsContent) o;
    return Objects.equals(message, that.message);
  }

  @Override
  public int hashCode() {
    return Objects.hash(message);
  }
}
