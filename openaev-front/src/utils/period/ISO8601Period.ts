import { type LocalHourMinute, PeriodExpressionHandler } from './PeriodExpressionHandler';

// Compile RegExp once for better performance
// Matches ISO 8601 duration formats: P[n]D, PT[n]H, PT[n]M, P[n]W, P[n]M (months before T)
const ISO8601PeriodRegex = /^P(?:(\d+)([DWM])|T(\d+)([HM]))$/;

class ISO8601Period extends PeriodExpressionHandler {
  constructor(expression: string) {
    super(expression);
  }

  isUiSupported(): boolean {
    return false; // ISO periods are not supported via the UI
  }

  isValid(): boolean {
    return ISO8601PeriodRegex.test(this.rawExpression);
  }

  toHumanReadableString(_locale: string): string {
    return this.rawExpression;
  }

  toTranslatableStringArray(_locale: string): string[] {
    let prefix: string;
    let suffix: string;
    const amount = Number(this.getRecurrenceAmount() || '1');
    const numAccord = Math.abs(amount) === 1 ? 'singular' : 'plural';
    switch (this.getRecurrenceMagnitude()) {
      case 'minutely':
        prefix = `every_fem_${numAccord}`;
        suffix = 'minutes';
        break;
      case 'hourly':
        prefix = `every_fem_${numAccord}`;
        suffix = 'hours';
        break;
      case 'weekly':
        prefix = `every_fem_${numAccord}`;
        suffix = 'weeks';
        break;
      case 'monthly':
        prefix = `every_masc_${numAccord}`;
        suffix = 'months';
        break;
      default: // daily is default
        prefix = `every_masc_${numAccord}`;
        suffix = 'days';
        break;
    }
    return numAccord === 'plural' ? [prefix, amount.toString(), `${suffix}_${numAccord}`] : [prefix, `${suffix}_${numAccord}`];
  }

  getRecurrenceMagnitude(): string {
    const match = ISO8601PeriodRegex.exec(this.rawExpression);
    // Group 2 = date unit (D, W, M), Group 4 = time unit (H, M)
    const unit = match?.[2] ?? match?.[4];
    switch (unit) {
      case 'H': return 'hourly';
      case 'W': return 'weekly';
      case 'M': return match?.[2] ? 'monthly' : 'minutely'; // M before T = months, after T = minutes
      case 'D': return 'daily';
      default: return 'daily';
    }
  }

  getRecurrenceAmount(): string | undefined {
    const match = ISO8601PeriodRegex.exec(this.rawExpression);
    // Group 1 = date amount, Group 3 = time amount
    return match?.[1] ?? match?.[3];
  }

  getRecurrenceTime(): LocalHourMinute {
    return {
      hour: 0,
      minute: 0,
    };
  }

  static canHandleExpression(expression: string) {
    return ISO8601PeriodRegex.test(expression);
  }
}

export default ISO8601Period;
