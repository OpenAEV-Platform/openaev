package io.openaev.database.id;

import jakarta.persistence.Column;

import java.io.Serializable;

public class CompositeId implements Serializable {
    private TenantId tenant;
    private String id;
    public CompositeId() {}
    public CompositeId(TenantId tenantId, String id) {
        this.tenant = tenantId;
        this.id = id;
    }
}
