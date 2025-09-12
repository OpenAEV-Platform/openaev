import { type FunctionComponent, type ReactNode } from 'react';

import Tabs, { type TabsEntry } from '../../../../../../components/common/tabs/Tabs';
import useTabs from '../../../../../../components/common/tabs/useTabs';
import TabPanel from './TabPanel';

interface TabConfig extends TabsEntry { component: ReactNode }

interface Props { tabs: TabConfig[] }

const TabbedView: FunctionComponent<Props> = ({ tabs }) => {
  const { currentTab, handleChangeTab } = useTabs(tabs[0].key);

  return (
    <>
      <Tabs
        entries={tabs}
        currentTab={currentTab}
        onChange={newValue => handleChangeTab(newValue)}
      />
      {tabs.map((tab, index) => (
        <TabPanel key={tab.key} value={tabs.findIndex(e => e.key === currentTab)} index={index}>
          {tab.component}
        </TabPanel>
      ))}
    </>
  );
};

export default TabbedView;
