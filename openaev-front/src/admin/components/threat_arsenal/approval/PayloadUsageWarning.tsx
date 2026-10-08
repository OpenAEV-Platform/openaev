import { ExpandMoreOutlined } from '@mui/icons-material';
import { Accordion, AccordionDetails, AccordionSummary, Alert } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
import { Link } from 'react-router';

import { useFormatter } from '../../../../components/i18n';
import { type ThreatArsenalActionUsageItem, type ThreatArsenalActionUsageOutput } from '../../../../utils/api-types';
import { isPayloadUsed } from './approvalStatusUtils';

interface Props { usage?: ThreatArsenalActionUsageOutput }

interface UsageGroup {
  key: string;
  label: string;
  count: number;
  items?: ThreatArsenalActionUsageItem[];
  path: (id: string) => string;
}

// ICU plurals (react-intl): every language gets its own plural categories.
const COUNT_MESSAGES = {
  atomicTestings: '{count, plural, one {# atomic testing} other {# atomic testings}}',
  scenarios: '{count, plural, one {# scenario} other {# scenarios}}',
  simulations: '{count, plural, one {# simulation} other {# simulations}}',
};

/**
 * Where a payload is used, shown before an approval change blocks launches (rejecting it, or an
 * edit that sends it back to pending): a warning with the pluralized counts, then one collapsible
 * group per type in use, each a compact scrollable list of links (new tab) to the first items the
 * user can open. Renders nothing when the payload is not used.
 */
const PayloadUsageWarning: FunctionComponent<Props> = ({ usage }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  if (!usage || !isPayloadUsed(usage)) {
    return null;
  }

  const groups: UsageGroup[] = ([
    {
      key: 'atomicTestings',
      label: t('Atomic testings'),
      count: usage.usage_atomic_testings_count ?? 0,
      items: usage.usage_atomic_testings,
      path: (id: string) => `/admin/atomic_testings/${id}`,
    },
    {
      key: 'scenarios',
      label: t('Scenarios'),
      count: usage.usage_scenarios_count ?? 0,
      items: usage.usage_scenarios,
      path: (id: string) => `/admin/scenarios/${id}`,
    },
    {
      key: 'simulations',
      label: t('Simulations'),
      count: usage.usage_simulations_count ?? 0,
      items: usage.usage_simulations,
      path: (id: string) => `/admin/simulations/${id}`,
    },
  ] as UsageGroup[]).filter(group => group.count > 0);

  const summary = groups
    .map(group => t(COUNT_MESSAGES[group.key as keyof typeof COUNT_MESSAGES], { count: String(group.count) }))
    .join(', ');

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: theme.spacing(1),
    }}
    >
      <Alert severity="warning">{t('Used in {items}.', { items: summary })}</Alert>
      {groups.filter(group => group.items && group.items.length > 0).map(group => (
        <Accordion key={group.key} disableGutters variant="outlined">
          <AccordionSummary expandIcon={<ExpandMoreOutlined />}>
            {`${group.label} (${group.count})`}
          </AccordionSummary>
          <AccordionDetails sx={{ pt: 0 }}>
            <ul
              aria-label={group.label}
              style={{
                margin: 0,
                paddingLeft: theme.spacing(2),
                maxHeight: 200,
                overflowY: 'auto',
              }}
            >
              {group.items!.map(item => (
                <li key={item.id}>
                  <Link to={group.path(item.id)} target="_blank" rel="noopener noreferrer">{item.name}</Link>
                </li>
              ))}
            </ul>
            {group.count > group.items!.length && (
              <div style={{
                marginTop: theme.spacing(1),
                color: theme.palette.text.secondary,
              }}
              >
                {t('Showing {shown} of {count}', {
                  shown: String(group.items!.length),
                  count: String(group.count),
                })}
              </div>
            )}
          </AccordionDetails>
        </Accordion>
      ))}
    </div>
  );
};

export default PayloadUsageWarning;
