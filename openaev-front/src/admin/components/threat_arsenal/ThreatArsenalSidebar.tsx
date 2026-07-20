import {
  CancelOutlined,
  CheckOutlined,
  DomainOutlined,
  GroupsOutlined,
  PendingOutlined,
  PersonOffOutlined,
  PersonOutlined,
  VerifiedOutlined,
} from '@mui/icons-material';
import { Box, Button, ButtonBase, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type ReactElement, useCallback, useMemo } from 'react';

import { type FilterHelpers } from '../../../components/common/queryable/filter/FilterHelpers';
import { generateFilterId } from '../../../components/common/queryable/filter/FilterUtils';
import { useFormatter } from '../../../components/i18n';
import PlatformIcon from '../../../components/PlatformIcon';
import { type Filter, type SearchPaginationInput } from '../../../utils/api-types';
import { type IconBarElement } from '../common/domains/IconBar-model';

const PLATFORM_FILTER_KEY = 'action_platforms';
const STATUS_FILTER_KEY = 'action_payload_status';
const AUTHOR_FILTER_KEY = 'action_author';
const PLATFORMS = ['Windows', 'Linux', 'MacOS'];

export interface AuthorOption {
  value: string;
  label: string;
  type?: string;
  // Number of contracts by this author under the current filters. Zero-count
  // authors stay visible but greyed out (like the domain facet).
  count?: number;
}

const authorIconComponent = (type?: string) => {
  switch (type) {
    case 'team':
      return GroupsOutlined;
    case 'organization':
      return DomainOutlined;
    default:
      return PersonOutlined;
  }
};

interface FacetRow {
  value: string;
  label: string;
  count?: number;
  icon?: () => ReactElement;
  onToggle: () => void;
  checked: boolean;
}

interface FacetRowItemProps { row: FacetRow }

// Same anatomy as the integrations marketplace CatalogSidebar row: custom
// checkbox + optional icon + label + optional count badge.
const FacetRowItem = ({ row }: FacetRowItemProps) => {
  const theme = useTheme();
  const disabled = row.count === 0 && !row.checked;
  return (
    <ButtonBase
      role="checkbox"
      aria-checked={row.checked}
      aria-label={row.label}
      disabled={disabled}
      onClick={row.onToggle}
      sx={{
        'display': 'flex',
        'alignItems': 'center',
        'gap': 1,
        'width': '100%',
        'justifyContent': 'flex-start',
        'padding': theme.spacing(0.5, 1),
        'borderRadius': 1,
        'textAlign': 'left',
        'opacity': disabled ? 0.4 : 1,
        'transition': 'background-color 0.15s ease',
        '&:hover': { backgroundColor: theme.palette.action.hover },
      }}
    >
      <span
        aria-hidden
        style={{
          width: 16,
          height: 16,
          flexShrink: 0,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          borderRadius: 2,
          border: `1px solid ${row.checked ? theme.palette.primary.main : theme.palette.divider}`,
          backgroundColor: row.checked ? theme.palette.primary.main : 'transparent',
          boxShadow: row.checked ? `0 0 6px ${alpha(theme.palette.primary.main, 0.5)}` : 'none',
          transition: 'all 0.15s ease',
        }}
      >
        {row.checked && (
          <CheckOutlined sx={{
            fontSize: 12,
            color: theme.palette.primary.contrastText,
          }}
          />
        )}
      </span>
      {row.icon && (
        <Box
          aria-hidden
          sx={{
            'display': 'flex',
            'alignItems': 'center',
            'justifyContent': 'center',
            'flexShrink': 0,
            'color': 'text.secondary',
            // Force a consistent 16px icon size (matching the integrations
            // marketplace sidebar), whatever the underlying icon/image.
            '& svg': { fontSize: 16 },
            '& img': {
              width: 16,
              height: 16,
            },
          }}
        >
          {row.icon()}
        </Box>
      )}
      <Typography
        variant="body2"
        sx={{
          flex: 1,
          minWidth: 0,
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
        }}
      >
        {row.label}
      </Typography>
      {row.count !== undefined && (
        <span
          style={{
            fontSize: 11,
            lineHeight: '18px',
            minWidth: 24,
            textAlign: 'center',
            padding: theme.spacing(0, 0.5),
            borderRadius: 2,
            backgroundColor: row.checked
              ? alpha(theme.palette.primary.main, 0.16)
              : theme.palette.action.hover,
            color: row.checked ? theme.palette.primary.main : theme.palette.text.secondary,
          }}
        >
          {row.count}
        </span>
      )}
    </ButtonBase>
  );
};

interface FacetSection {
  id: string;
  label: string;
  rows: FacetRow[];
}

interface Props {
  /** Domain facet rows (with live counts + icons), already ordered. */
  domainElements: IconBarElement[];
  /** Distinct authors present in the loaded page (id + label + type). */
  authorOptions: AuthorOption[];
  searchPaginationInput: SearchPaginationInput;
  filterHelpers: FilterHelpers;
}

// The Threat Arsenal "basket": a sticky faceted sidebar (mirrors the
// integrations marketplace CatalogSidebar) whose rows toggle REAL backend
// filters through `filterHelpers`. The generic "Add filter" bar still handles
// every other property (injectors, tags, dates...).
const ThreatArsenalSidebar = ({ domainElements, authorOptions, searchPaginationInput, filterHelpers }: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();

  const filters = useMemo(
    () => searchPaginationInput.filterGroup?.filters ?? [],
    [searchPaginationInput.filterGroup],
  );

  const platformValues = useMemo(
    () => filters.find((f: Filter) => f.key === PLATFORM_FILTER_KEY)?.values ?? [],
    [filters],
  );
  const statusValues = useMemo(
    () => filters.find((f: Filter) => f.key === STATUS_FILTER_KEY)?.values ?? [],
    [filters],
  );
  // The author filter is polymorphic: it holds either a set of author ids
  // (operator `eq`) OR the special "no author" state (operator `empty`). The two
  // are mutually exclusive - selecting one clears the other.
  const authorFilter = useMemo(
    () => filters.find((f: Filter) => f.key === AUTHOR_FILTER_KEY),
    [filters],
  );
  const noAuthorActive = authorFilter?.operator === 'empty';
  const authorValues = useMemo(
    () => (authorFilter && authorFilter.operator !== 'empty' ? authorFilter.values ?? [] : []),
    [authorFilter],
  );

  const setFilterValues = useCallback(
    (key: string, values: string[]) => {
      const existing = filters.find((f: Filter) => f.key === key);
      if (values.length === 0) {
        if (existing?.id) {
          filterHelpers.handleRemoveFilterById(existing.id);
        } else {
          filterHelpers.handleRemoveFilterByKey(key);
        }
        return;
      }
      if (existing?.id) {
        filterHelpers.handleUpdateValuesById(existing.id, values);
        return;
      }
      filterHelpers.handleAddFilterWithEmptyValue({
        id: generateFilterId(),
        key,
        operator: 'eq',
        values,
        mode: 'and',
      });
    },
    [filterHelpers, filters],
  );

  const toggleValue = useCallback(
    (key: string, current: string[], value: string) => {
      const next = current.includes(value)
        ? current.filter(v => v !== value)
        : [...current, value];
      setFilterValues(key, next);
    },
    [setFilterValues],
  );

  // Author needs its own setters because it juggles the `eq` (specific authors)
  // and `empty` ("no author") operators on the same filter key.
  const removeAuthorFilter = useCallback(() => {
    if (authorFilter?.id) {
      filterHelpers.handleRemoveFilterById(authorFilter.id);
    } else {
      filterHelpers.handleRemoveFilterByKey(AUTHOR_FILTER_KEY);
    }
  }, [authorFilter, filterHelpers]);

  const toggleAuthorValue = useCallback(
    (value: string) => {
      const current = noAuthorActive ? [] : authorValues;
      const next = current.includes(value)
        ? current.filter(v => v !== value)
        : [...current, value];
      if (next.length === 0) {
        removeAuthorFilter();
        return;
      }
      if (authorFilter?.id) {
        // Coming from "no author" (empty) or refining an existing selection.
        filterHelpers.handleChangeOperatorById(authorFilter.id, 'eq');
        filterHelpers.handleUpdateValuesById(authorFilter.id, next);
        return;
      }
      filterHelpers.handleAddFilterWithEmptyValue({
        id: generateFilterId(),
        key: AUTHOR_FILTER_KEY,
        operator: 'eq',
        values: next,
        mode: 'or',
      });
    },
    [authorFilter, authorValues, noAuthorActive, removeAuthorFilter, filterHelpers],
  );

  const toggleNoAuthor = useCallback(() => {
    if (noAuthorActive) {
      removeAuthorFilter();
      return;
    }
    if (authorFilter?.id) {
      filterHelpers.handleChangeOperatorById(authorFilter.id, 'empty');
      filterHelpers.handleUpdateValuesById(authorFilter.id, []);
    } else {
      filterHelpers.handleAddFilterWithEmptyValue({
        id: generateFilterId(),
        key: AUTHOR_FILTER_KEY,
        operator: 'empty',
        values: [],
        mode: 'and',
      });
    }
  }, [authorFilter, noAuthorActive, removeAuthorFilter, filterHelpers]);

  const statusMeta: {
    value: string;
    label: string;
    icon: () => ReactElement;
  }[] = useMemo(() => [
    {
      value: 'VERIFIED',
      label: t('Verified'),
      icon: () => <VerifiedOutlined sx={{ fontSize: 16 }} />,
    },
    {
      value: 'UNVERIFIED',
      label: t('Unverified'),
      icon: () => <PendingOutlined sx={{ fontSize: 16 }} />,
    },
    {
      value: 'DEPRECATED',
      label: t('Deprecated'),
      icon: () => <CancelOutlined sx={{ fontSize: 16 }} />,
    },
  ], [t]);

  const sections: FacetSection[] = useMemo(() => {
    const domainRows: FacetRow[] = domainElements.map(element => ({
      value: element.type ?? element.name,
      label: t(element.name),
      count: element.count ?? 0,
      icon: element.icon,
      checked: element.color === 'success',
      onToggle: element.function,
    }));

    const platformRows: FacetRow[] = PLATFORMS.map(platform => ({
      value: platform,
      label: t(platform),
      icon: () => <PlatformIcon platform={platform} width={16} />,
      checked: platformValues.includes(platform),
      onToggle: () => toggleValue(PLATFORM_FILTER_KEY, platformValues, platform),
    }));

    const statusRows: FacetRow[] = statusMeta.map(status => ({
      value: status.value,
      label: status.label,
      icon: status.icon,
      checked: statusValues.includes(status.value),
      onToggle: () => toggleValue(STATUS_FILTER_KEY, statusValues, status.value),
    }));

    // Union of the full author universe and any currently selected author (so
    // an active author filter stays visible even if its count drops to zero).
    const authorById = new Map(authorOptions.map(a => [a.value, a]));
    authorValues.forEach((value) => {
      if (!authorById.has(value)) {
        authorById.set(value, {
          value,
          label: value,
        });
      }
    });
    const authorRows: FacetRow[] = [
      // Always-available facet to isolate actions that carry no author.
      {
        value: '__no_author__',
        label: t('No author'),
        icon: () => <PersonOffOutlined sx={{ fontSize: 16 }} />,
        checked: noAuthorActive,
        onToggle: toggleNoAuthor,
      },
      ...Array.from(authorById.values()).map((author) => {
        const AuthorIcon = authorIconComponent(author.type);
        return {
          value: author.value,
          label: author.label,
          count: author.count,
          icon: () => <AuthorIcon sx={{ fontSize: 16 }} />,
          checked: authorValues.includes(author.value),
          onToggle: () => toggleAuthorValue(author.value),
        };
      }),
    ];

    return [
      {
        id: 'domains',
        label: t('Domains'),
        rows: domainRows,
      },
      {
        id: 'platforms',
        label: t('Platform'),
        rows: platformRows,
      },
      {
        id: 'status',
        label: t('Status'),
        rows: statusRows,
      },
      {
        id: 'author',
        label: t('Author'),
        rows: authorRows,
      },
    ].filter(section => section.rows.length > 0);
  }, [domainElements, authorOptions, platformValues, statusValues, authorValues, noAuthorActive, statusMeta, toggleValue, toggleAuthorValue, toggleNoAuthor, t]);

  const anyActive = platformValues.length > 0
    || statusValues.length > 0
    || authorValues.length > 0
    || noAuthorActive
    || domainElements.some(e => e.color === 'success');

  return (
    <aside
      style={{
        width: 250,
        flexShrink: 0,
        position: 'sticky',
        top: theme.spacing(2),
        alignSelf: 'flex-start',
        maxHeight: `calc(100vh - ${theme.spacing(20)})`,
        overflowY: 'auto',
      }}
    >
      <div style={{
        display: 'flex',
        flexDirection: 'column',
        gap: theme.spacing(2),
        padding: theme.spacing(2),
        borderRadius: theme.shape.borderRadius,
        border: `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
        backgroundColor: theme.palette.background.paper,
      }}
      >
        <header style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
        >
          <Typography
            sx={{
              fontFamily: theme.typography.h1.fontFamily,
              fontWeight: 600,
              fontSize: 15,
            }}
          >
            {t('Filters')}
          </Typography>
          {anyActive && (
            <Button size="small" onClick={() => filterHelpers.handleClearAllFilters()}>
              {t('Clear all')}
            </Button>
          )}
        </header>
        {sections.map((section, sectionIndex) => (
          <section
            key={section.id}
            aria-label={section.label}
            style={{
              display: 'flex',
              flexDirection: 'column',
              gap: theme.spacing(0.25),
              ...(sectionIndex > 0
                ? {
                    borderTop: `1px solid ${alpha(theme.palette.text.primary, 0.05)}`,
                    paddingTop: theme.spacing(2),
                  }
                : {}),
            }}
          >
            <Typography
              component="h3"
              sx={{
                fontFamily: theme.typography.h1.fontFamily,
                fontWeight: 600,
                fontSize: 12,
                textTransform: 'uppercase',
                letterSpacing: '0.12em',
                color: 'text.secondary',
                paddingInline: 1,
              }}
            >
              {section.label}
            </Typography>
            {section.rows.map(row => (
              <FacetRowItem key={row.value} row={row} />
            ))}
          </section>
        ))}
      </div>
    </aside>
  );
};

export default ThreatArsenalSidebar;
