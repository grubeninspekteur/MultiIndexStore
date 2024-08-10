package com.github.multiindexstore;

import org.jspecify.annotations.Nullable;

/**
 * An index in a {@link MultiIndexStore}. Indices are only usable by the store that created them. The key mapping
 * function produces a key under which a value is stored.
 *
 * @param <K> the type of key produced by the key mapper
 * @param <V> the type of value stored in the store this index belongs to
 */
public sealed interface Index<K, V> {

  /**
   * Returns the key for a given value.
   *
   * @param value the value
   * @return the key, can be null
   */
  @Nullable
  K getKey(V value);

  /**
   * A unique index stores at most one object per key. Inserting an object with the same unique index key as an existing
   * object will remove the existing object from the store.
   *
   * @param <K> the type of key produced by the key mapper
   * @param <V> the type of value stored in the store this index belongs to
   */
  non-sealed interface Unique<K, V> extends Index<K, V> {

  }

  /**
   * A non-unique index stores multiple objects per key.
   *
   * @param <K> the type of key produced by the key mapper
   * @param <V> the type of value stored in the store this index belongs to
   */
  non-sealed interface NonUnique<K, V> extends Index<K, V> {

  }
}
