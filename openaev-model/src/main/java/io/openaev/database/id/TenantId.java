package io.openaev.database.id;

import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PersistenceProperty;

import java.io.Serializable;

public class TenantId implements Serializable {
    @Column(name = "tenant_id", updatable = false)
    private String id;
    public TenantId() {}
    public TenantId(String tenantId) {
        this.id = tenantId;
    }
}
