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
import java.util.Arrays;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EndpointMapper {

  final AgentMapper agentMapper;

  public EndpointOutput toEndpointOutput(Endpoint endpoint) {
    return EndpointOutput.builder()
        .id(endpoint.getId())
        .name(endpoint.getName())
        .type(endpoint.getType())
        .agents(agentMapper.toAgentOutputs(getPrimaryAgents(endpoint)))
        .platform(endpoint.getPlatform())
        .arch(endpoint.getArch())
        .tags(endpoint.getTags().stream().map(Tag::getId).collect(Collectors.toSet()))
        .build();
  }

  public EndpointSimple toEndpointSimple(Asset asset) {
    return EndpointSimple.builder().id(asset.getId()).name(asset.getName()).build();
  }

  public EndpointOverviewOutput toEndpointOverviewOutput(Endpoint endpoint) {
    return EndpointOverviewOutput.builder()
        .id(endpoint.getId())
        .name(endpoint.getName())
        .description(endpoint.getDescription())
        .hostname(endpoint.getHostname())
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
        .isEol(endpoint.isEoL())
        .build();
  }

  public static String[] setMacAddresses(String[] macAddresses) {
    if (macAddresses == null) {
      return new String[0];
    } else {
      return Arrays.stream(macAddresses)
          .map(macAddress -> macAddress.toLowerCase().replaceAll(REGEX_MAC_ADDRESS, ""))
          .filter(macAddress -> !BAD_MAC_ADDRESS.contains(macAddress))
          .distinct()
          .toArray(String[]::new);
    }
  }

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
