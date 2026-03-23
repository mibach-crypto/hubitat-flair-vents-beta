# Groovy 2.4.21 Tooling & Build Knowledge Base

## 1. Running Groovy — The `groovy` Command

### Basic Usage
```bash
groovy MyScript.groovy     # .groovy extension is optional
groovy MyScript
```

### Command-Line Flags and Options

| Flag | Long Form | Description |
|------|-----------|-------------|
| `-v` | `--version` | Display Groovy and JVM versions |
| `-a` | `--autosplit <splitPattern>` | Split lines using specified regex pattern (default: `\s`); creates implicit `split` variable |
| `-b` | `--basescript <class>` | Set base class for scripts (must derive from `groovy.lang.Script`) |
| `-c` | `--encoding <charset>` | Specify file encoding (e.g., `utf-8`) |
| `-cp` | `--classpath <path>` | Specify compilation classpath. **Must be the first argument.** |
| `-D` | `--define <name=value>` | Define a system property |
| `-d` | `--debug` | Enable debug mode; prints full stack traces |
| `-e <script>` | — | Specify an inline command-line script (e.g., `groovy -e "println 'hello'"`) |
| `-h` | `--help` | Display usage information |
| `-i <extension>` | — | Modify files in-place; create backup with specified extension |
| `-l <port>` | — | Listen on port for processing inbound lines (default: 1960) |
| `-n` | — | Process files line-by-line using implicit `line` variable |
| `-p` | — | Process files line-by-line and print results |
| | `--disableopt <optlist>` | Disable optimizations; accepts comma-separated values (`all`, `int`) |
| | `--indy` | Enable invokedynamic support (requires Java 7+) |
| | `--configscript <path>` | Advanced compiler configuration script |

### Shebang Support (Unix)
```groovy
#!/usr/bin/env groovy
println "Hello from shebang script"
```
- The `#` character **must** be the first character of the file
- Any indentation before `#!` yields a compilation error
- Requires Groovy installed and `groovy` on the `PATH`

### Script Arguments
Arguments passed after the script name are available via the `args` variable (a `String[]` array).

### Inline Scripts with `-e`
```bash
groovy -e "println 'Hello World'"
groovy -e "println new Date()"
```

### Line Processing (`-n` and `-p` flags)
- `-n`: Iterates over each line of input, binding it to the `line` variable
- `-p`: Same as `-n` but also prints the result of each iteration
- `-a`: Used with `-n`/`-p` to auto-split lines into the `split` variable

### In-place Editing (`-i`)
```bash
groovy -i .bak -n -e "line.toUpperCase()" myfile.txt
# Creates myfile.txt.bak backup, replaces contents with uppercase
```

---

## 2. Compiling Groovy — `groovyc`

### Basic Usage
```bash
groovyc MyClass.groovy           # Generates .class files
groovyc -d target Person.groovy  # Output to target directory
```

### Command-Line Flags and Options

| Flag | Long Form | Description |
|------|-----------|-------------|
| `@argfile` | — | Load options/source file names from a file |
| `-cp` | `-classpath`, `--classpath` | Set compilation classpath (**must be first argument**) |
| `-d` | — | Specify output directory for class files |
| | `--encoding` | Define source file character encoding |
| | `--sourcepath` | Directory for source file discovery (**deprecated/non-functional**) |
| | `--temp` | Temporary directory for compiler operations |
| | `--configscript` | Advanced compiler configuration file |
| `-j` | `--jointCompilation` | Enable Java-Groovy mixed (joint) compilation |
| `-indy` | `--indy` | Enable invokedynamic (requires Java 7+) |
| `-b` | `--basescript` | Set Script base class (must extend `Script`) |
| `-v` | `--version` | Display compiler version |
| `-e` | `--exception` | Show full stack traces on errors |
| | `--help` | Display command help documentation |

### Java Compiler Pass-Through (Joint Compilation Only)

| Flag | Description | Example |
|------|-------------|---------|
| `-Jproperty=value` | Pass properties to javac | `groovyc -j -Jtarget=1.6 -Jsource=1.6 A.groovy B.java` |
| `-Fflag` | Pass flags to javac | `groovyc -j -Fnowarn A.groovy B.java` |

### Config Script Usage
```bash
groovyc --configscript src/conf/config.groovy src/main/groovy/MyClass.groovy
```

Config script example (`config.groovy`):
```groovy
withConfig(configuration) {
   ast(groovy.transform.CompileStatic)
}
```
The `configuration` variable (a `CompilerConfiguration` instance) and the `withConfig` builder are automatically available — no imports needed.

---

## 3. Joint Compilation

### Process Flow
1. Parse Groovy source files
2. Generate Java **stubs** for all Groovy sources (placeholder class definitions)
3. Invoke the **Java compiler** on stubs alongside real Java source files
4. Complete **normal Groovy compilation** to bytecode

### Key Points
- Without `-j`, Java sources processed by groovyc are compiled **as if they were Groovy** — this can produce unexpected semantic differences despite syntactic compatibility
- Stubs allow javac to resolve references to Groovy classes
- Enable via:
  - Command-line: `-j` flag
  - Ant: nested `<javac>` element inside `<groovyc>`
  - Maven: handled automatically by plugins
  - Gradle: handled automatically by Groovy plugin

### Stub Generation
- groovyc generates Java source stubs representing the structure of Groovy classes
- Stubs include class/method signatures but not implementation
- Controlled by `stubdir` (output directory) and `keepStubs` (preserve for debugging) attributes in the Ant task

---

## 4. The `<groovyc>` Ant Task

### Required Taskdef
```xml
<taskdef name="groovyc"
         classname="org.codehaus.groovy.ant.Groovyc"
         classpathref="my.classpath"/>
```

### Attributes

| Attribute | Description | Default |
|-----------|-------------|---------|
| `srcdir` | Source directory for Groovy/Java files | **Required** |
| `destdir` | Output directory for compiled class files | **Required** |
| `classpath` | Compilation classpath | — |
| `classpathref` | Classpath as a path reference | — |
| `sourcepath` | Source file location path | — |
| `sourcepathref` | Source path reference | — |
| `encoding` | Source file character encoding | — |
| `verbose` | Enable compiler verbosity | `false` |
| `failonerror` | Fail build on compilation errors | `true` |
| `listfiles` | Display source files being compiled | `false` |
| `stacktrace` | Include stack traces in error messages | `false` |
| `includeAntRuntime` | Include Ant runtime libraries | `yes` |
| `includeJavaRuntime` | Include VM runtime libraries | `no` |
| `fork` | Execute in a separate JVM | `no` |
| `memoryInitialSize` | Initial VM memory (e.g., `"83886080"`, `"81920k"`, `"80m"`) | — |
| `memoryMaximumSize` | Maximum VM memory (same format options) | — |
| `scriptBaseClass` | Base class for Groovy scripts (must extend `Script`) | — |
| `stubdir` | Directory for Java source stub generation | — |
| `keepStubs` | Preserve stub files after compilation (useful for debugging) | `false` |
| `forceLookupUnnamedFiles` | Search classpath for source files | `false` |
| `indy` | Enable invokedynamic support | — |
| `configscript` | Compiler configuration script | — |

### Nested Elements
- **`<src>`** — Path structure for source files (alternative to `srcdir`)
- **`<classpath>`** — Path structure for compilation classpath
- **`<javac>`** — Nested Java compiler configuration for joint compilation; inherits `srcdir`, `destdir`, and `classpath` from enclosing `<groovyc>`

### Joint Compilation Example
```xml
<groovyc srcdir="${srcdir}" destdir="${destdir}">
  <classpath>
    <pathelement path="${jardir}/dep.jar"/>
  </classpath>
  <javac source="1.7" target="1.7" debug="on" />
</groovyc>
```

---

## 5. The `<groovy>` Ant Task

### Required Taskdef
```xml
<taskdef name="groovy"
         classname="org.codehaus.groovy.ant.Groovy"
         classpathref="my.classpath"/>
```

### Attributes

| Attribute | Description | Required |
|-----------|-------------|----------|
| `src` | File containing Groovy statements; adds file's directory to classpath | Yes (unless inline code provided) |
| `classpath` | Specifies the classpath | No |
| `classpathref` | References a PATH defined elsewhere | No |
| `output` | Directs output to a file instead of the Ant log | No |
| `fork` | Executes script in a separate JVM | No (default: disabled) |
| `scriptBaseClass` | Defines the base class for scripts | No |
| `indy` | Enables invokedynamic execution | No (default: disabled) |

### Nested Elements
- **`<classpath>`** — PATH-like structure for classpath
- **`<arg>`** — Command-line arguments following standard Ant conventions

### Available Bindings
Scripts executed via the `<groovy>` Ant task have these objects in scope:

| Name | Description |
|------|-------------|
| `ant` | An `AntBuilder` instance aware of the current project |
| `args` | Command-line arguments array |
| `project` | Current Ant project object |
| `properties` | Map of Ant properties |
| `target` | Owning target that invoked the script |
| `task` | Wrapping task instance (`org.apache.tools.ant.Task`) |

### Inline Usage Example
```xml
<groovy>
  println "Hello from Ant!"
  ant.echo(message: "Ant echo from Groovy")
  println "Project base: ${project.baseDir}"
</groovy>
```

### External Script Example
```xml
<groovy src="scripts/build-helper.groovy">
  <classpath>
    <pathelement path="lib/utils.jar"/>
  </classpath>
  <arg value="--verbose"/>
</groovy>
```

---

## 6. Scripting Ant Tasks with AntBuilder

### Creating an Instance
```groovy
def ant = new AntBuilder()
```

### Usage Pattern
AntBuilder exposes Ant tasks as method calls with named parameters:
```groovy
ant.echo(message: "Hello from AntBuilder")
ant.mkdir(dir: "build/output")
ant.copy(todir: "build/output") {
    fileset(dir: "src") {
        include(name: "**/*.groovy")
    }
}
```

### FileScanner
```groovy
def scanner = ant.fileScanner {
    fileset(dir: "src/test") {
        include(name: "**/Ant*.groovy")
    }
}
for (f in scanner) {
    println "Found file: $f"
}
```

### Common Tasks Available
- `echo` — Output messages
- `mkdir` — Create directories
- `copy` — Copy file sets
- `zip` — Create archives
- `sequential` — Execute grouped tasks
- `fileset` / `include` — Define file patterns
- `junit` — Execute tests
- `javac` — Compile Java code
- `java` — Run Java applications

### Integration Notes
- AntBuilder is included in Gradle (available as `ant` in build scripts)
- Access underlying Ant project via `ant.project.baseDir`
- Ant jar files must be on the classpath for direct usage

---

## 7. Groovysh — The Groovy Shell

### Launching
```bash
groovysh
```

### Startup Options

| Flag | Description |
|------|-------------|
| `-C, --color[=FLAG]` | Control ANSI color usage |
| `-D, --define=NAME=VALUE` | Define system properties |
| `-T, --terminal=TYPE` | Specify terminal type (`none`, `unix`, `win`, `auto`) |
| `-V, --version` | Display version information |
| `-classpath, -cp` | Specify classpath for class file locations |
| `-d, --debug` | Enable debugging output |
| `-e, --evaluate=arg` | Evaluate code before entering interactive mode |
| `-h, --help` | Show help message |
| `-q, --quiet` | Suppress unnecessary output |
| `-v, --verbose` | Enable verbose output |

### Recognized Commands

| Command | Shortcut | Description |
|---------|----------|-------------|
| `help` | `:h` | Display command list or specific help |
| `exit` | `:x` | Terminate the shell (the only proper exit method) |
| `import` | `:i` | Add custom import statements |
| `display` | `:d` | Show incomplete buffer contents |
| `clear` | `:c` | Reset buffer and prompt counter |
| `show` | `:S` | Display variables, classes, imports, or preferences |
| `inspect` | `:n` | Open GUI object browser for inspection |
| `purge` | `:p` | Remove variables, classes, imports, or preferences |
| `edit` | `:e` | Edit buffer in external editor |
| `load` | `:l` or `.` | Load files or URLs into buffer |
| `save` | `:s` | Write buffer contents to file |
| `record` | `:r` | Record session to file |
| `history` | `:H` | Manage and recall command history |
| `alias` | `:a` | Create custom command aliases |
| `set` | `:=` | Configure preferences |
| `register` | `:rc` | Register custom commands |
| `doc` | `:D` | Open browser with documentation for a class |

### Preferences

| Preference | Description | Default |
|-----------|-------------|---------|
| `interpreterMode` | Allow typed variables (`def`) to persist across evaluations | Disabled |
| `verbosity` | Control output level (`DEBUG`, `VERBOSE`, `INFO`, `QUIET`) | `INFO` |
| `colors` | Enable/disable ANSI colors | `true` |
| `show-last-result` | Display execution results | `true` |
| `sanitize-stack-trace` | Filter stack traces to remove internal frames | `true` |
| `editor` | External editor application | `$EDITOR` env variable |

Setting preferences:
```
groovy:000> :set interpreterMode true
groovy:000> :set verbosity DEBUG
```

### Expression Evaluation
- **Untyped variables** (no `def`): persist in shell environment across evaluations
- **Typed variables** (`def x = ...`): local to current evaluation (unless `interpreterMode` is enabled)
- **Multi-line support**: closures and class definitions can span multiple lines; the shell detects completion
- **Result storage**: evaluation results are automatically stored in the `_` variable

### Profile Scripts
Located in `$HOME/.groovy/`:

| File | Purpose |
|------|---------|
| `groovysh.profile` | Executed at shell startup |
| `groovysh.rc` | Executed when entering interactive mode |
| `groovysh.history` | Stores command history |

### Custom Commands
- Extend `org.codehaus.groovy.tools.shell.CommandSupport`
- Register using `:register ClassName`
- The command class must be accessible on the classpath

### Troubleshooting

| Problem | Solution |
|---------|----------|
| JLine DLL issues (Windows) | Use `--terminal=none` to disable enhanced input features |
| Cygwin problems | Use `--terminal=unix` after executing terminal configuration commands |

### Integration Plugins
- **GMavenPlus Maven Plugin**: Enables groovysh integration with Maven builds
- **Gradle Groovysh Plugin**: Provides Groovy shell support within Gradle projects

---

## 8. Groovy Console (GUI)

### Launching
```bash
groovyConsole
# or
groovyConsole.bat   # Windows, from $GROOVY_HOME/bin
```

### Keyboard Shortcuts

| Action | Shortcut |
|--------|----------|
| Run script | `Ctrl+Enter` or `Ctrl+R` |
| Open file | `Ctrl+O` |
| Save file | `Ctrl+S` |
| New document | `Ctrl+Q` |
| Next history item | `Ctrl+N` |
| Previous history item | `Ctrl+P` |

### Features
- **Dual-pane interface**: input area (top) and output area (bottom)
- **Partial execution**: highlight specific text and run only that portion
- **Result binding**: scripts return the final expression's value
- **System output**: toggle System.out capture via the Actions menu
- **History**: maintains 10 previous script executions
- **Inspect Last**: from Actions menu, opens GUI inspector for results (great for lists/maps)
- **Script interruption**: interrupt button activates during execution; enable "Script > Allow interruption" to apply `@ThreadInterrupt` AST transformation
- **Font sizing**: via Actions menu
- **Auto-indentation**: on carriage return
- **Drag-and-drop**: file opening
- **Classpath modification**: via Script menu for JAR/directory additions
- **Error hyperlinking**: for compilation issues and exceptions

### Bound Variables
| Variable | Description |
|----------|-------------|
| `_` | Last non-null result |
| `__` | List of all historical results, indexed backward |

### Custom Output Visualization
Configure via `View > Visualize Script Results`. Built-in support for:
- `java.awt.Image`
- `javax.swing.Icon`
- Parent-less `java.awt.Component` objects

Custom transforms defined in `~/.groovy/OutputTransforms.groovy`:
```groovy
import javax.swing.*

transforms << { result ->
    if (result instanceof Map) {
        def table = new JTable(
            result.collect { k, v -> [k, v] as Object[] } as Object[][],
            ['Key', 'Value'] as Object[])
        new JScrollPane(table)
    }
}
```

### AST Browser
- Available from the console menu
- Visualizes the Abstract Syntax Tree of the current script
- Essential for developing AST transformations

### Embedding in Applications
```java
Console console = new Console();
console.setVariable("myVar", myObject);
console.run();
```

### Applet Support
Deploy as applet via `groovy.ui.ConsoleApplet`.

---

## 9. Groovydoc

### CLI Tool
The `groovydoc` command generates documentation from Groovy and Java source files, producing HTML output similar to JavaDoc.

### Groovydoc Ant Task

#### Required Taskdef
```xml
<taskdef name="groovydoc"
         classname="org.codehaus.groovy.tools.groovydoc.GroovyDocTool"
         classpathref="my.classpath"/>
```

#### Attributes

| Attribute | Description |
|-----------|-------------|
| `destdir` | Output directory for generated documentation |
| `sourcepath` | Location of source files to process |
| `packagenames` | Specific packages to document |
| `use` | Generate "Use" pages (where elements appear) |
| `windowtitle` | Browser window title |
| `doctitle` | Documentation main title |
| `header` | Page header text |
| `footer` | Page footer text |
| `overview` | Overview page file reference |
| `private` | Include private members |
| `protected` | Include protected members |
| `public` | Include public members only |
| `author` | Include author information |
| `processScripts` | Handle Groovy scripts in documentation |
| `includeMainForScripts` | Include main method details for scripts |
| `charset` | Character encoding |
| `fileEncoding` | Source file encoding |
| `overviewFile` | External overview content file |
| `styleSheetFile` | Custom CSS stylesheet |
| `noTimestamp` | Omit generation timestamp |
| `noVersionStamp` | Omit version information |

#### Nested Elements
- **`<link>`** — Cross-reference external documentation (like javadoc `-link`)

#### Example
```xml
<groovydoc destdir="${docsdir}/api"
           sourcepath="${srcdir}"
           packagenames="**.*"
           use="true"
           windowtitle="My Groovy API"
           doctitle="My Groovy API">
    <link packages="java.,org.xml."
          href="https://docs.oracle.com/javase/8/docs/api/"/>
</groovydoc>
```

### Custom Templates
- Extend the base groovydoc functionality by implementing custom documentation classes
- Apply custom templates for personalized documentation output
- Create custom groovydoc tasks for specialized generation

### Maven Integration
- **GMavenPlus Plugin** provides a `groovydoc` goal for Maven builds

---

## 10. Dependency Management with Grape

### Overview
Grape = "Groovy Adaptable/Advanced Packaging Engine." Leverages Apache Ivy for repository-driven module system, enabling runtime JAR downloading and linking.

### @Grab Annotation

#### Basic Syntax
```groovy
@Grab(group='org.springframework', module='spring-orm', version='3.2.5.RELEASE')
import org.springframework.orm.hibernate3.HibernateTemplate
```

#### Shorthand Notation
```groovy
@Grab('org.springframework:spring-orm:3.2.5.RELEASE')
```

#### Parameters

| Parameter | Aliases | Description |
|-----------|---------|-------------|
| `group` | `groupId`, `organisation`, `organization`, `org` | Module group / Maven groupId |
| `module` | `artifactId`, `artifact` | Module name / Maven artifactId |
| `version` | `revision`, `rev` | Version number or Ivy range (e.g., `[2.2.1,)`) |
| `classifier` | — | Maven classifier (e.g., `jdk15`) |
| `transitive` | — | Whether to resolve transitive dependencies (default: `true`) |

#### Version Ranges
- `[2.2.1,)` — version 2.2.1 or higher
- `1.1-RC3` — exact literal version

### @GrabResolver Annotation
Specifies additional Maven/Ivy repositories:
```groovy
@GrabResolver(name='restlet', root='http://maven.restlet.org/')
@Grab(group='org.restlet', module='org.restlet', version='1.1.6')
```

### @GrabExclude Annotation
Excludes transitive dependencies:
```groovy
@Grab('net.sourceforge.htmlunit:htmlunit:2.8')
@GrabExclude('xml-apis:xml-apis')
```

### @GrabConfig Annotation
Configures Grape behavior:
```groovy
@GrabConfig(systemClassLoader=true)
@Grab(group='mysql', module='mysql-connector-java', version='5.1.6')
```

| Parameter | Description |
|-----------|-------------|
| `systemClassLoader` | Attach to system classloader (required for JDBC drivers) |
| `initContextClassLoader` | Initialize context classloader |
| `autoDownload` | Automatic download management |
| `disableChecksums` | Disable checksum validation |

### @Grapes Annotation (Multiple Grabs)
```groovy
@Grapes([
   @Grab(group='commons-primitives', module='commons-primitives', version='1.0'),
   @Grab(group='org.ccil.cowan.tagsoup', module='tagsoup', version='0.9.7')
])
```

### Grape.grab() Method Call

#### Basic Usage
```groovy
groovy.grape.Grape.grab(group:'org.springframework', module:'spring', version:'2.5.6')
```

#### Multiple Dependencies
```groovy
Grape.grab([group:'com.jidesoft', module:'jide-oss', version:'[2.2.0,)'])
Grape.grab([group:'org.apache.ivy', module:'ivy', version:'2.0.0-beta1',
     conf:['default', 'optional']],
     [group:'org.apache.ant', module:'ant', version:'1.7.0'])
```

#### Dependency Parameters (per-dependency map)

| Parameter | Aliases | Description | Default |
|-----------|---------|-------------|---------|
| `group` | `groupId`, `organisation`, `organization`, `org` | Module group | — |
| `module` | `artifactId`, `artifact` | Module name | — |
| `version` | `revision`, `rev` | Version string or range | — |
| `classifier` | — | Maven classifier | — |
| `conf` | `scope`, `configuration` | Configuration/scope | `default` (maps to Maven runtime/master) |
| `force` | — | Enforce revision in conflicts | `true` |
| `changing` | — | Whether version content can change | `false` |
| `transitive` | — | Resolve transitive dependencies | `true` |

#### Arguments Map Parameters (first argument to grab)

| Parameter | Description | Default |
|-----------|-------------|---------|
| `classLoader` | Target `GroovyClassLoader` or `RootClassLoader` | — |
| `refObject` | Object whose parent classloader serves as target | — |
| `validate` | Validate POMs/Ivy files | `false` |
| `noExceptions` | Silently fail vs. throwing exceptions | `false` |

**Notes:**
- Multiple calls with identical parameters are idempotent
- Requires `RootLoader` or `GroovyClassLoader` in classloader chain

### Grape Command-Line Tool

| Command | Syntax | Description |
|---------|--------|-------------|
| `install` | `grape install <groupId> <artifactId> [<version>]` | Install module; uses newest version if omitted |
| `list` | `grape list` | Display locally cached modules with versions |
| `resolve` | `grape resolve (<groupId> <artifactId> <version>)+` | Return JAR file locations for artifacts and transitive dependencies |

#### Resolve Output Flags
- `-ant` — Ant format
- `-dos` — DOS/Windows path format
- `-shell` — Unix shell format
- `-ivy` — Ivy format

### Repository and Cache Configuration

#### Custom Repository Directory
```bash
groovy -Dgrape.root=/repo/grapes yourscript.groovy
```
Default location: `~/.groovy/grapes`

#### Custom Ivy Settings
Create `~/.groovy/grapeConfig.xml` to customize Ivy settings. Reference the default configuration at:
`https://github.com/apache/groovy/blob/master/src/resources/groovy/grape/defaultGrapeConfig.xml`

### Proxy Configuration
```bash
groovy -Dhttp.proxyHost=yourproxy -Dhttp.proxyPort=8080 yourscript.groovy
```
Or via environment variable:
```bash
JAVA_OPTS="-Dhttp.proxyHost=yourproxy -Dhttp.proxyPort=8080"
```

### Logging
```bash
# Report dependency resolution and downloads
groovy -Dgroovy.grape.report.downloads=true yourscript.groovy

# Increase Ivy verbosity (default level: -1)
groovy -Divy.message.logger.level=4 yourscript.groovy
```

### Reserved Group Pattern
Groups matching `/groovy[x][\..*]^/` are reserved for Groovy-endorsed modules.

### Practical Examples

#### Apache Commons
```groovy
import org.apache.commons.collections.primitives.ArrayIntList
@Grab(group='commons-primitives', module='commons-primitives', version='1.0')
def createEmptyInts() { new ArrayIntList() }
```

#### TagSoup HTML Parsing
```groovy
@Grab(group='org.ccil.cowan.tagsoup', module='tagsoup', version='1.2.1')
def getHtml() {
    def parser = new XmlParser(new org.ccil.cowan.tagsoup.Parser())
    parser.parse("https://docs.oracle.com/javase/specs/")
}
```

#### JDBC with systemClassLoader
```groovy
@GrabConfig(systemClassLoader=true)
@Grab('mysql:mysql-connector-java:5.1.6')
import groovy.sql.Sql
// Now JDBC driver is available
```

---

## 11. Gradle Integration

### Overview
Gradle is a build tool that combines Ant flexibility with Maven convention-over-configuration. Build files use Groovy DSL.

### Groovy Plugin
```groovy
apply plugin: 'groovy'
```

### Dependency Declaration
```groovy
dependencies {
    compile 'org.codehaus.groovy:groovy-all:2.4.21'
}
```

### Source Sets
Standard Gradle layout:
- `src/main/groovy/` — Main Groovy sources
- `src/test/groovy/` — Test Groovy sources
- Groovy plugin extends Java plugin, so `src/main/java/` also supported

### Joint Compilation
Gradle's Groovy plugin handles joint compilation automatically when both Groovy and Java sources exist. Place all sources in `src/main/groovy/` for joint compilation to work correctly (Java files can be in the Groovy source directory).

### InvokeDynamic
```groovy
compileGroovy {
    groovyOptions.optimizationOptions.indy = true
}
```

### Android Support
```groovy
buildscript {
    dependencies {
        classpath 'org.codehaus.groovy:groovy-android-gradle-plugin:1.0.0'
    }
}
apply plugin: 'groovyx.android'

dependencies {
    compile 'org.codehaus.groovy:groovy:2.4.8:grooid'
    compile ('org.codehaus.groovy:groovy-json:2.4.8') {
        transitive = false  // prevent non-grooid version inclusion
    }
}
```

**Key Android Notes:**
- Standard groovyc cannot target Android bytecode
- Use JARs with `grooid` classifier
- If a library lacks grooid classifier, it's already Android-compatible
- Set `transitive = false` to prevent non-grooid transitive dependency inclusion

---

## 12. Maven Integration

### Three Approaches

#### 1. GMavenPlus (Recommended Active Plugin)
The actively-maintained successor to GMaven. Supports:
- Recent Groovy versions
- InvokeDynamic
- Groovy-Android compilation
- GroovyDoc generation
- Configuration script support

**Limitation:** Uses stub-based joint compilation (same mechanism as groovyc)

#### 2. GMaven (Legacy — Not Recommended)
- Original Maven Groovy plugin
- **Not supported anymore**
- Has difficulties with joint compilation
- Do not use for new projects

#### 3. GMaven 2
- Despite the naming, does NOT replace GMaven
- Removes non-scripting features
- Inactive with no releases — avoid

#### 4. Groovy-Eclipse Maven Plugin (Best for Joint Compilation)
- Provides **stubless joint compilation**
- Enables seamless Java-Groovy mixing without traditional stub generation workarounds
- Uses Eclipse compiler infrastructure
- Best choice when complex Java-Groovy bidirectional references are needed

### Maven Ant Plugin Approach
Maven's Ant plugin can execute the Groovy compiler, bound to `compile` and `test-compile` phases. Compiled classes coexist with standard Java classes and appear identical to the JRE.

---

## 13. Gant

Gant is a tool for scripting Ant tasks using Groovy instead of XML. It provides a Groovy-based alternative to Ant build files while still leveraging Ant's task library.

---

## 14. IDE Integration

### IntelliJ IDEA
- Full Groovy support built-in (Community and Ultimate)
- Syntax highlighting, code completion, refactoring
- Groovy compilation and run configurations
- Debug support

### Eclipse — Groovy-Eclipse Plugin
- Provides Groovy support in Eclipse IDE
- Includes the Groovy-Eclipse compiler (also usable as Maven plugin)
- Syntax highlighting, code completion, refactoring

### NetBeans
- Groovy support available via plugins

### Text Editors
- IDE plugin installation is one method for obtaining Groovy development support

---

## 15. Compilation Customizers

### Overview
Compilation customizers modify compiler behavior through `CompilerConfiguration`. They are applied before bytecode generation and affect every class node being compiled.

### General Pattern
```groovy
import org.codehaus.groovy.control.CompilerConfiguration

def config = new CompilerConfiguration()
config.addCompilationCustomizers(/* customizers */)
def shell = new GroovyShell(config)
shell.evaluate(script)
```

### Import Customizer
Transparently adds imports — useful for DSLs:
```groovy
import org.codehaus.groovy.control.customizers.ImportCustomizer

def icz = new ImportCustomizer()
icz.addImports('java.util.concurrent.atomic.AtomicInteger')
icz.addImport('CHM', 'java.util.concurrent.ConcurrentHashMap')
icz.addStaticImport('java.lang.Math', 'PI')
icz.addStaticImport('pi', 'java.lang.Math', 'PI')
icz.addStarImports('java.util.concurrent')
icz.addStaticStars('java.lang.Math')
config.addCompilationCustomizers(icz)
```

Methods:
- `addImports(String...)` — regular class imports
- `addImport(alias, className)` — aliased imports
- `addStarImports(String...)` — wildcard imports
- `addStaticImport(className, memberName)` — static member imports
- `addStaticImport(alias, className, memberName)` — aliased static imports
- `addStaticStars(String...)` — static wildcard imports

### AST Transformation Customizer
Applies AST transformations transparently:
```groovy
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer
import groovy.util.logging.Log

def acz = new ASTTransformationCustomizer(Log)
config.addCompilationCustomizers(acz)

// With parameters:
def acz = new ASTTransformationCustomizer(Log, value: 'LOGGER')
```

For complex closure parameters, construct AST nodes:
```groovy
def expression = new AstBuilder().buildFromCode(CompilePhase.CONVERSION) { -> true }.expression[0]
def customizer = new ASTTransformationCustomizer(
    ConditionalInterrupt, value: expression, thrown: SecurityException)
```

### Secure AST Customizer
Restricts language grammar (AST-level only, not runtime security):
```groovy
import org.codehaus.groovy.control.customizers.SecureASTCustomizer
import static org.codehaus.groovy.syntax.Types.*

def scz = new SecureASTCustomizer()
scz.with {
    closuresAllowed = false
    methodDefinitionAllowed = false
    importsWhitelist = []
    staticImportsWhitelist = []
    staticStarImportsWhitelist = ['java.lang.Math']
    tokensWhitelist = [PLUS, MINUS, MULTIPLY, DIVIDE, MOD, POWER,
                       PLUS_PLUS, MINUS_MINUS,
                       COMPARE_EQUAL, COMPARE_NOT_EQUAL].asImmutable()
    constantTypesClassesWhiteList = [
        Integer, Float, Long, Double, BigDecimal,
        Integer.TYPE, Long.TYPE, Float.TYPE, Double.TYPE].asImmutable()
    receiversClassesWhiteList = [
        Math, Integer, Float, Double, Long, BigDecimal].asImmutable()
}
```

Properties:
- `closuresAllowed` / `methodDefinitionAllowed` — boolean
- `importsWhitelist` / `importsBlacklist`
- `staticImportsWhitelist` / `staticImportsBlacklist`
- `staticStarImportsWhitelist` / `staticStarImportsBlacklist`
- `tokensWhitelist` / `tokensBlacklist` — from `org.codehaus.groovy.syntax.Types`
- `constantTypesClassesWhiteList` / `constantTypesClassesBlackList`
- `receiversClassesWhiteList` / `receiversClassesBlackList`

Custom expression/statement checkers:
```groovy
def checker = { expr ->
    !(expr instanceof AttributeExpression)
} as SecureASTCustomizer.ExpressionChecker
scz.addExpressionCheckers(checker)
```

### Source Aware Customizer
Filters customizer application based on `SourceUnit` properties:
```groovy
import org.codehaus.groovy.control.customizers.SourceAwareCustomizer

def delegate = new ImportCustomizer()
def sac = new SourceAwareCustomizer(delegate)
sac.baseNameValidator = { baseName -> baseName.endsWith('Bean') }
sac.extensionValidator = { ext -> ext == 'spec' }
sac.sourceUnitValidator = { SourceUnit su -> su.AST.classes.size() == 1 }
sac.classValidator = { ClassNode cn -> cn.endsWith('Bean') }
config.addCompilationCustomizers(sac)
```

### Customizer Builder DSL
```groovy
import static org.codehaus.groovy.control.customizers.builder.CompilerCustomizationBuilder.withConfig

def conf = new CompilerConfiguration()
withConfig(conf) {
    imports {
        normal 'my.package.MyClass'
        alias 'AI', 'java.util.concurrent.atomic.AtomicInteger'
        star 'java.util.concurrent'
        staticMember 'java.lang.Math', 'PI'
        staticMember 'pi', 'java.lang.Math', 'PI'
    }
    ast(Log)
    ast(Log, value: 'LOGGER')
    ast(ToString)
    ast(EqualsAndHashCode)
    secureAst {
        closuresAllowed = false
        methodDefinitionAllowed = false
    }
    source(extension: 'sgroovy') {
        ast(CompileStatic)
    }
    source(extensions: ['sgroovy', 'sg']) {
        ast(CompileStatic)
    }
    source(basename: 'foo') {
        ast(CompileStatic)
    }
    source(basenames: ['foo', 'bar']) {
        ast(CompileStatic)
    }
    source(basenameValidator: { it in ['foo', 'bar'] }) {
        ast(CompileStatic)
    }
    source(unitValidator: { unit -> !unit.AST.classes.any { it.name == 'Baz' } }) {
        ast(CompileStatic)
    }
    inline(phase: 'CONVERSION') { source, context, classNode ->
        println "visiting $classNode"
    }
}
```

### Config Script Flag
Apply customizations via a Groovy script when using `groovyc`, Gradle, or Ant:

**config.groovy:**
```groovy
withConfig(configuration) {
    ast(groovy.transform.CompileStatic)
}
```

**Usage:**
```bash
groovyc -configscript src/conf/config.groovy src/main/groovy/MyClass.groovy
```

- The `configuration` variable (`CompilerConfiguration` instance) and `withConfig` builder are automatically available
- No imports needed in config scripts
- Enables "static compilation by default" for entire projects

---

## Quick Reference: Tool Comparison

| Tool | Purpose | Key Use Case |
|------|---------|--------------|
| `groovy` | Run scripts/programs | Development, scripting, one-liners |
| `groovyc` | Compile to bytecode | Production builds, JAR generation |
| `groovysh` | Interactive REPL | Exploration, testing, prototyping |
| `groovyConsole` | GUI REPL + AST browser | Script development, AST visualization |
| `groovydoc` | Generate documentation | API documentation |
| `grape` | Manage dependencies | CLI dependency installation/listing |
| `@Grab` | Declarative dependencies | Script-level dependency resolution |

## Quick Reference: Build Tool Comparison

| Build Tool | Joint Compilation | Stubless | Active | Notes |
|-----------|------------------|----------|--------|-------|
| Gradle (groovy plugin) | Yes (automatic) | No (stub-based) | Yes | Recommended for new projects |
| Maven (GMavenPlus) | Yes (stub-based) | No | Yes | Active successor to GMaven |
| Maven (Groovy-Eclipse) | Yes (stubless) | **Yes** | Yes | Best for complex Java-Groovy mixing |
| Maven (GMaven) | Problematic | No | **No** | Legacy, do not use |
| Ant (`<groovyc>`) | Yes (via `<javac>`) | No | Yes | For Ant-based projects |
| Gant | Via Ant tasks | No | Minimal | Groovy-based Ant scripting |
