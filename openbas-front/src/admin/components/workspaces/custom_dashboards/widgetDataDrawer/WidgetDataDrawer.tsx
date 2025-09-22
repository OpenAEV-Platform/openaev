import { Typography } from '@mui/material';
import { useContext, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router';

import { widgetToEntitiesRuntime } from '../../../../../actions/dashboards/dashboard-action';
import Drawer from '../../../../../components/common/Drawer';
import { useFormatter } from '../../../../../components/i18n';
import Loader from '../../../../../components/Loader';
import { type EsBase, type ListConfiguration, type WidgetToEntitiesInput } from '../../../../../utils/api-types';
import { CustomDashboardContext } from '../CustomDashboardContext';
import ListWidget from '../widgets/viz/list/ListWidget';

export type WidgetDataDrawerConf = WidgetToEntitiesInput & { widgetId: string };

const WidgetDataDrawer = () => {
  const { t } = useFormatter();

  const { customDashboard, customDashboardParameters, closeWidgetDataDrawer } = useContext(CustomDashboardContext);
  const [searchParams] = useSearchParams();
  const widgetId = searchParams.get('widget_id');
  const seriesIndex = searchParams.get('series_index');
  const filterValues = searchParams.get('filter_values');

  const [open, setOpen] = useState(false);
  const [listDatas, setListDatas] = useState<EsBase[]>([]);
  const [listConfig, setListConfig] = useState<ListConfiguration | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!customDashboard || !widgetId || filterValues == null || !seriesIndex) {
      setOpen(false);
      return;
    }
    setLoading(true);
    setOpen(true);

    const params: Record<string, string> = Object.fromEntries(
      Object.entries(customDashboardParameters).map(([key, val]) => [key, val.value]),
    );
    widgetToEntitiesRuntime(widgetId, {
      filter_values: filterValues.split(','),
      series_index: Number(seriesIndex),
      parameters: params,
    }).then(({ data }) => {
      setListDatas(data.es_entities);
      setListConfig(data.list_configuration);
      setLoading(false);
    }).catch(() => {
      setListConfig(null);
      setLoading(false);
    });
  }, [widgetId, filterValues, seriesIndex]);

  return (
    <Drawer
      open={open}
      handleClose={closeWidgetDataDrawer}
      title={t('Display list')}
    >
      <>
        {loading && <Loader variant="inElement" /> }
        {(!loading && listConfig == null) && <Typography align="center" variant="subtitle1">{t('No data to display')}</Typography>}
        {(!loading && listConfig != null) && <ListWidget widgetConfig={listConfig} elements={listDatas} />}
      </>
    </Drawer>
  );
};

export default WidgetDataDrawer;
