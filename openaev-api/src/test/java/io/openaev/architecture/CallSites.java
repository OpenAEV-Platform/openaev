package io.openaev.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.Handle;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.asm.Type;

/**
 * Where a call is made: in the body of a method, or in the body of a lambda it creates. ArchUnit
 * attributes the calls of a lambda body to the method that creates the lambda, so it cannot tell
 * {@code executeAsPlatformWrite(ctx, () -> repo.save(s))} from {@code repo.save(s)} written after
 * the primitive returned. The class file can: a lambda body is a synthetic {@code lambda$...}
 * method, linked to its creator by the {@code invokedynamic} that builds it.
 */
final class CallSites {

  /** A call target: the declaring class (dotted name as written at the call site) and the name. */
  record Target(String owner, String name) {}

  /**
   * Targets called directly in a method body, targets called only from its lambdas, and for a
   * method reference the type of the object it is taken on: the handle of {@code repo::delete}
   * names the declaring type ({@code CrudRepository}), never {@code repo}'s own type. The receivers
   * of a target are listed once per reference, in order, so two references on two types that share
   * a declaring type stay two.
   */
  record Sites(
      Set<Target> direct, Set<Target> inLambdas, Map<Target, List<String>> referenceReceivers) {
    Sites() {
      this(new HashSet<>(), new HashSet<>(), new HashMap<>());
    }
  }

  private static final Map<String, Map<String, Sites>> CACHE = new HashMap<>();

  private static final Map<String, Map<String, Set<String>>> STRINGS = new HashMap<>();

  private CallSites() {}

  /** The sites of {@code methodName} in {@code owner}, every overload merged. */
  static synchronized Sites of(JavaClass owner, String methodName) {
    return CACHE
        .computeIfAbsent(owner.getName(), n -> read(owner))
        .getOrDefault(methodName, new Sites());
  }

  /**
   * The string constants of {@code methodName} in {@code owner}, its lambdas included, every
   * overload merged: a literal, a compile-time constant inlined from a field, and the constant
   * parts of a concatenation. A string built at run time from something else is not here.
   */
  static synchronized Set<String> stringConstants(JavaClass owner, String methodName) {
    return STRINGS
        .computeIfAbsent(owner.getName(), n -> readStrings(owner))
        .getOrDefault(methodName, Set.of());
  }

  /** True when the call is made from a lambda of the method and from no direct statement of it. */
  static boolean onlyInsideLambdas(JavaClass owner, String methodName, Target target) {
    Sites sites = of(owner, methodName);
    return sites.inLambdas().contains(target) && !sites.direct().contains(target);
  }

  /**
   * The types the method reference to {@code target} is taken on in {@code methodName}, lambdas
   * included: the type of the bound object ({@code settingRepository::delete}) or of the first
   * parameter of the functional interface ({@code SettingRepository::delete}). Empty for a static
   * reference.
   */
  static Set<String> receiversOf(JavaClass owner, String methodName, Target target) {
    return new HashSet<>(receiverList(owner, methodName, target));
  }

  /**
   * The type of each reference to {@code target} in {@code methodName}, one entry per bytecode
   * reference, so the same type repeats when the method refers to it twice. A reference whose
   * receiver cannot be read has no entry.
   */
  static List<String> receiverList(JavaClass owner, String methodName, Target target) {
    return of(owner, methodName).referenceReceivers().getOrDefault(target, List.of());
  }

  static boolean directlyCalls(JavaClass owner, String methodName, Target target) {
    return of(owner, methodName).direct().contains(target);
  }

  private static Map<String, Sites> read(JavaClass owner) {
    String resource = owner.getName().replace('.', '/') + ".class";
    Map<String, Set<Target>> bodies = new HashMap<>();
    Map<String, String> creatorOfLambda = new HashMap<>();
    Map<String, Set<Target>> referencedBy = new HashMap<>();
    Map<String, Map<Target, List<String>>> receiversBy = new HashMap<>();
    try (InputStream in = owner.reflect().getClassLoader().getResourceAsStream(resource)) {
      if (in == null) {
        return Map.of();
      }
      new ClassReader(in)
          .accept(
              new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(
                    int access, String name, String descriptor, String signature, String[] ex) {
                  Set<Target> body = bodies.computeIfAbsent(name, n -> new HashSet<>());
                  return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(
                        int opcode, String ownerName, String callee, String desc, boolean itf) {
                      body.add(new Target(ownerName.replace('/', '.'), callee));
                    }

                    @Override
                    public void visitInvokeDynamicInsn(
                        String callee, String desc, Handle bsm, Object... bsmArgs) {
                      for (Object arg : bsmArgs) {
                        if (!(arg instanceof Handle handle)) {
                          continue;
                        }
                        if (handle.getName().startsWith("lambda$")
                            && handle.getOwner().equals(resource.replace(".class", ""))) {
                          creatorOfLambda.put(handle.getName(), name);
                        } else if (!handle.getName().startsWith("<")) {
                          // A method reference (this::initInstanceId) runs later, like a lambda
                          // body: it is a call made by the creator, but not a direct one.
                          Target reference =
                              new Target(handle.getOwner().replace('/', '.'), handle.getName());
                          referencedBy.computeIfAbsent(name, n -> new HashSet<>()).add(reference);
                          String receiver = receiverOf(handle, desc, bsmArgs);
                          if (receiver != null) {
                            receiversBy
                                .computeIfAbsent(name, n -> new HashMap<>())
                                .computeIfAbsent(reference, t -> new ArrayList<>())
                                .add(receiver);
                          }
                        }
                      }
                    }
                  };
                }
              },
              ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    } catch (IOException e) {
      throw new IllegalStateException("cannot read " + resource, e);
    }
    Map<String, Sites> sites = new HashMap<>();
    bodies.forEach(
        (name, calls) -> {
          if (name.startsWith("lambda$")) {
            sites
                .computeIfAbsent(topCreator(name, creatorOfLambda), n -> new Sites())
                .inLambdas()
                .addAll(calls);
          } else {
            sites.computeIfAbsent(name, n -> new Sites()).direct().addAll(calls);
          }
        });
    referencedBy.forEach(
        (creator, refs) ->
            sites
                .computeIfAbsent(topCreator(creator, creatorOfLambda), n -> new Sites())
                .inLambdas()
                .addAll(refs));
    receiversBy.forEach(
        (creator, receivers) ->
            receivers.forEach(
                (reference, types) ->
                    sites
                        .computeIfAbsent(topCreator(creator, creatorOfLambda), n -> new Sites())
                        .referenceReceivers()
                        .computeIfAbsent(reference, t -> new ArrayList<>())
                        .addAll(types)));
    return sites;
  }

  /**
   * The class a method reference is taken on. A bound reference ({@code repo::delete}) captures the
   * object, so the first argument of the call-site descriptor is its static type; an unbound one
   * ({@code Repo::delete}) takes it as the first parameter of the instantiated method type.
   */
  private static String receiverOf(Handle handle, String descriptor, Object[] bsmArgs) {
    boolean onAnObject =
        handle.getTag() == Opcodes.H_INVOKEVIRTUAL
            || handle.getTag() == Opcodes.H_INVOKEINTERFACE
            || handle.getTag() == Opcodes.H_INVOKESPECIAL;
    if (!onAnObject) {
      return null;
    }
    Type[] captured = Type.getArgumentTypes(descriptor);
    if (captured.length > 0) {
      return captured[0].getSort() == Type.OBJECT ? captured[0].getClassName() : null;
    }
    if (bsmArgs.length > 2 && bsmArgs[2] instanceof Type instantiated) {
      Type[] parameters = instantiated.getArgumentTypes();
      if (parameters.length > 0 && parameters[0].getSort() == Type.OBJECT) {
        return parameters[0].getClassName();
      }
    }
    return null;
  }

  private static Map<String, Set<String>> readStrings(JavaClass owner) {
    String resource = owner.getName().replace('.', '/') + ".class";
    Map<String, Set<String>> bodies = new HashMap<>();
    Map<String, String> creatorOfLambda = new HashMap<>();
    try (InputStream in = owner.reflect().getClassLoader().getResourceAsStream(resource)) {
      if (in == null) {
        return Map.of();
      }
      new ClassReader(in)
          .accept(
              new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(
                    int access, String name, String descriptor, String signature, String[] ex) {
                  Set<String> body = bodies.computeIfAbsent(name, n -> new HashSet<>());
                  return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitLdcInsn(Object value) {
                      if (value instanceof String text) {
                        body.add(text);
                      }
                    }

                    @Override
                    public void visitInvokeDynamicInsn(
                        String callee, String desc, Handle bsm, Object... bsmArgs) {
                      for (Object arg : bsmArgs) {
                        if (arg instanceof String recipe) {
                          // The recipe of a string concatenation holds its constant parts.
                          body.add(recipe);
                        } else if (arg instanceof Handle handle
                            && handle.getName().startsWith("lambda$")
                            && handle.getOwner().equals(resource.replace(".class", ""))) {
                          creatorOfLambda.put(handle.getName(), name);
                        }
                      }
                    }
                  };
                }
              },
              ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    } catch (IOException e) {
      throw new IllegalStateException("cannot read " + resource, e);
    }
    Map<String, Set<String>> strings = new HashMap<>();
    bodies.forEach(
        (name, constants) ->
            strings
                .computeIfAbsent(
                    name.startsWith("lambda$") ? topCreator(name, creatorOfLambda) : name,
                    n -> new HashSet<>())
                .addAll(constants));
    return strings;
  }

  /** A lambda created in a lambda belongs to the method that encloses both. */
  private static String topCreator(String lambda, Map<String, String> creatorOfLambda) {
    String current = lambda;
    for (int guard = 0; guard < 32 && current.startsWith("lambda$"); guard++) {
      String next = creatorOfLambda.get(current);
      if (next == null) {
        break;
      }
      current = next;
    }
    return current;
  }
}
