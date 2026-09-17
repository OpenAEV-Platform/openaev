import { Box, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type ReactNode } from 'react';

import { useFormatter } from '../../../components/i18n';

interface Props {
  totalElements: number;
  rightSlot: ReactNode;
  bottomSlot?: ReactNode;
}

const FindingHero = ({ totalElements, rightSlot, bottomSlot }: Props) => {
  const { t } = useFormatter();
  const theme = useTheme();

  return (
    <Box
      component="section"
      aria-label={t('Findings')}
      sx={{
        position: 'relative',
        overflow: 'hidden',
        border: `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
        borderRadius: 1,
        backgroundColor: 'background.paper',
        padding: {
          xs: 2,
          md: 3,
        },
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
          backgroundColor: alpha(theme.palette.primary.main, 0.08),
          filter: 'blur(60px)',
          pointerEvents: 'none',
        }}
      />
      <Box sx={{
        position: 'relative',
        display: 'flex',
        alignItems: {
          xs: 'stretch',
          md: 'center',
        },
        justifyContent: 'space-between',
        flexDirection: {
          xs: 'column',
          md: 'row',
        },
        gap: 1.5,
      }}
      >
        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: 1.25,
        }}
        >
          <Typography
            variant="h1"
            sx={{
              margin: 0,
              fontSize: 22,
              fontWeight: 700,
            }}
          >
            {t('Findings')}
          </Typography>
          <Box sx={{
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
              fontSize: 13,
              fontWeight: 600,
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
              {t('unique findings')}
            </Typography>
          </Box>
        </Box>
        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: {
            xs: 'flex-start',
            md: 'flex-end',
          },
          flexWrap: 'wrap',
          gap: 1,
        }}
        >
          {rightSlot}
        </Box>
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

export default FindingHero;
