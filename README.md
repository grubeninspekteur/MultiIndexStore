# MultiIndexStore for Java
This library implements an in-memory object store that indexes its elements based on user-defined indices.
Think `Map`, but with multiple key types, and automatic indexing for new entries.
It is inspired by [this reddit post](https://www.reddit.com/r/javahelp/comments/1em7wu1/an_purejava_inmemory_datastructure_with_builtin/).

## Usage
There are two variants of `MultiIndexStore` provided.
`HashMultiIndexStore` uses `equals()` and `hashCode()` to check if a value is already present in the store.
Adding values that are equal will not store a new object.

```java
record User(long id, String firstName, String lastName) {}

MultiIndexStore store = new HashMultiIndexStore<User>();
var lastName = store.createIndex(User::lastName);
var id = store.createUniqueIndex(User::id);

store.insert(new User(1L, "John", "Doe"));
store.insert(new User(2L, "Jane", "Doe"));

store.insert(new User(2L, "Jane", "Doe")); // will not insert another entry because record is component-wise equal

Set<User> does = store.findBy(lastName, "Doe"); // Set.of(John Doe, Jane Doe)
Optional<User> userWithId1 = store.findBy(id, 1L); // Optional.of(John Doe)
store.createIndex(User::firstName); // indices can be added after store creation, triggering reindexing
```

`IdentityMultiIndexStore` uses the object's identity to determine if the value is already present.
It additionally allows to notify the store of updates you made to a value so it can update its indices.

````java
MutableValueMultiIndexStore store = new IdentityMultiIndexStore<>();

var book = new Book("Pride and Prejudice");
var title = store.createIndex(Book::getTitle);
store.insert(book);
store.findBy(title, "Pride and Prejudice"); // Set.of(book)

book.setTitle("Of Mice and Men");
store.update(book);
store.findBy(title, "Of Mice and Men"); // Set.of(book)

````

## Memory characteristics
The store will maintain a Map per index, containing a map per key.
Additionally, a reverse map element -> indices is maintained for index cleanup after removal.

## Thread safety
The store is thread-safe for reads and writes.
For `IdentityMultiIndexStore#update` it is your responsibility to ensure the mutations you perform on the shared entry are synchronized.
The store will only protect updating the indices atomically once it was informed about the changes.
