package io.openaev.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Data
public class S3Config {

  @JsonProperty("use-aws-role")
  @Value("${openbas.s3.use-aws-role:${openaev.s3.use-aws-role:false}}")
  private boolean useAwsRole;

  @JsonProperty("sts-endpoint")
  @Value("${openbas.s3.sts-endpoint:${openaev.s3.sts-endpoint:#{null}}}")
  private String stsEndpoint;
}
