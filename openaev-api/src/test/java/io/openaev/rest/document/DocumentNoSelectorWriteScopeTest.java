package io.openaev.rest.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.service.MinioService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * The document create endpoints carry {@code @RequireTenantSelector}: when no selector is present
 * ({@code X-Tenant-Ids} or a {@code /{tenantId}} path), the write tenant is resolved from the
 * caller's memberships (issues #6331 / #6332). This pins the whole caller table, not only the
 * ambiguous case an admin enrolled in two non-default tenants already covers: a caller member of a
 * single tenant lands there, a multi-tenant caller with access to the default tenant falls back to
 * it, and a multi-tenant caller without the default is refused with 400.
 *
 * <p>Each test provisions the caller's memberships explicitly, since {@code @WithMockUser} starts
 * with no row in {@code users_tenants}. The row tenant is read with raw JDBC on the test's own
 * connection so the statement inspector (documents is armed here) never rewrites the ground-truth
 * read. Objects written to real storage are removed in teardown.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=documents")
@WithMockUser(isAdmin = true)
@DisplayName("Document create resolves the write tenant from the caller when no selector is given")
class DocumentNoSelectorWriteScopeTest extends IntegrationTest {

  private static final String DOCUMENTS = "/api/documents";
  private static final String DEFAULT_TENANT = Tenant.DEFAULT_TENANT_UUID;

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private MinioService minioService;

  private final List<String[]> uploadedObjects = new ArrayList<>();

  @AfterEach
  void clearContext() {
    for (String[] object : uploadedObjects) {
      try {
        minioService.deleteFileForTenant(object[0], object[1]);
      } catch (Exception e) {
        // best-effort cleanup: a missing object must not fail teardown
      }
    }
    uploadedObjects.clear();
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("POST /api/documents with no tenant selector")
  class NoSelector {

    @Test
    @DisplayName("given_singleTenantCaller_should_attributeRowToThatTenant")
    void given_singleTenantCaller_should_attributeRowToThatTenant() throws Exception {
      // Arrange: the caller is a member of exactly one tenant, so an absent selector is
      // unambiguous.
      String tenant = tenantHelper.createTenantWithCurrentUser("nosel-single").getId();

      // Act
      String id = uploadWithoutSelector();

      // Assert
      assertEquals(
          tenant,
          rowTenant(id),
          "a single-tenant caller with no selector must land the row in its own tenant");
    }

    @Test
    @DisplayName("given_multiTenantCallerWithDefault_should_fallBackToDefaultTenant")
    void given_multiTenantCallerWithDefault_should_fallBackToDefaultTenant() throws Exception {
      // Arrange: the caller belongs to the default tenant plus one more, so the platform-wide
      // no-context convention applies and the write falls back to the default tenant.
      tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT);
      tenantHelper.createTenantWithCurrentUser("nosel-multi-default");

      // Act
      String id = uploadWithoutSelector();

      // Assert
      assertEquals(
          DEFAULT_TENANT,
          rowTenant(id),
          "a multi-tenant caller with default access must fall back to the default tenant");
    }

    @Test
    @DisplayName("given_multiTenantCallerWithoutDefault_should_return400")
    void given_multiTenantCallerWithoutDefault_should_return400() throws Exception {
      // Arrange: two non-default tenants and no default access, the one genuinely ambiguous case.
      tenantHelper.createTenantWithCurrentUser("nosel-multi-a");
      tenantHelper.createTenantWithCurrentUser("nosel-multi-b");

      // Act & Assert
      mvc.perform(uploadRequest().with(csrf())).andExpect(status().isBadRequest());
    }
  }

  private MockMultipartHttpServletRequestBuilder uploadRequest() {
    MockMultipartHttpServletRequestBuilder request = multipart(DOCUMENTS);
    MockPart inputPart = new MockPart("input", "{}".getBytes(StandardCharsets.UTF_8));
    inputPart.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    MockMultipartFile filePart =
        new MockMultipartFile(
            "file",
            "nosel-" + UUID.randomUUID() + ".txt",
            MediaType.TEXT_PLAIN_VALUE,
            ("nosel-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8));
    request.part(inputPart).file(filePart);
    return request;
  }

  private String uploadWithoutSelector() throws Exception {
    String response =
        mvc.perform(uploadRequest().with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = JsonPath.read(response, "$.document_id");
    uploadedObjects.add(new String[] {rowTenant(id), rawTarget(id)});
    return id;
  }

  private String rawColumn(String documentId, String column) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "SELECT " + column + " FROM documents WHERE document_id = ?")) {
                statement.setString(1, documentId);
                try (ResultSet resultSet = statement.executeQuery()) {
                  return resultSet.next() ? resultSet.getString(1) : null;
                }
              }
            });
  }

  private String rowTenant(String documentId) {
    return rawColumn(documentId, "tenant_id");
  }

  private String rawTarget(String documentId) {
    return rawColumn(documentId, "document_target");
  }
}
