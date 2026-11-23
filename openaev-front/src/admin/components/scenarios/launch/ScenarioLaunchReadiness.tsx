import { Button } from '@filigran/design-system';
import { ErrorOutlineOutlined, WarningAmberOutlined } from '@mui/icons-material';
import { type FunctionComponent, useEffect, useRef } from 'react';

import useCountLabel from '../../../../utils/hooks/useCountLabel';
import useLaunchCheck from './useLaunchCheck';

interface Props {
  scenarioId: string;
  // Opens the launch check, which lists each fix.
  onOpen: () => void;
  // Whether the launch check is open: once it closes, what was fixed there is read again.
  checkOpen?: boolean;
}

// The Hero body's control for what the launch check finds: it hides when the scenario can launch cleanly.
// What blocks the launch keeps the alert tone; what only makes injects fail warns, as the launch starts.
const ScenarioLaunchReadiness: FunctionComponent<Props> = ({ scenarioId, onOpen, checkOpen = false }) => {
  const counted = useCountLabel();
  const { launchCheck, checkAgain } = useLaunchCheck(scenarioId);
  const wasOpen = useRef(checkOpen);
  useEffect(() => {
    if (wasOpen.current && !checkOpen) checkAgain();
    wasOpen.current = checkOpen;
  }, [checkOpen]);

  const blocking = launchCheck?.blocking ?? 0;
  const failing = launchCheck?.failing ?? 0;
  if (blocking > 0) {
    return (
      <Button
        type="button"
        variant="destructive"
        priority="secondary"
        startIcon={<ErrorOutlineOutlined fontSize="small" />}
        onClick={onOpen}
        data-testid="scenario-launch-readiness"
      >
        {counted(blocking, '1 to fix before launch', '{count} to fix before launch')}
      </Button>
    );
  }
  if (failing > 0) {
    return (
      <Button
        type="button"
        priority="secondary"
        startIcon={<WarningAmberOutlined fontSize="small" />}
        onClick={onOpen}
        data-testid="scenario-launch-readiness"
      >
        {counted(failing, '1 to check before launch', '{count} to check before launch')}
      </Button>
    );
  }
  return null;
};

export default ScenarioLaunchReadiness;
