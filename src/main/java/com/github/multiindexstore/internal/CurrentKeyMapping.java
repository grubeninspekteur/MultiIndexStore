package com.github.multiindexstore.internal;

import com.github.multiindexstore.Index;

public record CurrentKeyMapping<K, V>(Index<K, V> index, K key) {

}
