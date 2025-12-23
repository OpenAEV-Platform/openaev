import cronstrue from 'cronstrue/i18n';

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

interface ParsedCron {
  w: string | null;
  d: string | null;
  h: string;
  m: string;
  owd: boolean;
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

  toCronExpression() {
    return Array.from(this.fields.entries().map((value, index) => value[1].field_expression)).join(' ').trimEnd();
  }

  toHumanReadableString(locale: string) {
    return cronstrue.toString(this.toCronExpression(), {
      verbose: true,
      locale,
    });
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

class CronField {
  field_expression: string;
  parser: CronFieldParser;
  constructor(field_expression: string, parser: CronFieldParser) {
    this.field_expression = field_expression;
    this.parser = parser;
  }

  isValid() {
    return this.parser.validate(this.field_expression) || this.field_expression === undefined;
  }
}

class CronParseError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'CronParseError';
  }
}

class WellKnownMasks {
  static seconds: CronFieldMask = { base_mask: '\\d|[1-5]\\d', // 0 through to 59
  };

  static minutes: CronFieldMask = { base_mask: '\\d|[1-5]\\d', // 0 through to 59
  };

  static hours: CronFieldMask = { base_mask: '\\d|1\\d|2[0-3]', // 0 through to 23
  };

  static weekdays: CronFieldMask = {
    base_mask: '[1-7]((#[1-5])|L)?', // 1 through to 7, with optional #1 through to #5 or L, or standalone ? or standalone L
    exclusive_mask: '\\?|L',
  };

  static monthdays: CronFieldMask = {
    base_mask: '[1-9]|1\\d|2\\d|3[0-1]', // 1 through to 31, or standalone ? or standalone L
    exclusive_mask: '\\?|L',
  };

  static months: CronFieldMask = { base_mask: '[1-9]|1[0-2]', // 1 through to 12
  };

  static years: CronFieldMask = { base_mask: '\\d+', // any positive number
  };
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

const parseCron = (cron: string): ParsedCron => {
  const cronSplits = cron.split(' ');
  let owd = false;
  let w = null;
  let d = null;
  if (cronSplits[5] !== '*') {
    if (cronSplits[5].includes('#')) {
      w = cronSplits[5].split('#')[1];
      d = cronSplits[5].split('#')[0];
    } else if (cronSplits[5].includes('L')) {
      w = '5';
      d = cronSplits[5].split('L')[0];
    } else if (cronSplits[5] === '1-5') {
      owd = true;
    } else {
      d = cronSplits[5];
    }
  }

  return ({
    w,
    d,
    h: cronSplits[2],
    m: cronSplits[1],
    owd,
  });
};

export {
  Cron,
  CronField,
  CronFieldParser,
  CronFieldPosition,
  CronParseError,
  CronParser,
  generateDailyCronExpression,
  generateMonthlyCronExpression,
  generateWeeklyCronExpression,
  parseCron,
  type ParsedCron,
  WellKnownMasks,
};
