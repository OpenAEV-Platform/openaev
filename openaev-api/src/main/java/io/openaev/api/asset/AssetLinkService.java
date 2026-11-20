package io.openaev.api.asset;

import io.openaev.api.asset.dto.AssetLinkInput;
import io.openaev.api.asset.dto.AssetLinkOutput;
import io.openaev.database.model.AssetLink;
import io.openaev.database.model.AssetLinkType;
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
 */
@Service
public class AssetLinkService {

  /** Links listed for one asset, at most: an asset page is not a bulk export. */
  static final int MAX_LINKS = 500;

  private static final String LINK_COLUMNS =
      "SELECT s.id, s.name, s.type, t.id, t.name, t.type, l.type, l.createdAt"
          + " FROM AssetLink l, Asset s, Asset t"
          + " WHERE s.id = l.assetId AND t.id = l.linkedAssetId";

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

  /** Adds the link once: adding it again returns the stored link unchanged. */
  public AssetLinkOutput addLink(String assetId, AssetLinkInput input) {
    String linkedAssetId = input.linkedAssetId();
    if (assetId.equals(linkedAssetId)) {
      throw new BadRequestException("An asset cannot be linked to itself");
    }
    String tenantId = tenantOf(assetId);
    if (!tenantId.equals(tenantOf(linkedAssetId))) {
      throw new BadRequestException("Linked assets must belong to the same tenant");
    }
    AssetLink.AssetLinkId key = key(assetId, linkedAssetId, input.type());
    if (entityManager.find(AssetLink.class, key) == null) {
      AssetLink link = new AssetLink();
      link.setAssetId(assetId);
      link.setLinkedAssetId(linkedAssetId);
      link.setType(input.type());
      link.setCreatedAt(Instant.now());
      entityManager.persist(link);
      entityManager.flush();
    }
    return entityManager
        .createQuery(
            LINK_COLUMNS + " AND s.id = :id AND t.id = :linked AND l.type = :type", Object[].class)
        .setParameter("id", assetId)
        .setParameter("linked", linkedAssetId)
        .setParameter("type", input.type())
        .getResultList()
        .stream()
        .findFirst()
        .map(AssetLinkService::toOutput)
        .orElseThrow(() -> new ElementNotFoundException("Asset link not found"));
  }

  /** Removes an outgoing link of the asset; a link that does not exist is "not found". */
  public void deleteLink(String assetId, String linkedAssetId, AssetLinkType type) {
    tenantOf(assetId);
    AssetLink link = entityManager.find(AssetLink.class, key(assetId, linkedAssetId, type));
    if (link == null) {
      throw new ElementNotFoundException("Asset link not found");
    }
    entityManager.remove(link);
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
        (Instant) r[7]);
  }
}
