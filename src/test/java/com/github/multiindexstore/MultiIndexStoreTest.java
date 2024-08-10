package com.github.multiindexstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.github.multiindexstore.Index.NonUnique;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Multi index store")
class MultiIndexStoreTest {

  private final MultiIndexStore<User> store = new MultiIndexStore<>();

  private final User johnDoe = new User(1L, "John", "Doe");
  private final User janeDoe = new User(2L, "Jane", "Doe");
  private final User johnDoeCopy = new User(1L, "John", "Doe");
  private final User robertSmith = new User(3L, "Robert", "Smith");

  @Test
  @DisplayName("stores and retrieves non-mutable values")
  void addAndRetrieveNonMutableValue() {
    var firstNameIndex = store.createIndex(User::getFirstName);
    var lastNameIndex = store.createIndex(User::getLastName);
    var idIndex = store.createUniqueIndex(User::getId);

    store.add(johnDoe);
    store.add(janeDoe);
    store.add(robertSmith);

    assertThat(store.get(idIndex, 1L)).contains(johnDoe);
    assertThat(store.get(idIndex, 2L)).contains(janeDoe);
    assertThat(store.get(idIndex, 3L)).contains(robertSmith);
    assertThat(store.get(idIndex, 42L)).isEmpty();;

    assertThat(store.get(firstNameIndex, "John")).containsExactly(johnDoe);
    assertThat(store.get(lastNameIndex, "Doe")).containsExactlyInAnyOrder(johnDoe, janeDoe);
    assertThat(store.get(firstNameIndex, "Unknown")).isEmpty();
  }

  @Test
  @DisplayName("allows removing a value")
  void remove() {
    var lastNameIndex = store.createIndex(User::getLastName);
    var idIndex = store.createIndex(User::getId);

    store.add(johnDoe);
    store.add(janeDoe);
    assertThat(store.get(lastNameIndex, "Doe")).containsExactlyInAnyOrder(johnDoe, janeDoe);

    assertThat(store.contains(johnDoe)).isTrue();
    assertThat(store.contains(janeDoe)).isTrue();
    assertThat(store.contains(robertSmith)).isFalse();

    assertThat(store.remove(johnDoe)).isTrue();

    assertThat(store.get(lastNameIndex, "Doe")).containsExactlyInAnyOrder(janeDoe);
    assertThat(store.get(idIndex, johnDoe.getId())).isEmpty();

    assertThat(store.remove(johnDoe)).isFalse();
  }

  @Test
  @DisplayName("allows adding an index for existing values")
  void createIndexForExistingValue() {
    store.add(johnDoe);
    store.add(janeDoe);
    store.add(robertSmith);
    store.remove(robertSmith);

    var lastNameIndex = store.createIndex(User::getLastName);

    assertThat(store.get(lastNameIndex, "Doe")).containsExactlyInAnyOrder(johnDoe, janeDoe);
    assertThat(store.get(lastNameIndex, "Smith")).isEmpty();

    store.add(robertSmith);
    assertThat(store.get(lastNameIndex, "Smith")).containsExactly(robertSmith);
  }

  @Test
  @DisplayName("does not remove value entirely if same unique index presented, but overrides unique index")
  void addValueWithSameUniqueIndex() {
    var idIndex = store.createUniqueIndex(User::getId);
    var lastNameIndex = store.createIndex(User::getLastName);

    store.add(johnDoe);
    assertThat(store.get(idIndex, 1L)).contains(johnDoe);
    assertThat(store.get(lastNameIndex, "Doe")).containsExactlyInAnyOrder(johnDoe);

    store.add(johnDoeCopy);
    assertThat(store.get(idIndex, 1L)).contains(johnDoeCopy);

    assertThat(store.get(lastNameIndex, "Doe")).containsExactlyInAnyOrder(johnDoe, johnDoeCopy);
  }

  @Test
  @DisplayName("allows null key mappings")
  void nullKey() {
    var lastNameIndex = store.createIndex(User::getLastName);
    var firstNameIndex = store.createIndex(User::getFirstName);
    var peter = new User(10L, "Peter", null);
    store.add(johnDoe);
    store.add(peter);

    assertThat(store.contains(johnDoe)).isTrue();
    assertThat(store.contains(peter)).isTrue();
    assertThat(store.get(lastNameIndex, "Doe")).containsExactlyInAnyOrder(johnDoe);
    assertThat(store.get(firstNameIndex, "Peter")).containsExactlyInAnyOrder(peter);
  }

  @Test
  @DisplayName("rejects null values")
  void rejectsNullValues() {
    assertThatExceptionOfType(NullPointerException.class).isThrownBy(() ->store.add(null));
  }

  @Test
  @DisplayName("rejects null indices and keys for get")
  void rejectsNullIndicesAndKeysForGet() {
    assertThatExceptionOfType(NullPointerException.class).isThrownBy(() ->store.get((Index.Unique) null, "a"));
    assertThatExceptionOfType(NullPointerException.class).isThrownBy(() ->store.get((Index.NonUnique) null, "a"));

    var firstNameIndex = store.createIndex(User::getFirstName);
    var idIndex = store.createUniqueIndex(User::getLastName);

    assertThatExceptionOfType(NullPointerException.class).isThrownBy(() ->store.get(firstNameIndex, null));
    assertThatExceptionOfType(NullPointerException.class).isThrownBy(() ->store.get(idIndex, null));

  }

  @Test
  @DisplayName("rejects null key mappers")
  void rejectsNullKeyMappers() {
    assertThatExceptionOfType(NullPointerException.class).isThrownBy(() ->store.createIndex(null));
    assertThatExceptionOfType(NullPointerException.class).isThrownBy(() ->store.createUniqueIndex(null));
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
    var otherStore = new MultiIndexStore<User>();
    var lastNameIndex = otherStore.createIndex(User::getLastName);
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> store.get(lastNameIndex, "Doe"))
        .withMessage("Provided index is not a member of this store");
  }

  @Test
  @DisplayName("allows null keys")

  private Runnable createInsertReadRemoveTask(NonUnique<String, User> lastNameIndex, User user, CyclicBarrier cyclicBarrier) {
    return () -> {
      for (int i = 0; i < 100; i++) {
        try {
          store.add(user);
          assertThat(store.get(lastNameIndex, user.getLastName())).contains(user);
          assertThat(store.contains(user)).isTrue();

          store.remove(user);
          assertThat(store.get(lastNameIndex, user.getLastName())).doesNotContain(user);
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