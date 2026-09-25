import { Tabs, TabsList, TabsTrigger } from '@filigran/design-system';
import { type FunctionComponent, type ReactNode } from 'react';

export interface TabsEntry {
  key: string;
  label: ReactNode;
}

// The caller renders the active panel next to the bar, so the tab set declares its panels external.
const EntriesTabs: FunctionComponent<{
  entries: TabsEntry[];
  currentTab: string;
  onChange: (newValue: string) => void;
}> = ({ entries = [], currentTab, onChange }) => (
  <Tabs value={currentTab} onValueChange={onChange} panels="external">
    <TabsList>
      {entries.map(entry => (
        <TabsTrigger key={entry.key} value={entry.key}>{entry.label}</TabsTrigger>
      ))}
    </TabsList>
  </Tabs>
);

export default EntriesTabs;
