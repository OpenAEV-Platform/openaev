import { Alert, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect } from 'react';

import { useFormatter } from '../../../components/i18n';
import { type ExecutorOutput } from '../../../utils/api-types';
import useEnterpriseEdition from '../../../utils/hooks/useEnterpriseEdition';
import EEChip from '../common/entreprise_edition/EEChip';

interface Props {
  executor: ExecutorOutput;
  showEEChip?: boolean;
}

const ExecutorDocumentationLink: FunctionComponent<Props> = ({ executor, showEEChip }) => {
  // Standard hooks
  const { t } = useFormatter();
  const theme = useTheme();
  const { openDialog, setEEFeatureDetectedInfo } = useEnterpriseEdition();

  useEffect(() => {
  }, []);
  const onAlertClick = () => {
    setEEFeatureDetectedInfo(executor.executor_name);
    openDialog();
  };
  return (
    <div style={{
      display: 'grid',
      gap: theme.spacing(2),
    }}
    >
      {showEEChip && (
        <Alert
          style={{ cursor: 'pointer' }}
          icon={<EEChip style={{ marginTop: theme.spacing(1) }} />}
          severity="success"
          onClick={onAlertClick}
        >
          {`${executor.executor_name} ${t('executor is an enterprise edition feature. You can start the set up but you will need a license key to execute your injects. We provide a 3 month trial to let you test the platform at full capacity.')} `}
        </Alert>
      )}
      {executor.executor_doc && (
        <Typography variant="body1">
          {t('To install the agent please follow the ')}
          <a target="_blank" href={executor.executor_doc} rel="noreferrer">
            {t('{executor_name} documentation', { executor_name: executor.executor_name })}
          </a>
          .
        </Typography>
      )}
      {!executor.executor_doc && (
        <Typography variant="body1">
          {t('No documentation available')}
        </Typography>
      )}
    </div>
  );
};

export default ExecutorDocumentationLink;
