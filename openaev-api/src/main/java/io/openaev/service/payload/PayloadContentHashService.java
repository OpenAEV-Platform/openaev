package io.openaev.service.payload;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.model.hash.Hashed;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.service.hash.CanonicalContent;
import io.openaev.service.hash.UnreadableFileException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

/** Content hash of a payload: its own hashed fields, and the techniques of its contract. */
@Service
@RequiredArgsConstructor
public class PayloadContentHashService {

  private static final String ATTACK_PATTERNS_KEY = "payload_attack_patterns";

  private final CanonicalContent canonicalContent;
  private final PayloadRepository payloadRepository;
  private final InjectorContractRepository injectorContractRepository;

  /** Empty when an attached file cannot be read: such a payload is never linked by content. */
  public Optional<String> hash(Payload payload, Collection<AttackPattern> attackPatterns) {
    try {
      return Optional.of(canonicalContent.hash(canonicalTree(payload, attackPatterns)));
    } catch (UnreadableFileException e) {
      return Optional.empty();
    }
  }

  /** Hashes the payloads of this name saved before content hashes existed. */
  public void hashMissing(String tenantId, String name) {
    List<Payload> unhashed =
        payloadRepository.findAllByTenantIdAndNameAndContentHashIsNull(tenantId, name);
    unhashed.forEach(p -> p.setContentHash(hash(p, attackPatternsOf(p)).orElse(null)));
    payloadRepository.saveAll(unhashed);
  }

  private List<AttackPattern> attackPatternsOf(Payload payload) {
    return injectorContractRepository
        .findInjectorContractByPayload(payload)
        .map(InjectorContract::getAttackPatterns)
        .orElse(List.of());
  }

  /** Per key of the canonical form, what a change of it makes of a new version. */
  Map<String, Hashed.Change> changes(Payload payload) {
    Map<String, Hashed.Change> changes =
        new HashMap<>(canonicalContent.changes(Hibernate.getClass(payload)));
    // The techniques describe the threat: descriptive metadata.
    changes.put(ATTACK_PATTERNS_KEY, Hashed.Change.PATCH);
    return changes;
  }

  ObjectNode canonicalTree(Payload payload, CanonicalContent.Files files) {
    return canonicalTree(payload, attackPatternsOf(payload), files);
  }

  ObjectNode canonicalTree(Payload payload, Collection<AttackPattern> attackPatterns) {
    return canonicalTree(payload, attackPatterns, CanonicalContent.Files.BY_HASH);
  }

  // The techniques belong to the payload's contract, not to the payload: they are added here.
  ObjectNode canonicalTree(
      Payload payload, Collection<AttackPattern> attackPatterns, CanonicalContent.Files files) {
    ObjectNode tree = canonicalContent.tree(payload, files);
    ArrayNode techniques =
        canonicalContent.sortedValues(
            attackPatterns == null
                ? List.of()
                : attackPatterns.stream().map(AttackPattern::getExternalId).toList());
    if (!techniques.isEmpty()) {
      tree.set(ATTACK_PATTERNS_KEY, techniques);
    }
    return tree;
  }
}
