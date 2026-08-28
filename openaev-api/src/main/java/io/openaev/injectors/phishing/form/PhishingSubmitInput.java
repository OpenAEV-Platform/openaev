package io.openaev.injectors.phishing.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * Credentials submitted by a victim on the public phishing landing page. {@code username} / {@code
 * password} are the captured pair; {@code data} carries any additional form fields for
 * completeness.
 */
@Getter
@Setter
public class PhishingSubmitInput {

  @JsonProperty("username")
  private String username;

  @JsonProperty("password")
  private String password;

  @JsonProperty("data")
  private Map<String, String> data;
}
