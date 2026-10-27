package io.openaev.rest.payload.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import io.openaev.database.model.*;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.GrantService;
import io.openaev.service.UserService;
import io.openaev.service.content.ContentHistoryService;
import io.openaev.service.payload.PayloadRevocationService;
import io.openaev.service.payload.PayloadVersionNumbering;
import io.openaev.service.payload.PayloadVersionNumbering.Change;
import io.openaev.service.payload.PayloadVersionService;
import io.openaev.utils.SemanticVersion;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Variants of catalogue payloads (ADR-018, point 3): a local identity that keeps only the fields
 * changed on its base. Each version is computed once, base then overrides, and runs like any other
 * row. A new base is merged by a rebase the user previews first; the comparison is per block.
 */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class PayloadVariantService {

  public enum Block {
    EXECUTION,
    PLATFORMS
  }

  public enum Choice {
    KEEP_MINE,
    TAKE_CATALOG,
    EDIT
  }

  private static final List<String> EXECUTION_FIELDS =
      List.of(
          "command_executor",
          "command_content",
          "payload_cleanup_executor",
          "payload_cleanup_command",
          "payload_arguments",
          "payload_prerequisites");
  private static final List<String> PLATFORMS_FIELDS = List.of("payload_platforms");
  private static final Set<String> COMMAND_ONLY = Set.of("command_executor", "command_content");
  // Filled by the agent, never by an argument.
  private static final Set<String> BUILT_IN_VARIABLES =
      Set.of("location", "payload_location", "inject", "agent", "tenant", "token", "host");
  private static final Pattern VARIABLE = Pattern.compile("#\\{([A-Za-z0-9_.-]+)}");

  private final PayloadVersionService payloadVersionService;
  private final PayloadVersionNumbering payloadVersionNumbering;
  private final PayloadRevocationService payloadRevocationService;
  private final PayloadService payloadService;
  private final InjectorContractRepository injectorContractRepository;
  private final GrantService grantService;
  private final ContentHistoryService contentHistoryService;
  private final UserService userService;
  private final ObjectMapper mapper;

  public static List<String> fieldsOf(Block block) {
    return block == Block.EXECUTION ? EXECUTION_FIELDS : PLATFORMS_FIELDS;
  }

  /** Fields a variant may change on a payload of this type (closed list). */
  public static List<String> customizableFields(Payload payload) {
    List<String> fields = new ArrayList<>();
    for (Block block : Block.values()) {
      fieldsOf(block).stream()
          .filter(f -> !COMMAND_ONLY.contains(f) || payload instanceof Command)
          .forEach(fields::add);
    }
    return fields;
  }

  // -- READ --

  public record VariantInfo(
      PayloadIdentity identity,
      Payload latest,
      Payload base,
      Payload newestBase,
      Payload conflictTarget,
      List<String> customizableFields,
      Map<String, JsonNode> baseValues,
      Map<String, JsonNode> currentValues) {}

  @Transactional(readOnly = true)
  public VariantInfo info(String identityId) {
    PayloadIdentity identity = variantIdentity(identityId);
    Payload latest = highest(identity);
    Payload base = baseOf(latest);
    Payload newestBase = newerBase(base).orElse(null);
    Payload conflict =
        identity.getConflictTargetPayloadId() == null
            ? null
            : payloadVersionService.findById(identity.getConflictTargetPayloadId());
    List<String> fields = customizableFields(unproxy(base));
    return new VariantInfo(
        identity,
        latest,
        base,
        newestBase,
        conflict,
        fields,
        values(base, fields),
        values(latest, fields));
  }

  @Transactional(readOnly = true)
  public String actionIdOf(Payload payload) {
    InjectorContract contract = contractOf(payload);
    return contract == null ? null : contract.getId();
  }

  // -- CREATE --

  public record VariantResult(
      Payload payload, InjectorContract injectorContract, Payload previous, int previousUsages) {}

  /** "Customize": a new local identity with no change yet, at 1.0.0 (never on a revoked base). */
  public VariantResult customize(String basePayloadId) {
    Payload base = unproxy(payloadVersionService.findById(basePayloadId));
    if (!base.isManaged()) {
      throw new BadRequestException("Only a catalogue payload can be customized");
    }
    if (payloadRevocationService.isRevoked(base)) {
      throw new BadRequestException("A revoked payload cannot be customized");
    }
    PayloadIdentity identity = new PayloadIdentity();
    identity.setTenant(base.getTenant());
    identity.setSource(Payload.PAYLOAD_SOURCE.MANUAL);
    identity.setOverrides(new LinkedHashMap<>());
    Payload first = compose(base, Map.of());
    User user = userService.currentUserOrNull();
    if (user != null) {
      first.setAuthorUser(user);
      first.setAuthorTeam(null);
      first.setAuthorOrganization(null);
    }
    Payload saved =
        payloadVersionService.addVersion(
            identity, first, PayloadVersionService.Numbering.fixed(Payload.FIRST_VERSION));
    InjectorContract contract = synchronise(saved, contractOf(base));
    contentHistoryService.payloadCustomized(base, saved);
    return new VariantResult(saved, contract, null, 0);
  }

  // -- UPDATE --

  /** Changes the overrides: a new version when the computed content changes. */
  public VariantResult updateOverrides(String identityId, Map<String, Object> overrides) {
    PayloadIdentity identity = variantIdentity(identityId);
    Payload current = highest(identity);
    Payload base = baseOf(current);
    Map<String, Object> cleaned = validated(base, overrides);
    Payload candidate = compose(base, cleaned);
    Optional<Change> change =
        payloadVersionNumbering.change(
            current, attackPatternsOf(current), candidate, attackPatternsOf(base));
    identity.setOverrides(new LinkedHashMap<>(cleaned));
    if (change.isEmpty()) {
      return new VariantResult(current, contractOf(current), null, 0);
    }
    return addVariantVersion(identity, current, candidate, base, change.get(), null);
  }

  // -- REBASE --

  public record BlockPreview(
      Block block,
      boolean catalogChanged,
      boolean variantChanged,
      boolean conflict,
      Map<String, JsonNode> base,
      Map<String, JsonNode> catalog,
      Map<String, JsonNode> mine) {}

  public record RebasePreview(
      Payload current,
      Payload base,
      Payload target,
      List<BlockPreview> blocks,
      List<String> missingVariables) {

    public boolean hasConflict() {
      return blocks.stream().anyMatch(BlockPreview::conflict) || !missingVariables.isEmpty();
    }
  }

  /** What a rebase on the target catalogue version would merge alone, and its conflicts. */
  @Transactional(readOnly = true)
  public RebasePreview previewRebase(String identityId, String targetPayloadId) {
    PayloadIdentity identity = variantIdentity(identityId);
    return preview(identity, targetPayloadId);
  }

  private RebasePreview preview(PayloadIdentity identity, String targetPayloadId) {
    Payload current = highest(identity);
    Payload base = baseOf(current);
    Payload target = rebaseTarget(base, targetPayloadId);
    Map<String, Object> overrides = overridesOf(identity);
    List<BlockPreview> blocks = new ArrayList<>();
    for (Block block : Block.values()) {
      List<String> fields = applicable(fieldsOf(block), base);
      boolean catalogChanged =
          fields.stream().anyMatch(f -> !value(base, f).equals(value(target, f)));
      boolean variantChanged = fields.stream().anyMatch(overrides::containsKey);
      blocks.add(
          new BlockPreview(
              block,
              catalogChanged,
              variantChanged,
              catalogChanged && variantChanged,
              values(base, fields),
              values(target, fields),
              values(compose(base, overrides), fields)));
    }
    Map<String, Object> merged = new LinkedHashMap<>(overrides);
    blocks.stream()
        .filter(BlockPreview::conflict)
        .forEach(b -> fieldsOf(b.block()).forEach(merged::remove));
    return new RebasePreview(
        current, base, target, blocks, missingVariables(compose(target, merged), target));
  }

  /**
   * Rebases the variant on a catalogue version with the user's choice for each conflicting block.
   * Without conflict, no choice is needed: this is also what an automatic rebase would call.
   */
  public VariantResult rebase(
      String identityId, String targetPayloadId, Map<Block, BlockChoice> choices) {
    PayloadIdentity identity = variantIdentity(identityId);
    RebasePreview preview = preview(identity, targetPayloadId);
    Map<String, Object> overrides = new LinkedHashMap<>(overridesOf(identity));
    for (BlockPreview block : preview.blocks()) {
      if (!block.conflict()) {
        continue;
      }
      BlockChoice choice = choices == null ? null : choices.get(block.block());
      if (choice == null) {
        throw new BadRequestException("Choose how to merge the " + block.block() + " block");
      }
      List<String> fields = fieldsOf(block.block());
      switch (choice.choice()) {
        case KEEP_MINE -> {}
        case TAKE_CATALOG -> fields.forEach(overrides::remove);
        case EDIT -> {
          fields.forEach(overrides::remove);
          Optional.ofNullable(choice.values()).orElse(Map.of()).entrySet().stream()
              .filter(e -> fields.contains(e.getKey()))
              .forEach(e -> overrides.put(e.getKey(), e.getValue()));
        }
      }
    }
    Payload target = preview.target();
    Map<String, Object> cleaned = validated(target, overrides);
    Payload candidate = compose(target, cleaned);
    List<String> missing = missingVariables(candidate, target);
    if (!missing.isEmpty()) {
      throw new BadRequestException(
          "Variables without an argument after the merge: " + String.join(", ", missing));
    }
    // A rebased version moves at least as far as its base did.
    Change baseChange =
        payloadVersionNumbering
            .change(
                preview.base(), attackPatternsOf(preview.base()), target, attackPatternsOf(target))
            .orElse(Change.PATCH);
    Change ownChange =
        payloadVersionNumbering
            .change(
                preview.current(),
                attackPatternsOf(preview.current()),
                candidate,
                attackPatternsOf(target))
            .orElse(Change.PATCH);
    identity.setOverrides(cleaned);
    identity.setConflictTargetPayloadId(null);
    Map<String, String> taken = new LinkedHashMap<>();
    preview.blocks().stream()
        .filter(BlockPreview::conflict)
        .forEach(b -> taken.put(b.block().name(), choices.get(b.block()).choice().name()));
    return addVariantVersion(
        identity, preview.current(), candidate, target, stronger(baseChange, ownChange), taken);
  }

  public record BlockChoice(Choice choice, Map<String, Object> values) {}

  /**
   * After a new catalogue version is installed: marks the variants of that payload that cannot be
   * rebased on it without a choice. The rebase itself stays manual, with a preview.
   */
  public void markConflicts(Payload newCatalogVersion) {
    for (PayloadIdentity variant :
        payloadVersionService.findVariantsOf(newCatalogVersion.getIdentity())) {
      Payload base = baseOf(highest(variant));
      if (SemanticVersion.ORDER.compare(newCatalogVersion.getVersion(), base.getVersion()) <= 0) {
        continue;
      }
      RebasePreview preview = preview(variant, newCatalogVersion.getId());
      if (preview.hasConflict()) {
        variant.setConflictTargetPayloadId(newCatalogVersion.getId());
        contentHistoryService.payloadConflict(preview.current(), newCatalogVersion);
      } else {
        variant.setConflictTargetPayloadId(null);
      }
    }
  }

  // -- HELPERS --

  private VariantResult addVariantVersion(
      PayloadIdentity identity,
      Payload current,
      Payload candidate,
      Payload base,
      Change change,
      Map<String, String> choices) {
    InjectorContract previousContract = contractOf(current);
    candidate.setAuthorUser(current.getAuthorUser());
    candidate.setAuthorTeam(current.getAuthorTeam());
    candidate.setAuthorOrganization(current.getAuthorOrganization());
    Payload saved =
        payloadVersionService.addVersion(
            identity,
            candidate,
            highest -> PayloadVersionNumbering.bump(highest.getVersion(), change));
    InjectorContract contract =
        synchronise(saved, previousContract != null ? previousContract : contractOf(base));
    if (previousContract != null && contract != null) {
      grantService.copyGrants(
          previousContract.getId(), contract.getId(), Grant.GRANT_RESOURCE_TYPE.THREAT_ARSENAL);
    }
    if (choices != null) {
      contentHistoryService.payloadRebased(current, saved, choices);
    } else {
      contentHistoryService.payloadOverridden(current, saved);
    }
    int usages =
        previousContract == null ? 0 : payloadVersionService.countTemplateUsages(previousContract);
    return new VariantResult(saved, contract, current, usages);
  }

  /** The base content with the overrides on top: the row a variant version stores. */
  private Payload compose(Payload base, Map<String, Object> overrides) {
    Payload composed = payloadService.copyForNewVersion(base);
    composed.setName(base.getName() + " (customized)");
    composed.setOriginId(base.getId());
    composed.setStatus(Payload.PAYLOAD_STATUS.UNVERIFIED);
    overrides.forEach((field, value) -> write(composed, field, value));
    return composed;
  }

  private Map<String, Object> validated(Payload base, Map<String, Object> overrides) {
    List<String> allowed = customizableFields(unproxy(base));
    Map<String, Object> cleaned = new LinkedHashMap<>();
    Optional.ofNullable(overrides)
        .orElse(Map.of())
        .forEach(
            (field, value) -> {
              if (!allowed.contains(field)) {
                throw new BadRequestException("This field cannot be customized: " + field);
              }
              // A value equal to the base is no change.
              if (!mapper.valueToTree(value).equals(value(base, field))) {
                cleaned.put(field, value);
              }
            });
    return cleaned;
  }

  private List<String> missingVariables(Payload merged, Payload catalog) {
    Set<String> known = new HashSet<>(BUILT_IN_VARIABLES);
    Optional.ofNullable(merged.getArguments())
        .orElse(List.of())
        .forEach(a -> known.add(a.getKey()));
    // Placeholders the catalogue content uses without an argument are filled by the platform.
    Set<String> catalogUsed = variablesIn(catalog);
    Optional.ofNullable(catalog.getArguments())
        .orElse(List.of())
        .forEach(a -> catalogUsed.remove(a.getKey()));
    known.addAll(catalogUsed);
    return variablesIn(merged).stream().filter(v -> !known.contains(v)).sorted().toList();
  }

  private Set<String> variablesIn(Payload payload) {
    StringBuilder text = new StringBuilder();
    if (payload instanceof Command command) {
      text.append(command.getContent());
    }
    text.append(' ').append(payload.getCleanupCommand());
    JsonNode prerequisites = mapper.valueToTree(payload.getPrerequisites());
    text.append(' ').append(prerequisites);
    Set<String> variables = new HashSet<>();
    Matcher matcher = VARIABLE.matcher(text);
    while (matcher.find()) {
      variables.add(matcher.group(1));
    }
    return variables;
  }

  private JsonNode value(Payload payload, String field) {
    Payload p = unproxy(payload);
    Object raw =
        switch (field) {
          case "command_executor" -> p instanceof Command c ? c.getExecutor() : null;
          case "command_content" -> p instanceof Command c ? c.getContent() : null;
          case "payload_cleanup_executor" -> p.getCleanupExecutor();
          case "payload_cleanup_command" -> p.getCleanupCommand();
          case "payload_platforms" -> p.getPlatforms();
          case "payload_prerequisites" -> p.getPrerequisites();
          case "payload_arguments" -> p.getArguments();
          default -> throw new BadRequestException("Unknown field: " + field);
        };
    return raw == null ? NullNode.getInstance() : mapper.valueToTree(raw);
  }

  private void write(Payload payload, String field, Object value) {
    JsonNode node = mapper.valueToTree(value);
    switch (field) {
      case "command_executor" ->
          ((Command) payload).setExecutor(node.isNull() ? null : node.asText());
      case "command_content" ->
          ((Command) payload).setContent(node.isNull() ? null : node.asText());
      case "payload_cleanup_executor" ->
          payload.setCleanupExecutor(node.isNull() ? null : node.asText());
      case "payload_cleanup_command" ->
          payload.setCleanupCommand(node.isNull() ? null : node.asText());
      case "payload_platforms" ->
          payload.setPlatforms(mapper.convertValue(node, Endpoint.PLATFORM_TYPE[].class));
      case "payload_prerequisites" ->
          payload.setPrerequisites(
              new ArrayList<>(
                  Arrays.asList(mapper.convertValue(node, PayloadPrerequisite[].class))));
      case "payload_arguments" ->
          payload.setArguments(
              new ArrayList<>(Arrays.asList(mapper.convertValue(node, PayloadArgument[].class))));
      default -> throw new BadRequestException("This field cannot be customized: " + field);
    }
  }

  private Map<String, JsonNode> values(Payload payload, List<String> fields) {
    Map<String, JsonNode> values = new LinkedHashMap<>();
    fields.forEach(f -> values.put(f, value(payload, f)));
    return values;
  }

  private static List<String> applicable(List<String> fields, Payload payload) {
    return fields.stream()
        .filter(f -> !COMMAND_ONLY.contains(f) || Hibernate.unproxy(payload) instanceof Command)
        .toList();
  }

  private static Change stronger(Change a, Change b) {
    return a.ordinal() <= b.ordinal() ? a : b;
  }

  private Payload rebaseTarget(Payload base, String targetPayloadId) {
    Payload target =
        targetPayloadId != null
            ? payloadVersionService.findById(targetPayloadId)
            : newerBase(base)
                .orElseThrow(
                    () -> new BadRequestException("No newer catalogue version to rebase on"));
    if (!target.getIdentity().getId().equals(base.getIdentity().getId())) {
      throw new BadRequestException("The target is not a version of the variant's base");
    }
    if (target.getIdentity().isUninstalled()) {
      throw new BadRequestException(
          "The base payload is uninstalled: its variants are not rebased");
    }
    if (payloadRevocationService.isRevoked(target)) {
      throw new BadRequestException("The target version is revoked");
    }
    return unproxy(target);
  }

  // The newest installed, non-revoked catalogue version above the variant's base.
  private Optional<Payload> newerBase(Payload base) {
    if (base.getIdentity().isUninstalled()) {
      return Optional.empty();
    }
    return payloadVersionService.findVersions(base.getIdentity()).stream()
        .filter(v -> SemanticVersion.ORDER.compare(v.getVersion(), base.getVersion()) > 0)
        .filter(v -> !payloadRevocationService.isRevoked(v))
        .max(Comparator.comparing(Payload::getVersion, SemanticVersion.ORDER));
  }

  private PayloadIdentity variantIdentity(String identityId) {
    PayloadIdentity identity =
        payloadVersionService
            .findIdentity(identityId)
            .orElseThrow(() -> new ElementNotFoundException("Payload not found: " + identityId));
    if (!identity.isVariant()) {
      throw new BadRequestException("This payload is not a customized catalogue payload");
    }
    return identity;
  }

  private Map<String, Object> overridesOf(PayloadIdentity identity) {
    return identity.getOverrides() == null ? Map.of() : identity.getOverrides();
  }

  private Payload highest(PayloadIdentity identity) {
    return unproxy(
        payloadVersionService.findVersions(identity).stream()
            .max(Comparator.comparing(Payload::getVersion, SemanticVersion.ORDER))
            .orElseThrow(() -> new ElementNotFoundException("The variant has no version")));
  }

  private Payload baseOf(Payload variantVersion) {
    if (variantVersion.getOriginId() == null) {
      throw new BadRequestException("The variant has lost its catalogue base");
    }
    return unproxy(payloadVersionService.findById(variantVersion.getOriginId()));
  }

  private InjectorContract contractOf(Payload payload) {
    return injectorContractRepository.findInjectorContractByPayload(payload).orElse(null);
  }

  private List<AttackPattern> attackPatternsOf(Payload payload) {
    InjectorContract contract = contractOf(payload);
    return contract == null ? List.of() : contract.getAttackPatterns();
  }

  private InjectorContract synchronise(Payload saved, InjectorContract from) {
    return payloadService.synchroniseInjectorContractBasedOnPayload(
        saved,
        from == null ? List.of() : from.getAttackPatterns(),
        from == null ? Set.of() : from.getDomains(),
        from == null ? Set.of() : from.getTags());
  }

  private static Payload unproxy(Payload payload) {
    return (Payload) Hibernate.unproxy(payload);
  }
}
