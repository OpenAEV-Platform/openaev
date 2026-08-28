package io.openaev.database.repository;

import io.openaev.database.model.Secret;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SecretsRepository extends JpaRepository<Secret, String> {}
