package io.openaev.rest.audit_log;

import io.openaev.engine.model.auditlog.EsAuditLog;

/** Maps {@link EsAuditLog} engine documents to {@link AuditLogOutput} DTOs. */
public class AuditLogMapper {

  private AuditLogMapper() {}

  public static AuditLogOutput toOutput(EsAuditLog doc) {
    EsAuditLog.UserMetadata meta = doc.getUserMetadata();
    return new AuditLogOutput(
        doc.getId(),
        doc.getEventType(),
        doc.getEventStatus(),
        doc.getEventAccess(),
        doc.getEventScope(),
        doc.getUserId(),
        doc.getTenantId(),
        meta != null ? meta.getUserEmail() : null,
        meta != null ? meta.getIp() : null,
        meta != null ? meta.getUserAgent() : null,
        doc.getTimestamp(),
        doc.getCreatedAt(),
        doc.getContextData());
  }
}
