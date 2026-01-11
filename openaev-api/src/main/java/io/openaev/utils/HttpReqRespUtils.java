package io.openaev.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Utility class for HTTP request and response operations.
 *
 * <p>Provides helper methods for extracting information from HTTP requests, particularly useful for
 * obtaining client IP addresses in environments with proxies, load balancers, or reverse proxies.
 *
 * <p>This is a utility class and cannot be instantiated.
 */
public class HttpReqRespUtils {

  private HttpReqRespUtils() {}

  /**
   * HTTP headers commonly used by proxies and load balancers to forward the original client IP
   * address.
   *
   * <p>Headers are checked in order of preference, with X-Forwarded-For being the most commonly
   * used standard.
   */
  private static final String[] IP_HEADER_CANDIDATES = {
    "X-Forwarded-For",
    "Proxy-Client-IP",
    "WL-Proxy-Client-IP",
    "HTTP_X_FORWARDED_FOR",
    "HTTP_X_FORWARDED",
    "HTTP_X_CLUSTER_CLIENT_IP",
    "HTTP_CLIENT_IP",
    "HTTP_FORWARDED_FOR",
    "HTTP_FORWARDED",
    "HTTP_VIA",
    "REMOTE_ADDR"
  };

  /**
   * Extracts the client IP address from the current HTTP request.
   *
   * <p>This method handles various proxy configurations by checking multiple headers in order of
   * preference. It properly handles comma-separated IP lists (common when multiple proxies are
   * involved) by returning only the first (original client) IP.
   *
   * <p>If no request context exists, returns "0.0.0.0" as a fallback.
   *
   * @return the client IP address, or "0.0.0.0" if no request context is available
   */
  public static String getClientIpAddressIfServletRequestExist() {
    if (RequestContextHolder.getRequestAttributes() == null) {
      return "0.0.0.0";
    }
    HttpServletRequest request =
        ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
    for (String header : IP_HEADER_CANDIDATES) {
      String ipList = request.getHeader(header);
      if (ipList != null && !ipList.isEmpty() && !"unknown".equalsIgnoreCase(ipList)) {
        return ipList.split(",")[0];
      }
    }
    return request.getRemoteAddr();
  }
}
