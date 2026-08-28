import { Box, Typography } from '@mui/material';
import { alpha, type Theme, useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import { donutChartOptions } from '../../../../../utils/Charts';
import expectationIconByType, { expectationTypeColor } from '../../../common/ExpectationIconByType';
import { type ModuleDataState, type PostureData } from '../useReportingRenderData';
import { ModuleEmpty, ModuleError, ReportCell, ReportRow, ReportTable } from './ModuleSection';
import PrintChart from './PrintChart';

/**
 * Expectation results distribution: overall success/failure donut plus a
 * per-expectation-type table (type icon, counts, colored rate and a small
 * inline rate bar) on the shared print table primitives.
 */

interface Props { posture: ModuleDataState<PostureData> }

const rateColor = (theme: Theme, ratePct: number): string => {
  if (ratePct >= 75) return theme.palette.success.main;
  if (ratePct >= 50) return theme.palette.warning.main;
  return theme.palette.error.main;
};

const TYPE_LABELS: Record<string, string> = {
  PREVENTION: 'Prevention',
  DETECTION: 'Detection',
  VULNERABILITY: 'Vulnerability',
  MANUAL: 'Human response',
  ARTICLE: 'Media pressure',
  CHALLENGE: 'Challenge',
};

const ResultsBreakdownModule: FunctionComponent<Props> = ({ posture }) => {
  const theme = useTheme();
  const { t, n } = useFormatter();

  if (posture.status === 'error') return <ModuleError />;
  if (posture.status !== 'success' || !posture.data || posture.data.tested === 0) {
    return <ModuleEmpty message={t('No validated expectation over the selected time range.')} />;
  }

  const { success, failed, breakdown } = posture.data;
  const donutLabels = [t('Successful'), t('Failed')];
  const options = donutChartOptions({
    theme,
    labels: donutLabels,
    chartColors: [theme.palette.success.main, theme.palette.error.main],
    displayLegend: true,
    legendPosition: 'bottom',
    disableAnimation: true,
  });

  return (
    <Box sx={{
      display: 'flex',
      gap: 4,
      alignItems: 'flex-start',
    }}
    >
      <Box sx={{ flexShrink: 0 }}>
        <PrintChart
          options={options}
          series={[success, failed]}
          type="donut"
          width={240}
          height={240}
        />
      </Box>
      <Box sx={{
        flex: 1,
        paddingTop: 1,
      }}
      >
        <ReportTable
          columns={[
            { label: t('Expectation type') },
            {
              label: t('Successful'),
              width: 80,
            },
            {
              label: t('Failed'),
              width: 70,
            },
            {
              label: t('Success rate'),
              width: 130,
            },
          ]}
        >
          {breakdown.map((entry) => {
            const total = entry.success + entry.failed;
            const ratePct = total > 0 ? Math.round((entry.success / total) * 100) : 0;
            const color = rateColor(theme, ratePct);
            return (
              <ReportRow key={entry.type}>
                <ReportCell>
                  <Box sx={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 1,
                  }}
                  >
                    <Box sx={{
                      display: 'inline-flex',
                      color: expectationTypeColor(entry.type),
                    }}
                    >
                      {expectationIconByType(entry.type, { fontSize: 14 })}
                    </Box>
                    <Typography sx={{
                      fontSize: 12,
                      fontWeight: 500,
                    }}
                    >
                      {t(TYPE_LABELS[entry.type] ?? entry.type)}
                    </Typography>
                  </Box>
                </ReportCell>
                <ReportCell>
                  <Typography sx={{
                    fontSize: 12,
                    fontWeight: 600,
                    color: 'success.main',
                  }}
                  >
                    {n(entry.success)}
                  </Typography>
                </ReportCell>
                <ReportCell>
                  <Typography sx={{
                    fontSize: 12,
                    fontWeight: 600,
                    color: 'error.main',
                  }}
                  >
                    {n(entry.failed)}
                  </Typography>
                </ReportCell>
                <ReportCell>
                  <Box sx={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 1,
                  }}
                  >
                    <Box sx={{
                      flex: 1,
                      height: 5,
                      borderRadius: 2,
                      overflow: 'hidden',
                      backgroundColor: alpha(theme.palette.text.primary, 0.08),
                    }}
                    >
                      <Box sx={{
                        width: `${ratePct}%`,
                        height: '100%',
                        borderRadius: 2,
                        backgroundColor: color,
                      }}
                      />
                    </Box>
                    <Typography sx={{
                      width: 36,
                      flexShrink: 0,
                      fontSize: 12,
                      fontWeight: 700,
                      textAlign: 'right',
                      color,
                    }}
                    >
                      {`${ratePct}%`}
                    </Typography>
                  </Box>
                </ReportCell>
              </ReportRow>
            );
          })}
        </ReportTable>
      </Box>
    </Box>
  );
};

export default ResultsBreakdownModule;
