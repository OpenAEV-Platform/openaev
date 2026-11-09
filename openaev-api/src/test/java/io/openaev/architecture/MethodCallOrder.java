package io.openaev.architecture;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
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
   * The calls made in the lambdas of the one method of {@code type} called {@code methodName}: the
   * bodies of its {@code lambda$methodName$N} methods, which {@link #callsInOrder} does not follow.
   * ArchUnit attributes those calls to the enclosing method, so only the bytecode tells a call made
   * in a lambda from one made beside it.
   */
  static List<Call> callsInLambdasOf(Class<?> type, String methodName) {
    Pattern lambda = Pattern.compile("lambda\\$" + Pattern.quote(methodName) + "\\$\\d+");
    List<Call> calls = new ArrayList<>();
    try {
      new ClassReader(type.getName())
          .accept(
              new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(
                    int access, String name, String descriptor, String signature, String[] e) {
                  if (!lambda.matcher(name).matches()) {
                    return null;
                  }
                  return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(
                        int opcode, String owner, String called, String d, boolean itf) {
                      calls.add(new Call(owner.replace('/', '.'), called, false));
                    }
                  };
                }
              },
              ClassReader.SKIP_FRAMES);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read the bytecode of " + type.getName(), e);
    }
    return calls;
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
