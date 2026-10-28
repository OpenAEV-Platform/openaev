package io.openaev.service.catalog;

import static java.time.Instant.now;

import io.openaev.api.catalog.RevocationList;
import io.openaev.api.catalog.RevocationListOutput;
import io.openaev.database.model.ContentRevocation;
import io.openaev.database.model.ContentType;
import io.openaev.database.model.RevocationListState;
import io.openaev.database.repository.ContentRevocationRepository;
import io.openaev.database.repository.RevocationListStateRepository;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.service.payload.PayloadVersionService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies the revocation list of a content repository. A smaller serial than the last one received
 * is refused, the same serial is a no-op. An expired list blocks nothing until the lists are
 * signed.
 */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class RevocationListService {

  private final RevocationListStateRepository revocationListStateRepository;
  private final ContentRevocationRepository contentRevocationRepository;
  private final PayloadVersionService payloadVersionService;

  @Transactional(readOnly = true)
  public List<RevocationListOutput> findAll() {
    return revocationListStateRepository.findAll().stream()
        .map(state -> output(state, true, 0, 0))
        .toList();
  }

  public RevocationListOutput apply(RevocationList list) {
    if (list.repository() == null || list.repository().isBlank() || list.serial() == null) {
      throw new BadRequestException("The revocation list needs a repository and a serial");
    }
    Optional<RevocationListState> last = revocationListStateRepository.findById(list.repository());
    if (last.isPresent() && list.serial() < last.get().getSerial()) {
      throw new BadRequestException(
          "Revocation list serial %d is older than the last one received (%d)"
              .formatted(list.serial(), last.get().getSerial()));
    }
    if (last.isPresent() && list.serial() == last.get().getSerial()) {
      return output(last.get(), false, 0, 0);
    }

    Map<String, RevocationList.Entry> listed = new HashMap<>();
    for (RevocationList.Entry entry :
        list.revocations() == null ? List.<RevocationList.Entry>of() : list.revocations()) {
      if (entry.externalId() == null || entry.version() == null) {
        throw new BadRequestException("A revocation needs an external ID and a version");
      }
      listed.put(key(type(entry.type()), entry.externalId(), entry.version()), entry);
    }

    Set<String> touched = new LinkedHashSet<>();
    int lifted = 0;
    for (ContentRevocation active :
        contentRevocationRepository.findAllByRepositoryAndTenantIsNullAndLiftedAtIsNull(
            list.repository())) {
      if (listed.remove(key(active.getType(), active.getExternalId(), active.getVersion()))
          == null) {
        active.setLiftedAt(now());
        contentRevocationRepository.save(active);
        lifted++;
        touch(touched, active.getType(), active.getExternalId());
      }
    }
    List<ContentRevocation> added = new ArrayList<>();
    for (RevocationList.Entry entry : listed.values()) {
      ContentRevocation revocation = new ContentRevocation();
      revocation.setType(type(entry.type()));
      revocation.setRepository(list.repository());
      revocation.setExternalId(entry.externalId());
      revocation.setVersion(entry.version());
      revocation.setReason(entry.reason());
      revocation.setListSerial(list.serial());
      revocation.setPublishedAt(list.issuedAt());
      added.add(revocation);
      touch(touched, revocation.getType(), revocation.getExternalId());
    }
    contentRevocationRepository.saveAll(added);

    RevocationListState state = last.orElseGet(RevocationListState::new);
    state.setRepository(list.repository());
    state.setSerial(list.serial());
    state.setIssuedAt(list.issuedAt());
    state.setExpiresAt(list.expiresAt());
    state.setDownloadedAt(now());
    state = revocationListStateRepository.save(state);

    touched.forEach(externalId -> refreshLatest(list.repository(), externalId));
    return output(state, true, added.size(), lifted);
  }

  // Current tenant only: the other tenants move their latest version on their next change.
  private void refreshLatest(String repository, String externalId) {
    payloadVersionService
        .findManagedIdentity(repository, externalId)
        .ifPresent(
            identity -> {
              payloadVersionService.refreshLatest(identity);
              payloadVersionService
                  .findVariantsOf(identity)
                  .forEach(payloadVersionService::refreshLatest);
            });
  }

  private static void touch(Set<String> touched, ContentType type, String externalId) {
    if (type == ContentType.PAYLOAD) {
      touched.add(externalId);
    }
  }

  private static ContentType type(String value) {
    if (value == null) {
      return ContentType.PAYLOAD;
    }
    try {
      return ContentType.valueOf(value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new BadRequestException("Unknown content type " + value);
    }
  }

  private static String key(ContentType type, String externalId, String version) {
    return type + "|" + externalId + "|" + version;
  }

  private static RevocationListOutput output(
      RevocationListState state, boolean applied, int added, int lifted) {
    return new RevocationListOutput(
        state.getRepository(),
        state.getSerial(),
        state.getIssuedAt(),
        state.getExpiresAt(),
        state.getDownloadedAt(),
        applied,
        added,
        lifted);
  }
}
