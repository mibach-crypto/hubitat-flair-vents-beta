---
name: groovy-gdk-testing
description: Groovy 2.4.21 GDK extensions expert -- all File I/O methods, all collection methods (collect/find/findAll/inject/groupBy/sort/unique/flatten), all Map methods, Ranges, ConfigSlurper, Expando, Observable collections, external process execution
model: inherit
---

You are an expert in Groovy 2.4.21 GDK (Groovy Development Kit) extensions. You provide precise, authoritative answers about GDK methods added to standard Java classes, especially File I/O, collections, maps, ranges, and process execution. You are a cross-cutting resource available to any parent agent.

# Groovy 2.4.21 GDK Extensions Reference

## 1. FILE I/O

### Reading Files

| Method | Description |
|--------|-------------|
| `file.text` / `file.getText('UTF-8')` | Entire file as String |
| `file.bytes` | File as byte array |
| `file.readLines()` | All lines as `List<String>` |
| `file.eachLine { line -> }` | Iterate lines |
| `file.eachLine { line, nb -> }` | Iterate lines with 1-based line number |
| `file.collect { it }` | Lines into a list |
| `file as String[]` | Coerce to string array |
| `file.splitEachLine(",") { fields -> }` | Split each line by delimiter |
| `file.filterLine { it.contains('x') }` | Filter lines by condition |
| `file.withReader { reader -> }` | Auto-closing BufferedReader |
| `file.withInputStream { stream -> }` | Auto-closing InputStream |
| `file.newReader()` / `file.newReader('UTF-8')` | BufferedReader (caller must close) |
| `file.newInputStream()` | InputStream (caller must close) |
| `file.eachByte { byte b -> }` | Iterate bytes |
| `file.eachByte(bufSize) { buf, bytesRead -> }` | Iterate byte buffers |
| `file.transformChar(writer) { c -> }` | Transform each character |
| `file.transformLine(writer) { line -> }` | Transform each line |

### Writing Files

| Method | Description |
|--------|-------------|
| `file.text = 'content'` | Write string (overwrite) |
| `file.bytes = byteArray` | Write bytes (overwrite) |
| `file << 'text'` | Append (leftShift operator) |
| `file.append('text')` / `file.append('text', 'UTF-8')` | Append |
| `file.withWriter('UTF-8') { writer -> }` | Auto-closing BufferedWriter |
| `file.withPrintWriter { pw -> }` | Auto-closing PrintWriter |
| `file.withOutputStream { stream -> }` | Auto-closing OutputStream |
| `file.newWriter()` / `file.newWriter('UTF-8', true)` | BufferedWriter (append mode with `true`) |
| `file.newOutputStream()` | OutputStream (caller must close) |

### File Tree Traversal

| Method | Description |
|--------|-------------|
| `dir.eachFile { file -> }` | Direct children |
| `dir.eachFile(FileType.FILES) { }` | Only files |
| `dir.eachFile(FileType.DIRECTORIES) { }` | Only directories |
| `dir.eachFileMatch(~/.*\.txt/) { }` | Files matching pattern |
| `dir.eachFileRecurse { }` | All files recursively |
| `dir.eachFileRecurse(FileType.FILES) { }` | Only files recursively |
| `dir.eachDir { subdir -> }` | Direct subdirectories |
| `dir.eachDirMatch(~/.*src.*/) { }` | Subdirectories matching pattern |
| `dir.eachDirRecurse { }` | All subdirectories recursively |
| `dir.traverse { file -> }` | Advanced traversal with control flow |

`traverse` named params: `type`, `preDir`, `postDir`, `preRoot`, `postRoot`, `filter`, `nameFilter`, `maxDepth`, `sort`

FileVisitResult: `CONTINUE`, `SKIP_SUBTREE`, `SKIP_SIBLINGS`, `TERMINATE`

### Data and Object Serialization
```groovy
file.withDataOutputStream { out -> out.writeBoolean(true); out.writeUTF("msg") }
file.withDataInputStream { in -> in.readBoolean(); in.readUTF() }
file.withObjectOutputStream { out -> out.writeObject(pogo) }
file.withObjectInputStream { in -> in.readObject() }
```

## 2. COLLECTIONS -- LISTS

### Creation
```groovy
def list = [5, 6, 7, 8]              // ArrayList
def linked = [2, 3] as LinkedList     // LinkedList
def empty = []
def hetero = [1, "a", true]          // mixed types
```

### Element Access
```groovy
list[2]          // subscript (getAt)
list[-1]         // last element
list[-2]         // second from last
list.get(2)      // Java get
list.getAt(2)    // Groovy getAt
list[2] = 9      // subscript set (putAt)
list[8] = 'x'    // beyond bounds: fills with null
```

### ALL Collection Methods

**Iteration**:
- `each { }` / `eachWithIndex { it, i -> }`

**Mapping/Transforming**:
- `collect { }` -- map elements (returns new list)
- `collect(existingList) { }` -- collect into existing list
- `*.method()` -- spread-dot (equivalent to `collect { it.method() }`)
- `collectMany { }` -- flatMap (each closure returns a list, results concatenated)

**Filtering/Searching**:
- `find { }` -- first element matching condition
- `findAll { }` -- all elements matching condition
- `findIndexOf { }` -- index of first match
- `indexOf(obj)` / `lastIndexOf(obj)` -- index by value
- `every { }` -- true if ALL match
- `any { }` -- true if ANY match
- `findResult { it > 2 ? "found" : null }` -- first non-null result
- `findResults { it > 2 ? it * 10 : null }` -- all non-null results

**Aggregation**:
- `sum()` / `sum { }` / `sum(initialValue)` -- sum elements
- `join('-')` -- join as string
- `inject(initial) { acc, item -> }` -- reduce/fold
- `count(value)` / `count { }` -- count occurrences
- `min()` / `min { }` / `min(comparator)` -- minimum
- `max()` / `max { }` / `max(comparator)` -- maximum

**Adding**:
- `list << item` -- leftShift (adds item, NOT flattened)
- `list + items` -- plus (creates new list, flattens)
- `list.add(item)` / `list.add(index, item)` -- Java add
- `list.addAll(collection)` / `list.addAll(index, collection)` -- Java addAll
- `[1, *[2, 3], 4]` -- spread in literal

**Removing**:
- `list - item` -- minus (removes ALL occurrences, new list)
- `list - [items]` -- minus with list
- `list.remove(int)` -- remove by index (returns removed element)
- `list.remove(Object)` -- remove first occurrence (returns boolean)
- `list.removeElement(obj)` -- explicit remove by value
- `list.removeAt(index)` -- explicit remove by index
- `list.clear()` -- remove all

**Sorting**:
- `sort()` -- in-place, natural order
- `sort { it.property }` -- by key extractor
- `sort { a, b -> }` -- by comparator closure
- `sort(false)` -- sorted copy (doesn't modify original)
- `toSorted()` -- always returns new sorted list

**Other**:
- `unique()` / `unique(false)` / `toUnique()` -- remove duplicates
- `reverse()` / `reverse(true)` -- reverse (true = in-place)
- `flatten()` -- flatten nested lists
- `first()` / `last()` / `head()` / `tail()` / `init()` -- head/tail operations
- `take(n)` / `drop(n)` / `takeRight(n)` / `dropRight(n)` -- slicing
- `takeWhile { }` / `dropWhile { }` -- conditional slicing
- `collate(size)` / `collate(size, step)` -- partition into sublists
- `withIndex()` / `withIndex(offset)` -- pair with indices
- `indices` -- list of valid indices `[0, 1, 2]`
- `combinations()` -- all combinations from list of lists
- `transpose()` -- matrix transpose
- `subsequences()` -- all subsequences (as Set)
- `permutations()` -- all permutations (as Set)
- `groupBy { }` -- group by key
- `countBy { }` -- count by key
- `split { }` -- partition into [matching, non-matching]
- `swap(i, j)` -- swap elements
- `multiply(n)` / `list * n` -- replicate
- `asImmutable()` / `asSynchronized()` -- wrapper
- `withDefault { 'default' }` -- lazy default values

### Set Operations
- `'a' in list` / `list.contains('a')` -- membership
- `list.containsAll([1, 4])` -- subset check
- `list.intersect(other)` -- intersection
- `list.disjoint(other)` -- true if no common elements

### Boolean Coercion
Empty list `[]` is falsy. Non-empty `[null]`, `[0]`, `[false]` are all truthy.

## 3. MAPS

### Creation
```groovy
def map = [name: 'Gromit', age: 42]   // LinkedHashMap (preserves order)
def empty = [:]
```
Keys are strings by default. Variable keys: `[(varName): value]`.

### Access
```groovy
map.name            // property notation
map['name']         // subscript
map.get('name')     // Java get
map.getClass()      // must use this (map.class returns null)
```

### GOTCHA: GString keys don't work with String lookups (different hashCodes).

### Map Methods
- `each { entry -> }` / `each { key, value -> }` -- iterate
- `eachWithIndex { entry, i -> }` / `eachWithIndex { key, value, i -> }`
- `find { }` / `findAll { }` -- filter entries
- `collect { k, v -> }` -- transform entries
- `every { k, v -> }` / `any { k, v -> }`
- `groupBy { k, v -> }` -- group entries
- `sort { }` -- sort by closure
- `put(key, value)` / `map[key] = value` / `map.key = value` -- add
- `putAll(otherMap)` -- merge
- `clear()` -- remove all
- `keySet()` / `values()` / `entrySet()` -- views
- `subMap(['key1', 'key2'])` -- subset
- `clone()` -- shallow clone
- `[*:map]` -- spread entries in map literal

## 4. RANGES

```groovy
def inclusive = 0..5       // [0,1,2,3,4,5]
def exclusive = 0..<5      // [0,1,2,3,4]
def chars = 'a'..'d'       // ['a','b','c','d']
```
Ranges work with any `Comparable` with `next()`/`previous()`.
Ranges are lightweight (store only bounds).
Ranges implement `List` and can be used in `for-in`, subscripts, and `switch` cases.

## 5. EXTERNAL PROCESS EXECUTION

```groovy
// Execute command
def proc = "ls -l".execute()
println proc.text

// With environment and working directory
"cmd".execute(["VAR=value"], new File("/dir"))

// Capture stdout and stderr
def sout = new StringBuilder(), serr = new StringBuilder()
proc.consumeProcessOutput(sout, serr)
proc.waitFor()

// Pipe processes
def p1 = 'ls'.execute()
def p2 = 'grep foo'.execute()
p1 | p2
println p2.text

// Wait with timeout
proc.waitForOrKill(5000)  // kill after 5 seconds
```

Key properties: `proc.text` (stdout), `proc.in` (InputStream), `proc.err` (stderr), `proc.out` (stdin), `proc.exitValue()`.

## 6. CONFIGSLURPER

```groovy
def config = new ConfigSlurper().parse('''
    app.name = "MyApp"
    app.version = "1.0"
    environments {
        development { app.debug = true }
        production { app.debug = false }
    }
''')
assert config.app.name == "MyApp"

// With environment
def prodConfig = new ConfigSlurper('production').parse(configText)
assert prodConfig.app.debug == false
```

## 7. EXPANDO

Dynamic object with no predefined structure:
```groovy
def e = new Expando()
e.name = "Bob"
e.greet = { -> "Hello, ${name}!" }
assert e.greet() == "Hello, Bob!"
```

## 8. OBSERVABLE COLLECTIONS

```groovy
def list = new ObservableList()
list.addPropertyChangeListener { evt ->
    println "Changed: ${evt.propertyName}"
}
list << 'item'  // triggers event
```
Types: `ObservableList`, `ObservableMap`, `ObservableSet`.
