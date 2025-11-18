<a href="[[https://bytebuddy.net]](https://github.com/pfichtner/vaadoo/)(https://github.com/pfichtner/vaadoo/)">
<img src="https://pfichtner.github.io/assets/vaadoo/vaadoo.png" alt="vaadoo logo" height="120px" align="right" />
</a>

[![Java CI with Maven](https://github.com/pfichtner/vaadoo/actions/workflows/build.yml/badge.svg)](https://github.com/pfichtner/vaadoo/actions/workflows/maven.yml)

# Vaadoo
Validating automatically domain objects: It's magic

## ⚠️ Important
This project is a reimplementation of an [initial proof of concept (PoC)](https://github.com/pfichtner/vaadoo-poc). It is not feature complete yet and still missing at least:
- Caching of the generated regexp (so its significant slower when using @Pattern(...))
- Support for container element validation like ```List<@NotBlank String> list```

Further development is ongoing.


## Why? 
When implementing an application using Spring it's very handy to use the JSR 380 annotations. But where to place them? 
- If the code does not have exlicitly DTOs but mapping it's domain objects directly, the annotations have to been placed on the domain objects but then your domain won't be able to validate the classes until it has some dependency to any JSR 380 implementation and Spring initiating the validation. 
- If your code differs between DTOs and domain objects, you have to options: 
  - Place the JSR 380 annotations on the DTO but then your internal valid state would rely on checks being done in a non-domain layer, so the domain is not able to valid its state itself. 
  - Again make your domain dependant on a JSR 380 implemenation. But then: Who would then ensure that validation is performed? 

If you decide, that none of these possibilites is an option you **cannot** just declare things like this...

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

...because the constructor does not validate itself, something external must perform validation after (or before) constructing the object.
That's why you would start implementing all those contraint checks using hand-written code into you domain objects to have them self-validating: 

```java
class MyDomainObject {
    private final String name;
    private final int age;
    MyDomainObject(String name, int age) {
        if (name == null { throw new NullPointerException("name must not be null"); }
        if (name.isEmpty() { throw new InvalidArgumentException("name must not be empty"); }
        if (age < 0 { throw new InvalidArgumentException("age must be greater than or equal to 0"); }
        this.name = name;
        this.age = age;
    }
}
```

Ough, what a mess and waste of time! Manual constructor checks are messy, error-prone, and hard to maintain—they repeat the same boilerplate, can be forgotten or mistyped, and mix validation with business logic.

And this is where Vaadoo comes into play. Vaadoo is a compiler plugin that generates all this boilerplate code for you. Checks are woven directly into the bytecode, so you get rid of any runtime JSR 380 dependency. Everything needed for validation is compiled directly into your classes—there are no runtime dependencies required, making your domain objects fully self-validating and ready to use immediately.

PS: This is getting real fun with lombok ([with adjustments of lombok.config](https://github.com/pfichtner/vaadoo/blob/main/vaadoo-tests/lombok.config)) and records! 
```java
@lombok.Value class MyDomainObject {
    @NotEmpty String name;
    @Min(0) int age;
}
```

```java
record MyDomainObject(@NotEmpty String name, @Min(0) int age) {}
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
  - e.g. *plain-java*
  - or an implementation using Google Guava's `Preconditions`
  - or any other compatible class

- **Which classes (types) should be enhanced** by the plugin

### Default behavior
1. If a `vaadoo.config` exists → its configuration is used.
2. If no config is found and **jmolecules** is on the classpath → jmolecules value objects and records are enhanced.
3. If neither applies → **all classes** are enhanced (default fallback).

## Integration
build on top of https://github.com/raphw/byte-buddy/tree/master/byte-buddy-maven-plugin so integration is documented here: https://github.com/raphw/byte-buddy/blob/master/byte-buddy-maven-plugin/README.md
- integrates in javac (maven/gradle/...)
- integrates in eclipse
- integrates in intellij but seems to need some tweaks https://youtrack.jetbrains.com/issue/IDEA-199681/Detect-and-apply-byte-buddy-plugin

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

  Note: lombok copies the annotation of fields to existing constructors as well, so here is less danger
  ```java
  @lombok.Value class Foo {
  	@Min(1) @Max(9999) int bar;
  	Foo(int bar) { this.bar = bar; }
  }
  ```

## Advantages
- No reflection, what and how to check will be decided during compile- not during runtime. 
- Faster (at least 3-4x and up to 10x faster than validation via reflection, depending on the validations included)
- Zero runtime dependency: When using the JdkOnlyCodeFragment, everything needed for validation is compiled directly into each class. No additional jars, libraries, or runtime setup is required.
- Fully self-contained: Once compiled, the domain objects are completely self-validating.
- Can be used in environments where reflection is hard or impossible (e.g. native images)
- Safe for environments with limited resources or restricted classloading.

## Other projects/approaches
- https://github.com/opensanca/service-validator
- https://yavi.ik.am/
- https://hibernate.org/validator/
- https://bval.apache.org/
