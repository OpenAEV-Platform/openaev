import moment from 'moment-timezone';
import { Component, type ComponentType, type ReactNode } from 'react';
import { injectIntl, type IntlShape, useIntl, type WrappedComponentProps } from 'react-intl';

import { bytesFormat, numberFormat } from '../utils/number';

export const isNone = (date: moment.MomentInput) => {
  if (!date) return true;
  const parsedDate = moment(date).format();
  return (
    parsedDate.startsWith('Invalid')
    || parsedDate.startsWith('1970')
    || parsedDate.startsWith('5138')
  );
};

// @Deprecated
const inject18n = <P extends object>(WrappedComponent: ComponentType<P>) => {
  class InjectIntl extends Component<P & WrappedComponentProps & { children: ReactNode }> {
    render() {
      const { children } = this.props;
      const translate = (message: string, values?: Record<string, string>) => this.props.intl.formatMessage({ id: message }, values);
      const formatNumber = (number: number | '') => {
        if (number === null || number === '') {
          return '-';
        }
        return `${numberFormat(number).number}${
          numberFormat(number).symbol
        }`;
      };
      const formatBytes = (number: number) => `${bytesFormat(number).number}${
        bytesFormat(number).symbol
      }`;
      const longDate = (date: Parameters<IntlShape['formatDate']>[0]) => {
        if (isNone(date)) {
          return translate('None');
        }
        return this.props.intl.formatDate(date, {
          day: 'numeric',
          month: 'long',
          year: 'numeric',
        });
      };
      const longDateTime = (date: Parameters<IntlShape['formatDate']>[0]) => {
        if (isNone(date)) {
          return translate('None');
        }
        return this.props.intl.formatDate(date, {
          day: 'numeric',
          month: 'long',
          year: 'numeric',
          second: 'numeric',
          minute: 'numeric',
          hour: 'numeric',
        });
      };
      const shortDate = (date: Parameters<IntlShape['formatDate']>[0]) => {
        if (isNone(date)) {
          return translate('None');
        }
        return this.props.intl.formatDate(date, {
          day: 'numeric',
          month: 'short',
          year: 'numeric',
        });
      };
      const shortNumericDate = (date: Parameters<IntlShape['formatDate']>[0]) => {
        if (isNone(date)) {
          return translate('None');
        }
        return this.props.intl.formatDate(date, {
          day: 'numeric',
          month: 'numeric',
          year: 'numeric',
        });
      };
      const shortNumericDateTime = (date: Parameters<IntlShape['formatDate']>[0]) => {
        if (isNone(date)) {
          return translate('None');
        }
        return this.props.intl.formatDate(date, {
          second: 'numeric',
          minute: 'numeric',
          hour: 'numeric',
          day: 'numeric',
          month: 'short',
          year: 'numeric',
        });
      };
      const veryShortNumericDateTime = (date: Parameters<IntlShape['formatDate']>[0]) => {
        if (isNone(date)) {
          return translate('None');
        }
        return this.props.intl.formatDate(date, {
          minute: 'numeric',
          hour: 'numeric',
          day: 'numeric',
          month: 'numeric',
          year: 'numeric',
        });
      };
      const fullNumericDateTime = (date: Parameters<IntlShape['formatDate']>[0]) => {
        if (isNone(date)) {
          return translate('None');
        }
        return this.props.intl.formatDate(date, {
          second: 'numeric',
          minute: 'numeric',
          hour: 'numeric',
          day: 'numeric',
          month: 'numeric',
          year: 'numeric',
        });
      };
      const standardDate = (date: Parameters<IntlShape['formatDate']>[0]) => {
        if (isNone(date)) {
          return translate('None');
        }
        return this.props.intl.formatDate(date, {
          day: 'numeric',
          month: 'short',
          year: 'numeric',
        });
      };
      const monthDate = (date: Parameters<IntlShape['formatDate']>[0]) => {
        if (isNone(date)) {
          return translate('None');
        }
        return this.props.intl.formatDate(date, {
          month: 'short',
          year: 'numeric',
        });
      };
      const monthTextDate = (date: Parameters<IntlShape['formatDate']>[0]) => {
        if (isNone(date)) {
          return translate('None');
        }
        return this.props.intl.formatDate(date, { month: 'long' });
      };
      const yearDate = (date: Parameters<IntlShape['formatDate']>[0]) => {
        if (isNone(date)) {
          return translate('None');
        }
        return this.props.intl.formatDate(date, { year: 'numeric' });
      };
      return (
        <WrappedComponent
          {...this.props}
          {...{ t: translate }}
          {...{
            tPick: (labels: Record<string, string>) => labels[this.props.intl.locale]
              ?? labels[this.props.intl.defaultLocale],
          }}
          {...{ n: formatNumber }}
          {...{ b: formatBytes }}
          {...{ fld: longDate }}
          {...{ fldt: longDateTime }}
          {...{ fsd: shortDate }}
          {...{ nsd: shortNumericDate }}
          {...{ nsdt: shortNumericDateTime }}
          {...{ vnsdt: veryShortNumericDateTime }}
          {...{ fndt: fullNumericDateTime }}
          {...{ fd: standardDate }}
          {...{ md: monthDate }}
          {...{ mtd: monthTextDate }}
          {...{ yd: yearDate }}
        >
          {children}
        </WrappedComponent>
      );
    }
  }

  return injectIntl(InjectIntl);
};

export type Translate = {
  (message: string, values?: Record<string, string>): string;
  (message: string, values?: Record<string, ReactNode>): ReactNode[];
};

export const useFormatter = () => {
  const intl = useIntl();
  const translate: Translate = ((message, values) => intl.formatMessage({ id: message }, values)) as Translate;
  const formatNumber = (number: number | '') => {
    if (number === null || number === '') {
      return '-';
    }
    const t = numberFormat(number).number;
    return `${t}${
      numberFormat(number).symbol
    }`;
  };
  const formatBytes = (number: number) => `${bytesFormat(number).number}${
    bytesFormat(number).symbol
  }`;
  const longDate = (date: Parameters<IntlShape['formatDate']>[0]) => {
    if (isNone(date)) {
      return translate('None');
    }
    return intl.formatDate(date, {
      day: 'numeric',
      month: 'long',
      year: 'numeric',
    });
  };
  const longDateTime = (date: Parameters<IntlShape['formatDate']>[0]) => {
    if (isNone(date)) {
      return translate('None');
    }
    return intl.formatDate(date, {
      day: 'numeric',
      month: 'long',
      year: 'numeric',
      second: 'numeric',
      minute: 'numeric',
      hour: 'numeric',
    });
  };
  const shortDate = (date: Parameters<IntlShape['formatDate']>[0]) => {
    if (isNone(date)) {
      return translate('None');
    }
    return intl.formatDate(date, {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
    });
  };
  const shortNumericDate = (date: Parameters<IntlShape['formatDate']>[0]) => {
    if (isNone(date)) {
      return translate('None');
    }
    return intl.formatDate(date, {
      day: 'numeric',
      month: 'numeric',
      year: 'numeric',
    });
  };
  const shortNumericDateTime = (date: Parameters<IntlShape['formatDate']>[0]) => {
    if (isNone(date)) {
      return translate('None');
    }
    return intl.formatDate(date, {
      second: 'numeric',
      minute: 'numeric',
      hour: 'numeric',
      day: 'numeric',
      month: 'short',
      year: 'numeric',
    });
  };
  const veryShortNumericDateTime = (date: Parameters<IntlShape['formatDate']>[0]) => {
    if (isNone(date)) {
      return translate('None');
    }
    return intl.formatDate(date, {
      minute: 'numeric',
      hour: 'numeric',
      day: 'numeric',
      month: 'numeric',
      year: 'numeric',
    });
  };
  const fullNumericDateTime = (date: Parameters<IntlShape['formatDate']>[0]) => {
    if (isNone(date)) {
      return translate('None');
    }
    return intl.formatDate(date, {
      second: 'numeric',
      minute: 'numeric',
      hour: 'numeric',
      day: 'numeric',
      month: 'numeric',
      year: 'numeric',
    });
  };
  const time = (date: Parameters<IntlShape['formatDate']>[0]) => {
    return intl.formatTime(date, {
      second: 'numeric',
      minute: 'numeric',
      hour: 'numeric',
    });
  };
  const standardDate = (date: Parameters<IntlShape['formatDate']>[0]) => {
    if (isNone(date)) {
      return translate('None');
    }
    return intl.formatDate(date, {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
    });
  };
  const monthDate = (date: Parameters<IntlShape['formatDate']>[0]) => {
    if (isNone(date)) {
      return translate('None');
    }
    return intl.formatDate(date, {
      month: 'short',
      year: 'numeric',
    });
  };
  const monthTextDate = (date: Parameters<IntlShape['formatDate']>[0]) => {
    if (isNone(date)) {
      return translate('None');
    }
    return intl.formatDate(date, { month: 'long' });
  };
  const yearDate = (date: Parameters<IntlShape['formatDate']>[0]) => {
    if (isNone(date)) {
      return translate('None');
    }
    return intl.formatDate(date, { year: 'numeric' });
  };

  const formatDuration = (miliSeconds: number) => {
    const seconds = miliSeconds / 1000;
    const date = new Date(0);
    date.setSeconds(seconds);

    if (seconds < 60) {
      return `${Math.round(seconds)}s`;
    } else if (seconds < 3600) {
      const minutes = Math.floor(seconds / 60);
      const remainingSeconds = Math.round(seconds % 60);

      if (remainingSeconds === 0) {
        return `${minutes}min`;
      }
      return `${minutes}min ${remainingSeconds}s`;
    } else {
      const hours = Math.floor(seconds / 3600);
      const remainingMinutes = Math.floor((seconds % 3600) / 60);

      if (remainingMinutes === 0) {
        return `${hours}h`;
      }
      return `${hours}h ${remainingMinutes}min`;
    }
  };
  return {
    t: translate,
    locale: intl.locale ?? intl.defaultLocale,
    tPick: (labels?: Record<string, string>) => (labels ? labels[intl.locale] ?? labels[intl.defaultLocale] : ''),
    n: formatNumber,
    b: formatBytes,
    fld: longDate,
    fldt: longDateTime,
    fsd: shortDate,
    nsd: shortNumericDate,
    nsdt: shortNumericDateTime,
    vnsdt: veryShortNumericDateTime,
    fndt: fullNumericDateTime,
    ft: time,
    fd: standardDate,
    md: monthDate,
    mtd: monthTextDate,
    yd: yearDate,
    du: formatDuration,
  };
};

export default inject18n;
