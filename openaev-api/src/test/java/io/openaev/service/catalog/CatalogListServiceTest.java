package io.openaev.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.openaev.database.model.catalog.CatalogItemStatus;
import io.openaev.database.model.catalog.CatalogPayload;
import io.openaev.database.model.catalog.CatalogRelease;
import io.openaev.database.repository.CatalogItemRepository;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Catalogue list")
class CatalogListServiceTest {

  private static final String REPOSITORY = "filigran/openaev-payloads";

  @Mock private CatalogItemRepository catalogItemRepository;
  @InjectMocks private CatalogListService catalogListService;

  private static CatalogPayload item(
      String externalId, String name, String version, CatalogItemStatus status) {
    CatalogRelease release = new CatalogRelease();
    release.setRepository(REPOSITORY);
    CatalogPayload item = new CatalogPayload();
    item.setRelease(release);
    item.setExternalId(externalId);
    item.setName(name);
    item.setVersion(version);
    item.setStatus(status);
    return item;
  }

  @Nested
  @DisplayName("The latest offered versions")
  class LatestOffered {

    @Test
    @DisplayName("keep the highest offered version of each item, by name, but the excluded keys")
    void given_versionsAndExcludedKey_should_keepLatestOfferedOfOthers() {
      // Arrange
      CatalogPayload older = item("dump", "Dump", "1.2.0", CatalogItemStatus.VERIFIED);
      CatalogPayload latest = item("dump", "Dump", "1.10.0", CatalogItemStatus.VERIFIED);
      CatalogPayload revoked = item("dump", "Dump", "2.0.0", CatalogItemStatus.REVOKED);
      CatalogPayload deprecated = item("scan", "Scan", "1.0.0", CatalogItemStatus.DEPRECATED);
      CatalogPayload other = item("beacon", "Beacon", "1.0.0", CatalogItemStatus.VERIFIED);
      CatalogPayload installed = item("agent", "Agent", "1.0.0", CatalogItemStatus.VERIFIED);
      when(catalogItemRepository.findAllOfType(CatalogPayload.class))
          .thenReturn(List.of(older, latest, revoked, deprecated, other, installed));

      // Act
      List<CatalogPayload> offered =
          catalogListService.latestOfferedExcept(
              CatalogPayload.class, Set.of(CatalogOffer.key(REPOSITORY, "agent")));

      // Assert
      assertThat(offered).containsExactly(other, latest);
    }
  }

  @Nested
  @DisplayName("The offered versions of an item")
  class OfferedVersions {

    @Test
    @DisplayName("are sorted newest first, without the deprecated ones")
    void given_versions_should_listOfferedNewestFirst() {
      // Arrange
      CatalogPayload first = item("dump", "Dump", "1.9.0", CatalogItemStatus.VERIFIED);
      CatalogPayload second = item("dump", "Dump", "1.10.0", CatalogItemStatus.VERIFIED);
      CatalogPayload deprecated = item("dump", "Dump", "1.11.0", CatalogItemStatus.DEPRECATED);
      when(catalogItemRepository.findAllVersions(CatalogPayload.class, "dump"))
          .thenReturn(List.of(first, deprecated, second));

      // Act
      List<CatalogPayload> versions =
          catalogListService.offeredVersions(CatalogPayload.class, "dump");

      // Assert
      assertThat(versions).containsExactly(second, first);
    }

    @Test
    @DisplayName("refuse an unknown version")
    void given_unknownVersion_should_notFindIt() {
      // Arrange
      when(catalogItemRepository.findVersion(CatalogPayload.class, "dump", "9.0.0"))
          .thenReturn(Optional.empty());

      // Act & Assert
      assertThatThrownBy(() -> catalogListService.offered(CatalogPayload.class, "dump", "9.0.0"))
          .isInstanceOf(ElementNotFoundException.class);
    }

    @Test
    @DisplayName("refuse a version no longer offered")
    void given_deprecatedVersion_should_refuseIt() {
      // Arrange
      when(catalogItemRepository.findVersion(CatalogPayload.class, "dump", "1.0.0"))
          .thenReturn(Optional.of(item("dump", "Dump", "1.0.0", CatalogItemStatus.DEPRECATED)));

      // Act & Assert
      assertThatThrownBy(() -> catalogListService.offered(CatalogPayload.class, "dump", "1.0.0"))
          .isInstanceOf(BadRequestException.class);
    }
  }
}
