package io.openaev.database.repository;

import io.openaev.database.model.RegexGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface RegexGroupRepository
    extends JpaRepository<RegexGroup, String>, JpaSpecificationExecutor<RegexGroup> {}
