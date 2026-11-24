import { Button } from '@filigran/design-system';
import { ErrorOutlineOutlined, WarningAmberOutlined } from '@mui/icons-material';
import { type FunctionComponent } from 'react';

import useCountLabel from '../../../../utils/hooks/useCountLabel';
import { type LaunchCheck } from './useLaunchCheck';

interface Props {
  // The check the launch drawer shares: null while it loads.
  launchCheck: LaunchCheck | null;
  // Opens the launch check, which lists each fix.
  onOpen: () => void;
}

// The Hero body's control for what the launch check finds: it hides when the scenario can launch cleanly.
// What blocks the launch keeps the alert tone; what only makes injects fail warns, as the launch starts.
const ScenarioLaunchReadiness: FunctionComponent<Props> = ({ launchCheck, onOpen }) => {
  const counted = useCountLabel();

  const blocking = launchCheck?.blocking ?? 0;
  const failing = launchCheck?.failing ?? 0;
  // When the injects on a revoked version are all that fails, the button counts them.
  const failingInjects = launchCheck?.check.launch_check_revoked_injects.length ?? 0;
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
        {launchCheck?.onlyRevokedInjectsFail
          ? counted(failingInjects, '1 inject will fail', '{count} injects will fail')
          : counted(failing, '1 to check before launch', '{count} to check before launch')}
      </Button>
    );
  }
  return null;
};

export default ScenarioLaunchReadiness;
