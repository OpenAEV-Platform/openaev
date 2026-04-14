package io.openaev.database.model;

import static java.util.Map.entry;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;

public enum Capability {

  // Superuser
  BYPASS(
      null,
      CapabilityGroup.SUPERUSER,
      EnumSet.of(CapabilityScope.PLATFORM, CapabilityScope.TENANT),
      pair(null, null)),

  // Assessment
  ACCESS_ASSESSMENT(
      null,
      CapabilityGroup.ASSESSMENT,
      EnumSet.of(CapabilityScope.TENANT),
      pair(ResourceType.SCENARIO, Action.READ),
      pair(ResourceType.SCENARIO, Action.SEARCH),
      pair(ResourceType.SIMULATION, Action.READ),
      pair(ResourceType.SIMULATION, Action.SEARCH),
      pair(ResourceType.ATOMIC_TESTING, Action.READ),
      pair(ResourceType.ATOMIC_TESTING, Action.SEARCH),
      pair(ResourceType.WORKFLOW, Action.READ),
      pair(ResourceType.WORKFLOW, Action.SEARCH),
      pair(ResourceType.STEP, Action.READ),
      pair(ResourceType.STEP, Action.SEARCH),
      pair(ResourceType.CONDITION, Action.READ),
      pair(ResourceType.CONDITION, Action.SEARCH)),
  MANAGE_ASSESSMENT(
      ACCESS_ASSESSMENT,
      pair(ResourceType.SCENARIO, Action.WRITE),
      pair(ResourceType.SCENARIO, Action.DUPLICATE),
      pair(ResourceType.SCENARIO, Action.CREATE),
      pair(ResourceType.SIMULATION, Action.WRITE),
      pair(ResourceType.SIMULATION, Action.DUPLICATE),
      pair(ResourceType.SIMULATION, Action.CREATE),
      pair(ResourceType.ATOMIC_TESTING, Action.WRITE),
      pair(ResourceType.ATOMIC_TESTING, Action.DUPLICATE),
      pair(ResourceType.ATOMIC_TESTING, Action.CREATE),
      pair(ResourceType.STEP, Action.WRITE),
      pair(ResourceType.STEP, Action.DUPLICATE),
      pair(ResourceType.STEP, Action.CREATE),
      pair(ResourceType.WORKFLOW, Action.WRITE),
      pair(ResourceType.WORKFLOW, Action.DUPLICATE),
      pair(ResourceType.WORKFLOW, Action.CREATE),
      pair(ResourceType.CONDITION, Action.WRITE),
      pair(ResourceType.CONDITION, Action.DUPLICATE),
      pair(ResourceType.CONDITION, Action.CREATE)),
  DELETE_ASSESSMENT(
      MANAGE_ASSESSMENT,
      pair(ResourceType.SCENARIO, Action.DELETE),
      pair(ResourceType.SIMULATION, Action.DELETE),
      pair(ResourceType.ATOMIC_TESTING, Action.DELETE),
      pair(ResourceType.STEP, Action.DELETE),
      pair(ResourceType.WORKFLOW, Action.DELETE),
      pair(ResourceType.CONDITION, Action.DELETE)),
  LAUNCH_ASSESSMENT(
      ACCESS_ASSESSMENT,
      pair(ResourceType.SCENARIO, Action.LAUNCH),
      pair(ResourceType.SIMULATION, Action.LAUNCH),
      pair(ResourceType.ATOMIC_TESTING, Action.LAUNCH)),

  // Teams & Players
  ACCESS_TEAMS_AND_PLAYERS(
      null,
      CapabilityGroup.TARGETS,
      false,
      false,
      EnumSet.of(CapabilityScope.TENANT),
      pair(ResourceType.TEAM, Action.READ),
      pair(ResourceType.TEAM, Action.SEARCH),
      pair(ResourceType.PLAYER, Action.READ),
      pair(ResourceType.PLAYER, Action.SEARCH)),
  MANAGE_TEAMS_AND_PLAYERS(
      ACCESS_TEAMS_AND_PLAYERS,
      pair(ResourceType.TEAM, Action.WRITE),
      pair(ResourceType.TEAM, Action.CREATE),
      pair(ResourceType.PLAYER, Action.WRITE),
      pair(ResourceType.PLAYER, Action.CREATE)),
  DELETE_TEAMS_AND_PLAYERS(
      MANAGE_TEAMS_AND_PLAYERS,
      pair(ResourceType.TEAM, Action.DELETE),
      pair(ResourceType.PLAYER, Action.DELETE)),

  // Assets (Endpoints, Groups)
  ACCESS_ASSETS(
      null,
      CapabilityGroup.TARGETS,
      EnumSet.of(CapabilityScope.TENANT),
      pair(ResourceType.ASSET, Action.READ),
      pair(ResourceType.ASSET_GROUP, Action.READ),
      pair(ResourceType.JOB, Action.READ),
      pair(ResourceType.ASSET, Action.SEARCH),
      pair(ResourceType.ASSET_GROUP, Action.SEARCH),
      pair(ResourceType.JOB, Action.SEARCH)),
  MANAGE_ASSETS(
      ACCESS_ASSETS,
      pair(ResourceType.ASSET, Action.WRITE),
      pair(ResourceType.ASSET_GROUP, Action.WRITE),
      pair(ResourceType.JOB, Action.WRITE),
      pair(ResourceType.ASSET, Action.CREATE),
      pair(ResourceType.ASSET_GROUP, Action.CREATE),
      pair(ResourceType.JOB, Action.CREATE)),
  DELETE_ASSETS(
      MANAGE_ASSETS,
      pair(ResourceType.ASSET, Action.DELETE),
      pair(ResourceType.ASSET_GROUP, Action.DELETE),
      pair(ResourceType.JOB, Action.DELETE)),

  // Payloads
  ACCESS_PAYLOADS(
      null,
      CapabilityGroup.PAYLOADS,
      EnumSet.of(CapabilityScope.TENANT),
      pair(ResourceType.PAYLOAD, Action.READ),
      pair(ResourceType.PAYLOAD, Action.SEARCH)),
  MANAGE_PAYLOADS(
      ACCESS_PAYLOADS,
      pair(ResourceType.PAYLOAD, Action.WRITE),
      pair(ResourceType.PAYLOAD, Action.CREATE),
      pair(ResourceType.PAYLOAD, Action.DUPLICATE)),
  DELETE_PAYLOADS(MANAGE_PAYLOADS, pair(ResourceType.PAYLOAD, Action.DELETE)),

  // Dashboards
  ACCESS_DASHBOARDS(
      null,
      CapabilityGroup.DASHBOARDS,
      EnumSet.of(CapabilityScope.TENANT),
      pair(ResourceType.DASHBOARD, Action.READ),
      pair(ResourceType.DASHBOARD, Action.SEARCH)),
  MANAGE_DASHBOARDS(
      ACCESS_DASHBOARDS,
      pair(ResourceType.DASHBOARD, Action.WRITE),
      pair(ResourceType.DASHBOARD, Action.CREATE)),
  DELETE_DASHBOARDS(MANAGE_DASHBOARDS, pair(ResourceType.DASHBOARD, Action.DELETE)),

  // Findings
  ACCESS_FINDINGS(
      null,
      CapabilityGroup.FINDINGS,
      EnumSet.of(CapabilityScope.TENANT),
      pair(ResourceType.FINDING, Action.READ),
      pair(ResourceType.FINDING, Action.SEARCH)),
  MANAGE_FINDINGS(
      ACCESS_FINDINGS,
      true,
      pair(ResourceType.FINDING, Action.WRITE),
      pair(ResourceType.FINDING, Action.CREATE)),
  DELETE_FINDINGS(MANAGE_FINDINGS, true, pair(ResourceType.FINDING, Action.DELETE)),

  // Documents
  ACCESS_DOCUMENTS(
      null,
      CapabilityGroup.CONTENT,
      EnumSet.of(CapabilityScope.TENANT),
      pair(ResourceType.DOCUMENT, Action.READ),
      pair(ResourceType.DOCUMENT, Action.SEARCH)),
  MANAGE_DOCUMENTS(
      ACCESS_DOCUMENTS,
      pair(ResourceType.DOCUMENT, Action.WRITE),
      pair(ResourceType.DOCUMENT, Action.CREATE)),
  DELETE_DOCUMENTS(MANAGE_DOCUMENTS, pair(ResourceType.DOCUMENT, Action.DELETE)),

  // Channels
  ACCESS_CHANNELS(
      null,
      CapabilityGroup.CONTENT,
      EnumSet.of(CapabilityScope.TENANT),
      pair(ResourceType.CHANNEL, Action.READ),
      pair(ResourceType.CHANNEL, Action.SEARCH)),
  MANAGE_CHANNELS(
      ACCESS_CHANNELS,
      pair(ResourceType.CHANNEL, Action.WRITE),
      pair(ResourceType.CHANNEL, Action.CREATE)),
  DELETE_CHANNELS(MANAGE_CHANNELS, pair(ResourceType.CHANNEL, Action.DELETE)),

  // Challenges
  ACCESS_CHALLENGES(
      null,
      CapabilityGroup.CONTENT,
      EnumSet.of(CapabilityScope.TENANT),
      pair(ResourceType.CHALLENGE, Action.READ),
      pair(ResourceType.CHALLENGE, Action.SEARCH)),
  MANAGE_CHALLENGES(
      ACCESS_CHALLENGES,
      pair(ResourceType.CHALLENGE, Action.WRITE),
      pair(ResourceType.CHALLENGE, Action.CREATE)),
  DELETE_CHALLENGES(MANAGE_CHALLENGES, pair(ResourceType.CHALLENGE, Action.DELETE)),

  // Lessons Learned
  ACCESS_LESSONS_LEARNED(
      null,
      CapabilityGroup.CONTENT,
      EnumSet.of(CapabilityScope.TENANT),
      pair(ResourceType.LESSON_LEARNED, Action.READ),
      pair(ResourceType.LESSON_LEARNED, Action.SEARCH)),
  MANAGE_LESSONS_LEARNED(
      ACCESS_LESSONS_LEARNED,
      pair(ResourceType.LESSON_LEARNED, Action.WRITE),
      pair(ResourceType.LESSON_LEARNED, Action.CREATE)),
  DELETE_LESSONS_LEARNED(MANAGE_LESSONS_LEARNED, pair(ResourceType.LESSON_LEARNED, Action.DELETE)),

  // Security Platforms
  ACCESS_SECURITY_PLATFORMS(
      null,
      CapabilityGroup.TARGETS,
      EnumSet.of(CapabilityScope.TENANT),
      pair(ResourceType.SECURITY_PLATFORM, Action.READ),
      pair(ResourceType.SECURITY_PLATFORM, Action.SEARCH)),
  MANAGE_SECURITY_PLATFORMS(
      ACCESS_SECURITY_PLATFORMS,
      pair(ResourceType.SECURITY_PLATFORM, Action.WRITE),
      pair(ResourceType.SECURITY_PLATFORM, Action.CREATE)),
  DELETE_SECURITY_PLATFORMS(
      MANAGE_SECURITY_PLATFORMS, pair(ResourceType.SECURITY_PLATFORM, Action.DELETE)),

  // Platform Settings
  ACCESS_PLATFORM_SETTINGS(
      null,
      CapabilityGroup.PLATFORM_SETTINGS,
      EnumSet.of(CapabilityScope.PLATFORM),
      pair(ResourceType.PLATFORM_SETTING, Action.READ),
      pair(ResourceType.TAG_RULE, Action.READ),
      pair(ResourceType.COLLECTOR, Action.READ),
      pair(ResourceType.INJECTOR, Action.READ),
      pair(ResourceType.CATALOG, Action.READ),
      pair(ResourceType.MAPPER, Action.READ),
      pair(ResourceType.GROUP_ROLE, Action.READ),
      pair(ResourceType.USER_GROUP, Action.READ),
      pair(ResourceType.USER, Action.READ),
      pair(ResourceType.PLATFORM_SETTING, Action.SEARCH),
      pair(ResourceType.TAG_RULE, Action.SEARCH),
      pair(ResourceType.COLLECTOR, Action.SEARCH),
      pair(ResourceType.INJECTOR, Action.SEARCH),
      pair(ResourceType.CATALOG, Action.SEARCH),
      pair(ResourceType.MAPPER, Action.SEARCH),
      pair(ResourceType.ORGANIZATION, Action.SEARCH),
      pair(ResourceType.GROUP_ROLE, Action.SEARCH),
      pair(ResourceType.USER_GROUP, Action.SEARCH),
      pair(ResourceType.USER, Action.SEARCH)),
  MANAGE_PLATFORM_SETTINGS(
      ACCESS_PLATFORM_SETTINGS,
      pair(ResourceType.PLATFORM_SETTING, Action.WRITE),
      pair(ResourceType.ATTACK_PATTERN, Action.WRITE),
      pair(ResourceType.ATTACK_PATTERN, Action.CREATE),
      pair(ResourceType.KILL_CHAIN_PHASE, Action.WRITE),
      pair(ResourceType.KILL_CHAIN_PHASE, Action.CREATE),
      pair(ResourceType.TAG, Action.WRITE),
      pair(ResourceType.TAG, Action.CREATE),
      pair(ResourceType.TAG_RULE, Action.WRITE),
      pair(ResourceType.TAG_RULE, Action.CREATE),
      pair(ResourceType.VULNERABILITY, Action.WRITE),
      pair(ResourceType.VULNERABILITY, Action.CREATE),
      pair(ResourceType.COLLECTOR, Action.WRITE),
      pair(ResourceType.COLLECTOR, Action.CREATE),
      pair(ResourceType.INJECTOR, Action.WRITE),
      pair(ResourceType.INJECTOR, Action.CREATE),
      pair(ResourceType.CATALOG, Action.WRITE),
      pair(ResourceType.CATALOG, Action.CREATE),
      pair(ResourceType.INJECTOR_CONTRACT, Action.WRITE),
      pair(ResourceType.INJECTOR_CONTRACT, Action.CREATE),
      pair(ResourceType.ORGANIZATION, Action.WRITE),
      pair(ResourceType.ORGANIZATION, Action.CREATE),
      pair(ResourceType.GROUP_ROLE, Action.WRITE),
      pair(ResourceType.GROUP_ROLE, Action.CREATE),
      pair(ResourceType.USER_GROUP, Action.WRITE),
      pair(ResourceType.USER_GROUP, Action.CREATE),
      pair(ResourceType.USER, Action.WRITE),
      pair(ResourceType.USER, Action.CREATE),
      pair(ResourceType.PLATFORM_SETTING, Action.DELETE),
      pair(ResourceType.ATTACK_PATTERN, Action.DELETE),
      pair(ResourceType.KILL_CHAIN_PHASE, Action.DELETE),
      pair(ResourceType.TAG, Action.DELETE),
      pair(ResourceType.TAG_RULE, Action.DELETE),
      pair(ResourceType.VULNERABILITY, Action.DELETE),
      pair(ResourceType.COLLECTOR, Action.DELETE),
      pair(ResourceType.INJECTOR, Action.DELETE),
      pair(ResourceType.INJECTOR_CONTRACT, Action.DELETE),
      pair(ResourceType.ORGANIZATION, Action.DELETE),
      pair(ResourceType.MAPPER, Action.CREATE),
      pair(ResourceType.MAPPER, Action.DUPLICATE),
      pair(ResourceType.MAPPER, Action.DELETE),
      pair(ResourceType.MAPPER, Action.WRITE),
      pair(ResourceType.GROUP_ROLE, Action.DELETE),
      pair(ResourceType.USER_GROUP, Action.DELETE),
      pair(ResourceType.USER, Action.DELETE)),

  // Tenants
  ACCESS_TENANTS(
      null,
      CapabilityGroup.TENANTS,
      EnumSet.of(CapabilityScope.PLATFORM),
      pair(ResourceType.TENANT, Action.READ),
      pair(ResourceType.TENANT, Action.SEARCH)),
  MANAGE_TENANTS(
      ACCESS_TENANTS,
      pair(ResourceType.TENANT, Action.WRITE),
      pair(ResourceType.TENANT, Action.CREATE)),
  DELETE_TENANTS(MANAGE_TENANTS, pair(ResourceType.TENANT, Action.DELETE)),

  // Tenant Settings
  ACCESS_TENANT_SETTINGS(
      null,
      CapabilityGroup.TENANT_SETTING,
      EnumSet.of(CapabilityScope.TENANT),
      pair(ResourceType.TENANT_SETTING, Action.READ),
      pair(ResourceType.TENANT_SETTING, Action.SEARCH)),
  MANAGE_TENANT_SETTINGS(
      ACCESS_TENANT_SETTINGS,
      pair(ResourceType.TENANT_SETTING, Action.WRITE),
      pair(ResourceType.TENANT_SETTING, Action.CREATE)),
  DELETE_TENANT_SETTINGS(
      MANAGE_TENANT_SETTINGS, pair(ResourceType.TENANT_SETTING, Action.DELETE)),

  // Platform Groups & Roles
  ACCESS_PLATFORM_GROUPS_AND_ROLES(
      null,
      CapabilityGroup.PLATFORM_GROUPS_AND_ROLES,
      EnumSet.of(CapabilityScope.PLATFORM),
      pair(ResourceType.PLATFORM_GROUP, Action.READ),
      pair(ResourceType.PLATFORM_GROUP, Action.SEARCH),
      pair(ResourceType.PLATFORM_ROLE, Action.READ),
      pair(ResourceType.PLATFORM_ROLE, Action.SEARCH)),
  MANAGE_PLATFORM_GROUPS_AND_ROLES(
      ACCESS_PLATFORM_GROUPS_AND_ROLES,
      pair(ResourceType.PLATFORM_GROUP, Action.WRITE),
      pair(ResourceType.PLATFORM_GROUP, Action.CREATE),
      pair(ResourceType.PLATFORM_ROLE, Action.WRITE),
      pair(ResourceType.PLATFORM_ROLE, Action.CREATE)),
  DELETE_PLATFORM_GROUPS_AND_ROLES(
      MANAGE_PLATFORM_GROUPS_AND_ROLES,
      pair(ResourceType.PLATFORM_GROUP, Action.DELETE),
      pair(ResourceType.PLATFORM_ROLE, Action.DELETE)),

  // STIX
  MANAGE_STIX_BUNDLE(
      null,
      CapabilityGroup.STIX,
      true,
      true,
      EnumSet.of(CapabilityScope.TENANT),
      pair(ResourceType.STIX_BUNDLE, Action.PROCESS));

  private record ResourceTypeActionPair(ResourceType resource, Action action) {}

  private static ResourceTypeActionPair pair(ResourceType r, Action a) {
    return new ResourceTypeActionPair(r, a);
  }

  private final Set<ResourceTypeActionPair> pairs;
  @Getter private final Capability parent;
  @Getter private final Set<CapabilityScope> scopes;
  @Getter private final CapabilityGroup group;
  @Getter private final boolean hidden;
  @Getter private final boolean checkable;

  /** Root capability with explicit scope(s) and group. */
  Capability(
      Capability parent,
      CapabilityGroup group,
      Set<CapabilityScope> scopes,
      ResourceTypeActionPair... pairs) {
    this.parent = parent;
    this.group = group;
    this.scopes = scopes;
    this.hidden = false;
    this.checkable = true;
    this.pairs = Set.of(pairs);
  }

  /** Root capability with explicit hidden and checkable flags. */
  Capability(
      Capability parent,
      CapabilityGroup group,
      boolean hidden,
      boolean checkable,
      Set<CapabilityScope> scopes,
      ResourceTypeActionPair... pairs) {
    this.parent = parent;
    this.group = group;
    this.scopes = scopes;
    this.hidden = hidden;
    this.checkable = checkable;
    this.pairs = Set.of(pairs);
  }

  /** Child capability — inherits scopes and group from its parent. */
  Capability(Capability parent, ResourceTypeActionPair... pairs) {
    if (parent == null) {
      throw new IllegalStateException(
          "Child capability must have a parent. Use the scoped constructor for root capabilities.");
    }
    this.parent = parent;
    this.scopes = parent.scopes;
    this.group = parent.group;
    this.hidden = false;
    this.checkable = true;
    this.pairs = Set.of(pairs);
  }

  /** Child capability with explicit hidden flag — inherits scopes and group from its parent. */
  Capability(Capability parent, boolean hidden, ResourceTypeActionPair... pairs) {
    if (parent == null) {
      throw new IllegalStateException(
          "Child capability must have a parent. Use the scoped constructor for root capabilities.");
    }
    this.parent = parent;
    this.scopes = parent.scopes;
    this.group = parent.group;
    this.hidden = hidden;
    this.checkable = true;
    this.pairs = Set.of(pairs);
  }

  private static final Map<ResourceTypeActionPair, Capability> LOOKUP =
      Arrays.stream(values())
          .flatMap(cap -> cap.pairs.stream().map(k -> entry(k, cap)))
          .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

  public static Optional<Capability> of(ResourceType resource, Action action) {
    return Optional.ofNullable(LOOKUP.get(new ResourceTypeActionPair(resource, action)));
  }

  public static Set<Capability> resolveWithParents(Set<Capability> capabilities) {
    Set<Capability> result = new HashSet<>();
    for (Capability capability : capabilities) {
      Capability current = capability;
      while (current != null && result.add(current)) {
        current = current.getParent();
      }
    }
    return result;
  }

  public static void validateForPlatformRole(Set<Capability> capabilities) {
    validateScope(capabilities, CapabilityScope.PLATFORM);
  }

  public static void validateForTenantRole(Set<Capability> capabilities) {
    validateScope(capabilities, CapabilityScope.TENANT);
  }

  private static void validateScope(Set<Capability> capabilities, CapabilityScope requiredScope) {
    Set<Capability> invalid =
        capabilities.stream()
            .filter(c -> !c.scopes.contains(requiredScope))
            .collect(Collectors.toSet());
    if (!invalid.isEmpty()) {
      throw new IllegalArgumentException(
          "Capabilities " + invalid + " are not allowed for scope " + requiredScope);
    }
  }
}
