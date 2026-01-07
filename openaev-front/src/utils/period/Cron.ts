import cronstrue from 'cronstrue/i18n';

import { type LocalHourMinute, PeriodExpressionHandler } from './PeriodExpressionHandler';

const generateHourlyCronExpression = (h: string, m: string, owd: boolean) => {
  if (owd) {
    return `0 ${m} */${h} * * 1-5`;
  }
  return `0 ${m} */${h} * * *`;
};

const generateDailyCronExpression = (h: string, m: string, owd: boolean) => {
  if (owd) {
    return `0 ${m} ${h} * * 1-5`;
  }
  return `0 ${m} ${h} * * *`;
};

const generateWeeklyCronExpression = (d: string, h: string, m: string) => {
  return `0 ${m} ${h} * * ${d}`;
};

const generateMonthlyCronExpression = (w: string, d: string, h: string, m: string) => {
  if (w === '5') {
    return `0 ${m} ${h} * * ${d}L`;
  }
  return `0 ${m} ${h} * * ${d}#${w}`;
};

/**
 * Field positions in the cron expression
 */
enum CronFieldPosition {
  Seconds,
  Minutes,
  Hours,
  Monthdays,
  Months,
  Weekdays,
  Years,
}

/**
 * A cron field validation mask
 * * base_mask: this is the mask to validate the general case of the expression, which can be combined with commas,
 * or used as range parts
 * * exclusive_mask: validates an expression when it can take an arbitrary, non-combinable form
 * e.g. 'L' for last which cannot be used as part of ranges
 */
type CronFieldMask = {
  base_mask: string;
  exclusive_mask?: string;
};

/**
 * Parses and validates that a field expression matches a given validation mask
 */
class CronFieldParser {
  mask: CronFieldMask;

  constructor(mask: CronFieldMask) {
    this.mask = mask;
  }

  /**
   * Checks whether a given expression matches the internal validation mask
   * @param field_expression a cron field expression
   * @returns true if the field expression matches the validation mask
   */
  validate(field_expression: string): boolean {
    let validator: string = `((\\*|(${this.mask.base_mask})(-(${this.mask.base_mask}))?)(\\/(${this.mask.base_mask}))?)(,((\\*|(${this.mask.base_mask})(-(${this.mask.base_mask}))?)(\\/(${this.mask.base_mask})(-(${this.mask.base_mask}))?)?))*`;
    if (this.mask.exclusive_mask) {
      validator += `|${this.mask.exclusive_mask}`;
    }
    const validation_mask: RegExp = new RegExp(`^(${validator})$`);
    return validation_mask.test(field_expression);
  }
}

/**
 * Validation masks for individual field expressions
 */
class WellKnownMasks {
  static seconds: CronFieldMask = { base_mask: '\\d|[1-5]\\d' };

  static minutes: CronFieldMask = { base_mask: '\\d|[1-5]\\d' };

  static hours: CronFieldMask = { base_mask: '\\d|1\\d|2[0-3]' };

  static weekdays: CronFieldMask = {
    base_mask: '[1-7]((#[1-5])|L)?',
    exclusive_mask: '\\?|L',
  };

  static monthdays: CronFieldMask = {
    base_mask: '[1-9]|1\\d|2\\d|3[0-1]',
    exclusive_mask: '\\?|L',
  };

  static months: CronFieldMask = { base_mask: '[1-9]|1[0-2]' };

  static years: CronFieldMask = { base_mask: '\\d+' };
}

/**
 * Some well-known range expressions
 */
class WellKnownRanges {
  static weekdays: string = '1-5';
}

const quartz_parser_set: Map<CronFieldPosition, CronFieldParser> = new Map<CronFieldPosition, CronFieldParser>([
  [CronFieldPosition.Seconds, new CronFieldParser(WellKnownMasks.seconds)],
  [CronFieldPosition.Minutes, new CronFieldParser(WellKnownMasks.minutes)],
  [CronFieldPosition.Hours, new CronFieldParser(WellKnownMasks.hours)],
  [CronFieldPosition.Monthdays, new CronFieldParser(WellKnownMasks.monthdays)],
  [CronFieldPosition.Months, new CronFieldParser(WellKnownMasks.months)],
  [CronFieldPosition.Weekdays, new CronFieldParser(WellKnownMasks.weekdays)],
  [CronFieldPosition.Years, new CronFieldParser(WellKnownMasks.years)],
]);

const unix_parser_set: Map<CronFieldPosition, CronFieldParser> = new Map<CronFieldPosition, CronFieldParser>([
  [CronFieldPosition.Minutes, new CronFieldParser(WellKnownMasks.minutes)],
  [CronFieldPosition.Hours, new CronFieldParser(WellKnownMasks.hours)],
  [CronFieldPosition.Monthdays, new CronFieldParser(WellKnownMasks.monthdays)],
  [CronFieldPosition.Months, new CronFieldParser(WellKnownMasks.months)],
  [CronFieldPosition.Weekdays, new CronFieldParser(WellKnownMasks.weekdays)],
]);

/**
 * A single cron field with its own expression
 */
class CronField {
  field_expression: string;
  parser: CronFieldParser;
  position: CronFieldPosition;

  constructor(field_expression: string, parser: CronFieldParser, position: CronFieldPosition) {
    this.field_expression = field_expression;
    this.parser = parser;
    this.position = position;
  }

  /**
   * Gets the numerical value of the field (may be a range)
   * Does not match wildcard '*'
   * @returns undefined if no match, else the value
   * @example for '1/22' returns '1'
   */
  getValue() {
    const matches = this.field_expression.match('([^\\*]+)[\\/|#|L]?');
    return matches?.[1];
  }

  /**
   * Gets the localised time value if the field is a time part (minutes, hours)
   * and it is a pure numerical expression.
   * @returns the local timezone time value if applicable, otherwise gets the expression verbatim
   */
  getLocalisedTimeValue() {
    if (this.isPureNumeric()) {
      const dt = new Date();
      switch (this.position) {
        case CronFieldPosition.Hours:
          dt.setUTCHours(Number(this.getValue()));
          return dt.getHours().toString();
        case CronFieldPosition.Minutes:
          dt.setUTCHours(0, Number(this.getValue()));
          return dt.getMinutes().toString();
        default:
          return this.field_expression;
      }
    }
    return this.field_expression;
  }

  /**
   * Gets the "recurrence" part of a field expression, i.e. after a '/' or a '#'
   * May also be 'L' for "last"
   * @returns undefined if no recurrence is set, else the recurrence value
   * @example for '1/22' returns '22'
   */
  getRecurrence() {
    const matches = this.field_expression.match('.*[\\/|#](.*)|(L)');
    return matches?.[1] || matches?.[2];
  }

  /**
   * A field is valid when the expression within is well-formed or undefined
   * Note: a field may be valid while undefined, and a given cron expression
   * may or may not be valid because of it
   * @returns true if the expression is valid, else false
   */
  isValid() {
    return this.parser.validate(this.field_expression) || this.field_expression === undefined;
  }

  /**
   * @returns true if the value is the literal '*'
   */
  isWildcard() {
    return this.field_expression.startsWith('*');
  }

  /**
   * @returns true if the value is the literal '0'
   */
  isZero() {
    return this.field_expression.startsWith('0');
  }

  /**
   * Checks if the expression is an integer, with no recurrence markers, no ranges
   * @returns true if the value is a numeric value, i.e. an integer
   */
  isPureNumeric() {
    return !isNaN(Number(this.field_expression));
  }

  /**
   * Checks whether the field's expression matches a given range expression
   * @param range the range to check against the field value
   * @returns true if the field expression matches exactly the given range expression
   */
  isRange(range: string) {
    return this.field_expression === range;
  }

  /**
   * @returns the field value as a typed integer
   */
  toNumber() {
    return Number(this.field_expression);
  }
}

function toCronExpression(fields: Map<CronFieldPosition, CronField>) {
  return Array.from(fields.entries().map(value => value[1].field_expression)).join(' ').trimEnd();
}

function toLocalisedCronExpression(fields: Map<CronFieldPosition, CronField>) {
  return Array.from(fields.entries().map(value => value[1].getLocalisedTimeValue())).join(' ').trimEnd();
}

/**
 * A cron expression
 */
class Cron extends PeriodExpressionHandler {
  fields: Map<CronFieldPosition, CronField>;
  constructor(fields: Map<CronFieldPosition, CronField>) {
    super(toCronExpression(fields));
    this.fields = fields;
  }

  isValid() {
    return this.fields.entries().every((value) => {
      return value[1].isValid();
    });
  }

  isUiSupported() {
    return this.isValid()
      && (this.fields.get(CronFieldPosition.Seconds)?.isZero() || !this.fields.get(CronFieldPosition.Seconds))
      && (this.fields.get(CronFieldPosition.Minutes)?.isPureNumeric() || false)
      && (
        this.fields.get(CronFieldPosition.Hours)?.isPureNumeric() // e.g. '12'
        || (this.fields.get(CronFieldPosition.Hours)?.getRecurrence() !== undefined && this.fields.get(CronFieldPosition.Hours)?.isWildcard()) // e.g. '*/22'
        || false)
      && (this.fields.get(CronFieldPosition.Monthdays)?.isWildcard() || false)
      && (this.fields.get(CronFieldPosition.Months)?.isWildcard() || false);
  }

  /**
   * @returns the full cron expression as a string
   */
  toCronExpression() {
    return toCronExpression(this.fields);
  }

  toLocalisedCronExpression() {
    return toLocalisedCronExpression(this.fields);
  }

  toHumanReadableString(locale: string) {
    return cronstrue.toString(this.toLocalisedCronExpression(), { locale });
  }

  toTranslatableStringArray(locale: string): string[] {
    return [this.toHumanReadableString(locale)];
  }

  getRecurrenceMagnitude(): string {
    if (this.getMonthlyRecurrence()) {
      return 'monthly';
    } else if (this.getHours()?.getRecurrence()) {
      return 'hourly';
    } else if (this.getWeeklyRecurrence() && !this.isOnlyOnWeekdays()) {
      return 'weekly';
    } else {
      return 'daily';
    }
  }

  getRecurrenceTime(): LocalHourMinute {
    const localDate = new Date();
    localDate.setUTCHours(this.getHours()?.toNumber() || 0, this.getMinutes()?.toNumber() || 0, 0, 0);
    return {
      hour: localDate.getHours(),
      minute: localDate.getMinutes(),
    };
  }

  // convenience methods

  /**
   * Checks whether the cron expression is constrained within the working days (MON-FRI)
   * @returns true if there is a weekday constraint on the cron expression
   */
  isOnlyOnWeekdays() {
    return (this.fields.get(CronFieldPosition.Weekdays)?.isRange(WellKnownRanges.weekdays));
  }

  /**
   * Gets the occurrence value of the Weekdays part of the cron expression if it exists, or undefined
   * @example for '1#2', returns '1'
   * @returns the occurrence value, or undefined if it doesn't exist or there is no Weekdays part
   */
  getWeeklyRecurrence() {
    return this.fields.get(CronFieldPosition.Weekdays)?.getValue();
  }

  /**
   * Gets the recurrence value of the Weekdays part of the cron expression
   * @returns the recurrence value, or undefined if it doesn't exist or there is no Weekdays part
   */
  getMonthlyRecurrence() {
    return this.fields.get(CronFieldPosition.Weekdays)?.getRecurrence();
  }

  /**
   * @returns the Seconds field of the cron expression
   */
  getSeconds() {
    return this.fields.get(CronFieldPosition.Seconds);
  }

  /**
   * @returns the Minutes field of the cron expression
   */
  getMinutes() {
    return this.fields.get(CronFieldPosition.Minutes);
  }

  /**
   * @returns the Hours field of the cron expression
   */
  getHours() {
    return this.fields.get(CronFieldPosition.Hours);
  }

  /**
   * @returns the Monthdays field of the cron expression
   */
  getMonthdays() {
    return this.fields.get(CronFieldPosition.Monthdays);
  }

  /**
   * @returns the Months field of the cron expression
   */
  getMonths() {
    return this.fields.get(CronFieldPosition.Months);
  }

  /**
   * @returns the Weekdays field of the cron expression
   */
  getWeekdays() {
    return this.fields.get(CronFieldPosition.Weekdays);
  }

  /**
   * @returns the Years field of the cron expression
   */
  getYears() {
    return this.fields.get(CronFieldPosition.Years);
  }
}

/**
 * Cron parsing specific error
 */
class CronParseError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'CronParseError';
  }
}

/**
 * Parses string expressions and builds Cron objects
 */
class CronParser {
  parsers: Map<CronFieldPosition, CronFieldParser>;
  constructor(parsers: Map<CronFieldPosition, CronFieldParser>) {
    this.parsers = parsers;
  }

  /**
   * Initialises a parser with Quartz Engine fields arrangement (7 fields)
   */
  static quartz() {
    return new CronParser(quartz_parser_set);
  }

  /**
   * Initialises a parser with Quartz Engine fields arrangement (7 fields)
   */
  static unix() {
    return new CronParser(unix_parser_set);
  }

  /**
   * Initialises all fields with individual expression parts
   * @param parts ordered array of cron expression parts (fields)
   * @returns a Cron object with fields initialised with the provided parts
   */
  parseParts(parts: string[]): Cron {
    const fields = new Map<CronFieldPosition, CronField>(
      this.parsers.entries().map(
        (value, index) => [value[0], new CronField(parts[index], value[1], value[0])]));

    return new Cron(fields);
  }

  /**
   * Creates a Cron object with fields initialised from the provided cron expression string.
   * Depending on the cron expression, the Cron object may have different fields arrangements:
   * * Unix (minute, hour, monthday, month, weekday) if the cron expression has 5 fields
   * * Quartz (second, minute, hour, monthday, month, weekday, year) if the cron expression has 6 or 7 fields
   * (Note that the Year field in the Quartz arrangement is uninitialised if the cron expression has only 6 fields)
   * @param expression a cron expression, as a string
   * @returns a Cron object initialised from the cron expression
   * @throws CronParseError if there are any number of fields other than 5, 6 or 7 (i.e. malformed cron expression)
   */
  static parse(expression: string): Cron {
    const parts = expression.split(' ');
    switch (parts.length) {
      case 5:
        return CronParser.unix().parseParts(parts);
      case 6:
      case 7:
        return CronParser.quartz().parseParts(parts);
      default:
        throw new CronParseError('Illegal number of parts in expression');
    }
  }
}

function canHandleExpression(expression: string) {
  try {
    return CronParser.parse(expression).isValid();
  } catch {
    return false;
  }
}

export {
  canHandleExpression,
  Cron,
  CronField,
  CronFieldParser,
  CronFieldPosition,
  CronParseError,
  CronParser,
  generateDailyCronExpression,
  generateHourlyCronExpression,
  generateMonthlyCronExpression,
  generateWeeklyCronExpression,
  WellKnownMasks,
};
