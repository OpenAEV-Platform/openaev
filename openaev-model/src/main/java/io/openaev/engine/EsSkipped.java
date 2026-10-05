package io.openaev.engine;

/**
 * A row a {@link Handler} read after the indexing cursor but does not index: the bulk deletes any
 * document carrying its id, and the cursor moves past it as past an indexed row, so rows that are
 * never indexed cannot hold the cursor of their model on the same instant forever.
 */
public interface EsSkipped {}
