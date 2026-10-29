package io.openaev.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class RequestUtils {
  public String getContextStrippedUri(HttpServletRequest request) {
    String uri = request.getRequestURI();
    if (!StringUtils.isBlank(uri)) {
      String contextPath = request.getContextPath();
      if (!StringUtils.isBlank(contextPath) && uri.startsWith(contextPath)) {
        return uri.substring(contextPath.length());
      }
    }
    return uri;
  }
}
