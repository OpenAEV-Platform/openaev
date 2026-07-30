import { Box } from '@mui/material';
import { useEffect } from 'react';
import { useLocalStorage } from 'usehooks-ts';

import GettingStartedFAQ from './GettingStartedFAQ';
import GettingStartedHero from './GettingStartedHero';
import GettingStartedResources from './GettingStartedResources';
import GettingStartedScenarios from './GettingStartedScenarios';

export const GETTING_STARTED_LOCAL_STORAGE_KEY = 'go-to-getting-started';

const GettingStartedPage = () => {
  const [_, setGoToGettingStarted] = useLocalStorage<boolean>(GETTING_STARTED_LOCAL_STORAGE_KEY, true);
  useEffect(() => {
    setGoToGettingStarted(false);
  }, [setGoToGettingStarted]);

  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: 4,
    }}
    >
      <GettingStartedHero />
      <GettingStartedScenarios />
      <GettingStartedFAQ />
      <GettingStartedResources />
    </Box>
  );
};

export default GettingStartedPage;
