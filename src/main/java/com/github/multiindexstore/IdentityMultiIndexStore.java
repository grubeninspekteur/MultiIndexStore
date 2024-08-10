package com.github.multiindexstore;

import com.github.multiindexstore.internal.AbstractMultiIndexStore;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A {@link MultiIndexStore} that considers values equal if they are referring to the same object, using an
 * {@link IdentityHashMap} internally. Multiple values can be stored even if they return the same values for
 * {@link Object#hashCode()} and {@link Object#equals(Object)}. Existing values can be up
 * <p>
 * Instances are thread-safe.
 *
 * @param <V> type of value stored
 */
public final class IdentityMultiIndexStore<V> extends AbstractMultiIndexStore<V> implements
    MutableValueMultiIndexStore<V> {

  @Override
  public boolean update(V value) {
    Objects.requireNonNull(value, "value");

    return guard.writeGuard(() -> {
      if (contains(value)) {
        reindex(value);
        return true;
      } else {
        return false;
      }
    });
  }

  private void reindex(V value) {
    removeFromAllIndices(value);
    updateAllIndices(value);
  }

  @Override
  protected <K, E> Map<K, E> createMapWithStoreContainsSemantics() {
    return new IdentityHashMap<>();
  }

  @Override
  protected <E> Set<E> createSetWithStoreContainsSemantics() {
    return Collections.newSetFromMap(new IdentityHashMap<>());
  }
}
