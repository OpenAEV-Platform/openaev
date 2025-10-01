package io.openaev.database.raw;

import io.openaev.database.model.ImportMapper;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import lombok.Data;

@Data
public class RawPaginationImportMapper {

  @NotBlank String import_mapper_id;
  String import_mapper_name;
  Instant import_mapper_created_at;
  Instant import_mapper_updated_at;

  public RawPaginationImportMapper(final ImportMapper importMapper) {
    this.import_mapper_id = importMapper.getId();
    this.import_mapper_name = importMapper.getName();
    this.import_mapper_created_at = importMapper.getCreationDate();
    this.import_mapper_updated_at = importMapper.getUpdateDate();
  }
}
