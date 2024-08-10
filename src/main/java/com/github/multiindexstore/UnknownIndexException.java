package com.github.multiindexstore;

public class UnknownIndexException extends IllegalArgumentException {

  public UnknownIndexException() {
    super("Provided index is not a member of this store");
  }
}
