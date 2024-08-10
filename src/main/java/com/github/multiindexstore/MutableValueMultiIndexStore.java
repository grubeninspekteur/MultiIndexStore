package com.github.multiindexstore;

/**
 * A {@link MultiIndexStore} that allows mutable values in the store. After updating a value, call
 * {@link #update(Object)} to update the indices.
 *
 * @param <V> type of the stored values
 */
public interface MutableValueMultiIndexStore<V> extends MultiIndexStore<V> {

  /**
   * Updates indices for the given values in the store after it has been mutated. This is equivalent to calling
   * {@link #contains(Object)}, {@link #remove(Object)} and then {@link #insert(Object)} with the given object, but as
   * an atomic operation.
   * <p>
   * <strong>Atomicity is only guaranteed for the indices of the map.</strong> Concurrent threads may see partially
   * updated
   * values (tearing) for the mutations you perform before calling this method.
   * <p>
   * If the value was not previously present in the store then it will not be inserted.
   *
   * @param value the value to update.
   * @return true if the value was present and updated, false if not.
   */
  boolean update(V value);
}
