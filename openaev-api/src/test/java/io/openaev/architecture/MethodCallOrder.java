package io.openaev.architecture;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;

/**
 * The calls one method makes, in bytecode order. ArchUnit exposes the accesses of a method as an
 * unordered set, so a rule that asks "A is called before B" reads the instructions itself, as
 * {@link InjectEndpointScopeGuardRules} does.
 */
final class MethodCallOrder {

  /** A call as the bytecode names it: the class the call is made against and the method name. */
  record Call(String owner, String name) {}

  private MethodCallOrder() {}

  /**
   * The calls of the one method of {@code type} called {@code methodName}, in the order of its
   * instructions. A lambda body is a method of its own and is not followed.
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
                    @Override
                    public void visitMethodInsn(
                        int opcode, String owner, String called, String d, boolean itf) {
                      calls.add(new Call(owner.replace('/', '.'), called));
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
}
