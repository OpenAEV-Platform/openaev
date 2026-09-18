package io.openaev.database.repository;

import io.openaev.database.model.InjectAuthorisation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InjectAuthorisationRepository extends JpaRepository<InjectAuthorisation, String> {

  Optional<InjectAuthorisation> findByInjectId(String injectId);

  @Modifying
  @Query("delete from InjectAuthorisation ia where ia.inject.id = :injectId")
  void deleteAllByInjectId(String injectId);

  @Modifying
  @Query("delete from InjectAuthorisation ia where ia.inject.id in :ids")
  void deleteAllByInjectIds(@Param("ids") List<String> ids);
}
