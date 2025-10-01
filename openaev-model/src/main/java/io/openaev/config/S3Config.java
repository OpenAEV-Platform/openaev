package io.openaev.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "openaev.s3")
@Data
public class S3Config {

  @JsonProperty("use-aws-role")
  private boolean useAwsRole = false;

  @JsonProperty("sts-endpoint")
  private String stsEndpoint;
}
