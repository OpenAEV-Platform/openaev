import { useState } from 'react';

const useTabs = (initialValue: string) => {
  const [currentTab, setCurrentTab] = useState(initialValue);
  const handleChangeTab = (newValue: string) => {
    setCurrentTab(newValue);
  };

  return {
    currentTab,
    setCurrentTab,
    handleChangeTab,
  };
};

export default useTabs;
