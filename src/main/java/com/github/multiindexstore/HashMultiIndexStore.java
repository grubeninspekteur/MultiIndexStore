package com.github.multiindexstore;

import com.github.multiindexstore.internal.AbstractMultiIndexStore;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A {@link MultiIndexStore} that considers values equal if they have the same {@link Object#hashCode()} and are
 * {@link Object#equals(Object)}. Values must be immutable, otherwise consistency of the indices is not guaranteed.
 * Values should also not change their hash code over their lifetime in the store.
 * <p>
 * Instances are thread-safe provided that keys are immutable.
 *
 * @param <V> type of value stored
 */
public final class HashMultiIndexStore<V> extends AbstractMultiIndexStore<V> {

  /**
   * Returns true if the given value is already present in the store. A value is considered present if it is equal to an
   * existing one according to {@link Object#equals(Object)}.
   *
   * @param value the value to test
   * @return whether the value is already contained in the store
   * @throws NullPointerException if the value is null
   */
  @Override
  public boolean contains(V value) {
    return super.contains(value);
  }

  @Override
  protected <K, E> Map<K, E> createMapWithStoreContainsSemantics() {
    return new HashMap<>();
  }

  @Override
  protected <E> Set<E> createSetWithStoreContainsSemantics() {
    return new HashSet<>();
  }
}
