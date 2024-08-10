package com.github.multiindexstore.internal;

import com.github.multiindexstore.Index;
import com.github.multiindexstore.Index.NonUnique;
import com.github.multiindexstore.Index.Unique;
import com.github.multiindexstore.MultiIndexStore;
import com.github.multiindexstore.UnknownIndexException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * An abstract implementation of {@link MultiIndexStore}. Keys must not change their equals/hashCode values during the
 * lifetime of their usage.
 *
 * <p>Instances are thread-safe.
 *
 * @param <V> The type of value this store holds.
 */
public abstract class AbstractMultiIndexStore<V> implements MultiIndexStore<V> {

  protected final Map<Index<?, V>, Map<?, Set<V>>> indexedValuesByIndex = new HashMap<>();

  protected final Map<V, Set<CurrentKeyMapping<?, V>>> reverseIndexMap = createMapWithStoreContainsSemantics();

  protected final ReadWriteGuard guard = new ReadWriteGuard();

  @Override
  public boolean insert(V value) {
    Objects.requireNonNull(value, "value");

    return guard.writeGuard(() -> {
          if (contains(value)) {
            return false;
          } else {
            reverseIndexMap.put(value, new HashSet<>());
            updateAllIndices(value);
            return true;
          }
        }
    );
  }

  @Override
  public boolean contains(V value) {
    return guard.readGuard(() -> reverseIndexMap.containsKey(value));
  }

  @Override
  public <K> Optional<V> findBy(Index.Unique<K, V> index, K key) {
    return guard.readGuard(() -> getByIndex(index, key).stream().findAny());
  }

  @Override
  public <K> Set<V> findBy(Index.NonUnique<K, V> index, K key) {
    return guard.readGuard(() -> {
      var resultSet = createSetWithStoreContainsSemantics(getByIndex(index, key));
      return Collections.unmodifiableSet(resultSet);
    });
  }

  @Override
  public boolean remove(V value) {
    return guard.writeGuard(() -> {
      if (!contains(value)) {
        return false;
      }

      removeFromAllIndices(value);
      reverseIndexMap.remove(value);
      return true;
    });
  }

  @Override
  public <K> Index.Unique<K, V> createUniqueIndex(Function<V, @Nullable K> keyMapper) {
    Objects.requireNonNull(keyMapper);
    return guard.writeGuard(() -> {
          var index = new UniqueIndexImpl<>(keyMapper);
          createIndex(index);
          return index;
        }
    );
  }

  @Override
  public <K> Index.NonUnique<K, V> createIndex(Function<V, @Nullable K> keyMapper) {
    Objects.requireNonNull(keyMapper);
    return guard.writeGuard(() -> {
      var index = new NonUniqueIndexImpl<>(keyMapper);
      createIndex(index);
      return index;
    });
  }

  @Override
  public Set<V> values() {
    return guard.readGuard(
        () -> Collections.unmodifiableSet(createSetWithStoreContainsSemantics(reverseIndexMap.keySet())));
  }

  @Override
  public <K> Set<K> keySet(Index<K, V> index) {
    Objects.requireNonNull(index);
    return guard.readGuard(() -> {
      validateIndexExists(index);
      return Set.copyOf((Set<K>) indexedValuesByIndex.get(index).keySet());
    });
  }

  @Override
  public <K> Set<Entry<K, Set<V>>> entrySet(NonUnique<K, V> index) {
    Objects.requireNonNull(index);
    return guard.readGuard(() -> {
      validateIndexExists(index);
      Set<Entry<K, Set<V>>> entrySet = new HashSet<>();
      indexedValuesByIndex.get(index).entrySet()
          .forEach(entry -> entrySet.add(
              (Entry<K, Set<V>>) Map.entry(entry.getKey(), createSetWithStoreContainsSemantics(entry.getValue()))));
      return Collections.unmodifiableSet(entrySet);
    });
  }

  @Override
  public <K> Set<Entry<K, V>> entrySet(Unique<K, V> index) {
    Objects.requireNonNull(index);
    return guard.readGuard(() -> {
      validateIndexExists(index);
      Set<Entry<K, V>> entrySet = new HashSet<>();
      indexedValuesByIndex.get(index).entrySet()
          .forEach(entry -> {
            var singleValueSet = entry.getValue();
            entrySet.add(
                (Entry<K, V>) Map.entry(entry.getKey(), singleValueSet.iterator().next()));
          });
      return Collections.unmodifiableSet(entrySet);
    });
  }

  @Override
  public void clear() {
    guard.writeGuard(() -> {
      reverseIndexMap.clear();
      indexedValuesByIndex.keySet().forEach(index -> indexedValuesByIndex.put(index, new HashMap<>()));
    });
  }

  protected abstract <K, E> Map<K, E> createMapWithStoreContainsSemantics();

  protected abstract <E> Set<E> createSetWithStoreContainsSemantics();

  private Set<V> createSetWithStoreContainsSemantics(Set<V> value) {
    Set<V> resultSet = createSetWithStoreContainsSemantics();
    resultSet.addAll(value);
    return resultSet;
  }

  private <K> Set<V> getByIndex(Index<K, V> index, K key) {
    Objects.requireNonNull(index, "index");
    Objects.requireNonNull(key, "key");

    validateIndexExists(index);

    return indexedValuesByIndex.get(index).getOrDefault(key, Set.of());
  }

  protected void updateAllIndices(V value) {
    for (Index<?, V> index : indexedValuesByIndex.keySet()) {
      index(value, index);
    }
  }

  private <K> void validateIndexExists(Index<K, V> index) {
    if (!indexedValuesByIndex.containsKey(index)) {
      throw new UnknownIndexException();
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
            valuesByKey.put(key, createSetWithStoreContainsSemantics(Set.of(value)));
          }
        }
        case Index.NonUnique n -> {
          valuesByKey.computeIfAbsent(key, (__) -> createSetWithStoreContainsSemantics());
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

  private <K> void createIndex(Index<K, V> index) {
    indexedValuesByIndex.put(index, new HashMap<>());
    // need to make a copy since creating a unique index on the fly could remove values here
    var allValuesCopy = createSetWithStoreContainsSemantics(reverseIndexMap.keySet());
    allValuesCopy.forEach(v -> index(v, index));
  }

  protected void removeFromAllIndices(V value) {
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
            if (valuesForKey.isEmpty()) {
              indexedValuesByIndex.get(index).remove(oldKey);
            }
          }
        }
      }
    }
  }

}
