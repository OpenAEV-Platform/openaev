import { Box, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import { type MitreData, type ModuleDataState } from '../useReportingRenderData';
import { ModuleEmpty, ModuleError, PrintChip } from './ModuleSection';

/**
 * Kill chain coverage, print edition: distinct techniques exercised plus a
 * per-technique success/failure bar list, scoped to the kill chains selected
 * in the module config (all kill chains when unset). The interactive matrix
 * (SecurityCoverageContent) depends on the Redux attack-pattern referential
 * and local-storage-driven controls, so the render page uses this slim,
 * deterministic equivalent instead.
 */

interface Props { mitre: ModuleDataState<MitreData> }

const MitreCoverageModule: FunctionComponent<Props> = ({ mitre }) => {
  const theme = useTheme();
  const { t } = useFormatter();

  if (mitre.status === 'error') return <ModuleError />;
  if (mitre.status !== 'success' || !mitre.data || mitre.data.techniques.length === 0) {
    return <ModuleEmpty message={t('No technique was exercised over the selected time range.')} />;
  }

  const { coveredCount, techniques, killChains } = mitre.data;
  const maxTotal = Math.max(...techniques.map(entry => entry.success + entry.failed), 1);

  return (
    <Box>
      <Box sx={{
        display: 'flex',
        alignItems: 'baseline',
        gap: 1,
        marginBottom: 1,
      }}
      >
        <Typography sx={{
          fontFamily: '"Geologica", sans-serif',
          fontSize: 24,
          fontWeight: 600,
          color: 'primary.main',
        }}
        >
          {coveredCount}
        </Typography>
        <Typography sx={{
          fontSize: 12,
          color: 'text.secondary',
        }}
        >
          {t('techniques exercised - top results below')}
        </Typography>
      </Box>
      {/* Covered kill chain scope, explicit even in the default all-chains mode. */}
      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        flexWrap: 'wrap',
        gap: 0.75,
        marginBottom: 2.5,
      }}
      >
        {(killChains.length > 0 ? killChains : [t('All kill chains')]).map(name => (
          <PrintChip key={name} label={name} />
        ))}
      </Box>
      <Box sx={{
        display: 'flex',
        flexDirection: 'column',
        gap: 1.25,
      }}
      >
        {techniques.map((technique) => {
          const total = technique.success + technique.failed;
          const successPct = total > 0 ? (technique.success / total) * 100 : 0;
          const widthPct = (total / maxTotal) * 100;
          return (
            <Box
              key={technique.id}
              sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 1.5,
              }}
            >
              <Typography sx={{
                width: 240,
                flexShrink: 0,
                fontSize: 11,
                fontWeight: 500,
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
              }}
              >
                {technique.label}
              </Typography>
              <Box sx={{
                flex: 1,
                height: 14,
                borderRadius: 1,
                backgroundColor: alpha(theme.palette.text.primary, 0.05),
                overflow: 'hidden',
              }}
              >
                <Box sx={{
                  display: 'flex',
                  height: '100%',
                  width: `${widthPct}%`,
                }}
                >
                  <Box sx={{
                    width: `${successPct}%`,
                    backgroundColor: theme.palette.success.main,
                  }}
                  />
                  <Box sx={{
                    width: `${100 - successPct}%`,
                    backgroundColor: theme.palette.error.main,
                  }}
                  />
                </Box>
              </Box>
              <Typography sx={{
                width: 84,
                flexShrink: 0,
                fontSize: 10.5,
                color: 'text.secondary',
                textAlign: 'right',
                whiteSpace: 'nowrap',
              }}
              >
                {`${technique.success}/${total} ${t('passed')}`}
              </Typography>
            </Box>
          );
        })}
      </Box>
      <Box sx={{
        display: 'flex',
        gap: 2,
        marginTop: 2,
      }}
      >
        {[{
          color: theme.palette.success.main,
          label: t('Successful expectations'),
        }, {
          color: theme.palette.error.main,
          label: t('Failed expectations'),
        }].map(item => (
          <Box
            key={item.label}
            sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 0.75,
            }}
          >
            <Box sx={{
              width: 10,
              height: 10,
              borderRadius: 0.5,
              backgroundColor: item.color,
            }}
            />
            <Typography sx={{
              fontSize: 10,
              color: 'text.secondary',
            }}
            >
              {item.label}
            </Typography>
          </Box>
        ))}
      </Box>
    </Box>
  );
};

export default MitreCoverageModule;
