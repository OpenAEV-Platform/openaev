import { Box, Chip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import { type Reporting } from '../../../../../utils/api-types';
import { REPORTING_CONTEXT_ICONS, REPORTING_CONTEXT_LABELS } from '../../ReportingContexts';
import { TIME_RANGE_LABELS } from '../reportingRenderLabels';
import { type ModuleDataState, type ReportingSubject } from '../useReportingRenderData';

/**
 * Full-page branded cover: logo, report name, subject entity, generation date
 * and time range, over a subtle geometric decoration built from the branding
 * colors. Ends with a forced page break.
 */

interface Props {
  reporting: Reporting;
  subject: ModuleDataState<ReportingSubject>;
  logoUrl?: string;
}

const CoverModule: FunctionComponent<Props> = ({ reporting, subject, logoUrl }) => {
  const theme = useTheme();
  const { t, fld } = useFormatter();
  const ContextIcon = REPORTING_CONTEXT_ICONS[reporting.reporting_context_type];
  const primary = theme.palette.primary.main;
  const secondary = theme.palette.secondary.main;

  return (
    <Box
      className="reporting-cover"
      sx={{
        position: 'relative',
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'space-between',
        overflow: 'hidden',
        borderRadius: 1,
        padding: 6,
        backgroundColor: 'background.paper',
        // Layered radial glows + a hairline grid: premium without any asset.
        backgroundImage: [
          `radial-gradient(60% 45% at 85% 0%, ${alpha(primary, 0.22)} 0%, transparent 70%)`,
          `radial-gradient(50% 40% at 0% 100%, ${alpha(secondary, 0.16)} 0%, transparent 70%)`,
          `linear-gradient(${alpha(theme.palette.text.primary, 0.035)} 1px, transparent 1px)`,
          `linear-gradient(90deg, ${alpha(theme.palette.text.primary, 0.035)} 1px, transparent 1px)`,
        ].join(', '),
        backgroundSize: 'auto, auto, 28px 28px, 28px 28px',
      }}
    >
      {/* Diagonal accent ribbon */}
      <Box
        aria-hidden
        sx={{
          position: 'absolute',
          top: 0,
          left: 0,
          right: 0,
          height: 6,
          background: `linear-gradient(90deg, ${primary} 0%, ${secondary} 100%)`,
        }}
      />
      <Box
        aria-hidden
        sx={{
          position: 'absolute',
          top: -120,
          right: -120,
          width: 340,
          height: 340,
          borderRadius: '50%',
          border: `1.5px solid ${alpha(primary, 0.25)}`,
        }}
      />
      <Box
        aria-hidden
        sx={{
          position: 'absolute',
          top: -60,
          right: -60,
          width: 220,
          height: 220,
          borderRadius: '50%',
          border: `1.5px solid ${alpha(secondary, 0.2)}`,
        }}
      />

      {/* Header: logo */}
      <Box sx={{
        position: 'relative',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
      }}
      >
        {logoUrl && (
          <Box
            component="img"
            src={logoUrl}
            alt={t('Logo')}
            sx={{
              maxHeight: 44,
              maxWidth: 220,
              objectFit: 'contain',
            }}
          />
        )}
        <Typography sx={{
          fontFamily: '"Geologica", sans-serif',
          fontSize: 10,
          fontWeight: 600,
          letterSpacing: '0.22em',
          textTransform: 'uppercase',
          color: 'text.secondary',
        }}
        >
          {t('Adversarial exposure validation')}
        </Typography>
      </Box>

      {/* Body: title block */}
      <Box sx={{ position: 'relative' }}>
        <Typography sx={{
          fontFamily: '"Geologica", sans-serif',
          fontSize: 12,
          fontWeight: 600,
          letterSpacing: '0.18em',
          textTransform: 'uppercase',
          color: primary,
          marginBottom: 1.5,
        }}
        >
          {/* No "report" here: the user-defined name below usually already says
              it, and the cover would otherwise repeat the word three times. */}
          {t('Security posture')}
        </Typography>
        <Typography sx={{
          fontFamily: '"Geologica", sans-serif',
          fontSize: 40,
          fontWeight: 600,
          lineHeight: 1.15,
          marginBottom: 2,
        }}
        >
          {reporting.reporting_name}
        </Typography>
        {reporting.reporting_description && (
          <Typography sx={{
            fontSize: 13,
            color: 'text.secondary',
            maxWidth: 520,
            marginBottom: 3,
          }}
          >
            {reporting.reporting_description}
          </Typography>
        )}
        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1.5,
          flexWrap: 'wrap',
        }}
        >
          <Chip
            icon={<ContextIcon sx={{ fontSize: 16 }} />}
            label={t(REPORTING_CONTEXT_LABELS[reporting.reporting_context_type])}
            size="small"
            sx={{
              backgroundColor: alpha(primary, 0.12),
              color: primary,
              fontWeight: 600,
            }}
          />
          {subject.status === 'success' && subject.data && (
            <Typography sx={{
              fontFamily: '"Geologica", sans-serif',
              fontSize: 16,
              fontWeight: 600,
            }}
            >
              {subject.data.name}
            </Typography>
          )}
        </Box>
      </Box>

      {/* Footer: generation metadata */}
      <Box sx={{
        position: 'relative',
        display: 'flex',
        alignItems: 'flex-end',
        justifyContent: 'space-between',
        borderTop: `1px solid ${alpha(theme.palette.text.primary, 0.12)}`,
        paddingTop: 2,
      }}
      >
        <Box>
          <Typography sx={{
            fontSize: 10,
            letterSpacing: '0.14em',
            textTransform: 'uppercase',
            color: 'text.secondary',
          }}
          >
            {t('Generated on')}
          </Typography>
          <Typography sx={{
            fontFamily: '"Geologica", sans-serif',
            fontSize: 14,
            fontWeight: 600,
          }}
          >
            {fld(new Date())}
          </Typography>
        </Box>
        <Box sx={{ textAlign: 'right' }}>
          <Typography sx={{
            fontSize: 10,
            letterSpacing: '0.14em',
            textTransform: 'uppercase',
            color: 'text.secondary',
          }}
          >
            {t('Time range')}
          </Typography>
          <Typography sx={{
            fontFamily: '"Geologica", sans-serif',
            fontSize: 14,
            fontWeight: 600,
          }}
          >
            {t(TIME_RANGE_LABELS[reporting.reporting_time_range])}
          </Typography>
        </Box>
      </Box>
    </Box>
  );
};

export default CoverModule;
