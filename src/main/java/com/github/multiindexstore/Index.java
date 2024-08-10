package com.github.multiindexstore;

import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

public abstract sealed class Index<K, V> {

  private final Function<V, @Nullable K> keyMapper;

  Index(Function<V, @Nullable K> keyMapper) {
    this.keyMapper = keyMapper;
  }

  public @Nullable K getKey(V value) {
    Objects.requireNonNull(value, "value");
    return keyMapper.apply(value);
  }

  static final class Unique<K, V> extends Index<K, V> {

    Unique(Function<V, @Nullable K> keyMapper) {
      super(keyMapper);
    }
  }

  static final class NonUnique<K, V> extends Index<K, V> {

    NonUnique(Function<V, @Nullable K> keyMapper) {
      super(keyMapper);
    }
  }

}
