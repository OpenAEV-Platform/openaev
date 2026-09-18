package io.openaev.database.repository;

import io.openaev.database.model.UserEmailChangeConfirmation;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserEmailChangeConfirmationRepository
    extends CrudRepository<UserEmailChangeConfirmation, String>, JpaSpecificationExecutor<UserEmailChangeConfirmation> {}
