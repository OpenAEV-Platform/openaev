import {
  CheckOutlined,
  CloudOutlined,
  GroupsOutlined,
  Inventory2Outlined,
  OnlinePredictionOutlined,
  SmartButtonOutlined,
  TerminalOutlined,
  VerifiedOutlined,
  Key,
} from '@mui/icons-material';
import { Box, Button, ButtonBase, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type ComponentType, useMemo } from 'react';

import { useFormatter } from '../../../../components/i18n';
import {
  type CatalogFacetFilters,
  type ConnectorItem,
  countByPredicate,
  DEPLOYMENT_BUILT_IN,
  DEPLOYMENT_EXTERNAL,
  type FacetGroupId,
  filterConnectors,
  hasActiveFacetFilters,
  prettifyUseCase,
  STATUS_COMMUNITY,
  STATUS_FILIGRAN,
} from './catalog-facets';
import useCaseIcon from './use-case-icons';

interface FacetRow {
  value: string;
  label: string;
  count: number;
  icon?: ComponentType<{ sx?: object }>;
  capitalize?: boolean;
}

interface FacetGroup {
  id: FacetGroupId;
  label: string;
  rows: FacetRow[];
}

interface FacetRowItemProps {
  row: FacetRow;
  checked: boolean;
  onToggle: () => void;
}

const FacetRowItem = ({ row, checked, onToggle }: FacetRowItemProps) => {
  const theme = useTheme();
  const disabled = row.count === 0 && !checked;
  const RowIcon = row.icon;
  return (
    <ButtonBase
      role="checkbox"
      aria-checked={checked}
      aria-label={row.label}
      disabled={disabled}
      onClick={onToggle}
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
          border: `1px solid ${checked ? theme.palette.primary.main : theme.palette.divider}`,
          backgroundColor: checked ? theme.palette.primary.main : 'transparent',
          boxShadow: checked ? `0 0 6px ${alpha(theme.palette.primary.main, 0.5)}` : 'none',
          transition: 'all 0.15s ease',
        }}
      >
        {checked && (
          <CheckOutlined sx={{
            fontSize: 12,
            color: theme.palette.primary.contrastText,
          }}
          />
        )}
      </span>
      {RowIcon && (
        <RowIcon sx={{
          fontSize: 16,
          color: 'text.secondary',
          flexShrink: 0,
        }}
        />
      )}
      <Typography
        variant="body2"
        sx={{
          flex: 1,
          minWidth: 0,
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
          textTransform: row.capitalize ? 'capitalize' : 'none',
        }}
      >
        {row.label}
      </Typography>
      <span
        style={{
          fontSize: 11,
          lineHeight: '18px',
          minWidth: 24,
          textAlign: 'center',
          padding: theme.spacing(0, 0.5),
          borderRadius: 2,
          backgroundColor: checked
            ? alpha(theme.palette.primary.main, 0.16)
            : theme.palette.action.hover,
          color: checked ? theme.palette.primary.main : theme.palette.text.secondary,
        }}
      >
        {row.count}
      </span>
    </ButtonBase>
  );
};

interface Props {
  connectors: ConnectorItem[];
  filters: CatalogFacetFilters;
  keyword: string;
  onToggleFacet: (groupId: FacetGroupId, value: string) => void;
  onClearAll: () => void;
}

const CatalogSidebar = ({ connectors, filters, keyword, onToggleFacet, onClearAll }: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const anyActive = hasActiveFacetFilters(filters);

  const groups: FacetGroup[] = useMemo(() => {
    // Faceted-search count semantics: each group is counted against items
    // filtered by every group EXCEPT itself (plus the search keyword).
    const baseFor = (groupId: FacetGroupId) => filterConnectors(connectors, filters, keyword, groupId);

    const typesBase = baseFor('types');
    const useCasesBase = baseFor('useCases');
    const statusBase = baseFor('status');
    const deploymentBase = baseFor('deployment');

    const allUseCases = Array.from(new Set(connectors.flatMap(c => c.useCases))).sort((a, b) => a.localeCompare(b));

    return [
      {
        id: 'types' as const,
        label: t('Type'),
        rows: [
          {
            value: 'COLLECTOR',
            label: t('Collector'),
            icon: OnlinePredictionOutlined,
            count: countByPredicate(typesBase, c => c.type === 'COLLECTOR'),
          },
          {
            value: 'INJECTOR',
            label: t('Injector'),
            icon: SmartButtonOutlined,
            count: countByPredicate(typesBase, c => c.type === 'INJECTOR'),
          },
          {
            value: 'EXECUTOR',
            label: t('Executor'),
            icon: TerminalOutlined,
            count: countByPredicate(typesBase, c => c.type === 'EXECUTOR'),
          },
          {
            value: 'SECRETS_PROVIDER',
            label: t('Secrets Provider'),
            icon: Key,
            count: countByPredicate(typesBase, c => c.type === 'SECRETS_PROVIDER'),
          },
        ],
      },
      {
        id: 'useCases' as const,
        label: t('Use cases'),
        rows: allUseCases.map(useCase => ({
          value: useCase,
          label: prettifyUseCase(useCase),
          capitalize: true,
          icon: useCaseIcon(useCase),
          count: countByPredicate(useCasesBase, c => c.useCases.includes(useCase)),
        })),
      },
      // No "Deployed" status facet: it would be redundant with the Deployed tab.
      {
        id: 'status' as const,
        label: t('Status'),
        rows: [
          {
            value: STATUS_FILIGRAN,
            label: t('Supported by Filigran'),
            icon: VerifiedOutlined,
            count: countByPredicate(statusBase, c => c.verified),
          },
          {
            value: STATUS_COMMUNITY,
            label: t('Supported by Community'),
            icon: GroupsOutlined,
            count: countByPredicate(statusBase, c => !c.verified),
          },
        ],
      },
      {
        id: 'deployment' as const,
        label: t('Deployment'),
        rows: [
          {
            value: DEPLOYMENT_EXTERNAL,
            label: t('External'),
            icon: CloudOutlined,
            count: countByPredicate(deploymentBase, c => c.external),
          },
          {
            value: DEPLOYMENT_BUILT_IN,
            label: t('Built-in'),
            icon: Inventory2Outlined,
            count: countByPredicate(deploymentBase, c => !c.external),
          },
        ],
      },
    ].filter(group => group.rows.length > 0);
  }, [connectors, filters, keyword, t]);

  return (
    // Fixed sticky column on md+; below md the marketplace stacks it
    // full-width above the cards (sticky + max-height would then trap the
    // whole page behind the filters, so both are disabled).
    <Box
      component="aside"
      sx={{
        width: {
          xs: '100%',
          md: 250,
        },
        flexShrink: 0,
        position: {
          xs: 'static',
          md: 'sticky',
        },
        top: theme.spacing(2),
        alignSelf: {
          xs: 'stretch',
          md: 'flex-start',
        },
        maxHeight: {
          xs: 'none',
          md: `calc(100vh - ${theme.spacing(20)})`,
        },
        overflowY: {
          xs: 'visible',
          md: 'auto',
        },
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
            <Button size="small" onClick={onClearAll}>
              {t('Clear all')}
            </Button>
          )}
        </header>
        {groups.map((group, groupIndex) => (
          <section
            key={group.id}
            aria-label={group.label}
            style={{
              display: 'flex',
              flexDirection: 'column',
              gap: theme.spacing(0.25),
              ...(groupIndex > 0
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
              {group.label}
            </Typography>
            {group.rows.map(row => (
              <FacetRowItem
                key={row.value}
                row={row}
                checked={filters[group.id].includes(row.value)}
                onToggle={() => onToggleFacet(group.id, row.value)}
              />
            ))}
          </section>
        ))}
      </div>
    </Box>
  );
};

export default CatalogSidebar;
