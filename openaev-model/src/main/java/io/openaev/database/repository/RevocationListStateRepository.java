package io.openaev.database.repository;

import io.openaev.database.model.RevocationListState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RevocationListStateRepository extends JpaRepository<RevocationListState, String> {}
