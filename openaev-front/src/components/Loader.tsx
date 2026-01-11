import './Theme'; // Import for Theme augmentation

import { type Theme, useTheme } from '@mui/material/styles';
import { FiligranLoader } from 'filigran-icon';
import { type CSSProperties, type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

const useStyles = makeStyles()(() => ({
  container: {
    width: '100%',
    height: 'calc(100vh - 180px)',
    padding: '0 0 0 180px',
  },
  containerInElement: {
    width: '100%',
    height: '100%',
    display: 'table',
  },
  containerSizeXS: { width: 'auto' },
  loader: {
    width: '100%',
    margin: 0,
    padding: 0,
    position: 'absolute',
    top: '46%',
    left: 0,
    textAlign: 'center',
    zIndex: 20,
  },
  loaderInElement: {
    width: '100%',
    margin: 0,
    padding: 0,
    display: 'table-cell',
    verticalAlign: 'middle',
    textAlign: 'center',
  },
}));

type LoaderVariant = 'inElement' | 'default';
type LoaderSize = 'xs' | 'sm' | 'md' | 'lg';

interface LoaderProps {
  variant?: LoaderVariant;
  withRightPadding?: boolean;
  size?: LoaderSize;
}

const Loader: FunctionComponent<LoaderProps> = ({
  variant = 'default',
  withRightPadding = false,
  size,
}) => {
  const { classes } = useStyles();
  const theme = useTheme<Theme>();

  const getContainer = (): string => {
    if (size === 'xs') {
      return classes.containerSizeXS;
    }
    if (variant === 'inElement') {
      return classes.containerInElement;
    }
    return classes.container;
  };

  const getSize = (): number => {
    if (size === 'xs') {
      return 16;
    }
    if (variant === 'inElement') {
      return 40;
    }
    return 80;
  };

  const containerStyle: CSSProperties = variant === 'inElement'
    ? { paddingRight: withRightPadding ? 200 : 0 }
    : {};

  const loaderStyle: CSSProperties = variant !== 'inElement'
    ? { paddingRight: withRightPadding ? 100 : 0 }
    : {};

  return (
    <div className={getContainer()} style={containerStyle}>
      <div
        className={variant === 'inElement' ? classes.loaderInElement : classes.loader}
        style={loaderStyle}
      >
        <FiligranLoader height={getSize()} color={theme?.palette?.common?.grey} />
      </div>
    </div>
  );
};

export default Loader;
