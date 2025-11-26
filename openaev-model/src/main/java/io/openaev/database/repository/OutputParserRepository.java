package io.openaev.database.repository;

import io.openaev.database.model.OutputParser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface OutputParserRepository
    extends JpaRepository<OutputParser, String>, JpaSpecificationExecutor<OutputParser> {}
