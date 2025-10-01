package io.openaev.xtmhub;

import io.openaev.xtmhub.config.XtmHubConfig;
import jakarta.annotation.PostConstruct;
import java.net.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class XtmHubConnectivityService {
  private final XtmHubConfig XtmHubConfig;

  @Getter private boolean isReachable;

  @PostConstruct
  void init() {
    this.isReachable = XtmHubConfig.getEnable() && checkIsReachable();
  }

  boolean checkIsReachable() {
    HttpURLConnection connection = null;
    try {
      URI uri = new URI(XtmHubConfig.getApiUrl());
      connection = (HttpURLConnection) uri.toURL().openConnection();
      connection.setRequestMethod("HEAD");
      connection.setConnectTimeout(5000);
      connection.setReadTimeout(5000);
      return HttpStatus.valueOf(connection.getResponseCode()).is2xxSuccessful();
    } catch (Exception e) {
      return false;
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }
}
