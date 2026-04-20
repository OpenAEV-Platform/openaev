package io.openaev.api.payload;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.helper.StreamHelper.iterableToSet;

import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.repository.AttackPatternRepository;
import io.openaev.database.repository.DomainRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.jsonapi.Relationship;
import io.openaev.jsonapi.ResourceIdentifier;
import io.openaev.jsonapi.ZipJsonApi;
import io.openaev.rest.payload.service.PayloadService;
import io.openaev.service.ZipJsonService;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Handles importing a payload from a JSON:API ZIP file and synchronising the associated injector
 * contract with legacy relationship data (attack patterns, domains, tags).
 */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class PayloadImportService {

  private final ZipJsonApi<Payload> zipJsonApi;
  private final PayloadService payloadService;
  private final AttackPatternRepository attackPatternRepository;
  private final DomainRepository domainRepository;
  private final TagRepository tagRepository;

  /**
   * Imports a payload from a JSON:API ZIP and synchronises the associated injector contract.
   *
   * <p>Legacy payload exports may contain {@code payload_attack_patterns}, {@code
   * payload_domains}, and {@code payload_tags} as relationships. Since these fields now live on
   * {@code InjectorContract}, they are extracted from the source document and passed to the
   * synchronisation method.
   *
   * @param file the ZIP file containing the JSON:API payload document
   * @return the import result containing the persisted payload and the synchronised injector
   *     contract
   */
  public PayloadImportResult importPayload(MultipartFile file) throws Exception {
    ZipJsonService.ImportOutput<Payload> response =
        zipJsonApi.handleImport(file, "payload_name");

    Map<String, Relationship> rels =
        response.sourceDocument().data().relationships() != null
            ? response.sourceDocument().data().relationships()
            : Collections.emptyMap();

    List<String> attackPatternIds = extractRelationshipIds(rels, "payload_attack_patterns");
    List<String> domainIds = extractRelationshipIds(rels, "payload_domains");
    List<String> tagIds = extractRelationshipIds(rels, "payload_tags");

    InjectorContract injectorContract =
        payloadService.synchroniseInjectorContractBasedOnPayload(
            response.persistedData(),
            fromIterable(attackPatternRepository.findAllById(attackPatternIds)),
            iterableToSet(domainRepository.findAllById(domainIds)),
            iterableToSet(tagRepository.findAllById(tagIds)));

    return new PayloadImportResult(response, injectorContract);
  }

  private static List<String> extractRelationshipIds(
      Map<String, Relationship> rels, String relName) {
    Relationship rel = rels.get(relName);
    if (rel == null) {
      return Collections.emptyList();
    }
    return rel.asMany().stream().map(ResourceIdentifier::id).toList();
  }

  /**
   * Result of a payload import operation.
   *
   * @param payloadOutput the ZIP import output containing the persisted payload and JSON:API docs
   * @param injectorContract the synchronised injector contract (may be null if no injector matched)
   */
  public record PayloadImportResult(
      ZipJsonService.ImportOutput<Payload> payloadOutput, InjectorContract injectorContract) {}
}

