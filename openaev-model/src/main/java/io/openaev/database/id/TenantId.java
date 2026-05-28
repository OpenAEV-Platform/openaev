package io.openaev.database.id;

import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PersistenceProperty;

import java.io.Serializable;

public class TenantId implements Serializable {
    @Column(updatable = false)
    private final String id;
    public TenantId(String tenantId) {
        this.id = tenantId;
    }
}
