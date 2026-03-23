# Groovy 2.4.21 Language Core Knowledge Base

## 1. SYNTAX

### 1.1 Comments

**Single-line**: Start with `//`, extend to end of line.
```groovy
// standalone comment
println "hello" // trailing comment
```

**Multi-line**: Delimited by `/* ... */`, can appear anywhere.
```groovy
/* a standalone multiline comment
   spanning two lines */
println 1 /* one */ + 2 /* two */
```

**GroovyDoc**: Start with `/**`, end with `*/`. Associated with types, fields, properties, methods. Support `@param`, `@return`, etc.
```groovy
/**
 * A Class description
 */
class Person {
    /** the name of the person */
    String name

    /**
     * Creates a greeting method for a certain person.
     * @param otherPerson the person to greet
     * @return a greeting message
     */
    String greet(String otherPerson) {
       "Hello ${otherPerson}"
    }
}
```

**Shebang line**: `#!` must be the FIRST character of the file. Enables direct UNIX execution.
```groovy
#!/usr/bin/env groovy
println "Hello from the shebang line"
```

### 1.2 Keywords

Reserved: `as`, `assert`, `break`, `case`, `catch`, `class`, `const`, `continue`, `def`, `default`, `do`, `else`, `enum`, `extends`, `false`, `finally`, `for`, `goto`, `if`, `implements`, `import`, `in`, `instanceof`, `interface`, `new`, `null`, `package`, `return`, `super`, `switch`, `this`, `throw`, `throws`, `trait`, `true`, `try`, `while`.

**Key rule**: All keywords are valid identifiers when following a dot: `foo.as`, `foo.in`, `foo.return` are legal.

### 1.3 Identifiers

**Normal identifiers**: Start with letter, `$`, or `_`. Cannot start with a number. Valid: `name`, `item3`, `with_underscore`, `$dollarStart`. Invalid: `3tier`, `a+b`, `a#b`.

**Quoted identifiers**: Appear after a dot in dotted expressions. Allow any characters including spaces, dashes, etc. Can use any string type as the quote delimiter.
```groovy
def map = [:]
map."an identifier with a space" = "ALLOWED"
map.'with-dash-signs' = "ALLOWED"
map.'''triple single quote'''
map."""triple double quote"""
map./slashy string/
map.$/dollar slashy string/$
```

GStrings work in quoted identifiers and interpolate: `map."Simpson-${firstname}"`.

### 1.4 Strings (All 6 Types)

#### String Summary Table

| Type | Syntax | Interpolated | Multiline | Escape Char | Java Type |
|------|--------|-------------|-----------|-------------|-----------|
| Single quoted | `'...'` | No | No | `\` | `java.lang.String` |
| Triple single quoted | `'''...'''` | No | Yes | `\` | `java.lang.String` |
| Double quoted | `"..."` | Yes | No | `\` | `String` or `GString` |
| Triple double quoted | `"""..."""` | Yes | Yes | `\` | `String` or `GString` |
| Slashy | `/.../` | Yes | Yes | `\` | `String` or `GString` |
| Dollar slashy | `$/.../$` | Yes | Yes | `$` | `String` or `GString` |

#### Single Quoted Strings
Plain `java.lang.String`. No interpolation.
```groovy
'a single quoted string'
```

#### String Concatenation
All Groovy string types support `+`:
```groovy
assert 'ab' == 'a' + 'b'
```

#### Triple Single Quoted Strings
Multiline, no interpolation, plain `java.lang.String`.
```groovy
def aMultilineString = '''line one
line two
line three'''
```

**Stripping first newline**: Escape with backslash:
```groovy
def strippedFirstNewline = '''\
line one
line two
line three
'''
```

#### Escape Sequences (for single-quoted and triple-single-quoted)
| Escape | Meaning |
|--------|---------|
| `\t` | tab |
| `\b` | backspace |
| `\n` | newline |
| `\r` | carriage return |
| `\f` | formfeed |
| `\\` | backslash |
| `\'` | single quote |
| `\"` | double quote (in double-quoted strings) |
| `\uXXXX` | Unicode character (4 hex digits) |

```groovy
'an escaped single quote: \' needs a backslash'
'an escaped escape character: \\ needs a double backslash'
'The Euro currency symbol: \u20AC'
```

#### Double Quoted Strings
`java.lang.String` without interpolation, `groovy.lang.GString` with interpolation.

**String Interpolation** uses `${}` for expressions or `$` for dotted expressions:
```groovy
def name = 'Guillaume'
def greeting = "Hello ${name}"
assert greeting.toString() == 'Hello Guillaume'

def sum = "The sum of 2 and 3 equals ${2 + 3}"
assert sum.toString() == 'The sum of 2 and 3 equals 5'

// Statements inside interpolation:
"The sum of 1 and 2 is equal to ${def a = 1; def b = 2; a + b}"
```

**Dotted expressions** use single `$`:
```groovy
def person = [name: 'Guillaume', age: 36]
assert "$person.name is $person.age years old" == 'Guillaume is 36 years old'
```

**GOTCHA**: Only `a.b`, `a.b.c` dotted expressions work with bare `$`. Method calls, closures, operators, and parentheses require `${}`:
```groovy
def number = 3.14
// "$number.toString()" is interpreted as "${number.toString}()" -- WRONG!
// Use "${number.toString()}" instead
```

**Escaping `$`**: Use `\$` to prevent interpolation:
```groovy
assert '${name}' == "\${name}"
```

**Closure expressions in GStrings** (`${-> ...}`): Enable lazy evaluation.
```groovy
def sParameterLessClosure = "1 + 2 == ${-> 3}"
assert sParameterLessClosure == '1 + 2 == 3'

def sOneParamClosure = "1 + 2 == ${ w -> w << 3}"
assert sOneParamClosure == '1 + 2 == 3'
```

Lazy vs eager evaluation:
```groovy
def number = 1
def eagerGString = "value == ${number}"
def lazyGString = "value == ${ -> number }"

assert eagerGString == "value == 1"
assert lazyGString ==  "value == 1"

number = 2
assert eagerGString == "value == 1"  // bound at creation time
assert lazyGString ==  "value == 2"  // re-evaluated each time
```

Only zero-arg or one-arg closures are allowed in GString interpolation.

**GString auto-conversion**: When a method expects `java.lang.String` but receives `GString`, `toString()` is called automatically.

**GString vs String hashCode GOTCHA**: GString and String have DIFFERENT hashCodes even with same content. Never use GString as map key:
```groovy
assert "one: ${1}".hashCode() != "one: 1".hashCode()

def key = "a"
def m = ["${key}": "letter ${key}"]
assert m["a"] == null  // GString key != String key
```

#### Triple Double Quoted Strings
Multiline + interpolation. Neither double nor single quotes need escaping:
```groovy
def name = 'Groovy'
def template = """
    Dear Mr ${name},
    You're the winner!
"""
```

#### Slashy Strings
Delimited by `/`. Ideal for regex (no need to escape backslashes). Support interpolation and multiline.
```groovy
def fooPattern = /.*foo.*/
assert fooPattern == '.*foo.*'
```

Only forward slashes need escaping:
```groovy
def escapeSlash = /The character \/ is a forward slash/
```

**Cannot create empty slashy string** with `//` (interpreted as line comment).

#### Dollar Slashy Strings
Delimited by `$/` and `/$`. Escape character is `$`. Multiline + interpolation.
```groovy
def name = "Guillaume"
def date = "April, 1st"
def dollarSlashy = $/
    Hello $name,
    today we're ${date}.

    $ dollar sign
    $$ escaped dollar sign
    \ backslash
    / forward slash
    $/ escaped forward slash
    $$$/ escaped opening dollar slashy
    $/$$ escaped closing dollar slashy
/$
```

#### Characters
Groovy has NO explicit character literal. Three ways to create `char`:
```groovy
char c1 = 'A'           // explicit type declaration
def c2 = 'B' as char    // type coercion with 'as'
def c3 = (char)'C'      // cast
```

### 1.5 Numbers

#### Integral Literals
Types: `byte`, `char`, `short`, `int`, `long`, `java.math.BigInteger`.

```groovy
byte  b = 1
char  c = 2
short s = 3
int   i = 4
long  l = 5
BigInteger bi = 6
```

With `def`, type adapts to fit the value:
```groovy
def a = 1                          // Integer
def c = 2147483648                 // Long (> Integer.MAX_VALUE)
def e = 9223372036854775808        // BigInteger (> Long.MAX_VALUE)
```

**Binary literals**: Prefix `0b`:
```groovy
int xInt = 0b10101111              // 175
long xLong = 0b101101101101        // 2925
BigInteger xBigInteger = 0b111100100001  // 3873
```

**Octal literals**: Prefix `0`:
```groovy
int xInt = 077                     // 63
long xLong = 0246                  // 166
```

**Hexadecimal literals**: Prefix `0x`:
```groovy
int xInt = 0x77                    // 119
long xLong = 0xffff                // 65535
BigInteger xBigInteger = 0xaaaa    // 43690
```

#### Decimal Literals
Types: `float`, `double`, `java.math.BigDecimal`.

```groovy
float  f = 1.234
double d = 2.345
BigDecimal bd = 3.456
```

Default decimal type (with `def`) is `BigDecimal`.

**Exponent notation** with `e` or `E`:
```groovy
assert 1e3  ==  1_000.0
assert 2E4  == 20_000.0
assert 3e+1 ==     30.0
assert 4E-2 ==      0.04
```

#### Underscore in Literals
Allowed anywhere in number literals for readability:
```groovy
long creditCardNumber = 1234_5678_9012_3456L
long hexBytes = 0xFF_EC_DE_5E
long bytes = 0b11010010_01101001_10010100_10010010
```

#### Number Type Suffixes

| Type | Suffix |
|------|--------|
| BigInteger | `G` or `g` |
| Long | `L` or `l` |
| Integer | `I` or `i` |
| BigDecimal | `G` or `g` |
| Double | `D` or `d` |
| Float | `F` or `f` |

```groovy
assert 42I == new Integer('42')
assert 123L == new Long("123")
assert 456G == new BigInteger('456')
assert 123.45 == new BigDecimal('123.45')
assert 1.200065D == new Double('1.200065')
assert 1.234F == new Float('1.234')
assert 0b1111L.class == Long
assert 0xFFi.class == Integer
assert 034G.class == BigInteger
```

Note: `G`/`g` means BigInteger for integral values and BigDecimal for decimal values.

#### Math Operations - Type Promotion Rules
- Binary ops between `byte`, `char`, `short`, `int` -> `int`
- Any operand is `long` -> `long`
- Any operand is `BigInteger` -> `BigInteger`
- Any operand is `BigDecimal` -> `BigDecimal`
- Any operand is `float` or `double` -> `double`

**Division**: `/` produces `double` if either operand is float/double; `BigDecimal` otherwise. Use `intdiv()` for integer division.

**Power operator `**`**:
```groovy
assert    2    **   3    instanceof Integer    //  8
assert   10    **   9    instanceof Integer    //  1_000_000_000
assert    5L   **   2    instanceof Long       //  25
assert  100    **  10    instanceof BigInteger
assert    0.5  **  -2    instanceof Integer    //  4
assert   10    **  -1    instanceof Double     //  0.1
assert    1.2  **  10    instanceof BigDecimal
assert    3.4f **   5    instanceof Double
```

### 1.6 Booleans

```groovy
def myBooleanVariable = true
boolean untypedBooleanVar = false
```

See Groovy Truth (Section 4.5) for non-boolean coercion rules.

### 1.7 Lists

Lists are `java.util.ArrayList` by default:
```groovy
def numbers = [1, 2, 3]
assert numbers instanceof List
assert numbers instanceof java.util.ArrayList
assert numbers.size() == 3

def heterogeneous = [1, "a", true]
```

**Changing implementation**:
```groovy
def linkedList = [2, 3, 4] as LinkedList
assert linkedList instanceof java.util.LinkedList

LinkedList otherLinked = [3, 4, 5]
assert otherLinked instanceof java.util.LinkedList
```

**Accessing elements**: `list[index]`, negative indices count from end: `list[-1]` is last element.

### 1.8 Arrays

Java array initializer `{1,2,3}` does NOT work in Groovy (curly braces are closures). Use list syntax + coercion:
```groovy
String[] arrStr = ['Ananas', 'Banana', 'Kiwi']

int[] array = [1, 2, 3]

def numArr = [1, 2, 3] as int[]
assert numArr instanceof int[]
assert numArr[0] == 1
```

### 1.9 Maps

Default implementation is `java.util.LinkedHashMap` (preserves insertion order):
```groovy
def colors = [red: '#FF0000', green: '#00FF00', blue: '#0000FF']
assert colors instanceof java.util.LinkedHashMap
```

**Empty map**: `[:]`
```groovy
def emptyMap = [:]
```

**Access**: Dot notation or bracket notation:
```groovy
colors.red           // dot notation
colors['red']        // bracket notation
```

**Keys are strings by default**. To use a variable as key, wrap in parentheses:
```groovy
def keyVar = "myKey"
def map = [(keyVar): "value"]
assert map.containsKey("myKey")
// Without parens: [(keyVar): "value"] creates key "myKey"
// With bare keyVar: [keyVar: "value"] creates key "keyVar" (the string literal!)
```

---

## 2. OPERATORS

### 2.1 Arithmetic Operators

| Operator | Meaning | Example |
|----------|---------|---------|
| `+` | Addition | `1 + 2` -> `3` |
| `-` | Subtraction | `3 - 1` -> `2` |
| `*` | Multiplication | `2 * 3` -> `6` |
| `/` | Division | `10 / 3` -> `3.3333...` (BigDecimal) |
| `%` | Modulo | `10 % 3` -> `1` |
| `**` | Power | `2 ** 10` -> `1024` |

**Unary**: `+a` (positive), `-a` (negative), `++a`/`a++` (increment), `--a`/`a--` (decrement), `~a` (bitwise NOT).

**Compound assignment**: `+=`, `-=`, `*=`, `/=`, `%=`, `**=`.

### 2.2 Relational Operators

| Operator | Meaning |
|----------|---------|
| `==` | equal (calls `.equals()` or `.compareTo()`) |
| `!=` | not equal |
| `<` | less than |
| `<=` | less than or equal |
| `>` | greater than |
| `>=` | greater than or equal |

### 2.3 Logical Operators

| Operator | Meaning |
|----------|---------|
| `&&` | logical AND (short-circuits) |
| `||` | logical OR (short-circuits) |
| `!` | logical NOT |

Precedence: `!` > `&&` > `||`.

Short-circuiting: `&&` stops if left is false; `||` stops if left is true.

### 2.4 Bitwise Operators

| Operator | Meaning |
|----------|---------|
| `&` | bitwise AND |
| `|` | bitwise OR |
| `^` | bitwise XOR |
| `~` | bitwise NOT |
| `<<` | left shift |
| `>>` | right shift |
| `>>>` | unsigned right shift |

```groovy
assert (5 & 3) == 1
assert (5 | 3) == 7
assert (5 ^ 3) == 6
```

### 2.5 Conditional Operators

**Ternary** `?:`:
```groovy
def result = (x > 3) ? "big" : "small"
```

**Elvis** `?:` (shorthand ternary):
```groovy
def displayName = name ?: "Unknown"
// Equivalent to: name != null && name ? name : "Unknown"
// Returns name if Groovy-truthy, else "Unknown"
```

**Not** `!`:
```groovy
assert !false
assert !null
assert !0
```

### 2.6 Object Operators

**Safe navigation `?.`**: Returns `null` instead of NPE on null receiver:
```groovy
def person = null
def name = person?.name  // null, no exception
```

**Direct field access `.@`**: Bypasses getters, accesses underlying field:
```groovy
class User {
    public final String name
    User(String name) { this.name = name }
    String getName() { "Name: $name" }
}
def user = new User('Bob')
assert user.name == 'Name: Bob'    // calls getter
assert user.@name == 'Bob'         // direct field
```

**Method pointer `.&`**: Stores a method reference as a Closure:
```groovy
def str = 'example of method reference'
def fun = str.&toUpperCase
def upper = fun()
assert upper == str.toUpperCase()
```

Method pointers resolve overloads at runtime:
```groovy
def doSomething(String str) { str.toUpperCase() }
def doSomething(Integer x) { 2*x }
def reference = this.&doSomething
assert reference('foo') == 'FOO'
assert reference(123) == 246
```

### 2.7 Regular Expression Operators

**Pattern `~`**: Creates `java.util.regex.Pattern`:
```groovy
def pattern = ~/foo/
assert pattern instanceof java.util.regex.Pattern
```

**Find `=~`**: Creates `java.util.regex.Matcher`:
```groovy
def text = "some text to match"
def m = text =~ /match/
assert m instanceof java.util.regex.Matcher
assert m.find()
// In boolean context, equivalent to calling m.find()
```

**Match `==~`**: Tests if entire string matches pattern (returns boolean):
```groovy
assert "foobar" ==~ /foo.*/
assert !("foobar" ==~ /baz.*/)
```

### 2.8 Spread Operator `*.`

Invokes action on all items in a collection, returns results as a list:
```groovy
class Car {
    String make
    String model
}
def cars = [
    new Car(make: 'Peugeot', model: '508'),
    new Car(make: 'Renault', model: 'Clio')]
def makes = cars*.make
assert makes == ['Peugeot', 'Renault']
```

**Null-safe**: Returns null elements for null items, returns null for null receiver:
```groovy
cars = [new Car(make: 'Peugeot'), null, new Car(make: 'Renault')]
assert cars*.make == ['Peugeot', null, 'Renault']
assert null*.make == null
```

**Works on any Iterable**:
```groovy
class CompositeObject implements Iterable<Component> {
    def components = [new Component(id: 1, name: 'Foo'), new Component(id: 2, name: 'Bar')]
    Iterator<Component> iterator() { components.iterator() }
}
def composite = new CompositeObject()
assert composite*.id == [1,2]
```

**Spreading method arguments `*args`**:
```groovy
int function(int x, int y, int z) { x*y+z }
def args = [4,5,6]
assert function(*args) == 26

args = [4]
assert function(*args,5,6) == 26
```

**Spread list elements `[*list]`**:
```groovy
def items = [4,5]
def list = [1,2,3,*items,6]
assert list == [1,2,3,4,5,6]
```

**Spread map entries `[*:map]`**:
```groovy
def m1 = [c:3, d:4]
def map = [a:1, b:2, *:m1]
assert map == [a:1, b:2, c:3, d:4]

// Later entries override:
def map2 = [a:1, b:2, *:m1, d:8]
assert map2 == [a:1, b:2, c:3, d:8]
```

### 2.9 Range Operator `..` and `..<`

**Inclusive** `..`:
```groovy
def range = 0..5
assert range.collect() == [0, 1, 2, 3, 4, 5]
assert range instanceof List
assert range.size() == 6
```

**Exclusive (half-open)** `..<`:
```groovy
assert (0..<5).collect() == [0, 1, 2, 3, 4]
```

Works with any `Comparable` that has `next()` and `previous()`:
```groovy
assert ('a'..'d').collect() == ['a','b','c','d']
```

Ranges are lightweight (store only bounds).

### 2.10 Spaceship Operator `<=>`

Delegates to `compareTo()`, returns -1, 0, or 1:
```groovy
assert (1 <=> 1) == 0
assert (1 <=> 2) == -1
assert (2 <=> 1) == 1
assert ('a' <=> 'z') == -1
```

### 2.11 Subscript Operator `[]`

Shorthand for `getAt()`/`putAt()`:
```groovy
def list = [0,1,2,3,4]
assert list[2] == 2
list[2] = 4
assert list[0..2] == [0,1,4]    // range subscript
list[0..2] = [6,6,6]
assert list == [6,6,6,3,4]
```

Custom implementation:
```groovy
class User {
    Long id
    String name
    def getAt(int i) {
        switch (i) {
            case 0: return id
            case 1: return name
        }
        throw new IllegalArgumentException("No such element $i")
    }
    void putAt(int i, def value) {
        switch (i) {
            case 0: id = value; return
            case 1: name = value; return
        }
        throw new IllegalArgumentException("No such element $i")
    }
}
def user = new User(id: 1, name: 'Alex')
assert user[0] == 1
user[1] = 'Bob'
assert user.name == 'Bob'
```

### 2.12 Membership Operator `in`

Equivalent to `isCase()` / `contains()`:
```groovy
def list = ['Grace','Rob','Emmy']
assert ('Emmy' in list)
// equivalent to list.contains('Emmy') or list.isCase('Emmy')
```

### 2.13 Identity Operator `is()`

Tests reference identity (Java `==`):
```groovy
def list1 = ['Groovy 1.8','Groovy 2.0']
def list2 = ['Groovy 1.8','Groovy 2.0']
assert list1 == list2          // value equality
assert !list1.is(list2)       // different references
```

### 2.14 Coercion Operator `as`

Type conversion:
```groovy
Integer x = 123
String s = x as String
```

Custom coercion via `asType()`:
```groovy
class Identifiable { String name }
class User {
    Long id
    String name
    def asType(Class target) {
        if (target == Identifiable) {
            return new Identifiable(name: name)
        }
        throw new ClassCastException("User cannot be coerced")
    }
}
def u = new User(name: 'Xavier')
def p = u as Identifiable
assert p instanceof Identifiable
```

### 2.15 Diamond Operator `<>`

Generic type inference:
```groovy
List<String> strings = new LinkedList<>()
```

### 2.16 Call Operator `()`

Implicitly calls the `call()` method:
```groovy
class MyCallable {
    int call(int x) { 2*x }
}
def mc = new MyCallable()
assert mc.call(2) == 4
assert mc(2) == 4       // syntactic sugar for mc.call(2)
```

### 2.17 Operator Precedence (highest to lowest)

| Level | Operators |
|-------|-----------|
| 1 | `new` `()` (grouping) |
| 2 | `()` `[]` `.` `?.` `.&` `.@` (postfix) |
| 3 | `~` `!` `+` `-` (unary prefix), cast |
| 4 | `**` (right-associative) |
| 5 | `*` `/` `%` |
| 6 | `+` `-` (binary) |
| 7 | `<<` `>>` `>>>` `..` `..<` |
| 8 | `<` `<=` `>` `>=` `in` `!in` `instanceof` `as` |
| 9 | `==` `!=` `<=>` |
| 10 | `&` |
| 11 | `^` |
| 12 | `|` |
| 13 | `&&` |
| 14 | `||` |
| 15 | `?:` (ternary/elvis) |
| 16 | `=` `**=` `*=` `/=` `%=` `+=` `-=` `<<=` `>>=` `>>>=` `&=` `^=` `|=` |

### 2.18 Operator Overloading

Every operator maps to a method. Implement the method to overload the operator:

| Operator | Method |
|----------|--------|
| `a + b` | `a.plus(b)` |
| `a - b` | `a.minus(b)` |
| `a * b` | `a.multiply(b)` |
| `a / b` | `a.div(b)` |
| `a % b` | `a.mod(b)` |
| `a ** b` | `a.power(b)` |
| `a == b` | `a.equals(b)` |
| `a != b` | `!a.equals(b)` |
| `a < b` | `a.compareTo(b) < 0` |
| `a <= b` | `a.compareTo(b) <= 0` |
| `a > b` | `a.compareTo(b) > 0` |
| `a >= b` | `a.compareTo(b) >= 0` |
| `a <=> b` | `a.compareTo(b)` |
| `a & b` | `a.and(b)` |
| `a \| b` | `a.or(b)` |
| `a ^ b` | `a.xor(b)` |
| `~a` | `a.bitwiseNegate()` |
| `a << b` | `a.leftShift(b)` |
| `a >> b` | `a.rightShift(b)` |
| `a >>> b` | `a.rightShiftUnsigned(b)` |
| `a[b]` | `a.getAt(b)` |
| `a[b] = c` | `a.putAt(b, c)` |
| `a in b` | `b.isCase(a)` / `b.contains(a)` |
| `a as T` | `a.asType(T)` |
| `a++` | `a.next()` |
| `a--` | `a.previous()` |
| `+a` | `a.positive()` |
| `-a` | `a.negative()` |

Example:
```groovy
class MyNumber {
    int value
    MyNumber plus(MyNumber other) {
        new MyNumber(value: this.value + other.value)
    }
}
def a = new MyNumber(value: 5)
def b = new MyNumber(value: 3)
def c = a + b
assert c.value == 8
```

---

## 3. PROGRAM STRUCTURE

### 3.1 Package Names

Work identically to Java. Package declaration appears at beginning of source file.

### 3.2 Imports

#### Default Imports (automatic, no declaration needed)
- `java.io.*`
- `java.lang.*`
- `java.math.BigDecimal`
- `java.math.BigInteger`
- `java.net.*`
- `java.util.*`
- `groovy.lang.*`
- `groovy.util.*`

#### Simple Import
```groovy
import groovy.xml.MarkupBuilder
```

#### Star Import
```groovy
import groovy.xml.*
```

#### Static Import
```groovy
import static java.lang.Math.abs
```

#### Static Star Import
```groovy
import static java.lang.Math.*
```

#### Import Aliasing
```groovy
import java.util.List as GroovyList
import static java.lang.Math.max as maximum
```

### 3.3 Scripts vs Classes

**Scripts**: Top-level code runs directly. Compiled into a class extending `groovy.lang.Script` with a `run()` method containing the statements. A `public static void main(String[] args)` is auto-generated.

**Methods in scripts**: Become instance methods of the generated Script class.

**Variable scoping in scripts**:
- `def x = 1` or `int x = 1`: Local variable, scoped to `run()` method, NOT in binding
- `x = 1` (no type/def): Goes into Script binding, accessible externally

**`@Field` annotation**: Creates actual fields on the generated Script class (not binding variables and not local variables).

---

## 4. SEMANTICS

### 4.1 Variable Definitions and Multiple Assignment

```groovy
def x = 1
String name = "Groovy"
int count = 42
```

**Multiple assignment** (destructuring):
```groovy
def (a, b, c) = [1, 2, 3]
assert a == 1 && b == 2 && c == 3

// With types:
def (int i, String j) = [10, 'foo']

// Too many variables: extras get null
// Too few variables: extras ignored

// From method return:
def (_, month, year) = "18th June 2009".split()
assert "In $month of $year" == 'In June of 2009'
```

### 4.2 Control Structures

#### if / else
```groovy
if (condition) {
    // ...
} else if (other) {
    // ...
} else {
    // ...
}
```

#### switch / case
Groovy switch supports multiple matching types (not just constants):
```groovy
def x = 1.23
def result = ""
switch (x) {
    case "foo":              // String equality
        result = "found foo"
        break
    case [4, 5, 6, 'inList']:  // List membership
        result = "list"
        break
    case 12..30:             // Range containment
        result = "range"
        break
    case Integer:            // Class instanceof
        result = "integer"
        break
    case Number:             // Class instanceof (superclass)
        result = "number"
        break
    case ~/fo*/:             // Regex match
        result = "foo regex"
        break
    case { it < 0 }:         // Closure (Groovy Truth on return)
        result = "negative"
        break
    default:
        result = "default"
}
assert result == "number"
```

**Case matching mechanism**: Each `case` calls `isCase()` on the case value with the switch value as argument. This allows custom matching via implementing `isCase()`.

#### for loops

**Classic for**:
```groovy
for (int i = 0; i < 10; i++) { println i }
```

**for-in** with various iterable types:
```groovy
// Range:
for (i in 0..9) { println i }

// List:
for (i in [0, 1, 2, 3, 4]) { println i }

// Array:
def array = (0..4).toArray()
for (i in array) { println i }

// Map (iterates over entries):
def map = ['abc':1, 'def':2, 'xyz':3]
for (e in map) { println "${e.key}: ${e.value}" }

// Map values:
for (v in map.values()) { println v }

// String characters:
def text = "abc"
def list = []
for (c in text) { list.add(c) }
assert list == ["a", "b", "c"]
```

Java colon syntax also supported: `for (char c : text) {}`

**while**:
```groovy
while (condition) { /* ... */ }
```

**do-while**:
```groovy
do { /* ... */ } while (condition)
```

#### try / catch / finally
```groovy
try {
    // risky code
} catch (IOException e) {
    // handle
} catch (Exception e) {
    // handle
} finally {
    // cleanup
}
```

**Multi-catch** (since Groovy 2.0):
```groovy
try {
    /* ... */
} catch (IOException | NullPointerException e) {
    /* one block for multiple exception types */
}
```

### 4.3 Power Assertions

Provide detailed failure messages showing all intermediate values:
```groovy
assert 1+1 == 3
// Output:
// assert 1+1 == 3
//         |  |
//         2  false
```

### 4.4 Labeled Statements

```groovy
outer:
for (int i = 0; i < 10; i++) {
    for (int j = 0; j < i; j++) {
        if (j == 5) break outer
    }
}
```

Labels are AST nodes that can be used by AST transformations (e.g., Spock's `given:`, `when:`, `then:`).

### 4.5 Groovy Truth

Rules for coercing non-boolean values to boolean:

| Type | True | False |
|------|------|-------|
| Boolean | `true` | `false` |
| Collection/Array | Non-empty | Empty `[]` |
| Matcher | Has match | No match |
| Iterator/Enumeration | `hasNext()` | Exhausted |
| Map | Non-empty | Empty `[:]` |
| String/GString | Non-empty | Empty `""`, `''` |
| Number | Non-zero | `0` |
| Object reference | Non-null | `null` |

```groovy
// Booleans
assert true
assert !false

// Collections
assert [1, 2, 3]
assert ![]

// Matchers
assert ('a' =~ /a/)
assert !('a' =~ /b/)

// Iterators
assert [0].iterator()
assert ![].iterator()

// Maps
assert ['one': 1]
assert ![:]

// Strings
assert 'a'
assert !''
def nonEmpty = 'a'
assert "$nonEmpty"
def empty = ''
assert !"$empty"

// Numbers
assert 1
assert 3.5
assert !0

// Object references
assert new Object()
assert !null
```

**Custom `asBoolean()`**:
```groovy
class Color {
    String name
    boolean asBoolean() {
        name == 'green'
    }
}
assert new Color(name: 'green')
assert !new Color(name: 'red')
```

### 4.6 GPath Expressions

Dot-notation path through object graphs:
```groovy
assert ['aMethodFoo'] == this.class.methods.name.grep(~/.*Foo/)
```

Property access on collections auto-spreads to each element (equivalent to `*.`).

**XML navigation**:
```groovy
def root = new XmlSlurper().parseText(xmlText)
root.level.sublevel.size()
root.level.sublevel[1].keyVal[0].key.text()
```

XML attribute access:
```groovy
a["@href"]    // map notation
a.'@href'     // property notation
a.@href       // direct notation
```

### 4.7 Promotion and Coercion

**Closure to SAM type** (since Groovy 2.2.0, `as Type` is optional):
```groovy
Predicate filter = { it.contains 'G' }
assert filter.accept('Groovy') == true
```

**Closure to arbitrary type** (maps closure body to all methods):
```groovy
interface FooBar {
    int foo()
    void bar()
}
def impl = { println 'ok'; 123 } as FooBar
assert impl.foo() == 123
```

**Map to type coercion** (keys=method names, values=implementations):
```groovy
def map = [
    i: 10,
    hasNext: { map.i > 0 },
    next: { map.i-- },
]
def iter = map as Iterator
```

**String to enum coercion** (implicit in assignments):
```groovy
enum State { up, down }
State st = 'up'
assert st == State.up
```

**Custom coercion via `asType()`**:
```groovy
class Polar {
    double r, phi
    def asType(Class target) {
        if (Cartesian == target) {
            return new Cartesian(x: r*cos(phi), y: r*sin(phi))
        }
    }
}
def polar = new Polar(r: 1.0, phi: PI/2)
def cartesian = polar as Cartesian
```

**Class literals vs variables**: `as` works only with static class references. For runtime references, use `.asType()`:
```groovy
Class clazz = Class.forName('Greeter')
greeter = { println 'Hello!' }.asType(clazz)
```

### 4.8 Optionality

**Optional parentheses**: Can omit when method has at least 1 argument and no ambiguity:
```groovy
println 'Hello World'         // OK
def maximum = Math.max 5, 10  // OK
println()                     // Required: no args
println(Math.max(5, 10))      // Required: nested call ambiguity
```

**Optional semicolons**: Statement terminators inferred from newlines.
```groovy
assert true
boolean a = true; assert a  // semicolon needed for multiple on one line
```

**Optional return keyword**: Last expression is returned implicitly:
```groovy
int add(int a, int b) {
    a + b  // implicit return
}
assert add(1, 2) == 3
```

**Optional `public` keyword**: Classes and methods are public by default:
```groovy
class Server {
    String toString() { "a server" }
}
// Identical to:
public class Server {
    public String toString() { "a server" }
}
```

### 4.9 Typing

#### Optional Typing
`def` is an alias for `Object`. Enables dynamic typing.
```groovy
def x = 1          // dynamic
String name = "hi" // static
```

#### @TypeChecked
Compile-time type checking without changing bytecode generation.

```groovy
@groovy.transform.TypeChecked
void myMethod() { /* ... */ }
```

**What it checks**:
- Method existence and argument compatibility
- Property existence
- Assignment type compatibility
- Return type compatibility

**Type inference features**:
- **Flow typing**: Variable types change based on assignments:
  ```groovy
  def o = 'foo'        // String
  o = o.toUpperCase()  // still String
  o = 9d               // now double
  o = Math.sqrt(o)     // still double
  ```
- **instanceof narrowing**: No cast needed after instanceof check:
  ```groovy
  if (o instanceof String) {
      o.toUpperCase()  // compiler knows it's a String
  }
  ```
- **Least Upper Bound (LUB)**: For conditional type merging

**Field type inference limitation**: The compiler does NOT perform type inference on fields (only local variables). Fields use declared types only (thread safety concern).

**Skipping sections**:
```groovy
@TypeChecked
class GreetingService {
    String greeting() { doGreet() }

    @TypeChecked(TypeCheckingMode.SKIP)  // or @CompileDynamic
    private String doGreet() {
        def b = new SentenceBuilder()
        b.Hello.my.name.is.John  // dynamic code OK here
        b
    }
}
```

**Closure type inference**:
- Return types inferred from body
- Parameter types from SAM coercion or `@ClosureParams` annotation
- `@ClosureParams` hints: `FirstParam`, `SecondParam`, `SimpleType`, `MapEntryOrKeyValue`, `FromString`

#### @CompileStatic
Everything `@TypeChecked` does PLUS static compilation to Java-equivalent bytecode.

```groovy
@groovy.transform.CompileStatic
int calculate(int x) {
    return x * 2
}
```

**Key difference from @TypeChecked**: Methods are resolved at compile time AND the bytecode enforces this. Immune to monkey-patching:
```groovy
@CompileStatic
void test() {
    def computer = new Computer()
    computer.with {
        assert compute(compute('foobar')) == '6'
    }
}
// Even if Computer.metaClass.compute is overridden later, test() still works
```

**Performance**: Generates bytecode close to/equal to Java. Significant improvement for CPU-intensive code. Negligible for I/O-bound code.

**Limitations**: Cannot use dynamic method dispatch, runtime metaprogramming, monkey patching, or dynamic DSLs with undefined methods.

#### Type Checking Extensions
Custom DSL to extend the type checker. Configured via `extensions` attribute:
```groovy
@TypeChecked(extensions='MyExtension.groovy')
void method() { /* ... */ }
```

---

## 5. DIFFERENCES WITH JAVA

### 5.1 Default Imports
Groovy auto-imports: `java.io.*`, `java.lang.*`, `java.math.BigDecimal`, `java.math.BigInteger`, `java.net.*`, `java.util.*`, `groovy.lang.*`, `groovy.util.*`.

### 5.2 Multi-methods (Runtime Dispatch)
In Java, method selection uses compile-time declared types. In Groovy, it uses runtime actual types:
```groovy
int method(String arg) { return 1 }
int method(Object arg) { return 2 }
Object o = "Object"
int result = method(o)
// Java: calls method(Object) -> 2
// Groovy: calls method(String) -> 1 (runtime type is String)
```

### 5.3 Array Initializers
Java `int[] a = {1,2,3}` does NOT work (curly braces = closures in Groovy):
```groovy
int[] array = [1, 2, 3]  // Use list syntax instead
```

### 5.4 Package Scope Visibility
Omitting access modifier does NOT give package scope (unlike Java). It creates a property (private field + getter/setter). Use `@PackageScope` for true package-private:
```groovy
class Person {
    @PackageScope String name  // package-private field
}
```

### 5.5 ARM Blocks (Automatic Resource Management)
Java's try-with-resources is NOT supported. Use closure-based alternatives:
```groovy
new File('/path/to/file').eachLine('UTF-8') { println it }
// or:
new File('/path/to/file').withInputStream { stream ->
    // stream auto-closed after closure
}
```

### 5.6 Inner Classes
**Static inner classes**: Work well, preferred:
```groovy
class A {
    static class B {}
}
new A.B()
```

**Non-static inner class instantiation**: Use constructor syntax, NOT Java's `outer.new Inner()`:
```groovy
public static X createX(Y y) {
    return new X(y)  // NOT: y.new X()
}
```

**Anonymous inner classes**: Supported with simpler syntax.

### 5.7 Lambdas
Java 8 lambda syntax is NOT supported. Use closures instead:
```groovy
Runnable run = { println 'run' }
list.each { println it }
```

### 5.8 GStrings
Double-quoted strings with interpolation produce `GString`, not `String`. Beware of hashCode differences (see Strings section).

### 5.9 String and Character Literals
Single quotes create `String`, NOT `char`. Auto-cast to `char` works in declarations but explicit cast needed for method arguments:
```groovy
char a = 'a'                           // auto-cast works
Character.digit((char) 'a', 16)       // explicit cast needed
```

### 5.10 Primitives and Wrappers
Everything is an object in Groovy. Primitives are auto-boxed. **Boxing takes precedence over widening** (opposite of Java):
```groovy
int i = 42
m(i)
void m(Integer i) { }  // Groovy calls THIS (boxing)
void m(long l) { }     // Java would call THIS (widening)
```

### 5.11 Behavior of `==`
In Groovy, `==` calls `.compareTo()` (if `Comparable`) or `.equals()`. It does NOT test identity. Use `.is()` for identity:
```groovy
a == b      // value equality (a.equals(b) or a.compareTo(b)==0)
a.is(b)     // reference identity (Java's ==)
```

### 5.12 Conversions
Groovy has a wider automatic conversion matrix than Java. Supports conversions between numeric types, wrappers, and BigInteger/BigDecimal with categories: D (dynamic), Y (direct), T (truncated), B (boxing).

### 5.13 Extra Keywords
Groovy reserves additional keywords not in Java: `as`, `def`, `in`, `trait`. Do not use as variable names.

---

## QUICK REFERENCE: Common Gotchas

1. **`==` is `.equals()`**, not identity. Use `.is()` for identity.
2. **GString hashCode differs from String hashCode**. Never use GString as map key.
3. **Omitting access modifier creates a property** (private field + getter/setter), NOT package-scope.
4. **`def` means `Object`** at the bytecode level.
5. **Division `/` returns BigDecimal** for integer operands, not int. Use `intdiv()` for integer division.
6. **Empty string `""`, empty list `[]`, empty map `[:]`, `0`, and `null` are all falsy** in Groovy Truth.
7. **Map keys without parentheses are always strings**: `[key: 1]` creates key `"key"`, not the value of variable `key`. Use `[(key): 1]`.
8. **Curly braces `{}` are closures**, not blocks or array initializers.
9. **Multi-method dispatch**: Groovy resolves overloaded methods at runtime based on actual argument types.
10. **Boxing over widening**: `int` argument prefers `Integer` parameter over `long` parameter.
11. **No char literal**: `'a'` is a `String`, not a `char`. Must explicitly cast/coerce.
12. **`$` in double-quoted strings triggers interpolation**: Escape with `\$`.
13. **Slashy strings `//`**: Empty slashy string is impossible (parsed as comment).
14. **Power operator `**` return type varies**: Based on base type and exponent sign/type.
15. **Script variables without `def`**: Go into binding (global); with `def` are local to `run()`.
