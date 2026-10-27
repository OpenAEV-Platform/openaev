package io.openaev.rest.payload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.integration.ManagerFactory;
import io.openaev.integration.impl.injectors.openaev.OpenaevInjectorIntegrationFactory;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.payload.form.PayloadCreateInput;
import io.openaev.rest.payload.service.PayloadCreationService;
import io.openaev.rest.payload.service.PayloadService;
import io.openaev.rest.payload.service.PayloadVariantService;
import io.openaev.rest.payload.service.PayloadVariantService.Block;
import io.openaev.rest.payload.service.PayloadVariantService.BlockChoice;
import io.openaev.rest.payload.service.PayloadVariantService.Choice;
import io.openaev.rest.payload.service.PayloadVariantService.VariantResult;
import io.openaev.service.payload.PayloadContentHashService;
import io.openaev.service.payload.PayloadExecutionSnapshotService;
import io.openaev.service.payload.PayloadRevocationService;
import io.openaev.service.payload.PayloadVersionService;
import io.openaev.utils.InjectUtils;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.PayloadInputFixture;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.helpers.PayloadVersionTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Customized catalogue payloads (ADR-011, point 3)")
class PayloadVariantTest extends IntegrationTest {

  @Autowired private PayloadVariantService payloadVariantService;
  @Autowired private PayloadVersionService payloadVersionService;
  @Autowired private PayloadRevocationService payloadRevocationService;
  @Autowired private PayloadCreationService payloadCreationService;
  @Autowired private PayloadService payloadService;
  @Autowired private PayloadContentHashService payloadContentHashService;
  @Autowired private InjectUtils injectUtils;
  @Autowired private PayloadVersionTestHelper payloadVersionTestHelper;
  @Autowired private InjectComposer injectComposer;
  @Autowired private OpenaevInjectorIntegrationFactory openaevInjectorIntegrationFactory;
  @Autowired private ManagerFactory managerFactory;

  @BeforeEach
  void registerPayloadInjector() throws Exception {
    openaevInjectorIntegrationFactory.registerConnectorForTenant(TenantContext.getCurrentTenant());
    managerFactory.getManager(TenantContext.getCurrentTenant()).monitorIntegrations();
  }

  /** A catalogue payload released as these versions, each with its command. */
  private List<Payload> catalogVersions(String... contents) {
    String externalId = "variant-test-" + UUID.randomUUID();
    String name = "Dump LSASS " + UUID.randomUUID();
    String[] versions = {"1.0.0", "1.1.0", "1.2.0"};
    List<Payload> released = new java.util.ArrayList<>();
    for (int i = 0; i < contents.length; i++) {
      PayloadCreateInput input =
          PayloadInputFixture.createDefaultPayloadCreateInputForCommandLine(List.of());
      input.setName(name);
      input.setContent(contents[i]);
      Payload created =
          payloadCreationService
              .createPayload(TxCtx.forTenant(TenantContext.getCurrentTenant()), input)
              .payload();
      released.add(payloadVersionTestHelper.asManagedVersion(created, externalId, versions[i]));
    }
    return released;
  }

  private Command latest(VariantResult result) {
    return (Command) org.hibernate.Hibernate.unproxy(result.payload());
  }

  @Test
  @DisplayName("Customizing starts at 1.0.0, based on the catalogue version")
  @WithMockUser(isAdmin = true)
  void given_catalogVersion_when_customized_should_createVariant() {
    // Arrange
    Payload base = catalogVersions("echo base").getFirst();

    // Act
    VariantResult result = payloadVariantService.customize(base.getId());

    // Assert
    assertThat(result.payload().getVersion()).isEqualTo(Payload.FIRST_VERSION);
    assertThat(result.payload().getOriginId()).isEqualTo(base.getId());
    assertThat(result.payload().getIdentity().isVariant()).isTrue();
    assertThat(result.payload().getIdentity().isManaged()).isFalse();
  }

  @Test
  @DisplayName("Changing the overrides adds a version and reports the usages left behind")
  @WithMockUser(isAdmin = true)
  void given_overrides_should_addVersionAndCountUsages() {
    // Arrange
    Payload base = catalogVersions("echo base").getFirst();
    VariantResult variant = payloadVariantService.customize(base.getId());
    String identityId = variant.payload().getIdentity().getId();
    Inject inject = InjectFixture.getDefaultInject();
    inject.setInjectorContract(variant.injectorContract());
    injectComposer.forInject(inject).persist();

    // Act
    VariantResult changed =
        payloadVariantService.updateOverrides(identityId, Map.of("command_content", "echo mine"));

    // Assert
    assertThat(changed.previous()).isNotNull();
    assertThat(changed.previousUsages()).isEqualTo(1);
    assertThat(latest(changed).getContent()).isEqualTo("echo mine");
    assertThat(changed.payload().getVersion()).isEqualTo("1.1.0");
  }

  @Test
  @DisplayName("A rebase merges alone when Filigran and the variant changed different blocks")
  @WithMockUser(isAdmin = true)
  void given_differentBlocks_should_rebaseWithoutConflict() {
    // Arrange
    List<Payload> released = catalogVersions("echo base", "echo fixed");
    VariantResult variant = payloadVariantService.customize(released.getFirst().getId());
    String identityId = variant.payload().getIdentity().getId();
    payloadVariantService.updateOverrides(
        identityId, Map.of("payload_platforms", List.of("Linux", "Windows")));

    // Act
    VariantResult rebased = payloadVariantService.rebase(identityId, null, Map.of());

    // Assert
    Command command = latest(rebased);
    assertThat(command.getContent()).isEqualTo("echo fixed");
    assertThat(command.getPlatforms()).contains(Endpoint.PLATFORM_TYPE.Windows);
    assertThat(command.getOriginId()).isEqualTo(released.get(1).getId());
  }

  @Nested
  @DisplayName("Both sides changed the execution block")
  class Conflict {

    private String identityId;

    @BeforeEach
    void setUp() {
      List<Payload> released = catalogVersions("echo base", "echo fixed");
      VariantResult variant = payloadVariantService.customize(released.getFirst().getId());
      identityId = variant.payload().getIdentity().getId();
      payloadVariantService.updateOverrides(identityId, Map.of("command_content", "echo mine"));
    }

    @Test
    @DisplayName("The preview shows the conflict and the rebase needs a choice")
    @WithMockUser(isAdmin = true)
    void given_noChoice_should_refuseRebase() {
      assertThat(payloadVariantService.previewRebase(identityId, null).hasConflict()).isTrue();
      assertThrows(
          BadRequestException.class,
          () -> payloadVariantService.rebase(identityId, null, Map.of()));
    }

    @Test
    @DisplayName("Keep mine")
    @WithMockUser(isAdmin = true)
    void given_keepMine_should_keepVariantCommand() {
      VariantResult rebased =
          payloadVariantService.rebase(
              identityId, null, Map.of(Block.EXECUTION, new BlockChoice(Choice.KEEP_MINE, null)));
      assertThat(latest(rebased).getContent()).isEqualTo("echo mine");
    }

    @Test
    @DisplayName("Take the catalogue's")
    @WithMockUser(isAdmin = true)
    void given_takeCatalog_should_dropOverride() {
      VariantResult rebased =
          payloadVariantService.rebase(
              identityId,
              null,
              Map.of(Block.EXECUTION, new BlockChoice(Choice.TAKE_CATALOG, null)));
      assertThat(latest(rebased).getContent()).isEqualTo("echo fixed");
      assertThat(rebased.payload().getIdentity().getOverrides())
          .doesNotContainKey("command_content");
    }

    @Test
    @DisplayName("Edit")
    @WithMockUser(isAdmin = true)
    void given_edit_should_useEditedCommand() {
      VariantResult rebased =
          payloadVariantService.rebase(
              identityId,
              null,
              Map.of(
                  Block.EXECUTION,
                  new BlockChoice(Choice.EDIT, Map.of("command_content", "echo merged"))));
      assertThat(latest(rebased).getContent()).isEqualTo("echo merged");
    }
  }

  @Test
  @DisplayName("A revoked base blocks its variants until an admin lifts the inheritance")
  @WithMockUser(isAdmin = true)
  void given_revokedBase_should_blockVariantUntilLift() {
    // Arrange
    Payload base = catalogVersions("echo base").getFirst();
    VariantResult variant = payloadVariantService.customize(base.getId());
    Payload copy = payloadService.duplicate(base.getId()).payload();
    payloadRevocationService.revoke(base.getId(), "Crashes domain controllers");

    // Act & Assert
    assertThat(payloadRevocationService.isRevoked(variant.payload())).isTrue();
    assertThat(payloadRevocationService.isRevoked(copy)).isFalse();
    payloadRevocationService.liftInherited(variant.payload().getId(), "Our command is safe");
    assertThat(payloadRevocationService.isRevoked(variant.payload())).isFalse();
  }

  @Test
  @DisplayName("A variant runs with the hash of its stored content")
  @WithMockUser(isAdmin = true)
  void given_variantExecution_should_recordCoherentHash() {
    // Arrange
    Payload base = catalogVersions("echo base").getFirst();
    VariantResult variant = payloadVariantService.customize(base.getId());
    Inject inject = InjectFixture.getDefaultInject();
    inject.setInjectorContract(variant.injectorContract());

    // Act
    StatusPayload snapshot =
        new PayloadExecutionSnapshotService(payloadContentHashService)
            .stamp(injectUtils.getStatusPayloadFromInject(inject), inject);

    // Assert
    assertThat(snapshot.getContentHash()).isEqualTo(variant.payload().getContentHash());
    assertThat(snapshot.getVersion()).isEqualTo(Payload.FIRST_VERSION);
  }
}
