import { useTheme } from '@mui/material/styles';
import { useEffect } from 'react';
import { useLocalStorage } from 'usehooks-ts';

import GettingStartedFAQ from './GettingStartedFAQ';
import GettingStartedScenarios from './GettingStartedScenarios';
import GettingStartedSummary from './GettingStartedSummary';

export const GETTING_STARTED_LOCAL_STORAGE_KEY = 'go-to-getting-started';
const GettingStartedPage = () => {
  const theme = useTheme();

  const [_, setGoToGettingStarted] = useLocalStorage<boolean>(GETTING_STARTED_LOCAL_STORAGE_KEY, true);
  useEffect(() => {
    setGoToGettingStarted(false);
  }, [setGoToGettingStarted]);

  return (
    <div style={{
      display: 'grid',
      gap: theme.spacing(3),
    }}
    >
      <GettingStartedSummary />
      <GettingStartedScenarios />
      <GettingStartedFAQ />
    </div>
  );
};

export default GettingStartedPage;
