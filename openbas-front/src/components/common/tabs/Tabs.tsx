import { Box, Tab, Tabs as MUITabs } from '@mui/material';
import { type FunctionComponent, type ReactNode, useCallback } from 'react';

export interface TabsEntry {
  key: string;
  label: ReactNode;
}

const Tabs: FunctionComponent<{
  entries: TabsEntry[];
  currentTab: string;
  onChange: (newValue: string) => void;
}> = ({ entries = [], currentTab, onChange }) => {
  const handleChange = useCallback((_e: unknown, newValue: string) => {
    onChange(newValue);
  }, [onChange]);

  return (
    <Box sx={{
      borderBottom: 1,
      borderColor: 'divider',
    }}
    >
      <MUITabs value={currentTab} onChange={handleChange}>
        {entries.map(entry => (
          <Tab key={entry.key} value={entry.key} label={entry.label} />
        ))}
      </MUITabs>
    </Box>
  );
};

export default Tabs;
