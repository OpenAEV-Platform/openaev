package io.openaev.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Marked;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.persister.entity.AbstractEntityPersister;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("Every entity on a table with a marking_ids column implements Marked")
class MarkedEntitiesTest extends IntegrationTest {

  @Autowired private EntityManagerFactory entityManagerFactory;
  @Autowired private AllTablesWithMarkingIds allTablesWithMarkingIds;

  @Test
  void given_tableWithMarkingIds_should_mapOnlyEntitiesImplementingMarked() {
    // -- ARRANGE --
    Set<String> markedTables = allTablesWithMarkingIds.tableNames();

    // -- ACT --
    List<Class<?>> onMarkedTables = new ArrayList<>();
    entityManagerFactory
        .unwrap(SessionFactoryImplementor.class)
        .getMappingMetamodel()
        .forEachEntityDescriptor(
            entity -> {
              if (entity instanceof AbstractEntityPersister persister
                  && markedTables.contains(tableName(persister))) {
                onMarkedTables.add(entity.getMappedClass());
              }
            });

    // -- ASSERT --
    assertThat(onMarkedTables).isNotEmpty();
    assertThat(onMarkedTables)
        .as("entities on a marked table must implement Marked")
        .allMatch(Marked.class::isAssignableFrom);
  }

  private static String tableName(AbstractEntityPersister persister) {
    return persister.getTableName().replace("\"", "").toLowerCase(Locale.ROOT);
  }
}
