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
   * Import options that configure the collector relationship on detection remediations to only
   * include collectors that already exist in the target database. If a collector is not found by
   * its business key, the entire detection remediation is skipped.
   */
  private static final IncludeOptions IMPORT_OPTIONS =
      IncludeOptions.of(
          Map.of(
              "detection_remediation_collector_type", IncludeOptions.IncludeMode.IF_EXISTS_IN_DB));

  /**
   * Imports a payload from a JSON:API ZIP and synchronizes the associated injector contract.
   *
   * <p>Legacy payload exports may contain {@code payload_attack_patterns}, {@code payload_domains},
   * and {@code payload_tags} as relationships. Since these fields now live on {@code
   * InjectorContract}, they are extracted from the source document and passed to the
   * synchronization method.
   *
   * @param file the ZIP file containing the JSON:API payload document
   * @return the import result containing the persisted payload and the synchronised injector
   *     contract
   */
  public PayloadImportResult importPayload(MultipartFile file) throws Exception {
    ZipJsonService.ImportOutput<Payload> response =
        zipJsonApi.handleImport(file, "payload_name", IMPORT_OPTIONS, null);

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

  /**
   * Result of a payload import operation.
   *
   * @param payloadOutput the ZIP import output containing the persisted payload and JSON:API docs
   * @param injectorContract the synchronised injector contract (may be null if no injector matched)
   */
  public record PayloadImportResult(
      ZipJsonService.ImportOutput<Payload> payloadOutput, InjectorContract injectorContract) {}

  private AttackPattern handleAttackPatternImport(ResourceObject object) {
    AttackPatternCreateInput input = new AttackPatternCreateInput();
    input.setName(object.attributes().get("attack_pattern_name").toString());
    input.setDescription(object.attributes().get("attack_pattern_description").toString());
    input.setStixId(object.attributes().get("attack_pattern_stix_id").toString());
    input.setExternalId(object.attributes().get("attack_pattern_external_id").toString());
    input.setPlatforms(asStringArray(object.attributes().get("attack_pattern_platforms")));
    input.setPermissionsRequired(
        asStringArray(object.attributes().get("attack_pattern_permissions_required")));
    return attackPatternService.findOrCreate(input);
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
      JsonApiDocument<ResourceObject> resourceDocument) {
    Relationship relationship = getPayloadRelationship(resourceDocument, relName);
    if (relationship == null || relationship.asMany() == null || relationship.asMany().isEmpty()) {
      return Collections.emptyList();
    }

    Map<String, ResourceObject> includedById =
        getIncludedResourcesByType(resourceDocument, relName);
    if (includedById.isEmpty()) {
      return Collections.emptyList();
    }

    return relationship.asMany().stream()
        .map(ref -> includedById.get(ref.id()))
        .filter(Objects::nonNull)
        .map(valueExtractor)
        .filter(Objects::nonNull)
        .toList();
  }

  private Relationship getPayloadRelationship(
      JsonApiDocument<ResourceObject> resourceDocument, String relName) {
    if (resourceDocument == null || resourceDocument.data() == null) {
      return null;
    }
    Map<String, Relationship> relationships =
        Optional.ofNullable(resourceDocument.data().relationships()).orElse(Collections.emptyMap());
    return relationships.get("payload_" + relName);
  }

  private Map<String, ResourceObject> getIncludedResourcesByType(
      JsonApiDocument<ResourceObject> resourceDocument, String type) {
    return Optional.ofNullable(resourceDocument.included()).orElse(Collections.emptyList()).stream()
        .map(this::toResourceObject)
        .filter(Objects::nonNull)
        .filter(resource -> type.equals(resource.type()))
        .collect(
            java.util.stream.Collectors.toMap(
                ResourceObject::id,
                Function.identity(),
                (first, second) -> first,
                LinkedHashMap::new));
  }

  private ResourceObject toResourceObject(Object raw) {
    if (raw instanceof ResourceObject resourceObject) {
      return resourceObject;
    }
    return mapper.convertValue(raw, ResourceObject.class);
  }

  private String[] asStringArray(Object value) {
    if (value == null) {
      return new String[0];
    }
    return mapper.convertValue(value, String[].class);
  }
}
