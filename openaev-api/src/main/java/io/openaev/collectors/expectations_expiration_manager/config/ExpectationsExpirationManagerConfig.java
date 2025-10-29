package io.openaev.collectors.expectations_expiration_manager.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("collector.expectations-expiration-manager")
@Getter
@Setter
public class ExpectationsExpirationManagerConfig {

  public static final String COLLECTOR_ID = "96e476e0-b9c4-4660-869c-98585adf754d";

  private boolean enable = true;
  private String id = COLLECTOR_ID;
  private int interval = 60;
  private int expirationTimeForAsset = 3600; // 1 hour
  private int expirationTime = 21600; // 6 hours

  public int getAssetExpirationTimeInMinute() {
    return this.expirationTimeForAsset / 60;
  }

  public int getExpirationTimeInMinute() {
    return this.expirationTime / 60;
  }
}
