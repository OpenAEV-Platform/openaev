package io.openaev.database.repository;

import io.openaev.database.model.InjectImporter;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InjectImporterRepository extends CrudRepository<InjectImporter, UUID> {}
