package io.openaev.api.asset;

import io.openaev.api.asset.dto.AssetLinkInput;
import io.openaev.api.asset.dto.AssetLinkOutput;
import io.openaev.database.model.AssetLink;
import io.openaev.database.model.AssetLinkType;
import io.openaev.database.model.AssetRelationSource;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Links between assets (#9447): an identity asset logged on to an endpoint, a service running on a
 * host, a host reaching another.
 *
 * <p>{@code assets_links} has no tenant column: every read and write here goes through the two
 * assets it joins, whose reads the tenant and marking inspectors scope. An asset the caller cannot
 * read is "not found", and a link between two tenants is refused, so a link can neither reveal nor
 * reach an asset outside the caller's scope.
 *
 * <p>Each link carries its origin (#9725). A person always wins over an automatic source: a person
 * adding a link an agent reported takes it over as manual, an agent never downgrades a manual link,
 * and a person removing an automatic link leaves it suppressed (hidden everywhere) so the next
 * report does not bring it back.
 */
@Service
public class AssetLinkService {

  /** Links listed for one asset, at most: an asset page is not a bulk export. */
  static final int MAX_LINKS = 500;

  private static final String LINK_COLUMNS =
      "SELECT s.id, s.name, s.type, t.id, t.name, t.type, l.type, l.createdAt, l.source,"
          + " l.lastSeenAt"
          + " FROM AssetLink l, Asset s, Asset t"
          + " WHERE s.id = l.assetId AND t.id = l.linkedAssetId AND l.suppressed = false";

  @PersistenceContext private EntityManager entityManager;

  /** Every link that starts or ends on the asset, outgoing first, then by the other end's name. */
  public List<AssetLinkOutput> links(String assetId) {
    String tenantId = tenantOf(assetId);
    return entityManager
        .createQuery(
            LINK_COLUMNS
                + " AND (s.id = :id OR t.id = :id)"
                + " AND s.tenant.id = :tenantId AND t.tenant.id = :tenantId"
                + " ORDER BY CASE WHEN s.id = :id THEN 0 ELSE 1 END, s.name, t.name",
            Object[].class)
        .setParameter("id", assetId)
        .setParameter("tenantId", tenantId)
        .setMaxResults(MAX_LINKS)
        .getResultList()
        .stream()
        .map(AssetLinkService::toOutput)
        .toList();
  }

  /**
   * Adds the link by hand, once: adding it again returns the stored link unchanged. A link an
   * automatic source reported, or one a person removed before, becomes a manual link.
   */
  public AssetLinkOutput addLink(String assetId, AssetLinkInput input) {
    String linkedAssetId = input.linkedAssetId();
    checkPair(assetId, linkedAssetId);
    AssetLink link = entityManager.find(AssetLink.class, key(assetId, linkedAssetId, input.type()));
    if (link == null) {
      persist(assetId, linkedAssetId, input.type(), AssetRelationSource.MANUAL, null);
    } else if (link.isSuppressed() || link.getSource().isAutomatic()) {
      link.setSuppressed(false);
      link.setSource(AssetRelationSource.MANUAL);
      entityManager.flush();
    }
    return read(assetId, linkedAssetId, input.type());
  }

  /**
   * Records a link an automatic source reported, at {@code seenAt}: created when absent, its last
   * seen time refreshed when present. A manual link keeps its source; a suppressed link (a person
   * removed it) stays suppressed and is not refreshed.
   *
   * @return true when the link is visible after the call
   */
  public boolean reportLink(
      String assetId,
      String linkedAssetId,
      AssetLinkType type,
      AssetRelationSource source,
      Instant seenAt) {
    if (!source.isAutomatic()) {
      throw new IllegalArgumentException("A reported link has an automatic source");
    }
    checkPair(assetId, linkedAssetId);
    AssetLink link = entityManager.find(AssetLink.class, key(assetId, linkedAssetId, type));
    if (link == null) {
      persist(assetId, linkedAssetId, type, source, seenAt);
      return true;
    }
    if (link.isSuppressed()) {
      return false;
    }
    link.setLastSeenAt(seenAt);
    return true;
  }

  /**
   * Removes an outgoing link of the asset; a link that does not exist is "not found". Both ends
   * must be readable, as for {@link #addLink}: a link to an asset the caller cannot see is "not
   * found", so it can neither be removed nor confirmed to exist. A link an automatic source
   * reported is kept suppressed instead of deleted, so the source does not add it back.
   */
  public void deleteLink(String assetId, String linkedAssetId, AssetLinkType type) {
    tenantOf(assetId);
    tenantOf(linkedAssetId);
    AssetLink link = entityManager.find(AssetLink.class, key(assetId, linkedAssetId, type));
    if (link == null || link.isSuppressed()) {
      throw new ElementNotFoundException("Asset link not found");
    }
    if (link.getSource().isAutomatic()) {
      link.setSuppressed(true);
      entityManager.flush();
    } else {
      entityManager.remove(link);
    }
  }

  private void checkPair(String assetId, String linkedAssetId) {
    if (assetId.equals(linkedAssetId)) {
      throw new BadRequestException("An asset cannot be linked to itself");
    }
    String tenantId = tenantOf(assetId);
    if (!tenantId.equals(tenantOf(linkedAssetId))) {
      throw new BadRequestException("Linked assets must belong to the same tenant");
    }
  }

  private void persist(
      String assetId,
      String linkedAssetId,
      AssetLinkType type,
      AssetRelationSource source,
      Instant seenAt) {
    AssetLink link = new AssetLink();
    link.setAssetId(assetId);
    link.setLinkedAssetId(linkedAssetId);
    link.setType(type);
    link.setCreatedAt(Instant.now());
    link.setSource(source);
    link.setLastSeenAt(seenAt);
    entityManager.persist(link);
    entityManager.flush();
  }

  private AssetLinkOutput read(String assetId, String linkedAssetId, AssetLinkType type) {
    return entityManager
        .createQuery(
            LINK_COLUMNS + " AND s.id = :id AND t.id = :linked AND l.type = :type", Object[].class)
        .setParameter("id", assetId)
        .setParameter("linked", linkedAssetId)
        .setParameter("type", type)
        .getResultList()
        .stream()
        .findFirst()
        .map(AssetLinkService::toOutput)
        .orElseThrow(() -> new ElementNotFoundException("Asset link not found"));
  }

  /** The tenant of an asset the caller can read, else "not found". */
  private String tenantOf(String assetId) {
    return entityManager
        .createQuery("SELECT a.tenant.id FROM Asset a WHERE a.id = :id", String.class)
        .setParameter("id", assetId)
        .getResultList()
        .stream()
        .findFirst()
        .orElseThrow(() -> new ElementNotFoundException("Asset not found: " + assetId));
  }

  private static AssetLink.AssetLinkId key(
      String assetId, String linkedAssetId, AssetLinkType type) {
    AssetLink.AssetLinkId key = new AssetLink.AssetLinkId();
    key.setAssetId(assetId);
    key.setLinkedAssetId(linkedAssetId);
    key.setType(type);
    return key;
  }

  private static AssetLinkOutput toOutput(Object[] r) {
    return new AssetLinkOutput(
        (String) r[0],
        (String) r[1],
        (String) r[2],
        (String) r[3],
        (String) r[4],
        (String) r[5],
        (AssetLinkType) r[6],
        (Instant) r[7],
        (AssetRelationSource) r[8],
        (Instant) r[9]);
  }
}
