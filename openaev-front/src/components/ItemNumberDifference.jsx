import { ArrowDownwardOutlined, ArrowForwardOutlined, ArrowUpwardOutlined } from '@mui/icons-material';
import * as PropTypes from 'prop-types';
import { compose } from 'ramda';
import { Component } from 'react';
import { withStyles } from 'tss-react/mui';

import inject18n from './i18n';

const styles = theme => ({
  diff: {
    display: 'flex',
    alignItems: 'center',
    padding: theme.spacing(0, 1, 0, 1),
    fontSize: 12,
  },
  diffDescription: {
    float: 'left',
    fontSize: 9,
    color: theme.palette.text.primary,
  },
  diffIcon: {
    float: 'left',
    margin: theme.spacing(1, 1, 1, 0),
    fontSize: 13,
  },
  diffNumber: { float: 'left' },
});

const inlineStyles = {
  green: {
    backgroundColor: 'var(--color-feedback-success-secondary-transparency-30)',
    color: 'var(--color-feedback-success-primary)',
  },
  red: {
    backgroundColor: 'var(--color-feedback-error-secondary-transparency-30)',
    color: 'var(--color-feedback-error-primary)',
  },
  blueGrey: {
    backgroundColor: 'var(--color-feedback-neutral-secondary-transparency-30)',
    color: 'var(--color-feedback-neutral-primary)',
  },
};

class ItemNumberDifferenceComponent extends Component {
  render() {
    const { t, difference, classes, description } = this.props;

    if (!difference) {
      return (
        <div className={classes.diff} style={inlineStyles.blueGrey}>
          <ArrowForwardOutlined
            color="inherit"
            classes={{ root: classes.diffIcon }}
          />
          <div className={classes.diffNumber}>{difference ?? ''}</div>
          {description && (
            <div className={classes.diffDescription}>
              (
              {t(description)}
              )
            </div>
          )}
        </div>
      );
    }

    if (difference < 0) {
      return (
        <div className={classes.diff} style={inlineStyles.red}>
          <ArrowDownwardOutlined
            color="inherit"
            classes={{ root: classes.diffIcon }}
          />
          <div className={classes.diffNumber}>{difference}</div>
          {description && (
            <div className={classes.diffDescription}>
              (
              {t(description)}
              )
            </div>
          )}
        </div>
      );
    }
    return (
      <div className={classes.diff} style={inlineStyles.green}>
        <ArrowUpwardOutlined
          color="inherit"
          classes={{ root: classes.diffIcon }}
        />
        <div className={classes.diffNumber}>{difference}</div>
        {description && (
          <div className={classes.diffDescription}>
            (
            {t(description)}
            )
          </div>
        )}
      </div>
    );
  }
}

ItemNumberDifferenceComponent.propTypes = {
  classes: PropTypes.object.isRequired,
  t: PropTypes.func,
  difference: PropTypes.number,
  description: PropTypes.string.isRequired,
};

const ItemNumberDifference = compose(
  inject18n,
  Component => withStyles(Component, styles),
)(ItemNumberDifferenceComponent);

export default ItemNumberDifference;
