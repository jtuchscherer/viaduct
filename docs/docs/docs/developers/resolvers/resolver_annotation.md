---
title: Resolver Annotation
description: Using the @Resolver annotation
---


Field resolvers must be annotated with `@Resolver` to be registered. This annotation class also allows resolvers to declare data dependencies in the form of *required selection sets* via `objectValueFragment` and `queryValueFragment`:

```kotlin
annotation class Resolver(
  @Language("GraphQL") val objectValueFragment: String = "",
  @Language("GraphQL") val queryValueFragment: String = "",
  val variables: Array<Variable> = []
)
```

**objectValueFragment**: a GraphQL fragment on the object type that contains the field being resolved. In the `User.displayName` example below, the fragment must be on the `User` type.

**queryValueFragment**: a GraphQL fragment on the root query type.

**variables**: values of variables used in `objectValueFragment` or `queryValueFragment`.

## Required selection set syntax

A resolver can optionally specify one or both of `objectValueFragment` and `queryValueFragment` using either the shorthand fragment syntax, or full fragment syntax. Values can be accessed using `Context.objectValue` and `Context.queryValue`.

### Shorthand syntax

The shorthand fragment syntax just includes the selections within the fragment body.

Here's an example of what this looks like for a `User.displayName` field. The selections must be on the `User` type:

```kotlin
@Resolver("firstName lastName")
class UserDisplayNameResolver : UserResolvers.DisplayName()
```
The shorthand fragment syntax can also be used for `queryValueFragment`. The selections must be on the root query type:

```kotlin
@Resolver(queryValueFragment = "user { firstName lastName }")
```

### Full fragment syntax

The full fragment syntax is the regular GraphQL fragment syntax. You can name the fragment whatever you'd like, although we typically use `_` for the fragment name when there's only a single fragment to indicate that the name isn't used anywhere:

```kotlin
@Resolver("fragment _ on User { firstName lastName }")
```

You can define multiple named fragments and reference them within your main fragment:
```kotlin
@Resolver(
  queryValueFragment = """
  fragment _ on Query {
    listing {
      cover: coverImage {
        ...ImageDetails
      }
      rooms {
        images {
          ...ImageDetails
        }
      }
    }
  }
  fragment ImageDetails on Image {
    url
    caption
  }
  """
)
```

Note that if you have multiple fragments on the type of the main fragment (either the object type or the query type), the primary one needs to be named `Main`:

```kotlin
@Resolver(
  """
  fragment Main on User {
    firstName
    lastName
    ...UserProfilePhoto
  }
  fragment UserProfilePhoto on User {
    profilePhoto {
      url
      caption
    }
  }
  """
)
```

## Accessing required selection set values
You can access the required selection set values via the [`Context` object](field_resolvers.md#context) given as input to the field resolver. `Context.objectValue` and `Context.queryValue` are [GRTs](../generated_code/index.md) of the object and Query types, e.g.

```kotlin
ctx.objectValue.getFirstName()
ctx.queryValue.getListing().getCoverImage(alias = "cover")
```

If the resolver tries to access a field not included within its required selection set, it results in an `UnsetFieldException` at runtime.

In the Kotlin API, each of the field getters are suspend functions. Your resolver may begin execution before the selections have been fully resolved via their corresponding resolvers. If that happens, the field getter will suspend until the field is resolved.

## Variables

The fragments in `@Resolver` annotations can contain variables. These variables can be bound to values in one of two ways:

1. Via the `variables` parameter in `@Resolver`
2. Via the resolver’s variable provider

#### @Resolver variables parameter

Variables may be bound using the `variables` parameter of `@Resolver`, which is an array of `@Variable` annotations. For example, consider this resolver configuration for the field `MyType.foo` that takes an argument `include`:

```kotlin
@Resolver(
  objectValueFragment = """
    fragment _ on MyType {
      field @include(if: ${'$'}shouldInclude)
    }
  """,
  variables = [Variable("shouldInclude", fromArgument = "includeMe")]
)
```

This resolver fragment uses a `shouldInclude` variable. At runtime, the value for this variable will be determined by the value of the `includeMe` argument to `MyType.foo`. To support nested GraphQL input types, the `fromArgument` string can contain a dot-separated path.

There are three mutually-exclusive parameters to the `@Variable` class that can be used to set the value of a variable:

1. the `fromArgument` parameter just illustrated
2. the `fromObjectField` parameter, which takes a dot-separated path relative to the `objectValue` of an execution. If used, the path must be a selection defined in the resolver's objectValueFragment.
3. the `fromQueryField` parameter. This parameter is analogous to `fromObjectField`, but the path describes a selection in the resolver's `queryValueFragment`.

#### VariablesProvider

The `variables` parameter does not allow arbitrarily-computed values to be used as variables. To support dynamic use cases, a {{ kdoc("viaduct.api.VariablesProvider") }} can be used.

For example, consider a resolver for `MyType.foo` whose required selection set uses variables named `startDate` and `endDate`. To provide dynamically-computed values for these variables, the implementation for `MyTypeResolvers.Foo` may nest a class that implements the `VariablesProvider` interface:

```kotlin
@Variables(types = "startDate: Date, endDate: Date")
class Vars : VariablesProvider<MyType_Foo_Arguments> {
    override suspend fun provide(args: MyType_Foo_Arguments) =
        LocalDate.now().let {
            mapOf(
                "startDate" to it,
                "endDate" to it.plusDays(7)
            )
        }
    }
}
```

The value of the `types` parameter to `@Variables` must conform to *VariableDefinitionlist* from [GraphQL Spec](https://spec.graphql.org/draft/#sec-Language.Variables). The `args` parameter to the `provide` function is the arguments of the field whose resolver class defines this variable provider, or `NoArguments` if the field takes no arguments.
