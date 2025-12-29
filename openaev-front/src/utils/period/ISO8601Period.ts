import { PeriodExpressionHandler, type UTCHourMinute } from './PeriodExpressionHandler';

const ISO8601PeriodMask: string = 'PT?(\\d+)([HDWM])';

class ISO8601Period extends PeriodExpressionHandler {
  constructor(expression: string) {
    super(expression);
  }

  isUiSupported(): boolean {
    return false; // ISO periods are not supported via the UI
  }

  isValid(): boolean {
    return new RegExp(ISO8601PeriodMask).test(this.rawExpression);
  }

  toHumanReadableString(_locale: string): string {
    return this.rawExpression;
  }

  getRecurrenceMagnitude(): string {
    switch (new RegExp(ISO8601PeriodMask).exec(this.rawExpression)?.groups?.[2]) {
      case 'H': return 'hourly';
      case 'W': return 'weekly';
      case 'M': return 'monthly';
      default: return 'daily';
    }
  }

  getRecurrenceTime(): UTCHourMinute {
    return {
      hour: 0,
      minute: 0,
    };
  }

  static canHandleExpression(expression: string) {
    return new RegExp(ISO8601PeriodMask).test(expression);
  }
}

export default ISO8601Period;
