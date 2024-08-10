package com.github.multiindexstore;

import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * An object store that allows indexing its elements by multiple indices. Objects can be added, removed, and queried by
 * index.
 *
 * @param <V>
 */
public interface MultiIndexStore<V> {

  /**
   * Adds an object if the store does not {@link #contains(Object) contain} it already. Containment semantics vary by
   * implementation. See the documentation on {@link #contains(Object)} for details.
   *
   * @param value the (potentially new) value
   * @return true if the value was new and added, false if the value was already present
   * @throws NullPointerException if the value is null
   */
  boolean insert(V value);

  /**
   * Returns true if the given value is already present in the store.
   * <p>
   * Whether a value is considered to be already contained is up to the implementation. For example, it could use
   * {@link Object#equals(Object)} and {@link Object#hashCode()} for the check, or object identity. Subclasses are
   * encouraged to overwrite this JavaDoc specifying the concrete strategy.
   *
   * @param value the value to test
   * @return whether the value is already contained in the store
   * @throws NullPointerException if the value is null
   */
  boolean contains(V value);

  /**
   * Returns the value stored under the given unique key, or {@link Optional#empty()} if the value is not present.
   *
   * @param index the index to look up
   * @param key   key value to look up, must not be null
   * @param <K>   type of the key
   * @return the single value stored under this unique key, or {@link Optional#empty()} if no value is stored for this
   * key
   * @throws UnknownIndexException if the given index was not created by this store
   * @throws NullPointerException  if any of the parameters is null
   */
  <K> Optional<V> findBy(Index.Unique<K, V> index, K key);

  /**
   * Returns the values stored under this key, or an empty set if no values are present.
   *
   * @param index the index to look up
   * @param key   key value to look up, must not be null
   * @param <K>   type of the key
   * @return a set of values stored under this key. The set is immutable.
   * @throws UnknownIndexException if the given index was not created by this store
   * @throws NullPointerException  if any of the parameters is null
   */
  <K> Set<V> findBy(Index.NonUnique<K, V> index, K key);

  /**
   * Removes a value from the store if it is {@link #contains(Object) contained}.
   *
   * @param value the value
   * @return true if the value was present and removed, false if the value was not present
   * @throws NullPointerException if the value is null
   */
  boolean remove(V value);

  /**
   * Creates a unique index for this store. Unique indices map to at most one value per key.
   * {@link #insert(Object) adding} an object with a unique key for which there is already an existing object found will
   * remove that existing object from the store entirely.
   * <p>
   * Creating a unique index in a store that already contains values has undefined semantics if there are multiple
   * values present that map to the same unique key. Implementations may throw an exception or just select one value to
   * keep arbitrarily.
   * <p>
   * Only indices created by this store can be used for {@link #findBy(Index.Unique, Object)}. You cannot use indices
   * from different stores, nor can you provide arbitrary index implementations.
   *
   * @param keyMapper a function describing how to extract the key represented by this index for a given value
   * @param <K>       type of the key
   * @return a unique index belonging to this store
   * @throws NullPointerException if the {@code keyMapper} is null
   */
  <K> Index.Unique<K, V> createUniqueIndex(Function<V, @Nullable K> keyMapper);

  /**
   * Creates a non-unique index for this store. Non-unique indices map to multiple values per key.
   * <p>
   * A non-unique index can be added if the store already contains values, in which case all existing values will be
   * indexed.
   * <p>
   * Only indices created by this store can be used for {@link #findBy(Index.NonUnique, Object)}. You cannot use indices
   * from different stores, nor can you provide arbitrary index implementations.
   *
   * @param keyMapper a function describing how to extract the key represented by this index for a given value
   * @param <K>       type of the key
   * @return a unique index belonging to this store
   * @throws NullPointerException if the {@code keyMapper} is null
   */
  <K> Index.NonUnique<K, V> createIndex(Function<V, @Nullable K> keyMapper);
}
