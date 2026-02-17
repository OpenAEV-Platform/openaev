package io.openaev.database.repository;

import io.openaev.database.model.InjectDocument;
import io.openaev.database.model.InjectDocumentId;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InjectDocumentRepository
    extends CrudRepository<InjectDocument, InjectDocumentId>,
        JpaSpecificationExecutor<InjectDocument> {

  @NotNull
  Optional<InjectDocument> findById(@NotNull InjectDocumentId id);

  @Modifying
  @Query(
      value =
          "insert into injects_documents (inject_id, document_id, document_attached) "
              + "values (:injectId, :documentId, :documentAttached)",
      nativeQuery = true)
  void addInjectDoc(
      @Param("injectId") String injectId,
      @Param("documentId") String docId,
      @Param("documentAttached") boolean docAttached);

  @Modifying
  @Query(
      value = "UPDATE injects_documents SET inject_id = :injectId where inject_id = :oldInjectId",
      nativeQuery = true)
  void updateInjectId(@Param("injectId") String injectId, @Param("oldInjectId") String oldInjectId);
}
