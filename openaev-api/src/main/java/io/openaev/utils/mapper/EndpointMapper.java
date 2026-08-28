package io.openaev.utils.mapper;

import static io.openaev.database.model.Endpoint.*;
import static io.openaev.utils.AgentUtils.getPrimaryAgents;
import static java.util.Collections.emptySet;

import io.openaev.database.model.Asset;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Tag;
import io.openaev.rest.asset.endpoint.form.EndpointOutput;
import io.openaev.rest.asset.endpoint.form.EndpointOverviewOutput;
import io.openaev.rest.asset.endpoint.form.EndpointSimple;
import io.openaev.rest.asset.endpoint.output.EndpointTargetOutput;
import java.util.Arrays;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;

/**
 * Mapper component for converting Endpoint entities to output DTOs.
 *
 * <p>Provides methods for transforming endpoint domain objects into various API response formats,
 * including output, simple, target, and overview representations. Also includes utility methods for
 * sanitizing IP and MAC address data.
 *
 * @see io.openaev.database.model.Endpoint
 * @see io.openaev.rest.asset.endpoint.form.EndpointOutput
 */
@Component
@RequiredArgsConstructor
public class EndpointMapper {

  final AgentMapper agentMapper;

  /**
   * Converts an endpoint to a standard output DTO.
   *
   * <p>Includes primary agents, platform, architecture, and tag information.
   *
   * @param endpoint the endpoint to convert
   * @return the endpoint output DTO
   */
  public EndpointOutput toEndpointOutput(Endpoint endpoint) {
    return EndpointOutput.builder()
        .id(endpoint.getId())
        .name(endpoint.getName())
        .type(endpoint.getType())
        .externalReference(endpoint.getExternalReference())
        .agents(agentMapper.toAgentOutputs(getPrimaryAgents(endpoint)))
        .platform(endpoint.getPlatform())
        .arch(endpoint.getArch())
        .tags(endpoint.getTags().stream().map(Tag::getId).collect(Collectors.toSet()))
        .category(endpoint.getCategory())
        .subcategory(endpoint.getSubcategory())
        .criticality(endpoint.getCriticality())
        .internetFacing(endpoint.getInternetFacing())
        .cloudProvider(endpoint.getCloudProvider())
        .cloudNativeType(endpoint.getCloudNativeType())
        .cloudRegion(endpoint.getCloudRegion())
        .linkedPerson(endpoint.getLinkedPerson())
        .build();
  }

  /**
   * Converts ANY asset to the inventory output DTO. Endpoints keep their full representation
   * (agents, platform, arch); every other asset type (AI targets, identities, cloud / web / network
   * / generic assets) is mapped from the shared {@link Asset} fields with no agents and no
   * platform/arch, so the unified asset inventory can list all categories side by side.
   */
  public EndpointOutput toAssetOutput(Asset asset) {
    // Unproxy before the instanceof so a lazily-loaded Asset proxy still reveals the Endpoint
    // subtype (otherwise endpoints would lose their agents/platform in the unified list).
    if (Hibernate.unproxy(asset) instanceof Endpoint endpoint) {
      return toEndpointOutput(endpoint);
    }
    return EndpointOutput.builder()
        .id(asset.getId())
        .name(asset.getName())
        .type(asset.getType())
        .externalReference(asset.getExternalReference())
        .agents(emptySet())
        .tags(asset.getTags().stream().map(Tag::getId).collect(Collectors.toSet()))
        .category(asset.getCategory())
        .subcategory(asset.getSubcategory())
        .criticality(asset.getCriticality())
        .internetFacing(asset.getInternetFacing())
        .build();
  }

  /**
   * Converts ANY asset to the overview DTO used by the unified asset detail page. Endpoints get
   * their full representation (agents, platform, arch, EOL); other asset types map from the shared
   * {@link Asset} fields, and AI targets additionally expose their (non-secret) connection metadata
   * so a single detail page can render every category with the relevant sections.
   */
  public EndpointOverviewOutput toAssetOverviewOutput(Asset asset) {
    if (Hibernate.unproxy(asset) instanceof Endpoint endpoint) {
      return toEndpointOverviewOutput(endpoint);
    }
    return EndpointOverviewOutput.builder()
        .id(asset.getId())
        .name(asset.getName())
        .description(asset.getDescription())
        .hostname(asset.getHostname())
        .agents(emptySet())
        .tags(asset.getTags().stream().map(Tag::getId).collect(Collectors.toSet()))
        .category(asset.getCategory())
        .subcategory(asset.getSubcategory())
        .criticality(asset.getCriticality())
        .internetFacing(asset.getInternetFacing())
        .metadata(asset.getMetadata())
        // AI target connection metadata (token intentionally omitted)
        .aiTargetProvider(asset.getAiTargetProvider())
        .aiTargetModality(asset.getAiTargetModality())
        .aiTargetEndpoint(asset.getAiTargetEndpoint())
        .aiTargetModel(asset.getAiTargetModel())
        .aiTargetSystemPrompt(asset.getAiTargetSystemPrompt())
        .build();
  }

  /**
   * Converts an asset to a simplified endpoint DTO.
   *
   * @param asset the asset to convert
   * @return the simplified endpoint DTO
   */
  public EndpointSimple toEndpointSimple(Asset asset) {
    // Unproxy before the instanceof so a lazily-loaded Asset proxy still reveals the Endpoint
    // subtype (otherwise endpoints would lose their platform in list chips).
    Endpoint endpoint = Hibernate.unproxy(asset) instanceof Endpoint e ? e : null;
    return EndpointSimple.builder()
        .id(asset.getId())
        .name(asset.getName())
        .type(asset.getType())
        .category(asset.getCategory() != null ? asset.getCategory().name() : null)
        .platform(
            endpoint != null && endpoint.getPlatform() != null
                ? endpoint.getPlatform().name()
                : null)
        .build();
  }

  /**
   * Converts an endpoint to a target-focused output DTO.
   *
   * <p>Used for displaying endpoint information in targeting contexts.
   *
   * @param endpoint the endpoint to convert
   * @return the endpoint target output DTO
   */
  public EndpointTargetOutput toEndpointTargetOutput(Endpoint endpoint) {
    return EndpointTargetOutput.builder()
        .id(endpoint.getId())
        .hostname(endpoint.getHostname())
        .seenIp(endpoint.getSeenIp())
        .ips(
            endpoint.getIps() != null
                ? new HashSet<>(Arrays.asList(setIps(endpoint.getIps())))
                : emptySet())
        .agents(agentMapper.toAgentOutputs(endpoint.getAgents()))
        .build();
  }

  /**
   * Converts an endpoint to a comprehensive overview DTO.
   *
   * <p>Includes all endpoint details including IPs, MAC addresses, agents, and EOL status.
   *
   * @param endpoint the endpoint to convert
   * @return the endpoint overview output DTO
   */
  public EndpointOverviewOutput toEndpointOverviewOutput(Endpoint endpoint) {
    return EndpointOverviewOutput.builder()
        .id(endpoint.getId())
        .name(endpoint.getName())
        .description(endpoint.getDescription())
        .hostname(endpoint.getHostname())
        .url(endpoint.getUrl())
        .platform(endpoint.getPlatform())
        .arch(endpoint.getArch())
        .seenIp(endpoint.getSeenIp())
        .ips(
            endpoint.getIps() != null
                ? new HashSet<>(Arrays.asList(setIps(endpoint.getIps())))
                : emptySet())
        .macAddresses(
            endpoint.getMacAddresses() != null
                ? new HashSet<>(Arrays.asList(setMacAddresses(endpoint.getMacAddresses())))
                : emptySet())
        .agents(agentMapper.toAgentOutputs(getPrimaryAgents(endpoint)))
        .tags(endpoint.getTags().stream().map(Tag::getId).collect(Collectors.toSet()))
        .category(endpoint.getCategory())
        .subcategory(endpoint.getSubcategory())
        .criticality(endpoint.getCriticality())
        .internetFacing(endpoint.getInternetFacing())
        .cloudProvider(endpoint.getCloudProvider())
        .cloudNativeType(endpoint.getCloudNativeType())
        .cloudRegion(endpoint.getCloudRegion())
        .linkedPerson(endpoint.getLinkedPerson())
        .metadata(endpoint.getMetadata())
        .isEol(endpoint.isEoL())
        .build();
  }

  /**
   * Sanitizes and normalizes MAC addresses.
   *
   * <p>Converts to lowercase, removes formatting characters, and filters out known invalid MAC
   * addresses.
   *
   * <p>Only canonical 6-byte Ethernet addresses are kept. Interface enumeration reports an address
   * of whatever length the adapter declares, and tunnel pseudo-interfaces (Teredo reports the
   * 8-byte {@code 00:00:00:00:00:00:00:E0}) carry the same value on every Windows host. Since MAC
   * overlap is what identifies an endpoint at agent registration, keeping those would merge
   * unrelated assets. Discarding a non-Ethernet address costs nothing: the external reference is
   * mandatory on every registration and is the primary matcher, MAC overlap only being the
   * fallback.
   *
   * @param macAddresses the MAC addresses to sanitize
   * @return sanitized array of MAC addresses
   */
  public static String[] setMacAddresses(String[] macAddresses) {
    if (macAddresses == null) {
      return new String[0];
    } else {
      return Arrays.stream(macAddresses)
          .map(macAddress -> macAddress.toLowerCase().replaceAll(REGEX_MAC_ADDRESS, ""))
          .filter(macAddress -> macAddress.length() == MAC_ADDRESS_LENGTH)
          .filter(macAddress -> !BAD_MAC_ADDRESS.contains(macAddress))
          .distinct()
          .toArray(String[]::new);
    }
  }

  /**
   * Sanitizes and normalizes IP addresses.
   *
   * <p>Converts to lowercase and filters out known invalid IP addresses.
   *
   * @param ips the IP addresses to sanitize
   * @return sanitized array of IP addresses
   */
  public static String[] setIps(String[] ips) {
    if (ips == null) {
      return new String[0];
    } else {
      return Arrays.stream(ips)
          .map(String::toLowerCase)
          .filter(ip -> !BAD_IP_ADDRESSES.contains(ip))
          .distinct()
          .toArray(String[]::new);
    }
  }

  /**
   * Merges two address arrays with deduplication.
   *
   * @param array1 the first array (may be null)
   * @param array2 the second array (may be null)
   * @return merged array with duplicates removed
   */
  public static String[] mergeAddressArrays(String[] array1, String[] array2) {
    if (array1 == null) {
      return array2;
    }
    if (array2 == null) {
      return array1;
    }
    return Stream.concat(Arrays.stream(array1), Arrays.stream(array2))
        .distinct()
        .toArray(String[]::new);
  }
}
