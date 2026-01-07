import { DownloadingOutlined } from '@mui/icons-material';
import { Card, CardActionArea, CardContent, Grid, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../components/i18n';
import PlatformIcon from '../../../components/PlatformIcon';
import { type ExecutorOutput } from '../../../utils/api-types';

const useStyles = makeStyles()(() => ({
  area: {
    height: '100%',
    width: 220,
  },
}));

interface PlatformSelectorProps {
  selectedExecutor: ExecutorOutput;
  setPlatform: (platform: string) => void;
  setActiveStep: (step: number) => void;
}

const PlatformSelector: React.FC<PlatformSelectorProps> = ({ selectedExecutor, setPlatform, setActiveStep }) => {
  const theme = useTheme();
  const { classes } = useStyles();
  const { t } = useFormatter();

  const handlePlatformSelection = (platformSelected: string) => {
    setPlatform(platformSelected);
    setActiveStep(1);
  };

  return (
    <Grid container spacing={1}>
      {selectedExecutor?.executor_platforms
        && selectedExecutor?.executor_platforms.map(platform => (
          <Card
            key={platform}
            variant="outlined"
            style={{
              height: 150,
              margin: theme.spacing(2),
            }}
          >
            <CardActionArea onClick={() => handlePlatformSelection(platform)} classes={{ root: classes.area }}>
              <CardContent
                sx={{
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <PlatformIcon platform={platform} width={30} />
                <Typography
                  style={{
                    fontSize: 14,
                    padding: theme.spacing(3, 0),
                    display: 'flex',
                  }}
                >
                  <DownloadingOutlined style={{ marginRight: theme.spacing(1) }} />
                  {t('Install {platform} agent', { platform })}
                </Typography>
              </CardContent>
            </CardActionArea>
          </Card>
        ))}
    </Grid>
  );
};

export default PlatformSelector;
