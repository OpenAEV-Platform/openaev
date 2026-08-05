package io.openaev.service.attackpath.ingestion;

import io.openaev.annotation.AllowRawJdbc;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Batched, idempotent writes for the findings copy. Each call is a single multi-row {@code INSERT
 * ... VALUES ... ON CONFLICT}, chunked to stay under the parameter limit, so a large run copies in
 * a few statements rather than one per finding.
 *
 * <p>Raw JDBC on purpose (ADR-003): the tenant inspector rejects an {@code INSERT ... SELECT} and
 * adds no guarantee to a {@code VALUES} insert, so a batched copy cannot go through Hibernate.
 * {@code tenant_id} is set explicitly on every finding row and never touched by the conflict branch
 * (which only re-stamps the row version), so no tenant guarantee is lost.
 */
@Component
@RequiredArgsConstructor
@AllowRawJdbc(
    reason =
        "batched multi-row INSERT ... ON CONFLICT for the attack-path findings copy; the inspector"
            + " rejects INSERT ... SELECT and adds no guarantee to a VALUES insert (ADR-003, as the"
            + " seed generator). tenant_id is set explicitly on every row, and the conflict branch"
            + " only re-stamps the row version.")
public class AttackPathFindingWriter {

  /** Rows per statement; 10 params each keeps well under the Postgres parameter limit. */
  private static final int CHUNK = 500;

  private final JdbcTemplate jdbcTemplate;

  /**
   * One snapshot finding row to copy. {@code field} must be non-null: a copied finding always
   * carries one ({@code Finding.field} is {@code @NotBlank}). {@code endpointId} may be null (a
   * discovered target has no asset id).
   *
   * <p>The row {@code id} is a deterministic, injective encoding of the full natural key ({@code
   * simulationId, type, field, value, endpointKey}, see {@code AttackPathIds.findingRow}), so the
   * primary key alone enforces natural-key uniqueness: the same finding always resolves to the same
   * id. A short value keeps the legacy raw encoding (stable across upgrades); only a value whose
   * raw encoding would overflow the {@code varchar(255)} primary key is hashed inside the id
   * (ADR-004 lets arbitrarily long parsed outputs reach this table); {@code value} stays {@code
   * text} for display and is never indexed.
   */
  public record FindingRow(
      String id,
      String tenantId,
      String simulationId,
      String type,
      String field,
      String value,
      String endpointId,
      String endpointRaw,
      String endpointKey,
      boolean isFinding) {}

  /** One (execution, finding) link. */
  public record Link(String executionId, String findingId) {}

  /**
   * Inserts a batch of copied findings, all stamped with {@code rowVersion} — the simulation's
   * attack-path version bumped by the caller in this same transaction (#6647, spec 002), which is
   * what makes a polling client see them as changes. The version is uniform per batch by
   * construction: one bump covers one copy.
   *
   * <p>The conflict branch re-stamps the row version ({@code DO UPDATE SET row_version =
   * EXCLUDED.row_version}) and nothing else. It has to: a finding value already in the projection
   * can be re-discovered on a later execution, and {@link #insertLinks} then adds a NEW (execution,
   * finding) link. That link is only reachable through the finding row, so a row left at its first
   * version would keep the new edge out of every delta — the finding would stay attached to its
   * original execution alone until an unrelated write happened to touch it. Re-stamping an
   * otherwise identical row costs one redundant upsert in a client's next delta, which is
   * idempotent.
   *
   * <p>Conflict is resolved on the primary key {@code attackpath_finding_id}, which is a
   * deterministic, injective encoding of the natural key (hashing the value only when its raw
   * encoding would overflow the column), so a re-copied finding always collides with itself,
   * including rows written before the hashing existed. A batch must not carry the same id twice,
   * since {@code DO UPDATE} cannot touch a row twice in one statement; the ingestion dedupes on the
   * row id before calling this.
   */
  public void insertFindings(List<FindingRow> rows, long rowVersion) {
    for (int from = 0; from < rows.size(); from += CHUNK) {
      List<FindingRow> chunk = rows.subList(from, Math.min(from + CHUNK, rows.size()));
      String sql =
          "INSERT INTO attackpath_finding (attackpath_finding_id, tenant_id,"
              + " attackpath_finding_simulation_id, attackpath_finding_type,"
              + " attackpath_finding_field, attackpath_finding_value, attackpath_finding_endpoint_id,"
              + " attackpath_finding_endpoint_raw, attackpath_finding_endpoint_key,"
              + " attackpath_finding_is_finding, attackpath_finding_row_version) VALUES "
              + String.join(
                  ", ", Collections.nCopies(chunk.size(), "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"))
              + " ON CONFLICT (attackpath_finding_id)"
              + " DO UPDATE SET attackpath_finding_row_version ="
              + " EXCLUDED.attackpath_finding_row_version,"
              + " attackpath_finding_is_finding = attackpath_finding.attackpath_finding_is_finding"
              + " OR EXCLUDED.attackpath_finding_is_finding";
      List<Object> args = new ArrayList<>(chunk.size() * 11);
      for (FindingRow r : chunk) {
        args.add(r.id());
        args.add(r.tenantId());
        args.add(r.simulationId());
        args.add(r.type());
        args.add(r.field());
        args.add(r.value());
        args.add(r.endpointId());
        args.add(r.endpointRaw());
        args.add(r.endpointKey());
        args.add(r.isFinding());
        args.add(rowVersion);
      }
      jdbcTemplate.update(sql, args.toArray());
    }
  }

  public void insertLinks(List<Link> links) {
    for (int from = 0; from < links.size(); from += CHUNK) {
      List<Link> chunk = links.subList(from, Math.min(from + CHUNK, links.size()));
      String sql =
          "INSERT INTO attackpath_execution_finding (execution_id, finding_id) VALUES "
              + String.join(", ", Collections.nCopies(chunk.size(), "(?, ?)"))
              + " ON CONFLICT (execution_id, finding_id) DO NOTHING";
      List<Object> args = new ArrayList<>(chunk.size() * 2);
      for (Link l : chunk) {
        args.add(l.executionId());
        args.add(l.findingId());
      }
      jdbcTemplate.update(sql, args.toArray());
    }
  }
}
