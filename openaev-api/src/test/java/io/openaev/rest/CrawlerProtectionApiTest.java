package io.openaev.rest;

import static io.openaev.rest.CrawlerProtectionApi.NO_INDEX_DIRECTIVES;
import static io.openaev.rest.CrawlerProtectionApi.ROBOTS_TXT_BODY;
import static io.openaev.rest.CrawlerProtectionApi.X_ROBOTS_TAG_HEADER;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@TestInstance(PER_CLASS)
@DisplayName("Crawler Protection API tests")
class CrawlerProtectionApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;

  @Nested
  @DisplayName("GET /robots.txt")
  class RobotsTxt {

    @Test
    @DisplayName("Should return disallow-all robots.txt without authentication")
    void given_unauthenticatedRequest_should_returnDisallowAll() throws Exception {
      // Arrange & Act
      var result = mvc.perform(get("/robots.txt"));

      // Assert
      result
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
          .andExpect(content().string(ROBOTS_TXT_BODY))
          .andExpect(header().exists("Cache-Control"))
          .andExpect(header().string(X_ROBOTS_TAG_HEADER, NO_INDEX_DIRECTIVES));
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("Should return disallow-all robots.txt for authenticated user")
    void given_authenticatedRequest_should_returnDisallowAll() throws Exception {
      // Arrange & Act
      var result = mvc.perform(get("/robots.txt"));

      // Assert
      result.andExpect(status().isOk()).andExpect(content().string(ROBOTS_TXT_BODY));
    }
  }

  @Nested
  @DisplayName("GET /sitemap.xml")
  class SitemapXml {

    @Test
    @DisplayName("Should return empty sitemap without authentication")
    void given_unauthenticatedRequest_should_returnEmptySitemap() throws Exception {
      // Arrange & Act
      var result = mvc.perform(get("/sitemap.xml"));

      // Assert
      result
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
          .andExpect(
              content()
                  .xml(
                      """
              <?xml version="1.0" encoding="UTF-8"?>
              <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9"></urlset>
              """))
          .andExpect(header().exists("Cache-Control"))
          .andExpect(header().string(X_ROBOTS_TAG_HEADER, NO_INDEX_DIRECTIVES));
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("Should return empty sitemap for authenticated user")
    void given_authenticatedRequest_should_returnEmptySitemap() throws Exception {
      // Arrange & Act
      var result = mvc.perform(get("/sitemap.xml"));

      // Assert
      result
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML));
    }
  }

  @Nested
  @DisplayName("X-Robots-Tag header filter")
  @WithMockUser(isAdmin = true)
  class XRobotsTagFilter {

    @Test
    @DisplayName("Should add X-Robots-Tag header to any API response")
    void given_anyRequest_should_includeXRobotsTagHeader() throws Exception {
      // Arrange — use a known public-ish endpoint (platform settings)
      var result = mvc.perform(get("/api/settings").accept(MediaType.APPLICATION_JSON));

      // Assert — regardless of response status, the header must be present
      result.andExpect(header().string(X_ROBOTS_TAG_HEADER, NO_INDEX_DIRECTIVES));
    }
  }
}
