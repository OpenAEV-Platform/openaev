/*
Copyright (c) 2021-2024 Filigran SAS

This file is part of the OpenAEV Enterprise Edition ("EE") and is
licensed under the OpenAEV Enterprise Edition License (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

https://github.com/OpenAEV-Platform/openaev/blob/main/LICENSE

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*/

import { RocketLaunchOutlined } from '@mui/icons-material';
import { Button } from '@mui/material';
import { useTheme } from '@mui/material/styles';

import { useFormatter } from '../../../../components/i18n';
import useEnterpriseEdition from '../../../../utils/hooks/useEnterpriseEdition';

interface Props {
  style?: React.CSSProperties;
  classes?: { [key: string]: string };
}

const EnterpriseEditionButton = ({ style = {}, classes }: Props) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { openDialog } = useEnterpriseEdition();
  return (
    <Button
      size="small"
      variant="text"
      color="ee"
      onClick={() => openDialog()}
      startIcon={<RocketLaunchOutlined />}
      style={{
        textTransform: 'none',
        fontWeight: theme.typography.fontWeightBold,
        ...style,
      }}
      classes={classes}
    >
      {t('Manage your enterprise edition license')}
    </Button>
  );
};

export default EnterpriseEditionButton;
