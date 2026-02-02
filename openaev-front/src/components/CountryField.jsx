import { FlagOutlined } from '@mui/icons-material';
import { Box } from '@mui/material';
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
  autoCompleteIndicator: { display: 'none' },
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
          renderOption={(props, option) => (
            <Box component="li" {...props} key={option.id}>
              <div className={classes.icon}>
                <FlagOutlined />
              </div>
              <div className={classes.text}>{option.label}</div>
            </Box>
          )}
          classes={{ clearIndicator: classes.autoCompleteIndicator }}
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
