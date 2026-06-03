import type { APIRequestContext } from '@playwright/test';

/**
 * Minimal TypeScript shape of a backend {@code LogEvent} document.
 * Maps the JSON fields produced by {@code LogEvent.java}.
 */
export interface AuditLogEvent {
  id: string;
  entity_type: string;
  created_at: string;
  timestamp: string;
  /** High-level category: {@code "mutation"} or {@code "authentication"}. */
  event_type: string;
  /** Outcome: {@code "success"} or {@code "error"}. */
  event_status: string;
  /** Access classification: {@code "administration"} or {@code "extended"}. */
  event_access: string;
  /**
   * Specific action: {@code "create"}, {@code "update"}, {@code "delete"},
   * {@code "status_change"}, {@code "login"}, {@code "logout"}, {@code "unauthorized"}.
   */
  event_scope: string;
  user_id: string | null;
  tenant_id: string | null;
  user_metadata?: {
    user_email?: string;
    user_agent?: string;
    x_forwarded_for?: string;
    ip?: string;
    session_id?: string;
  };
  request_metadata?: {
    url?: string;
    method?: string;
    signature?: unknown;
  };
  context_data?: Record<string, string | number | boolean | null>;
}

/**
 * API helper for querying audit-log events in end-to-end tests.
 *
 * Targets the {@code POST /api/audit-logs/search} endpoint that indexes
 * events via the {@code engine} transport (Elasticsearch / OpenSearch).
 *
 * ⚠️ NOTE: The {@code /api/audit-logs/search} endpoint is planned but
 * not yet implemented. These helpers will return empty arrays ({@code []})
 * until that endpoint is available.
 */
class AuditLogApiHelpers {
  private readonly auditLogSearchUri = '/api/audit-logs/search';

  constructor(private readonly request: APIRequestContext) {}

  /**
   * Searches all audit-log events and returns those whose request URL
   * contains the given scenario ID.
   *
   * Uses a recursive retry strategy to avoid the ESLint no-await-in-loop rule
   * while compensating for the async nature of Elasticsearch indexing.
   */
  async searchByScenarioId(
    scenarioId: string,
    expectedMinCount = 3,
    maxRetries = 8,
    retryDelayMs = 1000,
  ): Promise<AuditLogEvent[]> {
    return this.retry(scenarioId, expectedMinCount, maxRetries, retryDelayMs);
  }

  private async retry(
    scenarioId: string,
    expectedMinCount: number,
    remainingRetries: number,
    retryDelayMs: number,
  ): Promise<AuditLogEvent[]> {
    const events = await this.fetchAllEvents();
    const lifecycle = events.filter(e => this.isScenarioLifecycleEvent(e, scenarioId));
    if (lifecycle.length >= expectedMinCount || remainingRetries <= 0) {
      return lifecycle;
    }
    await this.delay(retryDelayMs);
    return this.retry(scenarioId, expectedMinCount, remainingRetries - 1, retryDelayMs);
  }

  /**
   * Fetches all audit log events from the search endpoint.
   * Returns an empty array when the endpoint is not yet available (HTTP 404).
   */
  async fetchAllEvents(page = 0, size = 100): Promise<AuditLogEvent[]> {
    try {
      const response = await this.request.post(this.auditLogSearchUri, {
        data: {
          page,
          size,
        },
      });
      if (response.status() === 404) {
        return [];
      }
      if (!response.ok()) {
        return [];
      }
      const body = await response.json();
      return (body.content ?? []) as AuditLogEvent[];
    } catch {
      return [];
    }
  }

  private isScenarioLifecycleEvent(event: AuditLogEvent, scenarioId: string): boolean {
    const url = event.request_metadata?.url ?? '';
    if (url.includes(`/scenarios/${scenarioId}`)) {
      return true;
    }
    const ctx = event.context_data ?? {};
    return (
      ctx['resource_id'] === scenarioId
      || ctx['parent_id'] === scenarioId
      || ctx['scenario_id'] === scenarioId
    );
  }

  private delay(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
  }
}

export default AuditLogApiHelpers;
