import { Alert, AlertTitle } from '@mui/material';
import { Suspense, useEffect, useState } from 'react';
import { useParams } from 'react-router';
import { interval } from 'rxjs';

import { fetchInjectHeroOutput, fetchInjectResultOverviewOutput } from '../../../../actions/atomic_testings/atomic-testing-actions';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import { type InjectHeroOutput, type InjectResultOverviewOutput } from '../../../../utils/api-types';
import { FIVE_SECONDS } from '../../../../utils/Time';
import { TeamContext } from '../../common/Context';
import { InjectResultOverviewOutputContext } from '../InjectResultOverviewOutputContext';
import AtomicTestingHeader from './AtomicTestingHeader';
import AtomicTestingRoutes from './AtomicTestingRoutes';
import teamContextForAtomicTesting from './context/TeamContextForAtomicTesting';

const interval$ = interval(FIVE_SECONDS);

const Index = () => {
  const { t } = useFormatter();
  const { injectId } = useParams() as { injectId: InjectResultOverviewOutput['inject_id'] };

  const [pristine, setPristine] = useState(true);
  const [loading, setLoading] = useState(true);
  const [injectResultOverviewOutput, setInjectResultOverviewOutput] = useState<InjectResultOverviewOutput>();
  const [injectHeroOutput, setInjectHeroOutput] = useState<InjectHeroOutput>();

  // Keeps the hero's own data (including launched_by, which only InjectHeroOutput carries) in
  // lockstep with the overview whenever either refreshes, from any of the several call sites
  // below that already update injectResultOverviewOutput.
  const refreshInjectHero = (forInjectId: string) => {
    fetchInjectHeroOutput(forInjectId).then((result: { data: InjectHeroOutput }) => {
      setInjectHeroOutput(result.data);
    });
  };

  const applyInjectResultOverviewOutput = (data: InjectResultOverviewOutput) => {
    setInjectResultOverviewOutput(data);
    refreshInjectHero(data.inject_id);
  };

  useEffect(() => {
    setLoading(true);
    fetchInjectResultOverviewOutput(injectId).then((result: { data: InjectResultOverviewOutput }) => {
      setInjectResultOverviewOutput(result.data);
    }).finally(() => {
      setLoading(false);
      setPristine(false);
    });
    refreshInjectHero(injectId);
  }, [injectId]);

  useEffect(() => {
    const subscription = interval$.subscribe(() => {
      setLoading(true);
      fetchInjectResultOverviewOutput(injectId).then((result: { data: InjectResultOverviewOutput }) => {
        if (result.data.inject_updated_at !== injectResultOverviewOutput?.inject_updated_at) {
          setInjectResultOverviewOutput(result.data);
          refreshInjectHero(injectId);
        }
      }).catch(() => {
        subscription.unsubscribe();
      }).finally(() => {
        setLoading(false);
        setPristine(false);
      });
    });
    return () => {
      subscription.unsubscribe();
    };
  }, [injectResultOverviewOutput]);

  const updateInjectResultOverviewOutput = () => {
    fetchInjectResultOverviewOutput(injectId).then((result: { data: InjectResultOverviewOutput }) => {
      setInjectResultOverviewOutput(result.data);
    });
    refreshInjectHero(injectId);
  };

  if (pristine && loading) return <Loader />;

  if (!injectResultOverviewOutput || !injectHeroOutput) {
    return (
      <Alert severity="warning">
        <AlertTitle>{t('Warning')}</AlertTitle>
        {t('Atomic testing is currently unavailable or you do not have sufficient permissions to access it.')}
      </Alert>
    );
  }

  return (
    <TeamContext.Provider value={teamContextForAtomicTesting()}>
      <InjectResultOverviewOutputContext.Provider value={{
        injectResultOverviewOutput,
        updateInjectResultOverviewOutput,
      }}
      >
        <AtomicTestingHeader
          injectResultOverview={injectResultOverviewOutput}
          injectHero={injectHeroOutput}
          setInjectResultOverview={applyInjectResultOverviewOutput}
        />
        <Suspense fallback={<Loader />}>
          <AtomicTestingRoutes injectResultOverview={injectResultOverviewOutput} />
        </Suspense>
      </InjectResultOverviewOutputContext.Provider>
    </TeamContext.Provider>
  );
};

export default Index;
