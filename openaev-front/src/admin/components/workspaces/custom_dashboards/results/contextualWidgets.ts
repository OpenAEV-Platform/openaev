import { generateFilterId } from '../../../../../components/common/queryable/filter/FilterUtils';
import { type Filter, type Widget } from '../../../../../utils/api-types';

/**
 * Synthetic, non-persisted widgets powering the drill-downs of the scenario /
 * simulation OVERVIEW visuals (MITRE ATT&CK results matrix, posture gauges).
 * Those surfaces are fed by JPA endpoints, not stored dashboard widgets, so
 * a click cannot reference a widget id: instead the results page rebuilds the
 * equivalent ad-hoc widget from the URL (widget id + source + context id) and
 * resolves it through the ad-hoc runtime endpoint, exactly like the built-in
 * home dashboard does.
 */

export const CONTEXTUAL_MITRE_WIDGET_ID = '_contextual_mitre_coverage';
export const CONTEXTUAL_POSTURE_WIDGET_ID = '_contextual_posture';

/**
 * Hero-stat drill-downs: one synthetic list widget per targeted entity type.
 * Assets and simulations carry the scenario/simulation scope on their own ES
 * document (side fields); teams and asset groups do not, so their drill-down
 * carries the explicit id list as a base_id filter through the URL filter
 * values instead.
 */
export const CONTEXTUAL_ENTITY_WIDGET_IDS: Record<string, string> = {
  'asset': '_contextual_entities_asset',
  'asset-group': '_contextual_entities_asset-group',
  'team': '_contextual_entities_team',
  'simulation': '_contextual_entities_simulation',
};

interface EntityWidgetDef {
  entity: string;
  /** i18n key of the widget (and results page) title. */
  title: string;
  /** True when the ES documents carry the scenario/simulation side field. */
  scopedByContext: boolean;
}

const ENTITY_WIDGET_DEFS: Record<string, EntityWidgetDef> = {
  [CONTEXTUAL_ENTITY_WIDGET_IDS['asset']]: {
    entity: 'asset',
    title: 'Assets',
    scopedByContext: true,
  },
  [CONTEXTUAL_ENTITY_WIDGET_IDS['asset-group']]: {
    entity: 'asset-group',
    title: 'Asset groups',
    scopedByContext: false,
  },
  [CONTEXTUAL_ENTITY_WIDGET_IDS['team']]: {
    entity: 'team',
    title: 'Teams',
    scopedByContext: false,
  },
  [CONTEXTUAL_ENTITY_WIDGET_IDS['simulation']]: {
    entity: 'simulation',
    title: 'Simulations',
    scopedByContext: true,
  },
};

export type ContextualSource = 'simulation' | 'scenario';

const CONTEXT_SCOPE_FIELD: Record<ContextualSource, string> = {
  simulation: 'base_simulation_side',
  scenario: 'base_scenario_side',
};

/** Translator signature: titles are localized at build time. */
type Translate = (key: string) => string;

const filter = (key: string, values: string[]): Filter => ({
  id: generateFilterId(),
  key,
  mode: 'or',
  values,
  operator: 'eq',
});

const NOW = new Date().toISOString();

const contextualWidget = (
  id: string,
  title: string,
  field: string,
  filters: Filter[],
  widgetType: Widget['widget_type'],
): Widget => ({
  widget_id: id,
  widget_type: widgetType,
  widget_config: {
    title,
    field,
    // A single series carrying the whole drill-down scope: the runtime
    // endpoint reuses its filter as the list perspective.
    series: [{
      name: '',
      filter: {
        mode: 'and',
        filters,
      },
    }],
    mode: 'structural',
    stacked: false,
    limit: 10000,
    widget_configuration_type: 'structural-histogram',
    time_range: 'ALL_TIME',
    date_attribute: 'base_created_at',
    display_legend: false,
  },
  // Synthetic widgets never render inside a dashboard grid: a zero layout
  // satisfies the type without carrying any meaning.
  widget_layout: {
    widget_layout_x: 0,
    widget_layout_y: 0,
    widget_layout_w: 0,
    widget_layout_h: 0,
  },
  widget_created_at: NOW,
  widget_updated_at: NOW,
});

/**
 * Rebuild the synthetic overview widget referenced by a results URL, or
 * undefined when the widget id is not a contextual one (stored / built-in
 * widgets resolve through their own endpoints).
 */
export const buildContextualWidget = (
  widgetId: string | null,
  source: string,
  contextId: string | null,
  t: Translate = key => key,
): Widget | undefined => {
  if (!widgetId || !contextId || (source !== 'simulation' && source !== 'scenario')) {
    return undefined;
  }
  const scopeFilter = filter(CONTEXT_SCOPE_FIELD[source], [contextId]);
  switch (widgetId) {
    // A single unfiltered expectation series: the drill-down must list EVERY
    // expectation of the clicked scope (success and failure alike), not one
    // status bucket.
    case CONTEXTUAL_MITRE_WIDGET_ID:
      return contextualWidget(
        CONTEXTUAL_MITRE_WIDGET_ID,
        t('Kill chain results'),
        'base_attack_patterns_side',
        [filter('base_entity', ['expectation-inject']), scopeFilter],
        'security-coverage',
      );
    case CONTEXTUAL_POSTURE_WIDGET_ID:
      return contextualWidget(
        CONTEXTUAL_POSTURE_WIDGET_ID,
        t('Results'),
        'inject_expectation_type',
        [filter('base_entity', ['expectation-inject']), scopeFilter],
        'security-coverage',
      );
    // Hero-stat entity drill-downs: a plain (non security-coverage) series so
    // the runtime conversion keeps the scope untouched. Unscoped entities
    // (teams, asset groups) receive their explicit id list as a base_id
    // filter merged from the URL filter values.
    default: {
      const def = ENTITY_WIDGET_DEFS[widgetId];
      if (!def) {
        return undefined;
      }
      return contextualWidget(
        widgetId,
        t(def.title),
        'base_entity',
        [
          filter('base_entity', [def.entity]),
          ...(def.scopedByContext ? [scopeFilter] : []),
        ],
        'horizontal-barchart',
      );
    }
  }
};

/**
 * URL of the full-page results explorer for a contextual overview drill-down,
 * with a back link to the current page. `back` must be ROUTER-RELATIVE
 * (useLocation().pathname + search): window.location includes the tenant
 * basename and would double the prefix when navigated back to.
 */
export const contextualResultsUrl = (
  widgetId: string,
  source: ContextualSource,
  contextId: string,
  back: string,
  filterValuesMap: Record<string, string[] | undefined> = {},
): string => {
  const params = new URLSearchParams();
  params.set('widget_id', widgetId);
  params.set('series_index', '0');
  params.set('source', source);
  params.set('context_id', contextId);
  // One URL param per value: comma-joining would corrupt values containing
  // commas, and empty arrays must not produce an empty-string filter value.
  Object.entries(filterValuesMap).forEach(([key, values]) => {
    (values ?? []).forEach(value => params.append(key, value));
  });
  params.set('back', back);
  return `/admin/results?${params.toString()}`;
};
