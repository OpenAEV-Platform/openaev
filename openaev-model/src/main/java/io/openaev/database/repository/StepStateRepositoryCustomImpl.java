package io.openaev.database.repository;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class StepStateRepositoryCustomImpl implements StepStateRepositoryCustom {

  private final EntityManager em;

  @Override
  @Transactional
  public void addInput(Long id, String input) {
    em.createNativeQuery(
            """
            UPDATE steps_states
            SET step_entries = jsonb_set(
                step_entries,
                '{inputs}',
                COALESCE(step_entries->'inputs', '[]'::jsonb) ||
                CAST(:input AS jsonb)
            )
            WHERE state_id = :state_id
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
            UPDATE steps_states
            SET step_entries = jsonb_set(
                step_entries,
                '{correlated}',
                COALESCE(step_entries->'correlated', '[]'::jsonb) ||
                CAST(:correlated AS jsonb)
            )
            WHERE state_id = :state_id
        """)
        .setParameter("state_id", id)
        .setParameter("correlated", correlated)
        .executeUpdate();
  }

  @Override
  @Transactional
  public void addHash(Long id, Long hashExecution) {
    em.createNativeQuery(
            """
            UPDATE steps_states
            SET step_entries = jsonb_set(
                step_entries,
                '{hashExecution}',
                COALESCE(step_entries->'hashExecution', '[]'::jsonb) ||
                CAST(:hashExecution AS jsonb)
            )
            WHERE state_id = :state_id
        """)
        .setParameter("state_id", id)
        .setParameter("hashExecution", hashExecution)
        .executeUpdate();
  }
}
