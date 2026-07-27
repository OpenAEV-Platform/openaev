import { type AxiosResponse } from 'axios';
import { createContext } from 'react';

import {
  attackPaths, average,
  count,
  entities,
  series,
  widgetToEntitiesRuntime,
} from '../../../../actions/dashboards/dashboard-action';
import { type SearchOptionsConfig } from '../../../../components/common/queryable/filter/useSearchOptions';
import {
  type CustomDashboard,
  type EsAttackPath, type EsAvgs,
  type EsCountInterval, type EsEntities,
  type EsSeries, type Pagination,
  type WidgetToEntitiesInput, type WidgetToEntitiesOutput,
} from '../../../../utils/api-types';
// A widget element click: the clicked widget plus the clicked scope
// (field values + series). Consumed by the full-page results explorer.
export type WidgetResultsConf = WidgetToEntitiesInput & { widgetId: string };

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
  fetchAverage: (widgetId: string, params: Record<string, string | undefined>) => Promise<AxiosResponse<EsAvgs>>;
  fetchCount: (widgetId: string, params: Record<string, string | undefined>) => Promise<AxiosResponse<EsCountInterval>>;
  fetchSeries: (widgetId: string, params: Record<string, string | undefined>) => Promise<AxiosResponse<EsSeries[]>>;
  fetchEntities: (widgetId: string, params: Record<string, string | undefined>, pagination?: Pagination) => Promise<AxiosResponse<EsEntities>>;
  fetchEntitiesRuntime: (widgetId: string, input: WidgetToEntitiesInput) => Promise<AxiosResponse<WidgetToEntitiesOutput>>;
  fetchAttackPaths: (widgetId: string, params: Record<string, string | undefined>) => Promise<AxiosResponse<EsAttackPath[]>>;
  contextId?: string;
  canChooseDashboard?: boolean;
  handleSelectNewDashboard?: (dashboardId: string) => void;

  // Widget element clicks land on the full-page results explorer.
  openWidgetResults: (conf: WidgetResultsConf) => void;

  // Grid ready state for loader coordination
  setGridReady: (ready: boolean) => void;
}

export const CustomDashboardContext = createContext<CustomDashboardContextType>({
  customDashboard: undefined,
  setCustomDashboard: () => {},
  customDashboardParameters: {},
  setCustomDashboardParameters: () => {},
  fetchAverage: average,
  fetchCount: count,
  fetchSeries: series,
  fetchEntities: entities,
  fetchEntitiesRuntime: widgetToEntitiesRuntime,
  fetchAttackPaths: attackPaths,
  contextId: undefined, // Simulation or scenario id
  canChooseDashboard: false,
  handleSelectNewDashboard: undefined,

  // Widget element clicks land on the full-page results explorer.
  openWidgetResults: () => {},

  // Grid ready state for loader coordination
  setGridReady: () => {},
});
