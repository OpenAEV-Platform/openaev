package io.openaev.utils.fixtures;

import io.openaev.context.TenantContext;
import io.openaev.database.model.Asset;
import io.openaev.database.model.Tenant;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public class AssetFixture {

  public static Asset createDefaultAsset(@NotNull final String name) {
    Asset asset = new Asset();
    asset.setCreatedAt(Instant.now());
    asset.setUpdatedAt(Instant.now());
    asset.setName(name);
    asset.setDescription("asset description");
    // assets is tenant-active, and Asset deliberately KEEPS TenantBaseListener for now (see the
    // note on Asset itself; #7844 removes it), so an insert would not fail without this. Stamping
    // the tenant here anyway is what lets #7844 be a change to production code alone, and it
    // mirrors SecurityCoverageFixture and AssetGroupFixture rather than leaving every call site to
    // remember it. TenantContext.getCurrentTenant() never throws (it defaults to
    // Tenant.DEFAULT_TENANT_UUID), so this is safe outside a request too.
    asset.setTenant(new Tenant(TenantContext.getCurrentTenant()));
    return asset;
  }
}
