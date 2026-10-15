import { after, describe, it } from 'node:test';

import { RuleTester } from '@typescript-eslint/rule-tester';

// Report each RuleTester case as its own node:test test
RuleTester.afterAll = after;
RuleTester.describe = describe;
RuleTester.it = it;
RuleTester.itOnly = it.only;

export default RuleTester;
