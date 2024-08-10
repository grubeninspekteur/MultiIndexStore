package com.github.multiindexstore;

import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

public abstract class AbstractIndex<K, V> {

  private final Function<V, @Nullable K> keyMapper;

  protected AbstractIndex(Function<V, @Nullable K> keyMapper) {
    this.keyMapper = keyMapper;
  }

  public @Nullable K getKey(V value) {
    Objects.requireNonNull(value, "value");
    return keyMapper.apply(value);
  }
}
