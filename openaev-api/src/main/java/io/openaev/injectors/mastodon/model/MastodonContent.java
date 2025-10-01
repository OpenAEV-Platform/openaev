package io.openaev.injectors.mastodon.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import org.springframework.util.StringUtils;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MastodonContent {

  @JsonProperty("token")
  private String token;

  @JsonProperty("status")
  private String status;

  public String buildStatus(String footer, String header) {
    StringBuilder data = new StringBuilder();
    if (StringUtils.hasLength(header)) {
      data.append(header).append("\r\n");
    }
    data.append(status);
    if (StringUtils.hasLength(footer)) {
      data.append("\r\n").append(footer);
    }
    return data.toString();
  }

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MastodonContent that = (MastodonContent) o;
    return Objects.equals(token, that.token) && Objects.equals(status, that.status);
  }

  @Override
  public int hashCode() {
    return Objects.hash(token, status);
  }
}
