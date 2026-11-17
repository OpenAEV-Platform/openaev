import { Box } from '@mui/material';
import { useLocation, useSearchParams } from 'react-router';

import { type UserHelper } from '../../../actions/helper';
import { useHelper } from '../../../store';
import ThreatArsenalAvailable from './catalog/ThreatArsenalAvailable';
import { useCustomViews, VIEW_PARAM } from './customViews';
import ThreatArsenalInstalled from './ThreatArsenalInstalled';
import ThreatArsenalTabs from './ThreatArsenalTabs';

const ThreatArsenal = () => {
  const isAvailableTab = useLocation().pathname.endsWith('/available');
  const [searchParams] = useSearchParams();
  const me = useHelper((helper: UserHelper) => helper.getMe())?.user_id as string | undefined;
  const views = useCustomViews(me);
  // A custom view is the Active list with its own saved filters (an unknown id opens Active).
  const openView = isAvailableTab ? undefined : views.byId(searchParams.get(VIEW_PARAM) ?? '');

  const tabs = <ThreatArsenalTabs active={isAvailableTab ? 'available' : 'installed'} />;

  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: 3,
    }}
    >

      {isAvailableTab
        ? <ThreatArsenalAvailable tabs={tabs} />
        : (
            <ThreatArsenalInstalled
              // Another view, or saved filters: the list starts again from them.
              key={openView ? `${openView.id}:${JSON.stringify(openView.filters)}` : 'active'}
              tabs={tabs}
              view={openView}
            />
          )}
    </Box>
  );
};

export default ThreatArsenal;
