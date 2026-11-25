package io.openaev.utils.fixtures;

import static io.openaev.utils.fixtures.CatalogReleaseFixture.REPOSITORY;

import jakarta.persistence.EntityManager;
import java.sql.PreparedStatement;
import java.util.UUID;
import org.hibernate.Session;

/**
 * Catalogue releases and items written with raw JDBC and an explicit tenant, which the statement
 * inspector never rewrites: the offered version of an item, as a release leaves it.
 */
public class CatalogItemFixture {

  private CatalogItemFixture() {}

  /** One catalogue item; the fields an Available tab filters on, the others left empty. */
  public record Item(
      String type,
      String externalId,
      String domain,
      String scenarioType,
      String category,
      String author,
      String platforms) {

    public static Item payload(String externalId) {
      return new Item("PAYLOAD", externalId, null, null, null, null, "");
    }

    public static Item scenario(String externalId) {
      return new Item("SCENARIO", externalId, null, "TIME_BASED", null, null, "");
    }

    public Item withDomain(String value) {
      return new Item(type, externalId, value, scenarioType, category, author, platforms);
    }

    public Item withScenarioType(String value) {
      return new Item(type, externalId, domain, value, category, author, platforms);
    }

    public Item withCategory(String value) {
      return new Item(type, externalId, domain, scenarioType, value, author, platforms);
    }

    public Item withAuthor(String value) {
      return new Item(type, externalId, domain, scenarioType, category, value, platforms);
    }

    /** Comma-separated platforms. */
    public Item withPlatforms(String value) {
      return new Item(type, externalId, domain, scenarioType, category, author, value);
    }
  }

  public static String release(EntityManager entityManager, String tenantId, String name) {
    String id = UUID.randomUUID().toString();
    execute(
        entityManager,
        "INSERT INTO catalog_releases (catalog_release_id, catalog_release_repository,"
            + " catalog_release_name, tenant_id) VALUES (?, ?, ?, ?)",
        id,
        REPOSITORY,
        name,
        tenantId);
    return id;
  }

  /** The item is named after its external ID, at version 1.0.0, offered. */
  public static void item(
      EntityManager entityManager, String tenantId, String releaseId, Item item) {
    execute(
        entityManager,
        "INSERT INTO catalog_items (catalog_item_id, catalog_item_type, catalog_item_external_id,"
            + " catalog_item_version, catalog_item_status, catalog_item_latest_offered,"
            + " catalog_item_name, catalog_item_domain, catalog_item_scenario_type,"
            + " catalog_item_category, catalog_item_author, catalog_item_platforms,"
            + " catalog_item_archive_name, catalog_item_archive_hash, catalog_item_release_id,"
            + " tenant_id) VALUES (?, ?, ?, '1.0.0', 'VERIFIED', true, ?, ?, ?, ?, ?,"
            + " string_to_array(?, ','), ?, ?, ?, ?)",
        UUID.randomUUID().toString(),
        item.type(),
        item.externalId(),
        item.externalId(),
        item.domain(),
        item.scenarioType(),
        item.category(),
        item.author(),
        item.platforms(),
        item.externalId() + ".zip",
        "a".repeat(64),
        releaseId,
        tenantId);
  }

  private static void execute(EntityManager entityManager, String sql, String... parameters) {
    entityManager
        .unwrap(Session.class)
        .doWork(
            connection -> {
              try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < parameters.length; i++) {
                  statement.setString(i + 1, parameters[i]);
                }
                statement.executeUpdate();
              }
            });
  }
}
