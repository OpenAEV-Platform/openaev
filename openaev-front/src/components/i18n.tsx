import moment from 'moment-timezone';
import { type ComponentType, type ReactNode } from 'react';
import { type IntlShape, useIntl } from 'react-intl';

import { bytesFormat, numberFormat } from '../utils/number';

export const isNone = (date: Parameters<IntlShape['formatDate']>[0]) => {
  if (!date) return true;
  const parsedDate = moment(date as moment.MomentInput).format();
  return (
    parsedDate.startsWith('Invalid')
    || parsedDate.startsWith('1970')
    || parsedDate.startsWith('5138')
  );
};

// @Deprecated
const inject18n = <P extends object>(WrappedComponent: ComponentType<P>) => {
  const InjectIntl = (props: P & { children?: ReactNode }) => {
    const intl = useIntl();
    // formatjs throws an invariant error when the id is missing, which crashes the
    // whole render tree: degrade gracefully instead when a dynamic key is absent.
    const translate = (message: string, values?: Record<string, string>) => (message ? intl.formatMessage({ id: message }, values) : '');
    const formatNumber = (number: number | '') => {
      if (number === null || number === '') {
        return '-';
      }
      return `${numberFormat(number).number}${numberFormat(number).symbol}`;
    };
    const formatBytes = (number: number) => `${bytesFormat(number).number}${bytesFormat(number).symbol}`;
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
    return (
      <WrappedComponent
        {...props}
        t={translate}
        tPick={(labels: Record<string, string>) => labels[intl.locale]
          ?? labels[intl.defaultLocale]}
        n={formatNumber}
        b={formatBytes}
        fld={longDate}
        fldt={longDateTime}
        fsd={shortDate}
        nsd={shortNumericDate}
        nsdt={shortNumericDateTime}
        vnsdt={veryShortNumericDateTime}
        fndt={fullNumericDateTime}
        fd={standardDate}
        md={monthDate}
        mtd={monthTextDate}
        yd={yearDate}
      />
    );
  };

  return InjectIntl;
};

export type Translate = {
  (message: string, values?: Record<string, string>): string;
  (message: string, values?: Record<string, ReactNode>): ReactNode[];
};

export const useFormatter = () => {
  const intl = useIntl();
  // formatjs throws an invariant error when the id is missing, which crashes the
  // whole render tree: degrade gracefully instead when a dynamic key is absent.
  const translate: Translate = ((message, values) => (message ? intl.formatMessage({ id: message }, values) : '')) as Translate;
  const formatNumber = (number: number | '') => {
    if (number === null || number === '') {
      return '-';
    }
    const t = numberFormat(number).number;
    return `${t}${numberFormat(number).symbol}`;
  };
  const formatBytes = (number: number) => `${bytesFormat(number).number}${bytesFormat(number).symbol}`;
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
  // The tightest date+time that still carries the year (05/08/26 11:25 in fr), for space-constrained
  // spots such as a picker label sharing its line with a name. Every part is 2-digit so the width is
  // stable across values, and the year is kept 2-digit rather than dropped: a scenario accumulates
  // simulations over months, so "05/08" alone would be ambiguous across years.
  const compactNumericDateTime = (date: Parameters<IntlShape['formatDate']>[0]) => {
    if (isNone(date)) {
      return translate('None');
    }
    return intl.formatDate(date, {
      minute: '2-digit',
      hour: '2-digit',
      day: '2-digit',
      month: '2-digit',
      year: '2-digit',
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
    cnsdt: compactNumericDateTime,
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
