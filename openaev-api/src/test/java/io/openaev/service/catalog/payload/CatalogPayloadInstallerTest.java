package io.openaev.service.catalog.payload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.openaev.context.TxCtx;
import io.openaev.database.model.Command;
import io.openaev.database.model.Payload;
import io.openaev.database.model.catalog.CatalogHistory;
import io.openaev.service.catalog.CatalogFileService;
import io.openaev.service.catalog.Installed;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Catalogue payload installer")
class CatalogPayloadInstallerTest {

  private static final String TENANT_ID = "tenant";
  private static final TxCtx CTX = TxCtx.forTenant(TENANT_ID);

  @Mock private CatalogPayloadOfferService catalogPayloadOfferService;
  @Mock private CatalogPayloadService catalogPayloadService;
  @Mock private CatalogFileService catalogFileService;
  @InjectMocks private CatalogPayloadInstaller installer;

  /** One way of installing several payloads: what it installs, and how it runs. */
  interface Installs {
    void listing(CatalogPayloadOfferService offer, List<InstallablePayloadVersion> versions);

    Installed<InstallablePayloadVersion, Payload> run(CatalogPayloadInstaller installer);
  }

  static Stream<Arguments> installs() {
    return Stream.of(
        Arguments.of(
            "every payload never installed",
            CatalogHistory.Mode.MANUAL,
            new Installs() {
              public void listing(CatalogPayloadOfferService s, List<InstallablePayloadVersion> v) {
                when(s.neverInstalled(CTX, TENANT_ID)).thenReturn(v);
              }

              public Installed<InstallablePayloadVersion, Payload> run(CatalogPayloadInstaller i) {
                return i.installAll(CTX, TENANT_ID);
              }
            }),
        Arguments.of(
            "what a release brings",
            CatalogHistory.Mode.AUTO,
            new Installs() {
              public void listing(CatalogPayloadOfferService s, List<InstallablePayloadVersion> v) {
                when(s.toAutoInstall(CTX, TENANT_ID)).thenReturn(v);
              }

              public Installed<InstallablePayloadVersion, Payload> run(CatalogPayloadInstaller i) {
                return i.autoInstall(CTX, TENANT_ID);
              }
            }));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("installs")
  @DisplayName("Installing skips a payload that fails and installs the others")
  void given_onePayloadFailing_should_installTheOthers(
      String description, CatalogHistory.Mode mode, Installs installs) {
    // Arrange
    InstallablePayloadVersion broken =
        new InstallablePayloadVersion("repo", "a", "1.0.0", "c1", "f1");
    InstallablePayloadVersion sound =
        new InstallablePayloadVersion("repo", "b", "1.0.0", "c2", "f2");
    Payload payload = new Command();
    installs.listing(catalogPayloadOfferService, List.of(broken, sound));
    when(catalogFileService.storedFile("f1")).thenThrow(new IllegalStateException("changed"));
    when(catalogFileService.storedFile("f2")).thenReturn(new byte[0]);
    when(catalogPayloadService.install(eq(CTX), eq(TENANT_ID), eq(sound), any(), eq(mode)))
        .thenReturn(payload);

    // Act
    Installed<InstallablePayloadVersion, Payload> result = installs.run(installer);

    // Assert
    assertThat(result.installed()).containsExactly(payload);
    assertThat(result.failed()).containsExactly(broken);
  }
}
