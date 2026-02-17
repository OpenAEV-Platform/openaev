import { type FunctionComponent, type ReactNode, useEffect } from 'react';

import Tabs, { type TabsEntry } from '../../../../../../components/common/tabs/Tabs';
import useTabs from '../../../../../../components/common/tabs/useTabs';
import TabPanel from './TabPanel';

export interface TabConfig extends TabsEntry { component: ReactNode }

interface Props {
  tabs: TabConfig[];
  externalCurrentTab?: string | null;
  notifyTabChange?: (key: string) => void;
}

const TabbedView: FunctionComponent<Props> = ({ tabs, externalCurrentTab, notifyTabChange }) => {
  const { currentTab, handleChangeTab } = useTabs(tabs[0]?.key);

  useEffect(() => {
    if (externalCurrentTab && externalCurrentTab !== currentTab) {
      handleChangeTab(externalCurrentTab);
    }
  }, [externalCurrentTab]);

  const handleChange = (newKey: string) => {
    handleChangeTab(newKey);
    notifyTabChange?.(newKey);
  };

  return (
    <>
      <Tabs
        entries={tabs}
        currentTab={currentTab}
        onChange={handleChange}
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
