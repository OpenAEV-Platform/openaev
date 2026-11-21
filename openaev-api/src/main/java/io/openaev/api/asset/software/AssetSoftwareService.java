package io.openaev.api.asset.software;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.api.asset.software.SbomParser.ParsedComponent;
import io.openaev.api.asset.software.dto.AssetSoftwareInput;
import io.openaev.api.asset.software.dto.AssetSoftwareOutput;
import io.openaev.api.asset.software.dto.SbomImportOutput;
import io.openaev.database.model.Asset;
import io.openaev.database.model.AssetSoftware;
import io.openaev.database.model.SoftwareSource;
import io.openaev.database.model.SoftwareVersionState;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Software inventory of assets: what each asset runs, recorded by the API, an SBOM import or an
 * agent, and the assets that run a given software.
 *
 * <p>{@code asset_software} has no tenant column: every read and write here goes through the asset
 * it belongs to, whose reads the tenant and marking inspectors scope. An asset the caller cannot
 * read is "not found", and a search joins every row to its asset, so a component is never seen
 * without an asset the caller may read.
 *
 * <p>Every change bumps the asset's {@code updatedAt}, so the asset update event (notification
 * triggers on assets, the live stream) fires on an inventory change.
 */
@Service
public class AssetSoftwareService {

  /** Components listed for one asset, at most. */
  static final int MAX_SOFTWARE = 10_000;

  /** Rows a search across assets returns, at most. */
  static final int MAX_SEARCH = 1_000;

  private static final String OUTPUT_COLUMNS =
      "SELECT s.id, a.id, a.name, s.parentId, s.vendor, s.product, s.version, s.versionState,"
          + " s.cpe, s.purl, s.source, s.firstSeen, s.lastSeen"
          + " FROM AssetSoftware s, Asset a WHERE a.id = s.assetId";

  @PersistenceContext private EntityManager entityManager;

  // -- READ --

  /** The software of an asset, top-level components first, then by product and version. */
  public List<AssetSoftwareOutput> software(String assetId) {
    readableAsset(assetId);
    return entityManager
        .createQuery(
            OUTPUT_COLUMNS
                + " AND a.id = :assetId"
                + " ORDER BY CASE WHEN s.parentId IS NULL THEN 0 ELSE 1 END,"
                + " lower(s.product), s.version",
            Object[].class)
        .setParameter("assetId", assetId)
        .setMaxResults(MAX_SOFTWARE)
        .getResultList()
        .stream()
        .map(AssetSoftwareService::toOutput)
        .toList();
  }

  /**
   * The assets that run a software, by PURL (exact, or every version when the PURL has none), CPE
   * (exact), product name and vendor (case-insensitive). At least one criterion is required.
   */
  public List<AssetSoftwareOutput> search(String purl, String cpe, String product, String vendor) {
    String p = blankToNull(purl);
    String c = blankToNull(cpe);
    String name = blankToNull(product);
    String v = blankToNull(vendor);
    if (p == null && c == null && name == null && v == null) {
      throw new BadRequestException("Give at least one of purl, cpe, product or vendor");
    }
    StringBuilder jpql = new StringBuilder(OUTPUT_COLUMNS);
    Map<String, Object> params = new HashMap<>();
    if (p != null) {
      if (SoftwareIdentity.purlHasVersion(p)) {
        jpql.append(" AND s.purl = :purl");
        params.put("purl", p);
      } else {
        jpql.append(" AND (s.purl = :purl OR s.purl LIKE :purlPrefix ESCAPE '!')");
        params.put("purl", p);
        params.put("purlPrefix", SoftwareIdentity.escapeLike(p) + "@%");
      }
    }
    if (c != null) {
      jpql.append(" AND s.cpe = :cpe");
      params.put("cpe", c);
    }
    if (name != null) {
      jpql.append(" AND lower(s.product) = :product");
      params.put("product", name.toLowerCase(Locale.ROOT));
    }
    if (v != null) {
      jpql.append(" AND lower(s.vendor) = :vendor");
      params.put("vendor", v.toLowerCase(Locale.ROOT));
    }
    jpql.append(" ORDER BY a.name, lower(s.product), s.version");
    TypedQuery<Object[]> query = entityManager.createQuery(jpql.toString(), Object[].class);
    params.forEach(query::setParameter);
    return query.setMaxResults(MAX_SEARCH).getResultList().stream()
        .map(AssetSoftwareService::toOutput)
        .toList();
  }

  // -- WRITE --

  /** Records one component on the asset, or updates the one with the same identity. */
  public AssetSoftwareOutput upsert(
      String assetId, AssetSoftwareInput input, SoftwareSource source) {
    Asset asset = readableAsset(assetId);
    if (input.parentId() != null) {
      ownedComponent(assetId, input.parentId());
    }
    Instant now = Instant.now();
    AssetSoftware row =
        write(
            assetId,
            input.parentId(),
            input.vendor(),
            input.product(),
            input.version(),
            input.cpe(),
            input.purl(),
            source,
            now,
            new int[2]);
    touch(asset, now);
    return one(row.getId());
  }

  /**
   * Imports a CycloneDX or SPDX JSON SBOM on the asset: each component is recorded once (an
   * existing one is updated and its last seen date moved), its bundling parent kept, an unknown
   * version stored as unknown. Components already on the asset and absent from this SBOM are kept:
   * an SBOM describes one application or image, not the whole asset.
   */
  public SbomImportOutput importSbom(String assetId, JsonNode sbom) {
    Asset asset = readableAsset(assetId);
    List<ParsedComponent> components = SbomParser.parse(sbom);
    Instant now = Instant.now();
    int[] counts = new int[2];
    Map<String, String> idByRef = new HashMap<>();
    List<String> ids = new ArrayList<>();
    int unknown = 0;
    // Parents first: CycloneDX lists a parent before its children; an SPDX parent may come later,
    // so rows whose parent is not stored yet get it in a second pass.
    List<ParsedComponent> pending = new ArrayList<>();
    for (ParsedComponent c : components) {
      String parentId = c.parentRef() == null ? null : idByRef.get(c.parentRef());
      AssetSoftware row =
          write(
              assetId,
              parentId,
              c.vendor(),
              c.product(),
              c.version(),
              c.cpe(),
              c.purl(),
              SoftwareSource.SBOM,
              now,
              counts);
      idByRef.put(c.ref(), row.getId());
      ids.add(row.getId());
      if (row.getVersionState() == SoftwareVersionState.UNKNOWN) {
        unknown++;
      }
      if (c.parentRef() != null && parentId == null) {
        pending.add(c);
      }
    }
    for (ParsedComponent c : pending) {
      String parentId = idByRef.get(c.parentRef());
      String id = idByRef.get(c.ref());
      if (parentId != null && !parentId.equals(id)) {
        entityManager.find(AssetSoftware.class, id).setParentId(parentId);
      }
    }
    touch(asset, now);
    entityManager.flush();
    List<AssetSoftwareOutput> software = ids.stream().distinct().map(this::one).toList();
    return new SbomImportOutput(counts[0], counts[1], unknown, software);
  }

  /** Removes one component of the asset; the components it bundled lose their parent. */
  public void delete(String assetId, String softwareId) {
    Asset asset = readableAsset(assetId);
    AssetSoftware row = ownedComponent(assetId, softwareId);
    entityManager
        .createQuery("UPDATE AssetSoftware s SET s.parentId = NULL WHERE s.parentId = :id")
        .setParameter("id", softwareId)
        .executeUpdate();
    entityManager.remove(row);
    touch(asset, Instant.now());
  }

  // -- INTERNALS --

  private AssetSoftware write(
      String assetId,
      String parentId,
      String vendor,
      String product,
      String version,
      String cpe,
      String purl,
      SoftwareSource source,
      Instant now,
      int[] counts) {
    String productName = blankToNull(product);
    if (productName == null) {
      throw new BadRequestException("A software component needs a product name");
    }
    String cleanVersion = blankToNull(version);
    String cleanCpe = blankToNull(cpe);
    String cleanPurl = blankToNull(purl);
    String cleanVendor = blankToNull(vendor);
    String key = SoftwareIdentity.key(cleanVendor, productName, cleanVersion, cleanCpe, cleanPurl);
    AssetSoftware row =
        entityManager
            .createQuery(
                "SELECT s FROM AssetSoftware s WHERE s.assetId = :assetId AND s.key = :key",
                AssetSoftware.class)
            .setParameter("assetId", assetId)
            .setParameter("key", key)
            .getResultList()
            .stream()
            .findFirst()
            .orElse(null);
    if (row == null) {
      row = new AssetSoftware();
      row.setAssetId(assetId);
      row.setKey(key);
      row.setFirstSeen(now);
      counts[0]++;
    } else {
      counts[1]++;
    }
    if (parentId != null && !parentId.equals(row.getId())) {
      row.setParentId(parentId);
    }
    row.setVendor(cleanVendor == null ? row.getVendor() : cleanVendor);
    row.setProduct(productName);
    row.setVersion(cleanVersion);
    row.setVersionState(
        cleanVersion == null ? SoftwareVersionState.UNKNOWN : SoftwareVersionState.KNOWN);
    row.setCpe(cleanCpe == null ? row.getCpe() : cleanCpe);
    row.setPurl(cleanPurl == null ? row.getPurl() : cleanPurl);
    row.setSource(source);
    row.setLastSeen(now);
    if (row.getId() == null) {
      entityManager.persist(row);
    }
    entityManager.flush();
    return row;
  }

  /** An asset the caller can read, else "not found". */
  private Asset readableAsset(String assetId) {
    return entityManager
        .createQuery("SELECT a FROM Asset a WHERE a.id = :id", Asset.class)
        .setParameter("id", assetId)
        .getResultList()
        .stream()
        .findFirst()
        .orElseThrow(() -> new ElementNotFoundException("Asset not found: " + assetId));
  }

  /** A component of this asset, else "not found" (a component of another asset included). */
  private AssetSoftware ownedComponent(String assetId, String softwareId) {
    AssetSoftware row = entityManager.find(AssetSoftware.class, softwareId);
    if (row == null || !assetId.equals(row.getAssetId())) {
      throw new ElementNotFoundException("Software not found on this asset: " + softwareId);
    }
    return row;
  }

  /** Moves the asset's updatedAt, so its update event carries the inventory change. */
  private void touch(Asset asset, Instant now) {
    asset.setUpdatedAt(now);
  }

  private AssetSoftwareOutput one(String softwareId) {
    return entityManager
        .createQuery(OUTPUT_COLUMNS + " AND s.id = :id", Object[].class)
        .setParameter("id", softwareId)
        .getResultList()
        .stream()
        .findFirst()
        .map(AssetSoftwareService::toOutput)
        .orElseThrow(() -> new ElementNotFoundException("Software not found: " + softwareId));
  }

  private static String blankToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static AssetSoftwareOutput toOutput(Object[] r) {
    return new AssetSoftwareOutput(
        (String) r[0],
        (String) r[1],
        (String) r[2],
        (String) r[3],
        (String) r[4],
        (String) r[5],
        (String) r[6],
        (SoftwareVersionState) r[7],
        (String) r[8],
        (String) r[9],
        (SoftwareSource) r[10],
        (Instant) r[11],
        (Instant) r[12]);
  }
}
