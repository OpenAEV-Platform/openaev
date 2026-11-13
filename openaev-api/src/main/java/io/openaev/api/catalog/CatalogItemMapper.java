package io.openaev.api.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.api.catalog.dto.CatalogManifest;
import io.openaev.api.catalog.dto.payload.CatalogPayloadOutput;
import io.openaev.api.catalog.dto.scenario.CatalogScenarioOutput;
import io.openaev.database.model.catalog.CatalogItemStatus;
import io.openaev.database.model.catalog.CatalogPayload;
import io.openaev.database.model.catalog.CatalogScenario;
import io.openaev.rest.domain.DomainService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Copies a release manifest into catalogue items, and catalogue items into API outputs. */
@Component
@RequiredArgsConstructor
public class CatalogItemMapper {

  public static final String FILIGRAN = "Filigran";
  private static final String ORIGIN_FILIGRAN = "filigran";
  private static final String ORIGIN_COMMUNITY = "community";

  private final ObjectMapper mapper;

  public void fill(
      CatalogPayload target,
      CatalogManifest.PayloadVersion version,
      JsonNode file,
      CatalogManifest manifest) {
    target.setStatus(CatalogItemStatus.fromManifest(version.status()));
    target.setName(file.path("payload_name").asText(version.payload()));
    target.setDescription(file.path("payload_description").asText(null));
    List<String> platforms = new ArrayList<>();
    file.path("payload_platforms").forEach(platform -> platforms.add(platform.asText()));
    target.setPlatforms(platforms.toArray(new String[0]));
    target.setDomain(primaryDomain(file));
    target.setContentHash(version.sha256().toLowerCase(Locale.ROOT));
    target.setPublishedAt(manifest.issuedAt());
    target.setArchiveName(version.file().name());
  }

  // Same rule as the Threat Arsenal sections: the first domain by name.
  private static String primaryDomain(JsonNode file) {
    List<String> names = new ArrayList<>();
    file.path("payload_domains").forEach(domain -> names.add(domain.path("domain_name").asText()));
    return names.stream()
        .filter(name -> !name.isBlank() && !DomainService.TO_CLASSIFY.equals(name))
        .sorted()
        .findFirst()
        .orElse(null);
  }

  public void fill(CatalogScenario target, CatalogManifest.Item item, CatalogManifest manifest) {
    target.setStatus(CatalogItemStatus.fromManifest(item.status()));
    target.setName(item.name());
    target.setDescription(item.description());
    target.setAuthor(author(item));
    target.setSeverity(item.severity());
    target.setCategory(item.category());
    target.setMainFocus(item.mainFocus());
    target.setScenarioType(item.scenarioTypeOrType());
    target.setPlatforms(
        item.platforms() == null ? new String[0] : item.platforms().toArray(new String[0]));
    target.setInjectsCount(item.injectsCount());
    target.setChangelog(item.changelog());
    target.setPublishedAt(item.publishedAt() == null ? manifest.issuedAt() : item.publishedAt());
    target.setArchiveName(item.archive().name());
    try {
      target.setRequires(
          item.requires() == null ? null : mapper.writeValueAsString(item.requires()));
    } catch (Exception e) {
      throw new IllegalArgumentException("Unreadable requirements", e);
    }
    try {
      target.setPayloadRefs(
          mapper.writeValueAsString(item.payloadRefs() == null ? List.of() : item.payloadRefs()));
    } catch (Exception e) {
      throw new IllegalArgumentException("Unreadable payload references", e);
    }
  }

  // An item without a known origin has no author.
  private static String author(CatalogManifest.Item item) {
    if (ORIGIN_FILIGRAN.equals(item.origin())) {
      return FILIGRAN;
    }
    return ORIGIN_COMMUNITY.equals(item.origin()) ? item.contributor() : null;
  }

  // -- OUTPUT --

  public CatalogScenarioOutput toOutput(CatalogScenario latest) {
    return new CatalogScenarioOutput(latest.getExternalId(), latest.getVersion(), latest.getName());
  }

  public CatalogPayloadOutput toOutput(CatalogPayload latest) {
    return new CatalogPayloadOutput(latest.getExternalId(), latest.getVersion(), latest.getName());
  }
}
