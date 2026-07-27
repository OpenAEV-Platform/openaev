import { type AxiosRequestConfig } from 'axios';
import { useEffect, useMemo, useState } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { simpleCall, simplePostCall } from '../../../../utils/Action';
import {
  type Domain,
  type EsAvgs,
  type EsBase,
  type EsCountInterval,
  type EsSeries,
  type Option,
  type Reporting,
} from '../../../../utils/api-types';
import useAuth from '../../../../utils/hooks/useAuth';
import {
  buildAttackPathsConfig,
  buildFailedExpectationsConfig,
  buildFindingsByTypeConfig,
  buildInjectCountConfig,
  buildLatestFindingsConfig,
  buildMitreConfig,
  buildPostureConfig,
  buildSecurityDomainsConfig,
  buildTrendsConfig,
  EXPECTATION_TYPES,
  type ReportingContextType,
} from './reportingRenderQueries';

/**
 * Data layer of the standalone reporting render page.
 *
 * All requests funnel through the two `adhoc`/`call` helpers below so the
 * optional generation token (headless PDF rendering) is appended as a `token`
 * query parameter in exactly one place. Every fetch settles independently: a
 * failed query marks its module data as 'error' (the module renders an inline
 * error block) and never rejects the whole page.
 */

// -- PUBLIC TYPES ------------------------------------------------------------

export type ModuleDataStatus = 'loading' | 'success' | 'error' | 'unsupported';

export interface ModuleDataState<T> {
  status: ModuleDataStatus;
  data?: T;
}

export interface ReportingSubject {
  name: string;
  description?: string;
  /** Raw entity payload, consumed by the subject details module. */
  raw: Record<string, unknown>;
}

export interface PostureBreakdownEntry {
  type: string;
  success: number;
  failed: number;
}

export interface PostureData {
  success: number;
  failed: number;
  tested: number;
  breakdown: PostureBreakdownEntry[];
}

export interface MitreTechniqueEntry {
  id: string;
  label: string;
  success: number;
  failed: number;
}

export interface MitreData {
  /** Distinct techniques exercised in the window. */
  coveredCount: number;
  /** Top techniques by expectation volume, names resolved. */
  techniques: MitreTechniqueEntry[];
  /** Kill chain names the module is scoped to; empty = all kill chains. */
  killChains: string[];
}

export interface TrendPoint {
  date: string;
  success: number;
  failed: number;
}

export interface FailedExpectationRow {
  injectTitle: string;
  expectationName: string;
  expectationType: string;
  date?: string;
}

export interface FindingTypeEntry {
  type: string;
  count: number;
}

export interface FindingRow {
  value: string;
  type: string;
  date?: string;
}

export interface FindingsData {
  byType: FindingTypeEntry[];
  latest: FindingRow[];
}

export interface AttackPathPhaseEntry {
  id: string;
  name: string;
  order: number;
  count: number;
}

export interface SecurityDomainsData {
  /** Raw engine averages: per-domain expectation series (as the home band). */
  avgs: EsAvgs;
  /** Full domain referential, so untested domains still show as gaps. */
  domains: Domain[];
}

export interface ReportingRenderData {
  subject: ModuleDataState<ReportingSubject>;
  posture: ModuleDataState<PostureData>;
  injectCount: ModuleDataState<number>;
  mitre: ModuleDataState<MitreData>;
  securityDomains: ModuleDataState<SecurityDomainsData>;
  trends: ModuleDataState<TrendPoint[]>;
  failedExpectations: ModuleDataState<FailedExpectationRow[]>;
  findings: ModuleDataState<FindingsData>;
  attackPaths: ModuleDataState<AttackPathPhaseEntry[]>;
  /** True once every needed query settled (success, error or unsupported). */
  allSettled: boolean;
}

// -- INTERNALS ---------------------------------------------------------------

type DataKind = 'subject' | 'posture' | 'injectCount' | 'mitre' | 'securityDomains' | 'trends' | 'failedExpectations' | 'findings' | 'attackPaths';

/** Data kinds needed by each module type (COVER needs the subject name). */
const MODULE_DATA_KINDS: Record<string, DataKind[]> = {
  COVER: ['subject'],
  EXECUTIVE_SUMMARY: ['subject', 'posture', 'injectCount'],
  SUBJECT_DETAILS: ['subject'],
  MITRE_COVERAGE: ['mitre'],
  RESULTS_BREAKDOWN: ['posture'],
  SECURITY_DOMAINS: ['securityDomains'],
  SCORE_TRENDS: ['trends'],
  FAILED_EXPECTATIONS: ['failedExpectations'],
  FINDINGS: ['findings'],
  ATTACK_PATHS: ['attackPaths'],
  CUSTOM_MARKDOWN: [],
};

/** REST URI resolving the report subject entity, per context type. */
const SUBJECT_URI: Partial<Record<ReportingContextType, (id: string) => string>> = {
  SIMULATION: id => `/api/exercises/${id}`,
  SCENARIO: id => `/api/scenarios/${id}`,
  ATOMIC_TESTING: id => `/api/atomic-testings/${id}`,
  ENDPOINT: id => `/api/endpoints/${id}`,
  ASSET_GROUP: id => `/api/asset_groups/${id}`,
  TEAM: id => `/api/teams/${id}`,
};

/** Candidate name / description attributes across the subject entity shapes. */
const NAME_KEYS = ['exercise_name', 'scenario_name', 'inject_title', 'asset_name', 'endpoint_name', 'asset_group_name', 'team_name', 'user_email'];
const DESCRIPTION_KEYS = ['exercise_description', 'scenario_description', 'inject_description', 'asset_description', 'asset_group_description', 'team_description'];

const firstString = (raw: Record<string, unknown>, keys: string[]): string | undefined => {
  for (const key of keys) {
    const value = raw[key];
    if (typeof value === 'string' && value.length > 0) return value;
  }
  return undefined;
};

/** Sum of every bucket of a named series. */
const namedSeriesTotal = (series: EsSeries[], name: string): number =>
  (series.find(s => s.label === name)?.data ?? []).reduce((acc, bucket) => acc + (bucket.value ?? 0), 0);

/** Per-bucket totals of a named series, keyed by the raw bucket key. */
const namedSeriesBuckets = (series: EsSeries[], name: string): Record<string, number> =>
  (series.find(s => s.label === name)?.data ?? []).reduce<Record<string, number>>((acc, bucket) => {
    const key = bucket.key ?? bucket.label;
    if (key) acc[key] = (acc[key] ?? 0) + (bucket.value ?? 0);
    return acc;
  }, {});

const asString = (value: unknown): string | undefined => (typeof value === 'string' ? value : undefined);

const initialState = <T>(): ModuleDataState<T> => ({ status: 'loading' });

// -- HOOK --------------------------------------------------------------------

const useReportingRenderData = (reporting: Reporting | null, token: string | null): ReportingRenderData => {
  const { t } = useFormatter();
  const { settings } = useAuth();
  const platformName = settings?.platform_name;

  const [subject, setSubject] = useState<ModuleDataState<ReportingSubject>>(initialState);
  const [posture, setPosture] = useState<ModuleDataState<PostureData>>(initialState);
  const [injectCount, setInjectCount] = useState<ModuleDataState<number>>(initialState);
  const [mitre, setMitre] = useState<ModuleDataState<MitreData>>(initialState);
  const [securityDomains, setSecurityDomains] = useState<ModuleDataState<SecurityDomainsData>>(initialState);
  const [trends, setTrends] = useState<ModuleDataState<TrendPoint[]>>(initialState);
  const [failedExpectations, setFailedExpectations] = useState<ModuleDataState<FailedExpectationRow[]>>(initialState);
  const [findings, setFindings] = useState<ModuleDataState<FindingsData>>(initialState);
  const [attackPaths, setAttackPaths] = useState<ModuleDataState<AttackPathPhaseEntry[]>>(initialState);

  useEffect(() => {
    if (!reporting) return undefined;
    let cancelled = false;

    // Single place where the generation token reaches the network layer: every
    // request of the render page carries it as a `token` query parameter so
    // the backend can authorize headless (cookie-less) PDF captures.
    const requestConfig: AxiosRequestConfig | undefined = token ? { params: { token } } : undefined;
    // Error snackbars are meaningless on a print page: failures surface as
    // inline module error blocks instead.
    const call = (uri: string) => simpleCall(uri, requestConfig, false);
    const post = (uri: string, data: unknown) => simplePostCall(uri, data, requestConfig, false);
    const adhoc = (endpoint: 'series' | 'count' | 'entities' | 'average', body: Record<string, unknown>) =>
      post(`/api/dashboards/adhoc/${endpoint}`, body);

    const contextType = reporting.reporting_context_type;
    const contextId = reporting.reporting_context_id;
    const timeRange = reporting.reporting_time_range;

    const neededKinds = new Set<DataKind>(
      (reporting.reporting_modules ?? []).flatMap(
        module => (module.module_type ? MODULE_DATA_KINDS[module.module_type] : undefined) ?? [],
      ),
    );
    // The cover and the page header always display the subject name.
    neededKinds.add('subject');

    const settle = <T>(setter: (state: ModuleDataState<T>) => void, promise: Promise<T>) => {
      promise
        .then((data) => {
          if (!cancelled) setter({
            status: 'success',
            data,
          });
        })
        .catch(() => {
          if (!cancelled) setter({ status: 'error' });
        });
    };

    // -- Subject --
    if (contextType === 'PLATFORM') {
      setSubject({
        status: 'success',
        data: {
          name: platformName ?? t('Platform'),
          raw: {},
        },
      });
    } else if (!contextId) {
      setSubject({ status: 'error' });
    } else if (contextType === 'PLAYER') {
      // Players have no GET-by-id endpoint: resolve the display name through
      // the options endpoint (details degrade to name-only, by design).
      setSubject(initialState());
      settle(setSubject, post('/api/players/options', [contextId]).then((result: { data: Option[] }) => {
        const option = result.data.find(o => o.id === contextId) ?? result.data[0];
        if (!option?.label) throw new Error('Player not found');
        return {
          name: option.label,
          raw: {},
        };
      }));
    } else {
      setSubject(initialState());
      const uri = SUBJECT_URI[contextType]?.(contextId);
      if (uri) {
        settle(setSubject, call(uri).then((result: { data: Record<string, unknown> }) => ({
          name: firstString(result.data, NAME_KEYS) ?? contextId,
          description: firstString(result.data, DESCRIPTION_KEYS),
          raw: result.data,
        })));
      } else {
        setSubject({ status: 'error' });
      }
    }

    // -- Posture (executive summary + results breakdown) --
    if (neededKinds.has('posture')) {
      setPosture(initialState());
      settle(setPosture, adhoc('series', { widget_config: buildPostureConfig(contextType, contextId, timeRange) })
        .then((result: { data: EsSeries[] }) => {
          const breakdown = EXPECTATION_TYPES
            .map(type => ({
              type,
              success: namedSeriesTotal(result.data, `${type}_SUCCESS`),
              failed: namedSeriesTotal(result.data, `${type}_FAILED`),
            }))
            .filter(entry => entry.success + entry.failed > 0);
          const success = breakdown.reduce((acc, entry) => acc + entry.success, 0);
          const failed = breakdown.reduce((acc, entry) => acc + entry.failed, 0);
          return {
            success,
            failed,
            tested: success + failed,
            breakdown,
          };
        }));
    } else {
      setPosture({ status: 'unsupported' });
    }

    // -- Injects executed --
    if (neededKinds.has('injectCount')) {
      const config = buildInjectCountConfig(contextType, contextId, timeRange);
      if (!config) {
        setInjectCount({ status: 'unsupported' });
      } else {
        setInjectCount(initialState());
        settle(setInjectCount, adhoc('count', { widget_config: config })
          .then((result: { data: EsCountInterval }) => result.data.interval_count ?? 0));
      }
    } else {
      setInjectCount({ status: 'unsupported' });
    }

    // -- MITRE coverage --
    if (neededKinds.has('mitre')) {
      // Optional kill chain scoping from the module config (empty = all kill
      // chains, the historical behavior). Expectation documents only carry
      // attack pattern ids - kill chain membership is resolved through the
      // referential and applied client-side.
      const coverageModule = (reporting.reporting_modules ?? []).find(module => module.module_type === 'MITRE_COVERAGE');
      const rawKillChains = coverageModule?.module_config?.kill_chains;
      const selectedKillChains: string[] = Array.isArray(rawKillChains)
        ? (rawKillChains as unknown[]).filter((name): name is string => typeof name === 'string')
        : [];
      setMitre(initialState());
      settle(setMitre, adhoc('series', { widget_config: buildMitreConfig(contextType, contextId, timeRange) })
        .then(async (result: { data: EsSeries[] }) => {
          const successBuckets = namedSeriesBuckets(result.data, 'SUCCESS');
          const failedBuckets = namedSeriesBuckets(result.data, 'FAILED');
          let ids = [...new Set([...Object.keys(successBuckets), ...Object.keys(failedBuckets)])];
          if (ids.length > 0 && selectedKillChains.length > 0) {
            // Keep only the techniques carrying at least one phase of a
            // selected kill chain. One referential fetch per generation is
            // acceptable on a print page.
            const [phasesResult, patternsResult]: [
              { data: Record<string, unknown>[] },
              { data: Record<string, unknown>[] },
            ] = await Promise.all([call('/api/kill_chain_phases'), call('/api/attack_patterns')]);
            const selectedNames = new Set(selectedKillChains);
            const selectedPhaseIds = new Set(phasesResult.data
              .filter(phase => selectedNames.has(asString(phase.phase_kill_chain_name) ?? ''))
              .map(phase => asString(phase.phase_id) ?? ''));
            const coveredPatternIds = new Set(patternsResult.data
              .filter((pattern) => {
                const phases = pattern.attack_pattern_kill_chain_phases;
                return Array.isArray(phases) && phases.some(phaseId => selectedPhaseIds.has(phaseId as string));
              })
              .map(pattern => asString(pattern.attack_pattern_id) ?? ''));
            ids = ids.filter(id => coveredPatternIds.has(id));
          }
          const top = ids
            .map(id => ({
              id,
              success: successBuckets[id] ?? 0,
              failed: failedBuckets[id] ?? 0,
            }))
            .sort((a, b) => (b.success + b.failed) - (a.success + a.failed))
            .slice(0, 12);
          // Resolve technique names for the displayed rows only (the options
          // endpoint is lightweight, unlike the full referential).
          let labels: Record<string, string> = {};
          if (top.length > 0) {
            const options: { data: Option[] } = await post('/api/attack_patterns/options', top.map(entry => entry.id));
            labels = Object.fromEntries(options.data.map(option => [option.id ?? '', option.label ?? '']));
          }
          return {
            coveredCount: ids.length,
            techniques: top.map(entry => ({
              ...entry,
              label: labels[entry.id] || entry.id,
            })),
            killChains: selectedKillChains,
          };
        }));
    } else {
      setMitre({ status: 'unsupported' });
    }

    // -- Performance by security domain --
    if (neededKinds.has('securityDomains')) {
      setSecurityDomains(initialState());
      settle(setSecurityDomains, Promise.all([
        adhoc('average', { widget_config: buildSecurityDomainsConfig(contextType, contextId, timeRange) }),
        // The referential keeps untested domains visible (the home band does
        // the same through the Redux domain store).
        call('/api/domains'),
      ]).then(([avgsResult, domainsResult]: [{ data: EsAvgs }, { data: Domain[] }]) => ({
        avgs: avgsResult.data,
        domains: domainsResult.data,
      })));
    } else {
      setSecurityDomains({ status: 'unsupported' });
    }

    // -- Score trends --
    if (neededKinds.has('trends')) {
      setTrends(initialState());
      settle(setTrends, adhoc('series', { widget_config: buildTrendsConfig(contextType, contextId, timeRange) })
        .then((result: { data: EsSeries[] }) => {
          const success = result.data.find(s => s.label === 'SUCCESS')?.data ?? [];
          const failed = result.data.find(s => s.label === 'FAILED')?.data ?? [];
          const byDate = new Map<string, TrendPoint>();
          const upsert = (key: string | undefined) => {
            if (!key) return undefined;
            if (!byDate.has(key)) byDate.set(key, {
              date: key,
              success: 0,
              failed: 0,
            });
            return byDate.get(key);
          };
          success.forEach((bucket) => {
            const point = upsert(bucket.key ?? bucket.label);
            if (point) point.success += bucket.value ?? 0;
          });
          failed.forEach((bucket) => {
            const point = upsert(bucket.key ?? bucket.label);
            if (point) point.failed += bucket.value ?? 0;
          });
          return [...byDate.values()].sort((a, b) => a.date.localeCompare(b.date));
        }));
    } else {
      setTrends({ status: 'unsupported' });
    }

    // -- Failed expectations --
    if (neededKinds.has('failedExpectations')) {
      setFailedExpectations(initialState());
      settle(setFailedExpectations, adhoc('entities', { widget_config: buildFailedExpectationsConfig(contextType, contextId, timeRange) })
        .then((result: { data: { es_datas?: EsBase[] } }) => (result.data.es_datas ?? []).map((entity) => {
          const raw = entity as unknown as Record<string, unknown>;
          return {
            injectTitle: asString(raw.inject_title) ?? asString(raw.base_representative) ?? '-',
            expectationName: asString(raw.inject_expectation_name) ?? '-',
            expectationType: asString(raw.inject_expectation_type) ?? '-',
            date: asString(raw.base_created_at),
          };
        })));
    } else {
      setFailedExpectations({ status: 'unsupported' });
    }

    // -- Findings --
    if (neededKinds.has('findings')) {
      const byTypeConfig = buildFindingsByTypeConfig(contextType, contextId, timeRange);
      const latestConfig = buildLatestFindingsConfig(contextType, contextId, timeRange);
      if (!byTypeConfig || !latestConfig) {
        setFindings({ status: 'unsupported' });
      } else {
        setFindings(initialState());
        settle(setFindings, Promise.all([
          adhoc('series', { widget_config: byTypeConfig }),
          adhoc('entities', { widget_config: latestConfig }),
        ]).then(([seriesResult, entitiesResult]: [{ data: EsSeries[] }, { data: { es_datas?: EsBase[] } }]) => {
          const buckets = (seriesResult.data[0]?.data ?? [])
            .map(bucket => ({
              type: bucket.label ?? bucket.key ?? '-',
              count: bucket.value ?? 0,
            }))
            .filter(entry => entry.count > 0)
            .sort((a, b) => b.count - a.count);
          const latest = (entitiesResult.data.es_datas ?? []).map((entity) => {
            const raw = entity as unknown as Record<string, unknown>;
            return {
              value: asString(raw.finding_value) ?? asString(raw.base_representative) ?? '-',
              type: asString(raw.finding_type) ?? '-',
              date: asString(raw.base_created_at),
            };
          });
          return {
            byType: buckets,
            latest,
          };
        }));
      }
    } else {
      setFindings({ status: 'unsupported' });
    }

    // -- Attack paths (kill chain phase progression) --
    if (neededKinds.has('attackPaths')) {
      const config = buildAttackPathsConfig(contextType, contextId, timeRange);
      if (!config) {
        setAttackPaths({ status: 'unsupported' });
      } else {
        setAttackPaths(initialState());
        settle(setAttackPaths, Promise.all([
          adhoc('series', { widget_config: config }),
          call('/api/kill_chain_phases'),
        ]).then(([seriesResult, phasesResult]: [{ data: EsSeries[] }, { data: Record<string, unknown>[] }]) => {
          const counts = namedSeriesBuckets(seriesResult.data, seriesResult.data[0]?.label ?? '');
          const phases = phasesResult.data
            .map(phase => ({
              id: asString(phase.phase_id) ?? '',
              name: asString(phase.phase_name) ?? '',
              order: typeof phase.phase_order === 'number' ? phase.phase_order : 0,
              count: counts[asString(phase.phase_id) ?? ''] ?? 0,
            }))
            .filter(phase => phase.count > 0)
            .sort((a, b) => a.order - b.order);
          return phases;
        }));
      }
    } else {
      setAttackPaths({ status: 'unsupported' });
    }

    return () => {
      cancelled = true;
    };
    // platformName / t are stable enough for this one-shot page; keying the
    // effect on them would refetch everything on locale hydration.
  }, [reporting?.reporting_id, token]);

  const allSettled = useMemo(() => {
    if (!reporting) return false;
    return [subject, posture, injectCount, mitre, securityDomains, trends, failedExpectations, findings, attackPaths]
      .every(state => state.status !== 'loading');
  }, [reporting, subject, posture, injectCount, mitre, securityDomains, trends, failedExpectations, findings, attackPaths]);

  return {
    subject,
    posture,
    injectCount,
    mitre,
    securityDomains,
    trends,
    failedExpectations,
    findings,
    attackPaths,
    allSettled,
  };
};

export default useReportingRenderData;
