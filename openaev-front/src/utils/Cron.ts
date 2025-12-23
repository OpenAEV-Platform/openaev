import cronstrue from 'cronstrue/i18n';

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

enum CronFieldPosition {
  Seconds,
  Minutes,
  Hours,
  Monthdays,
  Months,
  Weekdays,
  Years,
}

type CronFieldMask = {
  base_mask: string;
  exclusive_mask?: string;
};

class CronFieldParser {
  mask: CronFieldMask;
  constructor(mask: CronFieldMask) {
    this.mask = mask;
  }

  validate(field_expression: string): boolean {
    let validator: string = `((\\*|(${this.mask.base_mask})(-(${this.mask.base_mask}))?)(\\/(${this.mask.base_mask}))?)(,((\\*|(${this.mask.base_mask})(-(${this.mask.base_mask}))?)(\\/(${this.mask.base_mask})(-(${this.mask.base_mask}))?)?))*`;
    if (this.mask.exclusive_mask) {
      validator += `|${this.mask.exclusive_mask}`;
    }
    const validation_mask: RegExp = new RegExp(`^(${validator})$`);
    return validation_mask.test(field_expression);
  }
}

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

class CronField {
  field_expression: string;
  parser: CronFieldParser;
  constructor(field_expression: string, parser: CronFieldParser) {
    this.field_expression = field_expression;
    this.parser = parser;
  }

  getValue() {
    const matches = this.field_expression.match('([^\\*])[\\/|#|L]?');
    return matches?.[1];
  }

  getRecurrence() {
    const matches = this.field_expression.match('.*[\\/|#](.*)|(L)');
    return matches?.[1] || matches?.[2];
  }

  isValid() {
    return this.parser.validate(this.field_expression) || this.field_expression === undefined;
  }

  isWildcard() {
    return this.field_expression === '*';
  }

  isZero() {
    return this.field_expression === '0';
  }

  isPureNumeric() {
    return !isNaN(Number(this.field_expression));
  }

  isRange(range: string) {
    return this.field_expression === range;
  }

  toNumber() {
    return Number(this.field_expression);
  }
}

class Cron {
  fields: Map<CronFieldPosition, CronField>;
  constructor(fields: Map<CronFieldPosition, CronField>) {
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
      && (this.fields.get(CronFieldPosition.Hours)?.isPureNumeric() || false)
      && (this.fields.get(CronFieldPosition.Monthdays)?.isWildcard() || false)
      && (this.fields.get(CronFieldPosition.Months)?.isWildcard() || false);
  }

  toCronExpression() {
    return Array.from(this.fields.entries().map(value => value[1].field_expression)).join(' ').trimEnd();
  }

  toHumanReadableString(locale: string) {
    return cronstrue.toString(this.toCronExpression(), { locale });
  }

  // convenience methods
  isOnlyOnWeekdays() {
    return (this.fields.get(CronFieldPosition.Weekdays)?.isRange(WellKnownRanges.weekdays));
  }

  getWeeklyRecurrence() {
    return this.fields.get(CronFieldPosition.Weekdays)?.getValue();
  }

  /**
   * This is one way to set a monthly recurrence, with the nth weekday
   */
  getMonthlyRecurrence() {
    return this.fields.get(CronFieldPosition.Weekdays)?.getRecurrence();
  }

  getSeconds() {
    return this.fields.get(CronFieldPosition.Seconds);
  }

  getMinutes() {
    return this.fields.get(CronFieldPosition.Minutes);
  }

  getHours() {
    return this.fields.get(CronFieldPosition.Hours);
  }

  getMonthdays() {
    return this.fields.get(CronFieldPosition.Monthdays);
  }

  getMonths() {
    return this.fields.get(CronFieldPosition.Months);
  }

  getWeekdays() {
    return this.fields.get(CronFieldPosition.Weekdays);
  }

  getYears() {
    return this.fields.get(CronFieldPosition.Years);
  }
}

class CronParseError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'CronParseError';
  }
}

class CronParser {
  parsers: Map<CronFieldPosition, CronFieldParser>;
  constructor(parsers: Map<CronFieldPosition, CronFieldParser>) {
    this.parsers = parsers;
  }

  static quartz() {
    return new CronParser(quartz_parser_set);
  }

  static unix() {
    return new CronParser(unix_parser_set);
  }

  parseParts(parts: string[]): Cron {
    const fields = new Map<CronFieldPosition, CronField>(
      this.parsers.entries().map(
        (value, index) => [value[0], new CronField(parts[index], value[1])]));

    return new Cron(fields);
  }

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

export {
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
