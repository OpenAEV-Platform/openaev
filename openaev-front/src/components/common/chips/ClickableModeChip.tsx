import { type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../i18n';

const useStyles = makeStyles()(theme => ({
  mode: {
    borderRadius: 4,
    fontFamily: 'Consolas, monaco, monospace',
    backgroundColor: theme.palette.action?.selected,
    padding: '0 8px',
    display: 'flex',
    alignItems: 'center',
  },
  hasClickEvent: {
    'cursor': 'pointer',
    '&:hover': {
      backgroundColor: theme.palette.action?.disabled,
      textDecorationLine: 'underline',
    },
  },
}));

interface Props {
  onClick?: () => void;
  mode?: string;
}

const ClickableModeChip: FunctionComponent<Props> = ({
  onClick,
  mode,
}) => {
  // Standard hooks
  const { classes, cx } = useStyles();
  const { t } = useFormatter();

  if (!mode) {
    return <></>;
  }

  return (
    (
      <div
        onClick={onClick}
        className={cx({
          [classes.mode]: true,
          [classes.hasClickEvent]: !!onClick,
        })}
      >
        {t(mode.toUpperCase())}
      </div>
    )
  );
};

export default ClickableModeChip;
