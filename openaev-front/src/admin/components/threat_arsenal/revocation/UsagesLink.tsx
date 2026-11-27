import { Button } from '@filigran/design-system';
import { OpenInNewOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
import { Link } from 'react-router';

import { useFormatter } from '../../../../components/i18n';
import { versionsTabLink } from '../hook/useOpenedAction';

interface Props { actionId: string }

// Where the version is used, one click away (new tab, the dialog stays).
const UsagesLink: FunctionComponent<Props> = ({ actionId }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  return (
    <Button asChild priority="secondary" size="sm">
      <Link to={versionsTabLink(actionId)} target="_blank" rel="noopener">
        {t('See where')}
        <OpenInNewOutlined fontSize="small" style={{ marginLeft: theme.spacing(0.5) }} />
      </Link>
    </Button>
  );
};

export default UsagesLink;
