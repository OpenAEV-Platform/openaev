import { Box, Card, CardActionArea, CardContent, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';

import { useFormatter } from '../../../components/i18n';
import PlatformIcon from '../../../components/PlatformIcon';
import { type ExecutorOutput } from '../../../utils/api-types';

interface PlatformSelectorProps {
  selectedExecutor: ExecutorOutput;
  setPlatform: (platform: string) => void;
  setActiveStep: (step: number) => void;
}

// Choice cards in a dialog, on the same frame as the sibling product: outlined,
// equal columns, the icon on top and the label under it, everything centred.
const PlatformSelector: React.FC<PlatformSelectorProps> = ({ selectedExecutor, setPlatform, setActiveStep }) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const platforms = selectedExecutor?.executor_platforms ?? [];

  const handlePlatformSelection = (platformSelected: string) => {
    setPlatform(platformSelected);
    setActiveStep(1);
  };

  return (
    <Box sx={{
      display: 'grid',
      gridTemplateColumns: `repeat(${platforms.length}, 1fr)`,
      gap: 1,
    }}
    >
      {platforms.map(platform => (
        <Card
          key={platform}
          variant="outlined"
          aria-label={t('Install {platform} agent', { platform })}
          sx={{
            minWidth: 0,
            textAlign: 'center',
            // No outline on a choice card: the outlined variant is kept only to
            // hold the geometry, its border is transparent.
            borderColor: 'transparent',
          }}
        >
          <CardActionArea
            onClick={() => handlePlatformSelection(platform)}
            sx={{
              height: '100%',
              padding: theme.spacing(3),
            }}
          >
            <CardContent sx={{
              'padding': 0,
              '&:last-child': { paddingBottom: 0 },
            }}
            >
              <Box><PlatformIcon platform={platform} width={40} /></Box>
              <Typography gutterBottom variant="h2" sx={{ marginBlock: 2 }}>
                {t('Install {platform} agent', { platform })}
              </Typography>
            </CardContent>
          </CardActionArea>
        </Card>
      ))}
    </Box>
  );
};

export default PlatformSelector;
