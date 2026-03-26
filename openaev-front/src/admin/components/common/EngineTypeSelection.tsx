import { OpenInNew } from '@mui/icons-material';
import { Box, Card, CardActionArea, CardContent, Link, Radio, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../components/i18n';
import useEnterpriseEdition from '../../../utils/hooks/useEnterpriseEdition';
import EEChip from './entreprise_edition/EEChip';

/**
 * Inline SVG illustration for the chaining scenario card.
 * Renders a branching workflow graph: two input nodes → hub node → two output nodes,
 * with a third output node branching downward.
 */
const ChainingIllustration: FunctionComponent<{ color: string }> = ({ color }) => (
  <svg width="160" height="60" viewBox="0 0 160 60" fill="none" xmlns="http://www.w3.org/2000/svg">
    {/* Connector lines */}
    <line x1="24" y1="14" x2="52" y2="26" stroke={color} strokeWidth="1.5" />
    <line x1="24" y1="46" x2="52" y2="34" stroke={color} strokeWidth="1.5" />
    <line x1="68" y1="30" x2="96" y2="30" stroke={color} strokeWidth="1.5" />
    <line x1="108" y1="26" x2="136" y2="14" stroke={color} strokeWidth="1.5" />
    <line x1="108" y1="34" x2="136" y2="46" stroke={color} strokeWidth="1.5" />
    {/* Input nodes (left) */}
    <rect x="8" y="6" width="16" height="16" rx="3" stroke={color} strokeWidth="1.5" fill="none" />
    <rect x="8" y="38" width="16" height="16" rx="3" stroke={color} strokeWidth="1.5" fill="none" />
    {/* Hub node (center-left) */}
    <circle cx="60" cy="30" r="10" stroke={color} strokeWidth="1.5" fill="none" />
    {/* Middle node */}
    <rect x="92" y="22" width="16" height="16" rx="3" stroke={color} strokeWidth="1.5" fill="none" />
    {/* Output nodes (right) */}
    <rect x="136" y="6" width="16" height="16" rx="3" stroke={color} strokeWidth="1.5" fill="none" />
    <rect x="136" y="38" width="16" height="16" rx="3" stroke={color} strokeWidth="1.5" fill="none" />
  </svg>
);

/**
 * Inline SVG illustration for the time-based scenario card.
 * Renders three evenly-spaced square nodes representing scheduled steps.
 */
const TimeBasedIllustration: FunctionComponent<{ color: string }> = ({ color }) => (
  <svg width="120" height="30" viewBox="0 0 120 30" fill="none" xmlns="http://www.w3.org/2000/svg">
    <rect x="6" y="5" width="20" height="20" rx="3" stroke={color} strokeWidth="1.5" fill="none" />
    <rect x="50" y="5" width="20" height="20" rx="3" stroke={color} strokeWidth="1.5" fill="none" />
    <rect x="94" y="5" width="20" height="20" rx="3" stroke={color} strokeWidth="1.5" fill="none" />
  </svg>
);

interface EngineTypeSelectionProps {
  selected: boolean | null;
  onSelect: (isChaining: boolean) => void;
}

const EngineTypeSelection: FunctionComponent<EngineTypeSelectionProps> = ({
  selected,
  onSelect,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const {
    isValidated: isEnterpriseEdition,
    openDialog: openEnterpriseEditionDialog,
    setEEFeatureDetectedInfo,
  } = useEnterpriseEdition();

  const options: Array<{
    isChaining: boolean;
    title: string;
    description: string;
  }> = [
    {
      isChaining: true,
      title: t('Chaining Scenario'),
      description: t('Runs a fully automated, end-to-end sequence where each step triggers the next potential steps.'),
    },
    {
      isChaining: false,
      title: t('Time-Based Scenario'),
      description: t('Runs at scheduled time intervals, following a fixed time-driven execution plan.'),
    },
  ];

  const handleCardClick = (isChaining: boolean) => {
    if (isChaining && !isEnterpriseEdition) {
      setEEFeatureDetectedInfo(t('Chaining Scenario'));
      openEnterpriseEditionDialog();
      return;
    }
    onSelect(isChaining);
  };

  return (
    <Box sx={{ marginBottom: theme.spacing(3) }}>
      <Box sx={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: theme.spacing(2),
      }}
      >
        <Typography variant="body2" color="text.secondary">
          {t('Select your scenario type')}
        </Typography>
        <Link
          href="https://docs.openaev.io/latest/usage/scenarios/"
          target="_blank"
          rel="noopener noreferrer"
          variant="body2"
          sx={{
            color: theme.palette.primary.main,
            display: 'flex',
            alignItems: 'center',
            gap: theme.spacing(0.5),
          }}
        >
          <OpenInNew sx={{ fontSize: 14 }} />
          {t('Learn more about scenario type')}
        </Link>
      </Box>
      <Box
        sx={{
          display: 'grid',
          gap: theme.spacing(2),
          gridTemplateColumns: '1fr 1fr',
        }}
      >
        {options.map((option) => {
          const isSelected = selected === option.isChaining;
          const isDisabled = option.isChaining && !isEnterpriseEdition;
          return (
            <Card
              key={option.title}
              variant="outlined"
              sx={{
                borderColor: isSelected ? theme.palette.primary.main : undefined,
                borderWidth: isSelected ? 2 : 1,
                opacity: isDisabled ? 0.6 : 1,
                transition: 'border-color 0.2s, opacity 0.2s',
                '&:hover': {
                  borderColor: theme.palette.primary.main,
                },
              }}
            >
              <CardActionArea
                onClick={() => handleCardClick(option.isChaining)}
                sx={{ height: '100%', padding: theme.spacing(2) }}
              >
                <CardContent sx={{
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  gap: theme.spacing(1),
                  padding: 0,
                  '&:last-child': { paddingBottom: 0 },
                }}
                >
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: theme.spacing(0.5) }}>
                    <Radio
                      checked={isSelected}
                      size="small"
                      disabled={isDisabled}
                      sx={{ padding: 0, color: theme.palette.primary.main }}
                    />
                    <Typography variant="subtitle2" sx={{ fontWeight: 'bold' }}>
                      {option.title}
                    </Typography>
                    {option.isChaining && !isEnterpriseEdition && <EEChip clickable />}
                  </Box>
                  <Typography variant="caption" color="text.secondary" sx={{ textAlign: 'center' }}>
                    {option.description}
                  </Typography>
                  {/* Illustrative workflow diagram */}
                  <Box sx={{ marginTop: theme.spacing(1) }}>
                    {option.isChaining
                      ? <ChainingIllustration color={theme.palette.text.secondary} />
                      : <TimeBasedIllustration color={theme.palette.text.secondary} />}
                  </Box>
                </CardContent>
              </CardActionArea>
            </Card>
          );
        })}
      </Box>
      {!isEnterpriseEdition && selected !== false && (
        <Box sx={{ marginTop: theme.spacing(1.5) }}>
          <Typography variant="caption" color="text.secondary">
            {t('You need to activate OpenAEV enterprise edition to use this feature.')}
            {' '}
            <Link
              component="button"
              variant="caption"
              onClick={() => {
                setEEFeatureDetectedInfo(t('Chaining Scenario'));
                openEnterpriseEditionDialog();
              }}
              sx={{ color: theme.palette.primary.main, verticalAlign: 'baseline' }}
            >
              {t('Manage your Enterprise Edition license')}
            </Link>
          </Typography>
        </Box>
      )}
    </Box>
  );
};

export default EngineTypeSelection;



