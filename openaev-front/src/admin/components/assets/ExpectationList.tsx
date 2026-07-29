import { HelpOutlineOutlined, KeyboardArrowRight } from '@mui/icons-material';
import { Box, Chip, List, ListItem, ListItemButton, ListItemIcon, ListItemText, Tooltip } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type CSSProperties, type FunctionComponent, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router';

import { searchSecurityPlatformByIdAsOption } from '../../../actions/assets/securityPlatform-actions';
import { adHocEntities } from '../../../actions/dashboards/dashboard-action';
import { generateFilterId } from '../../../components/common/queryable/filter/FilterUtils';
import { initSorting, type Page } from '../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../components/common/queryable/sort/SortHeadersComponentV2';
import useBodyItemsStyles from '../../../components/common/queryable/style/style';
import { useQueryableWithLocalStorage } from '../../../components/common/queryable/useQueryableWithLocalStorage';
import { type Header } from '../../../components/common/SortHeadersList';
import Empty from '../../../components/Empty';
import { useFormatter } from '../../../components/i18n';
import ItemStatus from '../../../components/ItemStatus';
import PaginatedListLoader from '../../../components/PaginatedListLoader';
import {
  type EsBase,
  type EsEntities,
  type EsInjectExpectation,
  type Filter,
  type FilterGroup,
  type SearchPaginationInput,
  type SortField,
  type Widget,
} from '../../../utils/api-types';
import { type Option } from '../../../utils/Option';
import { computeInjectExpectationLabel, computeStatusStyle } from '../../../utils/statusUtils';
import { buildTenantApiPath } from '../../../utils/url-helper';
import expectationIconByType, { expectationTypeIcon } from '../common/ExpectationIconByType';
import ExpectationTypeChip from '../workspaces/custom_dashboards/widgets/viz/list/elements/ExpectationTypeChip';
import { getNavigationUrl } from '../workspaces/custom_dashboards/widgets/viz/list/elements/ListNavigationHandler';

const filter = (key: string, values: string[], operator: Filter['operator'] = 'eq'): Filter => ({
  id: generateFilterId(),
  key,
  mode: 'or',
  values,
  operator,
});

const group = (...filters: Filter[]): FilterGroup => ({
  mode: 'and',
  filters,
});

// The expectation's own target ("source") is the entity whose page hosts the
// list, so that column is replaced by the security platforms the expectation
// was evaluated against - the reverse pivot of the security platform overview.
const COLUMNS = ['inject_title', 'base_security_platforms_side', 'inject_expectation_type', 'inject_expectation_status', 'inject_expectation_score', 'base_created_at'];

const inlineStyles: Record<string, CSSProperties> = {
  inject_title: { width: '26%' },
  base_security_platforms_side: { width: '16%' },
  inject_expectation_type: { width: '13%' },
  inject_expectation_status: { width: '17%' },
  inject_expectation_score: { width: '10%' },
  base_created_at: { width: '18%' },
};

// The ES document only carries security platform ids: resolve the display
// names once per page of results and cache them for the whole session so
// paginating back and forth doesn't re-fetch.
const platformNameCache = new Map<string, string>();

const useSecurityPlatformNames = (elements: EsBase[]): Map<string, string> => {
  const [names, setNames] = useState<Map<string, string>>(() => new Map(platformNameCache));
  useEffect(() => {
    const ids = new Set<string>();
    elements.forEach((element) => {
      ((element as EsInjectExpectation).base_security_platforms_side ?? []).forEach(id => ids.add(id));
    });
    const missing = [...ids].filter(id => !platformNameCache.has(id));
    if (missing.length === 0) {
      setNames(new Map(platformNameCache));
      return;
    }
    searchSecurityPlatformByIdAsOption(missing)
      .then((response: { data: Option[] }) => {
        response.data.forEach(option => platformNameCache.set(option.id, option.label));
        // Cache the misses too (deleted platform / no read permission) so the
        // API isn't hammered; those ids fall back to the generic kind label.
        missing.forEach((id) => {
          if (!platformNameCache.has(id)) platformNameCache.set(id, '');
        });
        setNames(new Map(platformNameCache));
      })
      .catch(() => {
        missing.forEach(id => platformNameCache.set(id, ''));
        setNames(new Map(platformNameCache));
      });
  }, [elements]);
  return names;
};

// Logo + name chip per security platform; expectations evaluated by several
// platforms show the first one and fold the rest behind a "+n" chip.
const SecurityPlatformsFragment: FunctionComponent<{
  ids: string[];
  names: Map<string, string>;
}> = ({ ids, names }) => {
  const theme = useTheme();
  const { t } = useFormatter();
  if (ids.length === 0) {
    return <span>-</span>;
  }
  const label = (id: string) => names.get(id) || t('Security platform');
  const [first, ...rest] = ids;
  return (
    <Box
      component="span"
      sx={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 0.5,
        maxWidth: '100%',
      }}
    >
      <Tooltip title={label(first)}>
        <Chip
          icon={(
            <img
              src={buildTenantApiPath(`/api/images/security_platforms/id/${first}/${theme.palette.mode}`)}
              alt=""
              style={{
                width: 14,
                height: 14,
                borderRadius: 2,
              }}
            />
          )}
          label={label(first)}
          size="small"
          variant="outlined"
          sx={{
            'height': 22,
            'maxWidth': '100%',
            'fontSize': 11,
            'fontWeight': 600,
            'borderRadius': 1,
            '& .MuiChip-icon': { marginLeft: 0.5 },
          }}
        />
      </Tooltip>
      {rest.length > 0 && (
        <Tooltip title={rest.map(label).join(', ')}>
          <Chip
            label={`+${rest.length}`}
            size="small"
            variant="outlined"
            sx={{
              height: 22,
              fontSize: 11,
              fontWeight: 600,
              borderRadius: 1,
            }}
          />
        </Tooltip>
      )}
    </Box>
  );
};

interface Props {
  /** Static localStorage key: one shared entry per page type instead of an unbounded entry per entity ever visited. */
  filterLocalStorageKey: string;
  /** ES side field scoping the expectations (base_asset_side / base_asset_group_side). */
  scopeField: string;
  entityId: string;
  scopeOperator?: Filter['operator'];
}

// Paginated list of every expectation evaluated against an asset or an asset
// group - same look & feel as the security platform overview's expectation
// list, minus the source column (the source is the page's own entity) and
// without any predefined filter.
const ExpectationList: FunctionComponent<Props> = ({
  filterLocalStorageKey,
  scopeField,
  entityId,
  scopeOperator = 'eq',
}) => {
  const { t, nsdt } = useFormatter();
  const bodyItemsStyles = useBodyItemsStyles();

  const [elements, setElements] = useState<EsBase[]>([]);
  const [loading, setLoading] = useState(true);

  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage(
    filterLocalStorageKey,
    buildSearchPagination({ sorts: initSorting('base_created_at', 'DESC') }),
  );

  const scopeFilter = useMemo(() => filter(scopeField, [entityId], scopeOperator), [scopeField, entityId, scopeOperator]);

  const platformNames = useSecurityPlatformNames(elements);

  const headers: Header[] = useMemo(() => [
    {
      field: 'inject_title',
      // The expectation's own "name" is just its type (e.g. "Detection"), so it
      // is redundant next to the Type column. Surface the inject (the attack that
      // was tested) instead - the piece of metadata that actually identifies the row.
      label: 'Inject',
      isSortable: false,
      value: (expectation: EsInjectExpectation) => {
        const title = expectation.inject_title || expectation.base_representative || t('Unknown');
        return (
          <Tooltip title={expectation.inject_expectation_description || title} placement="bottom-start">
            <span>{title}</span>
          </Tooltip>
        );
      },
    },
    {
      field: 'base_security_platforms_side',
      // Which security platforms were expected to catch the inject - the
      // reverse pivot of the source column on the security platform overview.
      label: 'Security platforms',
      isSortable: false,
      value: (expectation: EsInjectExpectation) => (
        <SecurityPlatformsFragment
          ids={expectation.base_security_platforms_side ?? []}
          names={platformNames}
        />
      ),
    },
    {
      field: 'inject_expectation_type',
      label: 'Type',
      isSortable: false,
      value: (expectation: EsInjectExpectation) => <ExpectationTypeChip type={expectation.inject_expectation_type} />,
    },
    {
      field: 'inject_expectation_status',
      label: 'Result',
      isSortable: false,
      value: (expectation: EsInjectExpectation) => {
        const label = computeInjectExpectationLabel(
          expectation.inject_expectation_status,
          expectation.inject_expectation_type,
        ) ?? '';
        return (
          <ItemStatus
            label={label}
            variant="inList"
            status={label}
            icon={expectationIconByType(expectation.inject_expectation_type, { fontSize: 14 })}
          />
        );
      },
    },
    {
      field: 'inject_expectation_score',
      // Obtained vs expected score - shows how far the result fell short.
      label: 'Score',
      isSortable: false,
      value: (expectation: EsInjectExpectation) => {
        const score = expectation.inject_expectation_score;
        const expected = expectation.inject_expectation_expected_score;
        if (score == null && expected == null) {
          return <>-</>;
        }
        // Same score pill as the exposure validation cards: status-colored
        // tint with the obtained score prominent and the expected score dimmed.
        // A null side means "not scored" - show a dash rather than a fake 0.
        const statusColor = computeStatusStyle(expectation.inject_expectation_status).color;
        return (
          <Box
            component="span"
            sx={{
              minWidth: 34,
              height: 22,
              borderRadius: 1,
              paddingInline: 0.75,
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 0.5,
              backgroundColor: alpha(statusColor, 0.12),
              color: statusColor,
              fontSize: 12,
              fontWeight: 700,
              fontVariantNumeric: 'tabular-nums',
            }}
          >
            {score != null ? Math.round(score) : '-'}
            {expected != null && (
              <Box
                component="span"
                sx={{
                  opacity: 0.6,
                  fontWeight: 600,
                }}
              >
                {`/ ${Math.round(expected)}`}
              </Box>
            )}
          </Box>
        );
      },
    },
    {
      field: 'base_created_at',
      label: 'Date',
      isSortable: true,
      value: (expectation: EsInjectExpectation) => <>{nsdt(expectation.base_created_at)}</>,
    },
  ], [t, nsdt, platformNames]);

  // The runtime search, filters, sort and page are translated into an ad-hoc
  // dashboard list query scoped to this entity's expectations.
  const fetchExpectations = (input: SearchPaginationInput): Promise<{ data: Page<EsBase> }> => {
    setLoading(true);
    const runtimeFilters = input.filterGroup?.filters ?? [];
    const searchFilters = input.textSearch
      ? [filter('base_representative', [input.textSearch], 'contains')]
      : [];
    const config = {
      title: '',
      series: [],
      perspective: {
        name: '',
        filter: group(
          filter('base_entity', ['expectation-inject']),
          scopeFilter,
          ...runtimeFilters,
          ...searchFilters,
        ),
      },
      columns: COLUMNS,
      sorts: (input.sorts ?? []).map((s: SortField) => ({
        fieldName: s.property,
        direction: s.direction,
      })),
      widget_configuration_type: 'list',
      time_range: 'ALL_TIME',
      date_attribute: 'base_created_at',
    } as unknown as Widget['widget_config'];
    return adHocEntities(config, undefined, {
      page: input.page,
      size: input.size,
    }).then((r: { data: EsEntities }) => ({
      data: {
        content: (r.data.es_datas ?? []) as EsBase[],
        totalElements: r.data.total ?? 0,
        totalPages: r.data.page_size ? Math.ceil((r.data.total ?? 0) / r.data.page_size) : 0,
        pageable: { pageNumber: r.data.page_number ?? 0 },
      } as Page<EsBase>,
    })).finally(() => setLoading(false));
  };

  return (
    <>
      <PaginationComponentV2
        fetch={fetchExpectations}
        searchPaginationInput={searchPaginationInput}
        setContent={setElements}
        // ES-backed list: filter properties come from the engine schema (the
        // JPA InjectExpectation schema does not expose the computed status).
        engineEntityName="expectation-inject"
        availableFilterNames={['inject_expectation_type', 'inject_expectation_status', 'inject_expectation_score', 'base_security_platforms_side']}
        queryableHelpers={queryableHelpers}
        contextId={entityId}
      />
      <List>
        <ListItem
          divider={false}
          style={{
            paddingTop: 0,
            textTransform: 'uppercase',
          }}
          secondaryAction={<>&nbsp;</>}
        >
          <ListItemIcon />
          <ListItemText
            primary={(
              <SortHeadersComponentV2
                headers={headers}
                inlineStylesHeaders={inlineStyles}
                sortHelpers={queryableHelpers.sortHelpers}
              />
            )}
          />
        </ListItem>
        {loading
          ? <PaginatedListLoader Icon={HelpOutlineOutlined} headers={headers} headerStyles={inlineStyles} />
          : elements.map((element) => {
              const expectation = element as EsInjectExpectation;
              const LeadingIcon = expectationTypeIcon(expectation.inject_expectation_type);
              // Real router link (not a JS navigate) so ctrl/cmd+click opens a new
              // tab; rows without a resolvable target stay non-navigable.
              const url = getNavigationUrl(element);
              return (
                <ListItem
                  key={expectation.base_id}
                  divider
                  disablePadding
                  secondaryAction={url ? <KeyboardArrowRight color="action" /> : <>&nbsp;</>}
                >
                  <ListItemButton
                    style={{ height: 50 }}
                    {...(url
                      ? {
                          component: Link,
                          to: url,
                        }
                      : { disabled: true })}
                    sx={url
                      ? undefined
                      : {
                          '&.Mui-disabled': { opacity: 1 },
                          'cursor': 'default',
                        }}
                  >
                    <ListItemIcon>
                      <LeadingIcon color="primary" />
                    </ListItemIcon>
                    <ListItemText
                      primary={(
                        <div style={bodyItemsStyles.bodyItems}>
                          {headers.map(header => (
                            <div
                              key={header.field}
                              style={{
                                ...bodyItemsStyles.bodyItem,
                                ...inlineStyles[header.field],
                              }}
                            >
                              {header.value?.(expectation)}
                            </div>
                          ))}
                        </div>
                      )}
                    />
                  </ListItemButton>
                </ListItem>
              );
            })}
        {!loading && elements.length === 0 && <Empty message={t('No data to display')} />}
      </List>
    </>
  );
};

export default ExpectationList;
