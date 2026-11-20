package io.openaev.service.payload;

import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.model.hash.Hashed;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.rest.payload.service.PayloadService;
import io.openaev.service.AuthorService;
import io.openaev.service.payload.PayloadCustomizationMerge.Choice;
import io.openaev.service.payload.PayloadCustomizationMerge.Update;
import io.openaev.utils.StringUtils;
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

  private final InjectorContractService injectorContractService;
  private final PayloadService payloadService;
  private final AuthorService authorService;
  private final PayloadLocalVersioning payloadLocalVersioning;
  private final PayloadChangeKind payloadChangeKind;
  private final PayloadCustomizationMerge merge;

  /** The Filigran version a copy is built from, and the newer one it can take. */
  public record Customization(InjectorContract baseAction, Optional<Update> update) {}

  // -- READ --

  @Transactional(readOnly = true)
  public Customization customization(String tenantId, String actionId) {
    Payload current =
        customized(payloadOf(injectorContractService.injectorContractInTenant(actionId, tenantId)));
    return new Customization(
        actionOf(tenantId, current.getBase()),
        current.isLatest() ? merge.update(current) : Optional.empty());
  }

  // -- CREATE --

  public InjectorContract customize(String tenantId, String actionId) {
    InjectorContract action = injectorContractService.injectorContractInTenant(actionId, tenantId);
    Payload base = payloadOf(action);
    if (!base.isManaged()) {
      throw new BadRequestException(NOT_FILIGRAN);
    }
    if (base.isRevoked()) {
      throw new BadRequestException(REVOKED);
    }
    Payload copy = payloadService.generateDuplicatedPayload(base);
    copy.setName(StringUtils.customizedString(base.getName()));
    copy.setBase(base);
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
      Map<CustomizableField, Choice> choices) {
    InjectorContract action = injectorContractService.injectorContractInTenant(actionId, tenantId);
    Payload current = customized(payloadOf(action));
    if (!current.isLatest()) {
      throw new BadRequestException(NOT_LATEST);
    }
    Update update =
        merge
            .update(current)
            .filter(newer -> newer.target().getId().equals(targetPayloadId))
            .orElseThrow(() -> new BadRequestException(UPDATE_CHANGED));
    InjectorContract targetAction = actionOf(tenantId, update.target());
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
    if (action.getPayload() == null) {
      throw new ElementNotFoundException(PayloadVersionService.NO_PAYLOAD);
    }
    return (Payload) Hibernate.unproxy(action.getPayload());
  }

  private static Payload customized(Payload payload) {
    if (!payload.isCustomized()) {
      throw new BadRequestException(NOT_CUSTOMIZED);
    }
    return payload;
  }

  private InjectorContract actionOf(String tenantId, Payload payload) {
    return Optional.ofNullable(
            injectorContractService
                .injectorContractsOfPayloads(tenantId, List.of(payload.getId()))
                .get(payload.getId()))
        .orElseThrow(ElementNotFoundException::new);
  }
}
