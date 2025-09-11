package io.openbas.database.repository;

import io.openbas.database.model.OutputParser;
import io.openbas.database.model.OutputParserByInject;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OutputParserRepository
    extends JpaRepository<OutputParser, String>, JpaSpecificationExecutor<OutputParser> {

            @Query(
                value =
                    "SELECT i.id injectId, op outputParser, ic injectorContract "
                        + "FROM Inject i "
                        + "LEFT JOIN i.injectorContract ic "
                        + "LEFT JOIN ic.payload p "
                        + "LEFT JOIN p.outputParsers op "
                        + "WHERE i.id IN :injectIds")
            List<OutputParserByInject> findAllOutputParsersByInjectIds(
                @NotNull @Param("injectIds") List<String> injectIds);
          }

        }