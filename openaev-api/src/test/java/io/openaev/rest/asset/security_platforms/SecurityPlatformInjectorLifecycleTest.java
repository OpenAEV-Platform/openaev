package io.openaev.rest.asset.security_platforms;

import static io.openaev.rest.asset.security_platforms.SecurityPlatformApi.SECURITY_PLATFORM_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Injector;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.rest.asset.security_platforms.form.SecurityPlatformUpsertInput;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regression test for #7063 (follow-up to #7025): a security platform registered by an injector
 * (e.g. Nuclei declaring itself as a VULNERABILITY_SCANNER at startup) must follow the same managed
 * lifecycle as collector-managed platforms:
 *
 * <ul>
 *   <li>the registration upsert (keyed on the injector type as the external reference) links the
 *       injector, so {@code security_platform_injectors} serializes non-empty and the UI keeps the
 *       platform read-only;
 *   <li>deleting the injector from the catalog releases the platform (FK is ON DELETE SET NULL);
 *   <li>a collector-style upsert whose external reference matches no injector type links nothing.
 * </ul>
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Injector-registered security platforms follow the managed lifecycle")
class SecurityPlatformInjectorLifecycleTest extends IntegrationTest {

  private static final String INJECTOR_TYPE = "openaev_nuclei_lifecycle_test";

  @Autowired private MockMvc mvc;
  @Autowired private InjectorRepository injectorRepository;

  private Injector injector;

  @BeforeEach
  void setUp() {
    injector =
        injectorRepository.save(
            InjectorFixture.createInjector(
                UUID.randomUUID().toString(), "Nuclei lifecycle test", INJECTOR_TYPE));
    entityManager.flush();
    entityManager.clear();
  }

  private String upsertPlatform(String externalReference) throws Exception {
    SecurityPlatformUpsertInput input = new SecurityPlatformUpsertInput();
    input.setName("NucleiLifecyclePlatform");
    input.setSecurityPlatformType(SecurityPlatform.SECURITY_PLATFORM_TYPE.VULNERABILITY_SCANNER);
    input.setExternalReference(externalReference);
    input.setDescription("Registered by the injector at startup");
    String response =
        mvc.perform(
                post(SECURITY_PLATFORM_URI + "/upsert")
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    entityManager.flush();
    entityManager.clear();
    return JsonPath.read(response, "$.asset_id");
  }

  @Test
  @DisplayName("the registration upsert links the injector and locks the platform in the UI")
  void upsertKeyedOnTheInjectorTypeLinksTheInjector() throws Exception {
    String platformId = upsertPlatform(INJECTOR_TYPE);

    mvc.perform(
            get(SECURITY_PLATFORM_URI + "/" + platformId)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.security_platform_injectors[0]").value(injector.getId()))
        .andExpect(jsonPath("$.security_platform_collectors").isEmpty());
  }

  @Test
  @DisplayName("a re-run of the registration upsert keeps the existing link (idempotent)")
  void upsertIsIdempotentOnTheInjectorLink() throws Exception {
    String platformId = upsertPlatform(INJECTOR_TYPE);
    String platformIdAgain = upsertPlatform(INJECTOR_TYPE);

    assertEquals(platformId, platformIdAgain);
    mvc.perform(
            get(SECURITY_PLATFORM_URI + "/" + platformId)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.security_platform_injectors[0]").value(injector.getId()));
  }

  @Test
  @DisplayName("deleting the injector from the catalog releases the platform")
  void deletedInjectorReleasesThePlatform() throws Exception {
    String platformId = upsertPlatform(INJECTOR_TYPE);

    Injector linked =
        injectorRepository
            .findByTypeAndTenantId(INJECTOR_TYPE, TenantContext.getCurrentTenant())
            .orElseThrow();
    injectorRepository.delete(linked);
    entityManager.flush();
    entityManager.clear();

    mvc.perform(
            get(SECURITY_PLATFORM_URI + "/" + platformId)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.security_platform_injectors").isEmpty());
  }

  @Test
  @DisplayName("a collector-style upsert (external reference matches no injector) links nothing")
  void collectorStyleUpsertLinksNoInjector() throws Exception {
    String platformId = upsertPlatform("stable-collector-id-no-injector");

    mvc.perform(
            get(SECURITY_PLATFORM_URI + "/" + platformId)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.security_platform_injectors").isEmpty());
  }
}
