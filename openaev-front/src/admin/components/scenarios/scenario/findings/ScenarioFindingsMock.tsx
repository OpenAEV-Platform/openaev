/**
 * ScenarioFindingsMock — mock findings matching FindingList visual design exactly.
 * Columns: Type | Value | Endpoints | Severity — same layout as the real FindingList.
 * Clicking a row opens the Drawer with finding details.
 * Reads ?filter= URL param set by stat-badge clicks.
 */
import { HubOutlined } from '@mui/icons-material';
import { Chip, Divider, List, ListItem, ListItemButton, ListItemIcon, ListItemText, Paper, Tooltip, Typography } from '@mui/material';
import { type CSSProperties, useState } from 'react';
import { useParams, useSearchParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import Drawer from '../../../../../components/common/Drawer';
import useBodyItemsStyles from '../../../../../components/common/queryable/style/style';
import FindingIcon from '../../../../../components/FindingIcon';
import { useFormatter } from '../../../../../components/i18n';
import { MOCK_SCENARIO_FINDINGS, type MockFinding } from '../../../simulations/simulation/attack_path/mockFindingsData';

const useStyles = makeStyles()(() => ({
  itemHead: { textTransform: 'uppercase' },
  item: { height: 50 },
}));

const SEV_COLOR: Record<string, string> = {
  critical: '#f44336',
  high: '#ff9800',
  medium: '#ffeb3b',
  low: '#4caf50',
  info: '#2196f3',
};

// Map URL filter param → MockFinding type group
const URL_TO_GROUP: Record<string, MockFinding['type'][]> = {
  endpoints: ['hostname'],
  files: ['file'],
  credentials: ['credential', 'username', 'domain_name'],
};

// Map type → FindingIcon type
const TYPE_TO_ICON: Record<MockFinding['type'], string> = {
  hostname: 'HOSTNAME',
  username: 'TEXT',
  credential: 'TEXT',
  domain_name: 'TEXT',
  file: 'FILE',
  port: 'HOSTNAME',
  cve: 'HOSTNAME',
};

// Type display label
const TYPE_LABEL: Record<MockFinding['type'], string> = {
  hostname: 'Hostname',
  username: 'Username',
  credential: 'Credential',
  domain_name: 'Domain Name',
  file: 'File',
  port: 'Port',
  cve: 'CVE',
};

// Mask a credential/username value: keep first and last char, replace rest with *
function maskValue(v: string): string {
  if (v.length <= 2) return v;
  return v[0] + '*'.repeat(Math.max(1, v.length - 2)) + v[v.length - 1];
}

// For credential/username types, apply masking to the password part after ':'
function displayValue(finding: MockFinding): string {
  const raw = finding.value ?? finding.name;
  if (finding.type === 'credential' || finding.type === 'username' || finding.type === 'domain_name') {
    // Mask the part after the last ':'
    const colonIdx = raw.lastIndexOf(':');
    if (colonIdx >= 0) {
      const user = raw.slice(0, colonIdx + 1);
      const pass = raw.slice(colonIdx + 1).trim();
      return `${user} ${maskValue(pass)}`;
    }
    return maskValue(raw);
  }
  return raw;
}

type FilterGroup = 'all' | 'endpoints' | 'files' | 'credentials';

const inlineStyles: Record<string, CSSProperties> = {
  finding_type:   { width: '20%' },
  finding_value:  { width: '30%' },
  finding_assets: { width: '30%' },
  finding_tags:   { width: '20%' },
};

const ScenarioFindingsMock = () => {
  const { classes } = useStyles();
  const bodyItemsStyles = useBodyItemsStyles();
  const { t } = useFormatter();
  const { scenarioId } = useParams<{ scenarioId: string }>();
  const [searchParams] = useSearchParams();
  const [selectedFinding, setSelectedFinding] = useState<MockFinding | null>(null);

  const urlFilter = (searchParams.get('filter') ?? '') as FilterGroup;
  const [filterGroup, setFilterGroup] = useState<FilterGroup>(
    URL_TO_GROUP[urlFilter] ? urlFilter as FilterGroup : 'all',
  );

  const all: MockFinding[] = MOCK_SCENARIO_FINDINGS[scenarioId ?? ''] ?? [];
  const findings = filterGroup === 'all'
    ? all
    : all.filter((f) => (URL_TO_GROUP[filterGroup] ?? []).includes(f.type));

  const headers = [
    { field: 'finding_type',   label: t('Type') },
    { field: 'finding_value',  label: t('Value') },
    { field: 'finding_assets', label: t('Endpoints') },
    { field: 'finding_tags',   label: t('Severity') },
  ];

  const cellValue = (field: string, finding: MockFinding) => {
    if (field === 'finding_type') {
      return <span style={{ fontSize: 13 }}>{TYPE_LABEL[finding.type] ?? finding.type}</span>;
    }
    if (field === 'finding_value') {
      const display = displayValue(finding);
      return (
        <Tooltip title={finding.value ?? finding.name}>
          <span style={{
            fontSize: 12,
            fontFamily: (finding.type === 'credential' || finding.type === 'username') ? 'monospace' : undefined,
          }}>
            {display.length > 38 ? `${display.slice(0, 36)}…` : display}
          </span>
        </Tooltip>
      );
    }
    if (field === 'finding_assets') {
      return <Chip label={finding.affected_asset} size="small" sx={{ fontSize: 11, height: 22 }} />;
    }
    if (field === 'finding_tags') {
      const sevColor = SEV_COLOR[finding.severity] ?? '#999';
      return (
        <Chip
          label={finding.severity.toUpperCase()}
          size="small"
          sx={{
            fontSize: 9, fontWeight: 700, height: 18,
            backgroundColor: `${sevColor}22`, color: sevColor, border: `1px solid ${sevColor}44`,
          }}
        />
      );
    }
    return null;
  };

  const counts = {
    all: all.length,
    endpoints: all.filter((f) => (URL_TO_GROUP.endpoints ?? []).includes(f.type)).length,
    files: all.filter((f) => (URL_TO_GROUP.files ?? []).includes(f.type)).length,
    credentials: all.filter((f) => (URL_TO_GROUP.credentials ?? []).includes(f.type)).length,
  };

  const chipColor = (g: FilterGroup) =>
    g === 'endpoints' ? '#e91e63' : g === 'files' ? '#9c27b0' : g === 'credentials' ? '#f44336' : undefined;

  return (
    <>
      {/* Filter chips matching FindingList position */}
      <div style={{ display: 'flex', gap: 8, padding: '8px 0 4px', flexWrap: 'wrap' }}>
        {(['all', 'endpoints', 'files', 'credentials'] as FilterGroup[]).map((g) => {
          const active = filterGroup === g;
          const color = chipColor(g);
          const label = g === 'all' ? `All (${counts.all})` : `${g.charAt(0).toUpperCase() + g.slice(1)} (${counts[g]})`;
          return (
            <Chip
              key={g}
              label={label}
              size="small"
              onClick={() => setFilterGroup(g)}
              sx={{
                cursor: 'pointer',
                fontWeight: active ? 700 : 400,
                backgroundColor: active ? `${color ?? '#90caf9'}20` : undefined,
                color: active ? (color ?? '#90caf9') : undefined,
                border: active ? `1px solid ${color ?? '#90caf9'}55` : undefined,
              }}
            />
          );
        })}
      </div>

      <List>
        <ListItem classes={{ root: classes.itemHead }} style={{ paddingTop: 0 }}>
          <ListItemIcon>
            <HubOutlined style={{ visibility: 'hidden' }} />
          </ListItemIcon>
          <ListItemText
            primary={(
              <div style={bodyItemsStyles.bodyItems}>
                {headers.map((h) => (
                  <div key={h.field} style={{ ...bodyItemsStyles.bodyItem, ...inlineStyles[h.field] }}>
                    <span style={{ fontSize: 12, fontWeight: 700, opacity: 0.5 }}>{h.label}</span>
                  </div>
                ))}
              </div>
            )}
          />
        </ListItem>

        {findings.length === 0 && (
          <ListItem>
            <ListItemText primary={<span style={{ opacity: 0.4, fontSize: 13 }}>{t('No findings')}</span>} />
          </ListItem>
        )}

        {findings.map((finding) => (
          <ListItem key={finding.id} classes={{ root: classes.item }} divider disablePadding>
            <ListItemButton classes={{ root: classes.item }} onClick={() => setSelectedFinding(finding)}>
              <ListItemIcon>
                <FindingIcon findingType={TYPE_TO_ICON[finding.type] ?? 'TEXT'} tooltip />
              </ListItemIcon>
              <ListItemText
                primary={(
                  <div style={bodyItemsStyles.bodyItems}>
                    {headers.map((h) => (
                      <div key={h.field} style={{ ...bodyItemsStyles.bodyItem, ...inlineStyles[h.field] }}>
                        {cellValue(h.field, finding)}
                      </div>
                    ))}
                  </div>
                )}
              />
            </ListItemButton>
          </ListItem>
        ))}
      </List>

      {/* Finding detail drawer */}
      <Drawer
        open={!!selectedFinding}
        handleClose={() => setSelectedFinding(null)}
        title={selectedFinding ? displayValue(selectedFinding) : ''}
      >
        {selectedFinding && <MockFindingDetail finding={selectedFinding} />}
      </Drawer>
    </>
  );
};

// ── Detail panel matching FindingDetail "Related Injects" tab ────────────────

const MockFindingDetail = ({ finding }: { finding: MockFinding }) => {
  const { t } = useFormatter();
  const sevColor = SEV_COLOR[finding.severity] ?? '#999';

  const rows: Array<[string, React.ReactNode]> = [
    [t('Type'), <Chip key="type" label={TYPE_LABEL[finding.type] ?? finding.type} size="small" />],
    [t('Value'), (
      <code key="val" style={{ fontSize: 11, wordBreak: 'break-all' }}>
        {displayValue(finding)}
      </code>
    )],
    [t('Severity'), (
      <Chip
        key="sev"
        label={finding.severity.toUpperCase()}
        size="small"
        sx={{
          fontSize: 9, fontWeight: 700, height: 18,
          backgroundColor: `${sevColor}22`, color: sevColor, border: `1px solid ${sevColor}44`,
        }}
      />
    )],
    [t('Status'), (
      <Chip
        key="status"
        label={finding.status}
        size="small"
        sx={{
          fontSize: 10, height: 20,
          backgroundColor: finding.status === 'open' ? 'rgba(244,67,54,0.12)' : 'rgba(76,175,80,0.12)',
          color: finding.status === 'open' ? '#f44336' : '#4caf50',
        }}
      />
    )],
    [t('Affected Asset'), <Chip key="asset" label={finding.affected_asset} size="small" />],
    [t('MITRE Technique'), <code key="mitre" style={{ fontSize: 12 }}>{finding.mitre_technique}</code>],
  ];

  return (
    <Paper elevation={0} sx={{ p: 2 }}>
      <Typography variant="subtitle2" gutterBottom>{finding.name}</Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>{finding.description}</Typography>
      <Divider sx={{ mb: 2 }} />
      {rows.map(([label, value]) => (
        <div key={String(label)} style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 12 }}>
          <Typography variant="caption" color="text.secondary" sx={{ minWidth: 140 }}>{label}</Typography>
          {value}
        </div>
      ))}
      {finding.detail && (
        <>
          <Divider sx={{ my: 2 }} />
          <Typography variant="caption" color="text.secondary">{t('Technical Detail')}</Typography>
          <Paper elevation={1} sx={{ mt: 1, p: 1.5, fontFamily: 'monospace', fontSize: 11, overflowX: 'auto' }}>
            {finding.detail}
          </Paper>
        </>
      )}
    </Paper>
  );
};

export default ScenarioFindingsMock;
