package io.openaev.rest.exercise;

import static io.openaev.rest.exercise.ExerciseApi.COMMUNICATION_ATTACHMENT_URI;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Communication;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.CommunicationRepository;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.service.FileService;
import io.openaev.service.MinioService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * A communication attachment's key names no tenant of its own (#7961): the key written by the fixed
 * writer is self-describing (prefixed with the tenant it was written under), while a key written
 * before that fix is a legacy bare key, always physically stored under the default tenant
 * regardless of which tenant the communication actually belongs to. In both cases the download
 * endpoint must authorize against the communication's real owning tenant (read off its inject),
 * never against the key's literal prefix or its physical storage location.
 *
 * <p>{@code injects} must be v2-active for the test to exercise anything: the test classpath's
 * {@code application.properties} declares no {@code openaev.tenant.active-tables} of its own, so
 * without this the join the download endpoint resolves the owning tenant through is never scoped at
 * all.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=injects")
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("A communication attachment download is scoped to its simulation's own tenant")
class CommunicationAttachmentTenantTest extends IntegrationTest {

  private static final String TENANT_IDS_HEADER = "X-Tenant-Ids";
  private static final byte[] CONTENT = "attachment-bytes".getBytes(StandardCharsets.UTF_8);

  @Autowired private MockMvc mvc;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private CommunicationRepository communicationRepository;
  @Autowired private FileService fileService;
  @Autowired private MinioService minioService;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String owningTenant;
  private String otherTenant;
  private String exerciseId;
  private String selfDescribingKey;
  private String legacyKey;

  @BeforeEach
  void seedCommunicationWithBothKeyFormats() throws Exception {
    owningTenant = tenantHelper.createTenantWithCurrentUser("comm-attachment-owner").getId();
    otherTenant = tenantHelper.createTenantWithCurrentUser("comm-attachment-other").getId();

    Exercise exercise = ExerciseFixture.getExercise();
    exercise.setName("comm-attachment-" + UUID.randomUUID());
    exercise.setTenant(new Tenant(owningTenant));
    exerciseId = exerciseRepository.save(exercise).getId();

    Inject inject = InjectFixture.getDefaultInject();
    inject.setExercise(exercise);
    inject.setTenant(new Tenant(owningTenant));
    inject = injectRepository.save(inject);

    Communication communication = new Communication();
    communication.setIdentifier("msg-" + UUID.randomUUID());
    communication.setFrom("sender@example.com");
    communication.setTo("recipient@example.com");
    communication.setInject(inject);
    String communicationId = communicationRepository.save(communication).getId();

    String path = "/" + exerciseId + "/communications/" + communicationId;
    // Self-describing key, written the way the fixed writer now writes it: explicitly under the
    // simulation's own tenant, whatever the ambient scope is.
    selfDescribingKey =
        fileService.uploadStream(owningTenant, path, "new.txt", new ByteArrayInputStream(CONTENT));
    // Legacy bare key, written the way the pre-fix writer always wrote it: under the ambient
    // tenant (the default one, in a test with no thread-local set), whatever the simulation's own
    // tenant actually is.
    legacyKey = fileService.uploadStream(path, "legacy.txt", new ByteArrayInputStream(CONTENT));

    communication.setAttachments(new String[] {selfDescribingKey, legacyKey});
    communicationRepository.save(communication);
  }

  @AfterEach
  void cleanup() {
    // One delete of the tenant row cascades to the exercise, the inject and the communication
    // (every FK to tenants is ON DELETE CASCADE, and communications.communication_inject
    // references injects ON DELETE CASCADE in turn); see
    // TenantIsolationTestHelper#deleteCommittedTenants.
    minioService.deleteDirectoryForTenant(owningTenant, exerciseId);
    minioService.deleteDirectoryForTenant(Tenant.DEFAULT_TENANT_UUID, exerciseId);
    tenantHelper.deleteCommittedTenants(owningTenant, otherTenant);
  }

  @Nested
  @DisplayName("the self-describing key written after the fix")
  class SelfDescribingKey {

    @Test
    @DisplayName("given a caller scoped to the owning tenant when downloading then it succeeds")
    void given_callerOfOwningTenant_should_downloadAttachment() throws Exception {
      // -- Act & Assert --
      mvc.perform(
              get(COMMUNICATION_ATTACHMENT_URI)
                  .param("file", selfDescribingKey)
                  .header(TENANT_IDS_HEADER, owningTenant))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("given a caller scoped only to another tenant when downloading then it is refused")
    void given_callerOfOtherTenantOnly_should_refuseAttachment() throws Exception {
      // -- Act & Assert --
      mvc.perform(
              get(COMMUNICATION_ATTACHMENT_URI)
                  .param("file", selfDescribingKey)
                  .header(TENANT_IDS_HEADER, otherTenant))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  @DisplayName("the legacy bare key written before the fix")
  class LegacyKey {

    @Test
    @DisplayName(
        "given a caller scoped to the owning tenant when downloading then it still succeeds,"
            + " from the default tenant path it was actually written under")
    void given_callerOfOwningTenant_should_downloadAttachment() throws Exception {
      // -- Act & Assert --
      mvc.perform(
              get(COMMUNICATION_ATTACHMENT_URI)
                  .param("file", legacyKey)
                  .header(TENANT_IDS_HEADER, owningTenant))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName(
        "given a caller scoped only to another tenant when downloading then it is refused, even"
            + " though the object physically lives under the default tenant")
    void given_callerOfOtherTenantOnly_should_refuseAttachment() throws Exception {
      // -- Act & Assert --
      mvc.perform(
              get(COMMUNICATION_ATTACHMENT_URI)
                  .param("file", legacyKey)
                  .header(TENANT_IDS_HEADER, otherTenant))
          .andExpect(status().isNotFound());
    }
  }

  @Test
  @DisplayName("given a file naming no known attachment when downloading then it is refused")
  void given_unknownFile_should_refuse() throws Exception {
    // -- Act & Assert --
    mvc.perform(
            get(COMMUNICATION_ATTACHMENT_URI)
                .param("file", "/communications/does-not-exist/file.txt")
                .header(TENANT_IDS_HEADER, owningTenant))
        .andExpect(status().isNotFound());
  }
}
