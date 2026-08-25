import { FlagOutlined } from '@mui/icons-material';
import * as R from 'ramda';
import { Component } from 'react';
import { withStyles } from 'tss-react/mui';

import { countryOptions } from '../utils/Option';
import Autocomplete from './Autocomplete';
import inject18n from './i18n';

const styles = () => ({
  icon: {
    paddingTop: 4,
    display: 'inline-block',
  },
  text: {
    display: 'inline-block',
    flexGrow: 1,
    marginLeft: 10,
  },
});

class CountryFieldComponent extends Component {
  render() {
    const { t, name, classes } = this.props;
    return (
      <div>
        <Autocomplete
          variant="standard"
          size="small"
          name={name}
          fullWidth={true}
          multiple={false}
          label={t('Country')}
          options={countryOptions()}
          style={{ marginTop: 20 }}
          // Country is optional: the clear icon is the only way to empty it.
          disableClearable={false}
          renderOption={option => (
            <>
              <div className={classes.icon}>
                <FlagOutlined />
              </div>
              <div className={classes.text}>{option.label}</div>
            </>
          )}
        />
      </div>
    );
  }
}

/**
 * @deprecated The component uses old form library react-final-form
 */
const CountryField = R.compose(
  inject18n,
  Component => withStyles(Component, styles),
)(CountryFieldComponent);

export default CountryField;
