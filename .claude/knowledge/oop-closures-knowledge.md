# Groovy 2.4.21 OOP & Closures Knowledge Base

## Part 1: Object Orientation

### 1.1 Primitive Types

Groovy supports the same primitive types as Java: `byte` (8-bit), `short` (16-bit), `int` (32-bit), `long` (64-bit), `float` (32-bit), `double` (64-bit), `boolean`, and `char` (16-bit).

Key behavior: Groovy auto-boxes primitives when accessed. A field declared as `int` is stored as a primitive, but when accessed through Groovy (e.g., `Foo.i`), it becomes the wrapper type:

```groovy
class Foo {
  static int i
}
assert Foo.class.getDeclaredField('i').type == int.class
assert Foo.i.class != int.class && Foo.i.class == Integer.class
```

Wrapper mappings: boolean->Boolean, char->Character, short->Short, int->Integer, long->Long, float->Float, double->Double.

Character literals -- Groovy has no explicit char literal. Three ways to create:
```groovy
char c1 = 'A'           // explicit type declaration
def c2 = 'B' as char    // type coercion with 'as'
def c3 = (char)'C'      // cast operation
```

### 1.2 Classes

#### Normal Classes
Classes default to public visibility. Fields without visibility modifiers become properties with auto-generated getters/setters:

```groovy
class Person {
    String name      // becomes a property (private field + getter/setter)
    Integer age

    def increaseAge(Integer years) {
        this.age += years
    }
}
def p = new Person()
```

#### Inner Classes
Inner classes can access private members of the enclosing class:

```groovy
class Outer {
    private String privateStr

    def callInnerMethod() {
        new Inner().methodA()
    }

    class Inner {
        def methodA() {
            println "${privateStr}."  // accesses outer private field
        }
    }
}
```

Inner class implementing an interface:
```groovy
class Outer2 {
    private String privateStr = 'some string'

    def startThread() {
       new Thread(new Inner2()).start()
    }

    class Inner2 implements Runnable {
        void run() {
            println "${privateStr}."
        }
    }
}
```

Benefits: increased encapsulation, better organization, more maintainable code.

#### Anonymous Inner Classes
```groovy
class Outer3 {
    private String privateStr = 'some string'

    def startThread() {
        new Thread(new Runnable() {
            void run() {
                println "${privateStr}."
            }
        }).start()
    }
}
```

#### Abstract Classes
Cannot be instantiated directly. May contain abstract methods that subclasses must implement:

```groovy
abstract class Abstract {
    String name

    abstract def abstractMethod()   // must be implemented by subclass

    def concreteMethod() {
        println 'concrete'
    }
}
```

### 1.3 Interfaces

Methods are always public. Using `protected` or `private` on interface methods is a compile-time error:

```groovy
interface Greeter {
    void greet(String name)
}
```

Implementing an interface:
```groovy
class SystemGreeter implements Greeter {
    void greet(String name) {
        println "Hello $name"
    }
}
def greeter = new SystemGreeter()
assert greeter instanceof Greeter
```

Interface extending another interface:
```groovy
interface ExtendedGreeter extends Greeter {
    void sayBye(String name)
}
```

Runtime coercion to interface (using `as`):
```groovy
class DefaultGreeter {
    void greet(String name) { println "Hello" }
}
greeter = new DefaultGreeter()
assert !(greeter instanceof Greeter)
coerced = greeter as Greeter
assert coerced instanceof Greeter
```

### 1.4 Constructors

#### Positional Argument Constructor
```groovy
class PersonConstructor {
    String name
    Integer age

    PersonConstructor(name, age) {
        this.name = name
        this.age = age
    }
}

def person1 = new PersonConstructor('Marie', 1)   // standard new
def person2 = ['Marie', 2] as PersonConstructor    // coercion with as
PersonConstructor person3 = ['Marie', 3]           // static type assignment
```

Three invocation methods: (1) Java `new` keyword, (2) coercion with `as`, (3) static typing assignment from list.

#### Named Argument Constructor (Map-Based)
When NO constructor is explicitly declared, Groovy provides a default map-based constructor:

```groovy
class PersonWOConstructor {
    String name
    Integer age
}

def person4 = new PersonWOConstructor()                      // no args
def person5 = new PersonWOConstructor(name: 'Marie')         // partial
def person6 = new PersonWOConstructor(age: 1)                // partial
def person7 = new PersonWOConstructor(name: 'Marie', age: 2) // full
```

Property names are used as keys. Order doesn't matter. Properties are optional.

### 1.5 Methods

#### Method Definition
Methods return the value of the last executed line. Return statements are optional:

```groovy
def someMethod() { 'method called' }                           // untyped return
String anotherMethod() { 'another method called' }             // typed return
def thirdMethod(param1) { "$param1 passed" }                   // untyped param
static String fourthMethod(String param1) { "$param1 passed" } // static, typed
```

#### Named Arguments
Methods can receive named arguments as a Map:

```groovy
def foo(Map args) { "${args.name}: ${args.age}" }
foo(name: 'Marie', age: 1)
```

#### Default Arguments
Make parameters optional. No mandatory parameters can follow a default parameter:

```groovy
def foo(String par1, Integer par2 = 1) { [name: par1, age: par2] }
assert foo('Marie').age == 1
```

#### Varargs
Two equivalent syntaxes -- ellipsis and array:

```groovy
def foo(Object... args) { args.length }
assert foo() == 0
assert foo(1) == 1
assert foo(1, 2) == 2
```

```groovy
def foo(Object[] args) { args.length }
assert foo() == 0
assert foo(1) == 1
assert foo(1, 2) == 2
```

Null handling:
```groovy
def foo(Object... args) { args }
assert foo(null) == null   // null passed as the entire array, not as element
```

Array argument passthrough:
```groovy
def foo(Object... args) { args }
Integer[] ints = [1, 2]
assert foo(ints) == [1, 2]  // array passed directly, not wrapped
```

Varargs with method overloading -- most specific method preferred:
```groovy
def foo(Object... args) { 1 }
def foo(Object x) { 2 }
assert foo() == 1       // no args -> varargs version
assert foo(1) == 2      // single arg -> specific version wins
assert foo(1, 2) == 1   // multiple args -> varargs version
```

#### Method Selection Algorithm
Groovy resolves overloaded methods using runtime (dynamic) dispatch by default. The most specific method matching the actual argument types at runtime is selected. This differs from Java's compile-time dispatch. Groovy uses multi-method (multimethods) dispatch -- the actual runtime types of ALL arguments are considered, not just the declared types.

#### Exception Declaration
```groovy
void riskyOperation() throws IOException {
    // method body
}
```

### 1.6 Fields and Properties

#### Fields
Fields require an access modifier (`private`, `protected`, `public`). Optional modifiers: `static`, `final`, `synchronized`:

```groovy
class Data {
    private int id                             // private field
    protected String description               // protected field
    public static final boolean DEBUG = false  // constant
}
```

Field initialization:
```groovy
class Data {
    private String id = IDGenerator.next()  // initialized at construction
}
```

#### Properties
Fields WITHOUT visibility modifiers become properties. Groovy automatically creates:
- A private backing field
- A public getter (`getName()`)
- A public setter (`setName()`) unless `final`

```groovy
class Person {
    String name   // property: private field + getter + setter
    int age        // property
}
```

Read-only properties with `final`:
```groovy
class Person {
    final String name
    final int age
    Person(String name, int age) {
        this.name = name   // can only be set in constructor
        this.age = age
    }
}
```

Property access vs field access -- within the class, `this.name` accesses the field directly (bypasses getter/setter to prevent stack overflow). Outside the class, `p.name` calls the getter:

```groovy
class Person {
    String name
    void name(String name) {
        this.name = "Wonder$name"    // direct field access
    }
    String wonder() {
        this.name                     // direct field access
    }
}
def p = new Person()
p.name = 'Marge'         // calls setName('Marge')
assert p.name == 'Marge' // calls getName()
p.name('Marge')           // calls method name(String)
assert p.wonder() == 'WonderMarge'
```

Listing properties:
```groovy
class Person { String name; int age }
def p = new Person()
assert p.properties.keySet().containsAll(['name','age'])
```

Pseudo-properties -- getters/setters without backing fields create virtual properties:
```groovy
class PseudoProperties {
    void setName(String name) {}
    String getName() {}
    int getAge() { 42 }
    void setGroovy(boolean groovy) {}
}
def p = new PseudoProperties()
p.name = 'Foo'        // calls setName
assert p.age == 42     // calls getAge
p.groovy = true        // calls setGroovy
```

### 1.7 Annotations

#### Annotation Definition
```groovy
@interface SomeAnnotation {}
```

Members limited to: primitives, Strings, Classes, Enumerations, other annotation types, or arrays thereof:

```groovy
@interface SomeAnnotation {
    String value()                       // String member
}
@interface SomeAnnotation {
    String value() default 'something'   // with default
}
@interface SomeAnnotation {
    int step()                           // primitive member
}
@interface SomeAnnotation {
    Class appliesTo()                    // Class member
}
@interface SomeAnnotations {
    SomeAnnotation[] value()             // array of annotations
}
enum DayOfWeek { mon, tue, wed, thu, fri, sat, sun }
@interface Scheduled {
    DayOfWeek dayOfWeek()                // enum member
}
```

#### Annotation Placement
```groovy
@SomeAnnotation
void someMethod() {}     // on method

@SomeAnnotation
class SomeClass {}       // on class

@SomeAnnotation String var  // on variable
```

Target restriction:
```groovy
import java.lang.annotation.ElementType
import java.lang.annotation.Target

@Target([ElementType.METHOD, ElementType.TYPE])
@interface SomeAnnotation {}
```

#### Annotation Member Values
```groovy
@interface Page {
    int statusCode()
}
@Page(statusCode=404)
void notFound() {}
```

Single mandatory member named `value` -- the name can be omitted:
```groovy
@interface Page {
    String value()
    int statusCode() default 200
}
@Page(value='/home')             // explicit
void home() {}
@Page('/users')                  // shorthand (value= omitted)
void userList() {}
@Page(value='error',statusCode=404)  // mixed
void notFound() {}
```

#### Retention Policy
```groovy
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

@Retention(RetentionPolicy.SOURCE)    // SOURCE, CLASS, or RUNTIME
@interface SomeAnnotation {}
```

#### Closure Annotation Parameters
Groovy uniquely allows closures as annotation parameters (stored as Class values):

```groovy
@Retention(RetentionPolicy.RUNTIME)
@interface OnlyIf {
    Class value()    // accepts a closure class
}

class Tasks {
    Set result = []
    void alwaysExecuted() {
        result << 1
    }
    @OnlyIf({ jdk>=6 })
    void supportedOnlyInJDK6() {
        result << 'JDK 6'
    }
    @OnlyIf({ jdk>=7 && windows })
    void requiresJDK7AndWindows() {
        result << 'JDK 7 Windows'
    }
}
```

Processing closure annotations at runtime:
```groovy
class Runner {
    static <T> T run(Class<T> taskClass) {
        def tasks = taskClass.newInstance()
        def params = [jdk:6, windows: false]
        tasks.class.declaredMethods.each { m ->
            if (Modifier.isPublic(m.modifiers) && m.parameterTypes.length == 0) {
                def onlyIf = m.getAnnotation(OnlyIf)
                if (onlyIf) {
                    Closure cl = onlyIf.value().newInstance(tasks, tasks)
                    cl.delegate = params        // set delegate for property resolution
                    if (cl()) {
                        m.invoke(tasks)
                    }
                } else {
                    m.invoke(tasks)
                }
            }
        }
        tasks
    }
}
def tasks = Runner.run(Tasks)
assert tasks.result == [1, 'JDK 6'] as Set
```

#### Meta-Annotations
Combine multiple annotations into one using `@AnnotationCollector`:

```groovy
import groovy.transform.AnnotationCollector

@Service
@Transactional
@AnnotationCollector
@interface TransactionalService {}

// Usage:
@TransactionalService
class MyService {}

// Verification:
def annotations = MyService.annotations*.annotationType()
assert (Service in annotations)
assert (Transactional in annotations)
```

Meta-annotation with parameters:
```groovy
@Timeout(after=3600)
@Dangerous(type='explosive')
@AnnotationCollector
public @interface Explosive {}

@Explosive(after=0)   // overrides @Timeout's after parameter
class Bomb {}
```

When collected annotations share parameter names, the value applies to ALL:
```groovy
@Retention(RetentionPolicy.RUNTIME)
public @interface Foo { String value() }

@Retention(RetentionPolicy.RUNTIME)
public @interface Bar { String value() }

@Foo @Bar
@AnnotationCollector
public @interface FooBar {}

@FooBar('a')
class Joe {}
assert Joe.getAnnotation(Foo).value() == 'a'  // both get 'a'
assert Joe.getAnnotation(Bar).value() == 'a'
```

Custom annotation processor:
```groovy
@AnnotationCollector(processor = "org.codehaus.groovy.transform.CompileDynamicProcessor")
public @interface CompileDynamic {}

@CompileStatic
class CompileDynamicProcessor extends AnnotationCollectorTransform {
    private static final ClassNode CS_NODE = ClassHelper.make(CompileStatic)
    private static final ClassNode TC_NODE = ClassHelper.make(TypeCheckingMode)

    List<AnnotationNode> visit(AnnotationNode collector,
                               AnnotationNode aliasAnnotationUsage,
                               AnnotatedNode aliasAnnotated,
                               SourceUnit source) {
        def node = new AnnotationNode(CS_NODE)
        def enumRef = new PropertyExpression(
            new ClassExpression(TC_NODE), "SKIP")
        node.addMember("value", enumRef)
        Collections.singletonList(node)
    }
}
```

### 1.8 Inheritance

Groovy uses `extends` for class inheritance (single inheritance only) and `implements` for interfaces:

```groovy
class Animal {
    void move() { println "moving" }
}

class Dog extends Animal {
    void bark() { println "woof" }
}
```

Method overriding with `super`:
```groovy
class Dog extends Animal {
    void move() {
        super.move()        // call parent implementation
        println "dog moves"
    }
}
```

All classes implicitly extend `java.lang.Object` if no superclass is specified.

### 1.9 Generics

Groovy supports Java generics syntax but handles them with type erasure at runtime (same as Java). In dynamic Groovy, generics are mostly advisory -- the compiler does not enforce them strictly unless `@CompileStatic` is used.

---

## Part 2: Traits

### 2.1 Declaring Traits

Traits use the `trait` keyword and can contain methods with default implementations:

```groovy
trait FlyingAbility {
    String fly() { "I'm flying!" }
}

class Bird implements FlyingAbility {}
def b = new Bird()
assert b.fly() == "I'm flying!"
```

Classes implement traits using `implements` (same keyword as interfaces).

### 2.2 Methods in Traits

#### Public Methods
Regular methods with implementations act like default methods:
```groovy
trait Greetable {
    String greeting() { "Hello!" }
}
```

#### Abstract Methods
Traits can declare abstract methods that implementing classes must provide:
```groovy
trait Greetable {
    abstract String name()
    String greeting() { "Hello, ${name()}!" }
}

class Person implements Greetable {
    String name() { 'Bob' }
}
def p = new Person()
assert p.greeting() == 'Hello, Bob!'
```

#### Private Methods
Private methods are NOT visible in the trait contract interface. Only `public` and `private` scopes are supported -- neither `protected` nor `package private` work:

```groovy
trait Greeter {
    private String greetingMessage() {
        'Hello from a private method!'
    }
    String greet() {
        def m = greetingMessage()
        println m
        m
    }
}

class GreetingMachine implements Greeter {}
def g = new GreetingMachine()
assert g.greet() == "Hello from a private method!"
```

### 2.3 The Meaning of `this` in a Trait

`this` refers to the implementing instance, NOT the trait itself. Think of a trait as a superclass:

```groovy
trait Introspector {
    def whoAmI() { this }
}
class Foo implements Introspector {}
def foo = new Foo()
assert foo.whoAmI().is(foo)  // this == the Foo instance
```

### 2.4 Implementing Interfaces

Traits can implement interfaces using the `implements` keyword:

```groovy
interface Named {
    String name()
}

trait Greetable implements Named {
    String greeting() { "Hello, ${name()}!" }
}

class Person implements Greetable {
    String name() { 'Bob' }
}

def p = new Person()
assert p.greeting() == 'Hello, Bob!'
assert p instanceof Named       // true!
assert p instanceof Greetable   // true!
```

The implementing class automatically satisfies both the trait and any interfaces the trait implements.

### 2.5 Properties in Traits

Traits can define properties which become available on implementing classes:

```groovy
trait Named {
    String name
}

class Person implements Named {}
def p = new Person(name: 'Bob')  // map-based constructor works
assert p.name == 'Bob'
assert p.getName() == 'Bob'
```

### 2.6 Fields

#### Private Fields
Traits support private fields for internal state. This is a key advantage over Java 8 default methods, which cannot hold state:

```groovy
trait Counter {
    private int count = 0
    int count() { count += 1; count }
}

class Foo implements Counter {}
def f = new Foo()
assert f.count() == 1
assert f.count() == 2
```

#### Public Fields
Public fields are REMAPPED in the implementing class to avoid diamond problems. The naming pattern is: `TraitName__fieldName` (fully qualified trait name with dots replaced by underscores, plus double underscore separator):

```groovy
trait Named {
    public String name
}

class Person implements Named {}
def p = new Person()
p.Named__name = 'Bob'   // must use remapped name to access public field
```

### 2.7 Composition of Behaviors

Multiple traits enable controlled multiple inheritance -- a class can implement many traits:

```groovy
trait FlyingAbility {
    String fly() { "I'm flying!" }
}

trait SpeakingAbility {
    String speak() { "I'm speaking!" }
}

class Duck implements FlyingAbility, SpeakingAbility {}

def d = new Duck()
assert d.fly() == "I'm flying!"
assert d.speak() == "I'm speaking!"
```

### 2.8 Overriding Default Methods

Implementing classes can override trait methods:

```groovy
class Duck implements FlyingAbility, SpeakingAbility {
    String quack() { "Quack!" }
    String speak() { quack() }  // overrides SpeakingAbility.speak()
}

def d = new Duck()
assert d.fly() == "I'm flying!"
assert d.quack() == "Quack!"
assert d.speak() == "Quack!"
```

### 2.9 Extending Traits

#### Simple Inheritance
Traits extend other traits using `extends`:

```groovy
trait Named {
    String name
}

trait Polite extends Named {
    String introduce() { "Hello, I am $name" }
}

class Person implements Polite {}
def p = new Person(name: 'Alice')
assert p.introduce() == 'Hello, I am Alice'
```

#### Multiple Inheritance
A trait can extend multiple traits using `implements` (not `extends`):

```groovy
trait WithId {
    Long id
}

trait WithName {
    String name
}

trait Identified implements WithId, WithName {}
```

### 2.10 Duck Typing with Traits

#### Dynamic Code in Traits
Traits support dynamic method calls, compatible with duck typing:

```groovy
trait SpeakingDuck {
    String speak() { quack() }  // calls quack() which doesn't exist in trait
}

class Duck implements SpeakingDuck {
    String methodMissing(String name, args) {
        "${name.capitalize()}!"
    }
}

def d = new Duck()
assert d.speak() == 'Quack!'
```

#### Implementing MOP Methods in Traits
Traits can implement `methodMissing` and `propertyMissing`:

```groovy
trait DynamicObject {
    private Map props = [:]
    def methodMissing(String name, args) {
        name.toUpperCase()
    }
    def propertyMissing(String prop) {
        props[prop]
    }
    void setProperty(String prop, Object value) {
        props[prop] = value
    }
}

class Dynamic implements DynamicObject {
    String existingProperty = 'ok'
    String existingMethod() { 'ok' }
}

def d = new Dynamic()
assert d.existingProperty == 'ok'
assert d.foo == null
d.foo = 'bar'
assert d.foo == 'bar'
assert d.existingMethod() == 'ok'
assert d.someMethod() == 'SOMEMETHOD'
```

### 2.11 Conflict Resolution

#### Default Resolution
When multiple traits define the same method, the LAST declared trait in the `implements` clause wins:

```groovy
trait A {
    String exec() { 'A' }
}

trait B {
    String exec() { 'B' }
}

class C implements A, B {}

def c = new C()
assert c.exec() == 'B'   // B is last, so B wins
```

#### User-Defined Resolution
Use `Trait.super.method()` syntax to explicitly choose:

```groovy
class C implements A, B {
    String exec() { A.super.exec() }  // explicitly choose A's implementation
}

def c = new C()
assert c.exec() == 'A'
```

### 2.12 Runtime Trait Implementation

#### Single Trait with `as`
Use the `as` keyword to coerce an object to a trait at runtime:

```groovy
trait Extra {
    String extra() { "I'm an extra method" }
}

class Something {
    String doSomething() { 'Something' }
}

def s = new Something() as Extra
s.extra()
s.doSomething()
```

IMPORTANT: The result is NOT the same instance. The coerced object implements both the trait and all interfaces of the original object, but it is a new proxy object.

#### Multiple Traits with `withTraits`
```groovy
trait A { void methodFromA() {} }
trait B { void methodFromB() {} }

class C {}

def c = new C()
def d = c.withTraits A, B
d.methodFromA()
d.methodFromB()
```

### 2.13 Chaining Behavior (Stackable Traits)

Traits can delegate to the next trait in the chain using `super`:

```groovy
interface MessageHandler {
    void on(String message, Map payload)
}

trait DefaultHandler implements MessageHandler {
    void on(String message, Map payload) {
        println "Received $message with payload $payload"
    }
}

trait LoggingHandler implements MessageHandler {
    void on(String message, Map payload) {
        println "Seeing $message with payload $payload"
        super.on(message, payload)  // delegates to next trait in chain
    }
}

trait SayHandler implements MessageHandler {
    void on(String message, Map payload) {
        if (message.startsWith("say")) {
            println "I say ${message - 'say'}!"
        } else {
            super.on(message, payload)
        }
    }
}

class Handler implements DefaultHandler, SayHandler, LoggingHandler {}
def h = new Handler()
h.on('foo', [:])         // LoggingHandler -> SayHandler -> DefaultHandler
h.on('sayHello', [:])    // LoggingHandler -> SayHandler (handled here)
```

Super resolution in traits: if unqualified `super` is used, (1) if another trait is in the chain, delegate to it; (2) if no trait left, `super` refers to the superclass.

Decorating final classes with traits:
```groovy
trait Filtering {
    StringBuilder append(String str) {
        def subst = str.replace('o','')
        super.append(subst)
    }
    String toString() { super.toString() }
}

def sb = new StringBuilder().withTraits Filtering
sb.append('Groovy')
assert sb.toString() == 'Grvy'
```

### 2.14 SAM Type Coercion with Traits

If a trait defines a single abstract method, it is a candidate for SAM coercion:

```groovy
trait Greeter {
    String greet() { "Hello $name" }
    abstract String getName()
}

Greeter greeter = { 'Alice' }  // closure coerced to trait

void greet(Greeter g) { println g.greet() }
greet { 'Alice' }
```

### 2.15 Differences from Java 8 Default Methods

Critical difference: if a class declares a trait in its interface list, the trait implementation is ALWAYS used, even if a superclass provides an implementation:

```groovy
class Person {
    String name
}

trait Bob {
    String getName() { 'Bob' }
}

def p = new Person(name: 'Alice')
assert p.name == 'Alice'
def p2 = p as Bob
assert p2.name == 'Bob'   // trait wins over class implementation
```

### 2.16 Self Types

Traits can be restricted to specific implementing types using `@SelfType`:

```groovy
class Device { String id }

@SelfType(Device)
@CompileStatic
trait Communicating {
    void sendMessage(Device to, String message) {
        CommunicationService.sendMessage(id, to.id, message)
    }
}

class MyDevice extends Device implements Communicating {}

// This would cause a compile-time error:
// class NotADevice implements Communicating {}  // ERROR!
```

### 2.17 Inheritance of State Gotchas

CRITICAL: Traits access their OWN field definitions, not overridden ones in the implementing class:

```groovy
trait IntCouple {
    int x = 1
    int y = 2
    int sum() { x + y }   // accesses trait's x and y fields directly
}

class Elem implements IntCouple {
    int x = 3
    int y = 4
    int f() { sum() }
}

def elem = new Elem()
assert elem.f() == 3   // Returns 3 (1+2), NOT 7 (3+4)!
```

Solution -- use getters instead of direct field access:
```groovy
trait IntCouple {
    int x = 1
    int y = 2
    int sum() { getX() + getY() }  // uses getters, which can be overridden
}

class Elem implements IntCouple {
    int x = 3
    int y = 4
    int f() { sum() }
}

def elem = new Elem()
assert elem.f() == 7   // Now returns 7 (3+4) as expected
```

### 2.18 Traits vs. Mixins

Key differences:
- Trait methods are visible in bytecode; mixin methods are only visible at runtime
- Traits are represented as an interface and helper classes
- Traits support `@CompileStatic`; mixins do not
- Trait methods can be seen from Java; mixin methods cannot

```groovy
class A { String methodFromA() { 'A' } }
class B { String methodFromB() { 'B' } }
A.metaClass.mixin B          // runtime mixin
def o = new A()
assert o.methodFromA() == 'A'
assert o.methodFromB() == 'B'
assert o instanceof A
assert !(o instanceof B)     // mixin does NOT make o instanceof B
```

### 2.19 Trait Limitations

#### Static Members
Static member support is experimental. Each implementing class gets its own copy of static members. Static field names are remapped:

```groovy
trait TestHelper {
    public static boolean CALLED = false
    static void init() {
        CALLED = true
    }
}

class Foo implements TestHelper {}
Foo.init()
assert Foo.TestHelper__CALLED   // remapped name

class Bar implements TestHelper {}
class Baz implements TestHelper {}
Bar.init()
assert Bar.TestHelper__CALLED
assert !Baz.TestHelper__CALLED  // each class has its own copy
```

#### Prefix/Postfix Operations
Within traits, prefix and postfix operations (++, --) are NOT allowed on trait fields. Use `+=` operator instead:

```groovy
trait Counting {
    int x
    void inc() {
        x++      // NOT allowed in traits
    }
}
// Workaround: use x += 1 instead of x++
```

#### AST Transformations
Traits are NOT officially compatible with AST transformations.

#### Scope Limitations
Only `public` and `private` method scopes are supported. Neither `protected` nor `package private` work.

---

## Part 3: Closures

### 3.1 Defining Closures

Syntax: `{ [closureParameters -> ] statements }`

```groovy
{ item++ }                                          // references external variable
{ -> item++ }                                       // explicit empty parameter list
{ println it }                                      // implicit parameter
{ name -> println name }                            // single named parameter
{ String x, int y -> println "hey ${x} = ${y}" }   // typed parameters
```

### 3.2 Closures as Objects

Closures are instances of `groovy.lang.Closure`. They are first-class objects:

```groovy
def listener = { e -> println "Clicked on $e.source" }
assert listener instanceof Closure

Closure callback = { println 'Done!' }
Closure<Boolean> isTextFile = { File it -> it.name.endsWith('.txt') }
```

The generic type parameter `Closure<T>` specifies the return type.

### 3.3 Calling Closures

Two equivalent invocation methods:

```groovy
def code = { 123 }
assert code() == 123        // direct invocation
assert code.call() == 123   // call() method

def isOdd = { int i -> i%2 != 0 }
assert isOdd(3) == true
assert isOdd.call(2) == false
```

Closures ALWAYS return a value (the last expression evaluated).

### 3.4 Parameters

#### Normal Parameters
Support optional types, names, and defaults:

```groovy
def closureWithOneArg = { str -> str.toUpperCase() }
def closureWithTwoArgsAndTypes = { int a, int b -> a+b }
def closureWithDefault = { int a, int b=2 -> a+b }
assert closureWithDefault(1) == 3
```

#### Implicit Parameter (`it`)
When no parameters are declared, closures receive an implicit `it` parameter:

```groovy
def greeting = { "Hello, $it!" }
assert greeting('Patrick') == 'Hello, Patrick!'
```

To explicitly declare a closure takes NO parameters, use `{ -> ... }`:
```groovy
def noParam = { -> 42 }
// noParam('arg')  // would fail - no parameters accepted
```

#### Varargs
```groovy
def concat1 = { String... args -> args.join('') }
assert concat1('abc','def') == 'abcdef'

def multiConcat = { int n, String... args -> args.join('')*n }
assert multiConcat(2, 'abc','def') == 'abcdefabcdef'
```

### 3.5 Delegation Strategy (CRITICAL)

This is the most powerful and complex aspect of Groovy closures. Every closure has three special properties that control how unqualified method/property references are resolved.

#### The Three Properties

**`this`** -- The enclosing CLASS where the closure is defined. In nested closures, `this` always refers to the outermost enclosing class, never a closure:

```groovy
class Enclosing {
    void run() {
        def whatIsThis = { this }
        assert whatIsThis() == this   // this = Enclosing instance
    }
}
```

In nested closures:
```groovy
class NestedClosures {
    void run() {
        def nestedClosures = {
            def cl = { this }
            cl()
        }
        assert nestedClosures() == this  // this = NestedClosures instance, NOT the outer closure
    }
}
```

**`owner`** -- The direct enclosing object, which can be a class instance OR another closure:

```groovy
class EnclosingWithOwner {
    void run() {
        def outer = {
            def inner = { owner }
            inner()
        }
        assert outer() == outer  // owner of inner = the outer closure
    }
}
```

Key difference: `this` always points to the enclosing class; `owner` points to the immediately enclosing object (which could be another closure).

**`delegate`** -- A user-assignable object used for method/property resolution. Defaults to `owner`:

```groovy
class Person { String name }
class Thing { String name }

def p = new Person(name: 'Norman')
def t = new Thing(name: 'Teapot')

def upperCasedName = { delegate.name.toUpperCase() }
upperCasedName.delegate = p
assert upperCasedName() == 'NORMAN'
upperCasedName.delegate = t
assert upperCasedName() == 'TEAPOT'
```

#### The Five Resolution Strategies

Set via `closure.resolveStrategy = Closure.STRATEGY_NAME`

##### OWNER_FIRST (Default)
Resolution order: owner -> delegate

When an unqualified property/method is accessed, the owner is checked first. Only if not found on the owner is the delegate consulted:

```groovy
class Person {
    String name
    def pretty = { "My name is $name" }
    String toString() { pretty() }
}
class Thing { String name }

def p = new Person(name: 'Sarah')
def t = new Thing(name: 'Teapot')
assert p.toString() == 'My name is Sarah'

p.pretty.delegate = t
assert p.toString() == 'My name is Sarah'  // owner (Person) still wins
```

##### DELEGATE_FIRST
Resolution order: delegate -> owner

Reverses the lookup priority -- delegate is checked before owner:

```groovy
p.pretty.resolveStrategy = Closure.DELEGATE_FIRST
assert p.toString() == 'My name is Teapot'  // delegate now wins
```

##### OWNER_ONLY
Only the owner is searched. The delegate is completely ignored:

```groovy
closure.resolveStrategy = Closure.OWNER_ONLY
```

##### DELEGATE_ONLY
Only the delegate is searched. The owner is completely ignored:

```groovy
class Person {
    String name
    int age
    def fetchAge = { age }
}
class Thing { String name }

def p = new Person(name:'Jessica', age:42)
def t = new Thing(name:'Printer')
def cl = p.fetchAge
cl.delegate = t
cl.resolveStrategy = Closure.DELEGATE_ONLY
try {
    cl()           // fails!
    assert false
} catch (MissingPropertyException ex) {
    // 'age' is not on the delegate (Thing), so MissingPropertyException
}
```

##### TO_SELF
Resolution only on the closure class itself. Used for advanced meta-programming when implementing custom Closure subclasses:

```groovy
closure.resolveStrategy = Closure.TO_SELF
// Only makes sense with custom Closure subclasses
```

#### Delegation Strategy Summary Table

| Strategy       | Resolution Order          | Use Case                    |
|----------------|---------------------------|-----------------------------|
| OWNER_FIRST    | owner -> delegate         | Default behavior            |
| DELEGATE_FIRST | delegate -> owner         | DSL builders                |
| OWNER_ONLY     | owner only                | Strict scoping              |
| DELEGATE_ONLY  | delegate only             | Pure delegation             |
| TO_SELF        | closure class itself only | Custom Closure subclasses   |

### 3.6 Closures in GStrings

GStrings evaluate expressions at creation time, NOT lazily:

```groovy
def x = 1
def gs = "x = ${x}"
x = 2
assert gs == 'x = 1'    // still old value -- captured at creation
```

Use the closure syntax `${-> expr}` for lazy evaluation:
```groovy
def x = 1
def gs = "x = ${-> x}"
assert gs == 'x = 1'
x = 2
assert gs == 'x = 2'    // lazily evaluated on each toString() call
```

CRITICAL DISTINCTION: `${x}` is an EXPRESSION (value captured once). `${-> x}` is a CLOSURE (re-evaluated each time).

Mutation vs reassignment with object references:
```groovy
class Person { String name; String toString() { name } }
def sam = new Person(name:'Sam')
def p = sam
def gs = "Name: ${p}"
p = new Person(name:'Lucy')
assert gs == 'Name: Sam'   // reassignment of p doesn't affect gs

sam.name = 'Lucy'
assert gs == 'Name: Lucy'  // but mutation of the captured object IS visible
```

Closures in GStrings can accept zero or one `StringWriter` parameter:
```groovy
def result = "Result: ${w -> w << "hello"}"
assert result == "Result: hello"
```

### 3.7 Closure Coercion (SAM Types)

Closures can be automatically coerced to functional interfaces with one abstract method (Single Abstract Method):

```groovy
Runnable r = { println "running" }
r.run()

java.util.Comparator comp = { a, b -> a <=> b }
```

Explicit coercion with `as`:
```groovy
Callable<Integer> task = { 42 } as Callable
assert task.call() == 42
```

### 3.8 Functional Programming

#### Currying (Partial Application)

**Left currying (`curry`)** -- fixes the leftmost parameter(s):
```groovy
def nCopies = { int n, String str -> str*n }
def twice = nCopies.curry(2)
assert twice('bla') == 'blabla'
assert twice('bla') == nCopies(2, 'bla')
```

**Right currying (`rcurry`)** -- fixes the rightmost parameter(s):
```groovy
def nCopies = { int n, String str -> str*n }
def blah = nCopies.rcurry('bla')
assert blah(2) == 'blabla'
```

**Index-based currying (`ncurry`)** -- fixes parameter(s) at arbitrary index:
```groovy
def volume = { double l, double w, double h -> l*w*h }
def fixedWidth = volume.ncurry(1, 2d)
assert volume(3d, 2d, 4d) == fixedWidth(3d, 4d)

def fixedWidthAndHeight = volume.ncurry(1, 2d, 4d)
assert volume(3d, 2d, 4d) == fixedWidthAndHeight(3d)
```

#### Memoization

Cache closure results for repeated calls with identical arguments. Uses LRU cache strategy:

```groovy
def fib
fib = { long n -> n<2?n:fib(n-1)+fib(n-2) }.memoize()
assert fib(25) == 75025   // fast via caching
```

Variants:
- `memoize()` -- unlimited cache
- `memoizeAtMost(n)` -- cache at most n values (LRU eviction)
- `memoizeAtLeast(n)` -- cache at least n values
- `memoizeBetween(n, m)` -- cache between n and m values

#### Composition

Use `<<` (right-to-left / compose) and `>>` (left-to-right / andThen):

```groovy
def plus2  = { it + 2 }
def times3 = { it * 3 }

def times3plus2 = plus2 << times3         // compose: times3 first, then plus2
assert times3plus2(3) == 11               // (3 * 3) + 2
assert times3plus2(3) == plus2(times3(3))

def plus2times3 = times3 << plus2         // plus2 first, then times3
assert plus2times3(3) == 15               // (3 + 2) * 3

// >> is the reverse of <<
assert times3plus2(3) == (times3 >> plus2)(3)
```

#### Trampoline (Tail-Call Optimization)

Avoids stack overflow in recursive algorithms. The recursive call returns a TrampolineClosure instead of stacking frames:

```groovy
def factorial
factorial = { int n, def accu = 1G ->
    if (n < 2) return accu
    factorial.trampoline(n - 1, n * accu)
}
factorial = factorial.trampoline()

assert factorial(1)    == 1
assert factorial(3)    == 6
assert factorial(1000) // computes without stack overflow!
```

How it works: `trampoline()` wraps the closure. Each recursive call via `.trampoline(args)` returns a thunk (deferred computation) instead of making a real recursive call. The trampoline framework iteratively evaluates thunks until a non-thunk result is produced.

#### Method Pointers (`.&` Operator)

Reference methods as closures:

```groovy
def str = "hello"
def toUpper = str.&toUpperCase
assert toUpper() == "HELLO"
```

Method pointers create Closure instances, so all closure features (currying, composition, etc.) are available:

```groovy
def myMethod(int x) { x * 2 }
def methodClosure = this.&myMethod
def curried = methodClosure.curry(5)
```

---

## Quick Reference: Key Differences from Java

| Feature | Java | Groovy |
|---------|------|--------|
| Method dispatch | Compile-time (single dispatch) | Runtime (multi-method dispatch) |
| Fields without modifier | Package-private field | Property with getter/setter |
| Default constructors | No-arg only | No-arg + map-based named args |
| Multiple inheritance | Not possible | Possible via traits |
| Closures | Lambdas (since Java 8) | Full closures with delegation |
| Inner class access | Standard Java rules | Same, inner can access outer private |
| Interface methods | Abstract (default since Java 8) | Always public |
| Generics | Enforced at compile-time | Advisory in dynamic mode |
| Annotations | No closure parameters | Supports closure parameters |
| Runtime interface impl | Not possible | `as` keyword for coercion |
| Trait state | No (Java 8 defaults) | Yes (private fields in traits) |

## Key Patterns

### Builder Pattern with Closure Delegation
```groovy
class HtmlBuilder {
    String result = ''
    void div(Closure cl) {
        result += '<div>'
        cl.delegate = this
        cl.resolveStrategy = Closure.DELEGATE_FIRST
        cl()
        result += '</div>'
    }
    void p(String text) {
        result += "<p>$text</p>"
    }
}

def builder = new HtmlBuilder()
builder.div {
    p 'Hello'    // resolved on delegate (HtmlBuilder)
    p 'World'
}
assert builder.result == '<div><p>Hello</p><p>World</p></div>'
```

### Trait-Based Mixin Pattern
```groovy
trait Serializable {
    byte[] serialize() { /* ... */ }
}
trait Loggable {
    void log(String msg) { println "[${this.class.name}] $msg" }
}

class MyService implements Serializable, Loggable {
    void process() {
        log 'Processing...'
    }
}
```

### Stackable Trait Decorators
```groovy
interface Transform {
    String transform(String s)
}
trait UpperCase implements Transform {
    String transform(String s) { s.toUpperCase() }
}
trait ExclamationMark implements Transform {
    String transform(String s) { super.transform(s) + '!' }
}
trait QuestionMark implements Transform {
    String transform(String s) { super.transform(s) + '?' }
}

class Transformer implements UpperCase, ExclamationMark, QuestionMark {}
def t = new Transformer()
assert t.transform('hello') == 'HELLO!?'
// Chain: QuestionMark -> ExclamationMark -> UpperCase
```
