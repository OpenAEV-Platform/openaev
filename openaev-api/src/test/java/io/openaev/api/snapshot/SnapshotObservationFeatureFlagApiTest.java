package io.openaev.api.snapshot;

import static io.openaev.api.snapshot.SnapshotObservationApi.TENANT_SNAPSHOT_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.api.snapshot.form.SnapshotSearchInput;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR39: with the {@code BULK_SNAPSHOT_EXPORT} preview feature left at its default (off), the bulk
 * snapshot endpoints must be indistinguishable from an unimplemented route (404), even for a user
 * holding the capability. Kept in its own class because the flag is read once per Spring context
 * via {@code @TestPropertySource} on {@link SnapshotObservationApiTest}.
 */
@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Snapshot observation API — feature flag off")
class SnapshotObservationFeatureFlagApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantIsolationTestHelper;

  @Test
  @WithMockUser(withCapabilities = {Capability.ACCESS_SNAPSHOT_OBSERVATION})
  @DisplayName("given_featureFlagOff_should_return404EvenWithCapability")
  void given_featureFlagOff_should_return404EvenWithCapability() throws Exception {
    // -- ARRANGE --
    Tenant tenant =
        tenantIsolationTestHelper.createTenantWithCapabilities(
            "snapshot-flag-off", Set.of(Capability.ACCESS_SNAPSHOT_OBSERVATION));
    String uri =
        TENANT_SNAPSHOT_URI.replace("{tenantId}", tenant.getId()) + "/attack-observations/search";
    SnapshotSearchInput input = new SnapshotSearchInput(null, null, null, null);

    // -- ACT & ASSERT --
    mvc.perform(
            post(uri)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());
  }
}
