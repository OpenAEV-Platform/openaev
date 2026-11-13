package io.openaev.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.openaev.context.TxCtx;
import io.openaev.database.model.Command;
import io.openaev.database.model.Payload;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Catalogue payload installer")
class CatalogPayloadInstallerTest {

  private static final String TENANT_ID = "tenant";

  @Mock private CatalogPayloadService catalogPayloadService;
  @Mock private CatalogFileService catalogFileService;
  @InjectMocks private CatalogPayloadInstaller installer;

  @Test
  @DisplayName("Installing every new payload skips one that fails and installs the others")
  void given_onePayloadFailing_should_installTheOthers() {
    // Arrange
    TxCtx ctx = TxCtx.forTenant(TENANT_ID);
    InstallablePayloadVersion broken =
        new InstallablePayloadVersion("repo", "a", "1.0.0", "c1", "f1");
    InstallablePayloadVersion sound =
        new InstallablePayloadVersion("repo", "b", "1.0.0", "c2", "f2");
    Payload payload = new Command();
    when(catalogPayloadService.neverInstalled(ctx, TENANT_ID)).thenReturn(List.of(broken, sound));
    when(catalogFileService.storedFile("f1")).thenThrow(new IllegalStateException("changed"));
    when(catalogFileService.storedFile("f2")).thenReturn(new byte[0]);
    when(catalogPayloadService.install(eq(ctx), eq(TENANT_ID), eq(sound), any()))
        .thenReturn(payload);

    // Act
    InstalledPayloads result = installer.installAll(ctx, TENANT_ID);

    // Assert
    assertThat(result.installed()).containsExactly(payload);
    assertThat(result.failed()).containsExactly(broken);
  }
}
