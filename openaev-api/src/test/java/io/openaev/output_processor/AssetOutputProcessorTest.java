package io.openaev.output_processor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Inject;
import io.openaev.rest.asset.endpoint.form.EndpointInput;
import io.openaev.rest.inject.service.ContractOutputContext;
import io.openaev.rest.inject.service.ExecutionProcessingContext;
import io.openaev.rest.tag.TagService;
import io.openaev.service.EndpointService;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AssetOutputProcessorTest {
  private final EndpointService endpointService = mock(EndpointService.class);
  private final TagService tagService = mock(TagService.class);
  private final AssetOutputProcessor processor =
      new AssetOutputProcessor(endpointService, tagService);
  private final ObjectMapper objectMapper = new ObjectMapper();

  private ExecutionProcessingContext executionContext;
  private ContractOutputContext contractOutputContext;

  @BeforeEach
  void setUp() {
    executionContext = mock(ExecutionProcessingContext.class);
    contractOutputContext = mock(ContractOutputContext.class);

    Inject inject = mock(Inject.class);
    when(executionContext.inject()).thenReturn(inject);
    when(tagService.findOrCreateTagsFromNames(any())).thenReturn(Set.of());
  }

  @Test
  @DisplayName("should return true for valid Endpoint node")
  void shouldReturnTrueForValidEndpointNode() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            """
        {"name":"Asset A","type":"Endpoint","external_reference":"https://ref/a",
         "extended_attributes":{"platform":"Linux","arch":"x86_64"}}
        """);
    assertTrue(processor.validate(node));
  }

  @Test
  @DisplayName("should return false when name is missing")
  void shouldReturnFalseWhenNameMissing() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            """
        {"type":"Endpoint","external_reference":"https://ref/a",
         "extended_attributes":{"platform":"Linux","arch":"x86_64"}}
        """);
    assertFalse(processor.validate(node));
  }

  @Test
  @DisplayName("should return false when type is missing")
  void shouldReturnFalseWhenTypeMissing() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            """
        {"name":"Asset A","external_reference":"https://ref/a",
         "extended_attributes":{"platform":"Linux","arch":"x86_64"}}
        """);
    assertFalse(processor.validate(node));
  }

  @Test
  @DisplayName("should return false when externalReference is missing")
  void shouldReturnFalseWhenExternalReferenceMissing() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            """
        {"name":"Asset A","type":"Endpoint",
         "extended_attributes":{"platform":"Linux","arch":"x86_64"}}
        """);
    assertFalse(processor.validate(node));
  }

  @Test
  @DisplayName("should return false for Endpoint when platform is missing")
  void shouldReturnFalseForEndpointWhenPlatformMissing() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            """
        {"name":"Asset A","type":"Endpoint","external_reference":"https://ref/a",
         "extended_attributes":{"arch":"x86_64"}}
        """);
    assertFalse(processor.validate(node));
  }

  @Test
  @DisplayName("should return false for Endpoint when arch is missing")
  void shouldReturnFalseForEndpointWhenArchMissing() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            """
        {"name":"Asset A","type":"Endpoint","external_reference":"https://ref/a",
         "extended_attributes":{"platform":"Linux"}}
        """);
    assertFalse(processor.validate(node));
  }

  @Test
  @DisplayName("should return false for unknown asset type")
  void shouldReturnFalseForUnknownAssetType() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            """
        {"name":"Asset A","type":"Unknown","external_reference":"https://ref/a",
         "extended_attributes":{}}
        """);
    assertFalse(processor.validate(node));
  }

  @Test
  @DisplayName("should skip invalid asset node")
  void shouldSkipInvalidAssetNode() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            """
        [{"type":"Endpoint","extended_attributes":{"platform":"Linux","arch":"x86_64"}}]
        """);
    processor.process(executionContext, contractOutputContext, node);
    verifyNoInteractions(endpointService);
  }

  @Test
  @DisplayName("should create endpoint when it does not exist")
  void shouldCreateEndpointWhenNotExisting() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            """
        [{"name":"Asset A","type":"Endpoint","external_reference":"https://ref/a","tags":[],
          "extended_attributes":{"platform":"Linux","arch":"x86_64"}}]
        """);
    Endpoint created = mock(Endpoint.class);
    when(created.getId()).thenReturn("endpoint-id");
    when(endpointService.findExistingEndpoint(any())).thenReturn(Optional.empty());
    when(endpointService.createEndpoint(any(EndpointInput.class))).thenReturn(created);

    processor.process(executionContext, contractOutputContext, node);

    verify(endpointService).createEndpoint(any(EndpointInput.class));
  }

  @Test
  @DisplayName("should not create endpoint when it already exists")
  void shouldNotCreateEndpointWhenAlreadyExisting() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            """
        [{"name":"Asset A","type":"Endpoint","external_reference":"https://ref/a","tags":[],
          "extended_attributes":{"platform":"Linux","arch":"x86_64"}}]
        """);
    Endpoint existing = mock(Endpoint.class);
    when(existing.getId()).thenReturn("existing-id");
    when(endpointService.findExistingEndpoint(any())).thenReturn(Optional.of(existing));

    processor.process(executionContext, contractOutputContext, node);

    verify(endpointService, never()).createEndpoint(any(EndpointInput.class));
  }

  @Test
  @DisplayName("should process asset node with tags and call tagService")
  void shouldProcessAssetNodeWithTags() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            """
        [{"name":"Asset B","type":"Endpoint","external_reference":"https://ref/b","tags":["tag1","tag2"],
          "extended_attributes":{"platform":"Linux","arch":"x86_64"}}]
        """);
    Endpoint created = mock(Endpoint.class);
    when(created.getId()).thenReturn("endpoint-id");
    when(endpointService.findExistingEndpoint(any())).thenReturn(Optional.empty());
    when(endpointService.createEndpoint(any(EndpointInput.class))).thenReturn(created);
    when(tagService.findOrCreateTagsFromNames(any())).thenReturn(Set.of());

    processor.process(executionContext, contractOutputContext, node);

    verify(tagService).findOrCreateTagsFromNames(any());
    verify(endpointService).createEndpoint(any(EndpointInput.class));
  }

  @Test
  @DisplayName("should process asset node with all extended attributes")
  void shouldProcessAssetNodeWithAllExtendedAttributes() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            """
        [{"name":"Asset C","type":"Endpoint","external_reference":"https://ref/c","tags":[],
          "extended_attributes":{"platform":"Linux","arch":"x86_64","hostname":"hostC","ip_addresses":["192.168.1.1"],"mac_addresses":["00:11:22:33:44:55"],"end_of_life":"true"}}]
        """);
    Endpoint created = mock(Endpoint.class);
    when(created.getId()).thenReturn("endpoint-id");
    when(endpointService.findExistingEndpoint(any())).thenReturn(Optional.empty());
    when(endpointService.createEndpoint(any(EndpointInput.class))).thenReturn(created);

    processor.process(executionContext, contractOutputContext, node);

    ArgumentCaptor<EndpointInput> captor = ArgumentCaptor.forClass(EndpointInput.class);
    verify(endpointService).createEndpoint(captor.capture());
    EndpointInput endpointInput = captor.getValue();
    assertTrue("hostC".equals(endpointInput.getHostname()));
    assertTrue(endpointInput.getIps() != null && endpointInput.getIps().length == 1);
    assertTrue("192.168.1.1".equals(endpointInput.getIps()[0]));
    assertTrue(
        endpointInput.getMacAddresses() != null && endpointInput.getMacAddresses().length == 1);
    assertTrue("00:11:22:33:44:55".equals(endpointInput.getMacAddresses()[0]));
    assertTrue(endpointInput.isEol());
  }

  @Test
  @DisplayName("should return false for security platform asset type")
  void shouldReturnFalseForSecurityPlatformAssetType() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            """
        {"name":"Asset D","type":"SecurityPlatform","external_reference":"https://ref/d",
         "extended_attributes":{}}
        """);
    assertTrue(processor.validate(node));
  }

  @Test
  @DisplayName("should handle malformed JSON gracefully")
  void shouldHandleMalformedJsonGracefully() throws Exception {
    JsonNode node = objectMapper.readTree("{}\n");
    assertFalse(processor.validate(node));
    processor.process(executionContext, contractOutputContext, node);
    verifyNoInteractions(endpointService);
  }

  @Test
  @DisplayName("should skip asset node with unexpected input type")
  void shouldSkipAssetNodeWithUnexpectedInputType() throws Exception {
    JsonNode node = objectMapper.readTree("42"); // not an object or array
    assertFalse(processor.validate(node));
    processor.process(executionContext, contractOutputContext, node);
    verifyNoInteractions(endpointService);
  }
}
