import { List, Map as ImmutableMap } from 'immutable';

export type DeepEqualCase = {
  name: string;
  a: unknown;
  b: unknown;
  expected: boolean;
  // when the result depends on the argument order (a known quirk), the result with the arguments swapped
  expectedSwapped?: boolean;
};

// Expected values are the results of Ramda's `equals` (0.32), frozen here so that the test needs no Ramda: they
// were checked against Ramda's `equals` in both argument orders when `deepEqual` replaced it.
// Values are built by functions where a cycle or a non-trivial object is needed.
const cycle = (value: number): Record<string, unknown> => {
  const node: Record<string, unknown> = { value };
  node.self = node;
  return node;
};
const mutualArray = (): unknown[] => {
  const list: unknown[] = [];
  list.push(list);
  return list;
};
const argumentsOf = (...args: unknown[]) => {
  function collect(..._items: unknown[]) {
    // eslint-disable-next-line prefer-rest-params
    return arguments;
  }
  return collect(...args);
};
class First { x = 1; }
class Second { x = 1; }
const withInherited = Object.create({ x: 1 });
const nonEnumerable = Object.defineProperty({}, 'hidden', {
  value: 1,
  enumerable: false,
});
const symbolKey = Symbol('key');
const sharedFunction = () => 1;
const sharedSymbol = Symbol('shared');
const sharedPromise = Promise.resolve(1);
const sparse: number[] = [];
sparse[1] = 1;
const arrayWithProperty = Object.assign([1, 2], { extra: true });

export const buildDeepEqualCases = (): DeepEqualCase[] => [
  // primitives
  {
    name: 'same numbers',
    a: 1,
    b: 1,
    expected: true,
  },
  {
    name: 'a number and a string',
    a: 1,
    b: '1',
    expected: false,
  },
  {
    name: 'zero and negative zero',
    a: 0,
    b: -0,
    expected: false,
  },
  {
    name: 'NaN and NaN',
    a: NaN,
    b: NaN,
    expected: true,
  },
  {
    name: 'null and null',
    a: null,
    b: null,
    expected: true,
  },
  {
    name: 'undefined and undefined',
    a: undefined,
    b: undefined,
    expected: true,
  },
  {
    name: 'null and undefined',
    a: null,
    b: undefined,
    expected: false,
  },
  {
    name: 'zero and false',
    a: 0,
    b: false,
    expected: false,
  },
  {
    name: 'empty string and null',
    a: '',
    b: null,
    expected: false,
  },
  {
    name: 'same strings',
    a: 'abc',
    b: 'abc',
    expected: true,
  },
  {
    name: 'different strings',
    a: 'abc',
    b: 'abd',
    expected: false,
  },
  {
    name: 'true and false',
    a: true,
    b: false,
    expected: false,
  },
  {
    name: 'same bigints',
    a: 1n,
    b: 1n,
    expected: true,
  },
  {
    name: 'different bigints',
    a: 1n,
    b: 2n,
    expected: false,
  },
  {
    name: 'same symbol',
    a: sharedSymbol,
    b: sharedSymbol,
    expected: true,
  },
  {
    name: 'two distinct symbols',
    a: Symbol('a'),
    b: Symbol('a'),
    expected: false,
  },
  // wrappers
  {
    name: 'a number and its wrapper',
    a: 1,
    b: Object(1),
    expected: false,
  },
  {
    name: 'two equal number wrappers',
    a: Object(1),
    b: Object(1),
    expected: true,
  },
  {
    name: 'two string wrappers',
    a: Object('a'),
    b: Object('a'),
    expected: true,
  },
  {
    name: 'wrappers of NaN',
    a: Object(NaN),
    b: Object(NaN),
    expected: true,
  },
  {
    name: 'wrappers of zero and negative zero',
    a: Object(0),
    b: Object(-0),
    expected: false,
  },
  {
    name: 'boolean wrappers',
    a: Object(true),
    b: Object(false),
    expected: false,
  },
  // arrays
  {
    name: 'empty arrays',
    a: [],
    b: [],
    expected: true,
  },
  {
    name: 'same arrays',
    a: [1, 2, 3],
    b: [1, 2, 3],
    expected: true,
  },
  {
    name: 'same items in another order',
    a: [1, 2, 3],
    b: [3, 2, 1],
    expected: false,
  },
  {
    name: 'nested arrays',
    a: [1, [2, [3]]],
    b: [1, [2, [3]]],
    expected: true,
  },
  {
    name: 'different lengths',
    a: [1, 2],
    b: [1, 2, 3],
    expected: false,
  },
  {
    name: 'an array and an object',
    a: [],
    b: {},
    expected: false,
  },
  {
    name: 'arrays of NaN',
    a: [NaN],
    b: [NaN],
    expected: true,
  },
  {
    name: 'arrays of zero and negative zero',
    a: [0],
    b: [-0],
    expected: false,
  },
  {
    name: 'a sparse array and a dense one',
    a: sparse,
    b: [undefined, 1],
    expected: false,
  },
  {
    name: 'an array with an extra property',
    a: arrayWithProperty,
    b: [1, 2],
    expected: false,
  },
  {
    name: 'array of objects',
    a: [{ id: 1 }, { id: 2 }],
    b: [{ id: 1 }, { id: 2 }],
    expected: true,
  },
  {
    name: 'array of objects with one different',
    a: [{ id: 1 }, { id: 2 }],
    b: [{ id: 1 }, { id: 3 }],
    expected: false,
  },
  // objects
  {
    name: 'empty objects',
    a: {},
    b: {},
    expected: true,
  },
  {
    name: 'keys in another order',
    a: {
      a: 1,
      b: 2,
    },
    b: {
      b: 2,
      a: 1,
    },
    expected: true,
  },
  {
    name: 'an undefined value and a missing key',
    a: { a: 1 },
    b: {
      a: 1,
      b: undefined,
    },
    expected: false,
  },
  {
    name: 'undefined values under different keys',
    a: { a: undefined },
    b: { b: undefined },
    expected: false,
  },
  {
    name: 'null and undefined values',
    a: { a: null },
    b: { a: undefined },
    expected: false,
  },
  {
    name: 'deep equal objects',
    a: { a: { b: { c: [1, { d: 2 }] } } },
    b: { a: { b: { c: [1, { d: 2 }] } } },
    expected: true,
  },
  {
    name: 'deep objects differing at the bottom',
    a: { a: { b: { c: [1, { d: 2 }] } } },
    b: { a: { b: { c: [1, { d: 3 }] } } },
    expected: false,
  },
  {
    name: 'an object without prototype and a plain one',
    a: Object.create(null),
    b: {},
    expected: true,
  },
  {
    name: 'instances of two classes with the same keys',
    a: new First(),
    b: new Second(),
    expected: true,
  },
  {
    name: 'an inherited key and an empty object',
    a: withInherited,
    b: {},
    expected: true,
  },
  {
    name: 'an inherited key and an own key',
    a: withInherited,
    b: { x: 1 },
    expected: false,
  },
  {
    name: 'a non enumerable key and an empty object',
    a: nonEnumerable,
    b: {},
    expected: true,
  },
  {
    name: 'a symbol key and an empty object',
    a: { [symbolKey]: 1 },
    b: {},
    expected: true,
  },
  {
    name: 'an object and null',
    a: {},
    b: null,
    expected: false,
  },
  // dates
  {
    name: 'same dates',
    a: new Date(1000),
    b: new Date(1000),
    expected: true,
  },
  {
    name: 'different dates',
    a: new Date(1000),
    b: new Date(2000),
    expected: false,
  },
  {
    name: 'two invalid dates',
    a: new Date(NaN),
    b: new Date(NaN),
    expected: true,
  },
  {
    name: 'a date and a number',
    a: new Date(1000),
    b: 1000,
    expected: false,
  },
  {
    name: 'a date and an empty object',
    a: new Date(1000),
    b: {},
    expected: false,
  },
  // regular expressions
  {
    name: 'same regexps',
    a: /a/g,
    b: /a/g,
    expected: true,
  },
  {
    name: 'regexps with a different flag',
    a: /a/,
    b: /a/g,
    expected: false,
  },
  {
    name: 'regexps ignoring case or not',
    a: /a/i,
    b: /a/,
    expected: false,
  },
  {
    name: 'multiline or not',
    a: /a/m,
    b: /a/,
    expected: false,
  },
  {
    name: 'sticky or not',
    a: /a/y,
    b: /a/,
    expected: false,
  },
  {
    name: 'unicode or not',
    a: /a/u,
    b: /a/,
    expected: false,
  },
  {
    name: 'regexps with different sources',
    a: /a/,
    b: /b/,
    expected: false,
  },
  {
    name: 'dotAll or not (flag ignored)',
    a: /a/s,
    b: /a/,
    expected: true,
  },
  {
    name: 'hasIndices or not (flag ignored)',
    a: /a/d,
    b: /a/,
    expected: true,
  },
  // errors
  {
    name: 'same errors',
    a: new Error('x'),
    b: new Error('x'),
    expected: true,
  },
  {
    name: 'errors with different messages',
    a: new Error('x'),
    b: new Error('y'),
    expected: false,
  },
  {
    name: 'errors of different names',
    a: new Error('x'),
    b: new TypeError('x'),
    expected: false,
  },
  // maps and sets
  {
    name: 'same maps',
    a: new Map([[1, { a: 1 }]]),
    b: new Map([[1, { a: 1 }]]),
    expected: true,
  },
  {
    name: 'maps with entries in another order',
    a: new Map([[1, 1], [2, 2]]),
    b: new Map([[2, 2], [1, 1]]),
    expected: true,
  },
  {
    name: 'maps of different sizes',
    a: new Map([[1, 1]]),
    b: new Map([[1, 1], [2, 2]]),
    expected: false,
  },
  {
    name: 'maps with a different value',
    a: new Map([[1, 1]]),
    b: new Map([[1, 2]]),
    expected: false,
  },
  {
    name: 'maps with deep equal keys',
    a: new Map([[{ a: 1 }, 1]]),
    b: new Map([[{ a: 1 }, 1]]),
    expected: true,
  },
  {
    name: 'sets in another order',
    a: new Set([1, 2]),
    b: new Set([2, 1]),
    expected: true,
  },
  {
    name: 'sets with a different item',
    a: new Set([1]),
    b: new Set([2]),
    expected: false,
  },
  {
    name: 'sets of deep equal objects',
    a: new Set([{ a: 1 }]),
    b: new Set([{ a: 1 }]),
    expected: true,
  },
  {
    name: 'a map and a set',
    a: new Map(),
    b: new Set(),
    expected: false,
  },
  {
    name: 'a map and an object',
    a: new Map(),
    b: {},
    expected: false,
  },
  {
    name: 'sets with duplicated contents (known quirk, not a multiset comparison)',
    a: new Set([{ a: 1 }, { a: 1 }]),
    b: new Set([{ a: 1 }, { a: 2 }]),
    expected: true,
    expectedSwapped: false,
  },
  // functions and other types
  {
    name: 'the same function',
    a: sharedFunction,
    b: sharedFunction,
    expected: true,
  },
  {
    name: 'two identical looking functions',
    a: () => 1,
    b: () => 1,
    expected: false,
  },
  {
    name: 'the same promise',
    a: sharedPromise,
    b: sharedPromise,
    expected: true,
  },
  {
    name: 'two promises',
    a: Promise.resolve(1),
    b: Promise.resolve(1),
    expected: false,
  },
  {
    name: 'two weak maps',
    a: new WeakMap(),
    b: new WeakMap(),
    expected: false,
  },
  // typed arrays and buffers
  {
    name: 'same typed arrays',
    a: new Uint8Array([1, 2]),
    b: new Uint8Array([1, 2]),
    expected: true,
  },
  {
    name: 'typed arrays with a different item',
    a: new Uint8Array([1, 2]),
    b: new Uint8Array([1, 3]),
    expected: false,
  },
  {
    name: 'typed arrays of different types',
    a: new Uint8Array([1]),
    b: new Int8Array([1]),
    expected: false,
  },
  {
    name: 'array buffers (no own key, known quirk)',
    a: new ArrayBuffer(2),
    b: new ArrayBuffer(3),
    expected: true,
  },
  // arguments
  {
    name: 'same arguments objects',
    a: argumentsOf(1, 2),
    b: argumentsOf(1, 2),
    expected: true,
  },
  {
    name: 'an arguments object and an array',
    a: argumentsOf(1, 2),
    b: [1, 2],
    expected: false,
  },
  // cycles
  {
    name: 'two similar cycles',
    a: cycle(1),
    b: cycle(1),
    expected: true,
  },
  {
    name: 'two cycles with different values',
    a: cycle(1),
    b: cycle(2),
    expected: false,
  },
  {
    name: 'a cycle and a finite object',
    a: cycle(1),
    b: {
      value: 1,
      self: {
        value: 1,
        self: undefined,
      },
    },
    expected: false,
  },
  {
    name: 'two arrays containing themselves',
    a: mutualArray(),
    b: mutualArray(),
    expected: true,
  },
  // custom equality
  {
    name: 'both sides with an equals method returning true',
    a: { equals: () => true },
    b: { equals: () => true },
    expected: true,
  },
  {
    name: 'one side with an equals method returning false',
    a: { equals: () => true },
    b: { equals: () => false },
    expected: false,
  },
  {
    name: 'an equals method on one side only',
    a: { equals: () => true },
    b: {},
    expected: false,
  },
  {
    name: 'a fantasy-land equals method on both sides',
    a: { 'fantasy-land/equals': () => true },
    b: { 'fantasy-land/equals': () => true },
    expected: true,
  },
  {
    name: 'a fantasy-land equals method on one side only',
    a: { 'fantasy-land/equals': () => true },
    b: {},
    expected: false,
  },
  {
    name: 'equal immutable lists',
    a: List([1, 2]),
    b: List([1, 2]),
    expected: true,
  },
  {
    name: 'different immutable lists',
    a: List([1, 2]),
    b: List([1, 3]),
    expected: false,
  },
  {
    name: 'equal immutable maps',
    a: ImmutableMap({ a: 1 }),
    b: ImmutableMap({ a: 1 }),
    expected: true,
  },
  // data shaped like the store
  {
    name: 'a list of teams',
    a: [{
      team_id: 't1',
      team_name: 'Blue',
      team_users: ['u1', 'u2'],
      team_tags: [],
    }],
    b: [{
      team_name: 'Blue',
      team_tags: [],
      team_id: 't1',
      team_users: ['u1', 'u2'],
    }],
    expected: true,
  },
  {
    name: 'a list of teams with a different user',
    a: [{
      team_id: 't1',
      team_users: ['u1', 'u2'],
    }],
    b: [{
      team_id: 't1',
      team_users: ['u1', 'u3'],
    }],
    expected: false,
  },
  {
    name: 'a map by id',
    a: {
      t1: { team_id: 't1' },
      t2: { team_id: 't2' },
    },
    b: {
      t2: { team_id: 't2' },
      t1: { team_id: 't1' },
    },
    expected: true,
  },
];
