import {
  CancelOutlined,
  DomainOutlined,
  PendingOutlined,
  PersonOffOutlined,
  PersonOutlined,
  VerifiedOutlined,
} from '@mui/icons-material';
import { type ReactElement, useCallback, useEffect, useMemo, useState } from 'react';

import ContractOutputElementType, { CONTRACT_OUTPUT_ELEMENT_TYPE_KEYS } from '../../../admin/components/findings/ContractOutputElementType';
import { type Filter, type Option, type SearchPaginationInput } from '../../../utils/api-types';
import FindingIcon from '../../FindingIcon';
import { type FilterHelpers } from '../queryable/filter/FilterHelpers';
import { generateFilterId } from '../queryable/filter/FilterUtils';
import { buildSearchPagination } from '../queryable/QueryableUtils';
import { type FacetRow } from './FacetFilters';

// Shared author + payload-status facet helpers for the injector-contract
// libraries (Threat Arsenal and the inject-contract picker), so both sidebars
// stay in lockstep on the polymorphic author filter and the status universe.

export interface AuthorOption {
  value: string;
  label: string;
  type?: string;
  // Number of contracts by this author under the current filters. Zero-count
  // authors stay visible but greyed out (like the domain facet).
  count?: number;
}

interface AuthorCountRow {
  author: string;
  author_name?: string;
  author_type?: string;
  count?: number;
}

type FetchAuthorCounts = (input: SearchPaginationInput) => Promise<{ data?: AuthorCountRow[] }>;

/**
 * Author facet source. Mirrors the domain facet: a stable universe of every
 * author (fetched unfiltered, so rows never vanish) plus per-author counts
 * under the current filters (so zero-count authors can be greyed out instead
 * of disappearing).
 */
export const useAuthorFacetOptions = (
  fetchAuthorCounts: FetchAuthorCounts,
  searchPaginationInput: SearchPaginationInput,
): AuthorOption[] => {
  const [universe, setUniverse] = useState<AuthorCountRow[]>([]);
  const [counts, setCounts] = useState<Record<string, number>>({});

  // Stable universe of all authors (unfiltered), fetched once.
  useEffect(() => {
    let cancelled = false;
    fetchAuthorCounts(buildSearchPagination({}))
      .then((response) => {
        if (!cancelled) setUniverse((response.data ?? []) as AuthorCountRow[]);
      })
      .catch(() => {
        if (!cancelled) setUniverse([]);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  // Per-author counts under the active filters.
  useEffect(() => {
    let cancelled = false;
    fetchAuthorCounts(searchPaginationInput)
      .then((response) => {
        if (cancelled) return;
        const map: Record<string, number> = {};
        ((response.data ?? []) as AuthorCountRow[]).forEach((row) => {
          map[row.author] = row.count ?? 0;
        });
        setCounts(map);
      })
      .catch(() => {
        if (!cancelled) setCounts({});
      });
    return () => {
      cancelled = true;
    };
  }, [searchPaginationInput]);

  return useMemo(
    () =>
      universe
        .map(row => ({
          value: row.author,
          label: row.author_name ?? row.author,
          type: row.author_type,
          count: counts[row.author] ?? 0,
        }))
        .sort((a, b) => a.label.localeCompare(b.label)),
    [universe, counts],
  );
};

// Every author, loaded once and sorted by name: a facet listed without counts.
export const useAuthorOptions = (fetchAuthors: () => Promise<AuthorOption[]>): AuthorOption[] => {
  const [authors, setAuthors] = useState<AuthorOption[]>([]);
  useEffect(() => {
    fetchAuthors().then(loaded => setAuthors([...loaded].sort((a, b) => a.label.localeCompare(b.label))));
  }, []);
  return authors;
};

/**
 * The author filter is polymorphic: it holds either a set of author ids
 * (operator `eq`) OR the special "no author" state (operator `empty`). The two
 * are mutually exclusive - selecting one clears the other.
 */
export const useAuthorFacetFilter = (
  filterKey: string,
  filters: Filter[],
  filterHelpers: FilterHelpers,
) => {
  const authorFilter = useMemo(
    () => filters.find((f: Filter) => f.key === filterKey),
    [filters, filterKey],
  );
  const noAuthorActive = authorFilter?.operator === 'empty';
  const authorValues = useMemo(
    () => (authorFilter && authorFilter.operator !== 'empty' ? authorFilter.values ?? [] : []),
    [authorFilter],
  );

  const removeAuthorFilter = useCallback(() => {
    if (authorFilter?.id) {
      filterHelpers.handleRemoveFilterById(authorFilter.id);
    } else {
      filterHelpers.handleRemoveFilterByKey(filterKey);
    }
  }, [authorFilter, filterHelpers, filterKey]);

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
        key: filterKey,
        operator: 'eq',
        values: next,
        mode: 'or',
      });
    },
    [authorFilter, authorValues, noAuthorActive, removeAuthorFilter, filterHelpers, filterKey],
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
        key: filterKey,
        operator: 'empty',
        values: [],
        mode: 'and',
      });
    }
  }, [authorFilter, noAuthorActive, removeAuthorFilter, filterHelpers, filterKey]);

  return {
    authorValues,
    noAuthorActive,
    toggleAuthorValue,
    toggleNoAuthor,
  };
};

const authorIconComponent = (type?: string) => {
  switch (type) {
    case 'organization':
      return DomainOutlined;
    default:
      return PersonOutlined;
  }
};

/**
 * Author facet rows: the always-available "No author" facet + one row per
 * known author (union of the universe and any currently selected author, so an
 * active author filter stays visible even if its count drops to zero).
 */
export const buildAuthorRows = ({
  authorOptions,
  authorValues,
  noAuthorActive,
  toggleAuthorValue,
  toggleNoAuthor,
  noAuthorLabel,
}: {
  authorOptions: AuthorOption[];
  authorValues: string[];
  noAuthorActive: boolean;
  toggleAuthorValue: (value: string) => void;
  toggleNoAuthor: () => void;
  noAuthorLabel: string;
}): FacetRow[] => {
  const authorById = new Map(authorOptions.map(a => [a.value, a]));
  authorValues.forEach((value) => {
    if (!authorById.has(value)) {
      authorById.set(value, {
        value,
        label: value,
      });
    }
  });
  return [
    // Always-available facet to isolate actions that carry no author.
    {
      value: '__no_author__',
      label: noAuthorLabel,
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
};

const statusFacetMeta = (t: (key: string) => string): {
  value: string;
  label: string;
  icon: () => ReactElement;
}[] => [
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
];

/**
 * Payload-status facet rows (Verified / Unverified / Deprecated). Counts stay
 * undefined (no badge, row clickable) until the aggregation endpoint answers;
 * afterwards zero-count rows grey out like the domain facet.
 */
export const buildStatusRows = ({
  t,
  statusValues,
  statusCounts,
  toggle,
}: {
  t: (key: string) => string;
  statusValues: string[];
  statusCounts: Record<string, number> | undefined;
  toggle: (value: string) => void;
}): FacetRow[] => statusFacetMeta(t).map(status => ({
  value: status.value,
  label: status.label,
  count: statusCounts ? statusCounts[status.value] ?? 0 : undefined,
  icon: status.icon,
  checked: statusValues.includes(status.value),
  onToggle: () => toggle(status.value),
}));

const findingTypeFacetMeta = (t: (key: string) => string): {
  value: string;
  label: string;
  icon: () => ReactElement;
}[] => {
  return CONTRACT_OUTPUT_ELEMENT_TYPE_KEYS
    .map(type => ({
      value: type,
      label: t(ContractOutputElementType[type]),
      icon: () => <FindingIcon findingType={type} />,
    }))
    .sort((a, b) => a.label.localeCompare(b.label));
};

export const buildFindingTypeRows = ({
  t,
  statusValues,
  statusCounts,
  toggle,
}: {
  t: (key: string) => string;
  statusValues: string[];
  statusCounts: Record<string, number> | undefined;
  toggle: (value: string) => void;
}): FacetRow[] => findingTypeFacetMeta(t).map(findingType => ({
  value: findingType.value,
  label: findingType.label,
  count: statusCounts ? statusCounts[findingType.value] ?? 0 : undefined,
  icon: findingType.icon,
  checked: statusValues.includes(findingType.value),
  onToggle: () => toggle(findingType.value),
}));

export const buildAssetRows = ({
  assetOptions,
  statusValues,
  toggle,
}: {
  assetOptions: Option[];
  statusValues: string[];
  toggle: (option: Option) => void;
}): FacetRow[] => {
  const assets = !!assetOptions && assetOptions.length ? assetOptions : [];
  return assets
    .filter(assetOption => assetOption.id && assetOption.label)
    .sort((assetA, assetB) => assetA.label!.localeCompare(assetB.label!))
    .map(assetOption => (
      {
        value: assetOption.id,
        label: assetOption.label,
        checked: statusValues.includes(assetOption.label!),
        onToggle: () => toggle(assetOption),
      } as FacetRow
    ));
};
