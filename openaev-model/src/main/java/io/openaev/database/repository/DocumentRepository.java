package io.openaev.database.repository;

import io.openaev.context.ExecState;
import io.openaev.context.TenantProxy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DocumentRepository {

  /** Internal Spring Data bean — never injected or used directly by external classes. */
  private final DocumentJpaRepository internal;

  public DocumentJpaRepository forOp(ExecState state) {
    return TenantProxy.of(internal, DocumentJpaRepository.class, state);
  }
}
