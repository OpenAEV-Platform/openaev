package io.openaev.architecture.background_fixtures;

/**
 * Near-miss for the inline hand-off blind spot in the call scan: a concrete bean whose only
 * detached hand-off, a {@code CompletableFuture.supplyAsync}, lives in the body of a method
 * INHERITED from {@link AbstractInlineHandoffParentFixture} and is not redeclared here. It has no
 * executor field, no annotation and no self-declared hand-off, so a scan of {@code
 * clazz.getMethodCallsFromSelf()} alone misses it; the scan must read the inherited method body.
 * Test-scope only; imported explicitly (with its parent) by the detection test.
 */
public class InheritedInlineHandoffFixture extends AbstractInlineHandoffParentFixture {}
