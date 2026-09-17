import { CheckCircleOutlined, RadioButtonUncheckedOutlined } from '@mui/icons-material';
import { Box, Card, CardActionArea, Checkbox, Chip, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type MouseEvent } from 'react';
import { useNavigate } from 'react-router';

import FindingIcon from '../../../components/FindingIcon';
import { useFormatter } from '../../../components/i18n';
import { type AggregatedFindingOutput } from '../../../utils/api-types';
import { getFindingAggregationCategory } from './findingAggregationCategories';
import FindingTriageControl from './FindingTriageControl';
import getFindingTypeLabel from './FindingTypeLabel';

type CardFinding = AggregatedFindingOutput & {
  finding_location?: string;
  finding_location_key?: string;
  finding_aggregation_category?: string;
  finding_occurrences?: number;
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
  const category = getFindingAggregationCategory(finding.finding_aggregation_category);
  const CategoryIcon = category.icon;
  const accent = theme.palette.primary.main;
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
        'transition': theme.transitions.create(['border-color', 'box-shadow', 'transform']),
        '&:hover': {
          borderColor: alpha(accent, 0.3),
          boxShadow: `0 0 30px ${alpha(accent, 0.12)}`,
          transform: 'translateY(-2px)',
        },
        '&:hover .finding-card-checkbox': { opacity: 1 },
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
        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 0.75,
          color: 'text.secondary',
        }}
        >
          <CategoryIcon sx={{ fontSize: 16 }} />
          <Typography variant="caption">{t(category.label)}</Typography>
        </Box>
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
          {location ?? t('No location')}
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
