package io.openaev.architecture;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.Handle;
import org.springframework.asm.Label;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;

/**
 * The calls one method makes, in bytecode order. ArchUnit exposes the accesses of a method as an
 * unordered set, so a rule that asks "A is called before B" reads the instructions itself, as
 * {@link InjectEndpointScopeGuardRules} does.
 */
final class MethodCallOrder {

  /**
   * A call as the bytecode names it: the class the call is made against and the method name, and
   * whether a jump, a switch, the start of a try block or a catch handler comes before it in the
   * method. A call with none of those before it runs on every execution of the method that reaches
   * the call after it, so it dominates every later call.
   */
  record Call(String owner, String name, boolean afterBranch) {}

  private MethodCallOrder() {}

  /**
   * The calls of the one method of {@code type} called {@code methodName}, in the order of its
   * instructions. A lambda body is a method of its own and is not followed: a call made in a lambda
   * is not a call of the method, and the lambda's creation is not one either.
   *
   * @throws IllegalStateException if the class has no such method, or several overloads of it: the
   *     order of a name shared by two bodies is not a property of either
   */
  static List<Call> callsInOrder(Class<?> type, String methodName) {
    List<List<Call>> bodies = new ArrayList<>();
    try {
      new ClassReader(type.getName())
          .accept(
              new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(
                    int access, String name, String descriptor, String signature, String[] e) {
                  if (!name.equals(methodName)) {
                    return null;
                  }
                  List<Call> calls = new ArrayList<>();
                  bodies.add(calls);
                  return new MethodVisitor(Opcodes.ASM9) {
                    private final Set<Label> tryStartsAndHandlers = new HashSet<>();
                    private boolean branched;

                    @Override
                    public void visitTryCatchBlock(
                        Label start, Label end, Label handler, String exceptionType) {
                      tryStartsAndHandlers.add(start);
                      tryStartsAndHandlers.add(handler);
                    }

                    @Override
                    public void visitLabel(Label label) {
                      branched |= tryStartsAndHandlers.contains(label);
                    }

                    @Override
                    public void visitJumpInsn(int opcode, Label label) {
                      branched = true;
                    }

                    @Override
                    public void visitTableSwitchInsn(
                        int min, int max, Label dflt, Label... labels) {
                      branched = true;
                    }

                    @Override
                    public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
                      branched = true;
                    }

                    @Override
                    public void visitMethodInsn(
                        int opcode, String owner, String called, String d, boolean itf) {
                      calls.add(new Call(owner.replace('/', '.'), called, branched));
                    }
                  };
                }
              },
              ClassReader.SKIP_FRAMES);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read the bytecode of " + type.getName(), e);
    }
    if (bodies.size() != 1) {
      throw new IllegalStateException(
          type.getName() + " has " + bodies.size() + " methods called " + methodName);
    }
    return bodies.get(0);
  }

  /**
   * A call or a method reference reached from one method, with its descriptor, and whether it is
   * made in the body of a lambda passed as the work argument of a scope-opening call.
   */
  record ScopedCall(String owner, String name, String descriptor, boolean inScopeWork) {}

  /**
   * The calls of the method {@code methodName} with {@code methodDescriptor} of {@code type}, and
   * the calls of the lambdas that method creates, at any depth, each marked with whether it is made
   * in a lambda passed as the work argument of {@code scopeOwner#scopeMethods}. The work argument
   * is the last one of those methods, so the lambda is the work only when its creation (the {@code
   * invokedynamic}) is the instruction right before the scope-opening call: a lambda stored in a
   * variable, or created anywhere else in the method, is not the work of that scope. A lambda
   * created inside the work lambda is not under the scope either, since nothing says when it runs.
   * A method reference is a call of its target where it is created.
   */
  static List<ScopedCall> callsWithScopeOf(
      Class<?> type,
      String methodName,
      String methodDescriptor,
      String scopeOwner,
      Set<String> scopeMethods) {
    String internalName = type.getName().replace('.', '/');
    String scopeInternalName = scopeOwner.replace('.', '/');
    Map<String, MethodBody> bodies = new HashMap<>();
    try {
      new ClassReader(type.getName())
          .accept(
              new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(
                    int access, String name, String descriptor, String signature, String[] e) {
                  MethodBody body = new MethodBody();
                  bodies.put(name + descriptor, body);
                  return new MethodVisitor(Opcodes.ASM9) {
                    private LambdaSite pending;

                    @Override
                    public void visitInvokeDynamicInsn(
                        String name, String descriptor, Handle bootstrap, Object... arguments) {
                      pending = null;
                      if (LAMBDA_METAFACTORY.equals(bootstrap.getOwner())
                          && arguments.length > 1
                          && arguments[1] instanceof Handle impl) {
                        pending = new LambdaSite(impl);
                        body.lambdas.add(pending);
                      }
                    }

                    @Override
                    public void visitMethodInsn(
                        int opcode, String owner, String called, String d, boolean itf) {
                      if (pending != null
                          && owner.equals(scopeInternalName)
                          && scopeMethods.contains(called)) {
                        pending.scopeWork = true;
                      }
                      pending = null;
                      body.calls.add(new ScopedCall(owner.replace('/', '.'), called, d, false));
                    }

                    @Override
                    public void visitInsn(int opcode) {
                      pending = null;
                    }

                    @Override
                    public void visitIntInsn(int opcode, int operand) {
                      pending = null;
                    }

                    @Override
                    public void visitVarInsn(int opcode, int varIndex) {
                      pending = null;
                    }

                    @Override
                    public void visitTypeInsn(int opcode, String typeName) {
                      pending = null;
                    }

                    @Override
                    public void visitFieldInsn(
                        int opcode, String owner, String name, String descriptor) {
                      pending = null;
                    }

                    @Override
                    public void visitJumpInsn(int opcode, Label label) {
                      pending = null;
                    }

                    @Override
                    public void visitLdcInsn(Object value) {
                      pending = null;
                    }

                    @Override
                    public void visitIincInsn(int varIndex, int increment) {
                      pending = null;
                    }

                    @Override
                    public void visitTableSwitchInsn(
                        int min, int max, Label dflt, Label... labels) {
                      pending = null;
                    }

                    @Override
                    public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
                      pending = null;
                    }

                    @Override
                    public void visitMultiANewArrayInsn(String descriptor, int dimensions) {
                      pending = null;
                    }
                  };
                }
              },
              ClassReader.SKIP_FRAMES);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read the bytecode of " + type.getName(), e);
    }
    if (!bodies.containsKey(methodName + methodDescriptor)) {
      throw new IllegalStateException(
          type.getName() + " has no method " + methodName + methodDescriptor);
    }
    List<ScopedCall> calls = new ArrayList<>();
    collect(bodies, internalName, methodName + methodDescriptor, false, new HashSet<>(), calls);
    return calls;
  }

  private static void collect(
      Map<String, MethodBody> bodies,
      String internalName,
      String method,
      boolean inScopeWork,
      Set<String> visited,
      List<ScopedCall> into) {
    MethodBody body = bodies.get(method);
    if (body == null || !visited.add(method + inScopeWork)) {
      return;
    }
    for (ScopedCall call : body.calls) {
      into.add(new ScopedCall(call.owner(), call.name(), call.descriptor(), inScopeWork));
    }
    for (LambdaSite lambda : body.lambdas) {
      Handle impl = lambda.impl;
      if (impl.getOwner().equals(internalName) && impl.getName().startsWith("lambda$")) {
        collect(
            bodies, internalName, impl.getName() + impl.getDesc(), lambda.scopeWork, visited, into);
      } else {
        into.add(
            new ScopedCall(
                impl.getOwner().replace('/', '.'),
                impl.getName(),
                impl.getDesc(),
                lambda.scopeWork));
      }
    }
  }

  private static final String LAMBDA_METAFACTORY = "java/lang/invoke/LambdaMetafactory";

  private static final class MethodBody {
    private final List<ScopedCall> calls = new ArrayList<>();
    private final List<LambdaSite> lambdas = new ArrayList<>();
  }

  private static final class LambdaSite {
    private final Handle impl;
    private boolean scopeWork;

    private LambdaSite(Handle impl) {
      this.impl = impl;
    }
  }

  /** The first call of {@code owner#name} in {@code calls}, or empty when there is none. */
  static Optional<Integer> firstIndexOf(List<Call> calls, String owner, String name) {
    return indexOf(calls, owner, name, false);
  }

  /** The first call of {@code owner#name} with no branch before it, or empty when there is none. */
  static Optional<Integer> firstUnconditionalIndexOf(List<Call> calls, String owner, String name) {
    return indexOf(calls, owner, name, true);
  }

  private static Optional<Integer> indexOf(
      List<Call> calls, String owner, String name, boolean unconditionalOnly) {
    for (int i = 0; i < calls.size(); i++) {
      Call call = calls.get(i);
      if (call.owner().equals(owner)
          && call.name().equals(name)
          && !(unconditionalOnly && call.afterBranch())) {
        return Optional.of(i);
      }
    }
    return Optional.empty();
  }
}
