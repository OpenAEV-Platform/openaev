import { OpenInNew } from '@mui/icons-material';
import { Card, CardActionArea, CardContent, Link, Radio, Stack, Typography } from '@mui/material';
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
    alt="Chaining illustration"
    style={{
      width: 160,
      height: 60,
      objectFit: 'contain',
    }}
  />
);

/**
 * Time-based scenario illustration loaded from a PNG asset.
 */
const TimeBasedIllustration: FunctionComponent<{ isDark: boolean }> = ({ isDark }) => (
  <img
    src={isDark ? timeBasedIllustrationDark : timeBasedIllustrationLight}
    alt="Time-based illustration"
    style={{
      width: 160,
      height: 60,
      objectFit: 'contain',
    }}
  />
);

export type EngineType = 'chaining' | 'time-based' | null;

interface EngineTypeSelectionProps {
  selected: EngineType;
  onSelect: (type: EngineType) => void;
  context?: 'scenario' | 'simulation';
}

const EngineTypeSelection: FunctionComponent<EngineTypeSelectionProps> = ({
  selected,
  onSelect,
  context = 'scenario',
}) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const {
    isValidated: isEnterpriseEdition,
    openDialog: openEnterpriseEditionDialog,
    setEEFeatureDetectedInfo,
  } = useEnterpriseEdition();

  const isSimulation = context === 'simulation';

  const options: Array<{
    type: NonNullable<EngineType>;
    title: string;
    description: string;
  }> = [
    {
      type: 'chaining',
      title: t(isSimulation ? 'chaining.chaining-simulation.title' : 'chaining.chaining-scenario.title'),
      description: t('chaining.chaining-scenario.description'),
    },
    {
      type: 'time-based',
      title: t(isSimulation ? 'chaining.chaining-timebased-simulation.title' : 'chaining.chaining-timebased.title'),
      description: t('chaining.chaining-timebased.description'),
    },
  ];

  const handleCardClick = (type: NonNullable<EngineType>) => {
    if (type === 'chaining' && !isEnterpriseEdition) {
      setEEFeatureDetectedInfo(t(isSimulation ? 'chaining.chaining-simulation.title' : 'chaining.chaining-scenario.title'));
      openEnterpriseEditionDialog();
      return;
    }
    onSelect(type);
  };

  return (
    <>
      <Stack sx={{
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: theme.spacing(2),
      }}
      >
        <Typography variant="body2" color="text.secondary">
          {t(isSimulation ? 'chaining.select-type-simulation' : 'chaining.select-type')}
        </Typography>
        <Link
          href="https://docs.openaev.io/latest/usage/chaining/"
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
          {isSimulation ? t('chaining.doc-link-simulation') : t('chaining.doc-link')}
        </Link>
      </Stack>
      <Stack
        sx={{
          display: 'grid',
          gap: theme.spacing(2),
          gridTemplateColumns: 'repeat(2, 1fr)',
        }}
      >
        {options.map((option) => {
          const isChaining = option.type === 'chaining';
          const isSelected = selected === option.type;
          const isDisabled = isChaining && !isEnterpriseEdition;
          return (
            <Card
              key={option.type}
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
                onClick={() => handleCardClick(option.type)}
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
                  <Stack sx={{
                    flexDirection: 'row',
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
                    {isChaining && !isEnterpriseEdition && <EEChip clickable />}
                  </Stack>
                  <Typography variant="caption" color="text.secondary" sx={{ textAlign: 'center' }}>
                    {option.description}
                  </Typography>
                  {/* Illustrative workflow diagram */}
                  <Stack sx={{ marginTop: theme.spacing(1) }}>
                    {isChaining
                      ? <ChainingIllustration isDark={theme.palette.mode === 'dark'} />
                      : <TimeBasedIllustration isDark={theme.palette.mode === 'dark'} />}
                  </Stack>
                </CardContent>
              </CardActionArea>
            </Card>
          );
        })}
      </Stack>
    </>
  );
};

export default EngineTypeSelection;
