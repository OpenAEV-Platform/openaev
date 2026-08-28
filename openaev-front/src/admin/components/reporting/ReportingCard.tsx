import { FileDownloadOutlined } from '@mui/icons-material';
import { Box, Chip, IconButton, Paper, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
import { Link } from 'react-router';

import { downloadReportingGenerationUrl } from '../../../actions/reporting/reporting-actions';
import { useFormatter } from '../../../components/i18n';
import { type Reporting } from '../../../utils/api-types';
import {
  latestGeneration,
  REPORTING_CONTEXT_ICONS,
  REPORTING_CONTEXT_LABELS,
} from './ReportingContexts';
import { ReportingFormatFragment, ReportingStatusChip } from './ReportingFragments';
import ReportingPopover from './ReportingPopover';

interface Props {
  reporting: Reporting;
  onUpdate?: (result: Reporting) => void;
  onDelete?: (reportingId: string) => void;
}

// Marketplace-style card, same anatomy as the custom dashboard cards
// (framed icon, name, clamped description, hover lift).
const ReportingCard: FunctionComponent<Props> = ({ reporting, onUpdate, onDelete }) => {
  const theme = useTheme();
  const { t, fldt } = useFormatter();
  const ContextIcon = REPORTING_CONTEXT_ICONS[reporting.reporting_context_type];
  const generation = latestGeneration(reporting);
  const downloadable = generation?.reporting_generation_status === 'SUCCESS' && generation.reporting_generation_document;

  return (
    <Paper
      variant="outlined"
      data-testid="reporting-card"
      // Real router link (not a JS navigate) so ctrl/cmd+click opens a new tab.
      component={Link}
      to={`/admin/reporting/${reporting.reporting_id}`}
      sx={{
        'position': 'relative',
        'display': 'flex',
        'flexDirection': 'column',
        'gap': 1.5,
        'padding': 2,
        'borderRadius': 1,
        'height': '100%',
        'cursor': 'pointer',
        'textDecoration': 'none',
        'color': 'inherit',
        'transition': theme.transitions.create(['border-color', 'box-shadow', 'transform']),
        '&:hover': {
          borderColor: alpha(theme.palette.primary.main, 0.5),
          boxShadow: `0 4px 16px ${alpha(theme.palette.common.black, 0.25)}`,
          transform: 'translateY(-2px)',
        },
      }}
    >
      <Box
        sx={{
          position: 'absolute',
          top: 6,
          right: 6,
          display: 'flex',
          alignItems: 'center',
        }}
        onClick={(event) => {
          // The card is a real link: also cancel the native anchor navigation,
          // stopPropagation() alone only blocks the router's client-side handler.
          event.preventDefault();
          event.stopPropagation();
        }}
      >
        {downloadable && (
          <Tooltip title={t('Download latest generation')}>
            <IconButton
              size="small"
              color="primary"
              // Programmatic download instead of an <a href>: the card itself is
              // an anchor, and the wrapper's preventDefault() (needed to cancel
              // the card navigation) would also cancel a nested link's default.
              // The endpoint replies Content-Disposition: attachment, so
              // assigning the URL downloads without leaving the page.
              onClick={() => window.location.assign(downloadReportingGenerationUrl(generation.reporting_generation_id))}
            >
              <FileDownloadOutlined fontSize="small" />
            </IconButton>
          </Tooltip>
        )}
        <ReportingPopover reporting={reporting} onUpdate={onUpdate} onDelete={onDelete} />
      </Box>

      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1.5,
        minWidth: 0,
        paddingRight: 7,
      }}
      >
        <Box sx={{
          width: 44,
          height: 44,
          flexShrink: 0,
          borderRadius: 1,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: 'primary.main',
          border: `1px solid ${alpha(theme.palette.primary.main, 0.2)}`,
          backgroundColor: alpha(theme.palette.primary.main, 0.08),
        }}
        >
          <ContextIcon />
        </Box>
        <Tooltip title={reporting.reporting_name}>
          <Typography sx={{
            fontSize: 14,
            fontWeight: 600,
            lineHeight: 1.35,
            display: '-webkit-box',
            WebkitLineClamp: 2,
            WebkitBoxOrient: 'vertical',
            overflow: 'hidden',
            wordBreak: 'break-word',
          }}
          >
            {reporting.reporting_name}
          </Typography>
        </Tooltip>
      </Box>

      <Typography
        variant="body2"
        sx={{
          color: 'text.secondary',
          minHeight: 40,
          display: '-webkit-box',
          WebkitLineClamp: 2,
          WebkitBoxOrient: 'vertical',
          overflow: 'hidden',
        }}
      >
        {reporting.reporting_description || '-'}
      </Typography>

      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1,
        flexWrap: 'wrap',
      }}
      >
        <Chip
          icon={<ContextIcon sx={{ fontSize: 14 }} />}
          label={t(REPORTING_CONTEXT_LABELS[reporting.reporting_context_type])}
          size="small"
          variant="outlined"
        />
        <ReportingFormatFragment format={reporting.reporting_default_format} />
        {generation && (
          <ReportingStatusChip status={generation.reporting_generation_status} />
        )}
      </Box>

      <Typography
        variant="body2"
        sx={{
          marginTop: 'auto',
          fontSize: 12,
          color: 'text.secondary',
        }}
      >
        {`${t('Updated at')} ${fldt(reporting.reporting_updated_at)}`}
      </Typography>
    </Paper>
  );
};

export default ReportingCard;
