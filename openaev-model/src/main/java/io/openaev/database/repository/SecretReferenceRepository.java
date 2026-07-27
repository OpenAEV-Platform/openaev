package io.openaev.database.repository;

import io.openaev.database.model.SecretReference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SecretReferenceRepository extends JpaRepository<SecretReference, String> {}
