import { CircularProgress } from '@mui/material';
import * as PropTypes from 'prop-types';
import { Component } from 'react';
import { withStyles } from 'tss-react/mui';

const styles = () => ({
  container: {
    width: '100vh',
    height: 'calc(100vh-180px)',
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
  loaderCircle: { display: 'inline-block' },
});

class Loader extends Component {
  render() {
    const { classes, variant, withRightPadding, size } = this.props;
    return (
      <div
        className={this.getContainer(variant, size, classes)}
        style={
          variant === 'inElement'
            ? { paddingRight: withRightPadding ? 200 : 0 }
            : {}
        }
      >
        <div
          className={
            variant === 'inElement' ? classes.loaderInElement : classes.loader
          }
          style={
            variant !== 'inElement'
              ? { paddingRight: withRightPadding ? 100 : 0 }
              : {}
          }
        >
          <CircularProgress
            size={this.getSize(variant, size)}
            thickness={1}
            className={this.props.classes.loaderCircle}
          />
        </div>
      </div>
    );
  }

  getContainer(variant, size, classes) {
    if (size === 'xs') {
      return classes.containerSizeXS;
    }
    if (variant === 'inElement') {
      return classes.containerInElement;
    }
    return classes.container;
  }

  getSize(variant, size) {
    if (size === 'xs') {
      return '1rem';
    }
    if (variant === 'inElement') {
      return 40;
    }
    return 80;
  }
}

Loader.propTypes = {
  classes: PropTypes.object.isRequired,
  variant: PropTypes.string,
  withRightPadding: PropTypes.bool,
  size: PropTypes.string,
};

export default withStyles(Loader, styles);
