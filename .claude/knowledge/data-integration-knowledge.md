# Groovy 2.4.21 Data & Integration Knowledge Base

## 1. JSON Processing

### 1.1 JsonSlurper (Parsing)

**Package:** `groovy.json.JsonSlurper`

**Purpose:** Parses JSON text into Groovy data structures (maps, lists, primitives).

**Parse Methods:**
- `parseText(String)` - parses JSON string
- `parseFile(File)` - parses JSON from file
- `parse(Reader)` / `parse(InputStream)` / `parse(URL)` - various source overloads

**Type Mapping:**

| JSON Type | Groovy Type |
|-----------|-------------|
| string | `java.lang.String` |
| number (integer) | `java.lang.Integer` |
| number (decimal) | `java.math.BigDecimal` |
| object | `java.util.LinkedHashMap` |
| array | `java.util.ArrayList` |
| true/false | `true`/`false` (Boolean) |
| null | `null` |
| date | `java.util.Date` (format: `yyyy-MM-dd'T'HH:mm:ssZ`) |

**Basic Usage:**
```groovy
def jsonSlurper = new JsonSlurper()
def object = jsonSlurper.parseText('{ "name": "John Doe" }')
assert object instanceof Map
assert object.name == 'John Doe'

// Arrays
def object2 = jsonSlurper.parseText('{ "myList": [4, 8, 15, 16, 23, 42] }')
assert object2.myList instanceof List
assert object2.myList == [4, 8, 15, 16, 23, 42]

// Numeric types
def object3 = jsonSlurper.parseText('{ "simple": 123, "fraction": 123.66, "exponential": 123e12 }')
assert object3.simple.class == Integer
assert object3.fraction.class == BigDecimal
assert object3.exponential.class == BigDecimal
```

**Parser Variants (JsonParserType enum):**

1. **CHAR_BUFFER (JsonParserCharArray)** - Default parser. Operates on character arrays with "chopping" mechanism.

2. **INDEX_OVERLAY (JsonFastParser)** - Fastest parser. Uses index-overlay technique avoiding new char array creation. Best for files under 2MB. Caution: don't cache parsed maps long-term as they reference original char buffer.

3. **LAX (JsonParserLax)** - Relaxed parser. Permits comments, unquoted strings, non-strict grammar (not ECMA-404 compliant). Similar performance to INDEX_OVERLAY.

4. **CHARACTER_SOURCE (JsonParserUsingCharacterSource)** - For very large files (2MB+). Uses "character windowing" for constant performance regardless of file size.

**Setting Parser Type:**
```groovy
def jsonSlurper = new JsonSlurper(type: JsonParserType.INDEX_OVERLAY)
def object = jsonSlurper.parseText('{ "myList": [4, 8, 15, 16, 23, 42] }')
```

**Key Notes:**
- Returns pure Groovy instances, no special JSON wrapper classes
- `null` JSON values map to Groovy `null`
- Results conform to GPath expressions for navigation
- Supports ECMA-404 standard plus JavaScript comments and dates

### 1.2 JsonOutput (Serialization)

**Package:** `groovy.json.JsonOutput`

**Purpose:** Serializes Groovy objects to JSON strings.

**Core Methods:**
- `static String toJson(Object)` - overloaded for all types (String, Number, Boolean, Map, List, POGO, etc.)
- `static String prettyPrint(String)` - pretty-prints any JSON string

**Basic Usage:**
```groovy
def json = JsonOutput.toJson([name: 'John Doe', age: 42])
assert json == '{"name":"John Doe","age":42}'

// POGO serialization
class Person { String name }
def json2 = JsonOutput.toJson([new Person(name: 'John'), new Person(name: 'Max')])
assert json2 == '[{"name":"John"},{"name":"Max"}]'

// Pretty printing
def pretty = JsonOutput.prettyPrint(json)
// Output:
// {
//     "name": "John Doe",
//     "age": 42
// }
```

### 1.3 JsonGenerator (Custom Serialization)

**Builder:** `JsonGenerator.Options`

**Configuration Options:**
- `excludeNulls()` - skip null values
- `excludeFieldsByName('field1', 'field2')` - exclude specific fields
- `excludeFieldsByType(TypeClass)` - exclude by type
- `dateFormat('yyyy@MM')` - custom date format
- `addConverter(Type) { value -> ... }` - custom type converter
- `addConverter(Type) { value, key -> ... }` - converter with key context
- `build()` - creates the generator

```groovy
def generator = new JsonGenerator.Options()
    .excludeNulls()
    .excludeFieldsByName('age', 'password')
    .excludeFieldsByType(URL)
    .dateFormat('yyyy@MM')
    .build()

// Custom converter with key awareness
def generator2 = new JsonGenerator.Options()
    .addConverter(URL) { URL u, String key ->
        if (key == 'favoriteUrl') {
            u.getHost()
        } else {
            u
        }
    }
    .build()

assert generator2.toJson(person) == '{"name":"John","favoriteUrl":"groovy-lang.org"}'
```

**Converter Rules:**
- First closure parameter must match registered type exactly
- Second parameter (optional) is String key name
- Key unavailable for JSON arrays

### 1.4 JsonBuilder (DSL-based)

**Package:** `groovy.json.JsonBuilder`

Extends GroovyObjectSupport, implements Writable.

**Constructors:**
- `JsonBuilder()` - empty builder
- `JsonBuilder(Object content)` - with pre-existing data

**call() Overloads:**
```groovy
def json = new JsonBuilder()

// Map arguments -> root object
json name: "Guillaume", age: 33
// {"name":"Guillaume","age":33}

// List -> root array
json([1, 2, 3])    // [1,2,3]

// Varargs -> root array
json 1, 2, 3       // [1,2,3]

// Closure -> root object
json { name "Guillaume"; age 33 }
// {"name":"Guillaume","age":33}

// Collection with closure -> array of objects
json(people) { Person p -> name p.name }
```

**invokeMethod() - Dynamic named keys:**
```groovy
json.person { name "Guillaume"; age 33 }
// {"person":{"name":"Guillaume","age":33}}

json.person name: "Guillaume", age: 33
json.person(name: "Guillaume", age: 33) { town "Paris" }
json.person()  // {"person":{}}
```

**Output:**
- `toString()` - compact JSON string
- `toPrettyString()` - formatted JSON
- `writeTo(Writer out)` - stream to writer
- `getContent()` - internal data structure

### 1.5 StreamingJsonBuilder

**Package:** `groovy.json.StreamingJsonBuilder`

Streams directly to a writer without in-memory data structure. More memory-efficient.

**Constructors:**
- `StreamingJsonBuilder(Writer writer)`
- `StreamingJsonBuilder(Writer writer, Object content)`

**Usage:**
```groovy
new StringWriter().with { w ->
    def builder = new groovy.json.StreamingJsonBuilder(w)
    builder.people {
        person {
            firstName 'Tim'
            lastName 'Yates'
            address(city: 'Manchester', country: 'UK')
        }
    }
}
```

---

## 2. XML Processing

### 2.1 Parsing XML

**Two Main Parsers:**

| Feature | XmlSlurper | XmlParser |
|---------|-----------|-----------|
| Return Type | `GPathResult` | `Node` objects |
| Evaluation | Lazy | Eager |
| Updates | Requires re-parsing | Immediate visibility |
| Memory | Lower for selective access | Complete structure in memory |
| Best For | Transforming docs, reading few nodes | Update and read simultaneously |

Both based on SAX (low memory footprint), both support `parseText()`, `parseFile()`, and other parse overloads.

**XmlSlurper:**
```groovy
def list = new XmlSlurper().parseText(text)
assert list instanceof groovy.xml.slurpersupport.GPathResult
assert list.technology.name == 'Groovy'
```

**XmlParser:**
```groovy
def list = new XmlParser().parseText(text)
assert list instanceof groovy.util.Node
assert list.technology.name.text() == 'Groovy'
```

**DOMCategory** - Adds GPath operations to Java's DOM classes:
```groovy
def reader = new StringReader(CAR_RECORDS)
def doc = DOMBuilder.parse(reader)
def records = doc.documentElement

use(DOMCategory) {
    assert records.car.size() == 3
}
```

### 2.2 GPath Navigation

**Dot Notation:**
```groovy
def response = new XmlSlurper().parseText(books)
def author = response.value.books.book[0].author
assert author.text() == 'Miguel de Cervantes'
```

**Attribute Access (three syntaxes):**
```groovy
def bookId1 = book.@id          // Direct
def bookId2 = book['@id']       // Map notation
def bookId3 = book["@id"]       // Alternative map
```

**Type Conversion:**
- `toInteger()`, `toFloat()`, `toBigInteger()`, `text()`

**Children Navigation (`*` or `children()`):**
```groovy
def catcherInTheRye = response.value.books.'*'.find { node ->
    node.name() == 'book' && node.@id == '2'
}
```

**Depth-First (`**` or `depthFirst()`):**
```groovy
def bookId = response.'**'.find { book ->
    book.author.text() == 'Lewis Carroll'
}.@id

def titles = response.'**'.findAll { node -> node.name() == 'title' }*.text()
```

**Breadth-First (`breadthFirst()`):**
```groovy
// depthFirst: finishes all nodes on a level before going deeper
// breadthFirst: goes as far down as possible before lateral movement
```

**Filtering:**
```groovy
def titles = response.value.books.book.findAll { book ->
    book.@id.toInteger() > 2
}*.title
```

### 2.3 Creating XML

#### MarkupBuilder
```groovy
def writer = new StringWriter()
def xml = new MarkupBuilder(writer)

// Simple element
xml.movie("the godfather")
// <movie>the godfather</movie>

// Element with attributes
xml.movie(id: "2", "the godfather")
// <movie id='2'>the godfather</movie>

// Nested elements
xml.movie(id: 2) {
    name("the godfather")
}

// Namespaces
xml.'x:movies'('xmlns:x': 'http://www.groovy-lang.org') {
    'x:movie'(id: 1, 'the godfather')
    'x:movie'(id: 2, 'ronin')
}

// Mixed code and logic
xml.'x:movies'('xmlns:x': 'http://www.groovy-lang.org') {
    (1..3).each { n ->
        'x:movie'(id: n, "the godfather $n")
        if (n % 2 == 0) {
            'x:movie'(id: n, "the godfather $n (Extended)")
        }
    }
}

// Complex nested structure
xml.records() {
    car(name: 'HSV Maloo', make: 'Holden', year: 2006) {
        country('Australia')
        record(type: 'speed', 'Production Pickup Truck with speed of 271kph')
    }
}
```

#### StreamingMarkupBuilder
Returns a `Writable` for streaming markup to a Writer.
```groovy
def xml = new StreamingMarkupBuilder().bind {
    records {
        car(name: 'HSV Maloo', make: 'Holden', year: 2006) {
            country('Australia')
            record(type: 'speed', 'Production Pickup Truck with speed of 271kph')
        }
    }
}
def records = new XmlSlurper().parseText(xml.toString())
```

#### MarkupBuilderHelper (mkp property)
Available methods: `comment()`, `yield()`, `yieldUnescaped()`, `pi()`, `xmlDeclaration()`, `out`, `namespaces`

```groovy
// In MarkupBuilder
xmlMarkup.rules {
    mkp.comment('THIS IS THE MAIN RULE')
    rule(sentence: mkp.yield('3 > n'))
}
// Output contains: 3 &gt; n and <!-- THIS IS THE MAIN RULE -->

// In StreamingMarkupBuilder
def xml = new StreamingMarkupBuilder().bind {
    records {
        car(name: mkp.yield('3 < 5'))       // Escaped: 3 &lt; 5
        car(name: mkp.yieldUnescaped('1 < 3'))  // Raw: 1 < 3
    }
}
```

#### DOMToGroovy
Converts DOM documents to MarkupBuilder code:
```groovy
def builder = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
def document = builder.parse(inputStream)
def converter = new DomToGroovy(new PrintWriter(output))
converter.print(document)
```

### 2.4 Manipulating XML

**Adding Nodes (XmlParser):**
```groovy
// createNode from parser
def numberOfResults = parser.createNode(response, new QName("numberOfResults"), [:])
numberOfResults.value = "1"

// appendNode from document
response.appendNode(new QName("numberOfResults"), [:], "1")
```

**Modifying Nodes - replaceNode():**
```groovy
// XmlParser - changes visible immediately
response.value.books.book[0].replaceNode {
    book(id: "3") { title("To Kill a Mockingbird"); author(id: "3", "Harper Lee") }
}

// XmlSlurper - must re-parse to see changes
response.value.books.book[0].replaceNode { ... }
def result = new StreamingMarkupBuilder().bind { mkp.yield response }.toString()
def changed = new XmlSlurper().parseText(result)
```

**Modifying Attributes:**
```groovy
// Both XmlParser and XmlSlurper
response.@numberOfResults = "1"
// XmlSlurper note: adding attributes does NOT require re-evaluation
```

**Printing/Serializing - XmlUtil.serialize():**
```groovy
def nodeAsText = XmlUtil.serialize(nodeToSerialize)
// Works with Node, GPathResult, String sources
```

---

## 3. SQL/Database Interaction

### 3.1 Connecting

**Sql.newInstance():**
```groovy
import groovy.sql.Sql

def sql = Sql.newInstance('jdbc:hsqldb:mem:yourDB', 'sa', '', 'org.hsqldb.jdbcDriver')
sql.close()
```

**withInstance() (auto-close):**
```groovy
Sql.withInstance(url, user, password, driver) { sql ->
    // use sql
}
```

**DataSource:**
```groovy
def dataSource = new JDBCDataSource(database: 'jdbc:hsqldb:mem:yourDB', user: 'sa', password: '')
def sql = new Sql(dataSource)

// Or with Apache Commons DBCP
def ds = new BasicDataSource(driverClassName: "org.hsqldb.jdbcDriver",
    url: 'jdbc:hsqldb:mem:yourDB', username: 'sa', password: '')
def sql = new Sql(ds)
```

**@Grab for drivers:**
```groovy
@Grab('org.hsqldb:hsqldb:2.7.4')
@GrabConfig(systemClassLoader=true)  // Required for JDBC
```

### 3.2 Executing SQL

```groovy
sql.execute '''
  CREATE TABLE Author (
    id INTEGER GENERATED BY DEFAULT AS IDENTITY,
    firstname VARCHAR(64),
    lastname VARCHAR(64)
  );
'''
```

### 3.3 Basic CRUD

**INSERT:**
```groovy
// Simple execute
sql.execute "INSERT INTO Author (firstname, lastname) VALUES ('Dierk', 'Koenig')"

// executeInsert with placeholders (returns generated keys)
def keys = sql.executeInsert 'INSERT INTO Author (firstname, lastname) VALUES (?,?)', ['Jon', 'Skeet']
assert keys[0] == [1]

// executeInsert with GStrings and key names
def first = 'Guillaume'; def last = 'Laforge'
def myKeys = sql.executeInsert """
  INSERT INTO Author (firstname, lastname) VALUES (${first}, ${last})
""", ['ID']
assert myKeys[0] == [ID: 2]
```

**READ:**
```groovy
// query() - raw ResultSet access
sql.query('SELECT firstname, lastname FROM Author') { resultSet ->
    while (resultSet.next()) {
        def f = resultSet.getString(1)
        def l = resultSet.getString('lastname')
    }
}

// eachRow() - Groovy-style iteration
sql.eachRow('SELECT firstname, lastname FROM Author') { row ->
    def first = row[0]       // By index
    def last = row.lastname  // By name
}

// firstRow() - single row
def first = sql.firstRow('SELECT lastname, firstname FROM Author')

// rows() - all rows as list
List authors = sql.rows('SELECT firstname, lastname FROM Author')

// Scalar
assert sql.firstRow('SELECT COUNT(*) AS num FROM Author').num == 3
```

**UPDATE:**
```groovy
sql.execute "UPDATE Author SET firstname='Erik' where lastname='Thorvaldsson'"

def updateCount = sql.executeUpdate "UPDATE Author SET lastname='Pragt' where lastname='Thorvaldsson'"
assert updateCount == 1
```

**DELETE:**
```groovy
sql.execute "DELETE FROM Author WHERE lastname = 'Skeet'"
```

### 3.4 Advanced SQL

**Transactions:**
```groovy
sql.withTransaction {
    sql.execute "INSERT INTO Author (firstname, lastname) VALUES ('Dierk', 'Koenig')"
    sql.execute "INSERT INTO Author (firstname, lastname) VALUES ('Jon', 'Skeet')"
    // Automatically rolled back if any statement fails
}
```

**Batching:**
```groovy
// Basic batch
sql.withBatch(3) { stmt ->
    stmt.addBatch "INSERT INTO Author (firstname, lastname) VALUES ('Dierk', 'Koenig')"
    stmt.addBatch "INSERT INTO Author (firstname, lastname) VALUES ('Paul', 'King')"
}

// Prepared statement batch
def qry = 'INSERT INTO Author (firstname, lastname) VALUES (?,?)'
sql.withBatch(3, qry) { ps ->
    ps.addBatch('Dierk', 'Koenig')
    ps.addBatch('Paul', 'King')
    ps.addBatch('Guillaume', 'Laforge')
}

// Enable SQL logging
import java.util.logging.*
Logger.getLogger('groovy.sql').level = Level.FINE
```

**Pagination:**
```groovy
def qry = 'SELECT * FROM Author'
assert sql.rows(qry, 1, 3)*.firstname == ['Dierk', 'Paul', 'Guillaume']  // offset 1, max 3
assert sql.rows(qry, 4, 3)*.firstname == ['Hamlet', 'Cedric', 'Erik']
assert sql.rows(qry, 7, 3)*.firstname == ['Jon']
```

**Metadata:**
```groovy
// Row metadata
sql.eachRow("SELECT * FROM Author WHERE firstname = 'Dierk'") { row ->
    def md = row.getMetaData()
    assert md.getTableName(1) == 'AUTHOR'
    assert (1..md.columnCount).collect{ md.getColumnName(it) } == ['ID', 'FIRSTNAME', 'LASTNAME']
}

// Metadata closure parameter
def metaClosure = { meta -> assert meta.getColumnName(1) == 'FIRSTNAME' }
def rowClosure = { row -> assert row.FIRSTNAME == 'Dierk' }
sql.eachRow("SELECT firstname FROM Author WHERE firstname = 'Dierk'", metaClosure, rowClosure)

// Connection metadata
def md = sql.connection.metaData
assert md.driverName == 'HSQL Database Engine Driver'
```

**Named Parameters:**
```groovy
// Colon form
sql.execute "INSERT INTO Author (firstname, lastname) VALUES (:first, :last)",
    first: 'Dierk', last: 'Koenig'

// Question mark form
sql.execute "INSERT INTO Author (firstname, lastname) VALUES (?.first, ?.last)",
    first: 'Jon', last: 'Skeet'

// Named-ordinal (object properties)
class Rockstar { String first, last }
def pogo = new Rockstar(first: 'Paul', last: 'McCartney')
def map = [lion: 'King']
sql.execute "INSERT INTO Author (firstname, lastname) VALUES (?1.first, ?2.lion)", pogo, map
```

**Stored Procedures:**
```groovy
// Creating a stored function
sql.execute """
  CREATE FUNCTION FULL_NAME (p_lastname VARCHAR(64))
  RETURNS VARCHAR(100)
  READS SQL DATA
  BEGIN ATOMIC
    DECLARE ans VARCHAR(100);
    SELECT CONCAT(firstname, ' ', lastname) INTO ans FROM Author WHERE lastname = p_lastname;
    RETURN ans;
  END
"""

// Calling function with output parameter
def result = sql.firstRow("{? = call FULL_NAME(?)}", ['Koenig'])
assert result[0] == 'Dierk Koenig'

// Stored procedure with OUT parameters
sql.call("{call CONCAT_NAME(?, ?, ?)}", [Sql.VARCHAR, 'Dierk', 'Koenig']) {
    fullname -> assert fullname == 'Dierk Koenig'
}

// INOUT parameters
sql.call scall, [Sql.inout(Sql.VARCHAR("MESSAGE")), 1, Sql.VARCHAR], {
    res, p_err -> assert res == 'MESSAGE_OK' && p_err == 'RET_OK'
}

// Table-returning functions
sql.eachRow('CALL SELECT_AUTHOR_INITIALS()') { result << "$it.firstInitial$it.lastInitial" }
```

### 3.5 DataSets

DataSets provide mini ORM functionality using object properties instead of SQL.

```groovy
def authorDS = sql.dataSet('Author')
def result = authorDS.findAll{ it.firstname > 'Dierk' }
        .findAll{ it.lastname < 'Pragt' }
        .sort{ it.lastname }
        .reverse()
assert result.rows()*.firstname == ['Eric', 'Guillaume', 'Paul']
```

---

## 4. Template Engines

### 4.1 Framework

Abstract `TemplateEngine` base class + `Template` interface. Five built-in engines.

### 4.2 SimpleTemplateEngine

JSP-like syntax. Cannot handle strings > 64k.

**Syntax:** `$variable`, `${expression}`, `<% code %>`, `<%= expression %>`

```groovy
def text = 'Dear "$firstname $lastname",\nSo nice to meet you in <% print city %>.\nSee you in ${month},\n${signed}'
def binding = ["firstname":"Sam", "lastname":"Pullara", "city":"San Francisco", "month":"December", "signed":"Groovy-Dev"]
def engine = new groovy.text.SimpleTemplateEngine()
def template = engine.createTemplate(text).make(binding)
def result = template.toString()
```

### 4.3 StreamingTemplateEngine

Same syntax as SimpleTemplateEngine but uses writable closures for scalability. Handles strings > 64k.

```groovy
def text = '''\
Dear <% out.print firstname %> ${lastname},
We <% if (accepted) out.print 'are pleased' else out.print 'regret' %> \
to inform you that your paper entitled
'$title' was ${ accepted ? 'accepted' : 'rejected' }.
The conference committee.'''

def template = new groovy.text.StreamingTemplateEngine().createTemplate(text)
String response = template.make([firstname: "Grace", lastname: "Hopper", accepted: true, title: 'Groovy for COBOL programmers'])
```

### 4.4 GStringTemplateEngine

Stores templates as writeable closures. Uses `out <<` for streaming.

```groovy
// External file: test.template
// Dear "$firstname $lastname",
// So nice to meet you in <% out << city %>.
def engine = new groovy.text.GStringTemplateEngine()
def template = engine.createTemplate(new File('test.template')).make(binding)
```

### 4.5 XmlTemplateEngine

Source and output are valid XML. Auto-escapes `<`, `>`, `"`, `'`. Pretty-prints output.

**Special tags:** `<gsp:scriptlet>`, `<gsp:expression>`, plus `${expression}` and `$variable`.

```groovy
def engine = new groovy.text.XmlTemplateEngine()
def text = '''\
<document xmlns:gsp='http://groovy.codehaus.org/2005/gsp' xmlns:foo='baz' type='letter'>
    <gsp:scriptlet>def greeting = "${salutation}est"</gsp:scriptlet>
    <gsp:expression>greeting</gsp:expression>
    <foo:to>$firstname "$nickname" $lastname</foo:to>
    How are you today?
</document>'''
def template = engine.createTemplate(text).make(binding)
```

### 4.6 MarkupTemplateEngine

Complete, optimized engine using builder DSL syntax. Compiled to bytecode.

**Configuration (TemplateConfiguration):**

| Option | Default | Description |
|--------|---------|-------------|
| `declarationEncoding` | null | Encoding in XML declaration |
| `expandEmptyElements` | false | `<p/>` vs `<p></p>` |
| `useDoubleQuotes` | false | `attr="value"` vs `attr='value'` |
| `newLineString` | System default | Newline string |
| `autoEscape` | false | Auto-escape model variables |
| `autoIndent` | false | Auto-indent after newlines |
| `autoIndentString` | 4 spaces | Indent string |
| `autoNewLine` | false | Auto-insert newlines |
| `baseTemplateClass` | BaseTemplate | Custom template base class |
| `locale` | Default locale | Default locale for i18n |

**Support Methods:**

| Method | Purpose |
|--------|---------|
| `yield` | Render with escaping |
| `yieldUnescaped` | Render raw |
| `xmlDeclaration()` | XML declaration |
| `comment` | XML comment |
| `newLine` | Insert newline |
| `pi` | Processing instruction |
| `tryEscape` | Conditional escape |

**Basic Usage:**
```groovy
TemplateConfiguration config = new TemplateConfiguration()
config.setAutoNewLine(true)
config.setAutoIndent(true)
MarkupTemplateEngine engine = new MarkupTemplateEngine(config)
Template template = engine.createTemplate("p('test template')")
Writable output = template.make(model)
output.writeTo(writer)
```

**Includes:**
```groovy
include template: 'other_template.tpl'     // Another template
include unescaped: 'raw.txt'               // Raw content
include escaped: 'to_be_escaped.txt'       // Escaped content
includeGroovy('name')                      // Helper methods
```

**Fragments:**
```groovy
ul {
    pages.each {
        fragment "li(line)", line:it
    }
}
```

**Layouts:**
```groovy
// layout-main.tpl
html { head { title(title) }; body { bodyContents() } }

// Using layout
layout 'layout-main.tpl',
    title: 'Layout example',
    bodyContents: contents { p('This is the body') }

// With model inheritance (second param = true)
layout 'layout-main.tpl', true,
    title: 'overridden title',
    bodyContents: contents { p('This is the body') }
```

**Auto-escaping:**
```groovy
config.setAutoEscape(true)
// Bypass for specific variables:
div(unescaped.unsafeContents)
```

**String with embedded markup (gotcha):**
```groovy
// Use stringOf or $tag notation
p("This is a ${stringOf {a(href:'target.html', "link")}} to another page")
// Or
p("This is a ${$a(href:'target.html', "link")} to another page")
```

**Internationalization:**
- Locale-specific files: `file.tpl`, `file_fr_FR.tpl`, `file_en_US.tpl`
- Configured via `config.setLocale(Locale.FRENCH)`

**Type Checking:**
```groovy
// Method 1: Pass model types
Map<String,String> modelTypes = ['pages': 'List<Page>']
Template template = engine.createTypeCheckedModelTemplate("main.tpl", modelTypes)

// Method 2: Inline declaration
modelTypes = { List<Page> pages }
// Errors caught at compile time, not runtime
```

**Custom Template Classes:**
```groovy
public abstract class MyTemplate extends BaseTemplate {
    // Custom constructor matching BaseTemplate signature
    // Add custom methods available in templates
    boolean hasModule(String name) { modules?.any { it.name == name } }
}
config.setBaseTemplateClass(MyTemplate.class)
```

---

## 5. Integrating Groovy in Java

### 5.1 Eval Class

Simplest method, no caching. For one-liners only.

```groovy
assert Eval.me('33*3') == 99
assert Eval.me('"foo".toUpperCase()') == 'FOO'
assert Eval.x(4, '2*x') == 8
assert Eval.me('k', 4, '2*k') == 8
assert Eval.xy(4, 5, 'x*y') == 20
assert Eval.xyz(4, 5, 6, 'x*y+z') == 26
```

### 5.2 GroovyShell

**Basic evaluation:**
```groovy
def shell = new GroovyShell()
def result = shell.evaluate '3*5'
def result2 = shell.evaluate(new StringReader('3*5'))
assert result == result2
def script = shell.parse '3*5'
assert script instanceof groovy.lang.Script
assert script.run() == 15
```

**Binding for data sharing:**
```groovy
def sharedData = new Binding()
def shell = new GroovyShell(sharedData)
sharedData.setProperty('text', 'I am shared data!')
sharedData.setProperty('date', new Date())
String result = shell.evaluate('"At $date, $text"')

// Scripts write to binding using undeclared variables (not def or typed)
shell.evaluate('foo=123')
assert sharedData.getProperty('foo') == 123
```

**Thread safety with separate Script instances:**
```groovy
def shell = new GroovyShell()
def b1 = new Binding(x:3)
def b2 = new Binding(x:4)
def script1 = shell.parse('x = 2*x')
def script2 = shell.parse('x = 2*x')
script1.binding = b1; script2.binding = b2
def t1 = Thread.start { script1.run() }
def t2 = Thread.start { script2.run() }
[t1,t2]*.join()
assert b1.getProperty('x') == 6
assert b2.getProperty('x') == 8
```

**Custom Script Base Class:**
```groovy
abstract class MyScript extends Script {
    String name
    String greet() { "Hello, $name!" }
}

def config = new CompilerConfiguration()
config.scriptBaseClass = 'MyScript'
def shell = new GroovyShell(this.class.classLoader, new Binding(), config)
def script = shell.parse('greet()')
script.setName('Michel')
assert script.run() == 'Hello, Michel!'
```

### 5.3 GroovyClassLoader

**WARNING: Keeps references to all created classes - easy to create memory leaks.**

```groovy
def gcl = new GroovyClassLoader()
def clazz = gcl.parseClass('class Foo { void doIt() { println "ok" } }')
assert clazz.name == 'Foo'
def o = clazz.newInstance()
o.doIt()

// String parsing creates different Class instances each time!
def clazz1 = gcl.parseClass('class Foo { }')
def clazz2 = gcl.parseClass('class Foo { }')
assert clazz1 != clazz2  // Different classes!

// File parsing uses caching - returns same class
def clazz1 = gcl.parseClass(file)
def clazz2 = gcl.parseClass(new File(file.absolutePath))
assert clazz1 == clazz2  // Same class (cached)
```

### 5.4 GroovyScriptEngine

Handles script reloading and dependencies. URL-based construction.

```groovy
def binding = new Binding()
def engine = new GroovyScriptEngine([tmpDir.toURI().toURL()] as URL[])
// Automatically reloads modified scripts
def greeter = engine.run('ReloadingTest.groovy', binding)
```

### 5.5 CompilationUnit

Advanced compilation control with phases, source units, AST transformations. Used internally by compiler infrastructure.

### 5.6 Bean Scripting Framework (BSF)

Legacy integration via BSFManager. Provides `eval()`, `declareBean()/undeclareBean()`, `apply()`.

### 5.7 JSR 223 javax.script API

Standard Java scripting API:
```groovy
import javax.script.*

ScriptEngineManager factory = new ScriptEngineManager()
ScriptEngine engine = factory.getEngineByName("groovy")

// Evaluate
Integer sum = (Integer) engine.eval("(1..10).sum()")
assertEquals(Integer.valueOf(55), sum)

// Variable sharing
engine.put("first", "HELLO")
engine.put("second", "world")
String result = (String) engine.eval("first.toLowerCase() + ' ' + second.toUpperCase()")

// Invocable - calling functions
engine.eval("def factorial(n) { n == 1 ? 1 : n * factorial(n - 1) }")
Invocable inv = (Invocable) engine
Object result = inv.invokeFunction("factorial", 5)
assertEquals(Integer.valueOf(120), result)

// Global reference management
// #jsr223.groovy.engine.keep.globals: "phantom", "weak", "soft", or hard (default)
```

**Recommendation:** Use native Groovy integration mechanisms over JSR-223 for Groovy-only applications.

---

## 6. Servlet Support (Groovylets)

### 6.1 GroovyServlet

Executes `.groovy` scripts as servlets with automatic compilation and caching. Recompiles on source change.

### 6.2 Implicit Variables

| Variable | Bound To | Notes |
|----------|----------|-------|
| `request` | ServletRequest | |
| `response` | ServletResponse | |
| `context` | ServletContext | |
| `application` | ServletContext | |
| `session` | getSession(false) | Can be null |
| `params` | Map | Request parameters |
| `headers` | Map | Request headers |
| `out` | response.getWriter() | Read-only after first access |
| `sout` | response.getOutputStream() | Read-only after first access |
| `html` | new MarkupBuilder(out) | Read-only after first access |
| `json` | new StreamingJsonBuilder(out) | Read-only after first access |

### 6.3 Examples

**String Interpolation:**
```groovy
if (!session) { session = request.getSession(true) }
if (!session.counter) { session.counter = 1 }
println """<html><body><p>
Hello, ${request.remoteHost}: ${session.counter}! ${new Date()}
</p></body></html>"""
session.counter = session.counter + 1
```

**MarkupBuilder:**
```groovy
if (!session) { session = request.getSession(true) }
if (!session.counter) { session.counter = 1 }
html.html {
    head { title('Groovy Servlet') }
    body { p("Hello, ${request.remoteHost}: ${session.counter}! ${new Date()}") }
}
session.counter = session.counter + 1
```

### 6.4 Configuration

**web.xml:**
```xml
<servlet>
    <servlet-name>Groovy</servlet-name>
    <servlet-class>groovy.servlet.GroovyServlet</servlet-class>
</servlet>
<servlet-mapping>
    <servlet-name>Groovy</servlet-name>
    <url-pattern>*.groovy</url-pattern>
</servlet-mapping>
```

---

## 7. JMX

### 7.1 GroovyMBean

Facade for underlying MBean that acts like a normal Groovy object.

**Constructors:**
- `GroovyMBean(MBeanServerConnection server, String objectName)`
- `GroovyMBean(MBeanServerConnection server, String objectName, boolean ignoreErrors)`
- `GroovyMBean(MBeanServerConnection server, ObjectName name)`
- `GroovyMBean(MBeanServerConnection server, ObjectName name, boolean ignoreErrors)`

**Methods:**
- `getProperty(String)` / `setProperty(String, Object)` - attribute access
- `listAttributeNames()` / `listAttributeDescriptions()` / `listAttributeValues()`
- `describeAttribute(String)` - specific attribute details
- `listOperationNames()` / `listOperationDescriptions()` / `describeOperation(String)`
- `invokeMethod(String method, Object arguments)` - operation invocation
- `info()` - MBeanInfo, `name()` - ObjectName, `server()` - MBeanServerConnection

### 7.2 JmxBuilder

Factory builder class for JMX management (groovy.jmx.builder).

**Constructors:**
- `JmxBuilder()` - default
- `JmxBuilder(MBeanServerConnection svrConnection)` - with server connection

**Configuration:**
- `getDefaultJmxNameDomain()` / `setDefaultJmxNameDomain(String)`
- `getDefaultJmxNameType()` / `setDefaultJmxNameType(String)`
- `getMBeanServer()` / `setMBeanServer(MBeanServerConnection)`

**Key Nodes:**
- **export()** - export node with implicit/explicit descriptors
- **bean()** - MBean registration with ObjectName, attribute export (wildcard `*`, list, explicit), constructor export, operation export
- **timer()** - timer export with period configuration
- **listener()** - JMX event listening
- **emitter()** - JMX event emission/broadcasting

**Events:** onChange (attribute), onCall (operation), with closures (parameterless or with event parameter).

---

## 8. Security

### 8.1 SecureASTCustomizer

Restricts language grammar at compile-time. NOT a runtime security manager replacement.

**Configuration Options:**
- `closuresAllowed` - allow/disallow closures
- `methodDefinitionAllowed` - allow/disallow method definitions
- `allowedImports` / disallowed equivalents - import control
- `allowedStaticImports` / `allowedStaticStarImports`
- `allowedTokens` - restrict grammar tokens (PLUS, MINUS, MULTIPLY, DIVIDE, POWER, etc.)
- `allowedConstantTypesClasses` - restrict constant types
- `allowedReceiversClasses` - restrict method call receivers

**Example:**
```groovy
import org.codehaus.groovy.control.customizers.SecureASTCustomizer
import static org.codehaus.groovy.syntax.Types.*

def scz = new SecureASTCustomizer()
scz.with {
    closuresAllowed = false
    methodDefinitionAllowed = false
    allowedImports = []
    allowedStaticStarImports = ['java.lang.Math']
    allowedTokens = [PLUS, MINUS, MULTIPLY, DIVIDE, POWER].asImmutable()
    allowedConstantTypesClasses = [Integer, Float, Long, Double].asImmutable()
    allowedReceiversClasses = [Math, Integer, Float, Double].asImmutable()
}

def config = new CompilerConfiguration()
config.addCompilationCustomizers(scz)
def shell = new GroovyShell(config)
```

**Custom Checkers:**
```groovy
def checker = { expr -> !(expr instanceof AttributeExpression) } as SecureASTCustomizer.ExpressionChecker
scz.addExpressionCheckers(checker)
```

### 8.2 Other Compilation Customizers

**ImportCustomizer:**
```groovy
def icz = new ImportCustomizer()
icz.addImports('java.util.concurrent.atomic.AtomicInteger')
icz.addImport('CHM', 'java.util.concurrent.ConcurrentHashMap')  // Aliased
icz.addStaticImport('java.lang.Math', 'PI')
icz.addStaticImport('pi', 'java.lang.Math', 'PI')  // Aliased
icz.addStarImports('java.util.concurrent')
icz.addStaticStars('java.lang.Math')
```

**ASTTransformationCustomizer:**
```groovy
def acz = new ASTTransformationCustomizer(Log)
def acz2 = new ASTTransformationCustomizer(Log, value: 'LOGGER')
config.addCompilationCustomizers(acz)
```

**SourceAwareCustomizer:**
```groovy
def sac = new SourceAwareCustomizer(delegate)
sac.baseNameValidator = { baseName -> baseName.endsWith('Bean') }
sac.extensionValidator = { ext -> ext == 'spec' }
sac.sourceUnitValidator = { sourceUnit -> sourceUnit.AST.classes.size() == 1 }
```

**Builder DSL:**
```groovy
import static org.codehaus.groovy.control.customizers.builder.CompilerCustomizationBuilder.withConfig

withConfig(conf) {
    imports {
        normal 'my.package.MyClass'
        alias 'AI', 'java.util.concurrent.atomic.AtomicInteger'
        star 'java.util.concurrent'
        staticMember 'java.lang.Math', 'PI'
    }
    ast(Log)
    secureAst { closuresAllowed = false; methodDefinitionAllowed = false }
    source(extension: 'sgroovy') { ast(CompileStatic) }
    inline(phase:'CONVERSION') { source, context, classNode -> println "visiting $classNode" }
}
```

---

## 9. Design Patterns in Groovy

### 9.1 Abstract Factory
Maps and dynamic instantiation replace verbose factory hierarchies.
```groovy
def guessFactory = [messages: GuessGameMessages, control: GuessGameControl, converter: GuessGameInputConverter]
GameFactory.factory = guessFactory
```

### 9.2 Adapter
Four implementations: delegation, inheritance, closure-based (`as Interface`), ExpandoMetaClass (add properties on-the-fly).

### 9.3 Bouncer
Use `assert` statements instead of utility checker classes.
```groovy
assert name != null, 'name should not be null'
assert dividendStr =~ NumberChecker.NUMBER_PATTERN
```

### 9.4 Chain of Responsibility
Traditional class chain, Elvis operator chain (`?:`), Groovy switch, or Stream-based (Groovy 3+).

### 9.5 Command
Method closures (`lamp.&turnOn`), Map of lambdas/closures replace Command classes.

### 9.6 Composite
Standard tree pattern with `leftShift(<<)` for adding children.

### 9.7 Decorator
Multiple approaches: traditional wrapping, closure composition (`stamp << upper`), MOP (`invokeMethod`), ExpandoMetaClass, ProxyMetaClass with interceptors, Java Reflection Proxy, Spring AOP, GPars async decorators.

### 9.8 Delegation
`@Delegate` annotation (AST transformation) or ExpandoMetaClass-based delegation.

### 9.9 Flyweight
Factory maintains shared intrinsic state instances referenced by multiple objects.

### 9.10 Iterator
Built into Groovy: `each`, `eachWithIndex`, `for..in`, `eachByte`, `eachFile`, `eachDir`, `eachLine`, `eachObject`, `eachMatch`.

### 9.11 Loan My Resource
Built into Groovy helper methods: `withPrintWriter`, `eachLine`, `withReader`, `splitEachLine`. Resources automatically closed.

### 9.12 Monoids
`inject()` (fold operation), Streams `reduce()`. Properties: closure, associativity, identity element. Parallel aggregation with GPars.

### 9.13 Null Object
NullObject classes or Groovy's safe-dereference operator (`?.`). `max()` is null-aware.

### 9.14 Observer
Classic interface, closure-based (`{ messages << it }`), lambdas, `@Bindable`/`@Vetoable` annotations with PropertyChangeListener/VetoableChangeListener.

### 9.15 Pimp My Library
Categories add methods via `use(Category) { ... }`. Static methods with special first parameter.

### 9.16 Proxy
Remote proxy with Socket/ObjectStreams. Server-side accumulator pattern.

### 9.17 Singleton
Classic Java style, MetaProgramming (MetaClassImpl override), Guice `@Singleton`, Spring bean definitions.

### 9.18 State
State classes with context reference controlling transitions.

### 9.19 Strategy
Interface-based, closure-based strategies, lambda-based (Predicate).

### 9.20 Template Method
Abstract base class with hook methods, or closure-based (pass step closures).

### 9.21 Visitor
Double-dispatch with accept/visit methods. Multiple visitors for different operations on same structure.

### Key Groovy Features Simplifying Patterns

| Feature | Benefit |
|---------|---------|
| Closures | Replace command/strategy/visitor classes |
| Safe-dereference (`?.`) | Eliminates null checks |
| Metaprogramming | Runtime behavior modification for decorators/proxies |
| Duck Typing | Removes need for explicit interfaces |
| Maps | Simplify factory and strategy selection |
| Method References (`&`) | Convert methods to closures |
| Elvis (`?:`) | Chain fallback logic |
| `@Delegate` | Built-in delegation via AST |
| Categories | Library extension without modifying source |
| ExpandoMetaClass | Dynamic property/method addition |

---

## 10. Swing UIs

SwingBuilder provides builder-style DSL for creating Swing UIs. Documentation for Groovy 2.4.21 Swing is minimal (the official page was a placeholder). SwingBuilder supports:
- Declarative UI construction with closures
- Event binding
- Layout managers
- Data binding via `@Bindable`

Basic pattern:
```groovy
import groovy.swing.SwingBuilder
import java.awt.BorderLayout

new SwingBuilder().edt {
    frame(title: 'My App', size: [300, 200], show: true, defaultCloseOperation: javax.swing.WindowConstants.EXIT_ON_CLOSE) {
        borderLayout()
        label(text: 'Hello World', constraints: BorderLayout.CENTER)
        button(text: 'Click Me', constraints: BorderLayout.SOUTH, actionPerformed: { println 'Clicked!' })
    }
}
```
