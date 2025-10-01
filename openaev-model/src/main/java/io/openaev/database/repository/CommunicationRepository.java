package io.openaev.database.repository;

import io.openaev.database.model.Communication;
import io.openaev.database.raw.RawCommunication;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CommunicationRepository
    extends CrudRepository<Communication, String>, JpaSpecificationExecutor<Communication> {

  @NotNull
  Optional<Communication> findById(@NotNull String id);

  List<Communication> findByInjectId(@NotNull String injectId);

  @Query(
      "select c from Communication c join c.users as user where user.id = :userId order by c.receivedAt desc")
  List<Communication> findByUser(@Param("userId") String userId);

  boolean existsByIdentifier(String identifier);

  @Query(
      value =
          "SELECT c.*, injects.inject_exercise as communication_exercise, "
              + "coalesce(array_agg(DISTINCT cu.user_id) FILTER ( WHERE cu.user_id IS NOT NULL ), '{}') as communication_users "
              + "FROM communications c "
              + "LEFT JOIN communications_users cu ON cu.communication_id = c.communication_id "
              + "LEFT JOIN injects ON injects.inject_id = c.communication_inject "
              + "WHERE c.communication_id IN :ids "
              + "GROUP BY c.communication_id, injects.inject_exercise ;",
      nativeQuery = true)
  List<RawCommunication> rawByIds(@Param("ids") List<String> ids);
}
