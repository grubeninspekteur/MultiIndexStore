package com.github.multiindexstore;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("IdentityMultiIndexStore")
public class IdentityMultiIndexStoreTest extends AbstractMultiIndexStoreTest<IdentityMultiIndexStore<User>> {

  @Override
  protected IdentityMultiIndexStore<User> createStore() {
    return new IdentityMultiIndexStore<>();
  }

  @DisplayName("allows inserting multiple values that are equal to each other")
  @Test
  void insertMultipleEqual() {
    assertThat(System.identityHashCode(johnDoeCopy)).isNotEqualTo(System.identityHashCode(johnDoe));

    var lastNameIndex = store.createIndex(User::getLastName);
    assertThat(store.insert(johnDoe)).isTrue();
    assertThat(store.insert(johnDoeCopy)).isTrue();

    assertThat(store.contains(johnDoe)).isTrue();
    assertThat(store.contains(johnDoeCopy)).isTrue();

    assertThat(store.findBy(lastNameIndex, "Doe"))
        .hasSize(2)
        .extracting(System::identityHashCode)
        .containsExactlyInAnyOrder(System.identityHashCode(johnDoe), System.identityHashCode(johnDoeCopy));
  }

  @DisplayName("updates indices on update")
  @Test
  void update() {
    var lastNameIndex = store.createIndex(User::getLastName);

    store.insert(janeDoe);
    store.insert(johnDoe);

    assertThat(store.findBy(lastNameIndex, "Doe")).containsExactlyInAnyOrder(janeDoe, johnDoe);
    assertThat(store.findBy(lastNameIndex, "Miller")).isEmpty();

    johnDoe.setLastName("Miller");

    assertThat(store.findBy(lastNameIndex, "Doe")).containsExactlyInAnyOrder(janeDoe, johnDoe);
    assertThat(store.findBy(lastNameIndex, "Miller")).isEmpty();

    store.update(johnDoe);

    assertThat(store.findBy(lastNameIndex, "Doe")).containsExactlyInAnyOrder(janeDoe);
    assertThat(store.findBy(lastNameIndex, "Miller")).containsExactlyInAnyOrder(johnDoe);
  }

  @DisplayName("allows adding unique index for existing values without exception")
  @Test
  void createUniqueIndexForExistingValuesDoesNotCrash() {
    assertThat(johnDoe.getId()).isEqualTo(johnDoeCopy.getId());

    store.insert(johnDoe);
    store.insert(johnDoeCopy);

    var idIndex = store.createUniqueIndex(User::getId);

    var valueFoundById = store.findBy(idIndex, johnDoe.getId());
    assertThat(valueFoundById).isPresent();
    assertThat(valueFoundById.get()).matches(user -> user == johnDoe || user == janeDoe);
  }
}
