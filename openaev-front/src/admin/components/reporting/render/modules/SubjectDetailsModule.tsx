import {
  CrisisAlertOutlined,
  FlagOutlined,
  MemoryOutlined,
  TrackChangesOutlined,
} from '@mui/icons-material';
import { Box, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactElement, type ReactNode } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import PlatformIcon from '../../../../../components/PlatformIcon';
import { type Reporting } from '../../../../../utils/api-types';
import { capitalize } from '../../../../../utils/String';
import { type AssetCategory } from '../../../assets/asset-categories';
import AssetCategoryIcon from '../../../assets/AssetCategoryIcon';
import { REPORTING_CONTEXT_ICONS, REPORTING_CONTEXT_LABELS } from '../../ReportingContexts';
import { type ModuleDataState, type ReportingSubject } from '../useReportingRenderData';
import { ModuleEmpty, ModuleError, PrintChip } from './ModuleSection';

/**
 * Key attributes of the report subject in a clean two-column definition list.
 * Attributes are resolved by candidate keys across the different subject
 * entity shapes (simulation, scenario, endpoint, ...): a row only renders when
 * the subject actually carries the attribute. Enumerated values (status,
 * platform, architecture, ...) render as iconed tags; technical lists (IPs)
 * render as compact monospaced tags.
 */

interface Props {
  reporting: Reporting;
  subject: ModuleDataState<ReportingSubject>;
}

interface RowSpec {
  label: string;
  keys: string[];
  kind?: 'text' | 'chip' | 'date' | 'list' | 'count';
  /** Which contextual icon the chip carries (chip kind only). */
  iconKind?: 'status' | 'category' | 'focus' | 'severity' | 'platform' | 'arch';
}

const MAX_LIST_ITEMS = 6;

const ROW_SPECS: RowSpec[] = [
  {
    label: 'Status',
    keys: ['exercise_status', 'inject_status'],
    kind: 'chip',
    iconKind: 'status',
  },
  {
    label: 'Category',
    keys: ['scenario_category', 'asset_category'],
    kind: 'chip',
    iconKind: 'category',
  },
  {
    label: 'Main focus',
    keys: ['scenario_main_focus'],
    kind: 'chip',
    iconKind: 'focus',
  },
  {
    label: 'Severity',
    keys: ['scenario_severity'],
    kind: 'chip',
    iconKind: 'severity',
  },
  {
    label: 'Platform',
    keys: ['endpoint_platform', 'asset_platform'],
    kind: 'chip',
    iconKind: 'platform',
  },
  {
    label: 'Architecture',
    keys: ['endpoint_arch', 'asset_arch'],
    kind: 'chip',
    iconKind: 'arch',
  },
  {
    label: 'Hostname',
    keys: ['endpoint_hostname', 'asset_hostname'],
  },
  {
    label: 'IP addresses',
    keys: ['endpoint_ips', 'asset_ips'],
    kind: 'list',
  },
  {
    label: 'Start date',
    keys: ['exercise_start_date', 'inject_sent_at'],
    kind: 'date',
  },
  {
    label: 'End date',
    keys: ['exercise_end_date'],
    kind: 'date',
  },
  {
    label: 'Created',
    keys: ['exercise_created_at', 'scenario_created_at', 'endpoint_created_at', 'asset_created_at', 'asset_group_created_at', 'team_created_at', 'inject_created_at'],
    kind: 'date',
  },
  {
    label: 'Updated',
    keys: ['exercise_updated_at', 'scenario_updated_at', 'endpoint_updated_at', 'asset_updated_at', 'asset_group_updated_at', 'team_updated_at', 'inject_updated_at'],
    kind: 'date',
  },
  {
    label: 'Players',
    keys: ['team_users_number'],
  },
  {
    label: 'Assets',
    keys: ['asset_group_assets'],
    kind: 'count',
  },
  {
    label: 'Teams',
    keys: ['exercise_teams', 'scenario_teams'],
    kind: 'count',
  },
];

const SubjectDetailsModule: FunctionComponent<Props> = ({ reporting, subject }) => {
  const theme = useTheme();
  const { t, fldt } = useFormatter();

  if (subject.status === 'error') return <ModuleError />;
  if (subject.status !== 'success' || !subject.data) return <ModuleEmpty />;

  const { name, description, raw } = subject.data;
  const ContextIcon = REPORTING_CONTEXT_ICONS[reporting.reporting_context_type];

  const textValue = (value: string): ReactNode => (
    <Typography sx={{
      fontSize: 12,
      fontWeight: 500,
      textAlign: 'right',
      overflowWrap: 'anywhere',
    }}
    >
      {value}
    </Typography>
  );

  // Severity is the only chip borrowing the result palette on purpose: it IS
  // a criticality signal, not a category.
  const severityColor = (value: string): string => {
    switch (value.toLowerCase()) {
      case 'critical': return theme.palette.error.main;
      case 'high': return '#ff7043';
      case 'medium': return theme.palette.warning.main;
      default: return theme.palette.success.main;
    }
  };

  const chipIcon = (iconKind: RowSpec['iconKind'], value: string): ReactElement | undefined => {
    switch (iconKind) {
      case 'status': return <FlagOutlined sx={{ fontSize: 12 }} />;
      case 'category': return <AssetCategoryIcon category={value.toLowerCase() as AssetCategory} sx={{ fontSize: 12 }} />;
      case 'focus': return <TrackChangesOutlined sx={{ fontSize: 12 }} />;
      case 'severity': return <CrisisAlertOutlined sx={{ fontSize: 12 }} />;
      case 'platform': return <PlatformIcon platform={value} width={12} />;
      case 'arch': return <MemoryOutlined sx={{ fontSize: 12 }} />;
      default: return undefined;
    }
  };

  const render = (value: unknown, kind: RowSpec['kind'], iconKind?: RowSpec['iconKind']): ReactNode | null => {
    if (value === null || value === undefined || value === '') return null;
    switch (kind) {
      case 'chip':
        return typeof value === 'string'
          ? (
              <PrintChip
                label={t(capitalize(value))}
                color={iconKind === 'severity' ? severityColor(value) : undefined}
                icon={chipIcon(iconKind, value)}
              />
            )
          : null;
      case 'date':
        return typeof value === 'string' ? textValue(String(fldt(value))) : null;
      case 'list': {
        if (!Array.isArray(value) || value.length === 0) return null;
        const items = value.slice(0, MAX_LIST_ITEMS).map(String);
        const overflow = value.length - items.length;
        return (
          <Box sx={{
            display: 'flex',
            flexWrap: 'wrap',
            justifyContent: 'flex-end',
            gap: 0.5,
            maxWidth: 260,
          }}
          >
            {items.map(item => (
              <PrintChip
                key={item}
                label={item}
                color={theme.palette.text.secondary}
                mono
              />
            ))}
            {overflow > 0 && (
              <PrintChip label={`+${overflow}`} color={theme.palette.text.secondary} />
            )}
          </Box>
        );
      }
      case 'count':
        return Array.isArray(value) ? textValue(String(value.length)) : null;
      default:
        if (typeof value === 'string') return textValue(value);
        if (typeof value === 'number') return textValue(String(value));
        return null;
    }
  };

  const rows: {
    label: string;
    node: ReactNode;
  }[] = [
    {
      label: t('Name'),
      node: textValue(name),
    },
    {
      label: t('Type'),
      node: (
        <PrintChip
          label={t(REPORTING_CONTEXT_LABELS[reporting.reporting_context_type])}
          icon={<ContextIcon sx={{ fontSize: 12 }} />}
        />
      ),
    },
  ];
  ROW_SPECS.forEach((spec) => {
    for (const key of spec.keys) {
      const node = render(raw[key], spec.kind, spec.iconKind);
      if (node) {
        rows.push({
          label: t(spec.label),
          node,
        });
        break;
      }
    }
  });

  // Two-column grid: the last visual row (one or two items) drops its rule.
  const lastRowStart = rows.length - (rows.length % 2 === 0 ? 2 : 1);

  return (
    <Box>
      <Box sx={{
        display: 'grid',
        gridTemplateColumns: '1fr 1fr',
        columnGap: 6,
        rowGap: 0,
        border: `1px solid ${alpha(theme.palette.text.primary, 0.12)}`,
        borderRadius: 1,
        padding: '12px 24px',
        backgroundColor: alpha(theme.palette.text.primary, 0.02),
      }}
      >
        {rows.map((row, index) => (
          <Box
            key={row.label}
            sx={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: 2,
              paddingY: 1.5,
              minHeight: 46,
              borderBottom: index >= lastRowStart
                ? 'none'
                : `1px solid ${alpha(theme.palette.text.primary, 0.06)}`,
            }}
          >
            <Typography sx={{
              fontSize: 10.5,
              letterSpacing: '0.08em',
              textTransform: 'uppercase',
              color: 'text.secondary',
              flexShrink: 0,
            }}
            >
              {row.label}
            </Typography>
            {row.node}
          </Box>
        ))}
      </Box>
      {description && (
        <Typography sx={{
          fontSize: 12,
          lineHeight: 1.7,
          color: 'text.secondary',
          marginTop: 2.5,
        }}
        >
          {description}
        </Typography>
      )}
    </Box>
  );
};

export default SubjectDetailsModule;
