import { Paper } from '@filigran/design-system';
import { type SvgIconProps } from '@mui/material';
import { cloneElement, type FunctionComponent, type ReactElement } from 'react';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../components/i18n';
import ItemNumberDifference from '../../../../components/ItemNumberDifference';

const useStyles = makeStyles()(theme => ({
  title: {
    textTransform: 'uppercase',
    fontSize: theme.typography.h4.fontSize,
    fontWeight: theme.typography.h4.fontWeight,
    color: theme.palette.text?.secondary,
  },
  subtitle: {
    fontSize: theme.typography.h4.fontSize,
    fontWeight: theme.typography.h4.fontWeight,
    color: theme.palette.text?.secondary,
  },
  number: {
    fontSize: 40,
    fontWeight: 800,
    float: 'left',
  },
}));

interface Props {
  title: string;
  subTitle: string;
  icon: ReactElement<SvgIconProps>;
  number?: number;
  progression?: number;
}

const PaperMetric: FunctionComponent<Props> = ({
  title,
  subTitle,
  icon,
  number,
  progression,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const { classes } = useStyles();
  const component = cloneElement(icon as ReactElement<SvgIconProps>, {
    color: 'primary',
    style: {
      fontSize: 35,
      marginTop: 15,
    },
  });
  return (
    <Paper
      padding={16}
      style={{
        display: 'flex',
        flexDirection: 'row',
        justifyContent: 'space-between',
      }}
    >
      <div>
        <div>
          <span className={classes.title}>{t(title)}</span>
          <span className={classes.subtitle}>
            &nbsp;
            {t(subTitle)}
          </span>
        </div>
        <div className={classes.number}>
          {number ?? '-'}
        </div>
        <ItemNumberDifference
          difference={progression}
          description={t('1 month')}
        />
      </div>
      <div>
        {component}
      </div>
    </Paper>
  );
};
export default PaperMetric;
