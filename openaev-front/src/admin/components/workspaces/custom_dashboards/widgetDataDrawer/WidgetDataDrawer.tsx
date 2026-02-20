import { Typography } from '@mui/material';
import { useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router';

import Drawer from '../../../../../components/common/Drawer';
import { useFormatter } from '../../../../../components/i18n';
import Loader from '../../../../../components/Loader';
import {
  type EsEntities,
  type ListConfiguration, type Pagination,
  type WidgetToEntitiesInput,
} from '../../../../../utils/api-types';
import { CustomDashboardContext } from '../CustomDashboardContext';
import ListWidget from '../widgets/viz/list/ListWidget';

export type WidgetDataDrawerConf = WidgetToEntitiesInput & { widgetId: string };

const WidgetDataDrawer = () => {
  const { t } = useFormatter();

  const { customDashboard, customDashboardParameters, fetchEntitiesRuntime, closeWidgetDataDrawer } = useContext(CustomDashboardContext);
  const [searchParams] = useSearchParams();
  const widgetId = searchParams.get('widget_id');
  const seriesIndex = searchParams.get('series_index');

  const filterValues = useMemo(() => {
    return Object.fromEntries(
      searchParams.entries()
        .filter(([key]) => !['widget_id', 'series_index'].includes(key))
        .map(([key, value]) => [key, value.split(',')]),
    );
  }, [searchParams]);

  const [open, setOpen] = useState(false);
  const [paginatedEntities, setPaginatedEntities] = useState<EsEntities>();
  const [listConfig, setListConfig] = useState<ListConfiguration | null | undefined>(null);
  const [initialLoading, setInitialLoading] = useState(true); // full widget loader
  const [contentLoading, setContentLoading] = useState(false);

  const fetchEntitiesAtRuntime = useCallback(async (currentWidgetId: string, pagination?: Pagination) => {
    const params: Record<string, string> = Object.fromEntries(
      Object.entries(customDashboardParameters).map(([key, val]) => [key, val.value]),
    );
    return fetchEntitiesRuntime(currentWidgetId, {
      filter_values_map: filterValues,
      series_index: Number(seriesIndex),
      parameters: params,
      pagination,
    }).then(({ data }) => {
      setPaginatedEntities(data.es_entities);
      setListConfig(data.list_configuration);
    }).catch(() => {
      setListConfig(null);
    });
  }, [widgetId, filterValues]);

  useEffect(() => {
    if (!customDashboard || !widgetId || filterValues == null) {
      setOpen(false);
      return;
    }
    setInitialLoading(true);
    setOpen(true);
    fetchEntitiesAtRuntime(widgetId).then(() => setInitialLoading(false));
  }, [widgetId, filterValues, seriesIndex]);

  const onPaginationChange = (pagination: Pagination) => {
    if (!widgetId) {
      setOpen(false);
      return;
    }
    setContentLoading(true);
    fetchEntitiesAtRuntime(widgetId, pagination).then(() => setContentLoading(false));
  };

  return (
    <Drawer
      open={open}
      handleClose={closeWidgetDataDrawer}
      title={t('Display list')}
    >
      <>
        {initialLoading && <Loader variant="inElement" /> }
        {(!initialLoading && listConfig == null) && <Typography align="center" variant="subtitle1">{t('No data to display')}</Typography>}
        {(!initialLoading && listConfig != null && paginatedEntities != null)
          && (
            <ListWidget
              widgetConfig={listConfig}
              elements={paginatedEntities.es_datas}
              currentPageNumber={paginatedEntities.page_number}
              elementsPerPage={paginatedEntities.page_size}
              totalElements={paginatedEntities.total}
              onPaginationChange={onPaginationChange}
              contentLoading={contentLoading}
            />
          )}
      </>
    </Drawer>
  );
};

export default WidgetDataDrawer;
