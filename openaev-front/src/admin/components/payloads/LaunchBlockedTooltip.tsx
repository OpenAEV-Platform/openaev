import { Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { type FunctionComponent, type ReactNode } from 'react';

import { useFormatter } from '../../../components/i18n';
import { type LaunchBlockerOutput } from '../../../utils/api-types';
import { launchBlockedLabel } from './payloadApprovalDisplay';

interface Props {
  blockers?: LaunchBlockerOutput[];
  /** The launch button, already disabled by the caller when the launch is blocked. */
  children: ReactNode;
}

/**
 * Explains a launch button disabled because an action is not approved ("Can't launch: TEST-A is
 * pending approval"). A disabled button fires no pointer event, so the tooltip hangs on a span
 * around it. Renders the button alone when nothing blocks the launch.
 */
const LaunchBlockedTooltip: FunctionComponent<Props> = ({ blockers, children }) => {
  const { t } = useFormatter();
  const label = launchBlockedLabel(t, blockers);
  if (!label) {
    return <>{children}</>;
  }
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <span style={{ display: 'inline-flex' }} aria-label={label} tabIndex={0}>{children}</span>
      </TooltipTrigger>
      <TooltipContent>{label}</TooltipContent>
    </Tooltip>
  );
};

export default LaunchBlockedTooltip;
