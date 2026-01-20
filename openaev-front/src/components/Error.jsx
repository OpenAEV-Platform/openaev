import { Alert, AlertTitle } from '@mui/material';
import * as PropTypes from 'prop-types';
import { Component } from 'react';

import { sendErrorToBackend } from '../utils/Action.ts';
import { useFormatter } from './i18n';

class ErrorBoundaryComponent extends Component {
  constructor(props) {
    super(props);
    this.state = {
      error: null,
      stack: null,
    };
  }

  componentDidCatch(error, stack) {
    this.setState({
      error,
      stack,
    });
    // Send the error to the backend
    sendErrorToBackend(error, stack);
  }

  render() {
    if (this.state.stack) {
      return this.props.display;
    }
    return this.props.children;
  }
}
ErrorBoundaryComponent.propTypes = {
  display: PropTypes.object,
  children: PropTypes.node,
};
export const ErrorBoundary = ErrorBoundaryComponent;

export const SimpleError = () => {
  const { t } = useFormatter();
  return (
    <Alert severity="error">
      <AlertTitle>{t('Error')}</AlertTitle>
      {t('An unknown error occurred. Please contact your administrator or the OpenAEV maintainers.')}
    </Alert>
  );
};
