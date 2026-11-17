package io.openaev.utils;

import io.openaev.database.model.Tag;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.TagRepository;
import io.openaev.utils.fixtures.TagFixture;
import jakarta.persistence.EntityManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.Session;

/**
 * Seeds tags and reads link rows through JDBC on the test connection, which the statement inspector
 * does not rewrite, so a link table is read as stored whatever the request scope.
 */
public final class TagLinkJdbc {

  private TagLinkJdbc() {}

  /** Saves a tag of the given tenant and returns its id. */
  public static String saveTag(TagRepository tagRepository, String tenantId) {
    Tag tag = TagFixture.getTagWithText("tag-link-" + UUID.randomUUID());
    tag.setTenant(new Tenant(tenantId));
    return tagRepository.save(tag).getId();
  }

  /** The first column of every row the query returns for the single parameter. */
  public static List<String> column(EntityManager entityManager, String sql, String parameter) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              List<String> values = new ArrayList<>();
              try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, parameter);
                try (ResultSet resultSet = statement.executeQuery()) {
                  while (resultSet.next()) {
                    values.add(resultSet.getString(1));
                  }
                }
              }
              return values;
            });
  }
}
