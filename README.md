<a href="[[https://bytebuddy.net]](https://github.com/pfichtner/vaadoo/)(https://github.com/pfichtner/vaadoo/)">
<img src="https://pfichtner.github.io/vaadoo/vaadoo.png" alt="vaadoo logo" height="120px" align="right" />
</a>

[![Java CI with Maven](https://github.com/pfichtner/vaadoo/actions/workflows/maven.yml/badge.svg)](https://github.com/pfichtner/vaadoo/actions/workflows/maven.yml)

# Vaadoo
Validating automatically domain objects: It's magic

## This is a "work in progress" PoC of weaving JSR 380 (Bean Validation 2.0) checks based on annotations into the bytecode. 
## Update 2024-10-01: Feature-complete, all JSR 380 annotations are supported now

## Why? 
When implementing an application using Spring it's very handy to use the JSR 380 annotations. But where to place them? 
- If the code does not have exlicitly DTOs but mapping it's domain objects directly, the annotations have to been placed on the domain objects but then your domain won't be able to validate the classes until it has some dependency to any JSR 380 implementation and Spring initiating the validation. 
- If your code differs between DTOs and domain objects, you have to options: 
  - Place the JSR 380 annotations on the DTO but then your internal valid state would rely on checks being done in a non-domain layer, so the domain is not able to valid its state itself. 
  - Again make your domain dependant on a JSR 380 implemenation. But then: Who would then ensure that validation is performed? 

So if you decide, that none of these possibilites is an option you **cannot** just declare things like this...

```java
public class MyDomainObject {
    private final String name;
    private final int age;
    public MyDomainObject(@NotEmpty String name, @Min(0) int age) {
        this.name = name;
        this.age = age;
    }
}
```

...but you would start implementing all those contraint checks using hand-written code into you domain objects to make them self-validating: 

```java
public class MyDomainObject {
    private final String name;
    private final int age;
    public MyDomainObject(String name, int age) {
        if (name == null { throw new NullPointerException("name must not be null"); }
        if (name.isEmpty() { throw new InvalidArgumentException("name must not be empty"); }
        if (age < 0 { throw new InvalidArgumentException("age must be greater than or equal to 0"); }
        this.name = name;
        this.age = age;
    }
}
```

Ough, what a mess and waste of time! 

And this is where vaadoo comes into play. Vaadoo is a compiler plugin that generates this boilerplate code for you. Checks are added to the bytecode so you get rid of a JSR 380 validation library. The generated code does nor depend on JSR 380 API- nor on JSR 380 validation libaries anymore. 

PS: This is getting real fun with lombok ([with adjustments of lombok.config](https://github.com/pfichtner/vaadoo/blob/main/vaadoo-tests/lombok.config)) and records! 
```java
@lombok.Value public class MyDomainObject {
    @NotEmpty String name;
    @Min(0) int age;
}
```

```java
public record MyDomainObject(@NotEmpty String name, @Min(0) int age) {}
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
  - Can be used in environments where reflection is hard or impossible (e.g. native images)
  - No dependencies to additional jars/libraries during runtime, everything is compiled into the classes

## Other projects/approaches
- https://github.com/opensanca/service-validator

