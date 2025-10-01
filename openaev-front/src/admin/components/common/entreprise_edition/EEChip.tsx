import { Tooltip } from '@mui/material';
import { type CSSProperties } from 'react';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../components/i18n';
import useEnterpriseEdition from '../../../../utils/hooks/useEnterpriseEdition';

const useStyles = makeStyles<{ isClickable: boolean }>()((theme, { isClickable }) => ({
  container: {
    fontSize: 'xx-small',
    height: 14,
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
    width: 21,
    borderRadius: theme.borderRadius,
    border: `1px solid ${theme.palette.ee.main}`,
    color: theme.palette.ee.main,
    backgroundColor: theme.palette.ee.background,
    cursor: isClickable ? 'pointer' : 'default',
  },
}));

const EEChip = ({ clickable = false, featureDetectedInfo = null, style = {} }: {
  clickable?: boolean;
  featureDetectedInfo?: string | null;
  style?: CSSProperties;
}) => {
  const { classes } = useStyles({ isClickable: clickable });
  const { t } = useFormatter();
  const { isValidated: isEnterpriseEdition, openDialog, setEEFeatureDetectedInfo } = useEnterpriseEdition();
  if (featureDetectedInfo) {
    setEEFeatureDetectedInfo(featureDetectedInfo);
  }

  return (
    <Tooltip
      title={t('Enterprise Edition Feature')}
      className={classes.container}
      onClick={() => clickable && !isEnterpriseEdition && openDialog()}
      style={style}
    >
      <span>
        EE
      </span>
    </Tooltip>
  );
};

export default EEChip;
