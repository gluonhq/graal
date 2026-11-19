---
layout: docs
title: Web Image API Guide
link_title: Web Image API Guide
permalink: /reference-manual/web-image/api-guide/
toc_group: web-image
---

# Working with the Web Image API

This document describes the recommended approach for using the Web Image API to enable JavaScript interoperability in JVM applications that are compiled into WebAssembly modules using the [GraalVM Native Image Web Image backend](get-started.md), and can run in Node.js or browser environments.

The [Web Image API](https://www.graalvm.org/sdk/javadoc/org/graalvm/webimage/api/package-summary.html) provides a JavaScript interoperability layer for JVM applications compiled to WebAssembly.

> Note: Web Image is experimental and under active development. APIs, tooling, and capabilities may change.

## Table of Contents

* [Embedding JavaScript Code with `@JS`](#embedding-javascript-code-with-js)
* [Working with Primitives](#working-with-primitives)
* [Working with Objects](#working-with-objects)
* [Creating JavaScript Objects from Application Code](#creating-javascript-objects-from-application-code)
* [Creating Instances of JavaScript Classes](#creating-instances-of-javascript-classes)
* [Functions Are Objects](#functions-are-objects)
* [Exporting Application Code to JavaScript](#exporting-application-code-to-javascript)
* [Passing Arguments](#passing-arguments)
* [What Counts as a Callable Value?](#what-counts-as-a-callable-value)
* [Error Handling](#error-handling)

## Embedding JavaScript Code with `@JS`

The central entry point is the `@JS` annotation, which allows a Java method to execute a JavaScript snippet instead of a Java method body.
For example:
```java
import org.graalvm.webimage.api.JS;

public class Example {
    @JS("console.log(message);")
    static native void log(String message);
}
```

The method is typically declared `native`, because its implementation is supplied by the JavaScript snippet rather than a Java method body.

## Working with Primitives

Primitive JavaScript values are represented by wrapper classes:

* `JSBoolean`
* `JSNumber`
* `JSBigInt`
* `JSString`
* `JSSymbol`
* `JSUndefined`

All of them extend `JSValue`.

For exchanging primitive values, there are two practical styles.

* The first style is to work with the explicit JavaScript wrapper types directly. In that mode, method signatures make it obvious that the values crossing the boundary are JavaScript values rather than ordinary Java values:

    ```java
    import org.graalvm.webimage.api.JS;
    import org.graalvm.webimage.api.JSNumber;

    public class Adder {
        @JS("return a + b;")
        static native JSNumber add(JSNumber a, JSNumber b);
    }
    ```

    This style is explicit and predictable when you want to stay close to JavaScript semantics, or when you are debugging conversion behavior.

* The second style is to use `@JS.Coerce`, which asks Web Image to convert between Java types and JavaScript types automatically:

    ```java
    import org.graalvm.webimage.api.JS;

    public class Adder {
        @JS.Coerce
        @JS("return a + b;")
        static native int add(int a, int b);
    }
    ```

    This is often the most convenient approach for `boolean`, the Java numeric types, `String`, and `BigInteger`.
    It produces signatures that look natural from Java, which makes small helper methods easier to read.
    If the conversion behavior is unclear, start with explicit wrapper types and add coercion later.

To conclude:

- use plain Java types together with `@JS.Coerce` for simple methods;
- use `JSNumber`, `JSString`, `JSBoolean`, and the other wrapper types when you want to be explicit about JavaScript values;
- use `JSValue` when the runtime type may vary and you need to inspect or convert it manually.

For example, if a value may be different kinds of JavaScript values depending on runtime behavior, you can accept `JSValue` and convert it explicitly:

```java
import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSValue;

public class Example {
    @JS("return value;")
    static native JSValue identity(JSValue value);

    static int use(JSValue value) {
        return value.asInt();
    }
}
```

If JavaScript returns a value that does not match the Java type you requested, Web Image throws `ClassCastException`.

The recommendation is also to use typed `get(...)` whenever possible:
```java
int price = request.get("price", Integer.class);
```

Instead of `Object raw = request.get("price");`.
Typed `get(...)` is clearer and gives you earlier failures if the data does not match what your application expects.

## Working with Objects

At present, **`JSObject`** is the primary way to work with JavaScript objects from JVM application code.
The current limitations are:

* `@JS.Import` is not implemented yet
* `@JS.Export` is not implemented yet
* subclasses of `JSObject` are not supported yet

The recommended object model today is intentionally simple: accept and return `JSObject`, manipulate properties through `get(...)` and `set(...)`, and use typed reads such as `get("name", String.class)` whenever possible.
For example, this pattern reads a "flat" object together with a nested object:

```java
import org.graalvm.webimage.api.JSObject;

String operation = request.get("operation", String.class);
int price = request.get("price", Integer.class);

JSObject user = request.get("user", JSObject.class);
boolean premium = user.get("premium", Boolean.class);
```

Writing object properties is slightly different: when you want JavaScript primitive values on the JavaScript side, pass explicit `JSValue` wrappers such as `JSNumber`, `JSBoolean`, or `JSString` to `set(...)`.
Start with an empty object and populate it field by field:

```java
JSObject response = JSObject.create();
response.set("finalPrice", JSNumber.of(96));
response.set("discountApplied", JSNumber.of(24));
response.set("premium", JSBoolean.of(true));
```

This ensures that the stored properties are JavaScript values instead of proxied JVM-side objects.
When returned back to JavaScript, this becomes a normal JavaScript object.

Nested objects are handled in the same way: read the nested property as another `JSObject`, then continue reading from it.

```java
JSObject user = request.get("user", JSObject.class);
String tier = user.get("tier", String.class);
```

## Creating JavaScript Objects from Application Code

The simplest way to create a plain JavaScript object is:

```java
JSObject obj = JSObject.create();
```

There are also additional `JSObject.create(...)` overloads and related helper methods such as:

* `JSObject.create()`
* `JSObject.create(proto)`
* `JSObject.create(proto, properties)`
* `JSObject.defineProperty(...)`
* `JSObject.defineProperties(...)`

These methods are useful when you want to construct objects in application code without writing a JavaScript helper.

Use these helpers for descriptor objects, plain record-like objects, temporary request or response objects.

## Creating Instances of JavaScript Classes

There is an important distinction between creating a plain JavaScript object and creating an instance of a specific JavaScript class.
Plain object creation works well with `JSObject.create(...)`.
However, if you need an actual instance of a JavaScript class, such as a browser object, or a custom constructor type, the most practical approach currently is to define a helper method:

```java
@JS("return new Foo(arg1, arg2);")
@JS.Coerce
static native JSObject newFoo(int arg1, String arg2);
```

In practice, create the instance in JavaScript and then handle it in application code as a `JSObject`.
The same guidance applies to browser and framework objects.
If you need something like a JavaScript `Date`, a DOM-like helper object, or an application-specific JavaScript instance, construct it in a small `@JS` helper and return `JSObject`.

## Functions Are Objects

A JavaScript function can be represented as a `JSObject`.
You can call it from application code using:

* `invoke(args...)`
* `call(thisArg, args...)`

For example:
```java
@JS("return (a, b) => a + b;")
static native JSObject createFunction();

JSObject fn = createFunction();
Object result = fn.invoke(1, 2);
```

This is useful when JavaScript returns callbacks or factories.
This is also useful when an API returns a function-valued property instead of a plain object.

## Exporting Application Code to JavaScript

Another common use of `@JS` is exposing application code to JavaScript.
The current practical pattern is to export an application function through a small bootstrap helper written with `@JS`.

Example pattern:
```java
@JS(args = {"adder"}, value = "globalThis.adder = adder;")
private static native void export(java.util.function.BiFunction<JSNumber, JSNumber, JSNumber> adder);
```

Then in `main()`:
```java
export((a, b) -> JSNumber.of(a.asInt() + b.asInt()));
```

Here, `@JS` is used to install an application-provided function onto the JavaScript side so that browser or Node.js code can call it later.

This is different from the simpler `@JS` example shown earlier, where `@JS` is used to run JavaScript from application code:
```java
public class Example {
    @JS("console.log(message);")
    static native void log(String message);
}
```

This is also different from `@JS.Export`.
Because `@JS.Export` is not implemented yet, this guide recommends the helper-based pattern instead.

## Passing Arguments

Arguments are visible inside the JavaScript body by parameter name.
Parameter names are only available if they are recorded in bytecode, for example by compiling with `javac -parameters`.

If parameter names are not available in bytecode, or if you want to make the Java-to-JavaScript binding explicit, provide them explicitly:

```java
@JS.Coerce
@JS(args = {"x", "y"}, value = "return x + y;")
static native int add(int a, int b);
```

## What Counts as a Callable Value?

JavaScript needs a callable value, and the practical JVM-side model is a functional interface.
That callable can be a lambda, a method reference, or an implementation of a functional interface.
Common choices include:

* `Runnable` for no arguments and no return value
* `Consumer<T>` for one argument and no return value
* `Function<T, R>` for one argument and a return value
* `BiFunction<T, U, R>` for two arguments and a return value

For example, these are all reasonable callable values to export:

```java
@JS(args = {"handler"}, value = "globalThis.pricingService = handler;")
private static native void export(Function<JSObject, JSObject> handler);

static JSObject handleRequest(JSObject request) {
    JSObject response = JSObject.create();
    response.set("ok", JSBoolean.of(true));
    return response;
}

static final class PricingHandler implements Function<JSObject, JSObject> {
    @Override
    public JSObject apply(JSObject request) {
        return handleRequest(request);
    }
}

export(request -> handleRequest(request));
export(Example::handleRequest);
export(new PricingHandler());
```

For structured request/response exchange, `Function<JSObject, JSObject>` is often the most convenient shape: JavaScript passes a plain object, the application reads it with typed `get(...)` calls, and returns a fresh `JSObject`.

## Error Handling

When JavaScript throws a non-Java exception value, it may be surfaced as `ThrownFromJavaScript`.
That gives application code access to the thrown object:

```java
try {
...
} catch (ThrownFromJavaScript ex) {
    Object thrown = ex.getThrownObject();
}
```

This is useful for APIs called directly from browser JavaScript.
A returned error object is often easier to inspect in the console or UI than a JVM-side exception crossing the boundary.

### Summary

For the current state of the Web Image API, prefer the following style:

* use `@JS` for small JavaScript glue snippets
* add `@JS.Coerce` when you want a Java-friendly signature
* use `JSObject` for all structured objects exchange
* use `get(...)` and `set(...)` instead of `JSObject` subclasses
* use helper methods that construct JavaScript class instances explicitly with `new`

Until `@JS.Import`, `@JS.Export`, and typed `JSObject` subclass support are fully implemented, the safest approach is to treat JavaScript objects as `JSObject` values and manipulate them through `get(...)`, `set(...)`, and small helper methods.

### Related Documentation

* [Web Image: Export Java Method with JavaScript Objects](https://github.com/graalvm/graalvm-demos/tree/master/web-image/js-java-object-exchange)
* [Web Image: Export Java Method Example](https://github.com/graalvm/graalvm-demos/tree/master/web-image/export-java-function)
* [Web Image API](https://www.graalvm.org/sdk/javadoc/org/graalvm/webimage/api/package-summary.html)
