package com.github.multiindexstore;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class HashMultiIndexStoreTest extends AbstractMultiIndexStoreTest<HashMultiIndexStore<User>> {

  @DisplayName("does not insert equal values")
  @Test
  void doesNotInsertEqualValues() {
    var lastNameIndex = store.createIndex(User::getLastName);

    assertThat(store.insert(johnDoe)).isTrue();
    assertThat(store.insert(johnDoeCopy)).isFalse();

    assertThat(store.findBy(lastNameIndex, "Doe")).containsExactly(johnDoe);
  }

  @Override
  protected HashMultiIndexStore<User> createStore() {
    return new HashMultiIndexStore<>();
  }
}
