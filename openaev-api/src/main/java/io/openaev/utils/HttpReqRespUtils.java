package io.openaev.utils;

import io.openaev.config.ThreadPoolTaskLoggerConfig;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

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
    "X-Real-IP",
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
    HttpServletRequest request = getCurrentRequest();
    Map<String, String> headers = extractHeaders(request);

    String ip = getClientIpAddressFromHeaders(headers);

    if (ip != null) {
      return ip;
    }

    if (request != null) {
      return request.getRemoteAddr();
    }

    String remoteAddress = ThreadPoolTaskLoggerConfig.ThreadRequestContextHolder.getRemoteAddress();

    if (remoteAddress != null) {
      return remoteAddress;
    }

    return "0.0.0.0";
  }

  public static String getClientIpAddressFromHeaders(Map<String, String> headers) {
    if (headers != null) {
      for (String header : IP_HEADER_CANDIDATES) {
        String ipList = extractHeader(headers, header);

        if (ipList != null && !ipList.isEmpty() && !"unknown".equalsIgnoreCase(ipList)) {
          return ipList.split(",")[0];
        }
      }
    }

    return null;
  }

  public static String extractHeader(Map<String, String> headers, String name) {
    if (headers != null) {
      if (headers.containsKey(name)) {
        return headers.get(name);
      }

      if (headers.containsKey(name.toLowerCase())) {
        return headers.get(name.toLowerCase());
      }

      for (var entry : headers.entrySet()) {
        if (entry.getKey().equalsIgnoreCase(name)) {
          return entry.getValue();
        }
      }
    }

    return null;
  }

  public static Map<String, String> extractHeaders(HttpServletRequest request) {
    try {
      if (request != null) {
        Map<String, String> headers = new HashMap<>();

        Enumeration<String> headerNames = request.getHeaderNames();

        while (headerNames.hasMoreElements()) {
          String headerName = headerNames.nextElement();
          headers.put(headerName, request.getHeader(headerName));
        }

        return headers;
      }
      //else if no headers, the returns the headers saved in the thread context
    } catch(IllegalStateException e) {
      //It means the request object has been recycled and is no longer associated with this facade. In this case returns the headers saved in the thread context
    }

    return ThreadPoolTaskLoggerConfig.ThreadRequestContextHolder.getHeaders();
  }

  public static String extractMethod(HttpServletRequest request) {
    String method = null;

    try {
      if (request != null) {
        method = request.getMethod();
      }
      //else if no method, the returns the method saved in the thread context
    } catch (IllegalStateException e) {
      //It means the request object has been recycled and is no longer associated with this facade. In this case returns the method saved in the thread context
    }

    if (method == null) {
      method = ThreadPoolTaskLoggerConfig.ThreadRequestContextHolder.getMethod();
    }

    return method;
  }

    public static HttpServletRequest getCurrentRequest() {
    try {
      ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
      return attrs != null ? attrs.getRequest() : null;
    } catch (Exception e) {
      return null;
    }
  }
}
