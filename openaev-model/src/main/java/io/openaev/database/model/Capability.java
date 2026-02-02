package io.openaev.database.model;

import static java.util.Map.entry;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;

public enum Capability {

  // Superuser
  BYPASS(null, pair(null, null)),

  // Assessment
  ACCESS_ASSESSMENT(
      null,
      pair(ResourceType.SCENARIO, Action.READ),
      pair(ResourceType.SCENARIO, Action.SEARCH),
      pair(ResourceType.SIMULATION, Action.READ),
      pair(ResourceType.SIMULATION, Action.SEARCH),
      pair(ResourceType.ATOMIC_TESTING, Action.READ),
      pair(ResourceType.ATOMIC_TESTING, Action.SEARCH)),
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
      pair(ResourceType.ATOMIC_TESTING, Action.CREATE)),
  DELETE_ASSESSMENT(
      MANAGE_ASSESSMENT,
      pair(ResourceType.SCENARIO, Action.DELETE),
      pair(ResourceType.SIMULATION, Action.DELETE),
      pair(ResourceType.ATOMIC_TESTING, Action.DELETE)),
  LAUNCH_ASSESSMENT(
      MANAGE_ASSESSMENT,
      pair(ResourceType.SCENARIO, Action.LAUNCH),
      pair(ResourceType.SIMULATION, Action.LAUNCH),
      pair(ResourceType.ATOMIC_TESTING, Action.LAUNCH)),

  // Teams & Players
  MANAGE_TEAMS_AND_PLAYERS(
      null,
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
      null, pair(ResourceType.PAYLOAD, Action.READ), pair(ResourceType.PAYLOAD, Action.SEARCH)),
  MANAGE_PAYLOADS(
      ACCESS_PAYLOADS,
      pair(ResourceType.PAYLOAD, Action.WRITE),
      pair(ResourceType.PAYLOAD, Action.CREATE),
      pair(ResourceType.PAYLOAD, Action.DUPLICATE)),
  DELETE_PAYLOADS(MANAGE_PAYLOADS, pair(ResourceType.PAYLOAD, Action.DELETE)),

  // Dashboards
  ACCESS_DASHBOARDS(
      null, pair(ResourceType.DASHBOARD, Action.READ), pair(ResourceType.DASHBOARD, Action.SEARCH)),
  MANAGE_DASHBOARDS(
      ACCESS_DASHBOARDS,
      pair(ResourceType.DASHBOARD, Action.WRITE),
      pair(ResourceType.DASHBOARD, Action.CREATE)),
  DELETE_DASHBOARDS(MANAGE_DASHBOARDS, pair(ResourceType.DASHBOARD, Action.DELETE)),

  // Findings
  ACCESS_FINDINGS(
      null, pair(ResourceType.FINDING, Action.READ), pair(ResourceType.FINDING, Action.SEARCH)),
  MANAGE_FINDINGS(
      ACCESS_FINDINGS,
      pair(ResourceType.FINDING, Action.WRITE),
      pair(ResourceType.FINDING, Action.CREATE)),
  DELETE_FINDINGS(MANAGE_FINDINGS, pair(ResourceType.FINDING, Action.DELETE)),

  // Documents
  ACCESS_DOCUMENTS(
      null, pair(ResourceType.DOCUMENT, Action.READ), pair(ResourceType.DOCUMENT, Action.SEARCH)),
  MANAGE_DOCUMENTS(
      ACCESS_DOCUMENTS,
      pair(ResourceType.DOCUMENT, Action.WRITE),
      pair(ResourceType.DOCUMENT, Action.CREATE)),
  DELETE_DOCUMENTS(MANAGE_DOCUMENTS, pair(ResourceType.DOCUMENT, Action.DELETE)),

  // Channels
  ACCESS_CHANNELS(
      null, pair(ResourceType.CHANNEL, Action.READ), pair(ResourceType.CHANNEL, Action.SEARCH)),
  MANAGE_CHANNELS(
      ACCESS_CHANNELS,
      pair(ResourceType.CHANNEL, Action.WRITE),
      pair(ResourceType.CHANNEL, Action.CREATE)),
  DELETE_CHANNELS(MANAGE_CHANNELS, pair(ResourceType.CHANNEL, Action.DELETE)),

  // Challenges
  ACCESS_CHALLENGES(
      null, pair(ResourceType.CHALLENGE, Action.READ), pair(ResourceType.CHALLENGE, Action.SEARCH)),
  MANAGE_CHALLENGES(
      ACCESS_CHALLENGES,
      pair(ResourceType.CHALLENGE, Action.WRITE),
      pair(ResourceType.CHALLENGE, Action.CREATE)),
  DELETE_CHALLENGES(MANAGE_CHALLENGES, pair(ResourceType.CHALLENGE, Action.DELETE)),

  // Lessons Learned
  ACCESS_LESSONS_LEARNED(
      null,
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
      pair(ResourceType.SECURITY_PLATFORM, Action.READ),
      pair(ResourceType.SECURITY_PLATFORM, Action.SEARCH)),
  MANAGE_SECURITY_PLATFORMS(
      ACCESS_SECURITY_PLATFORMS,
      pair(ResourceType.SECURITY_PLATFORM, Action.WRITE),
      pair(ResourceType.SECURITY_PLATFORM, Action.CREATE)),
  DELETE_SECURITY_PLATFORMS(
      MANAGE_SECURITY_PLATFORMS, pair(ResourceType.SECURITY_PLATFORM, Action.DELETE)),
  // Platform Settings
  //
  ACCESS_PLATFORM_SETTINGS(
      null,
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
  MANAGE_STIX_BUNDLE(null, pair(ResourceType.STIX_BUNDLE, Action.PROCESS));

  private record ResourceTypeActionPair(ResourceType resource, Action action) {}

  private static ResourceTypeActionPair pair(ResourceType r, Action a) {
    return new ResourceTypeActionPair(r, a);
  }

  private final Set<ResourceTypeActionPair> pairs;

  @Getter private final Capability parent;

  Capability(Capability parent, ResourceTypeActionPair... pairs) {
    this.parent = parent;
    this.pairs = Set.of(pairs);
  }

  private static final Map<ResourceTypeActionPair, Capability> LOOKUP =
      Arrays.stream(values())
          .flatMap(cap -> cap.pairs.stream().map(k -> entry(k, cap)))
          .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

  public static Optional<Capability> of(ResourceType resource, Action action) {
    return Optional.ofNullable(LOOKUP.get(new ResourceTypeActionPair(resource, action)));
  }
}
