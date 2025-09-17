import { useTheme } from '@mui/material/styles';

import GettingStartedFAQ from './GettingStartedFAQ';
import GettingStartedScenarios from './GettingStartedScenarios';
import GettingStartedSummary from './GettingStartedSummary';

const GettingStartedPage = () => {
  const theme = useTheme();

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
