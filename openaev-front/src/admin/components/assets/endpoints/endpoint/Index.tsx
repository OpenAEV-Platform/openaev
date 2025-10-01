import { Alert, AlertTitle } from '@mui/material';
import { lazy, useState } from 'react';
import { Route, Routes, useParams } from 'react-router';

import { type EndpointHelper } from '../../../../../actions/assets/asset-helper';
import { fetchEndpoint } from '../../../../../actions/assets/endpoint-actions';
import Breadcrumbs from '../../../../../components/Breadcrumbs';
import { errorWrapper } from '../../../../../components/Error';
import { useFormatter } from '../../../../../components/i18n';
import Loader from '../../../../../components/Loader';
import NotFound from '../../../../../components/NotFound';
import { useHelper } from '../../../../../store';
import { type EndpointOverviewOutput as EndpointType } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import EndpointHeader from './EndpointHeader';

const Endpoint = lazy(() => import('./Endpoint'));

const Index = () => {
  const dispatch = useAppDispatch();
  const [pristine, setPristine] = useState(true);
  const [loading, setLoading] = useState(true);
  const { t } = useFormatter();
  const { endpointId } = useParams() as { endpointId: EndpointType['asset_id'] };

  // Fetching data
  const { endpoint } = useHelper((helper: EndpointHelper) => ({ endpoint: helper.getEndpoint(endpointId) }));
  useDataLoader(() => {
    setLoading(true);
    dispatch(fetchEndpoint(endpointId)).finally(() => {
      setPristine(false);
      setLoading(false);
    });
  });

  // avoid to show loader if something trigger useDataLoader
  if (pristine && loading) {
    return <Loader />;
  }
  if (!loading && !endpoint) {
    return (
      <Alert severity="warning">
        <AlertTitle>{t('Warning')}</AlertTitle>
        {t('Endpoint is currently unavailable or you do not have sufficient permissions to access it.')}
      </Alert>
    );
  }
  return (
    <div>
      <Breadcrumbs
        variant="object"
        elements={[
          { label: t('Assets') },
          {
            label: t('Endpoints'),
            link: '/admin/assets/endpoints',
          },
          {
            label: endpoint.asset_name,
            current: true,
          },
        ]}
      />
      <EndpointHeader />
      <div className="clearfix" />
      <Routes>
        <Route path="" element={errorWrapper(Endpoint)()} />
        {/* Not found */}
        <Route path="*" element={<NotFound />} />
      </Routes>
    </div>
  );
};

export default Index;
