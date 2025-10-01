const generateDailyCron = (h: number, m: number, owd: boolean) => {
  if (owd) {
    return `0 ${m} ${h} * * 1-5`;
  }
  return `0 ${m} ${h} * * *`;
};

const generateWeeklyCron = (d: number, h: number, m: number) => {
  return `0 ${m} ${h} * * ${d}`;
};

const generateMonthlyCron = (w: number, d: number, h: number, m: number) => {
  if (w === 5) {
    return `0 ${m} ${h} * * ${d}L`;
  }
  return `0 ${m} ${h} * * ${d}#${w}`;
};

interface ParsedCron {
  w: number | null;
  d: number | null;
  h: number;
  m: number;
  owd: boolean;
}

const parseCron = (cron: string): ParsedCron => {
  const cronSplits = cron.split(' ');
  let owd = false;
  let w = null;
  let d = null;
  if (cronSplits[5] !== '*') {
    if (cronSplits[5].includes('#')) {
      w = Number(cronSplits[5].split('#')[1]);
      d = Number(cronSplits[5].split('#')[0]);
    } else if (cronSplits[5].includes('L')) {
      w = 5;
      d = Number(cronSplits[5].split('L')[0]);
    } else if (cronSplits[5] === '1-5') {
      owd = true;
    } else {
      d = Number(cronSplits[5]);
    }
  }

  return ({
    w,
    d,
    h: Number(cronSplits[2]),
    m: Number(cronSplits[1]),
    owd,
  });
};

export {
  generateDailyCron,
  generateMonthlyCron,
  generateWeeklyCron,
  parseCron,
  type ParsedCron,
};
