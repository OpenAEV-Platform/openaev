package io.openaev.api.payload;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.helper.StreamHelper.iterableToSet;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.*;
import io.openaev.jsonapi.*;
import io.openaev.rest.attack_pattern.form.AttackPatternCreateInput;
import io.openaev.rest.attack_pattern.service.AttackPatternService;
import io.openaev.rest.domain.DomainService;
import io.openaev.rest.domain.form.DomainBaseInput;
import io.openaev.rest.payload.service.PayloadService;
import io.openaev.rest.tag.TagService;
import io.openaev.rest.tag.form.TagCreateInput;
import io.openaev.service.ZipJsonService;
import jakarta.annotation.Resource;
import java.util.*;
import java.util.function.Function;
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
  private final AttackPatternService attackPatternService;
  private final DomainService domainService;
  private final TagService tagService;

  @Resource protected ObjectMapper mapper;

  /**
   * Imports a payload from a JSON:API ZIP and synchronises the associated injector contract.
   *
   * <p>Legacy payload exports may contain {@code payload_attack_patterns}, {@code payload_domains},
   * and {@code payload_tags} as relationships. Since these fields now live on {@code
   * InjectorContract}, they are extracted from the source document and passed to the
   * synchronisation method.
   *
   * @param file the ZIP file containing the JSON:API payload document
   * @return the import result containing the persisted payload and the synchronised injector
   *     contract
   */
  public PayloadImportResult importPayload(MultipartFile file) throws Exception {
    ZipJsonService.ImportOutput<Payload> response = zipJsonApi.handleImport(file, "payload_name");

    List<AttackPattern> attackPatterns =
        extractRelationshipObjects(
            "attack_patterns", this::handleAttackPatternImport, response.sourceDocument());
    List<Domain> domains =
        extractRelationshipObjects("domains", this::handleDomainImport, response.sourceDocument());
    List<Tag> tags =
        extractRelationshipObjects("tags", this::handleTagImport, response.sourceDocument());

    InjectorContract injectorContract =
        payloadService.synchroniseInjectorContractBasedOnPayload(
            response.persistedData(),
            fromIterable(attackPatterns),
            iterableToSet(domains),
            iterableToSet(tags));

    return new PayloadImportResult(response, injectorContract);
  }

  private AttackPattern handleAttackPatternImport(ResourceObject object) {
    AttackPatternCreateInput input = new AttackPatternCreateInput();
    input.setName(object.attributes().get("attack_pattern_name").toString());
    input.setDescription(object.attributes().get("attack_pattern_description").toString());
    input.setStixId(object.attributes().get("attack_pattern_stix_id").toString());
    input.setExternalId(object.attributes().get("attack_pattern_external_id").toString());
    input.setPlatforms(asStringArray(object.attributes().get("attack_pattern_platforms")));
    input.setPermissionsRequired(
        asStringArray(object.attributes().get("attack_pattern_permissions_required")));
    return attackPatternService.upsert(input);
  }

  private Domain handleDomainImport(ResourceObject object) {
    DomainBaseInput input = new DomainBaseInput();
    input.setName(object.attributes().get("domain_name").toString());
    input.setColor(object.attributes().get("domain_color").toString());
    return domainService.upsert(input);
  }

  private Tag handleTagImport(ResourceObject object) {
    TagCreateInput input = new TagCreateInput();
    input.setName(object.attributes().get("tag_name").toString());
    input.setColor(object.attributes().get("tag_color").toString());
    return tagService.upsertTag(input);
  }

  private <T> List<T> extractRelationshipObjects(
      String relName,
      Function<ResourceObject, T> valueExtractor,
      JsonApiDocument<ResourceObject> ressourceDocument) {
    Map<String, Relationship> rels =
        ressourceDocument.data().relationships() != null
            ? ressourceDocument.data().relationships()
            : Collections.emptyMap();

    Relationship rel = rels.get("payload_" + relName);
    if (rel == null) {
      return Collections.emptyList();
    }

    List<ResourceObject> importRessources =
        Optional.ofNullable(ressourceDocument.included()).orElseGet(Collections::emptyList).stream()
            .map(
                o ->
                    o instanceof ResourceObject ro
                        ? ro
                        : mapper.convertValue(o, ResourceObject.class))
            .filter(resource -> relName.equals(resource.type()))
            .toList();

    if (importRessources.isEmpty()) {
      return Collections.emptyList();
    }

    return rel.asMany().stream()
        .map(
            ressourceToimport -> {
              ResourceObject importedData =
                  importRessources.stream()
                      .filter(ressource -> ressource.id().equals(ressourceToimport.id()))
                      .findFirst()
                      .orElse(null);

              if (importedData == null) {
                return null;
              }
              return valueExtractor.apply(importedData);
            })
        .filter(Objects::nonNull)
        .toList();
  }

  private String[] asStringArray(Object value) {
    if (value == null) {
      return new String[0];
    }
    return mapper.convertValue(value, String[].class);
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
