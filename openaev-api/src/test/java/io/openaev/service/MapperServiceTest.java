package io.openaev.service;

import static io.openaev.utils.StringUtils.duplicateString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReaderBuilder;
import io.openaev.IntegrationTest;
import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.Domain;
import io.openaev.database.model.ImportMapper;
import io.openaev.database.model.InjectImporter;
import io.openaev.database.model.Injector;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Tag;
import io.openaev.database.repository.EndpointRepository;
import io.openaev.database.repository.ImportMapperRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.mapper.form.*;
import io.openaev.rest.tag.TagService;
import io.openaev.utils.CsvType;
import io.openaev.utils.constants.Constants;
import io.openaev.utils.mockMapper.MockMapperUtils;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.openaev.utilstest.RabbitMQTestListener;
import jakarta.servlet.http.HttpServletResponse;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestExecutionListeners;

@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@ExtendWith(MockitoExtension.class)
public class MapperServiceTest extends IntegrationTest {

  @Mock private ImportMapperRepository importMapperRepository;
  @Mock private InjectorContractRepository injectorContractRepository;
  @Mock private EndpointRepository endpointRepository;
  @Mock private ObjectMapper objectMapper;
  @Mock private EndpointService endpointService;
  @Mock private TagService tagService;

  private MapperService mapperService;

  @BeforeEach
  void before() {
    // Injecting mocks into the controller
    mapperService =
        new MapperService(
            importMapperRepository,
            injectorContractRepository,
            endpointRepository,
            endpointService,
            tagService,
            objectMapper);
  }

  // -- SCENARIOS --

  @DisplayName("Test create a mapper")
  @Test
  void createMapper() throws Exception {
    // -- PREPARE --
    ImportMapper importMapper = MockMapperUtils.createImportMapper();
    ImportMapperAddInput importMapperInput = new ImportMapperAddInput();
    importMapperInput.setName(importMapper.getName());
    importMapperInput.setInjectTypeColumn(importMapper.getInjectTypeColumn());
    importMapperInput.setImporters(
        importMapper.getInjectImporters().stream()
            .map(
                injectImporter -> {
                  InjectImporterAddInput injectImporterAddInput = new InjectImporterAddInput();
                  injectImporterAddInput.setInjectTypeValue(injectImporter.getImportTypeValue());
                  injectImporterAddInput.setInjectorContractId(
                      injectImporter.getInjectorContract().getId());

                  injectImporterAddInput.setRuleAttributes(
                      injectImporter.getRuleAttributes().stream()
                          .map(
                              ruleAttribute -> {
                                RuleAttributeAddInput ruleAttributeAddInput =
                                    new RuleAttributeAddInput();
                                ruleAttributeAddInput.setName(ruleAttribute.getName());
                                ruleAttributeAddInput.setColumns(ruleAttribute.getColumns());
                                ruleAttributeAddInput.setDefaultValue(
                                    ruleAttribute.getDefaultValue());
                                ruleAttributeAddInput.setAdditionalConfig(
                                    ruleAttribute.getAdditionalConfig());
                                return ruleAttributeAddInput;
                              })
                          .toList());
                  return injectImporterAddInput;
                })
            .toList());
    when(importMapperRepository.save(any())).thenReturn(importMapper);
    // -- EXECUTE --
    ImportMapper importMapperResponse = mapperService.createAndSaveImportMapper(importMapperInput);

    // -- ASSERT --
    assertNotNull(importMapperResponse);
    assertEquals(importMapperResponse.getId(), importMapper.getId());
  }

  @DisplayName("Test duplicate a mapper")
  @Test
  void duplicateMapper() throws Exception {
    // -- PREPARE --
    ImportMapper importMapper = MockMapperUtils.createImportMapper();
    when(importMapperRepository.findById(any())).thenReturn(Optional.of(importMapper));
    ImportMapper importMapperSaved = MockMapperUtils.createImportMapper();
    when(importMapperRepository.save(any(ImportMapper.class))).thenReturn(importMapperSaved);

    // -- EXECUTE --
    ImportMapper response = mapperService.getDuplicateImportMapper(importMapper.getId());

    // -- ASSERT --
    ArgumentCaptor<ImportMapper> importMapperCaptor = ArgumentCaptor.forClass(ImportMapper.class);
    verify(importMapperRepository).save(importMapperCaptor.capture());

    ImportMapper capturedImportMapper = importMapperCaptor.getValue();
    // verify importMapper
    assertEquals(duplicateString(importMapper.getName()), capturedImportMapper.getName());
    assertEquals(importMapper.getInjectTypeColumn(), capturedImportMapper.getInjectTypeColumn());
    assertEquals(
        importMapper.getInjectImporters().size(), capturedImportMapper.getInjectImporters().size());
    // verify injectImporter
    assertEquals("", capturedImportMapper.getInjectImporters().get(0).getId());
    assertEquals(
        importMapper.getInjectImporters().get(0).getImportTypeValue(),
        capturedImportMapper.getInjectImporters().get(0).getImportTypeValue());
    assertEquals(
        importMapper.getInjectImporters().get(0).getRuleAttributes().size(),
        capturedImportMapper.getInjectImporters().get(0).getRuleAttributes().size());
    // verify ruleAttribute
    assertEquals(
        "", capturedImportMapper.getInjectImporters().get(0).getRuleAttributes().get(0).getId());
    assertEquals(
        importMapper.getInjectImporters().get(0).getRuleAttributes().get(0).getName(),
        capturedImportMapper.getInjectImporters().get(0).getRuleAttributes().get(0).getName());

    assertEquals(response.getId(), importMapperSaved.getId());
  }

  @DisplayName("Test update a specific mapper by using new rule attributes and new inject importer")
  @Test
  void updateSpecificMapperWithNewElements() throws Exception {
    // -- PREPARE --
    ImportMapper importMapper = MockMapperUtils.createImportMapper();
    ImportMapperUpdateInput importMapperInput = new ImportMapperUpdateInput();
    importMapperInput.setName(importMapper.getName());
    importMapperInput.setInjectTypeColumn(importMapper.getInjectTypeColumn());
    importMapperInput.setImporters(
        importMapper.getInjectImporters().stream()
            .map(
                injectImporter -> {
                  InjectImporterUpdateInput injectImporterUpdateInput =
                      new InjectImporterUpdateInput();
                  injectImporterUpdateInput.setInjectTypeValue(injectImporter.getImportTypeValue());
                  injectImporterUpdateInput.setInjectorContractId(
                      injectImporter.getInjectorContract().getId());

                  injectImporterUpdateInput.setRuleAttributes(
                      injectImporter.getRuleAttributes().stream()
                          .map(
                              ruleAttribute -> {
                                RuleAttributeUpdateInput ruleAttributeUpdateInput =
                                    new RuleAttributeUpdateInput();
                                ruleAttributeUpdateInput.setName(ruleAttribute.getName());
                                ruleAttributeUpdateInput.setColumns(ruleAttribute.getColumns());
                                ruleAttributeUpdateInput.setDefaultValue(
                                    ruleAttribute.getDefaultValue());
                                ruleAttributeUpdateInput.setAdditionalConfig(
                                    ruleAttribute.getAdditionalConfig());
                                return ruleAttributeUpdateInput;
                              })
                          .toList());
                  return injectImporterUpdateInput;
                })
            .toList());
    when(importMapperRepository.findById(any())).thenReturn(Optional.of(importMapper));
    when(importMapperRepository.save(any())).thenReturn(importMapper);
    when(injectorContractRepository.findAllById(any()))
        .thenReturn(
            importMapper.getInjectImporters().stream()
                .map(InjectImporter::getInjectorContract)
                .toList());

    // -- EXECUTE --
    ImportMapper response =
        mapperService.updateImportMapper(importMapper.getId(), importMapperInput);

    // -- ASSERT --
    assertNotNull(response);
    assertEquals(response.getId(), importMapper.getId());
  }

  @DisplayName(
      "Test update a specific mapper by creating rule attributes and updating new inject importer")
  @Test
  void updateSpecificMapperWithUpdatedInjectImporter() throws Exception {
    // -- PREPARE --
    ImportMapper importMapper = MockMapperUtils.createImportMapper();
    ImportMapperUpdateInput importMapperInput = new ImportMapperUpdateInput();
    importMapperInput.setName(importMapper.getName());
    importMapperInput.setInjectTypeColumn(importMapper.getInjectTypeColumn());
    importMapperInput.setImporters(
        importMapper.getInjectImporters().stream()
            .map(
                injectImporter -> {
                  InjectImporterUpdateInput injectImporterUpdateInput =
                      new InjectImporterUpdateInput();
                  injectImporterUpdateInput.setInjectTypeValue(injectImporter.getImportTypeValue());
                  injectImporterUpdateInput.setInjectorContractId(
                      injectImporter.getInjectorContract().getId());
                  injectImporterUpdateInput.setId(injectImporter.getId());

                  injectImporterUpdateInput.setRuleAttributes(
                      injectImporter.getRuleAttributes().stream()
                          .map(
                              ruleAttribute -> {
                                RuleAttributeUpdateInput ruleAttributeUpdateInput =
                                    new RuleAttributeUpdateInput();
                                ruleAttributeUpdateInput.setName(ruleAttribute.getName());
                                ruleAttributeUpdateInput.setColumns(ruleAttribute.getColumns());
                                ruleAttributeUpdateInput.setDefaultValue(
                                    ruleAttribute.getDefaultValue());
                                ruleAttributeUpdateInput.setAdditionalConfig(
                                    ruleAttribute.getAdditionalConfig());
                                return ruleAttributeUpdateInput;
                              })
                          .toList());
                  return injectImporterUpdateInput;
                })
            .toList());
    when(importMapperRepository.findById(any())).thenReturn(Optional.of(importMapper));
    when(importMapperRepository.save(any())).thenReturn(importMapper);
    when(injectorContractRepository.findAllById(any()))
        .thenReturn(
            importMapper.getInjectImporters().stream()
                .map(InjectImporter::getInjectorContract)
                .toList());

    // -- EXECUTE --
    ImportMapper response =
        mapperService.updateImportMapper(importMapper.getId(), importMapperInput);

    // -- ASSERT --
    assertNotNull(response);
    assertEquals(response.getId(), importMapper.getId());
  }

  @DisplayName(
      "Test update a specific mapper by updating rule attributes and updating inject importer")
  @Test
  void updateSpecificMapperWithUpdatedElements() throws Exception {
    // -- PREPARE --
    ImportMapper importMapper = MockMapperUtils.createImportMapper();
    ImportMapperUpdateInput importMapperInput = new ImportMapperUpdateInput();
    importMapperInput.setName(importMapper.getName());
    importMapperInput.setInjectTypeColumn(importMapper.getInjectTypeColumn());
    importMapperInput.setImporters(
        importMapper.getInjectImporters().stream()
            .map(
                injectImporter -> {
                  InjectImporterUpdateInput injectImporterUpdateInput =
                      new InjectImporterUpdateInput();
                  injectImporterUpdateInput.setInjectTypeValue(injectImporter.getImportTypeValue());
                  injectImporterUpdateInput.setInjectorContractId(
                      injectImporter.getInjectorContract().getId());
                  injectImporterUpdateInput.setId(injectImporter.getId());

                  injectImporterUpdateInput.setRuleAttributes(
                      injectImporter.getRuleAttributes().stream()
                          .map(
                              ruleAttribute -> {
                                RuleAttributeUpdateInput ruleAttributeUpdateInput =
                                    new RuleAttributeUpdateInput();
                                ruleAttributeUpdateInput.setName(ruleAttribute.getName());
                                ruleAttributeUpdateInput.setColumns(ruleAttribute.getColumns());
                                ruleAttributeUpdateInput.setDefaultValue(
                                    ruleAttribute.getDefaultValue());
                                ruleAttributeUpdateInput.setAdditionalConfig(
                                    ruleAttribute.getAdditionalConfig());
                                ruleAttributeUpdateInput.setId(ruleAttribute.getId());
                                return ruleAttributeUpdateInput;
                              })
                          .toList());
                  return injectImporterUpdateInput;
                })
            .toList());
    when(importMapperRepository.findById(any())).thenReturn(Optional.of(importMapper));
    when(importMapperRepository.save(any())).thenReturn(importMapper);
    when(injectorContractRepository.findAllById(any()))
        .thenReturn(
            importMapper.getInjectImporters().stream()
                .map(InjectImporter::getInjectorContract)
                .toList());

    // -- EXECUTE --
    ImportMapper response =
        mapperService.updateImportMapper(importMapper.getId(), importMapperInput);

    // -- ASSERT --
    assertNotNull(response);
    assertEquals(response.getId(), importMapper.getId());
  }

  @DisplayName("given_blankMapperId_should_throwElementNotFoundException")
  @Test
  void given_blankMapperId_should_throwElementNotFoundException() {
    // Arrange
    String blankMapperId = "";

    // Act / Assert
    assertThrows(
        ElementNotFoundException.class,
        () -> mapperService.getDuplicateImportMapper(blankMapperId));
  }

  @DisplayName("given_unsupportedCsvType_should_throwBadRequestException_whenExportMappersCsv")
  @Test
  void given_unsupportedCsvType_should_throwBadRequestException_whenExportMappersCsv() {
    // Arrange
    HttpServletResponse response = org.mockito.Mockito.mock(HttpServletResponse.class);

    // Act / Assert
    assertThrows(
        BadRequestException.class,
        () ->
            mapperService.exportMappersCsv(
                CsvType.AGENT, new io.openaev.utils.pagination.SearchPaginationInput(), response));
    verifyNoInteractions(endpointRepository, injectorContractRepository);
  }

  @DisplayName("given_unsupportedTargetType_should_throwBadRequestException_whenImportMappersCsv")
  @Test
  void given_unsupportedTargetType_should_throwBadRequestException_whenImportMappersCsv() {
    // Arrange
    MockMultipartFile csvFile =
        new MockMultipartFile(
            "file", "mappers.csv", "text/csv", "header\nvalue".getBytes(StandardCharsets.UTF_8));

    // Act / Assert
    assertThrows(
        BadRequestException.class, () -> mapperService.importMappersCsv(csvFile, CsvType.AGENT));
  }

  @DisplayName("given_mappersInput_should_appendImportedSuffix_whenImportMappers")
  @Test
  void given_mappersInput_should_appendImportedSuffix_whenImportMappers() {
    // Arrange
    ImportMapperAddInput input = new ImportMapperAddInput();
    input.setName("My mapper");
    input.setInjectTypeColumn("type");
    input.setImporters(List.of());

    // Act
    mapperService.importMappers(List.of(input));

    // Assert
    ArgumentCaptor<List<ImportMapper>> captor = ArgumentCaptor.forClass(List.class);
    verify(importMapperRepository).saveAll(captor.capture());
    assertEquals(1, captor.getValue().size());
    assertTrue(captor.getValue().get(0).getName().endsWith(Constants.IMPORTED_OBJECT_NAME_SUFFIX));
  }

  @DisplayName(
      "given_injectorContractWithPayload_should_exportSortedAndFallbackValues_whenExportMappersCsv")
  @Test
  void given_injectorContractWithPayload_should_exportSortedAndFallbackValues_whenExportMappersCsv()
      throws Exception {
    // Arrange
    InjectorContract injectorContract = new InjectorContract();
    injectorContract.setCreatedAt(Instant.parse("2024-01-01T01:01:01Z"));
    injectorContract.setLabels(
        new LinkedHashMap<>(java.util.Map.of("fr", "Nom Francais", "en", "English contract name")));
    injectorContract.setPlatforms(
        new io.openaev.database.model.Endpoint.PLATFORM_TYPE[] {
          io.openaev.database.model.Endpoint.PLATFORM_TYPE.Windows,
          io.openaev.database.model.Endpoint.PLATFORM_TYPE.Linux
        });

    Domain domainB = new Domain();
    domainB.setName("Domain B");
    Domain domainA = new Domain();
    domainA.setName("Domain A");
    injectorContract.setDomains(Set.of(domainB, domainA));

    Tag tagB = new Tag();
    tagB.setName("Beta");
    Tag tagA = new Tag();
    tagA.setName("Alpha");
    injectorContract.setTags(Set.of(tagB, tagA));

    AttackPattern attackPattern2 = new AttackPattern();
    attackPattern2.setName("Credential Access");
    AttackPattern attackPattern1 = new AttackPattern();
    attackPattern1.setName("Initial Access");
    injectorContract.setAttackPatterns(new ArrayList<>(List.of(attackPattern2, attackPattern1)));

    Injector injector = new Injector();
    injector.setId("injector-id");
    injector.setType("atomic-testing");
    injectorContract.addInjector(injector);

    Payload payload = new Payload();
    payload.setStatus(Payload.PAYLOAD_STATUS.VERIFIED);
    payload.setDescription("Payload description");
    payload.setSource(Payload.PAYLOAD_SOURCE.MANUAL);
    payload.setCreatedAt(Instant.parse("2024-01-05T06:07:08Z"));
    injectorContract.setPayload(payload);
    injectorContract.setUpdatedAt(Instant.parse("2024-01-02T03:04:05Z"));

    SearchPaginationInput input = new SearchPaginationInput();
    org.springframework.mock.web.MockHttpServletResponse response =
        new org.springframework.mock.web.MockHttpServletResponse();

    when(injectorContractRepository.findAll(any())).thenReturn(List.of(injectorContract));

    // Act
    mapperService.exportMappersCsv(CsvType.INJECTOR_CONTRACTS, input, response);

    // Assert
    assertEquals("text/csv", response.getContentType());
    assertTrue(
        response
            .getHeader("Content-Disposition")
            .startsWith("attachment; filename=InjectorContracts"));

    List<String[]> csvRows =
        new CSVReaderBuilder(new StringReader(response.getContentAsString())).build().readAll();
    assertEquals(2, csvRows.size());
    String[] row = csvRows.get(1);

    assertEquals("atomic-testing", row[0]);
    assertEquals("English contract name", row[1]);
    assertEquals("Domain A, Domain B", row[2]);
    assertEquals("Linux, Windows", row[3]);
    assertEquals("VERIFIED", row[4]);
    assertEquals("alpha, beta", row[5]);
    assertEquals("2024-01-02T03:04:05Z", row[6]);
    assertEquals("Payload description", row[7]);
    assertEquals("MANUAL", row[8]);
    assertEquals("2024-01-05T06:07:08Z", row[9]);
    assertEquals("Credential Access, Initial Access", row[10]);
    assertEquals("payload", row[11]);
  }

  @DisplayName("given_injectorContractWithoutPayload_should_exportEmptyValues_whenExportMappersCsv")
  @Test
  void given_injectorContractWithoutPayload_should_exportEmptyValues_whenExportMappersCsv()
      throws Exception {
    // Arrange
    InjectorContract injectorContract = new InjectorContract();
    injectorContract.setLabels(new LinkedHashMap<>());
    injectorContract.setDomains(Set.of());
    injectorContract.setPlatforms(new io.openaev.database.model.Endpoint.PLATFORM_TYPE[] {});
    injectorContract.setTags(Set.of());
    injectorContract.setAttackPatterns(new ArrayList<>());
    injectorContract.setCreatedAt(null);
    injectorContract.setUpdatedAt(null);
    injectorContract.setPayload(null);

    SearchPaginationInput input = new SearchPaginationInput();
    org.springframework.mock.web.MockHttpServletResponse response =
        new org.springframework.mock.web.MockHttpServletResponse();

    when(injectorContractRepository.findAll(any())).thenReturn(List.of(injectorContract));

    // Act
    mapperService.exportMappersCsv(CsvType.INJECTOR_CONTRACTS, input, response);

    // Assert
    List<String[]> csvRows =
        new CSVReaderBuilder(new StringReader(response.getContentAsString())).build().readAll();
    assertEquals(2, csvRows.size());
    String[] row = csvRows.get(1);

    assertEquals("-", row[0]);
    assertEquals("-", row[1]);
    assertEquals("-", row[2]);
    assertEquals("-", row[3]);
    assertEquals("-", row[4]);
    assertEquals("-", row[5]);
    assertEquals("-", row[6]);
    assertEquals("-", row[7]);
    assertEquals("-", row[8]);
    assertEquals("-", row[9]);
    assertEquals("-", row[10]);
    assertEquals("injector", row[11]);
  }

  @DisplayName("given_endpointsCsvType_should_useEndpointsFilenamePrefix_whenExportMappersCsv")
  @Test
  void given_endpointsCsvType_should_useEndpointsFilenamePrefix_whenExportMappersCsv() {
    // Arrange
    SearchPaginationInput input = new SearchPaginationInput();
    org.springframework.mock.web.MockHttpServletResponse response =
        new org.springframework.mock.web.MockHttpServletResponse();
    when(endpointRepository.findAll(any())).thenReturn(List.of());

    // Act
    mapperService.exportMappersCsv(CsvType.ENDPOINTS, input, response);

    // Assert
    assertTrue(
        response.getHeader("Content-Disposition").startsWith("attachment; filename=Endpoints"));
  }

  @DisplayName("given_exporterFailure_should_wrapException_whenExportMappersCsv")
  @Test
  void given_exporterFailure_should_wrapException_whenExportMappersCsv() {
    // Arrange
    SearchPaginationInput input = new SearchPaginationInput();
    org.springframework.mock.web.MockHttpServletResponse response =
        new org.springframework.mock.web.MockHttpServletResponse();
    RuntimeException repositoryException = new RuntimeException("boom");

    when(injectorContractRepository.findAll(any())).thenThrow(repositoryException);

    // Act
    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () -> mapperService.exportMappersCsv(CsvType.INJECTOR_CONTRACTS, input, response));

    // Assert
    assertEquals("Error during export CSV", thrown.getMessage());
    assertInstanceOf(RuntimeException.class, thrown.getCause());
    assertEquals("boom", thrown.getCause().getMessage());
  }
}
