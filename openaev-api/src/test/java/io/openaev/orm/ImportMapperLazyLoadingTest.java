package io.openaev.orm;

import static io.openaev.utils.fixtures.import_mapper.ImportMapperFixture.createImportMapper;
import static io.openaev.utils.fixtures.import_mapper.ImportMapperFixture.createInjectImporter;

import io.openaev.database.model.ImportMapper;
import io.openaev.database.model.InjectImporter;
import io.openaev.database.repository.ImportMapperRepository;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

/** ORM regression guard for the ImportMapper EAGER to LAZY + {@code @BatchSize} refactoring. */
class ImportMapperLazyLoadingTest extends OrmPerformanceTest {

  private static final int SEED_COUNT = 10;
  // The fixture already creates one importer, so the seed only adds the remaining ones.
  private static final int IMPORTERS_PER_MAPPER = 2;

  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private ImportMapperRepository importMapperRepository;

  @BeforeEach
  void setUp() {
    injectorContractComposer.reset();
  }

  @Override
  protected String entityName() {
    return "ImportMapper";
  }

  @Override
  protected Class<?> entityClass() {
    return ImportMapper.class;
  }

  @Override
  protected int seedCount() {
    return SEED_COUNT;
  }

  @Override
  protected List<UUID> seed() {
    List<UUID> ids = new ArrayList<>();
    for (int i = 0; i < SEED_COUNT; i++) {
      ImportMapper mapper = createImportMapper("Mapper-" + i, "type-0", "A");

      for (int j = 1; j < IMPORTERS_PER_MAPPER; j++) {
        mapper.getInjectImporters().add(createInjectImporter("type-" + j));
      }

      for (InjectImporter importer : mapper.getInjectImporters()) {
        injectorContractComposer.forInjectorContract(importer.getInjectorContract()).persist();
      }

      entityManager.persist(mapper);
      ids.add(UUID.fromString(mapper.getId()));
    }
    clearCache();
    return ids;
  }

  @Override
  protected int loadFullGraph(UUID id) {
    ImportMapper result = importMapperRepository.findById(id).orElseThrow();
    int importerCount = result.getInjectImporters().size();
    int attrCount =
        result.getInjectImporters().stream().mapToInt(imp -> imp.getRuleAttributes().size()).sum();
    return importerCount + attrCount;
  }
}
