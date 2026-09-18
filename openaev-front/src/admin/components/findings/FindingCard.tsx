import { CheckCircleOutlined, RadioButtonUncheckedOutlined } from '@mui/icons-material';
import { Box, Card, CardActionArea, Checkbox, Chip, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type MouseEvent } from 'react';
import { useNavigate } from 'react-router';

import FindingIcon from '../../../components/FindingIcon';
import { useFormatter } from '../../../components/i18n';
import { type AggregatedFindingOutput } from '../../../utils/api-types';
import FindingTriageControl from './FindingTriageControl';
import getFindingTypeLabel from './FindingTypeLabel';

type CardFinding = AggregatedFindingOutput & {
  finding_location?: string;
  finding_location_key?: string;
  finding_occurrences?: number;
};

const severityAccent = (severity: string | null | undefined, fallback: string): string => {
  switch (severity?.toLowerCase()) {
    case 'critical':
      return '#f44336';
    case 'high':
      return '#ff9800';
    case 'medium':
      return '#facc15';
    case 'low':
      return '#4caf50';
    case 'unknown':
      return '#607d8b';
    default:
      return fallback;
  }
};

interface Props {
  finding: CardFinding;
  checked: boolean;
  anySelected: boolean;
  onToggleEntity: (event: MouseEvent<HTMLElement>) => void;
  onTriageChange: (status: NonNullable<AggregatedFindingOutput['finding_triage_status']>) => void;
}

const FindingCard = ({ finding, checked, anySelected, onToggleEntity, onTriageChange }: Props) => {
  const { t, nsdt } = useFormatter();
  const theme = useTheme();
  const navigate = useNavigate();
  const accent = severityAccent(finding.finding_severity, theme.palette.primary.main);
  const showCheckbox = checked || anySelected;
  const location = finding.finding_location ?? finding.finding_location_key;

  return (
    <Card
      variant="outlined"
      data-testid="finding-card"
      sx={{
        'position': 'relative',
        'display': 'flex',
        'height': '100%',
        'flexDirection': 'column',
        'overflow': 'hidden',
        'borderRadius': 1,
        'borderColor': checked ? accent : theme.palette.divider,
        'transition': theme.transitions.create(['border-color', 'box-shadow', 'transform']),
        '&:hover': {
          borderColor: alpha(accent, 0.3),
          boxShadow: `0 0 30px ${alpha(accent, 0.12)}`,
          transform: 'translateY(-2px)',
        },
        '&:hover .finding-card-checkbox': { opacity: 1 },
        '& .MuiSvgIcon-root': { color: accent },
      }}
    >
      <Box
        aria-hidden
        sx={{
          position: 'relative',
          height: 58,
          borderBottom: `1px solid ${alpha(accent, 0.18)}`,
          background: `linear-gradient(135deg, ${alpha(accent, 0.2)} 0%, ${alpha(accent, 0.03)} 100%)`,
        }}
      >
        <Box sx={{
          position: 'absolute',
          bottom: -20,
          left: 16,
          display: 'flex',
          width: 40,
          height: 40,
          alignItems: 'center',
          justifyContent: 'center',
          border: `1px solid ${alpha(accent, 0.35)}`,
          borderRadius: 1.5,
          backgroundColor: 'background.paper',
          boxShadow: `0 4px 12px -4px ${alpha(accent, 0.4)}`,
        }}
        >
          <FindingIcon findingType={finding.finding_type} />
        </Box>
        {finding.finding_severity && (
          <Chip
            label={finding.finding_severity}
            size="small"
            sx={{
              position: 'absolute',
              top: 12,
              right: 12,
              height: 22,
              border: `1px solid ${alpha(accent, 0.45)}`,
              backgroundColor: alpha(accent, 0.2),
              color: accent,
              fontSize: 10,
              fontWeight: 700,
              textTransform: 'uppercase',
            }}
          />
        )}
      </Box>
      <Box
        className="finding-card-checkbox"
        onClick={(event) => {
          event.stopPropagation();
          onToggleEntity(event);
        }}
        sx={{
          'position': 'absolute',
          'top': 8,
          'left': 8,
          'zIndex': 2,
          'opacity': showCheckbox ? 1 : 0,
          'transition': theme.transitions.create('opacity'),
          '& .MuiCheckbox-root': {
            color: alpha('#fff', 0.85),
            padding: 0.5,
          },
        }}
      >
        <Checkbox
          checked={checked}
          disableRipple
          size="small"
          icon={<RadioButtonUncheckedOutlined />}
          checkedIcon={<CheckCircleOutlined />}
          slotProps={{ input: { 'aria-label': finding.finding_value } }}
        />
      </Box>
      <CardActionArea
        onClick={() => navigate(`/admin/findings/${finding.finding_id}`)}
        sx={{
          display: 'flex',
          flex: 1,
          flexDirection: 'column',
          alignItems: 'stretch',
          justifyContent: 'flex-start',
          gap: 1,
          paddingTop: 3.5,
          paddingInline: 2,
          paddingBottom: 2,
        }}
      >
        <Tooltip title={finding.finding_value}>
          <Typography sx={{
            minHeight: 38,
            overflow: 'hidden',
            fontFamily: 'Consolas, monaco, monospace',
            fontSize: 13.5,
            fontWeight: 600,
            lineHeight: 1.4,
            textOverflow: 'ellipsis',
            wordBreak: 'break-word',
          }}
          >
            {finding.finding_value}
          </Typography>
        </Tooltip>
        <Typography variant="caption" color="text.secondary">
          {getFindingTypeLabel(t, finding.finding_type, finding.finding_cloud_provider)}
        </Typography>
        <Typography
          variant="body2"
          sx={{
            overflow: 'hidden',
            minHeight: 20,
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {location ?? t('No asset')}
        </Typography>
        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 1,
          marginTop: 'auto',
          paddingTop: 1,
        }}
        >
          <Typography variant="caption" color="text.secondary">
            {t('{count} occurrences', { count: finding.finding_occurrences ?? 0 })}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {nsdt(finding.finding_updated_at)}
          </Typography>
        </Box>
      </CardActionArea>
      <Box
        onClick={event => event.stopPropagation()}
        sx={{
          borderTop: `1px solid ${theme.palette.divider}`,
          padding: 1,
        }}
      >
        <FindingTriageControl
          variant="inList"
          findingId={finding.finding_id}
          status={finding.finding_triage_status}
          onStatusChange={onTriageChange}
        />
      </Box>
    </Card>
  );
};

export default FindingCard;
