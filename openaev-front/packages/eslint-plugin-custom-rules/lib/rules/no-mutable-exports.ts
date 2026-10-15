// Importers get a live binding to an exported `let`/`var`, so a module that reassigns
// it changes values under their feet. Exported bindings must be `const`.

import { AST_NODE_TYPES, ASTUtils, ESLintUtils, type TSESTree } from '@typescript-eslint/utils';

const MUTABLE_KINDS = new Set(['let', 'var']);

export default ESLintUtils.RuleCreator.withoutDocs({
  meta: {
    type: 'suggestion',
    docs: { description: 'Forbid exporting mutable (`let` or `var`) bindings' },
    messages: { mutable: 'Exporting mutable \'{{kind}}\' binding, use \'const\' instead.' },
    schema: [],
  },
  defaultOptions: [],
  create: (context) => {
    const { sourceCode } = context;
    // `let a, b; export { a, b };` reports the declaration once
    const reported = new Set<TSESTree.Node>();

    const reportDeclaration = (declaration: TSESTree.Node) => {
      if (declaration.type === AST_NODE_TYPES.VariableDeclaration && MUTABLE_KINDS.has(declaration.kind) && !reported.has(declaration)) {
        reported.add(declaration);
        context.report({
          node: declaration,
          messageId: 'mutable',
          data: { kind: declaration.kind },
        });
      }
    };

    const checkLocalBinding = (node: TSESTree.Node, identifier: TSESTree.Identifier) => {
      ASTUtils.findVariable(sourceCode.getScope(node), identifier)?.defs.forEach((def) => {
        if (def.type === 'Variable') {
          reportDeclaration(def.parent);
        }
      });
    };

    return {
      ExportNamedDeclaration(node) {
        if (node.declaration) {
          reportDeclaration(node.declaration);
          return;
        }
        // `export { x } from '...'` re-exports another module's binding
        if (node.source || node.exportKind === 'type') {
          return;
        }
        node.specifiers.forEach((specifier) => {
          if (specifier.exportKind !== 'type' && specifier.local.type === AST_NODE_TYPES.Identifier) {
            checkLocalBinding(node, specifier.local);
          }
        });
      },
      ExportDefaultDeclaration(node) {
        if (node.declaration.type === AST_NODE_TYPES.Identifier) {
          checkLocalBinding(node, node.declaration);
        }
      },
    };
  },
});
