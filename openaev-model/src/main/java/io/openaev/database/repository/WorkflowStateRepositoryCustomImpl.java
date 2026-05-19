package io.openaev.database.repository;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class WorkflowStateRepositoryCustomImpl implements WorkflowStateRepositoryCustom {

  private final EntityManager em;

  @Override
  @Transactional
  public void addInput(Long id, String input) {
    em.createNativeQuery(
            """
            UPDATE workflow_states
            SET workflow_state_entries = jsonb_set(
                workflow_state_entries,
                '{inputs}',
                COALESCE(workflow_state_entries->'inputs', '[]'::jsonb) ||
                CAST(:input AS jsonb)
            )
            WHERE workflow_state_id = :state_id
        """)
        .setParameter("state_id", id)
        .setParameter("input", input)
        .executeUpdate();
  }

  @Override
  @Transactional
  public void addCorrelated(Long id, String correlated) {
    em.createNativeQuery(
            """
            UPDATE workflow_states
            SET workflow_state_entries = jsonb_set(
                workflow_state_entries,
                '{correlated}',
                COALESCE(workflow_state_entries->'correlated', '[]'::jsonb) ||
                CAST(:correlated AS jsonb)
            )
            WHERE workflow_state_id = :state_id
        """)
        .setParameter("state_id", id)
        .setParameter("correlated", correlated)
        .executeUpdate();
  }

  @Override
  @Transactional
  public void addHash(Long id, String hashExecution) {
    em.createNativeQuery(
            """
            UPDATE workflow_states
            SET workflow_state_entries = jsonb_set(
                workflow_state_entries,
                '{hashExecution}',
                COALESCE(workflow_state_entries->'hashExecution', '[]'::jsonb) ||
                jsonb_build_array(CAST(:hashExecution AS text))
            )
            WHERE workflow_state_id = :state_id
        """)
        .setParameter("state_id", id)
        .setParameter("hashExecution", hashExecution)
        .executeUpdate();
  }
}
