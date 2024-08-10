package com.github.multiindexstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.github.multiindexstore.Index.NonUnique;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

abstract class AbstractMultiIndexStoreTest<S extends MultiIndexStore<User>> {

  protected S store;

  protected final User johnDoe = new User(1L, "John", "Doe");
  protected final User janeDoe = new User(2L, "Jane", "Doe");
  protected final User johnDoeCopy = new User(1L, "John", "Doe");
  protected final User robertSmith = new User(3L, "Robert", "Smith");

  @BeforeEach
  public void setup() {
    store = createStore();
  }

  protected abstract S createStore();

  @Test
  @DisplayName("stores and retrieves values")
  void insertAndRetrieveValues() {
    var firstNameIndex = store.createIndex(User::getFirstName);
    var lastNameIndex = store.createIndex(User::getLastName);
    var idIndex = store.createUniqueIndex(User::getId);

    store.insert(johnDoe);
    store.insert(janeDoe);
    store.insert(robertSmith);

    assertThat(store.findBy(idIndex, 1L)).contains(johnDoe);
    assertThat(store.findBy(idIndex, 2L)).contains(janeDoe);
    assertThat(store.findBy(idIndex, 3L)).contains(robertSmith);
    assertThat(store.findBy(idIndex, 42L)).isEmpty();
    ;

    assertThat(store.findBy(firstNameIndex, "John")).containsExactly(johnDoe);
    assertThat(store.findBy(lastNameIndex, "Doe")).containsExactlyInAnyOrder(johnDoe, janeDoe);
    assertThat(store.findBy(firstNameIndex, "Unknown")).isEmpty();
  }

  @Test
  @DisplayName("allows removing a value")
  void remove() {
    var lastNameIndex = store.createIndex(User::getLastName);
    var idIndex = store.createIndex(User::getId);

    store.insert(johnDoe);
    store.insert(janeDoe);
    assertThat(store.findBy(lastNameIndex, "Doe")).containsExactlyInAnyOrder(johnDoe, janeDoe);

    assertThat(store.contains(johnDoe)).isTrue();
    assertThat(store.contains(janeDoe)).isTrue();
    assertThat(store.contains(robertSmith)).isFalse();

    assertThat(store.remove(johnDoe)).isTrue();

    assertThat(store.findBy(lastNameIndex, "Doe")).containsExactlyInAnyOrder(janeDoe);
    assertThat(store.findBy(idIndex, johnDoe.getId())).isEmpty();

    assertThat(store.remove(johnDoe)).isFalse();
  }

  @Test
  @DisplayName("allows adding an index for existing values")
  void createIndexForExistingValue() {
    store.insert(johnDoe);
    store.insert(janeDoe);
    store.insert(robertSmith);
    store.remove(robertSmith);

    var lastNameIndex = store.createIndex(User::getLastName);

    assertThat(store.findBy(lastNameIndex, "Doe")).containsExactlyInAnyOrder(johnDoe, janeDoe);
    assertThat(store.findBy(lastNameIndex, "Smith")).isEmpty();

    store.insert(robertSmith);
    assertThat(store.findBy(lastNameIndex, "Smith")).containsExactly(robertSmith);
  }

  @Test
  @DisplayName("remove value entirely if same unique index presented")
  void insertValueWithSameUniqueIndex() {
    var idIndex = store.createUniqueIndex(User::getId);
    var lastNameIndex = store.createIndex(User::getLastName);

    store.insert(johnDoe);
    assertThat(store.findBy(idIndex, 1L)).contains(johnDoe);
    assertThat(store.findBy(lastNameIndex, "Doe")).containsExactlyInAnyOrder(johnDoe);

    store.insert(johnDoeCopy);
    assertThat(store.findBy(idIndex, 1L)).contains(johnDoeCopy);
    assertThat(store.findBy(lastNameIndex, "Doe")).containsExactlyInAnyOrder(johnDoeCopy);
  }

  @Test
  @DisplayName("allows null key mappings")
  void nullKey() {
    var lastNameIndex = store.createIndex(User::getLastName);
    var firstNameIndex = store.createIndex(User::getFirstName);
    var peter = new User(10L, "Peter", null);
    store.insert(johnDoe);
    store.insert(peter);

    assertThat(store.contains(johnDoe)).isTrue();
    assertThat(store.contains(peter)).isTrue();
    assertThat(store.findBy(lastNameIndex, "Doe")).containsExactlyInAnyOrder(johnDoe);
    assertThat(store.findBy(firstNameIndex, "Peter")).containsExactlyInAnyOrder(peter);
  }

  @Test
  @DisplayName("rejects null values")
  void rejectsNullValues() {
    assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> store.insert(null));
  }

  @Test
  @DisplayName("rejects null indices and keys for get")
  void rejectsNullIndicesAndKeysForFindBy() {
    assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> store.findBy((Index.Unique) null, "a"));
    assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> store.findBy((Index.NonUnique) null, "a"));

    var firstNameIndex = store.createIndex(User::getFirstName);
    var idIndex = store.createUniqueIndex(User::getLastName);

    assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> store.findBy(firstNameIndex, null));
    assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> store.findBy(idIndex, null));

  }

  @Test
  @DisplayName("rejects null key mappers")
  void rejectsNullKeyMappers() {
    assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> store.createIndex(null));
    assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> store.createUniqueIndex(null));
  }

  @Test
  @DisplayName("does not break under concurrent updates")
  void worksMultithreaded() throws Throwable {
    var lastNameIndex = store.createIndex(User::getLastName);
    var cyclicBarrier = new CyclicBarrier(2);

    try (ExecutorService executorService = Executors.newCachedThreadPool()) {
      Future<?> johnFuture = executorService.submit(
          createInsertReadRemoveTask(lastNameIndex, johnDoe, cyclicBarrier));
      Future<?> janeFuture = executorService.submit(
          createInsertReadRemoveTask(lastNameIndex, janeDoe, cyclicBarrier));

      johnFuture.get(5, TimeUnit.SECONDS);
      janeFuture.get(5, TimeUnit.SECONDS);
    }
  }

  @Test
  @DisplayName("rejects foreign indices")
  void rejectsForeignIndices() {
    var otherStore = createStore();
    var lastNameIndex = otherStore.createIndex(User::getLastName);
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> store.findBy(lastNameIndex, "Doe"))
        .withMessage("Provided index is not a member of this store");
  }

  @DisplayName("returns all values")
  @Test
  void returnsAllValues() {
    store.insert(johnDoe);
    store.insert(janeDoe);

    var valuesObtained = store.values();
    assertThat(valuesObtained).containsExactlyInAnyOrder(johnDoe, janeDoe);
    store.remove(johnDoe);
    assertThat(valuesObtained).as("set should be a copy").containsExactlyInAnyOrder(johnDoe, janeDoe);
  }

  @DisplayName("returns key set")
  @Test
  void returnsKeySet() {
    var lastNameIndex = store.createIndex(User::getLastName);
    store.insert(johnDoe);
    store.insert(janeDoe);
    store.insert(robertSmith);

    assertThat(store.keySet(lastNameIndex)).containsExactlyInAnyOrder("Doe", "Smith");
  }

  @DisplayName("returns unique key entry set")
  @Test
  void returnsUniqueKeyEntries() {
    var uniqueIndex = store.createUniqueIndex(User::getId);
    store.insert(johnDoe);
    store.insert(janeDoe);
    store.insert(robertSmith);
    store.remove(robertSmith); // check index is tidied up

    assertThat(store.entrySet(uniqueIndex)).containsExactlyInAnyOrder(Map.entry(1L, johnDoe), Map.entry(2L, janeDoe));
  }

  @DisplayName("returns non-unique key entry set")
  @Test
  void returnsNonUniqueKeyEntries() {
    var lastNameIndex = store.createIndex(User::getLastName);
    store.insert(johnDoe);
    store.insert(janeDoe);
    store.insert(robertSmith);

    assertThat(store.entrySet(lastNameIndex)).containsExactlyInAnyOrder(
        Map.entry("Doe", Set.of(johnDoe, janeDoe)),
        Map.entry("Smith", Set.of(robertSmith))
    );

    store.remove(johnDoe);
    store.remove(janeDoe);

    assertThat(store.entrySet(lastNameIndex)).containsExactlyInAnyOrder(
        Map.entry("Smith", Set.of(robertSmith))
    );
  }

  @DisplayName("clear the store but keeps the indice")
  @Test
  void clear() {
    var lastNameIndex = store.createIndex(User::getLastName);
    var idIndex = store.createUniqueIndex(User::getId);
    store.insert(johnDoe);
    store.insert(janeDoe);
    store.insert(robertSmith);

    store.clear();

    assertThat(store.findBy(lastNameIndex, johnDoe.getLastName())).isEmpty();
    assertThat(store.entrySet(lastNameIndex)).isEmpty();
    assertThat(store.findBy(idIndex, johnDoe.getId())).isEmpty();
    assertThat(store.entrySet(idIndex)).isEmpty();
  }

  private Runnable createInsertReadRemoveTask(NonUnique<String, User> lastNameIndex, User user,
      CyclicBarrier cyclicBarrier) {
    return () -> {
      for (int i = 0; i < 100; i++) {
        try {
          store.insert(user);
          assertThat(store.findBy(lastNameIndex, user.getLastName())).contains(user);
          assertThat(store.contains(user)).isTrue();

          store.remove(user);
          assertThat(store.findBy(lastNameIndex, user.getLastName())).doesNotContain(user);
          assertThat(store.contains(user)).isFalse();

          cyclicBarrier.await();
        } catch (InterruptedException | BrokenBarrierException e) {
          Thread.currentThread().interrupt();
          throw new RuntimeException(e);
        }
      }
    };
  }
}