package io.openaev.rest;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.openaev.aop.RBAC;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeApi {

  private static String readResourceAsString(Resource resource) {
    try (Reader reader = new InputStreamReader(resource.getInputStream(), UTF_8)) {
      return FileCopyUtils.copyToString(reader);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Value("${server.servlet.context-path}")
  private String contextPath;

  @GetMapping(
      path = {"/", "/{path:^(?!api$|login$|logout$|oauth2$|saml2$|static$|swagger-ui$).*$}/**"},
      produces = MediaType.TEXT_HTML_VALUE)
  @RBAC(skipRBAC = true) // No RBAC check for home endpoint
  public ResponseEntity<String> home() {
    ClassPathResource classPathResource = new ClassPathResource("/build/index.html");
    String index = readResourceAsString(classPathResource);
    String basePath =
        this.contextPath.endsWith("/")
            ? this.contextPath.substring(0, this.contextPath.length() - 1)
            : this.contextPath;
    String newIndex =
        index
            .replaceAll("%APP_TITLE%", "OpenAEV - Open Breach & Attack Simulation Platform")
            .replaceAll(
                "%APP_DESCRIPTION%",
                "OpenAEV is an open source platform allowing organizations to plan, schedule and conduct adversary simulation campaigns and cyber crisis exercises.")
            .replaceAll("%APP_FAVICON%", basePath + "/static/ext/favicon.png")
            .replaceAll("%APP_MANIFEST%", basePath + "/static/ext/manifest.json")
            .replaceAll("%BASE_PATH%", basePath);
    return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-cache").body(newIndex);
  }
}
