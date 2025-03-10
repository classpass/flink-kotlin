[ ![Download](https://api.bintray.com/packages/classpass-oss/maven/flink-kotlin/images/download.svg) ](https://bintray.com/classpass-oss/maven/flink-kotlin/_latestVersion)

# Overview

[Apache Flink](https://flink.apache.org/) is a platform for stateful stream computation for the JVM,
and [Kotlin](https://kotlinlang.org/) is a popular JVM language. This project tries to make using Flink with Kotlin more
delightful with helpers that allow using idiomatic Kotlin patterns with Flink's Java API.

As an example, suppose you have some keyed state in a `ProcessJoinFunction` implementation:

```kotlin
@Transient
private lateinit var someState: ValueState<Int>
```

Using the existing Java API, you can initialize this state by overriding `open()` set the `var`:

```
someState = runtimeContext.getState(ValueStateDescriptor("some-state", Int::class.java))
```

However, by using an [extension function](https://kotlinlang.org/docs/reference/extensions.html) that uses the inferred
type parameter `Int` as
a [reified generic type](https://kotlinlang.org/docs/reference/inline-functions.html#reified-type-parameters), there's
no need to specify any type parameters as the type of the `var` is sufficient:

```
someState = runtimeContext.getState("some-state")
```

Implementing simple callbacks where the callback type is an abstract class can also be
streamlined. `ProcessJoinFunction` only has one abstract method, but SAM construction for lambdas doesn't work for
abstract classes, only interfaces. So, if we're joining a stream of users with clicks, the normal way looks like this:

```kotlin
someStream.process(
    object : ProcessJoinFunction<User, Click, UserClick> {
        override fun processElement(left: User, right: Click, ctx: Context, out: Collector<UserClick>) {
            out.collect(UserClick(user, click))
        }
    }
)
```

And with a suitable helper function:

```kotlin
 someStream.process(
    processJoinFunction<User, Click, UserClick> { user, click, _, out ->
        out.collect(UserClick(user, click))
    }
)
```

We can't get rid of the type signature entirely (the input type parameters could hypothetically be inferred, but the output parameter cannot, at least in Kotlin's type system) but it's still a fair bit more compact.

If there are more helpers you'd like to see added, please file an issue or submit a PR!

## Lambdas, Kotlin pre-1.4, and `InvalidTypesException`

Prior to Kotlin 1.4, the bytecode generated for a Kotlin lambda like `.map { applySomeLogic(it) }` did not encode the
inferred generic type in the `Map<T, O>` implemented, so helper functions were needed for each callback
type (`MapFunction`, etc). This issue would lead to bytecode with a type signature of:

```
[generated lambda class name] implements org.apache.flink.api.common.functions.MapFunction<T, R>
```

At runtime, Flink would complain with an `InvalidTypesException`:

```
The return type of function 'keySelectorHelper$flink_core_kotlin(FlinkCoreExtensionsTest.kt:60)' could not be determined automatically, due to type erasure.
```

With Kotlin 1.4, however, it generates bytecode that incorporates the proper inferred types:

```
... implements org.apache.flink.api.common.functions.MapFunction<java.lang.String, java.lang.Integer> {
```

Since Kotlin 1.4 is stable now, this library doesn't include wrappers to generate the necessary `object` declarations to
work around this on 1.3.

# Usage

Artifacts are hosted in jcenter, available as the `jcenter()` repository in Gradle.

In your `build.gradle.kts`, add whichever of the core or streaming libraries are useful to you (`flink-core-kotlin`
depends on `flink-core`, while `flink-streaming-kotlin` depends on `flink-streaming`):

```
implementation("com.classpass.oss.flink.kotlin", "flink-core-kotlin", "LATEST-VERSION-HERE")
implementation("com.classpass.oss.flink.kotlin", "flink-streaming-kotlin", "LATEST-VERSION-HERE")
```

# Contributing

We welcome contributions from everyone! See [CONTRIBUTING.md](CONTRIBUTING.md) for information on making a contribution.

# Development

## Formatting

The `check` task will check formatting (in addition to the other normal checks), and `formatKotlin` will auto-format.

## License headers

The `check` task will ensure that license headers are properly applied, and `licenseFormat` will apply headers for you.

## Releasing a version

### Checking artifacts locally

To see the artifacts that would be released, build the relevant artifacts locally using a fake version `foo`:

```
./gradlew publishSonatypePublicationToLocalDebugRepository -Pversion=foo
tree */build/repos/localDebug
```

### Maven Central requirements

To publish to Maven Central, you'll need the `sonatypeUsername` and `sonatypePassword` Gradle properties set (`~/.gradle/gradle.properties` is typically where people put these). You'll also need GPG set up for the [signing plugin](https://docs.gradle.org/current/userguide/signing_plugin.html), as per Sonatype's [requirements](https://central.sonatype.org/publish/requirements/gpg/). All told, your properties should have:


```
sonatypeUsername = ...
sonatypePassword = ...

signing.keyId = ...
signing.password = ...
signing.secretKeyRingFile = ...
```

### Uploading a test version to Sonatype

Once you have that set up, you can try publishing with a test version:

```
./gradlew -Pversion=0.0 publishToSonatype
```

This will create and populate a staging repo in [Sonatype's s01 nexus instance](https://s01.oss.sonatype.org/#stagingRepositories). You can inspect the contents, and "Close" it, which runs validation for GPG signatures, etc. If closing reports validation errors, those must be addressed before releasing can work. Either way, "Drop" the staging repo once you're done playing with it.

### Releasing a real version

The [release plugin](https://github.com/researchgate/gradle-release) automates making appropriate commits, tags, etc. Run `./gradlew release` and follow the prompts to pick the released version and the next snapshot version.

Once that's done, go to [nexus](https://s01.oss.sonatype.org/#stagingRepositories) and "Close", then "Release" the staging repo you just uploaded. After 15-20 mins, the artifacts should be available in Maven Central.

# License

See [LICENSE](LICENSE) for the project license.
