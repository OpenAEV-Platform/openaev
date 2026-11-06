package io.openaev.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.Handle;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;

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

  /** Targets called directly in a method body, and targets called only from its lambdas. */
  record Sites(Set<Target> direct, Set<Target> inLambdas) {
    Sites() {
      this(new HashSet<>(), new HashSet<>());
    }
  }

  private static final Map<String, Map<String, Sites>> CACHE = new HashMap<>();

  private CallSites() {}

  /** The sites of {@code methodName} in {@code owner}, every overload merged. */
  static synchronized Sites of(JavaClass owner, String methodName) {
    return CACHE
        .computeIfAbsent(owner.getName(), n -> read(owner))
        .getOrDefault(methodName, new Sites());
  }

  /** True when the call is made from a lambda of the method and from no direct statement of it. */
  static boolean onlyInsideLambdas(JavaClass owner, String methodName, Target target) {
    Sites sites = of(owner, methodName);
    return sites.inLambdas().contains(target) && !sites.direct().contains(target);
  }

  static boolean directlyCalls(JavaClass owner, String methodName, Target target) {
    return of(owner, methodName).direct().contains(target);
  }

  private static Map<String, Sites> read(JavaClass owner) {
    String resource = owner.getName().replace('.', '/') + ".class";
    Map<String, Set<Target>> bodies = new HashMap<>();
    Map<String, String> creatorOfLambda = new HashMap<>();
    Map<String, Set<Target>> referencedBy = new HashMap<>();
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
                          referencedBy
                              .computeIfAbsent(name, n -> new HashSet<>())
                              .add(
                                  new Target(
                                      handle.getOwner().replace('/', '.'), handle.getName()));
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
    return sites;
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
