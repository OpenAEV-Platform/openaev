import { Box, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactNode } from 'react';

import { useFormatter } from '../../../components/i18n';

interface Stat {
  id: string;
  label: string;
  value: number | string;
  color?: string;
}

interface Props {
  totalElements: number;
  stats: Stat[];
  rightSlot?: ReactNode;
  bottomSlot?: ReactNode;
}

const ThreatArsenalHero: FunctionComponent<Props> = ({
  totalElements,
  stats,
  rightSlot,
  bottomSlot,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const primary = theme.palette.primary.main;

  return (
    <Box
      component="section"
      aria-label={t('Threat Arsenal')}
      sx={{
        // Same hero surface as the Integrations marketplace (shared visual language).
        position: 'relative',
        borderRadius: 1,
        padding: {
          xs: 2,
          md: 3,
        },
        overflow: 'hidden',
        border: `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
        backgroundColor: theme.palette.background.paper,
      }}
    >
      <Box
        aria-hidden
        sx={{
          position: 'absolute',
          top: -100,
          right: -60,
          width: 260,
          height: 260,
          borderRadius: '50%',
          background: alpha(primary, 0.08),
          filter: 'blur(60px)',
          pointerEvents: 'none',
        }}
      />

      <Box sx={{
        position: 'relative',
        display: 'flex',
        flexDirection: {
          xs: 'column',
          md: 'row',
        },
        gap: 1.5,
        alignItems: {
          xs: 'stretch',
          md: 'center',
        },
        justifyContent: 'space-between',
      }}
      >
        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: 1.25,
          minWidth: 0,
        }}
        >
          <Typography
            variant="h1"
            sx={{
              fontWeight: 700,
              margin: 0,
              fontSize: 22,
              whiteSpace: 'nowrap',
            }}
          >
            {t('Threat Arsenal')}
          </Typography>

          <Box sx={{
            display: 'flex',
            flexWrap: 'wrap',
            gap: 0.75,
          }}
          >
            {/* Same stat pill design as the Integrations marketplace hero. */}
            <Box
              sx={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 0.75,
                paddingBlock: 0.5,
                paddingInline: 1.25,
                borderRadius: 1,
                border: `1px solid ${alpha(theme.palette.text.primary, 0.1)}`,
                backgroundColor: alpha(theme.palette.text.primary, 0.04),
              }}
            >
              <Typography sx={{
                fontWeight: 600,
                fontSize: 13,
                fontVariantNumeric: 'tabular-nums',
              }}
              >
                {totalElements}
              </Typography>
              <Typography sx={{
                color: 'text.secondary',
                fontSize: 13,
              }}
              >
                {t('total actions')}
              </Typography>
            </Box>
            {stats.map(stat => (
              <Box
                key={stat.id}
                sx={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 0.75,
                  paddingBlock: 0.5,
                  paddingInline: 1.25,
                  borderRadius: 1,
                  border: `1px solid ${alpha(theme.palette.text.primary, 0.1)}`,
                  backgroundColor: alpha(theme.palette.text.primary, 0.04),
                }}
              >
                <Typography sx={{
                  fontWeight: 600,
                  fontSize: 13,
                  color: stat.color ?? 'text.primary',
                  fontVariantNumeric: 'tabular-nums',
                }}
                >
                  {stat.value}
                </Typography>
                <Typography sx={{
                  color: 'text.secondary',
                  fontSize: 13,
                }}
                >
                  {t(stat.label)}
                </Typography>
              </Box>
            ))}
          </Box>
        </Box>

        {rightSlot && (
          <Box sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 1,
            flexWrap: 'wrap',
            justifyContent: {
              xs: 'flex-start',
              md: 'flex-end',
            },
            flexShrink: 0,
          }}
          >
            {rightSlot}
          </Box>
        )}
      </Box>

      {bottomSlot && (
        <Box sx={{
          position: 'relative',
          marginTop: 1.25,
        }}
        >
          {bottomSlot}
        </Box>
      )}
    </Box>
  );
};

export default ThreatArsenalHero;
