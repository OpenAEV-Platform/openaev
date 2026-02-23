package io.openaev.output_processor;

import static io.openaev.database.model.AssetType.Values.ENDPOINT_TYPE;
import static io.openaev.database.model.AssetType.Values.SECURITY_PLATFORM_TYPE;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.*;
import io.openaev.rest.asset.endpoint.form.EndpointInput;
import io.openaev.rest.inject.service.ContractOutputContext;
import io.openaev.rest.inject.service.ExecutionProcessingContext;
import io.openaev.rest.tag.TagService;
import io.openaev.service.EndpointService;
import java.time.Instant;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AssetOutputProcessor extends AbstractOutputProcessor {

  private static final String NAME = "name";
  private static final String TYPE = "type";
  private static final String DESCRIPTION = "description";
  private static final String EXTERNAL_REFERENCE = "externalReference";
  private static final String TAGS = "tags";
  private static final String EXTENDED_ATTRIBUTES = "extendedAttributes";
  private static final String PLATFORM = "platform";
  private static final String ARCH = "arch";
  private static final String IP_ADDRESSES = "ip_addresses";
  private static final String HOSTNAME = "hostname";
  private static final String MAC_ADDRESSES = "mac_addresses";
  private static final String END_OF_LIFE = "end_of_life";
  private final EndpointService endpointService;
  private final TagService tagService;

  public AssetOutputProcessor(EndpointService endpointService, TagService tagService) {
    super(
        ContractOutputType.Asset,
        ContractOutputTechnicalType.Object,
        List.of(
            new ContractOutputField(NAME, ContractOutputTechnicalType.Text, true),
            new ContractOutputField(TYPE, ContractOutputTechnicalType.Text, true),
            new ContractOutputField(DESCRIPTION, ContractOutputTechnicalType.Text, false),
            new ContractOutputField(EXTERNAL_REFERENCE, ContractOutputTechnicalType.Text, true),
            new ContractOutputField(TAGS, ContractOutputTechnicalType.Text, true),
            new ContractOutputField(EXTENDED_ATTRIBUTES, ContractOutputTechnicalType.Object, true)),
        false);
    this.endpointService = endpointService;
    this.tagService = tagService;
  }

  @Override
  public boolean validate(JsonNode jsonNode) {
    if (!jsonNode.hasNonNull(NAME)
        || !jsonNode.hasNonNull(TYPE)
        || !jsonNode.hasNonNull(EXTERNAL_REFERENCE)) {
      return false;
    }
    String type = jsonNode.path(TYPE).asText();
    return switch (type) {
      case ENDPOINT_TYPE -> validateEndpoint(jsonNode.path(EXTENDED_ATTRIBUTES));
      default -> false;
    };
  }

  private boolean validateEndpoint(JsonNode extended) {
    return extended.hasNonNull(PLATFORM) && extended.hasNonNull(ARCH);
  }

  @Override
  public void process(
      ExecutionProcessingContext executionContext,
      ContractOutputContext contractOutputContext,
      JsonNode structuredOutputNode) {
    for (JsonNode assetNode : structuredOutputNode) {
      if (!validate(assetNode)) {
        log.warn("Invalid asset node, skipping: {}", assetNode);
        continue;
      }
      String type = assetNode.path(TYPE).asText();
      switch (type) {
        case ENDPOINT_TYPE -> processEndpoint(executionContext, assetNode);
        case SECURITY_PLATFORM_TYPE -> log.info("SecurityPlatform not yet supported, skipping");
        default -> log.warn("Unknown asset type '{}', skipping", type);
      }
    }
  }

  /**
   * Processes a single endpoint asset node. Creates the endpoint if it does not already exist.
   *
   * @param assetNode The JSON node representing the endpoint asset
   */
  private void processEndpoint(ExecutionProcessingContext executionContext, JsonNode assetNode) {
    EndpointInput input = buildEndpointInput(assetNode);
    Optional<Endpoint> existing = endpointService.findExistingEndpoint(input);
    if (existing.isPresent()) {
      log.info("Endpoint already existent: {} (id={})", input.getName(), existing.get().getId());
      addTrace(
          executionContext,
          ExecutionTraceStatus.INFO,
          "Endpoint already existent: " + input.getName() + " (id=" + existing.get().getId() + ")");
      return;
    }
    Endpoint created = endpointService.createEndpoint(input);
    log.info("Created endpoint: {} (id={})", input.getName(), created.getId());
    addTrace(
        executionContext,
        ExecutionTraceStatus.INFO,
        "Created endpoint: " + input.getName() + " (id=" + created.getId() + ")");
  }

  /**
   * Builds an EndpointInput object from a JSON asset node, extracting all relevant fields.
   *
   * @param assetNode The JSON node representing the endpoint asset
   * @return EndpointInput populated with asset data
   */
  private EndpointInput buildEndpointInput(JsonNode assetNode) {
    JsonNode extended = assetNode.path(EXTENDED_ATTRIBUTES);

    EndpointInput input = new EndpointInput();

    // AssetInput fields
    input.setName(assetNode.path(NAME).asText());
    input.setDescription(assetNode.path(DESCRIPTION).asText());
    input.setExternalReference(assetNode.path(EXTERNAL_REFERENCE).asText());

    // Tags
    Set<String> tagNames = new HashSet<>();
    for (JsonNode tag : assetNode.path(TAGS)) {
      tagNames.add(tag.asText());
    }
    input.setTagIds(
        tagService.findOrCreateTagsFromNames(tagNames).stream().map(Tag::getId).toList());

    // Platform and arch
    input.setPlatform(Endpoint.PLATFORM_TYPE.fromString(extended.path(PLATFORM).asText()));
    input.setArch(Endpoint.PLATFORM_ARCH.fromString(extended.path(ARCH).asText()));

    // IPs
    JsonNode ipNode = extended.path(IP_ADDRESSES);
    input.setIps(toStringArray(ipNode));

    // Hostname
    String hostname = extended.path(HOSTNAME).asText();
    input.setHostname(hostname);

    // MAC addresses
    JsonNode macNode = extended.path(MAC_ADDRESSES);
    input.setMacAddresses(toStringArray(macNode));

    // EoL
    input.setEol(extended.path(END_OF_LIFE).asBoolean(false));

    return input;
  }

  private String[] toStringArray(JsonNode node) {
    if (node.isArray()) {
      List<String> values = new ArrayList<>();
      node.forEach(n -> values.add(n.asText()));
      return values.toArray(new String[0]);
    }

    if (!node.isMissingNode() && !node.isNull()) {
      return new String[] {node.asText()};
    }

    return new String[0];
  }

  private void addTrace(
      ExecutionProcessingContext executionContext,
      ExecutionTraceStatus traceStatus,
      String message) {
    executionContext
        .inject()
        .getStatus()
        .ifPresent(
            status -> {
              ExecutionTrace trace =
                  new ExecutionTrace(
                      status,
                      traceStatus,
                      null,
                      message,
                      ExecutionTraceAction.EXECUTION,
                      null,
                      Instant.now());
              status.addTrace(trace);
            });
  }
}
