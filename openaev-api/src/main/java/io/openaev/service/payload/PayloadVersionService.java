package io.openaev.service.payload;

import io.openaev.database.model.ContentRevocation;
import io.openaev.database.model.ContentType;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.model.PayloadIdentity;
import io.openaev.database.repository.ContentRevocationRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.PayloadIdentityRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.database.repository.StepRepository;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.exception.ManagedPayloadReadOnlyException;
import io.openaev.rest.exception.PayloadInUseException;
import io.openaev.utils.SemanticVersion;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payload versions (ADR-011). Every version is created here, under a lock on its identity, and this
 * is the only writer of the identity's latest pointer.
 */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class PayloadVersionService {

  private final PayloadRepository payloadRepository;
  private final PayloadIdentityRepository payloadIdentityRepository;
  private final ContentRevocationRepository contentRevocationRepository;
  private final InjectRepository injectRepository;
  private final StepRepository stepRepository;
  private final EntityManager entityManager;

  public static boolean isManaged(Payload payload) {
    return payload.isManaged();
  }

  // -- LIST --

  @Transactional(readOnly = true)
  public Payload findById(String id) {
    return payloadRepository.findById(id).orElseThrow(ElementNotFoundException::new);
  }

  @Transactional(readOnly = true)
  public List<Payload> findAllById(Collection<String> ids) {
    List<Payload> payloads = new ArrayList<>();
    payloadRepository.findAllById(ids).forEach(payloads::add);
    return payloads;
  }

  @Transactional(readOnly = true)
  public List<Payload> findVersions(PayloadIdentity identity) {
    return versionsOf(identity);
  }

  private List<Payload> versionsOf(PayloadIdentity identity) {
    return payloadRepository.findAllByIdentityId(identity.getId());
  }

  @Transactional(readOnly = true)
  public Optional<PayloadIdentity> findManagedIdentity(String repository, String externalId) {
    return payloadIdentityRepository.findManaged(repository, externalId);
  }

  /** Managed identities of an external ID; several only when repositories publish the same ID. */
  @Transactional(readOnly = true)
  public List<PayloadIdentity> findManagedIdentities(String externalId) {
    return payloadIdentityRepository.findAllManagedByExternalId(externalId);
  }

  @Transactional(readOnly = true)
  public Optional<PayloadIdentity> findIdentity(String identityId) {
    return payloadIdentityRepository.findById(identityId);
  }

  /** Variants whose versions are based on a version of this catalogue payload. */
  @Transactional(readOnly = true)
  public List<PayloadIdentity> findVariantsOf(PayloadIdentity catalogIdentity) {
    return payloadRepository.findVariantIdentitiesOf(catalogIdentity.getId());
  }

  /** The local identity the legacy collector upsert writes to. */
  @Transactional(readOnly = true)
  public Optional<PayloadIdentity> findCollectorIdentity(
      String collectorTypeId, String externalId) {
    return payloadIdentityRepository.findCollectorIdentity(collectorTypeId, externalId);
  }

  @Transactional(readOnly = true)
  public List<PayloadIdentity> findLocalIdentitiesClaiming(String externalId) {
    return payloadIdentityRepository.findAllLocalByExternalId(externalId);
  }

  /** Versions of the local payloads that claim this external ID. */
  @Transactional(readOnly = true)
  public List<Payload> findLocalVersionsClaiming(String externalId) {
    return payloadIdentityRepository.findAllLocalByExternalId(externalId).stream()
        .flatMap(identity -> versionsOf(identity).stream())
        .toList();
  }

  /** Default version of the catalogue payload with this external ID, when one repository has it. */
  @Transactional(readOnly = true)
  public Optional<Payload> findLatest(String externalId) {
    List<PayloadIdentity> identities =
        payloadIdentityRepository.findAllManagedByExternalId(externalId);
    if (identities.size() != 1 || identities.getFirst().getLatestPayloadId() == null) {
      return Optional.empty();
    }
    return payloadRepository.findById(identities.getFirst().getLatestPayloadId());
  }

  /**
   * Payloads an imported one can link to (F4), best first: a managed version only when its external
   * ID matches too and no other managed version does (a zip does not name its repository), then
   * local copies, whatever their external ID.
   */
  @Transactional(readOnly = true)
  public List<Payload> findLinkCandidates(String externalId, String hash, String tenantId) {
    List<Payload> sameContent = payloadRepository.findAllByContentHash(hash, tenantId);
    List<Payload> managed =
        sameContent.stream()
            .filter(p -> isManaged(p) && Objects.equals(p.getExternalId(), externalId))
            .toList();
    List<Payload> candidates = new ArrayList<>();
    if (managed.size() == 1) {
      candidates.add(managed.getFirst());
    }
    sameContent.stream().filter(p -> !isManaged(p)).forEach(candidates::add);
    return candidates;
  }

  /**
   * Ids of the revoked payloads among these: their repository's revocation list names their
   * version, or their tenant revoked them (F34).
   */
  @Transactional(readOnly = true)
  public Set<String> revokedIds(Collection<Payload> payloads) {
    return revokedAmong(payloads);
  }

  private Set<String> revokedAmong(Collection<Payload> payloads) {
    if (payloads.isEmpty()) {
      return Set.of();
    }
    Set<String> revoked = new HashSet<>(directlyRevoked(payloads));
    // A variant version inherits its base's revocation, unless a lift frees it from it.
    Map<String, String> baseOf =
        payloads.stream()
            .filter(p -> p.getIdentity().isVariant() && p.getOriginId() != null)
            .filter(p -> !revoked.contains(p.getId()))
            .collect(Collectors.toMap(Payload::getId, Payload::getOriginId));
    if (baseOf.isEmpty()) {
      return revoked;
    }
    List<Payload> bases = new ArrayList<>();
    payloadRepository.findAllById(new HashSet<>(baseOf.values())).forEach(bases::add);
    Set<String> revokedBases = directlyRevoked(bases);
    Map<String, String> baseRevocation = new HashMap<>();
    bases.stream()
        .filter(base -> revokedBases.contains(base.getId()))
        .forEach(
            base ->
                activeRevocationOf(base)
                    .ifPresent(r -> baseRevocation.put(base.getId(), r.getId())));
    Set<String> lifted =
        contentRevocationRepository.findActiveLifts(baseOf.keySet()).stream()
            .filter(
                l ->
                    l.getLiftsRevocationId()
                        .equals(baseRevocation.get(baseOf.get(l.getPayload().getId()))))
            .map(l -> l.getPayload().getId())
            .collect(Collectors.toSet());
    baseOf.forEach(
        (variantId, baseId) -> {
          if (revokedBases.contains(baseId) && !lifted.contains(variantId)) {
            revoked.add(variantId);
          }
        });
    return revoked;
  }

  private Set<String> directlyRevoked(Collection<Payload> payloads) {
    Set<String> revoked = new HashSet<>(tenantRevokedIds(payloads));
    Set<String> externalIds =
        payloads.stream()
            .filter(PayloadVersionService::isManaged)
            .map(Payload::getExternalId)
            .collect(Collectors.toSet());
    if (!externalIds.isEmpty()) {
      Set<String> listed =
          contentRevocationRepository
              .findAllByTypeAndExternalIdInAndTenantIsNullAndLiftedAtIsNull(
                  ContentType.PAYLOAD, externalIds)
              .stream()
              .map(r -> listKey(r.getRepository(), r.getExternalId(), r.getVersion()))
              .collect(Collectors.toSet());
      payloads.stream()
          .filter(p -> isManaged(p) && listed.contains(listKey(p)))
          .forEach(p -> revoked.add(p.getId()));
    }
    return revoked;
  }

  /** The base revocation a variant version inherits: its tenant's first, else the list's. */
  @Transactional(readOnly = true)
  public Optional<ContentRevocation> findActiveRevocation(Payload payload) {
    return activeRevocationOf(payload);
  }

  private Optional<ContentRevocation> activeRevocationOf(Payload payload) {
    Optional<ContentRevocation> tenant =
        contentRevocationRepository.findActiveByPayload(
            payload.getId(), payload.getTenant().getId());
    return tenant.isPresent() ? tenant : listRevocationOf(payload);
  }

  /** The revocation list entry naming this managed version, if any. */
  @Transactional(readOnly = true)
  public Optional<ContentRevocation> findListRevocation(Payload payload) {
    return listRevocationOf(payload);
  }

  private Optional<ContentRevocation> listRevocationOf(Payload payload) {
    if (!isManaged(payload)) {
      return Optional.empty();
    }
    return contentRevocationRepository
        .findAllByTypeAndExternalIdAndTenantIsNullAndLiftedAtIsNull(
            ContentType.PAYLOAD, payload.getExternalId())
        .stream()
        .filter(
            r ->
                listKey(r.getRepository(), r.getExternalId(), r.getVersion())
                    .equals(listKey(payload)))
        .findFirst();
  }

  /** Scenario injects, atomic tests and scenario chained steps running this contract's version. */
  @Transactional(readOnly = true)
  public int countTemplateUsages(InjectorContract contract) {
    int injects =
        injectRepository.findTemplateInjectsByPayloadId(contract.getPayload().getId()).size();
    long steps =
        stepRepository
            .findTemplateStepsByInjectorContractIds(
                List.of(contract.getId()), contract.getTenant().getId())
            .stream()
            .filter(s -> s.getWorkflow() != null && s.getWorkflow().getScenario() != null)
            .count();
    return injects + (int) steps;
  }

  /** Per identity, how many scenario and atomic testing injects still run an older version. */
  @Transactional(readOnly = true)
  public Map<String, Long> countTemplateInjectsOnOlderVersions(Collection<String> identityIds) {
    if (identityIds.isEmpty()) {
      return Map.of();
    }
    Map<String, Long> counts = new HashMap<>();
    injectRepository
        .countTemplateInjectsOnOlderVersions(identityIds)
        .forEach(row -> counts.put((String) row[0], (Long) row[1]));
    return counts;
  }

  /** Per identity, its installed versions. */
  @Transactional(readOnly = true)
  public Map<String, Long> countInstalledVersions(Collection<String> identityIds) {
    if (identityIds.isEmpty()) {
      return Map.of();
    }
    Map<String, Long> counts = new HashMap<>();
    payloadRepository
        .countInstalledVersions(identityIds)
        .forEach(row -> counts.put((String) row[0], (Long) row[1]));
    return counts;
  }

  /** Installed managed versions per external ID, for the Threat Arsenal Available tab (F5). */
  @Transactional(readOnly = true)
  public Map<String, Set<String>> findInstalledManagedVersions() {
    Map<String, Set<String>> versions = new HashMap<>();
    payloadRepository
        .findInstalledManagedVersions()
        .forEach(
            row ->
                versions
                    .computeIfAbsent((String) row[0], k -> new HashSet<>())
                    .add((String) row[1]));
    return versions;
  }

  @Transactional(readOnly = true)
  public Set<String> findUninstalledExternalIds() {
    return new HashSet<>(payloadIdentityRepository.findUninstalledExternalIds());
  }

  /**
   * Local payloads with this content that claim this external ID, as {@link Promotion}: a single
   * copy, alone in its identity, becomes the release's version; otherwise the copies stay local and
   * their injects move to the release (RFC 0005 §4.2, §4.5).
   */
  @Transactional(readOnly = true)
  public Promotion findPromotion(String externalId, String hash, String tenantId) {
    List<Payload> copies =
        payloadRepository.findAllByContentHash(hash, tenantId).stream()
            .filter(p -> !isManaged(p) && Objects.equals(p.getExternalId(), externalId))
            .toList();
    if (copies.size() == 1 && versionsOf(copies.getFirst().getIdentity()).size() == 1) {
      return new Promotion(Optional.of(copies.getFirst()), List.of());
    }
    return new Promotion(Optional.empty(), copies);
  }

  public record Promotion(Optional<Payload> promotable, List<Payload> copiesToRepoint) {

    public boolean found() {
      return promotable.isPresent() || !copiesToRepoint.isEmpty();
    }
  }

  // -- CREATE --

  /**
   * Finds or creates the managed identity of a catalogue payload; concurrent installs end on the
   * same row.
   */
  public PayloadIdentity findOrCreateManagedIdentity(
      String tenantId, String repository, String externalId) {
    if (repository == null || externalId == null) {
      throw new BadRequestException("A catalogue payload needs a repository and an external ID");
    }
    payloadIdentityRepository.insertManagedIfAbsent(
        tenantId, repository, externalId, Payload.PAYLOAD_SOURCE.FILIGRAN.name());
    return payloadIdentityRepository
        .findManaged(repository, externalId)
        .orElseThrow(() -> new IllegalStateException("Managed identity not found after insert"));
  }

  /**
   * Adds a version to an identity: locks it, numbers the version from the highest one, inserts the
   * row and moves the latest pointer, in this transaction. A version row never changes afterwards.
   *
   * @param numbering next version number, from the identity's highest version (null if none)
   */
  public Payload addVersion(PayloadIdentity identity, Payload version, Numbering numbering) {
    PayloadIdentity locked = lock(identity);
    List<Payload> versions = versionsOf(locked);
    Payload top = highestOf(versions).orElse(null);
    String number = numbering.next(top);
    if (versions.stream().anyMatch(v -> number.equals(v.getVersion()))) {
      throw new BadRequestException("Version " + number + " already exists");
    }
    version.setIdentity(locked);
    version.setTenant(locked.getTenant());
    version.setVersion(number);
    // No explicit flush: the next query flushes, and its errors are translated like any save.
    Payload saved = payloadRepository.save(version);
    refreshLatest(locked);
    return saved;
  }

  @FunctionalInterface
  public interface Numbering {
    String next(Payload highest);

    static Numbering fixed(String version) {
      return highest -> version;
    }
  }

  /**
   * Moves a row into another identity under a new number: the promotion of a local copy, or the
   * adoption by a catalogue install of the copy it just imported. Its content does not change. The
   * source identity is deleted once empty.
   */
  public Payload moveToIdentity(Payload payload, PayloadIdentity target, String version) {
    PayloadIdentity source = payload.getIdentity();
    // Two identities: always lock in the same order.
    List<PayloadIdentity> ordered =
        new ArrayList<>(List.of(source, target))
            .stream().sorted(Comparator.comparing(PayloadIdentity::getId)).map(this::lock).toList();
    PayloadIdentity lockedTarget =
        ordered.stream().filter(i -> i.getId().equals(target.getId())).findFirst().orElseThrow();
    PayloadIdentity lockedSource =
        ordered.stream().filter(i -> i.getId().equals(source.getId())).findFirst().orElseThrow();
    if (versionsOf(lockedTarget).stream().anyMatch(v -> version.equals(v.getVersion()))) {
      throw new BadRequestException("Version " + version + " already exists");
    }
    payload.setIdentity(lockedTarget);
    payload.setVersion(version);
    if (lockedTarget.isManaged()) {
      payload.setStatus(Payload.PAYLOAD_STATUS.VERIFIED);
    }
    payloadRepository.save(payload);
    refreshLatest(lockedTarget);
    if (versionsOf(lockedSource).isEmpty()) {
      payloadIdentityRepository.delete(lockedSource);
    } else {
      refreshLatest(lockedSource);
    }
    return payload;
  }

  // -- UPDATE --

  /** Hides every version of a payload and keeps auto-install away from it (F26). */
  public void setUninstalled(PayloadIdentity identity, boolean uninstalled) {
    PayloadIdentity locked = lock(identity);
    locked.setUninstalled(uninstalled);
    payloadIdentityRepository.save(locked);
  }

  /**
   * Points the identity to its highest non-revoked version, or to none. To call after every new
   * version, revocation or lift.
   */
  public Optional<Payload> refreshLatest(PayloadIdentity identity) {
    List<Payload> versions = versionsOf(identity);
    Set<String> revoked = revokedAmong(versions);
    Optional<Payload> latest =
        highestOf(versions.stream().filter(v -> !revoked.contains(v.getId())).toList());
    String latestId = latest.map(Payload::getId).orElse(null);
    if (!Objects.equals(identity.getLatestPayloadId(), latestId)) {
      identity.setLatestPayloadId(latestId);
      payloadIdentityRepository.save(identity);
    }
    return latest;
  }

  public void refreshLatest(Payload payload) {
    refreshLatest(payload.getIdentity());
  }

  /** Status shown and filtered on; the execution check reads the revocations, not this status. */
  public Payload setStatus(Payload payload, Payload.PAYLOAD_STATUS status) {
    payload.setStatus(status);
    return payloadRepository.save(payload);
  }

  // -- DELETE --

  /** F26: managed versions are never deleted; a local copy is not deleted while injects run it. */
  @Transactional(readOnly = true)
  public void assertDeletable(Payload payload) {
    checkDeletable(payload);
  }

  @Transactional(readOnly = true)
  public void assertContractDeletable(String contractId, String tenantId) {
    payloadRepository
        .findByInjectorContractId(contractId, tenantId)
        .ifPresent(this::checkDeletable);
  }

  private void checkDeletable(Payload payload) {
    if (isManaged(payload)) {
      throw new ManagedPayloadReadOnlyException(
          "Managed payload versions cannot be deleted: payload " + payload.getId());
    }
    List<String> usages =
        payloadRepository.findUsageNamesByPayloadId(payload.getId()).stream()
            .map(name -> name != null ? name : "atomic testing")
            .toList();
    if (!usages.isEmpty()) {
      throw new PayloadInUseException(usages);
    }
  }

  /** Contracts among these whose payload {@link #assertDeletable} would refuse, in one query. */
  @Transactional(readOnly = true)
  public Set<String> findNotDeletableContractIds(Collection<String> contractIds) {
    return contractIds.isEmpty()
        ? Set.of()
        : payloadRepository.findNotDeletableContractIds(contractIds);
  }

  /** After deleting a local version: the identity points elsewhere, or goes when empty. */
  public void afterVersionDeleted(PayloadIdentity identity) {
    PayloadIdentity locked = lock(identity);
    if (versionsOf(locked).isEmpty()) {
      payloadIdentityRepository.delete(locked);
    } else {
      refreshLatest(locked);
    }
  }

  /**
   * Locks an identity before a version is written into it by another path (catalogue import), and
   * checks the number is free; call {@link #refreshLatest(PayloadIdentity)} once it is saved.
   */
  public PayloadIdentity lockForVersion(PayloadIdentity identity, String version) {
    PayloadIdentity locked = lock(identity);
    if (versionsOf(locked).stream().anyMatch(v -> version.equals(v.getVersion()))) {
      throw new BadRequestException("Version " + version + " already exists");
    }
    return locked;
  }

  // -- HELPERS --

  private PayloadIdentity lock(PayloadIdentity identity) {
    if (identity.getId() == null) {
      return payloadIdentityRepository.save(identity);
    }
    PayloadIdentity managed =
        entityManager.contains(identity)
            ? identity
            : entityManager.find(PayloadIdentity.class, identity.getId());
    entityManager.lock(managed, LockModeType.PESSIMISTIC_WRITE);
    return managed;
  }

  private static Optional<Payload> highestOf(Collection<Payload> versions) {
    return versions.stream().max(Comparator.comparing(Payload::getVersion, SemanticVersion.ORDER));
  }

  private static String listKey(Payload payload) {
    return listKey(
        payload.getIdentity().getRepository(), payload.getExternalId(), payload.getVersion());
  }

  private static String listKey(String repository, String externalId, String version) {
    return repository + "|" + externalId + "@" + version;
  }

  private Set<String> tenantRevokedIds(Collection<Payload> payloads) {
    Map<String, List<String>> idsByTenant =
        payloads.stream()
            .collect(
                Collectors.groupingBy(
                    p -> p.getTenant().getId(),
                    Collectors.mapping(Payload::getId, Collectors.toList())));
    Set<String> revoked = new HashSet<>();
    idsByTenant.forEach(
        (tenantId, ids) ->
            revoked.addAll(contentRevocationRepository.findActivePayloadIds(ids, tenantId)));
    return revoked;
  }
}
