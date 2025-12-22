<a href="[[https://bytebuddy.net]](https://github.com/pfichtner/vaadoo/)(https://github.com/pfichtner/vaadoo/)">
<img src="https://pfichtner.github.io/assets/vaadoo/vaadoo.png" alt="vaadoo logo" height="120px" align="right" />
</a>

[![Java CI with Maven](https://github.com/pfichtner/vaadoo/actions/workflows/build.yml/badge.svg)](https://github.com/pfichtner/vaadoo/actions/workflows/maven.yml)

# Vaadoo
Validating automatically domain objects: It's magic

## Getting Started

Vaadoo is available on **Maven Central**. The current release is an **alpha version (0.0.1-alpha-2)**:

### Maven Build Plugin Configuration

```xml
<dependency>
    <groupId>io.github.pfichtner</groupId>
    <artifactId>vaadoo-bytebuddy</artifactId>
    <version>0.0.1-alpha-2</version>
</dependency>
```

To enable build-time class transformation using Byte Buddy, add the following to your `build/plugins` section:

```xml
<plugin>
    <groupId>net.bytebuddy</groupId>
    <artifactId>byte-buddy-maven-plugin</artifactId>
    <version>1.17.8</version>
    <executions>
        <execution>
            <goals>
                <goal>transform-extended</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <incremental>true</incremental>
        <classPathDiscovery>true</classPathDiscovery>
    </configuration>
</plugin>
```

### Gradle Build Plugin Configuration

```groovy
implementation 'io.github.pfichtner:vaadoo-bytebuddy:0.0.1-alpha-2'

plugins {
    id 'java'
    id 'net.bytebuddy.byte-buddy-gradle-plugin' version '1.17.8'
}

byteBuddy {
    transformation {
        plugin = net.bytebuddy.build.Plugin.ForElementMatchers.SubType.of(Object) // adjust as needed
    }
}

tasks.withType(JavaCompile).configureEach {
    options.compilerArgs += ['-parameters']
}
```

## Integration
build on top of https://github.com/raphw/byte-buddy/tree/master/byte-buddy-maven-plugin so integration is documented here: https://github.com/raphw/byte-buddy/blob/master/byte-buddy-maven-plugin/README.md
- integrates in javac (maven/gradle/...)
- integrates in eclipse
- integrates in intellij but seems to need some tweaks https://youtrack.jetbrains.com/issue/IDEA-199681/Detect-and-apply-byte-buddy-plugin


## Why Vaadoo?

When building applications with Spring, JSR 380 (Bean Validation) annotations are handy. But where should you put them?

- **In the domain objects?**:
  - Adding annotations directly to domain objects makes them dependent on a JSR 380 implementation for validation.
  - Who ensures validation is executed?

- **In the DTOs separated from domain objects?**:
  - Adding annotations to DTOs pushes validation outside the domain. 
  - Using DTO-level annotations is acceptable, but if they are the only enforcement of business rules, it violates the DDD principle of “make illegal states unrepresentable,” since domain invariants must be enforced inside the domain model itself to preserve domain integrity.


Manual validation in constructors quickly becomes messy, is error-prone and hard to maintain:

```java
class MyDomainObject {
    private final String name;
    private final int age;

    MyDomainObject(String name, int age) {
        if (name == null) throw new NullPointerException("name must not be null");
        if (name.isEmpty()) throw new IllegalArgumentException("name must not be empty");
        if (age < 0) throw new IllegalArgumentException("age must be greater than or equal to 0");
        this.name = name;
        this.age = age;
    }
}
```

Vaadoo solves this by generating validation code at compile time. The checks Vaadoo adds match exactly the ones you would have written manually, but without the boilerplate or risk of mistakes. The checks are woven directly into the bytecode, and all JSR 380 runtime dependencies gets eliminated. Your domain objects become fully self-validating and safe. 

**Plain java class**
```java
class MyDomainObject {
    private final String name;
    private final int age;

    MyDomainObject(@NotEmpty String name, @Min(0) int age) {
        this.name = name;
        this.age = age;
    }
}
```

**Java record**
```java
record MyDomainObject(@NotEmpty String name, @Min(0) int age) {}
```

**With lombok** ([with adjustments of lombok.config](https://github.com/pfichtner/vaadoo/blob/main/vaadoo-bytebuddy-tests/vaadoo-bytebuddy-tests-vaadoo/lombok.config))
```java
@lombok.Value class MyDomainObject {
    @NotEmpty String name;
    @Min(0) int age;
}
```

## Why are only constructors supported? Please add support for methods as well! 

The intention is to support creating domain classes (value types/entities) and get rid of boilerplate code there. 
You don't want to have methods like ...
```java
void sendMail(String from, String to, String subject, String body) {}
```

... but domain classes MailAddress, Subject and Text. Vaadoo helps you to add validation in a declarative way, so you get: 
```java
record MailAddress(@Email String value) {}
record Subject(@NotBlank @Max(256) String value) {}
record Text(@Max(4 * 1024) String value) {}
[...]
void send(MailAddress from, MailAddress to, Subject subject, Text body) {}
```

If vaadoo would support validation on methods we'd still write code like this
```java
void sendMail(@Email String value, @NotBlank @Max(256) String value, @Max(4 * 1024) String value) {}
```

This is not what vaadoo was thought for! 

## Configuration (vaadoo.config)
Vaadoo can be configured using a file named **`vaadoo.config`** in the project's root directory.

### You can configure:
- **Which bytecode implementation should be weaved in**
  - e.g. *plain-java* (default)
  - or an implementation using Google Guava's `Preconditions`
  - or any other compatible class

- **Which classes (types) should be enhanced** by the plugin (default: all)

- **Custom annotation handling**
  Determines whether JSR 380 custom validators should be considered and enabled during bytecode weaving. (default: [see Default behavior](#default-behavior))

- **Regex optimization**
  Enables caching of compiled regular expressions, so Pattern.compile is called only once per regex. (default: true)

- **Removing JSR 380 annotations**
  If set, the original JSR 380 annotations are removed after the code has been weaved in. (default: true)


### Default behavior
1. If a `vaadoo.config` exists → its configuration is used.
2. If no config is found and **jmolecules** is on the classpath → jmolecules value objects and records are enhanced.
3. If neither applies → **all classes** are enhanced (default fallback).


## Drawbacks
- no runtime internationalization (i18n) since messages are copied during compile-time into the bytecode
- no central point to change validation logic, e.g. if the regexp for mail address validation changes the classes have to been recompiled
- increased class sizes since the code gets copied into each class instead of having a central point that contains the code

## Pitfalls
- if you switch from generated constructors, e.g. 
  ```java
  @lombok.RequiredArgsConstructor @lombok.Value class Foo {
  	@Min(1) @Max(9999) int bar;
  }
  ```
  to a handwritten one it's easy to get lost of the annotations copied to the constructor done by lombok
  ```java
  class Foo {
  	@Min(1) @Max(9999) private final int bar;
  	Foo(int bar) { this.bar = bar; }
  }
  ```
  When adding constructors via the IDE the IDE takes care of it: Foo(@Min(1) @Max(9999) int bar) { this.bar = bar; }

  Note: lombok copies the annotation of fields to existing constructors ([those who are configured as ```lombok.copyableAnnotations``` within lombok.config](https://github.com/pfichtner/vaadoo/blob/main/vaadoo-bytebuddy-tests/vaadoo-bytebuddy-tests-vaadoo/lombok.config)) as well, so here is less danger
  ```java
  @lombok.Value class Foo {
  	@Min(1) @Max(9999) int bar;
  	Foo(int bar) { this.bar = bar; }
  }
  ```

## Advantages
- No reflection, what and how to check will be decided during compile- not during runtime. 
- Faster (at least 3-4x and up to 10x faster than validation via reflection, depending on the validations included)
- Zero runtime dependency: When using the JdkOnlyCodeFragment, everything needed for validation is compiled directly into each class. No additional jars, libraries, or runtime setup is required. The bytecode added needs at least a Java 8 Runtime (JRE). 
- Fully self-contained: Once compiled, the domain objects are completely self-validating.
- Can be used in environments where reflection is hard or impossible (e.g. native images)
- Safe for environments with limited resources or restricted classloading.

## Performance
- **Handwritten vs Vaadoo-generated validation:**
  With the **exception of regex validations**, Vaadoo generates validation code that is **essentially identical to handwritten checks**, so execution speed is the same as if you wrote the validation yourself.

- **Reflection-based validation (e.g., Hibernate Validator) vs Vaadoo-generated validation:**
  Vaadoo is **much faster**, because it eliminates reflection entirely — typically **5–30x faster** depending on the validations.

- **Regex handling (special note):**
  - By default, Vaadoo compiles regex patterns on demand and caches them in a `Map`.
  - Accessing a regex via the cache is slightly slower than using a `private static final` field directly.
  - Thanks to this **regex optimization**, Vaadoo is **much faster than compiling the regex on every validation call**.
  - An added benefit is that **only the regex patterns that are actually used are compiled**, rather than all patterns being compiled during class loading.
  - For most use cases, this minor overhead is negligible.

## Other projects/approaches
- https://github.com/opensanca/service-validator
- https://yavi.ik.am/
- https://hibernate.org/validator/
- https://bval.apache.org/
