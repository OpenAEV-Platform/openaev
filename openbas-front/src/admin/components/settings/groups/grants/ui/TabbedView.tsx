import { Tab, Tabs } from '@mui/material';
import { type FunctionComponent, type ReactNode, useState } from 'react';

import TabPanel from './TabPanel';

interface TabConfig {
  label: string;
  component: ReactNode;
}

interface Props { tabs: TabConfig[] }

const TabbedView: FunctionComponent<Props> = ({ tabs }) => {
  const [tabSelect, setTabSelect] = useState(0);
  return (
    <>
      <Tabs
        value={tabSelect}
        onChange={(_, i) => setTabSelect(i)}
        textColor="secondary"
        indicatorColor="secondary"
      >
        {tabs.map(tab => (
          <Tab key={tab.label} label={tab.label} />
        ))}
      </Tabs>
      {tabs.map((tab, index) => (
        <TabPanel key={tab.label} value={tabSelect} index={index}>
          {tab.component}
        </TabPanel>
      ))}
    </>
  );
};

export default TabbedView;
