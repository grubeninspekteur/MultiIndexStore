package com.github.multiindexstore.internal;

import com.github.multiindexstore.AbstractIndex;
import com.github.multiindexstore.Index;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

public class NonUniqueIndexImpl<K, V> extends AbstractIndex<K, V> implements Index.NonUnique<K, V> {

  public NonUniqueIndexImpl(Function<V, @Nullable K> keyMapper) {
    super(keyMapper);
  }
}
