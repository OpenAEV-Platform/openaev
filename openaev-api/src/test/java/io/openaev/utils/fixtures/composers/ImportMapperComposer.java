package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.ImportMapper;
import io.openaev.database.repository.ImportMapperRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ImportMapperComposer extends ComposerBase<ImportMapper> {
  @Autowired private ImportMapperRepository importMapperRepository;

  public class Composer extends InnerComposerBase<ImportMapper> {
    private final ImportMapper importMapper;

    public Composer(ImportMapper importMapper) {
      this.importMapper = importMapper;
    }

    @Override
    public Composer persist() {
      importMapperRepository.save(importMapper);
      return this;
    }

    @Override
    public Composer delete() {
      importMapperRepository.delete(importMapper);
      return this;
    }

    @Override
    public ImportMapper get() {
      return this.importMapper;
    }
  }

  public Composer forImportMapper(ImportMapper importMapper) {
    generatedItems.add(importMapper);
    return new Composer(importMapper);
  }
}
