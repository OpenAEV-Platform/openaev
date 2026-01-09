import { type LocalHourMinute, PeriodExpressionHandler } from './PeriodExpressionHandler';

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

  toTranslatableStringArray(_locale: string): string[] {
    let prefix: string;
    let suffix: string;
    const amount = Number(this.getRecurrenceAmount() || '1');
    const num_accord = Math.abs(amount) === 1 ? 'singular' : 'plural';
    switch (this.getRecurrenceMagnitude()) {
      case 'hourly':
        prefix = 'every_fem_' + num_accord;
        suffix = 'hours';
        break;
      case 'weekly':
        prefix = 'every_fem_' + num_accord;
        suffix = 'weeks';
        break;
      case 'monthly':
        prefix = 'every_masc_' + num_accord;
        suffix = 'months';
        break;
      default: // let's say daily is default
        prefix = 'every_masc_' + num_accord;
        suffix = 'days';
        break;
    }
    return num_accord == 'plural' ? [prefix, amount.toString(), suffix + '_' + num_accord] : [prefix, suffix + '_' + num_accord];
  }

  getRecurrenceMagnitude(): string {
    switch (new RegExp(ISO8601PeriodMask).exec(this.rawExpression)?.[2]) {
      case 'H': return 'hourly';
      case 'W': return 'weekly';
      case 'M': return 'monthly';
      default: return 'daily';
    }
  }

  getRecurrenceAmount(): string | undefined {
    return new RegExp(ISO8601PeriodMask).exec(this.rawExpression)?.[1];
  }

  getRecurrenceTime(): LocalHourMinute {
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
