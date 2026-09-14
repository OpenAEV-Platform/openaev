package io.openaev.architecture.background_fixtures;

/**
 * Near-miss for the {@code getMethods()} blind spot: a concrete bean whose only background marker,
 * a {@code @PostConstruct}, is INHERITED from {@link AbstractPostConstructParentFixture} and not
 * redeclared here. {@code JavaClass.getMethods()} returns declared methods only, so it misses the
 * inherited marker; {@code getAllMethods()} sees it. Test-scope only; imported explicitly by the
 * detection test.
 */
public class InheritedPostConstructFixture extends AbstractPostConstructParentFixture {}
