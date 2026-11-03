import rule from '../../../lib/rules/no-mutable-exports.ts';
import RuleTester from '../../rule-tester.ts';

const ruleTester = new RuleTester();

ruleTester.run('no-mutable-exports', rule, {
  valid: [
    'export const a = 1;',
    'export function f() {}',
    'export class C {}',
    'const a = 1; export { a };',
    'const a = 1; export { a as b };',
    'const a = 1; export default a;',
    'function f() {} export { f };',
    'class C {} export default C;',
    'export default 1;',
    'export default function () {}',
    'export { a } from \'./a\';',
    'export * from \'./a\';',
    'let a = 1; a += 1; console.log(a);',
    'function f() { let a = 1; return a; } export { f };',
    'type T = string; export type { T };',
    'interface I { a: string } export { type I };',
  ],

  invalid: [
    {
      code: 'export let a = 1;',
      errors: [{
        messageId: 'mutable',
        data: { kind: 'let' },
      }],
    },
    {
      code: 'export var a = 1;',
      errors: [{
        messageId: 'mutable',
        data: { kind: 'var' },
      }],
    },
    {
      code: 'let a = 1; export { a };',
      errors: [{
        messageId: 'mutable',
        data: { kind: 'let' },
      }],
    },
    {
      code: 'var a = 1; export { a as b };',
      errors: [{
        messageId: 'mutable',
        data: { kind: 'var' },
      }],
    },
    {
      code: 'let a = 1; export default a;',
      errors: [{
        messageId: 'mutable',
        data: { kind: 'let' },
      }],
    },
    {
      code: 'let a = 1, b = 2; export { a, b };',
      errors: [{ messageId: 'mutable' }],
    },
    {
      code: 'let a = 1; const b = 2; export { a, b };',
      errors: [{ messageId: 'mutable' }],
    },
    {
      code: 'export let a: string = \'\';',
      errors: [{
        messageId: 'mutable',
        data: { kind: 'let' },
      }],
    },
  ],
});
