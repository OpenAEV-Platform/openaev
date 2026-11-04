import { Link } from 'react-router';

import { MESSAGING$ } from '../../../../utils/Environment';

export const INSTALLED_PATH = '/admin/threat-arsenal';

// The Installed tab shows this payload alone, its drawer open.
export const installedState = (name: string) => ({ openName: name });

const notifyPayloadInstalled = (message: string, name: string, openLabel: string) => MESSAGING$.notifySuccess(
  <span>
    {`${message} `}
    <Link to={INSTALLED_PATH} state={installedState(name)}>{openLabel}</Link>
  </span>,
);

export default notifyPayloadInstalled;
