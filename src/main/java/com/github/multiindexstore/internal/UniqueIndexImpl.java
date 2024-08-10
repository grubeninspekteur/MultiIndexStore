package com.github.multiindexstore.internal;

import com.github.multiindexstore.AbstractIndex;
import com.github.multiindexstore.Index;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

public class UniqueIndexImpl<K, V> extends AbstractIndex<K, V> implements Index.Unique<K, V> {

  public UniqueIndexImpl(Function<V, @Nullable K> keyMapper) {
    super(keyMapper);
  }
}
