import { Box, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import { type FindingsData, type ModuleDataState } from '../useReportingRenderData';
import { ModuleEmpty, ModuleError, PrintChip, ReportCell, ReportRow, ReportTable } from './ModuleSection';

/**
 * Findings module: distribution by finding type (criticality-tinted tags:
 * the search engine does not index a per-finding severity, so vulnerability
 * carrying types are emphasized) plus the latest findings table, on the
 * shared print table for guaranteed header/column alignment.
 */

interface Props { findings: ModuleDataState<FindingsData> }

/** Finding types that represent actual security weaknesses. */
const CRITICAL_TYPES = new Set(['cve', 'vulnerability', 'credentials']);
const WARNING_TYPES = new Set(['port', 'portscan', 'kerberoastable_account', 'asreproastable_account', 'account_with_password_not_required']);

const FindingsModule: FunctionComponent<Props> = ({ findings }) => {
  const theme = useTheme();
  const { t, n, nsdt } = useFormatter();

  if (findings.status === 'error') return <ModuleError />;
  if (findings.status === 'unsupported') {
    return <ModuleEmpty message={t('Findings are not available for this report subject.')} />;
  }
  const data = findings.data;
  if (findings.status !== 'success' || !data || (data.byType.length === 0 && data.latest.length === 0)) {
    return <ModuleEmpty message={t('No finding over the selected time range.')} />;
  }

  const typeColor = (type: string): string => {
    const normalized = type.toLowerCase();
    if (CRITICAL_TYPES.has(normalized)) return theme.palette.error.main;
    if (WARNING_TYPES.has(normalized)) return theme.palette.warning.main;
    return theme.palette.primary.main;
  };

  return (
    <Box>
      <Box sx={{
        display: 'flex',
        gap: 1,
        flexWrap: 'wrap',
        marginBottom: 2.5,
      }}
      >
        {data.byType.map(entry => (
          <PrintChip
            key={entry.type}
            label={`${entry.type} - ${n(entry.count)}`}
            color={typeColor(entry.type)}
          />
        ))}
      </Box>
      {data.latest.length > 0 && (
        <ReportTable
          columns={[
            { label: t('Finding') },
            {
              label: t('Type'),
              width: 150,
            },
            {
              label: t('First seen'),
              width: 170,
            },
          ]}
        >
          {data.latest.map((row, index) => (
            // Rows have no natural id in the flattened ES payload.
            // eslint-disable-next-line react/no-array-index-key
            <ReportRow key={index}>
              <ReportCell>
                <Typography sx={{
                  fontSize: 12,
                  fontWeight: 500,
                  overflowWrap: 'anywhere',
                }}
                >
                  {row.value}
                </Typography>
              </ReportCell>
              <ReportCell>
                <PrintChip label={row.type} color={typeColor(row.type)} />
              </ReportCell>
              <ReportCell>
                <Typography sx={{
                  fontSize: 11,
                  color: 'text.secondary',
                  whiteSpace: 'nowrap',
                }}
                >
                  {row.date ? nsdt(row.date) : '-'}
                </Typography>
              </ReportCell>
            </ReportRow>
          ))}
        </ReportTable>
      )}
    </Box>
  );
};

export default FindingsModule;
