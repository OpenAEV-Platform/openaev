import { type AxiosResponse } from 'axios';
import { createContext } from 'react';

import { attackPaths, count, entities, series } from '../../../../actions/dashboards/dashboard-action';
import { type SearchOptionsConfig } from '../../../../components/common/queryable/filter/useSearchOptions';
import { type CustomDashboard, type EsAttackPath, type EsBase, type EsSeries } from '../../../../utils/api-types';
import { type WidgetDataDrawerConf } from './widgetDataDrawer/WidgetDataDrawer';

export interface ParameterOption {
  value: string;
  hidden: boolean;
  searchOptionsConfig?: SearchOptionsConfig;
}

export interface CustomDashboardContextType {
  customDashboard: CustomDashboard | undefined;
  setCustomDashboard: React.Dispatch<React.SetStateAction<CustomDashboard | undefined>>;
  customDashboardParameters: Record<string, ParameterOption>;
  setCustomDashboardParameters: React.Dispatch<React.SetStateAction<Record<string, ParameterOption>>>;
  fetchCount: (widgetId: string, params: Record<string, string | undefined>) => Promise<AxiosResponse<number>>;
  fetchSeries: (widgetId: string, params: Record<string, string | undefined>) => Promise<AxiosResponse<EsSeries[]>>;
  fetchEntities: (widgetId: string, params: Record<string, string | undefined>) => Promise<AxiosResponse<EsBase[]>>;
  fetchAttackPaths: (widgetId: string, params: Record<string, string | undefined>) => Promise<AxiosResponse<EsAttackPath[]>>;
  contextId?: string;
  canChooseDashboard?: boolean;
  handleSelectNewDashboard?: (dashboardId: string) => void;

  // handle widget data drawer
  openWidgetDataDrawer: (conf: WidgetDataDrawerConf) => void;
  closeWidgetDataDrawer: () => void;
}

export const CustomDashboardContext = createContext<CustomDashboardContextType>({
  customDashboard: undefined,
  setCustomDashboard: () => {},
  customDashboardParameters: {},
  setCustomDashboardParameters: () => {},
  fetchCount: count,
  fetchSeries: series,
  fetchEntities: entities,
  fetchAttackPaths: attackPaths,
  contextId: undefined, // Simulation or scenario id
  canChooseDashboard: false,
  handleSelectNewDashboard: undefined,

  // handle widget data drawer
  openWidgetDataDrawer: () => {},
  closeWidgetDataDrawer: () => {},
});
