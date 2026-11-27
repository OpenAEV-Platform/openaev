package io.openaev.service.catalog.merge;

/** How the user settles a conflict of a three-way merge, for a payload or a scenario. */
public enum MergeChoice {
  KEEP_MINE {
    @Override
    public <T> T value(T applied, T local, T edit) {
      return local;
    }
  },
  TAKE_APPLIED {
    @Override
    public <T> T value(T applied, T local, T edit) {
      return applied;
    }
  },
  EDIT {
    @Override
    public boolean fits(boolean editable, Object edit) {
      return editable && edit != null;
    }

    @Override
    public <T> T value(T applied, T local, T edit) {
      return edit;
    }
  };

  public static final String MISSING = "Choose a side for each conflict";

  /** A choice that does not fit leaves the conflict open. */
  public boolean fits(boolean editable, Object edit) {
    return true;
  }

  public abstract <T> T value(T applied, T local, T edit);
}
