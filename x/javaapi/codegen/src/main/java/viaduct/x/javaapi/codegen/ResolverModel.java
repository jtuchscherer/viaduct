package viaduct.x.javaapi.codegen;

/**
 * Model representing a GraphQL field resolver for code generation.
 *
 * <p>Each resolver model corresponds to a field annotated with @resolver directive in the GraphQL
 * schema. The generated class will be an abstract base class that tenant developers extend.
 */
public record ResolverModel(
    String gqlTypeName,
    String gqlFieldName,
    String resolverClassName,
    String returnType,
    String objectType,
    String queryType,
    String argumentsType,
    String selectionsType,
    boolean hasArguments,
    boolean isCompositeOutput,
    boolean includeBatchResolve) {

  // ST (StringTemplate) requires JavaBean-style getters
  public String getGqlTypeName() {
    return gqlTypeName;
  }

  public String getGqlFieldName() {
    return gqlFieldName;
  }

  public String getResolverClassName() {
    return resolverClassName;
  }

  public String getReturnType() {
    return returnType;
  }

  public String getObjectType() {
    return objectType;
  }

  public String getQueryType() {
    return queryType;
  }

  public String getArgumentsType() {
    return argumentsType;
  }

  public String getSelectionsType() {
    return selectionsType;
  }

  public boolean getHasArguments() {
    return hasArguments;
  }

  public boolean getIsCompositeOutput() {
    return isCompositeOutput;
  }

  public boolean getIncludeBatchResolve() {
    return includeBatchResolve;
  }

  // Pre-formatted type strings for template use (avoids angle bracket escaping issues)

  /** Returns FieldResolverBase<returnType, objectType, queryType, argumentsType, selectionsType> */
  public String getFieldResolverBaseType() {
    return "FieldResolverBase<"
        + returnType
        + ", "
        + objectType
        + ", "
        + queryType
        + ", "
        + argumentsType
        + ", "
        + selectionsType
        + ">";
  }

  /** Returns FieldResolverBase.Context<objectType, queryType, argumentsType, selectionsType> */
  public String getContextBaseType() {
    return "FieldResolverBase.Context<"
        + objectType
        + ", "
        + queryType
        + ", "
        + argumentsType
        + ", "
        + selectionsType
        + ">";
  }

  /** Returns FieldExecutionContext<objectType, queryType, argumentsType, selectionsType> */
  public String getFieldExecutionContextType() {
    return "FieldExecutionContext<"
        + objectType
        + ", "
        + queryType
        + ", "
        + argumentsType
        + ", "
        + selectionsType
        + ">";
  }

  /** Returns CompletableFuture<returnType> */
  public String getResolveFutureType() {
    return "CompletableFuture<" + returnType + ">";
  }

  /** Returns CompletableFuture<Map<Context, returnType>> */
  public String getBatchResolveFutureType() {
    return "CompletableFuture<Map<Context, " + returnType + ">>";
  }

  /** Returns List<Context> */
  public String getBatchResolveContextListType() {
    return "List<Context>";
  }
}
