package io.openaev.injectors.ovh.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ovh.sms")
@Getter
@Setter
public class OvhSmsConfig {

  @NotBlank private Boolean enable;

  @NotBlank private String ak;

  @NotBlank private String as;

  @NotBlank private String ck;

  @NotBlank private String service;

  @NotBlank private String sender;
}
