import { Tabs, TabsList, TabsTrigger } from '@filigran/design-system';
import { type FunctionComponent } from 'react';
import { Link } from 'react-router';

export interface MarketplaceTab {
  value: string;
  label: string;
  to: string;
}

interface Props {
  tabs: MarketplaceTab[];
  active: string;
}

// Routed tabs of a marketplace page (installed content, catalogue).
const MarketplaceTabs: FunctionComponent<Props> = ({ tabs, active }) => (
  <Tabs value={active} panels="external">
    <TabsList>
      {tabs.map(tab => (
        <TabsTrigger key={tab.value} value={tab.value} asChild>
          <Link to={tab.to} aria-current={active === tab.value ? 'page' : undefined}>{tab.label}</Link>
        </TabsTrigger>
      ))}
    </TabsList>
  </Tabs>
);

export default MarketplaceTabs;
