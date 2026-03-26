import { OpenInNew } from '@mui/icons-material';
import { Box, Card, CardActionArea, CardContent, Link, Radio, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../components/i18n';
import chainingIllustrationDark from '../../../static/images/misc/chaining_illustration_dark.png';
import chainingIllustrationLight from '../../../static/images/misc/chaining_illustration_light.png';
import timeBasedIllustrationDark from '../../../static/images/misc/time_based_illustration_dark.png';
import timeBasedIllustrationLight from '../../../static/images/misc/time_based_illustration_light.png';
import useEnterpriseEdition from '../../../utils/hooks/useEnterpriseEdition';
import EEChip from './entreprise_edition/EEChip';

/**
 * Chaining scenario illustration loaded from a PNG asset.
 */
const ChainingIllustration: FunctionComponent<{ isDark: boolean }> = ({ isDark }) => (
  <img
    src={isDark ? chainingIllustrationDark : chainingIllustrationLight}
    alt="Chaining scenario illustration"
    style={{ width: 160, height: 60, objectFit: 'contain' }}
  />
);

/**
 * Time-based scenario illustration loaded from a PNG asset.
 */
const TimeBasedIllustration: FunctionComponent<{ isDark: boolean }> = ({ isDark }) => (
  <img
    src={isDark ? timeBasedIllustrationDark : timeBasedIllustrationLight}
    alt="Time-based scenario illustration"
    style={{ width: 160, height: 60, objectFit: 'contain' }}
  />
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
                'borderColor': isSelected ? theme.palette.primary.main : undefined,
                'borderWidth': isSelected ? 2 : 1,
                'opacity': isDisabled ? 0.6 : 1,
                'transition': 'border-color 0.2s, opacity 0.2s',
                '&:hover': { borderColor: theme.palette.primary.main },
              }}
            >
              <CardActionArea
                onClick={() => handleCardClick(option.isChaining)}
                sx={{
                  height: '100%',
                  padding: theme.spacing(2),
                }}
              >
                <CardContent sx={{
                  'display': 'flex',
                  'flexDirection': 'column',
                  'alignItems': 'center',
                  'gap': theme.spacing(1),
                  'padding': 0,
                  '&:last-child': { paddingBottom: 0 },
                }}
                >
                  <Box sx={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: theme.spacing(0.5),
                  }}
                  >
                    <Radio
                      checked={isSelected}
                      size="small"
                      disabled={isDisabled}
                      sx={{
                        padding: 0,
                        color: theme.palette.primary.main,
                      }}
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
                      ? <ChainingIllustration isDark={theme.palette.mode === 'dark'} />
                      : <TimeBasedIllustration isDark={theme.palette.mode === 'dark'} />}
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
              sx={{
                color: theme.palette.primary.main,
                verticalAlign: 'baseline',
              }}
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
