package io.openaev.service.payload;

import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.model.catalog.CatalogPayload;
import io.openaev.database.model.hash.Hashed;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.payload.service.PayloadService;
import io.openaev.service.AuthorService;
import io.openaev.service.catalog.CatalogListService;
import io.openaev.service.catalog.merge.MergeChoice;
import io.openaev.service.payload.PayloadCustomizationMerge.Update;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customized copies of Filigran payloads: a local payload that stays linked to the Filigran version
 * it is built from, and takes the newer ones when the user reviews them.
 */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class PayloadCustomizationService {

  public static final String NOT_FILIGRAN = "Only a Filigran payload can be customized";
  public static final String REVOKED = "A revoked version cannot be customized";
  public static final String NOT_CUSTOMIZED = "This action is not a customized copy";
  public static final String NOT_LATEST = "Only the latest version of a copy takes updates";
  public static final String UPDATE_CHANGED =
      "This Filigran version is no longer the update to take: review the update again";

  private final PayloadVersionService payloadVersionService;
  private final PayloadService payloadService;
  private final AuthorService authorService;
  private final PayloadLocalVersioning payloadLocalVersioning;
  private final PayloadChangeKind payloadChangeKind;
  private final PayloadCustomizationMerge merge;
  private final CatalogListService catalogListService;

  /**
   * The Filigran version a copy is built from, the newer one it can take and the release notes of
   * the versions in between, newest first.
   */
  public record Customization(
      InjectorContract baseAction, Optional<Update> update, List<CatalogPayload> releaseNotes) {}

  // -- READ --

  @Transactional(readOnly = true)
  public Customization customization(String tenantId, String actionId) {
    Payload current =
        customized(payloadOf(payloadVersionService.payloadAction(tenantId, actionId)));
    Payload base = current.getBase();
    Optional<Update> update = current.isLatest() ? merge.update(current) : Optional.empty();
    return new Customization(
        payloadVersionService.versionAction(tenantId, base.getId()),
        update,
        update
            .map(
                newer ->
                    catalogListService.releaseNotes(
                        CatalogPayload.class,
                        base.getExternalId(),
                        base.getVersion(),
                        newer.target().getVersion()))
            .orElse(List.of()));
  }

  // -- CREATE --

  public InjectorContract customize(String tenantId, String actionId) {
    InjectorContract action = payloadVersionService.payloadAction(tenantId, actionId);
    Payload base = payloadOf(action);
    if (!base.isManaged()) {
      throw new BadRequestException(NOT_FILIGRAN);
    }
    if (base.isRevoked()) {
      throw new BadRequestException(REVOKED);
    }
    Payload copy = merge.customizedCopy(base);
    authorService.authorByCurrentUser(copy);
    return payloadService
        .saveWithContract(
            copy,
            new ArrayList<>(action.getAttackPatterns()),
            new HashSet<>(action.getDomains()),
            new HashSet<>(action.getTags()))
        .injectorContract();
  }

  // -- UPDATE --

  /** A new version of the copy on the newer Filigran version, with a choice per conflict. */
  public InjectorContract update(
      String tenantId,
      String actionId,
      String targetPayloadId,
      Map<CustomizableBlock, MergeChoice> choices) {
    InjectorContract action = payloadVersionService.payloadAction(tenantId, actionId);
    Payload current = customized(payloadOf(action));
    if (!current.isLatest()) {
      throw new BadRequestException(NOT_LATEST);
    }
    Update update =
        merge
            .update(current)
            .filter(newer -> newer.target().getId().equals(targetPayloadId))
            .orElseThrow(() -> new BadRequestException(UPDATE_CHANGED));
    InjectorContract targetAction =
        payloadVersionService.versionAction(tenantId, update.target().getId());
    List<AttackPattern> attackPatterns = new ArrayList<>(targetAction.getAttackPatterns());
    Payload merged = merge.merged(update, choices == null ? Map.of() : choices);
    Hashed.Change kind =
        payloadChangeKind.of(current, merged, attackPatterns).kind().orElse(Hashed.Change.PATCH);
    return payloadService
        .saveWithContract(
            payloadLocalVersioning.addVersion(current, merged, kind),
            attackPatterns,
            new HashSet<>(targetAction.getDomains()),
            new HashSet<>(action.getTags()))
        .injectorContract();
  }

  private static Payload payloadOf(InjectorContract action) {
    return (Payload) Hibernate.unproxy(action.getPayload());
  }

  private static Payload customized(Payload payload) {
    if (!payload.isCustomized()) {
      throw new BadRequestException(NOT_CUSTOMIZED);
    }
    return payload;
  }
}
