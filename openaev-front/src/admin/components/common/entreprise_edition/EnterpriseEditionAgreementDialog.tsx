/*
Copyright (c) 2021-2024 Filigran SAS
This file is part of the OpenAEV Enterprise Edition ("EE") and is
licensed under the OpenAEV Enterprise Edition License (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
https://github.com/OpenAEV-Platform/openaev/blob/master/LICENSE
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*/

import { Alert, Button, Link, TextField } from '@mui/material';
import { useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { updatePlatformEnterpriseEditionParameters } from '../../../../actions/Application';
import Dialog from '../../../../components/common/dialog/Dialog';
import { useFormatter } from '../../../../components/i18n';
import type { SettingsEnterpriseEditionUpdateInput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useEnterpriseEdition from '../../../../utils/hooks/useEnterpriseEdition';
import { isEmptyField } from '../../../../utils/utils';
import EEChip from './EEChip';

const useStyles = makeStyles()(theme => ({
  eeDialogContainer: {
    display: 'grid',
    gap: theme.spacing(2),
  },
}));

const EnterpriseEditionAgreementDialog = () => {
  const { t } = useFormatter();
  const { open, closeDialog, EEFeatureDetectedInfo, setEEFeatureDetectedInfo } = useEnterpriseEdition();
  const { classes } = useStyles();
  const dispatch = useAppDispatch();
  const [enterpriseLicense, setEnterpriseLicense] = useState('');

  const onCloseEnterpriseEditionDialog = () => {
    closeDialog();
    setEEFeatureDetectedInfo('');
  };

  const updateEnterpriseEdition = (data: SettingsEnterpriseEditionUpdateInput) => {
    dispatch(updatePlatformEnterpriseEditionParameters(data));
    onCloseEnterpriseEditionDialog();
  };

  const enableEnterpriseEdition = () => updateEnterpriseEdition({ platform_enterprise_license: enterpriseLicense });

  return (
    <Dialog
      open={open}
      handleClose={onCloseEnterpriseEditionDialog}
      title={t('OpenAEV Enterprise Edition (EE) license agreement')}
      actions={(
        <>
          <Button onClick={onCloseEnterpriseEditionDialog}>{t('Cancel')}</Button>
          <Button
            color="secondary"
            onClick={enableEnterpriseEdition}
            disabled={isEmptyField((enterpriseLicense))}
          >
            {t('Enable')}
          </Button>
        </>
      )}
    >
      <div className={classes.eeDialogContainer}>
        {!isEmptyField(EEFeatureDetectedInfo) && (
          <Alert style={{ alignItems: 'center' }} icon={<EEChip />} severity="success">
            {`${t('Enterprise Edition feature detected :')} `}
            {EEFeatureDetectedInfo}
          </Alert>
        )}
        <Alert severity="info">
          {t('OpenAEV Enterprise Edition requires a license key to be enabled. Filigran provides a free-to-use license for development and research purposes as well as for charity organizations.')}
          <p>
            {t('To obtain a license, please {url}', {
              url: (
                <Link
                  href="https://filigran.io/contact/"
                  target="_blank"
                  style={{ textDecoration: 'none' }}
                  rel="noreferrer"
                >
                  {t('reach out to the Filigran team')}
                </Link>
              ),
            })}
          </p>
          <p>
            {t('You just need to try ? Get right now {url}.', {
              url: (
                <Link
                  href="https://filigran.io/enterprise-editions-trial/"
                  target="_blank"
                  style={{ textDecoration: 'none' }}
                  rel="noreferrer"
                >
                  {t('your trial license online')}
                </Link>
              ),
            })}
          </p>
        </Alert>
        <div>
          <TextField
            onChange={event => setEnterpriseLicense(event.target.value)}
            multiline={true}
            fullWidth={true}
            minRows={5}
            variant="outlined"
            placeholder={t('Paste your Filigran OpenAEV Enterprise Edition license')}
          />
        </div>
        <div>
          {t('By enabling the OpenAEV Enterprise Edition, you (and your organization) agrees')}
                    &nbsp;
          <a
            href="https://github.com/OpenAEV-Platform/openaev/blob/master/LICENSE"
            target="_blank"
            rel="noreferrer"
          >
            {t('OpenAEV EE license terms and conditions of usage')}
          </a>
          .
        </div>
      </div>
    </Dialog>
  );
};

export default EnterpriseEditionAgreementDialog;
