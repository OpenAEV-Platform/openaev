package io.openaev.service;

import static com.opencsv.ICSVWriter.*;
import static io.openaev.helper.StreamHelper.iterableToSet;
import static io.openaev.utils.FilterUtilsJpa.computeFilterGroupJpa;
import static io.openaev.utils.StringUtils.duplicateString;
import static io.openaev.utils.pagination.SearchUtilsJpa.computeSearchJpa;
import static java.io.File.createTempFile;
import static java.time.Instant.now;
import static java.util.stream.StreamSupport.stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.bean.ColumnPositionMappingStrategy;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import io.openaev.database.model.*;
import io.openaev.database.repository.EndpointRepository;
import io.openaev.database.repository.ImportMapperRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.helper.ObjectMapperHelper;
import io.openaev.rest.asset.endpoint.form.EndpointExportImport;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.mapper.export.MapperExportMixins;
import io.openaev.rest.mapper.form.*;
import io.openaev.rest.tag.TagService;
import io.openaev.rest.tag.form.TagCreateInput;
import io.openaev.rest.tag.form.TagExportImport;
import io.openaev.service.utils.CustomColumnPositionStrategy;
import io.openaev.utils.CopyObjectListUtils;
import io.openaev.utils.TargetType;
import io.openaev.utils.constants.Constants;
import io.openaev.utils.mapper.EndpointMapper;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@RequiredArgsConstructor
@Service
public class MapperService {

  private final ImportMapperRepository importMapperRepository;
  private final InjectorContractRepository injectorContractRepository;
  private final EndpointRepository endpointRepository;
  private final EndpointService endpointService;

  private final TagService tagService;
  private final ObjectMapper objectMapper;

  /**
   * Create and save an ImportMapper object from a MapperAddInput one
   *
   * @param importMapperAddInput The input from the call
   * @return The created ImportMapper
   */
  public ImportMapper createAndSaveImportMapper(ImportMapperAddInput importMapperAddInput) {
    ImportMapper importMapper = createImportMapper(importMapperAddInput);

    return importMapperRepository.save(importMapper);
  }

  public ImportMapper createImportMapper(ImportMapperAddInput importMapperAddInput) {
    ImportMapper importMapper = new ImportMapper();
    importMapper.setUpdateAttributes(importMapperAddInput);
    importMapper.setInjectImporters(new ArrayList<>());

    Map<String, InjectorContract> mapInjectorContracts =
        getMapOfInjectorContracts(
            importMapperAddInput.getImporters().stream()
                .map(InjectImporterAddInput::getInjectorContractId)
                .toList());

    importMapperAddInput
        .getImporters()
        .forEach(
            injectImporterInput -> {
              InjectImporter injectImporter = new InjectImporter();
              injectImporter.setInjectorContract(
                  mapInjectorContracts.get(injectImporterInput.getInjectorContractId()));
              injectImporter.setImportTypeValue(injectImporterInput.getInjectTypeValue());

              injectImporter.setRuleAttributes(new ArrayList<>());
              injectImporterInput
                  .getRuleAttributes()
                  .forEach(
                      ruleAttributeInput -> {
                        injectImporter
                            .getRuleAttributes()
                            .add(
                                CopyObjectListUtils.copyObjectWithoutId(
                                    ruleAttributeInput, RuleAttribute.class));
                      });
              importMapper.getInjectImporters().add(injectImporter);
            });

    return importMapper;
  }

  /**
   * Duplicate importMapper by id
   *
   * @param importMapperId id of the mapper that need to be duplicated
   * @return The duplicated ImportMapper
   */
  @Transactional
  public ImportMapper getDuplicateImportMapper(@NotBlank String importMapperId) {
    if (StringUtils.isNotBlank(importMapperId)) {
      ImportMapper importMapperOrigin =
          importMapperRepository.findById(UUID.fromString(importMapperId)).orElseThrow();
      ImportMapper importMapper =
          CopyObjectListUtils.copyObjectWithoutId(importMapperOrigin, ImportMapper.class);
      importMapper.setName(duplicateString(importMapperOrigin.getName()));
      List<InjectImporter> injectImporters =
          getInjectImportersDuplicated(importMapperOrigin.getInjectImporters());
      importMapper.setInjectImporters(injectImporters);
      return importMapperRepository.save(importMapper);
    }
    throw new ElementNotFoundException();
  }

  private List<InjectImporter> getInjectImportersDuplicated(
      List<InjectImporter> injectImportersOrigin) {
    List<InjectImporter> injectImporters =
        CopyObjectListUtils.copyWithoutIds(injectImportersOrigin, InjectImporter.class);
    injectImporters.forEach(
        injectImport -> {
          List<RuleAttribute> ruleAttributes =
              CopyObjectListUtils.copyWithoutIds(
                  injectImport.getRuleAttributes(), RuleAttribute.class);
          injectImport.setRuleAttributes(ruleAttributes);
        });
    return injectImporters;
  }

  /**
   * Update an ImportMapper object from a MapperUpdateInput one
   *
   * @param mapperId the id of the mapper that needs to be updated
   * @param importMapperUpdateInput The input from the call
   * @return The updated ImportMapper
   */
  public ImportMapper updateImportMapper(
      String mapperId, ImportMapperUpdateInput importMapperUpdateInput) {
    ImportMapper importMapper =
        importMapperRepository
            .findById(UUID.fromString(mapperId))
            .orElseThrow(ElementNotFoundException::new);
    importMapper.setUpdateAttributes(importMapperUpdateInput);
    importMapper.setUpdateDate(Instant.now());

    Map<String, InjectorContract> mapInjectorContracts =
        getMapOfInjectorContracts(
            importMapperUpdateInput.getImporters().stream()
                .map(InjectImporterUpdateInput::getInjectorContractId)
                .toList());

    updateInjectImporter(
        importMapperUpdateInput.getImporters(),
        importMapper.getInjectImporters(),
        mapInjectorContracts);

    return importMapperRepository.save(importMapper);
  }

  /**
   * Gets a map of injector contracts by ids
   *
   * @param ids The ids of the injector contracts we want
   * @return The map of injector contracts by ids
   */
  private Map<String, InjectorContract> getMapOfInjectorContracts(List<String> ids) {
    return stream(injectorContractRepository.findAllById(ids).spliterator(), false)
        .collect(Collectors.toMap(InjectorContract::getId, Function.identity()));
  }

  /**
   * Updates rule attributes from a list of input
   *
   * @param ruleAttributesInput the list of rule attributes input
   * @param ruleAttributes the list of rule attributes to update
   */
  private void updateRuleAttributes(
      List<RuleAttributeUpdateInput> ruleAttributesInput, List<RuleAttribute> ruleAttributes) {
    // First, we remove the entities that are no longer linked to the mapper
    ruleAttributes.removeIf(
        ruleAttribute ->
            ruleAttributesInput.stream()
                .noneMatch(importerInput -> ruleAttribute.getId().equals(importerInput.getId())));

    // Then we update the existing ones
    ruleAttributes.forEach(
        ruleAttribute -> {
          RuleAttributeUpdateInput ruleAttributeInput =
              ruleAttributesInput.stream()
                  .filter(
                      ruleAttributeUpdateInput ->
                          ruleAttribute.getId().equals(ruleAttributeUpdateInput.getId()))
                  .findFirst()
                  .orElseThrow(ElementNotFoundException::new);
          ruleAttribute.setUpdateAttributes(ruleAttributeInput);
        });

    // Then we add the new ones
    ruleAttributesInput.forEach(
        ruleAttributeUpdateInput -> {
          if (ruleAttributeUpdateInput.getId() == null
              || ruleAttributeUpdateInput.getId().isBlank()) {
            RuleAttribute ruleAttribute = new RuleAttribute();
            ruleAttribute.setColumns(ruleAttributeUpdateInput.getColumns());
            ruleAttribute.setName(ruleAttributeUpdateInput.getName());
            ruleAttribute.setDefaultValue(ruleAttributeUpdateInput.getDefaultValue());
            ruleAttribute.setAdditionalConfig(ruleAttributeUpdateInput.getAdditionalConfig());
            ruleAttributes.add(ruleAttribute);
          }
        });
  }

  /**
   * Updates a list of inject importers from an input one
   *
   * @param injectImportersInput the input
   * @param injectImporters the inject importers to update
   * @param mapInjectorContracts a map of injector contracts by contract id
   */
  private void updateInjectImporter(
      List<InjectImporterUpdateInput> injectImportersInput,
      List<InjectImporter> injectImporters,
      Map<String, InjectorContract> mapInjectorContracts) {
    // First, we remove the entities that are no longer linked to the mapper
    injectImporters.removeIf(
        importer ->
            !injectImportersInput.stream()
                .anyMatch(importerInput -> importer.getId().equals(importerInput.getId())));

    // Then we update the existing ones
    injectImporters.forEach(
        injectImporter -> {
          InjectImporterUpdateInput injectImporterInput =
              injectImportersInput.stream()
                  .filter(
                      injectImporterUpdateInput ->
                          injectImporter.getId().equals(injectImporterUpdateInput.getId()))
                  .findFirst()
                  .orElseThrow(ElementNotFoundException::new);
          injectImporter.setUpdateAttributes(injectImporterInput);
          updateRuleAttributes(
              injectImporterInput.getRuleAttributes(), injectImporter.getRuleAttributes());
        });

    // Then we add the new ones
    injectImportersInput.forEach(
        injectImporterUpdateInput -> {
          if (injectImporterUpdateInput.getId() == null
              || injectImporterUpdateInput.getId().isBlank()) {
            InjectImporter injectImporter = new InjectImporter();
            injectImporter.setInjectorContract(
                mapInjectorContracts.get(injectImporterUpdateInput.getInjectorContractId()));
            injectImporter.setImportTypeValue(injectImporterUpdateInput.getInjectTypeValue());
            injectImporter.setRuleAttributes(new ArrayList<>());
            injectImporterUpdateInput
                .getRuleAttributes()
                .forEach(
                    ruleAttributeInput -> {
                      RuleAttribute ruleAttribute = new RuleAttribute();
                      ruleAttribute.setColumns(ruleAttributeInput.getColumns());
                      ruleAttribute.setName(ruleAttributeInput.getName());
                      ruleAttribute.setDefaultValue(ruleAttributeInput.getDefaultValue());
                      ruleAttribute.setAdditionalConfig(ruleAttributeInput.getAdditionalConfig());
                      injectImporter.getRuleAttributes().add(ruleAttribute);
                    });
            injectImporters.add(injectImporter);
          }
        });
  }

  public String exportMappers(@NotNull final List<String> idsToExport)
      throws JsonProcessingException {
    ObjectMapper objectMapper = ObjectMapperHelper.openAEVJsonMapper();
    List<ImportMapper> mappersList =
        StreamSupport.stream(
                importMapperRepository
                    .findAllById(idsToExport.stream().map(UUID::fromString).toList())
                    .spliterator(),
                false)
            .toList();

    objectMapper.addMixIn(ImportMapper.class, MapperExportMixins.ImportMapper.class);
    objectMapper.addMixIn(InjectImporter.class, MapperExportMixins.InjectImporter.class);
    objectMapper.addMixIn(RuleAttribute.class, MapperExportMixins.RuleAttribute.class);

    return objectMapper.writeValueAsString(mappersList);
  }

  /**
   * Export CSV with options and return the file
   *
   * @param targetType used to know which entity list we want to export
   * @param input used to know which filter we want to apply to get the entity list to export
   * @param response used to return the file
   */
  public void exportMappersCsv(
      TargetType targetType, SearchPaginationInput input, HttpServletResponse response) {
    switch (targetType) {
      case ENDPOINTS:
        try {
          List<EndpointExportImport> endpointsToExport = getEndpointsToExport(input);
          String dateNow = DateTimeFormatter.ofPattern("yyyyMMddHHmm").format(LocalDateTime.now());
          exportCsv(
              response,
              "Endpoints" + dateNow + ".csv",
              endpointsToExport,
              EndpointExportImport.class);
        } catch (Exception e) {
          throw new RuntimeException("Error during export csv ", e);
        }
        break;
      default:
        throw new BadRequestException(
            "Target type " + targetType + " for CSV export is not supported");
    }
  }

  private List<EndpointExportImport> getEndpointsToExport(SearchPaginationInput input)
      throws JsonProcessingException {
    Specification<Endpoint> filterSpecifications = computeFilterGroupJpa(input.getFilterGroup());
    filterSpecifications = filterSpecifications.and(computeSearchJpa(input.getTextSearch()));
    List<Endpoint> endpointsToProcess = endpointRepository.findAll(filterSpecifications);
    List<EndpointExportImport> exports = new ArrayList<>();
    EndpointExportImport endpointExport;
    for (Endpoint endpoint : endpointsToProcess) {
      endpointExport = new EndpointExportImport();
      endpointExport.setName(endpoint.getName());
      endpointExport.setDescription(endpoint.getDescription());
      endpointExport.setHostname(endpoint.getHostname());
      endpointExport.setIps(objectMapper.writeValueAsString(endpoint.getIps()));
      endpointExport.setMacAddresses(objectMapper.writeValueAsString(endpoint.getMacAddresses()));
      endpointExport.setPlatform(endpoint.getPlatform());
      endpointExport.setArch(endpoint.getArch());
      endpointExport.setTags(
          objectMapper.writeValueAsString(
              endpoint.getTags().stream()
                  .map(tag -> new TagExportImport(tag.getName(), tag.getColor()))
                  .collect(Collectors.toSet())));
      endpointExport.setEol(endpoint.isEoL());
      exports.add(endpointExport);
    }
    return exports;
  }

  private static <T> void exportCsv(
      HttpServletResponse response, String filename, List<T> exports, Class<T> exportClass)
      throws IOException, CsvDataTypeMismatchException, CsvRequiredFieldEmptyException {
    response.setContentType("text/csv");
    response.setHeader("Content-Disposition", "attachment; filename=" + filename);
    response.setStatus(HttpServletResponse.SC_OK);
    CustomColumnPositionStrategy<T> columns = new CustomColumnPositionStrategy();
    columns.setType(exportClass);
    StatefulBeanToCsv<T> writer =
        new StatefulBeanToCsvBuilder<T>(response.getWriter())
            .withQuotechar(DEFAULT_QUOTE_CHARACTER)
            .withSeparator(DEFAULT_SEPARATOR)
            .withMappingStrategy(columns)
            .build();
    writer.write(exports);
  }

  /**
   * Import CSV with options
   *
   * @param file file to import
   * @param targetType entity to know which columns format we use for the import
   * @throws Exception exception if problem during the import
   */
  public void importMappersCsv(MultipartFile file, TargetType targetType) throws Exception {
    File tempFile = createTempFile("openaev-import-" + now().getEpochSecond(), ".csv");
    FileUtils.copyInputStreamToFile(file.getInputStream(), tempFile);

    try {
      CSVParser csvParser =
          new CSVParserBuilder()
              .withSeparator(DEFAULT_SEPARATOR)
              .withIgnoreQuotations(false)
              .build();

      CSVReader csvReader =
          new CSVReaderBuilder(new FileReader(tempFile))
              .withSkipLines(1)
              .withCSVParser(csvParser)
              .build();

      switch (targetType) {
        case ENDPOINTS:
          try {
            importEndpointsCsv(setEndpointsColumnMapping(), csvReader);
          } catch (Exception e) {
            throw new RuntimeException("Error during export csv ", e);
          }
          break;
        default:
          throw new BadRequestException(
              "Target type " + targetType + " for CSV export is not supported");
      }
    } finally {
      tempFile.delete();
    }
  }

  private void importEndpointsCsv(
      ColumnPositionMappingStrategy columnPositionMappingStrategy, CSVReader csvReader)
      throws JsonProcessingException {

    CsvToBean csv = new CsvToBean();
    csv.setCsvReader(csvReader);
    csv.setMappingStrategy(columnPositionMappingStrategy);

    List list = csv.parse();

    for (Object object : list) {
      EndpointExportImport endpointExportImport = (EndpointExportImport) object;

      Endpoint endpoint = new Endpoint();
      endpoint.setName(endpointExportImport.getName());
      endpoint.setDescription(endpointExportImport.getDescription());
      endpoint.setHostname(endpointExportImport.getHostname());
      endpoint.setPlatform(endpointExportImport.getPlatform());
      endpoint.setArch(endpointExportImport.getArch());
      endpoint.setIps(
          EndpointMapper.setIps(
              objectMapper.readValue(endpointExportImport.getIps(), new TypeReference<>() {})));
      endpoint.setMacAddresses(
          EndpointMapper.setMacAddresses(
              objectMapper.readValue(
                  endpointExportImport.getMacAddresses(), new TypeReference<>() {})));

      List<Tag> tagsForCreation = new ArrayList<>();
      Set<TagExportImport> endpointExportImportTags =
          objectMapper.readValue(endpointExportImport.getTags(), new TypeReference<>() {});
      for (TagExportImport tag : endpointExportImportTags) {
        TagCreateInput tagCreateInput = new TagCreateInput();
        tagCreateInput.setName(tag.getName());
        tagCreateInput.setColor(tag.getColor());
        tagsForCreation.add(this.tagService.upsertTag(tagCreateInput));
      }
      endpoint.setTags(iterableToSet(tagsForCreation));
      endpoint.setEoL(endpointExportImport.isEol());
      endpointService.createEndpoint(endpoint);
    }
  }

  private static ColumnPositionMappingStrategy<EndpointExportImport> setEndpointsColumnMapping() {
    ColumnPositionMappingStrategy<EndpointExportImport> strategy =
        new ColumnPositionMappingStrategy<>();
    strategy.setType(EndpointExportImport.class);
    String[] columns =
        new String[] {
          "name",
          "description",
          "hostname",
          "ips",
          "platform",
          "arch",
          "macAddresses",
          "tags",
          "isEol"
        };
    strategy.setColumnMapping(columns);
    return strategy;
  }

  public void importMappers(List<ImportMapperAddInput> mappers) {
    importMapperRepository.saveAll(
        mappers.stream()
            .map(this::createImportMapper)
            .peek(
                (m) ->
                    m.setName(m.getName() + " %s".formatted(Constants.IMPORTED_OBJECT_NAME_SUFFIX)))
            .toList());
  }
}
