package io.openaev.api.asset;

import io.openaev.api.asset.dto.AssetOwnerInput;
import io.openaev.api.asset.dto.AssetOwnerOutput;
import io.openaev.api.graph.GraphExplorationService;
import io.openaev.database.model.AssetOwner;
import io.openaev.database.model.AssetOwnerRole;
import io.openaev.database.model.AssetRelationSource;
import io.openaev.database.repository.TenantRepository;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Owners of assets (#9725): a person or a team, as business owner ({@link AssetOwnerRole#OWNED_BY})
 * or technical owner ({@link AssetOwnerRole#MANAGED_BY}), several per asset.
 *
 * <p>{@code assets_owners} has no tenant column: every read and write goes through the asset, whose
 * reads the tenant and marking inspectors scope, so an asset the caller cannot read is "not found".
 * A team must belong to the asset's tenant (its read is scoped the same way) and a person must be a
 * member of it; anything else is refused, so an owner never reaches across tenants.
 */
@Service
@RequiredArgsConstructor
public class AssetOwnerService {

  /** Owners listed for one asset, or assets listed for one owner, at most. */
  static final int MAX_OWNERS = 500;

  private static final String USER_OWNERS =
      "SELECT o.id, a.id, a.name, u.id, u.firstname, u.lastname, u.email, o.role, o.source,"
          + " o.createdAt FROM AssetOwner o, Asset a, User u"
          + " WHERE a.id = o.assetId AND u.id = o.userId";

  private static final String TEAM_OWNERS =
      "SELECT o.id, a.id, a.name, t.id, t.name, o.role, o.source, o.createdAt"
          + " FROM AssetOwner o, Asset a, Team t"
          + " WHERE a.id = o.assetId AND t.id = o.teamId AND t.tenant.id = a.tenant.id";

  private final TenantRepository tenantRepository;

  @PersistenceContext private EntityManager entityManager;

  /** The owners of an asset: people first, then teams, each by name. */
  public List<AssetOwnerOutput> owners(String assetId) {
    tenantOf(assetId);
    List<AssetOwnerOutput> people =
        users(USER_OWNERS + " AND a.id = :id ORDER BY u.lastname, u.firstname, u.email", assetId);
    List<AssetOwnerOutput> teams = teams(TEAM_OWNERS + " AND a.id = :id ORDER BY t.name", assetId);
    return java.util.stream.Stream.concat(people.stream(), teams.stream())
        .limit(MAX_OWNERS)
        .toList();
  }

  /** The assets a person owns or manages, by asset name. */
  public List<AssetOwnerOutput> ownedByUser(String userId) {
    return users(USER_OWNERS + " AND u.id = :id ORDER BY a.name", userId);
  }

  /** The assets a team owns or manages, by asset name; an unreadable team is "not found". */
  public List<AssetOwnerOutput> ownedByTeam(String teamId) {
    teamTenant(teamId);
    return teams(TEAM_OWNERS + " AND t.id = :id ORDER BY a.name", teamId);
  }

  /** Adds the owner once: adding it again returns the stored owner, its source kept. */
  public AssetOwnerOutput addOwner(String assetId, AssetOwnerInput input) {
    boolean byUser = input.userId() != null && !input.userId().isBlank();
    boolean byTeam = input.teamId() != null && !input.teamId().isBlank();
    if (byUser == byTeam) {
      throw new BadRequestException("An owner is either a person or a team");
    }
    String tenantId = tenantOf(assetId);
    if (byUser) {
      if (!tenantRepository.existsByUserIdAndTenantId(input.userId(), tenantId)) {
        throw new BadRequestException("The person is not a member of the asset's tenant");
      }
    } else if (!tenantId.equals(teamTenant(input.teamId()))) {
      throw new BadRequestException("The team must belong to the asset's tenant");
    }
    AssetOwner owner = find(assetId, input.userId(), input.teamId(), input.role());
    if (owner == null) {
      owner = new AssetOwner();
      owner.setId(UUID.randomUUID().toString());
      owner.setAssetId(assetId);
      owner.setUserId(byUser ? input.userId() : null);
      owner.setTeamId(byTeam ? input.teamId() : null);
      owner.setRole(input.role());
      owner.setSource(input.source() == null ? AssetRelationSource.MANUAL : input.source());
      owner.setCreatedAt(Instant.now());
      entityManager.persist(owner);
      entityManager.flush();
    }
    return read(owner.getId());
  }

  /** Removes one owner of the asset; an owner of another asset, or none, is "not found". */
  public void deleteOwner(String assetId, String ownerId) {
    tenantOf(assetId);
    AssetOwner owner = entityManager.find(AssetOwner.class, ownerId);
    if (owner == null || !owner.getAssetId().equals(assetId)) {
      throw new ElementNotFoundException("Asset owner not found");
    }
    entityManager.remove(owner);
  }

  private AssetOwner find(String assetId, String userId, String teamId, AssetOwnerRole role) {
    boolean byUser = userId != null && !userId.isBlank();
    return entityManager
        .createQuery(
            "SELECT o FROM AssetOwner o WHERE o.assetId = :asset AND o.role = :role AND "
                + (byUser ? "o.userId = :owner" : "o.teamId = :owner"),
            AssetOwner.class)
        .setParameter("asset", assetId)
        .setParameter("role", role)
        .setParameter("owner", byUser ? userId : teamId)
        .getResultList()
        .stream()
        .findFirst()
        .orElse(null);
  }

  private AssetOwnerOutput read(String ownerId) {
    List<AssetOwnerOutput> found = users(USER_OWNERS + " AND o.id = :id", ownerId);
    if (found.isEmpty()) {
      found = teams(TEAM_OWNERS + " AND o.id = :id", ownerId);
    }
    return found.stream()
        .findFirst()
        .orElseThrow(() -> new ElementNotFoundException("Asset owner not found"));
  }

  private List<AssetOwnerOutput> users(String jpql, String id) {
    return entityManager
        .createQuery(jpql, Object[].class)
        .setParameter("id", id)
        .setMaxResults(MAX_OWNERS)
        .getResultList()
        .stream()
        .map(
            r ->
                new AssetOwnerOutput(
                    (String) r[0],
                    (String) r[1],
                    (String) r[2],
                    AssetOwnerOutput.KIND_USER,
                    (String) r[3],
                    null,
                    GraphExplorationService.personLabel(r[3], r[4], r[5], r[6]),
                    (AssetOwnerRole) r[7],
                    (AssetRelationSource) r[8],
                    (Instant) r[9]))
        .toList();
  }

  private List<AssetOwnerOutput> teams(String jpql, String id) {
    return entityManager
        .createQuery(jpql, Object[].class)
        .setParameter("id", id)
        .setMaxResults(MAX_OWNERS)
        .getResultList()
        .stream()
        .map(
            r ->
                new AssetOwnerOutput(
                    (String) r[0],
                    (String) r[1],
                    (String) r[2],
                    AssetOwnerOutput.KIND_TEAM,
                    null,
                    (String) r[3],
                    (String) r[4],
                    (AssetOwnerRole) r[5],
                    (AssetRelationSource) r[6],
                    (Instant) r[7]))
        .toList();
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

  /** The tenant of a team the caller can read, else "not found". */
  private String teamTenant(String teamId) {
    return entityManager
        .createQuery("SELECT t.tenant.id FROM Team t WHERE t.id = :id", String.class)
        .setParameter("id", teamId)
        .getResultList()
        .stream()
        .findFirst()
        .orElseThrow(() -> new ElementNotFoundException("Team not found: " + teamId));
  }
}
