package com.github.multiindexstore;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

class Util {

  public static <T> Set<T> newIdentityHashSet() {
    return Collections.newSetFromMap(new IdentityHashMap<T, Boolean>());
  }
}
