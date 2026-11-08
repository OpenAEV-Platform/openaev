package io.openaev.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import io.openaev.aop.AccessControl;
import io.openaev.database.model.ResourceType;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.Handle;
import org.springframework.asm.Label;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.asm.Type;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * The by-id scope guard rule: an endpoint whose {@link AccessControl} names an inject (or an atomic
 * testing, which is an inject) as its resource must load <em>that inject</em> inside the request's
 * tenant scope, before it writes anything. {@code PermissionService#checkPermission} lets an admin
 * or a BYPASS caller through without resolving the resource's parent, and a primary-key load is not
 * narrowed by the v1 tenant filter, so the guard is the only tenant check those callers meet.
 *
 * <p>A guard is one of the three scoped lookups of {@code InjectService}: {@code inject(TxCtx,
 * String)}, {@code findInScope} and {@code existsInScope}. Every other guard of the controllers
 * ({@code requireInjectInScope}, {@code requireAtomicTestingInScope}, {@code
 * requireInjectInScenario}, {@code requireInjectInSimulation}, {@code
 * AtomicTestingService#existsInScope}) ends in one of them.
 *
 * <p>The rule reads the bytecode of the endpoint, and of the methods it hands the resource id to,
 * and asks two things:
 *
 * <ul>
 *   <li><b>Data flow</b>: the parameter named by {@code resourceId} (a {@code #name} expression) is
 *       an argument of the guard, in the endpoint or in a method it passes that parameter to, to a
 *       depth of {@link #MAX_CALL_DEPTH} calls. A guard called on another id does not count.
 *   <li><b>Order</b>: no repository write and no service write is called before that guard, in the
 *       endpoint or in the delegate that holds it. A write is a {@code save}, {@code delete} or
 *       {@code flush} of a {@code *Repository}, or a call of a {@code *Service} of {@code
 *       io.openaev} whose name starts with a verb of {@link #WRITE_VERBS}.
 * </ul>
 *
 * <p>Denominator: an endpoint is a method with an HTTP mapping and an {@code @AccessControl} whose
 * {@code resourceType} is INJECT, ATOMIC_TESTING or INJECT_SECRET and whose {@code resourceId} is
 * set. An endpoint typed with another resource that loads an inject by id (a scenario's inject, an
 * implant's JOB callback) is not seen.
 *
 * <p>What the analysis does not see: an argument that is not a plain parameter, a field or a
 * constant (an expression, an array, a copy of the id into another local variable) is followed only
 * as "some argument received it"; a guard called through an interface, a method reference or by
 * reflection; a write whose name is not in the lists above; a conditional guard (called on one
 * branch only) counts as a guard.
 */
final class InjectEndpointScopeGuardRules {

  private static final Set<ResourceType> INJECT_TYPES =
      Set.of(ResourceType.INJECT, ResourceType.ATOMIC_TESTING, ResourceType.INJECT_SECRET);

  private static final String INJECT_SERVICE = "io/openaev/rest/inject/service/InjectService";

  private static final Set<String> GUARD_NAMES = Set.of("inject", "findInScope", "existsInScope");

  private static final String GUARD_PARAMETERS = "(Lio/openaev/context/TxCtx;Ljava/lang/String;)";

  private static final String PERMISSION_SERVICE = "io/openaev/service/PermissionService";

  /** Service methods named with one of these verbs write. */
  static final Pattern WRITE_VERBS =
      Pattern.compile(
          "^(create|save|update|delete|remove|add|set|duplicate|launch|relaunch|trigger|execute|"
              + "import|clear|replace|mark|toggle|send|start|stop|cancel|attach|detach|assign|"
              + "apply)[A-Z0-9_]?.*");

  private static final Pattern REPOSITORY_WRITES =
      Pattern.compile("^(save|delete|flush|update|remove).*");

  /**
   * Controller, guard helper, owning service, {@code InjectService}: the deepest chain of the
   * controllers today, with one call of slack.
   */
  static final int MAX_CALL_DEPTH = 4;

  private InjectEndpointScopeGuardRules() {}

  static DescribedPredicate<JavaMethod> byIdInjectEndpoints() {
    return new DescribedPredicate<>(
        "are by-id endpoints typed INJECT, ATOMIC_TESTING or INJECT_SECRET") {
      @Override
      public boolean test(JavaMethod method) {
        if (!isHttpEndpoint(method) || !method.isAnnotatedWith(AccessControl.class)) {
          return false;
        }
        AccessControl accessControl = method.getAnnotationOfType(AccessControl.class);
        return INJECT_TYPES.contains(accessControl.resourceType())
            && !accessControl.resourceId().isBlank();
      }
    };
  }

  static ArchCondition<JavaMethod> guardTheResourceIdBeforeAnyWrite() {
    return new ArchCondition<>(
        "pass their resource id to InjectService#inject(TxCtx, String), #findInScope or"
            + " #existsInScope, before any write") {
      @Override
      public void check(JavaMethod endpoint, ConditionEvents events) {
        Outcome outcome = outcomeOf(endpoint);
        if (outcome != Outcome.GUARDED) {
          events.add(
              SimpleConditionEvent.violated(
                  endpoint,
                  endpoint.getOwner().getSimpleName()
                      + "#"
                      + endpoint.getName()
                      + (outcome == Outcome.WRITE_FIRST
                          ? " writes before it guards its resource id"
                          : " never passes its resource id to a scope guard")
                      + ": an admin or BYPASS caller meets no tenant check on it ("
                      + endpoint.getSourceCodeLocation()
                      + ")"));
        }
      }
    };
  }

  enum Outcome {
    /** The resource id reaches a scope guard, and nothing writes before it. */
    GUARDED,
    /** A write is called before the guard. */
    WRITE_FIRST,
    /** No scope guard receives the resource id. */
    NONE
  }

  static Outcome outcomeOf(JavaMethod endpoint) {
    String idName = endpoint.getAnnotationOfType(AccessControl.class).resourceId().strip();
    if (!idName.startsWith("#")) {
      return Outcome.NONE;
    }
    Body body =
        Body.of(endpoint.getOwner().getName(), endpoint.getName(), endpoint.getDescriptor());
    Integer slot = body.slotOfParameter(idName.substring(1));
    if (slot == null) {
      return Outcome.NONE;
    }
    return analyse(body, Set.of(slot), 0, new HashSet<>());
  }

  private static Outcome analyse(Body body, Set<Integer> idSlots, int depth, Set<String> visiting) {
    if (!visiting.add(body.key())) {
      return Outcome.NONE;
    }
    try {
      for (Call call : body.calls()) {
        List<Integer> idArguments = call.argumentsCarrying(idSlots);
        if (isScopeGuard(call) && idArguments.contains(1)) {
          return Outcome.GUARDED;
        }
        if (!idArguments.isEmpty() && followed(call) && depth + 1 < MAX_CALL_DEPTH) {
          Body callee = Body.of(call.owner(), call.name(), call.descriptor());
          Set<Integer> calleeSlots = callee.slotsOfArguments(idArguments);
          if (!calleeSlots.isEmpty()) {
            Outcome outcome = analyse(callee, calleeSlots, depth + 1, visiting);
            if (outcome != Outcome.NONE) {
              return outcome;
            }
          }
        }
        if (isWrite(call)) {
          return Outcome.WRITE_FIRST;
        }
      }
      return Outcome.NONE;
    } finally {
      visiting.remove(body.key());
    }
  }

  private static boolean isScopeGuard(Call call) {
    return call.owner().equals(INJECT_SERVICE)
        && GUARD_NAMES.contains(call.name())
        && call.descriptor().startsWith(GUARD_PARAMETERS);
  }

  private static boolean followed(Call call) {
    return call.owner().startsWith("io/openaev/") && !call.owner().equals(PERMISSION_SERVICE);
  }

  private static boolean isWrite(Call call) {
    String simpleOwner = call.owner().substring(call.owner().lastIndexOf('/') + 1);
    if (simpleOwner.endsWith("Repository")) {
      return REPOSITORY_WRITES.matcher(call.name()).matches();
    }
    return call.owner().startsWith("io/openaev/")
        && simpleOwner.endsWith("Service")
        && WRITE_VERBS.matcher(call.name()).matches();
  }

  private static boolean isHttpEndpoint(JavaMethod method) {
    return method.isAnnotatedWith(GetMapping.class)
        || method.isAnnotatedWith(PostMapping.class)
        || method.isAnnotatedWith(PutMapping.class)
        || method.isAnnotatedWith(DeleteMapping.class)
        || method.isAnnotatedWith(PatchMapping.class)
        || method.isAnnotatedWith(RequestMapping.class);
  }

  // -- BYTECODE --

  /**
   * A call, in the order of the method body, with what each argument was: the local variable it was
   * loaded from, or -1 for anything else.
   */
  private record Call(String owner, String name, String descriptor, List<Integer> argumentSlots) {

    /** The positions of the arguments loaded from one of {@code slots}. */
    List<Integer> argumentsCarrying(Set<Integer> slots) {
      List<Integer> positions = new ArrayList<>();
      for (int i = 0; i < argumentSlots.size(); i++) {
        if (slots.contains(argumentSlots.get(i))) {
          positions.add(i);
        }
      }
      return positions;
    }
  }

  /** What the rule reads of a method: its calls, and where its parameters live. */
  private static final class Body {

    private static final Map<String, Body> CACHE = new HashMap<>();

    private final String key;
    private final boolean isStatic;
    private final String descriptor;
    private final List<String> parameterNames = new ArrayList<>();
    private final Map<String, Integer> slotsByName = new HashMap<>();
    private final List<Call> calls = new ArrayList<>();

    private Body(String key, boolean isStatic, String descriptor) {
      this.key = key;
      this.isStatic = isStatic;
      this.descriptor = descriptor;
    }

    static Body of(String owner, String name, String descriptor) {
      String binaryOwner = owner.replace('.', '/');
      return CACHE.computeIfAbsent(
          binaryOwner + "#" + name + descriptor, key -> read(binaryOwner, name, descriptor, key));
    }

    private static Body read(String owner, String name, String descriptor, String key) {
      Body[] found = new Body[1];
      try {
        new ClassReader(owner)
            .accept(
                new ClassVisitor(Opcodes.ASM9) {
                  @Override
                  public MethodVisitor visitMethod(
                      int access,
                      String methodName,
                      String methodDescriptor,
                      String s,
                      String[] e) {
                    if (!methodName.equals(name) || !methodDescriptor.equals(descriptor)) {
                      return null;
                    }
                    Body body = new Body(key, (access & Opcodes.ACC_STATIC) != 0, descriptor);
                    found[0] = body;
                    return body.visitor();
                  }
                },
                ClassReader.SKIP_FRAMES);
      } catch (IOException e) {
        throw new IllegalStateException("Cannot read the bytecode of " + owner, e);
      }
      // A method that is not in the class (inherited, abstract): nothing to follow.
      return found[0] != null ? found[0] : new Body(key, true, descriptor);
    }

    String key() {
      return key;
    }

    List<Call> calls() {
      return calls;
    }

    /** The local variable slot of the parameter called {@code parameterName}. */
    Integer slotOfParameter(String parameterName) {
      return slotsByName.get(parameterName);
    }

    /** The slots of the parameters at these positions. */
    Set<Integer> slotsOfArguments(List<Integer> positions) {
      Type[] types = Type.getArgumentTypes(descriptor);
      Set<Integer> slots = new HashSet<>();
      int slot = isStatic ? 0 : 1;
      for (int i = 0; i < types.length; i++) {
        if (positions.contains(i)) {
          slots.add(slot);
        }
        slot += types[i].getSize();
      }
      return slots;
    }

    private MethodVisitor visitor() {
      Type[] types = Type.getArgumentTypes(descriptor);
      int parameterSlotsEnd = (isStatic ? 0 : 1);
      for (Type type : types) {
        parameterSlotsEnd += type.getSize();
      }
      int slotsEnd = parameterSlotsEnd;
      return new MethodVisitor(Opcodes.ASM9) {
        // The values pushed and not yet consumed: a local variable slot, or -1.
        private final List<Integer> stack = new ArrayList<>();

        @Override
        public void visitParameter(String parameterName, int access) {
          parameterNames.add(parameterName);
          int slot = isStatic ? 0 : 1;
          for (int i = 0; i < parameterNames.size() - 1; i++) {
            slot += types[i].getSize();
          }
          slotsByName.putIfAbsent(parameterName, slot);
        }

        @Override
        public void visitLocalVariable(
            String variable, String d, String s, Label start, Label end, int index) {
          if (index < slotsEnd && !variable.equals("this")) {
            slotsByName.putIfAbsent(variable, index);
          }
        }

        @Override
        public void visitVarInsn(int opcode, int slot) {
          if (opcode >= Opcodes.ILOAD && opcode <= Opcodes.ALOAD) {
            stack.add(slot);
          } else if (opcode >= Opcodes.ISTORE && opcode <= Opcodes.ASTORE) {
            pop(1);
          }
        }

        @Override
        public void visitInsn(int opcode) {
          if (opcode == Opcodes.ACONST_NULL
              || (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.DCONST_1)) {
            stack.add(-1);
          } else if (opcode == Opcodes.DUP && !stack.isEmpty()) {
            stack.add(stack.getLast());
          } else if (opcode == Opcodes.POP) {
            pop(1);
          }
        }

        @Override
        public void visitJumpInsn(int opcode, Label label) {
          if (opcode >= Opcodes.IFEQ && opcode <= Opcodes.IFLE
              || opcode == Opcodes.IFNULL
              || opcode == Opcodes.IFNONNULL) {
            pop(1);
          } else if (opcode >= Opcodes.IF_ICMPEQ && opcode <= Opcodes.IF_ACMPNE) {
            pop(2);
          }
        }

        @Override
        public void visitIntInsn(int opcode, int operand) {
          stack.add(-1);
        }

        @Override
        public void visitLdcInsn(Object value) {
          stack.add(-1);
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
          if (opcode == Opcodes.NEW) {
            stack.add(-1);
          }
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String fieldName, String d) {
          if (opcode == Opcodes.GETFIELD) {
            pop(1);
            stack.add(-1);
          } else if (opcode == Opcodes.GETSTATIC) {
            stack.add(-1);
          } else {
            pop(opcode == Opcodes.PUTFIELD ? 2 : 1);
          }
        }

        @Override
        public void visitMethodInsn(
            int opcode, String owner, String methodName, String methodDescriptor, boolean itf) {
          call(owner, methodName, methodDescriptor, opcode != Opcodes.INVOKESTATIC);
        }

        @Override
        public void visitInvokeDynamicInsn(
            String indyName, String indyDescriptor, Handle bootstrap, Object... arguments) {
          Handle target = null;
          for (Object argument : arguments) {
            if (argument instanceof Handle handle) {
              target = handle;
            }
          }
          // The captured values are the first arguments of the lambda body, the receiver first.
          int captured = Type.getArgumentTypes(indyDescriptor).length;
          List<Integer> slots = takeLast(captured);
          if (target != null) {
            boolean instance =
                target.getTag() != Opcodes.H_INVOKESTATIC
                    && target.getTag() != Opcodes.H_NEWINVOKESPECIAL;
            List<Integer> arguments2 =
                instance && !slots.isEmpty() ? slots.subList(1, slots.size()) : slots;
            calls.add(
                new Call(
                    target.getOwner(),
                    target.getName(),
                    target.getDesc(),
                    new ArrayList<>(arguments2)));
          }
          stack.add(-1);
        }

        private void call(
            String owner, String methodName, String methodDescriptor, boolean instance) {
          int count = Type.getArgumentTypes(methodDescriptor).length;
          List<Integer> arguments = takeLast(count);
          if (instance) {
            pop(1);
          }
          calls.add(new Call(owner, methodName, methodDescriptor, arguments));
          if (Type.getReturnType(methodDescriptor).getSort() != Type.VOID) {
            stack.add(-1);
          }
        }

        private List<Integer> takeLast(int count) {
          List<Integer> taken = new ArrayList<>();
          for (int i = 0; i < count; i++) {
            taken.add(0, stack.isEmpty() ? -1 : stack.removeLast());
          }
          return taken;
        }

        private void pop(int count) {
          for (int i = 0; i < count && !stack.isEmpty(); i++) {
            stack.removeLast();
          }
        }
      };
    }
  }
}
