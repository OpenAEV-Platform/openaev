import { ArrowBackOutlined } from '@mui/icons-material';
import { Chip, IconButton, Paper, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { useLocalStorage } from 'usehooks-ts';

import { type SecurityPlatformHelper } from '../../../actions/assets/asset-helper';
import { fetchSecurityPlatforms } from '../../../actions/assets/securityPlatform-actions';
import { type AttackPatternHelper } from '../../../actions/attack_patterns/attackpattern-helper';
import { adHocEntitiesRuntime } from '../../../actions/dashboards/dashboard-action';
import { type DomainHelper } from '../../../actions/domains/domain-helper';
import Breadcrumbs from '../../../components/Breadcrumbs';
import { useFormatter } from '../../../components/i18n';
import Loader from '../../../components/Loader';
import { useHelper } from '../../../store';
import {
  type AttackPattern,
  type Domain,
  type EsEntities,
  type ListConfiguration,
  type Pagination,
  type SecurityPlatform,
} from '../../../utils/api-types';
import { useAppDispatch } from '../../../utils/hooks';
import useDataLoader from '../../../utils/hooks/useDataLoader';
import ListWidget from '../workspaces/custom_dashboards/widgets/viz/list/ListWidget';
import { buildDefaultHomeWidgets, type DefaultTimeRange } from './defaultHomeWidgets';

const RESERVED_PARAMS = ['widget_id', 'series_index'];

// Human labels for the technical filter keys carried in the URL.
const FILTER_KEY_LABELS: Record<string, string> = {
  inject_expectation_type: 'Type',
  inject_expectation_status: 'Status',
  base_security_domains_side: 'Security domain',
  base_security_platforms_side: 'Security platform',
  base_attack_patterns_side: 'Attack pattern',
  finding_type: 'Finding type',
  date: 'Date',
};

// i18n keys for known enum-ish filter values; unknown values render raw
// (never through t(), which would log a missing-translation error per render).
const FILTER_VALUE_LABEL_KEYS: Record<string, string> = {
  SUCCESS: 'Success',
  FAILED: 'Failed',
  PENDING: 'Pending',
  DETECTION: 'Detection',
  PREVENTION: 'Prevention',
  VULNERABILITY: 'Vulnerability',
  MANUAL: 'Manual',
  CVE: 'Cve',
  PORTSSCAN: 'Portsscan',
};

/**
 * Full-page drill-down for the built-in home dashboard: every click on a
 * score, bar, gate or gauge lands here with the widget id and the clicked
 * scope in the URL. The ad-hoc runtime endpoint converts the widget into a
 * scoped entity list (expectations, findings, injects, simulations...) and
 * each row navigates to the actual underlying object.
 */
const DefaultHomeResults = () => {
  const theme = useTheme();
  const { t, fldt } = useFormatter();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const [searchParams] = useSearchParams();

  // id -> name resolution for filter chips
  const { domains, securityPlatforms, attackPatterns }: {
    domains: Domain[];
    securityPlatforms: SecurityPlatform[];
    attackPatterns: AttackPattern[];
  } = useHelper((helper: DomainHelper & SecurityPlatformHelper & AttackPatternHelper) => ({
    domains: helper.getDomains(),
    securityPlatforms: helper.getSecurityPlatforms(),
    attackPatterns: helper.getAttackPatterns(),
  }));
  useDataLoader(() => {
    dispatch(fetchSecurityPlatforms());
  });

  const widgetId = searchParams.get('widget_id');
  const seriesIndex = Number(searchParams.get('series_index') ?? 0);

  // Values are carried as one URL param per value (repeatable keys).
  const filterValues = useMemo(() => Object.fromEntries(
    [...new Set(searchParams.keys())]
      .filter(key => !RESERVED_PARAMS.includes(key))
      .map(key => [key, searchParams.getAll(key).filter(value => value !== '')]),
  ), [searchParams]);

  // Same time range as the home dashboard so the list matches what was clicked.
  const [timeRange] = useLocalStorage<DefaultTimeRange>('default-home-dashboard-time-range', 'LAST_QUARTER');
  const widget = useMemo(
    () => buildDefaultHomeWidgets(timeRange, t).find(w => w.widget_id === widgetId),
    [timeRange, widgetId, t],
  );

  const [paginatedEntities, setPaginatedEntities] = useState<EsEntities>();
  const [listConfig, setListConfig] = useState<ListConfiguration | null>();
  const [initialLoading, setInitialLoading] = useState(true);
  const [contentLoading, setContentLoading] = useState(false);
  // Monotonic request id: a slow earlier response must never overwrite a newer one.
  const requestIdRef = useRef(0);

  const fetchResults = useCallback(async (pagination?: Pagination) => {
    if (!widget) return;
    requestIdRef.current += 1;
    const requestId = requestIdRef.current;
    await adHocEntitiesRuntime(widget.widget_type, widget.widget_config, {
      filter_values_map: filterValues,
      series_index: seriesIndex,
      parameters: {},
      pagination: pagination ?? {
        page: 0,
        size: 20,
      },
    }).then(({ data }) => {
      if (requestId !== requestIdRef.current) return;
      setPaginatedEntities(data.es_entities);
      setListConfig(data.list_configuration);
    }).catch(() => {
      if (requestId !== requestIdRef.current) return;
      setListConfig(null);
    });
  }, [widget, filterValues, seriesIndex]);

  useEffect(() => {
    setInitialLoading(true);
    fetchResults().then(() => setInitialLoading(false));
  }, [fetchResults]);

  const onPaginationChange = (pagination: Pagination) => {
    setContentLoading(true);
    fetchResults(pagination).then(() => setContentLoading(false));
  };

  // Resolve raw filter values (uuids, enums, dates) into readable labels.
  const resolveValue = useCallback((key: string, value: string): string => {
    if (key === 'base_security_domains_side') {
      return domains.find(d => d.domain_id === value)?.domain_name ?? value;
    }
    if (key === 'base_security_platforms_side') {
      return securityPlatforms.find(p => p.asset_id === value)?.asset_name ?? value;
    }
    if (key === 'base_attack_patterns_side') {
      const pattern = attackPatterns.find(a => a.attack_pattern_id === value);
      return pattern ? `${pattern.attack_pattern_external_id} - ${pattern.attack_pattern_name}` : value;
    }
    if (key === 'date') {
      return fldt(value);
    }
    const labelKey = FILTER_VALUE_LABEL_KEYS[value.toUpperCase()];
    return labelKey ? t(labelKey) : value;
  }, [domains, securityPlatforms, attackPatterns, t, fldt]);

  // Titles are already localized at build time (buildDefaultHomeWidgets).
  const widgetTitle = widget?.widget_config.title ?? t('Results');
  const total = paginatedEntities?.total ?? 0;

  if (!widgetId || !widget) {
    return (
      <Typography variant="subtitle1" align="center" sx={{ marginTop: 6 }}>
        {t('No data to display')}
      </Typography>
    );
  }

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      height: '100%',
    }}
    >
      <Breadcrumbs
        variant="list"
        elements={[
          {
            label: t('Home'),
            link: '/admin',
          },
          {
            label: widgetTitle,
            current: true,
          },
        ]}
      />
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: theme.spacing(1.5),
          marginBottom: theme.spacing(2),
        }}
      >
        <Tooltip title={t('Home')}>
          <IconButton size="small" color="primary" onClick={() => navigate('/admin')}>
            <ArrowBackOutlined fontSize="small" />
          </IconButton>
        </Tooltip>
        <Typography variant="h1" sx={{ margin: 0 }}>
          {widgetTitle}
        </Typography>
        {Object.entries(filterValues).map(([key, values]) => (
          <Chip
            key={key}
            size="small"
            variant="outlined"
            color="primary"
            label={`${t(FILTER_KEY_LABELS[key] ?? key)}: ${values.map(v => resolveValue(key, v)).join(', ')}`}
          />
        ))}
        <div style={{ flex: 1 }} />
        {!initialLoading && (
          <Typography variant="body2" color="textSecondary">
            {`${total} ${t('results')}`}
          </Typography>
        )}
      </div>
      <Paper
        variant="outlined"
        sx={{
          flex: 1,
          minHeight: 0,
          display: 'flex',
          flexDirection: 'column',
          padding: 2,
          borderRadius: 1,
        }}
      >
        {initialLoading && <Loader variant="inElement" />}
        {!initialLoading && listConfig == null && (
          <Typography variant="subtitle1" align="center">{t('No data to display')}</Typography>
        )}
        {!initialLoading && listConfig != null && paginatedEntities != null && (
          <ListWidget
            widgetConfig={listConfig}
            elements={paginatedEntities.es_datas}
            currentPageNumber={paginatedEntities.page_number}
            elementsPerPage={paginatedEntities.page_size}
            totalElements={paginatedEntities.total}
            onPaginationChange={onPaginationChange}
            contentLoading={contentLoading}
          />
        )}
      </Paper>
    </div>
  );
};

export default DefaultHomeResults;
