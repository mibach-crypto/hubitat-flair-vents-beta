# Groovy 2.4.21 GDK & Testing Knowledge Base

## Part 1: Working with IO

### 1.1 Reading Files

**eachLine** - iterate through each line of a file:
```groovy
new File(baseDir, 'haiku.txt').eachLine { line ->
    println line
}

// With line number (1-based)
new File(baseDir, 'haiku.txt').eachLine { line, nb ->
    println "Line $nb: $line"
}
```

**withReader** - resource-managed reader with auto-close:
```groovy
def count = 0, MAXSIZE = 3
new File(baseDir, "haiku.txt").withReader { reader ->
    while (reader.readLine()) {
        if (++count > MAXSIZE) {
            throw new RuntimeException('Haiku should only have 3 verses')
        }
    }
}
```

**collect** - read lines into a list:
```groovy
def list = new File(baseDir, 'haiku.txt').collect { it }
```

**as String[]** - coerce file to string array:
```groovy
def array = new File(baseDir, 'haiku.txt') as String[]
```

**bytes property** - get file contents as byte array:
```groovy
byte[] contents = file.bytes
```

**text property (getText)** - get entire file as string:
```groovy
String content = file.text
String content = file.getText('UTF-8')  // with encoding
```

**readLines** - read all lines into a List<String>:
```groovy
List<String> lines = file.readLines()
List<String> lines = file.readLines('UTF-8')
```

**splitEachLine** - split each line by a pattern/delimiter:
```groovy
file.splitEachLine(",") { fields ->
    println "Name: ${fields[0]}, Age: ${fields[1]}"
}
```

**filterLine** - filter lines matching a closure condition:
```groovy
def filtered = file.filterLine { it.contains('groovy') }
// also: file.filterLine(writer) { ... }
```

**transformChar** - transform each character:
```groovy
file.transformChar(writer) { it.toUpperCase() }
```

**transformLine** - transform each line:
```groovy
file.transformLine(writer) { it.toUpperCase() }
```

**newInputStream** - get an InputStream (caller must close):
```groovy
def is = file.newInputStream()
// ...use it...
is.close()
```

**withInputStream** - auto-closing InputStream:
```groovy
new File(baseDir, 'haiku.txt').withInputStream { stream ->
    // do something with the stream
}
```

**newReader** - get a BufferedReader (caller must close):
```groovy
def reader = file.newReader()
def reader = file.newReader('UTF-8')
```

**eachByte** - iterate over bytes:
```groovy
file.eachByte { byte b ->
    // process each byte
}
file.eachByte(bufferSize) { byte[] buf, int bytesRead ->
    // process buffer
}
```

### 1.2 Writing Files

**withWriter** - resource-managed writer with auto-close:
```groovy
new File(baseDir, 'haiku.txt').withWriter('utf-8') { writer ->
    writer.writeLine 'Into the ancient pond'
    writer.writeLine 'A frog jumps'
    writer.writeLine 'Water\'s sound!'
}
```

**leftShift operator (<<)** - append text to file:
```groovy
new File(baseDir, 'haiku.txt') << '''Into the ancient pond
A frog jumps
Water's sound!'''
```

**bytes property assignment (setBytes)** - write byte array:
```groovy
file.bytes = [66, 22, 11]
```

**text property assignment (setText)** - write string content:
```groovy
file.text = 'Hello World'
```

**withOutputStream** - auto-closing OutputStream:
```groovy
new File(baseDir, 'data.bin').withOutputStream { stream ->
    // write to stream
}
```

**withPrintWriter** - auto-closing PrintWriter:
```groovy
file.withPrintWriter { pw ->
    pw.println 'line 1'
    pw.println 'line 2'
}
file.withPrintWriter('UTF-8') { pw -> ... }
```

**newWriter** - get a BufferedWriter (caller must close):
```groovy
def writer = file.newWriter()
def writer = file.newWriter('UTF-8')
def writer = file.newWriter('UTF-8', true) // append mode
```

**newOutputStream** - get an OutputStream (caller must close):
```groovy
def os = file.newOutputStream()
```

**append** - append text to file:
```groovy
file.append('more text')
file.append('more text', 'UTF-8')
```

### 1.3 Traversing File Trees

**eachFile** - iterate over directory contents:
```groovy
dir.eachFile { file ->
    println file.name
}
dir.eachFile(FileType.FILES) { file -> ... }
dir.eachFile(FileType.DIRECTORIES) { file -> ... }
```

**eachFileMatch** - iterate files matching a pattern:
```groovy
dir.eachFileMatch(~/.*\.txt/) { file ->
    println file.name
}
dir.eachFileMatch(FileType.FILES, ~/.*\.txt/) { file -> ... }
```

**eachFileRecurse** - recursively iterate all files:
```groovy
dir.eachFileRecurse { file ->
    println file.name
}
dir.eachFileRecurse(FileType.FILES) { file ->
    println file.name
}
```

**eachDir** - iterate over subdirectories only:
```groovy
dir.eachDir { subdir ->
    println subdir.name
}
```

**eachDirMatch** - iterate subdirectories matching pattern:
```groovy
dir.eachDirMatch(~/.*src.*/) { subdir -> ... }
```

**eachDirRecurse** - recursively iterate subdirectories:
```groovy
dir.eachDirRecurse { subdir -> ... }
```

**traverse** - advanced traversal with control flow:
```groovy
dir.traverse { file ->
    if (file.directory && file.name == 'bin') {
        FileVisitResult.TERMINATE   // stop traversal
    } else {
        println file.name
        FileVisitResult.CONTINUE    // continue traversal
    }
}
```

FileType enum values: `FileType.FILES`, `FileType.DIRECTORIES`, `FileType.ANY`

FileVisitResult values: `FileVisitResult.CONTINUE`, `FileVisitResult.SKIP_SUBTREE`, `FileVisitResult.SKIP_SIBLINGS`, `FileVisitResult.TERMINATE`

traverse named parameters: `type`, `preDir`, `postDir`, `preRoot`, `postRoot`, `filter`, `nameFilter`, `maxDepth`, `sort`

### 1.4 Working with Data and Objects

**withDataOutputStream / withDataInputStream** - typed data serialization:
```groovy
boolean b = true
String message = 'Hello from Groovy'

file.withDataOutputStream { out ->
    out.writeBoolean(b)
    out.writeUTF(message)
}

file.withDataInputStream { input ->
    assert input.readBoolean() == b
    assert input.readUTF() == message
}
```

**withObjectOutputStream / withObjectInputStream** - object serialization:
```groovy
Person p = new Person(name: 'Bob', age: 76)

file.withObjectOutputStream { out ->
    out.writeObject(p)
}

file.withObjectInputStream { input ->
    def p2 = input.readObject()
    assert p2.name == p.name
    assert p2.age == p.age
}
```

### 1.5 Executing External Processes

**execute()** - run an external command (added to String, String[], List):
```groovy
def process = "ls -l".execute()
println "Found text ${process.text}"

// Process output line by line
def process = "ls -l".execute()
process.in.eachLine { line ->
    println line
}
```

**Windows shell commands** - must use cmd /c:
```groovy
def process = "cmd /c dir".execute()
println "${process.text}"
```

**execute with environment and working directory**:
```groovy
def p = "rm -f foo.tmp".execute([], tmpDir)
```

**consumeProcessOutput** - consume stdout/stderr to prevent blocking:
```groovy
def p = "rm -f foo.tmp".execute([], tmpDir)
p.consumeProcessOutput()
p.waitFor()

// With StringBuilders to capture output
def sout = new StringBuilder()
def serr = new StringBuilder()
proc.consumeProcessOutput(sout, serr)
```

**consumeProcessErrorStream** - consume only stderr:
```groovy
[proc2, proc3].each { it.consumeProcessErrorStream(serr) }
```

**waitFor** - wait for process completion:
```groovy
p.waitFor()
```

**waitForOrKill** - wait with timeout, kill if exceeded:
```groovy
proc4.waitForOrKill(1000) // milliseconds
```

**waitForProcessOutput** - wait and consume output:
```groovy
proc.waitForProcessOutput()
proc.waitForProcessOutput(sout, serr)
```

**pipeTo and | operator** - chain processes:
```groovy
proc1 = 'ls'.execute()
proc2 = 'tr -d o'.execute()
proc3 = 'tr -d e'.execute()
proc4 = 'tr -d i'.execute()
proc1 | proc2 | proc3 | proc4
proc4.waitFor()
if (proc4.exitValue()) {
    println proc4.err.text
} else {
    println proc4.text
}
```

**Complete piping example with error handling**:
```groovy
def sout = new StringBuilder()
def serr = new StringBuilder()
proc2 = 'tr -d o'.execute()
proc3 = 'tr -d e'.execute()
proc4 = 'tr -d i'.execute()
proc4.consumeProcessOutput(sout, serr)
proc2 | proc3 | proc4
[proc2, proc3].each { it.consumeProcessErrorStream(serr) }
proc2.withWriter { writer ->
    writer << 'testfile.groovy'
}
proc4.waitForOrKill(1000)
println "Standard output: $sout"
println "Standard error: $serr"
```

**Key properties on Process**:
- `process.text` - read all stdout as string
- `process.in` - InputStream for stdout
- `process.err` - InputStream for stderr
- `process.out` - OutputStream for stdin
- `process.exitValue()` - exit code (0 = success)

---

## Part 2: Working with Collections

### 2.1 Lists

#### Creation and Literals
```groovy
def list = [5, 6, 7, 8]                    // ArrayList by default
assert list instanceof java.util.List
def emptyList = []
assert emptyList.size() == 0

def heterogeneous = [1, "a", true]          // mixed types OK

// Type coercion
def linkedList = [2, 3, 4] as LinkedList
assert linkedList instanceof java.util.LinkedList

LinkedList otherLinked = [3, 4, 5]          // coercion via type declaration
assert otherLinked instanceof java.util.LinkedList

// Cloning
def list1 = ['a', 'b', 'c']
def list2 = new ArrayList<String>(list1)    // copy constructor
def list3 = list1.clone()                   // shallow clone
```

#### Element Access
```groovy
def list = [5, 6, 7, 8]
assert list[2] == 7             // subscript operator
assert list.get(2) == 7         // get() method
assert list.getAt(2) == 7       // getAt() method

// Negative indexing (from end)
assert [1, 2, 3, 4, 5][-1] == 5
assert [1, 2, 3, 4, 5][-2] == 4
assert [1, 2, 3, 4, 5].getAt(-2) == 4

// Setting elements
list[2] = 9
assert list == [5, 6, 9, 8]
list.putAt(2, 10)
assert list == [5, 6, 10, 8]
assert list.set(2, 11) == 10     // returns old value
assert list == [5, 6, 11, 8]

// Setting beyond bounds (fills with null)
list = ['a', 'b', 'z', 'e', 'u', 'v', 'g']
list[8] = 'x'
assert list == ['a', 'b', 'z', 'e', 'u', 'v', 'g', null, 'x']
```

#### List as Boolean
```groovy
assert ![]                                   // empty list is falsy
assert [1] && ['a'] && [0] && [false] && [null]  // non-empty is truthy
```

#### Iteration
```groovy
[1, 2, 3].each { println "Item: $it" }

['a', 'b', 'c'].eachWithIndex { it, i ->
    println "$i: $it"
}
```

#### Collecting / Mapping
```groovy
assert [1, 2, 3].collect { it * 2 } == [2, 4, 6]

// Spread-dot operator equivalent
assert [1, 2, 3]*.multiply(2) == [1, 2, 3].collect { it.multiply(2) }

// Collect into existing collection
def list = [0]
assert [1, 2, 3].collect(list) { it * 2 } == [0, 2, 4, 6]
assert list == [0, 2, 4, 6]
```

#### Filtering and Searching
```groovy
assert [1, 2, 3].find { it > 1 } == 2            // first match
assert [1, 2, 3].findAll { it > 1 } == [2, 3]    // all matches

assert ['a', 'b', 'c', 'd', 'e'].findIndexOf {
    it in ['c', 'e', 'g']
} == 2

assert ['a', 'b', 'c', 'd', 'c'].indexOf('c') == 2
assert ['a', 'b', 'c', 'd', 'c'].indexOf('z') == -1
assert ['a', 'b', 'c', 'd', 'c'].lastIndexOf('c') == 4

assert [1, 2, 3].every { it < 5 }       // all match
assert ![1, 2, 3].every { it < 3 }
assert [1, 2, 3].any { it > 2 }          // at least one matches
assert ![1, 2, 3].any { it > 3 }
```

#### Aggregation
```groovy
assert [1, 2, 3, 4, 5, 6].sum() == 21
assert ['a', 'b', 'c', 'd', 'e'].sum() == 'abcde'  // string concatenation
assert [['a', 'b'], ['c', 'd']].sum() == ['a', 'b', 'c', 'd']  // list flattening

// sum with closure
assert ['a', 'b', 'c', 'd', 'e'].sum {
    ((char) it) - ((char) 'a')
} == 10

// sum with initial value
assert [].sum(1000) == 1000
assert [1, 2, 3].sum(1000) == 1006

// join
assert [1, 2, 3].join('-') == '1-2-3'

// inject (reduce/fold)
assert [1, 2, 3].inject('counting: ') {
    str, item -> str + item
} == 'counting: 123'
assert [1, 2, 3].inject(0) { count, item ->
    count + item
} == 6
```

#### Min / Max
```groovy
def list = [9, 4, 2, 10, 5]
assert list.max() == 10
assert list.min() == 2

assert ['x', 'y', 'a', 'z'].min() == 'a'  // natural ordering

// With closure (single-value comparator key)
def list2 = ['abc', 'z', 'xyzuvw', 'Hello', '321']
assert list2.max { it.size() } == 'xyzuvw'
assert list2.min { it.size() } == 'z'

// With Comparator
Comparator mc = { a, b -> a == b ? 0 : (a < b ? -1 : 1) }
def list3 = [7, 4, 9, -6, -1, 11, 2, 3, -9, 5, -13]
assert list3.max(mc) == 11
assert list3.min(mc) == -13

// With closure (two-arg comparator)
Comparator mc2 = { a, b ->
    a == b ? 0 : (Math.abs(a) < Math.abs(b)) ? -1 : 1
}
assert list3.max(mc2) == -13
assert list3.min(mc2) == -1
```

#### Adding Elements
```groovy
def list = []
assert list.empty

list << 5                                // leftShift
assert list.size() == 1

list << 7 << 'i' << 11                  // chained leftShift
assert list == [5, 7, 'i', 11]

list << ['m', 'o']                       // adds as nested list
assert list == [5, 7, 'i', 11, ['m', 'o']]

assert ([1, 2] << 3 << [4, 5] << 6) == [1, 2, 3, [4, 5], 6]
assert ([1, 2, 3] << 4) == ([1, 2, 3].leftShift(4))

// plus operator (creates new list, flattens)
assert [1, 2] + 3 + [4, 5] + 6 == [1, 2, 3, 4, 5, 6]
assert [1, 2].plus(3).plus([4, 5]).plus(6) == [1, 2, 3, 4, 5, 6]

def a = [1, 2, 3]
a += 4
a += [5, 6]
assert a == [1, 2, 3, 4, 5, 6]

// Spread operator in list literals
assert [1, *[222, 333], 456] == [1, 222, 333, 456]
assert [*[1, 2, 3]] == [1, 2, 3]

// Flatten nested lists
assert [1, [2, 3, [4, 5], 6], 7, [8, 9]].flatten() == [1, 2, 3, 4, 5, 6, 7, 8, 9]

// add and addAll
def list2 = [1, 2]
list2.add(3)
list2.addAll([5, 4])
assert list2 == [1, 2, 3, 5, 4]

list2 = [1, 2]
list2.add(1, 3)              // insert at index
assert list2 == [1, 3, 2]
list2.addAll(2, [5, 4])      // insert multiple at index
assert list2 == [1, 3, 5, 4, 2]
```

#### Removing Elements
```groovy
// Minus operator (creates new list)
assert ['a','b','c','b','b'] - 'c' == ['a','b','b','b']
assert ['a','b','c','b','b'] - 'b' == ['a','c']           // removes ALL occurrences
assert ['a','b','c','b','b'] - ['b','c'] == ['a']

def list = [1,2,3,4,3,2,1]
list -= 3
assert list == [1,2,4,2,1]
assert ( list -= [2,4] ) == [1,1]

// remove(int index) - removes by index, returns removed element
def list2 = ['a','b','c','d','e','f','b','b','a']
assert list2.remove(2) == 'c'
assert list2 == ['a','b','d','e','f','b','b','a']

// remove(Object) - removes first occurrence, returns boolean
def list3 = ['a','b','c','b','b']
assert list3.remove('c')
assert list3.remove('b')        // removes first 'b'
assert !list3.remove('z')       // false if not found
assert list3 == ['a','b','b']

// Disambiguation between remove(int) and remove(Object) for Integer lists
def list4 = [1,2,3,4,5,6,2,2,1]
assert list4.remove(2) == 3              // removes at index 2
assert list4 == [1,2,4,5,6,2,2,1]

assert list4.removeElement(2)            // removes first value 2
assert list4 == [1,4,5,6,2,2,1]

assert !list4.removeElement(8)           // false, not found
assert list4 == [1,4,5,6,2,2,1]

assert list4.removeAt(1) == 4            // removes at index 1
assert list4 == [1,5,6,2,2,1]

// clear
def list5 = ['a',2,'c',4]
list5.clear()
assert list5 == []
```

#### Set Operations
```groovy
assert 'a' in ['a','b','c']                     // in operator
assert ['a','b','c'].contains('a')
assert [1,3,4].containsAll([1,4])

assert [1,2,3,3,3,3,4,5].count(3) == 4          // count occurrences
assert [1,2,3,3,3,3,4,5].count { it % 2 == 0 } == 2  // count with closure

assert [1,2,4,6,8,10,12].intersect([1,3,6,9,12]) == [1,6,12]

assert [1,2,3].disjoint([4,6,9])         // true if no common elements
assert ![1,2,3].disjoint([2,4,6])
```

#### Sorting
```groovy
assert [6, 3, 9, 2, 7, 1, 5].sort() == [1, 2, 3, 5, 6, 7, 9]

// Sort with closure (key extractor)
def list = ['abc', 'z', 'xyzuvw', 'Hello', '321']
assert list.sort { it.size() } == ['z', 'abc', '321', 'Hello', 'xyzuvw']

// Sort with two-arg closure (comparator)
def list2 = [7, 4, -6, -1, 11, 2, 3, -9, 5, -13]
assert list2.sort { a, b ->
    a == b ? 0 : Math.abs(a) < Math.abs(b) ? -1 : 1
} == [-1, 2, 3, 4, 5, -6, 7, -9, 11, -13]

// Sort with Comparator
Comparator mc = { a, b ->
    a == b ? 0 : Math.abs(a) < Math.abs(b) ? -1 : 1
}
def list3 = [6, -3, 9, 2, -7, 1, 5]
Collections.sort(list3)
assert list3 == [-7, -3, 1, 2, 5, 6, 9]

Collections.sort(list3, mc)
assert list3 == [1, 2, -3, 5, 6, -7, 9]
```

Note: `sort()` modifies the list in place and returns it. Use `sort(false)` to get a sorted copy without modifying the original. `toSorted()` always returns a new sorted list.

#### Duplicating
```groovy
assert [1, 2, 3] * 3 == [1, 2, 3, 1, 2, 3, 1, 2, 3]
assert [1, 2, 3].multiply(2) == [1, 2, 3, 1, 2, 3]
assert Collections.nCopies(3, 'b') == ['b', 'b', 'b']
assert Collections.nCopies(2, [1, 2]) == [[1, 2], [1, 2]]
```

#### Additional List Methods
```groovy
// unique - removes duplicates (in place)
assert [1, 1, 2, 3, 3].unique() == [1, 2, 3]
assert [1, 1, 2, 3, 3].unique(false) == [1, 2, 3]  // returns new list
assert [1, 1, 2, 3, 3].toUnique() == [1, 2, 3]      // always returns new list

// first, last, head, tail, init
assert [1, 2, 3].first() == 1
assert [1, 2, 3].last() == 3
assert [1, 2, 3].head() == 1             // same as first()
assert [1, 2, 3].tail() == [2, 3]        // all except first
assert [1, 2, 3].init() == [1, 2]        // all except last

// take, drop
assert [1, 2, 3, 4, 5].take(3) == [1, 2, 3]
assert [1, 2, 3, 4, 5].drop(3) == [4, 5]
assert [1, 2, 3, 4, 5].takeWhile { it < 4 } == [1, 2, 3]
assert [1, 2, 3, 4, 5].dropWhile { it < 4 } == [4, 5]

// takeRight, dropRight
assert [1, 2, 3, 4, 5].takeRight(3) == [3, 4, 5]
assert [1, 2, 3, 4, 5].dropRight(3) == [1, 2]

// reverse
assert [1, 2, 3].reverse() == [3, 2, 1]
assert [1, 2, 3].reverse(true) // in-place reverse

// collate - partition into sub-lists
assert [1, 2, 3, 4, 5, 6, 7].collate(3) == [[1, 2, 3], [4, 5, 6], [7]]
assert [1, 2, 3, 4, 5, 6, 7].collate(3, 2) == [[1, 2, 3], [3, 4, 5], [5, 6, 7]]
assert [1, 2, 3, 4, 5, 6, 7].collate(3, false) == [[1, 2, 3], [4, 5, 6]] // keepRemainder=false

// withIndex
assert ['a', 'b', 'c'].withIndex() == [['a', 0], ['b', 1], ['c', 2]]
assert ['a', 'b', 'c'].withIndex(5) == [['a', 5], ['b', 6], ['c', 7]]  // start offset

// indices
assert [10, 20, 30].indices == [0, 1, 2]

// combinations
assert [[1, 2], [3, 4]].combinations() == [[1, 3], [2, 3], [1, 4], [2, 4]]

// eachCombination
[[2, 3], [4, 5, 6]].eachCombination { println it[0] + it[1] }

// transpose
assert [[1, 2, 3], ['a', 'b', 'c']].transpose() == [[1, 'a'], [2, 'b'], [3, 'c']]

// subsequences
assert [1, 2, 3].subsequences() == [[1, 2, 3], [1, 3], [2, 3], [1, 2], [1], [2], [3]] as Set

// permutations
assert [1, 2, 3].permutations() == [[1,2,3],[1,3,2],[2,1,3],[2,3,1],[3,1,2],[3,2,1]] as Set

// asImmutable / asSynchronized
def immutable = [1, 2, 3].asImmutable()
def synced = [1, 2, 3].asSynchronized()

// withDefault (lazy default values)
def sparse = [].withDefault { 'default' }
assert sparse[42] == 'default'

// groupBy
assert ['a', 7, 'b', [2, 3]].groupBy { it.class } ==
    [(String): ['a', 'b'], (Integer): [7], (ArrayList): [[2, 3]]]

// countBy
assert ['a', 'b', 'a', 'c', 'b', 'a'].countBy { it } == [a: 3, b: 2, c: 1]

// collectMany (flatMap)
assert [[1, 2], [3, 4]].collectMany { it } == [1, 2, 3, 4]

// findResult - returns first non-null result of closure
assert [1, 2, 3].findResult { it > 2 ? "found $it" : null } == 'found 3'

// findResults - returns all non-null results of closure
assert [1, 2, 3, 4].findResults { it > 2 ? it * 10 : null } == [30, 40]

// split - partitions list based on condition
assert [1, 2, 3, 4, 5].split { it % 2 == 0 } == [[2, 4], [1, 3, 5]]

// swap
assert [1, 2, 3].swap(0, 2) == [3, 2, 1]
```

### 2.2 Maps

#### Creation and Literals
```groovy
def map = [name: 'Gromit', likes: 'cheese', id: 1234]
assert map instanceof java.util.Map        // LinkedHashMap by default
assert map.get('name') == 'Gromit'
assert map['name'] == 'Gromit'
assert map.name == 'Gromit'                // property notation

def emptyMap = [:]
assert emptyMap.size() == 0
emptyMap.put("foo", 5)
assert emptyMap.size() == 1
assert emptyMap.get("foo") == 5
```

#### Dynamic Keys vs Literal Keys
```groovy
def a = 'Bob'
def ages = [a: 43]                  // key is literal 'a'
assert ages['Bob'] == null
assert ages['a'] == 43

ages = [(a): 43]                    // key is value of variable a = 'Bob'
assert ages['Bob'] == 43
```

#### Property Notation Gotchas
```groovy
def map = [name: 'Gromit', likes: 'cheese', id: 1234]
assert map.class == null                // 'class' is not a key
assert map.get('class') == null
assert map.getClass() == LinkedHashMap  // must use getClass()

map = [1: 'a', (true): 'p', (false): 'q', (null): 'x', 'null': 'z']
assert map.containsKey(1)
assert map.true == null                 // property notation doesn't work for boolean keys
assert map.get(true) == 'p'
assert map.get(false) == 'q'
assert map.null == 'z'                  // string 'null', not null value
assert map.get(null) == 'x'            // actual null key
```

#### Map Cloning (Shallow)
```groovy
def map = [simple: 123, complex: [a: 1, b: 2]]
def map2 = map.clone()
assert map2.get('simple') == map.get('simple')
assert map2.get('complex') == map.get('complex')
map2.get('complex').put('c', 3)
assert map.get('complex').get('c') == 3   // shallow copy shares nested objects
```

#### GString Key Warning
```groovy
def key = 'some key'
def map = [:]
def gstringKey = "${key.toUpperCase()}"
map.put(gstringKey, 'value')
assert map.get('SOME KEY') == null     // GString hash != String hash!
```

#### Iteration
```groovy
def map = [Bob: 42, Alice: 54, Max: 33]

// Entry-based iteration
map.each { entry ->
    println "Name: $entry.key Age: $entry.value"
}

map.eachWithIndex { entry, i ->
    println "$i - Name: $entry.key Age: $entry.value"
}

// Key-value decomposition
map.each { key, value ->
    println "Name: $key Age: $value"
}

map.eachWithIndex { key, value, i ->
    println "$i - Name: $key Age: $value"
}
```

#### Adding and Removing
```groovy
def defaults = [1: 'a', 2: 'b', 3: 'c', 4: 'd']
def overrides = [2: 'z', 5: 'x', 13: 'x']

def result = new LinkedHashMap(defaults)
result.put(15, 't')
result[17] = 'u'                         // subscript assignment
result.putAll(overrides)
assert result == [1: 'a', 2: 'z', 3: 'c', 4: 'd', 5: 'x', 13: 'x', 15: 't', 17: 'u']

// Property notation for adding
emptyMap.foo = 5

// Clearing
def m = [1:'a', 2:'b']
m.clear()
assert m == [:]
```

#### Keys, Values, and Entries
```groovy
def map = [1:'a', 2:'b', 3:'c']

def entries = map.entrySet()
entries.each { entry ->
    assert entry.key in [1,2,3]
    assert entry.value in ['a','b','c']
}

def keys = map.keySet()
assert keys == [1,2,3] as Set
```

#### Filtering and Searching
```groovy
def people = [
    1: [name:'Bob', age: 32, gender: 'M'],
    2: [name:'Johnny', age: 36, gender: 'M'],
    3: [name:'Claire', age: 21, gender: 'F'],
    4: [name:'Amy', age: 54, gender:'F']
]

def bob = people.find { it.value.name == 'Bob' }
def females = people.findAll { it.value.gender == 'F' }

def ageOfBob = bob.value.age
def agesOfFemales = females.collect { it.value.age }
assert ageOfBob == 32
assert agesOfFemales == [21, 54]

def agesOfMales = people.findAll { id, person ->
    person.gender == 'M'
}.collect { id, person ->
    person.age
}
assert agesOfMales == [32, 36]

assert people.every { id, person -> person.age > 18 }
assert people.any { id, person -> person.age == 54 }
```

#### Grouping
```groovy
assert ['a', 7, 'b', [2, 3]].groupBy { it.class } ==
    [(String): ['a', 'b'], (Integer): [7], (ArrayList): [[2, 3]]]

assert [
    [name: 'Clark', city: 'London'], [name: 'Sharma', city: 'London'],
    [name: 'Maradona', city: 'LA'], [name: 'Zhang', city: 'HK'],
    [name: 'Ali', city: 'HK'], [name: 'Liu', city: 'HK'],
].groupBy { it.city } == [
    London: [[name: 'Clark', city: 'London'], [name: 'Sharma', city: 'London']],
    LA    : [[name: 'Maradona', city: 'LA']],
    HK    : [[name: 'Zhang', city: 'HK'], [name: 'Ali', city: 'HK'], [name: 'Liu', city: 'HK']],
]
```

#### Additional Map Methods
```groovy
// subMap - select subset of keys
def map = [a: 1, b: 2, c: 3, d: 4]
assert map.subMap(['a', 'c']) == [a: 1, c: 3]

// collect on Map
def result = map.collect { k, v -> "$k=$v" }
assert result == ['a=1', 'b=2', 'c=3', 'd=4']

// collectEntries - transform entries into new map
def result2 = [1, 2, 3].collectEntries { [(it): it * 10] }
assert result2 == [1: 10, 2: 20, 3: 30]

// withDefault
def map2 = [:].withDefault { key -> key.toUpperCase() }
assert map2.foo == 'FOO'

// sort
def sorted = [c: 3, a: 1, b: 2].sort()
assert sorted.keySet() as List == ['a', 'b', 'c']

// plus (+) operator
assert [a: 1] + [b: 2] == [a: 1, b: 2]

// spread map operator
assert [*: [a: 1, b: 2], c: 3] == [a: 1, b: 2, c: 3]

// inject
def sum = [a: 1, b: 2, c: 3].inject(0) { acc, entry -> acc + entry.value }
assert sum == 6

// count
assert [a: 1, b: 2, c: 3].count { k, v -> v > 1 } == 2

// findResult
assert [a: 1, b: 2, c: 3].findResult { k, v -> v > 2 ? k : null } == 'c'

// getOrDefault (Java 8)
def m = [a: 1, b: 2]
assert m.getOrDefault('c', 42) == 42

// any, every
assert [a: 1, b: 2, c: 3].any { k, v -> v > 2 }
assert [a: 1, b: 2, c: 3].every { k, v -> v > 0 }
```

### 2.3 Ranges

#### Creation
```groovy
// Inclusive range
def range = 5..8
assert range.size() == 4
assert range.get(2) == 7
assert range[2] == 7
assert range instanceof java.util.List
assert range.contains(5)
assert range.contains(8)

// Half-open range (exclusive end)
range = 5..<8
assert range.size() == 3
assert range.get(2) == 7
assert range[2] == 7
assert range.contains(5)
assert !range.contains(8)

// Properties
range = 1..10
assert range.from == 1
assert range.to == 10
```

#### String Ranges
```groovy
def range = 'a'..'d'
assert range.size() == 4
assert range.get(2) == 'c'
assert range[2] == 'c'
assert range.contains('a')
assert range.contains('d')
assert !range.contains('e')
```

#### Iteration
```groovy
for (i in 1..10) {
    println "Hello ${i}"
}

(1..10).each { i ->
    println "Hello ${i}"
}
```

#### Ranges in Switch
```groovy
switch (years) {
    case 1..10: interestRate = 0.076; break;
    case 11..25: interestRate = 0.052; break;
    default: interestRate = 0.037;
}
```

#### Custom Ranges
Any type that implements `Comparable` and provides `next()` and `previous()` methods can define ranges.

#### Ranges as Subscript Indices
```groovy
def list = [10, 11, 12, 13]
def answer = list[2, 3]           // multiple indices
assert answer == [12, 13]

list = 100..200
def sub = list[1, 3, 20..25, 33]  // mixed indices and ranges
assert sub == [101, 103, 120, 121, 122, 123, 124, 125, 133]

// Range assignment
list = ['a', 'x', 'x', 'd']
list[1..2] = ['b', 'c']
assert list == ['a', 'b', 'c', 'd']
```

### 2.4 Syntax Enhancements

#### GPath Support
```groovy
def listOfMaps = [['a': 11, 'b': 12], ['a': 21, 'b': 22]]
assert listOfMaps.a == [11, 21]            // GPath - property access on list
assert listOfMaps*.a == [11, 21]           // spread-dot equivalent

listOfMaps = [['a': 11, 'b': 12], ['a': 21, 'b': 22], null]
assert listOfMaps*.a == [11, 21, null]     // spread-dot is null-safe
assert listOfMaps*.a == listOfMaps.collect { it?.a }
assert listOfMaps.a == [11, 21]            // GPath skips nulls
```

#### Spread Operator in Map Literals
```groovy
assert ['z': 900, *: ['a': 100, 'b': 200], 'a': 300] == ['a': 300, 'b': 200, 'z': 900]
assert [*: [3: 3, *: [5: 5]], 7: 7] == [3: 3, 5: 5, 7: 7]

def f = { [1: 'u', 2: 'v', 3: 'w'] }
assert [*: f(), 10: 'zz'] == [1: 'u', 10: 'zz', 2: 'v', 3: 'w']

// Spread map in method arguments
f = { map -> map.c }
assert f(*: ['a': 10, 'b': 20, 'c': 30], 'e': 50) == 30

// Spread list + spread map combined
f = { m, i, j, k -> [m, i, j, k] }
assert f('e': 100, *[4, 5], *: ['a': 10, 'b': 20, 'c': 30], 6) ==
    [["e": 100, "b": 20, "c": 30, "a": 10], 4, 5, 6]
```

#### Star-Dot Operator (*.)
```groovy
assert [1, 3, 5] == ['a', 'few', 'words']*.size()

class Person {
    String name
    int age
}
def persons = [new Person(name:'Hugo', age:17), new Person(name:'Sandra', age:19)]
assert [17, 19] == persons*.age
```

#### Subscript Slicing
```groovy
def text = 'nice cheese gromit!'
def x = text[2]
assert x == 'c'

def sub = text[5..10]
assert sub == 'cheese'

def list = [10, 11, 12, 13]
def answer = list[2, 3]
assert answer == [12, 13]

// Negative indices
text = "nice cheese gromit!"
x = text[-1]
assert x == "!"
def name = text[-7..-2]
assert name == "gromit"

// Backwards range
text = "nice cheese gromit!"
name = text[3..1]                    // reverses
assert name == "eci"
```

### 2.5 Arrays

Arrays have GDK methods similar to lists:
```groovy
Number[] nums = [5, 6, 7, 8]
assert nums[1] == 6
assert nums.getAt(2) == 7
assert nums[-1] == 8
assert nums instanceof Number[]

int[] primes = [2, 3, 5, 7]
assert primes instanceof int[]

def odds = [1, 3, 5] as int[]
assert odds instanceof int[]

def evens = new int[]{2, 4, 6}
assert evens instanceof int[]

// GDK methods on arrays
int[] nums2 = [1, 2, 3]
def doubled = nums2.collect { it * 2 }
assert doubled == [2, 4, 6] && doubled instanceof List

assert nums2.any { it > 2 }
assert nums2.every { it < 4 }
assert nums2.average() == 2
assert nums2.min() == 1
assert nums2.max() == 3
assert nums2.sum() == 6
assert nums2.indices == [0, 1, 2]
assert nums2.swap(0, 2) == [3, 2, 1] as int[]
```

---

## Part 3: Handy Utilities

### 3.1 ConfigSlurper

Reads configuration files written as Groovy scripts. Produces a `ConfigObject` (specialized `java.util.Map`).

#### Basic Parsing
```groovy
def config = new ConfigSlurper().parse('''
    app.date = new Date()
    app.age  = 42
    app {
        name = "Test${42}"
    }
''')

assert config.app.date instanceof Date
assert config.app.age == 42
assert config.app.name == 'Test42'
```

#### ConfigObject Never Returns Null
```groovy
def config = new ConfigSlurper().parse('''
    app.date = new Date()
    app.age  = 42
    app.name = "Test${42}"
''')

assert config.test != null   // returns empty ConfigObject, never null
```

#### Escaped Dot Keys
```groovy
def config = new ConfigSlurper().parse('''
    app."person.age"  = 42
''')

assert config.app."person.age" == 42
```

#### Environment Support
```groovy
def config = new ConfigSlurper('development').parse('''
  environments {
       development {
           app.port = 8080
       }
       test {
           app.port = 8082
       }
       production {
           app.port = 80
       }
  }
''')

assert config.app.port == 8080
```

#### Custom Conditional Blocks
```groovy
def slurper = new ConfigSlurper()
slurper.registerConditionalBlock('myProject', 'developers')

def config = slurper.parse('''
  sendMail = true

  myProject {
       developers {
           sendMail = false
       }
  }
''')

assert !config.sendMail
```

#### Converting to Properties
```groovy
def config = new ConfigSlurper().parse('''
    app.date = new Date()
    app.age  = 42
    app {
        name = "Test${42}"
    }
''')

def properties = config.toProperties()
assert properties."app.date" instanceof String   // all values become Strings
assert properties."app.age" == '42'
assert properties."app.name" == 'Test42'
```

### 3.2 Expando

Dynamically expandable object. Properties and methods can be added at runtime.

#### Dynamic Properties
```groovy
def expando = new Expando()
expando.name = 'John'
assert expando.name == 'John'
```

#### Dynamic Methods (Closures)
```groovy
def expando = new Expando()
expando.toString = { -> 'John' }
expando.say = { String s -> "John says: ${s}" }

assert expando as String == 'John'
assert expando.say('Hi') == 'John says: Hi'
```

### 3.3 Observable Collections

Observable collections fire `java.beans.PropertyChangeEvent` events when modified.

#### ObservableList - ElementAddedEvent
```groovy
def event
def listener = {
    if (it instanceof ObservableList.ElementEvent) {
        event = it
    }
} as PropertyChangeListener

def observable = [1, 2, 3] as ObservableList
observable.addPropertyChangeListener(listener)

observable.add 42

assert event instanceof ObservableList.ElementAddedEvent

def elementAddedEvent = event as ObservableList.ElementAddedEvent
assert elementAddedEvent.changeType == ObservableList.ChangeType.ADDED
assert elementAddedEvent.index == 3
assert elementAddedEvent.oldValue == null
assert elementAddedEvent.newValue == 42
```

Note: Adding an element fires TWO events: an `ElementAddedEvent` (content change) and a plain `PropertyChangeEvent` (size change).

#### ObservableList - ElementClearedEvent
```groovy
def event
def listener = {
    if (it instanceof ObservableList.ElementEvent) {
        event = it
    }
} as PropertyChangeListener

def observable = [1, 2, 3] as ObservableList
observable.addPropertyChangeListener(listener)

observable.clear()

assert event instanceof ObservableList.ElementClearedEvent

def elementClearedEvent = event as ObservableList.ElementClearedEvent
assert elementClearedEvent.values == [1, 2, 3]
assert observable.size() == 0
```

#### Event Types
- `ObservableList.ElementAddedEvent` - element added (properties: changeType, index, oldValue, newValue)
- `ObservableList.ElementRemovedEvent` - element removed
- `ObservableList.ElementUpdatedEvent` - element replaced
- `ObservableList.ElementClearedEvent` - list cleared (property: values)
- `ObservableList.MultiElementAddedEvent` - multiple elements added (e.g., addAll)
- `ObservableList.MultiElementRemovedEvent` - multiple elements removed

#### ChangeType Enum
`ObservableList.ChangeType`: `ADDED`, `UPDATED`, `REMOVED`, `CLEARED`, `MULTI_ADD`, `MULTI_REMOVE`, `NONE`

#### ObservableMap and ObservableSet
Follow the same pattern as ObservableList with appropriate event types.

```groovy
def map = [:] as ObservableMap
map.addPropertyChangeListener(listener)

def set = [] as ObservableSet
set.addPropertyChangeListener(listener)
```

---

## Part 4: Testing Guide

### 4.1 Power Assertions

Groovy's `assert` keyword provides detailed diagnostic output on failure:

```groovy
def x = 1
assert x == 2

// Output:
// Assertion failed:
// assert x == 2
//        | |
//        1 false
```

Key characteristics:
- Enabled by default (unlike Java assertions which need `-ea`)
- Shows intermediate expression values at each evaluation point
- Works with complex expressions - shows nested sub-expression values
- Disabled for custom messages: `assert expression1 : expression2`
- Side-effecting methods may produce misleading error messages (stores references not copies)

Complex example:
```groovy
def x = [1,2,3,4,5]
assert (x << 6) == [6,7,8,9,10]
// Shows evaluation results from outer to inner expressions
```

### 4.2 Mocking and Stubbing

#### Map Coercion
```groovy
class TranslationService {
    String convert(String key) {
        return "test"
    }
}

def service = [convert: { String key -> 'some text' }] as TranslationService
assert 'some text' == service.convert('key.text')
```
Map keys = method names, values = Closure implementations.

#### Closure Coercion (SAM types)
```groovy
def service = { String key -> 'some text' } as TranslationService
assert 'some text' == service.convert('key.text')

// Implicit SAM coercion
abstract class BaseService {
    abstract void doSomething()
}
BaseService service = { -> println 'doing something' }
service.doSomething()
```

#### MockFor (Strictly Ordered)
```groovy
class Person {
    String first, last
}

class Family {
    Person father, mother
    def nameOfMother() { "$mother.first $mother.last" }
}

def mock = new MockFor(Person)
mock.demand.getFirst { 'dummy' }
mock.demand.getLast { 'name' }
mock.use {
    def mary = new Person(first:'Mary', last:'Smith')
    def f = new Family(mother:mary)
    assert f.nameOfMother() == 'dummy name'
}
mock.expect.verify()
```

Features:
- Sequence-dependent: calls must occur in the order demands are defined
- `demand.methodName { closure }` sets up expected method behavior
- `demand.methodName(min..max) { closure }` sets call count range
- `use { ... }` activates the mock for code under test
- `expect.verify()` confirms all demands were satisfied
- Calls `proxyInstance()` or `proxyDelegateInstance()` to create mock objects

Limitation: Cannot be used with `@CompileStatic` classes or Java classes.

#### StubFor (Loosely Ordered)
```groovy
def stub = new StubFor(Person)
stub.demand.with {
    getLast { 'name' }
    getFirst { 'dummy' }
}
stub.use {
    def john = new Person(first:'John', last:'Smith')
    def f = new Family(father:john)
    assert f.father.first == 'dummy'
    assert f.father.last == 'name'
}
stub.expect.verify()
```

Features:
- Sequence-independent: calls can occur in any order
- Verification optional (but recommended)
- Otherwise same API as MockFor

#### Expando Meta-Class (EMC) for Mocking

**Adding methods to existing classes**:
```groovy
String.metaClass.swapCase = {->
    def sb = new StringBuffer()
    delegate.each {
        sb << (Character.isUpperCase(it as char) ?
               Character.toLowerCase(it as char) :
               Character.toUpperCase(it as char))
    }
    sb.toString()
}

def s = "heLLo, worLD!"
assert s.swapCase() == 'HEllO, WORld!'
```

**Mocking static methods**:
```groovy
class Book { String title }

Book.metaClass.static.create << { String title -> new Book(title:title) }
def b = Book.create("The Stand")
assert b.title == 'The Stand'
```

**Mocking constructors**:
```groovy
Book.metaClass.constructor << { String title -> new Book(title:title) }
def b = new Book("The Stand")
assert b.title == 'The Stand'
```

**Per-instance metaclass changes**:
```groovy
def b = new Book(title: "The Stand")
b.metaClass.getTitle { -> 'My Title' }
assert b.title == 'My Title'
```

**Cleanup** (important for test isolation):
```groovy
GroovySystem.metaClassRegistry.removeMetaClass(String)
```

### 4.3 GDK Testing Methods

#### shouldFail

**In GroovyTestCase** (returns message String):
```groovy
void testInvalidIndexAccess1() {
    def numbers = [1,2,3,4]
    shouldFail {
        numbers.get(4)
    }
}

void testInvalidIndexAccess2() {
    def numbers = [1,2,3,4]
    shouldFail IndexOutOfBoundsException, {
        numbers.get(4)
    }
}

void testInvalidIndexAccess3() {
    def numbers = [1,2,3,4]
    def msg = shouldFail IndexOutOfBoundsException, {
        numbers.get(4)
    }
    assert msg.contains('Index: 4, Size: 4')
}
```

**In GroovyAssert** (returns exception object):
```groovy
import static groovy.test.GroovyAssert.shouldFail

def e = shouldFail {
    throw new RuntimeException('foo', new RuntimeException('bar'))
}
assert e instanceof RuntimeException
assert e.message == 'foo'
assert e.cause.message == 'bar'
```

Key difference: `GroovyTestCase.shouldFail` returns the exception message, `GroovyAssert.shouldFail` returns the exception itself.

#### combinations and eachCombination

```groovy
void testCombinations() {
    def combinations = [[2, 3],[4, 5, 6]].combinations()
    assert combinations == [[2, 4], [3, 4], [2, 5], [3, 5], [2, 6], [3, 6]]
}

void testEachCombination() {
    [[2, 3],[4, 5, 6]].eachCombination { println it[0] + it[1] }
}
```

### 4.4 JUnit 3 - GroovyTestCase

```groovy
class MyTestCase extends GroovyTestCase {

    void testAssertions() {
        assertTrue(1 == 1)
        assertEquals("test", "test")

        def x = "42"
        assertNotNull "x must not be null", x
        assertNull null

        assertSame x, x
    }
}
```

**assertScript** - run Groovy script as test:
```groovy
void testScriptAssertions() {
    assertScript '''
        def x = 1
        def y = 2
        assert x + y == 3
    '''
}
```

**notYetImplemented** - marks test as expected to fail:
```groovy
void testNotYetImplemented1() {
    if (notYetImplemented()) return    // method call style
    assert 1 == 2                     // will "pass" because it's expected to fail
}

@NotYetImplemented                    // annotation style
void testNotYetImplemented2() {
    assert 1 == 2
}
```

### 4.5 JUnit 4 - groovy.test.GroovyAssert

```groovy
import org.junit.Test
import static groovy.test.GroovyAssert.shouldFail

class JUnit4ExampleTests {

    @Test
    void indexOutOfBoundsAccess() {
        def numbers = [1,2,3,4]
        shouldFail {
            numbers.get(4)
        }
    }
}
```

**JUnit 5 features** (later Groovy versions):
```groovy
class MyTest {
    @Test
    void streamSum() {
        assertTrue(Stream.of(1, 2, 3)
            .mapToInt(i -> i)
            .sum() > 5, () -> "Sum should be greater than 5")
    }

    @RepeatedTest(value=2, name = "{displayName} {currentRepetition}/{totalRepetitions}")
    void streamSumRepeated() {
        assert Stream.of(1, 2, 3).mapToInt(i -> i).sum() == 6
    }

    @ParameterizedTest
    @ValueSource(strings = ["racecar", "radar", "able was I ere I saw elba"])
    void palindromes(String candidate) {
        assert isPalindrome(candidate)
    }

    @TestFactory
    def dynamicTestCollection() {[
        dynamicTest("Add test") { -> assert 1 + 1 == 2 },
        dynamicTest("Multiply Test", () -> { assert 2 * 3 == 6 })
    ]}
}
```

### 4.6 Spock Framework

Spock is the de facto standard for Groovy testing. Based on JUnit but with a rich DSL.

#### Specification Structure
```groovy
import spock.lang.*

class MyFirstSpecification extends Specification {
    // fields
    // fixture methods
    // feature methods
    // helper methods
}
```

#### Fixture Methods
```groovy
def setup() {}          // runs before EVERY feature method
def cleanup() {}        // runs after EVERY feature method
def setupSpec() {}      // runs ONCE before first feature method
def cleanupSpec() {}    // runs ONCE after last feature method
```

Rules:
- `setupSpec()` and `cleanupSpec()` can only access `@Shared` or static fields
- Superclass fixture methods run first (setup) or last (cleanup)
- Each feature method gets a fresh specification instance

#### Feature Methods (Named with Strings)
```groovy
def "pushing an element on the stack"() {
    // blocks go here
}
```

Must contain at least one explicit labeled block.

#### Blocks

**given/setup** - set up test preconditions:
```groovy
given: "a new stack instance is created"
def stack = new Stack()
def elem = "push me"
```

**when/then** - stimulus and response (always paired):
```groovy
when:
stack.push(elem)

then:
!stack.empty
stack.size() == 1
stack.peek() == elem
```

`when` blocks contain arbitrary code. `then` blocks contain conditions, exception conditions, interactions, and variable definitions. Conditions are implicit boolean assertions (no `assert` needed). Multiple when-then pairs allowed.

**expect** - combined stimulus and response for pure functions:
```groovy
expect:
Math.max(1, 2) == 2
```

**cleanup** - resource cleanup (runs even on exceptions):
```groovy
cleanup:
file.delete()
```

**where** - data-driven parameterization (always last):
```groovy
where:
a | b || c
1 | 3 || 3
7 | 4 || 7
```

**and** - documentation label (no semantic effect):
```groovy
given: "open a database connection"
// ...
and: "seed the customer table"
// ...
```

#### Exception Conditions
```groovy
when:
stack.pop()

then:
thrown(EmptyStackException)
stack.empty

// Bind exception to variable
when:
stack.pop()

then:
def e = thrown(EmptyStackException)
e.cause == null

// Type-first syntax
when:
stack.pop()

then:
EmptyStackException e = thrown()
e.cause == null

// Negative
when:
map.put(null, "elem")

then:
notThrown(NullPointerException)
```

#### Data-Driven Testing

**Data Tables**:
```groovy
def "maximum of two numbers"(int a, int b, int c) {
    expect:
    Math.max(a, b) == c

    where:
    a | b | c
    1 | 3 | 3
    7 | 4 | 7
    0 | 0 | 0
}
```

First row = variable names, subsequent rows = values. Each row = separate iteration. `||` separates inputs from expected outputs (visual only, no semantic difference).

**Data Pipes**:
```groovy
where:
a << [1, 7, 0]
b << [3, 4, 0]
c << [3, 7, 0]
```

Any Iterable, Collection, or String works as data provider.

**Multi-Variable Data Pipes**:
```groovy
where:
[a, b, c] << sql.rows("select a, b, c from maxdata")

// Ignore columns with underscore
[a, b, _, c] << sql.rows("select * from maxdata")
```

**Data Variable Assignment**:
```groovy
where:
a = 3
b = Math.random() * 100
c = a > b ? a : b
```

**Combining approaches**:
```groovy
where:
a | _
3 | _
7 | _

b << [5, 0, 0]
c = a > b ? a : b
```

**@Unroll** - report each iteration separately:
```groovy
@Unroll
def "maximum of #a and #b is #c"() {
    expect:
    Math.max(a, b) == c

    where:
    a | b | c
    1 | 3 | 3
    7 | 4 | 7
}
// Reports: "maximum of 1 and 3 is 3", "maximum of 7 and 4 is 7"
```

Placeholder syntax: `#variableName`, `#variable.property`, `#variable.method()` (zero-arg only).

Can be applied at class level to affect all data-driven features.

#### Interaction-Based Testing

**Creating Mocks**:
```groovy
def subscriber = Mock(Subscriber)          // explicit type
Subscriber subscriber = Mock()             // inferred type
def subscriber = Mock(Subscriber) {        // with interactions
    1 * receive("hello")
}
```

**Interaction Syntax**: `cardinality * target.method(args) >> response`

**Cardinality** (expected call count):
```groovy
1 * subscriber.receive("hello")        // exactly one
0 * subscriber.receive("hello")        // zero calls
(1..3) * subscriber.receive("hello")   // 1 to 3 calls
(1.._) * subscriber.receive("hello")   // at least one
(_..3) * subscriber.receive("hello")   // at most three
_ * subscriber.receive("hello")        // any number
```

**Target Constraints**:
```groovy
1 * subscriber.receive("hello")   // specific mock
1 * _.receive("hello")            // any mock
```

**Method Constraints**:
```groovy
1 * subscriber.receive("hello")      // exact name
1 * subscriber./r.*e/("hello")       // regex match
1 * subscriber.status                // getter shorthand
1 * subscriber.setStatus("ok")       // setter (method syntax only)
1 * subscriber._(*_)                 // any method, any args
1 * subscriber._                     // shortcut for above
1 * _._                              // any method on any mock
1 * _                                // shortcut for above
```

**Argument Constraints**:
```groovy
1 * subscriber.receive("hello")            // exact value
1 * subscriber.receive(!"hello")           // not equal
1 * subscriber.receive()                   // no args
1 * subscriber.receive(_)                  // any single arg (including null)
1 * subscriber.receive(*_)                 // any arg list (varargs)
1 * subscriber.receive(!null)              // any non-null
1 * subscriber.receive(_ as String)        // any non-null of type
1 * subscriber.receive({ it.size() > 3 })  // satisfies predicate
```

**Stubbing** (return values):
```groovy
// Fixed value
subscriber.receive(_) >> "ok"

// Different responses for different args
subscriber.receive("message1") >> "ok"
subscriber.receive("message2") >> "fail"

// Sequence of values
subscriber.receive(_) >>> ["ok", "error", "error", "ok"]

// Computed value
subscriber.receive(_) >> { args -> args[0].size() > 3 ? "ok" : "fail" }
subscriber.receive(_) >> { String message -> message.size() > 3 ? "ok" : "fail" }

// Side effect (exception)
subscriber.receive(_) >> { throw new InternalError("ouch") }

// Chained responses
subscriber.receive(_) >>> ["ok", "fail", "ok"] >> { throw new InternalError() } >> "ok"
```

**Combining Mocking and Stubbing** (must be in same interaction):
```groovy
1 * subscriber.receive("message1") >> "ok"    // verify AND return
1 * subscriber.receive("message2") >> "fail"
```

**Strict Mocking**:
```groovy
then:
1 * subscriber.receive("hello")    // demand this call
_ * auditing._                     // allow auditing calls
0 * _                              // forbid everything else (must be last)
```

**Ordering** - split `then:` blocks to enforce order:
```groovy
then:
2 * subscriber.receive("hello")      // these must happen first

then:
1 * subscriber.receive("goodbye")    // then this
```

#### Mock Types

**Mock()** - can mock and stub, lenient by default:
```groovy
def subscriber = Mock(Subscriber)
```

**Stub()** - stubbing only, error if used for mocking assertions:
```groovy
def subscriber = Stub(Subscriber)
def subscriber = Stub(Subscriber) {
    receive("message1") >> "ok"
    receive("message2") >> "fail"
}
```

**Spy()** - wraps real object, delegates calls:
```groovy
def subscriber = Spy(SubscriberImpl, constructorArgs: ["Fred"])

// Stub specific method
subscriber.receive(_) >> "ok"

// Call real method with modified logic
subscriber.receive(_) >> { String message ->
    callRealMethod()
    message.size() > 3 ? "ok" : "fail"
}
```

#### Groovy-Specific Mocks

```groovy
def subscriber = GroovyMock(Subscriber)    // can mock dynamic methods
def subscriber = GroovyStub(Subscriber)
def subscriber = GroovySpy(SubscriberImpl)

// Global mocks (replace ALL instances)
def anySubscriber = GroovyMock(RealSubscriber, global: true)
when:
publisher.publish("message")
then:
2 * anySubscriber.receive("message")

// Mock constructors
def anySubscriber = GroovySpy(RealSubscriber, global: true)
1 * new RealSubscriber("Fred")
new RealSubscriber("Fred") >> new RealSubscriber("Barney")

// Mock static methods
def anySubscriber = GroovySpy(RealSubscriber, global: true)
1 * RealSubscriber.someStaticMethod("hello") >> 42
```

#### Grouping with with()
```groovy
// Group conditions
when:
def pc = shop.buyPc()
then:
with(pc) {
    vendor == "Sunny"
    clockRate >= 2333
    ram >= 4096
    os == "Linux"
}

// Group interactions
with(subscriber) {
    1 * receive("hello")
    1 * receive("goodbye")
}
```

#### old() Method
Access pre-stimulus state in `then` blocks:
```groovy
when:
list.add(42)

then:
list.size() == old(list.size()) + 1
```

#### Interaction Blocks
```groovy
then:
interaction {
    def message = "hello"
    1 * subscriber.receive(message)
}
```

#### Spock Extensions (Annotations)

```groovy
@Timeout(5)                          // fail after 5 seconds
@Timeout(value = 100, unit = TimeUnit.MILLISECONDS)

@Ignore                              // skip this feature
@Ignore("TODO")

@IgnoreRest                          // run only this, skip all others

@IgnoreIf({ os.windows })           // conditional skip
@IgnoreIf({ System.getProperty("os.name").contains("windows") })

@Requires({ os.windows })           // conditional run (inverse of IgnoreIf)

@PendingFeature                      // expected to fail (passes -> error)

@Stepwise                            // run features in order, stop on failure

@Shared                              // share field across iterations
@Shared res = new VeryExpensiveResource()

@AutoCleanup                         // auto-call close() after spec
@AutoCleanup("dispose")             // custom cleanup method
@AutoCleanup(quiet = true)          // suppress cleanup exceptions

@FailsWith(SomeException)           // document known bugs

@Use(ListExtensions)                // activate Groovy categories

@ConfineMetaClassChanges([String])  // isolate metaclass changes

@RestoreSystemProperties            // save/restore system properties

@Title("Readable Title")
@Narrative("As a user I want foo so that bar")
@See("http://example.com/spec")
@Issue("http://bugs.org/FOO-1")
@Subject([Foo, Bar])
```

#### MockDetector
```groovy
def detector = new MockDetector()
def list1 = []
def list2 = Mock(List)

expect:
!detector.isMock(list1)
detector.isMock(list2)

def mock = detector.asMock(list2)
mock.name == "list2"
mock.type == List
mock.nature == MockNature.MOCK
```

### 4.7 Geb Functional Testing

Browser-based functional testing using WebDriver.

#### Browser API
```groovy
import geb.Browser
import org.openqa.selenium.firefox.FirefoxDriver

def browser = new Browser(driver: new FirefoxDriver(), baseUrl: 'http://myhost:8080/myapp')
browser.drive {
    go "/login"

    $("#username").text = 'John'
    $("#password").text = 'Doe'

    $("#loginButton").click()

    assert title == "My Application - Dashboard"
}
```

#### JUnit 4 Integration
```groovy
class SearchTests extends geb.junit4.GebTest {

    @Test
    void executeSearch() {
        go 'http://somehost/myapp/search'
        $('#searchField').text = 'John Doe'
        $('#searchButton').click()

        assert $('.searchResult a').first().text() == 'Mr. John Doe'
    }
}
```

Key Geb features:
- `go` - navigate to URLs
- `$()` - jQuery-like CSS selector for DOM access
- `.click()` - trigger element clicks
- `.text` / `.text =` - read/set text content
- `.first()` - get first matched element
- `title` - page title
- Page Object pattern support
- Module support for reusable components
- JavaScript integration via `js` variable

---

## Part 5: Date/Time GDK Enhancements

### 5.1 Legacy Date/Calendar

```groovy
import static java.util.Calendar.*

def cal = Calendar.instance
cal[YEAR] = 2000
cal[MONTH] = JANUARY
cal[DAY_OF_MONTH] = 1
assert cal[DAY_OF_WEEK] == SATURDAY

// Date arithmetic
Date date = Date.parse("yyyy-MM-dd HH:mm", "2010-05-23 09:01", utc)
def prev = date - 1       // subtract days
def next = date + 1       // add days
def diffInDays = next - prev
assert diffInDays == 2

// Iteration
int count = 0
prev.upto(next) { count++ }
assert count == 3

// Formatting / Parsing
def orig = '2000-01-01'
def newYear = Date.parse('yyyy-MM-dd', orig)
assert newYear.format('dd/MM/yyyy') == '01/01/2000'

// copyWith
def newYearsEve = newYear.copyWith(year: 1999, month: DECEMBER, dayOfMonth: 31)
```

### 5.2 JSR 310 (java.time) Enhancements

```groovy
// Parsing with patterns
def date = LocalDate.parse('Jun 3, 04', 'MMM d, yy')
def time = LocalTime.parse('4:45', 'H:mm')

// Addition/Subtraction
def aprilFools = LocalDate.of(2018, Month.APRIL, 1)
def next = aprilFools + 365              // add days (int)
def prev = aprilFools - 17               // subtract days (int)
def next2 = aprilFools + Period.ofDays(365)  // add Period

// Multiplication/Division of periods
def period = Period.ofMonths(1) * 2
def duration = Duration.ofSeconds(10) / 5

// Increment/Decrement
def year = Year.of(2000)
--year
assert year.value == 1999

// Property notation
def date2 = LocalDate.of(2018, Month.MARCH, 12)
assert date2[ChronoField.YEAR] == 2018

// Left-shift operator for combining
MonthDay monthDay = Month.JUNE << 3
LocalDate date3 = monthDay << Year.of(2015)
LocalDateTime dateTime = date3 << LocalTime.NOON

// Right-shift for periods
def period2 = newYears >> aprilFools   // Period between dates
def duration2 = LocalTime.NOON >> (LocalTime.NOON + 30)  // Duration

// upto/downto with optional ChronoUnit
start.upto(end) { next -> println next.dayOfWeek }
start.upto(end, ChronoUnit.MONTHS) { next -> ... }

// Conversion between legacy and JSR 310
Date legacy = Date.parse('yyyy-MM-dd', '2010-04-03')
assert legacy.toLocalDate() == LocalDate.of(2010, 4, 3)
assert legacy.toLocalTime() == LocalTime.of(...)
assert legacy.toYear() == Year.of(2010)
assert legacy.toMonth() == Month.APRIL
assert legacy.toDayOfWeek() == DayOfWeek.SATURDAY

def valentines = LocalDate.of(2018, Month.FEBRUARY, 14)
assert valentines.toDate().format('MMMM dd, yyyy') == 'February 14, 2018'
```
