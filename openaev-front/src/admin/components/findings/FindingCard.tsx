import { Chip, type ChipSeverity, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { CheckCircleOutlined, RadioButtonUncheckedOutlined } from '@mui/icons-material';
import { Box, Card, CardActionArea, CardContent, Checkbox, Stack, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import moment from 'moment-timezone';
import { type MouseEvent, useState } from 'react';
import { useNavigate } from 'react-router';

import FindingIcon from '../../../components/FindingIcon';
import { useFormatter } from '../../../components/i18n';
import prowlerIcon from '../../../static/images/prowler.png';
import { buildTenantApiPath } from '../../../utils/url-helper';
import { findingAssetLabel, type StableFindingRow } from './FindingRowUtils';
import getFindingTypeLabel from './FindingTypeLabel';

type CardFinding = StableFindingRow;

// Severity is the only colored element of the card, carried by its chip.
const SEVERITY_CHIP: Record<string, ChipSeverity> = {
  critical: 'critical',
  high: 'high',
  medium: 'medium',
  low: 'low',
};

// Logo of the scanner that produced the Finding, like the connector logo of an Integration card:
// Prowler ships its own image, other injectors serve theirs, and the type icon is the fallback.
const SourceLogo = ({ finding }: { finding: CardFinding }) => {
  const [failed, setFailed] = useState(false);
  const source = finding.finding_source;
  const isProwler = [source?.injector_name, source?.injector_type]
    .some(value => value?.toLowerCase().includes('prowler'));
  const logoSrc = isProwler
    ? prowlerIcon
    : source?.injector_type && buildTenantApiPath(`/api/injectors/${source.injector_type}/image`);

  if (!logoSrc || failed) {
    return <FindingIcon findingType={finding.finding_type} />;
  }
  return (
    <img
      src={logoSrc}
      alt={source?.injector_name ?? ''}
      onError={() => setFailed(true)}
      style={{
        width: 40,
        height: 40,
        objectFit: 'contain',
        borderRadius: 4,
      }}
    />
  );
};

interface Props {
  finding: CardFinding;
  checked: boolean;
  anySelected: boolean;
  onToggleEntity: (event: MouseEvent<HTMLElement>) => void;
}

const FindingCard = ({ finding, checked, anySelected, onToggleEntity }: Props) => {
  const { t, nsdt } = useFormatter();
  const theme = useTheme();
  const navigate = useNavigate();
  const showCheckbox = checked || anySelected;

  const asset = findingAssetLabel(finding);
  const severity = finding.finding_severity?.toLowerCase();

  return (
    // Same frame as the Integrations catalog card (CatalogConnectorCard): neutral surface,
    // lift and primary glow on hover.
    <Box
      data-testid="finding-card"
      sx={{
        'height': '100%',
        '& .MuiCard-root': {
          border: `1px solid ${checked ? theme.palette.primary.main : alpha(theme.palette.text.primary, 0.08)}`,
          transition: 'transform 0.3s ease-in-out, border-color 0.3s ease-in-out, box-shadow 0.3s ease-in-out',
        },
        '&:hover .MuiCard-root': {
          transform: 'translateY(-2px)',
          borderColor: alpha(theme.palette.primary.main, 0.3),
          boxShadow: `0 0 30px ${alpha(theme.palette.primary.main, 0.12)}`,
        },
        '&:hover .finding-card-checkbox': { opacity: 1 },
      }}
    >
      <Card
        variant="outlined"
        sx={{
          height: 220,
          borderRadius: 1,
          display: 'flex',
          backgroundColor: theme.palette.mode === 'dark' ? theme.palette.background.secondary : undefined,
        }}
      >
        <CardActionArea
          onClick={() => navigate(`/admin/findings/${finding.finding_id}`)}
          sx={{
            display: 'flex',
            alignItems: 'stretch',
          }}
        >
          <CardContent
            sx={{
              display: 'flex',
              flexDirection: 'column',
              gap: 2,
              padding: 3,
              height: '100%',
              width: '100%',
            }}
          >
            <Stack
              direction="row"
              sx={{
                gap: 1.5,
                alignItems: 'flex-start',
                width: '100%',
              }}
            >
              <Box
                sx={{
                  width: 56,
                  height: 56,
                  flexShrink: 0,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  borderRadius: 1,
                  backgroundColor: alpha(theme.palette.text.primary, 0.04),
                }}
              >
                <SourceLogo finding={finding} />
              </Box>
              <Box sx={{
                flex: 1,
                minWidth: 0,
              }}
              >
                <Typography
                  variant="body2"
                  sx={{
                    color: 'primary.main',
                    fontSize: 12,
                    fontWeight: 500,
                    letterSpacing: '0.06em',
                    textTransform: 'uppercase',
                    marginBottom: 0.5,
                  }}
                >
                  {getFindingTypeLabel(t, finding.finding_type, finding.finding_cloud_provider)}
                </Typography>
                <Tooltip>
                  <TooltipTrigger asChild>
                    <Typography
                      sx={{
                        fontSize: 15,
                        fontWeight: 600,
                        lineHeight: 1.35,
                        display: '-webkit-box',
                        WebkitLineClamp: 2,
                        WebkitBoxOrient: 'vertical',
                        overflow: 'hidden',
                        wordBreak: 'break-word',
                      }}
                    >
                      {finding.finding_value}
                    </Typography>
                  </TooltipTrigger>
                  <TooltipContent side="bottom" align="start">{finding.finding_value}</TooltipContent>
                </Tooltip>
              </Box>
              {/* Selection, where the Integration card shows its support icon: visible on hover or
                  as soon as one card is selected. */}
              <Box
                className="finding-card-checkbox"
                onMouseDown={event => event.stopPropagation()}
                onClick={(event) => {
                  event.preventDefault();
                  event.stopPropagation();
                  onToggleEntity(event);
                }}
                sx={{
                  flexShrink: 0,
                  marginTop: -0.75,
                  marginRight: -0.75,
                  opacity: showCheckbox ? 1 : 0,
                  transition: theme.transitions.create('opacity'),
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
            </Stack>

            <Box sx={{
              flexGrow: 1,
              overflow: 'hidden',
              width: '100%',
            }}
            >
              <Typography
                variant="body2"
                sx={{
                  color: 'text.secondary',
                  lineHeight: 1.5,
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                }}
              >
                {`${t('Asset')}: ${asset}`}
              </Typography>
            </Box>

            {/* Footer: severity on the left, last detection on the right (US.1 card fields: type,
                value, severity, asset, last seen). */}
            <div style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              flexWrap: 'wrap',
              gap: theme.spacing(1),
              width: '100%',
            }}
            >
              <Stack direction="row" spacing={1} sx={{ flexShrink: 0 }}>
                {finding.finding_severity && (
                  <Chip
                    label={finding.finding_severity.toUpperCase()}
                    severity={(severity ? SEVERITY_CHIP[severity] : undefined) ?? 'neutral'}
                  />
                )}
              </Stack>
              <Tooltip>
                <TooltipTrigger asChild>
                  <Typography
                    variant="body2"
                    sx={{
                      marginLeft: 'auto',
                      flexShrink: 0,
                      color: 'text.secondary',
                      fontSize: 12,
                    }}
                  >
                    {moment(finding.finding_updated_at).fromNow()}
                  </Typography>
                </TooltipTrigger>
                <TooltipContent>{`${t('Last seen')}: ${nsdt(finding.finding_updated_at)}`}</TooltipContent>
              </Tooltip>
            </div>
          </CardContent>
        </CardActionArea>
      </Card>
    </Box>
  );
};

export default FindingCard;
