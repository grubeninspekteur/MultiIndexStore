package com.github.multiindexstore;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * An object store that can be indexed by multiple keys. Keys must not change their equals/hashCode
 * values during the lifetime of their usage. Values can be mutable, but the {@link #add(Object)}
 * method must be called after changing the values of an object to reindex.
 *
 * <p>Instances are thread-safe.
 *
 * @param <V> The type of value this store holds.
 */
public final class MultiIndexStore<V> {

  private final Set<V> allValues = Util.newIdentityHashSet();

  private final Map<Index<?, V>, Map<?, Set<V>>> indexedValuesByIndex = new HashMap<>();

  private final Map<V, Set<CurrentKeyMapping<?, V>>> reverseIndexMap = new IdentityHashMap<>();

  private final ReadWriteLock lock = new ReentrantReadWriteLock();

  // TODO javadoc
  public boolean add(V value) {
    Objects.requireNonNull(value, "value");

    return writeGuard(() -> {
          if (contains(value)) {
            return false;
          } else {
            insert(value);
            return true;
          }
        }
    );
  }

  // TODO javadoc
  public boolean update(V value) {
    Objects.requireNonNull(value, "value");

    return writeGuard(() -> {
      if (contains(value)) {
        reindex(value);
        return true;
      } else {
        return false;
      }
    });
  }

  // TODO javadoc
  public boolean contains(V value) {
    return writeGuard(() -> allValues.contains(value));
  }

  // TODO javadoc
  public <K> Optional<V> get(Index.Unique<K, V> index, K key) {
    return readGuard(() -> getByIndex(index, key).stream().findAny());
  }

  // TODO javadoc
  public <K> Set<V> get(Index.NonUnique<K, V> index, K key) {
    return readGuard(() -> {
      var resultSet = createSet(getByIndex(index, key));
      return Collections.unmodifiableSet(resultSet);
    });
  }

  // TODO javadoc

  public boolean remove(V value) {
    return writeGuard(() -> {
      if (!contains(value)) {
        return false;
      }

      removeFromAllIndices(value);
      allValues.remove(value);
      return true;
    });
  }
  // TODO javadoc

  public <K> Index.Unique<K, V> createUniqueIndex(Function<V, @Nullable K> keyMapper) {
    Objects.requireNonNull(keyMapper);
    return writeGuard(() -> {
          var index = new Index.Unique<>(keyMapper);
          createIndex(index);
          return index;
        }
    );
  }

  // TODO javadoc
  public <K> Index.NonUnique<K, V> createIndex(Function<V, @Nullable K> keyMapper) {
    Objects.requireNonNull(keyMapper);
    return writeGuard(() -> {
      var index = new Index.NonUnique<>(keyMapper);
      createIndex(index);
      return index;
    });
  }

  private <K> Set<V> createSet(Set<V> value) {
    Set<V> resultSet = Util.newIdentityHashSet();
    resultSet.addAll(value);
    return resultSet;
  }

  private <K> Set<V> getByIndex(Index<K, V> index, K key) {
    Objects.requireNonNull(index, "index");
    Objects.requireNonNull(key, "key");

    if (!indexedValuesByIndex.containsKey(index)) {
      throw new IllegalArgumentException("Provided index is not a member of this store");
    }

    return indexedValuesByIndex.get(index).getOrDefault(key, Set.of());
  }

  private void insert(V value) {
    allValues.add(value);
    updateAllIndices(value);
  }

  private void updateAllIndices(V value) {
    for (Index<?, V> index : indexedValuesByIndex.keySet()) {
      index(value, index);
    }
  }

  private void index(V value, Index<?, V> index) {
    Object key = index.getKey(value);

    if (key != null) {
      Map<Object, Set<V>> valuesByKey = (Map<Object, Set<V>>) indexedValuesByIndex.get(index);

      switch (index) {
        case Index.Unique u -> {
          Set<V> existingValue = valuesByKey.get(key);
          if (existingValue != null && !existingValue.contains(value)) {
            remove(existingValue.iterator().next());
          }
          if (existingValue == null || !existingValue.contains(value)) {
            valuesByKey.put(key, createSet(Set.of(value)));
          }
        }
        case Index.NonUnique n -> {
          valuesByKey.computeIfAbsent(key, (__) -> Util.newIdentityHashSet());
          valuesByKey.get(key).add(value);
        }
      }

      addToReverseIndices(value, index, key);
    }
  }

  private <K> void addToReverseIndices(V value, Index<?, V> index, K key) {
    reverseIndexMap.computeIfAbsent(value, (__) -> new HashSet<>());
    reverseIndexMap.get(value).add(new CurrentKeyMapping<Object, V>((Index<Object, V>) index, key));
  }

  private void reindex(V value) {
    removeFromAllIndices(value);
    updateAllIndices(value);
  }

  private <K> void createIndex(Index<K, V> index) {
    indexedValuesByIndex.put(index, new HashMap<>());
    allValues.forEach(v -> index(v, index));
  }

  private void removeFromAllIndices(V value) {
    if (!reverseIndexMap.containsKey(value)) {
      return;
    }

    var indicesToRemoveValueFrom = reverseIndexMap.getOrDefault(value, Set.of());

    for (CurrentKeyMapping<?, V> currentKeyMapping : indicesToRemoveValueFrom) {
      Index<?, V> index = currentKeyMapping.index();
      Object oldKey = currentKeyMapping.key();

      switch (index) {
        case Index.Unique u -> indexedValuesByIndex.get(index).remove(oldKey);
        case Index.NonUnique n -> {
          Set<V> valuesForKey = indexedValuesByIndex.get(index).get(oldKey);

          if (valuesForKey != null) {
            valuesForKey.remove(value);
          }
        }
      }
    }

    reverseIndexMap.remove(value);
  }

  private <T> T readGuard(Supplier<T> supplier) {
    try {
      lock.readLock().lock();
      return supplier.get();
    } finally {
      lock.readLock().unlock();
    }
  }

  private <T> T writeGuard(Supplier<T> supplier) {
    try {
      lock.writeLock().lock();
      return supplier.get();
    } finally {
      lock.writeLock().unlock();
    }
  }

  private record CurrentKeyMapping<K, V>(Index<K, V> index, K key) {

  }
}
