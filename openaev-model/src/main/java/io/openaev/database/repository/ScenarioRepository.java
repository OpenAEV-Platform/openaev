package io.openaev.database.repository;

import io.openaev.context.ExecState;
import io.openaev.context.TenantProxy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScenarioRepository {

  /** Internal Spring Data bean — never injected or used directly by external classes. */
  private final ScenarioJpaRepository internal;

  public ScenarioJpaRepository forOp(ExecState state) {
    return TenantProxy.of(internal, ScenarioJpaRepository.class, state);
  }
}
