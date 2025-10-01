package io.openaev.rest.stream.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ai")
public class AiConfig {

  @JsonProperty("enabled")
  private boolean enabled;

  @JsonProperty("type")
  private String type;

  @JsonProperty("endpoint")
  private String endpoint;

  @JsonProperty("token")
  private String token;

  @JsonProperty("model")
  private String model;

  @JsonProperty("model_images")
  private String modelImages;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getEndpoint() {
    return endpoint;
  }

  public void setEndpoint(String endpoint) {
    this.endpoint = endpoint;
  }

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public String getModelImages() {
    return modelImages;
  }

  public void setModelImages(String modelImages) {
    this.modelImages = modelImages;
  }
}
