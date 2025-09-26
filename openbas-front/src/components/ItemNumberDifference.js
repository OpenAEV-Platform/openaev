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
    padding: '2px 5px',
    fontSize: 12,
  },
  diffDescription: {
    margin: '2px 0 0 10px',
    float: 'left',
    fontSize: 9,
    color: theme.palette.text.primary,
  },
  diffIcon: {
    float: 'left',
    margin: '1px 5px 0 0',
    fontSize: 13,
  },
  diffNumber: { float: 'left' },
});

const inlineStyles = {
  green: {
    backgroundColor: 'rgba(76, 175, 80, 0.08)',
    color: '#4caf50',
  },
  red: {
    backgroundColor: 'rgba(244, 67, 54, 0.08)',
    color: '#f44336',
  },
  blueGrey: {
    backgroundColor: 'rgba(96, 125, 139, 0.08)',
    color: '#607d8b',
  },
};

class ItemNumberDifference extends Component {
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

ItemNumberDifference.propTypes = {
  classes: PropTypes.object.isRequired,
  t: PropTypes.func,
  difference: PropTypes.number,
  description: PropTypes.string.isRequired,
};

export default compose(inject18n, Component => withStyles(Component, styles))(ItemNumberDifference);
