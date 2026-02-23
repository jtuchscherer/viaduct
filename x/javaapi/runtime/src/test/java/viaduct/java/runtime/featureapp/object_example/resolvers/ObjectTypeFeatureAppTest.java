package viaduct.java.runtime.featureapp.object_example.resolvers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import viaduct.java.api.annotations.Resolver;
import viaduct.java.runtime.featureapp.object_example.grt.Address;
import viaduct.java.runtime.featureapp.object_example.grt.Person;
import viaduct.java.runtime.featureapp.object_example.resolverbases.PersonResolvers;
import viaduct.java.runtime.featureapp.object_example.resolverbases.QueryResolvers;
import viaduct.java.runtime.fixtures.JavaFeatureAppTestBase;
import viaduct.service.api.ExecutionResult;

/**
 * Example demonstrating Java FeatureApp pattern with object types.
 *
 * <p>This test shows how to:
 *
 * <ul>
 *   <li>Define GraphQL object types (Person, Address) in the schema
 *   <li>Create resolvers that return object types with nested objects
 *   <li>Create object field resolvers (Person.fullAddress) that compute derived values
 *   <li>Test nested object field access and nullable fields
 * </ul>
 *
 * <p><b>How it works:</b>
 *
 * <ol>
 *   <li>Person and Address implement CompositeOutput (simulating codegen output)
 *   <li>QueryResolvers.PersonResolver handles Query.person field
 *   <li>PersonResolvers.FullAddressResolver handles Person.fullAddress field (object field
 *       resolver)
 *   <li>The object field resolver uses ctx.getObjectValue() to access the Person instance
 *   <li>JavaFeatureAppTestBase bootstraps both resolvers via JavaModuleBootstrapper
 * </ol>
 */
public class ObjectTypeFeatureAppTest extends JavaFeatureAppTestBase {

  /**
   * Returns the SDL schema for Viaduct engine. Do NOT define 'type Query' - Viaduct provides root
   * types automatically. Use only 'extend type Query' for your resolvers.
   */
  @Override
  public String getSdl() {
    return """
    type Address {
        street: String!
        city: String!
        country: String
    }

    type Person {
        name: String!
        age: Int
        address: Address
        fullAddress: String @resolver
    }

    extend type Query {
        person(name: String!): Person @resolver
    }
    """;
  }

  /**
   * Resolver implementation for Query.person field.
   *
   * <p>This resolver creates a Person object with nested Address based on the name argument.
   */
  @Resolver
  public static class PersonResolver extends QueryResolvers.PersonResolver {
    @Override
    public CompletableFuture<Person> resolve(Context ctx) {
      String name = ctx.getArguments().getName();

      Address address =
          Address.builder().street("123 Main St").city("San Francisco").country("USA").build();

      Person person = Person.builder().name(name).age(30).address(address).build();

      return CompletableFuture.completedFuture(person);
    }
  }

  /**
   * Resolver implementation for Person.fullAddress field.
   *
   * <p>This resolver demonstrates an object field resolver - it operates on Person (not Query) and
   * returns a computed value. Note that accessing other fields on the parent Person object requires
   * those fields to be in the selection set or declared as required selections on the resolver.
   *
   * <p>For simplicity, this resolver returns a static value to demonstrate the resolver pattern.
   */
  @Resolver
  public static class FullAddressResolver extends PersonResolvers.FullAddressResolver {
    @Override
    public CompletableFuture<String> resolve(Context ctx) {
      // Demonstrate that the resolver is called and can return a computed value.
      // In a real scenario, you might access ctx.getObjectValue() and read fields
      // that are declared in the resolver's objectSelectionSet.
      return CompletableFuture.completedFuture("123 Main St, San Francisco, USA");
    }
  }

  @Test
  public void personResolverReturnsObjectType() {
    ExecutionResult result =
        execute(
            "query($name: String!) { person(name: $name) { name age } }", Map.of("name", "Alice"));

    assertThat(result.getErrors()).isEmpty();
    assertThat(result.getData()).isNotNull();

    var data = result.getData();
    @SuppressWarnings("unchecked")
    var person = (Map<String, Object>) data.get("person");

    Assertions.assertNotNull(person);
    assertThat(person.get("name")).isEqualTo("Alice");
    assertThat(person.get("age")).isEqualTo(30);
  }

  @Test
  public void nestedObjectTypesAreAccessible() {
    ExecutionResult result =
        execute(
            "query($name: String!) { person(name: $name) { name address { street city country } }"
                + " }",
            Map.of("name", "Bob"));

    assertThat(result.getErrors()).isEmpty();

    var data = result.getData();
    Assertions.assertNotNull(data);
    @SuppressWarnings("unchecked")
    var person = (Map<String, Object>) data.get("person");

    Assertions.assertNotNull(person);
    assertThat(person.get("name")).isEqualTo("Bob");

    @SuppressWarnings("unchecked")
    var address = (Map<String, Object>) person.get("address");
    Assertions.assertNotNull(address);
    assertThat(address.get("street")).isEqualTo("123 Main St");
    assertThat(address.get("city")).isEqualTo("San Francisco");
    assertThat(address.get("country")).isEqualTo("USA");
  }

  @Test
  public void objectFieldResolverComputesValue() {
    ExecutionResult result =
        execute(
            "query($name: String!) { person(name: $name) { name fullAddress } }",
            Map.of("name", "Charlie"));

    assertThat(result.getErrors()).isEmpty();

    var data = result.getData();
    Assertions.assertNotNull(data);
    @SuppressWarnings("unchecked")
    var person = (Map<String, Object>) data.get("person");

    Assertions.assertNotNull(person);
    assertThat(person.get("name")).isEqualTo("Charlie");
    // The resolver returns a computed full address
    assertThat(person.get("fullAddress")).isEqualTo("123 Main St, San Francisco, USA");
  }

  @Test
  public void nullableFieldsHandledCorrectly() {
    // Test with inline query - the resolver still returns a Person with all fields populated
    // This test verifies that nullable fields (age, country) are correctly returned
    ExecutionResult result = execute("{ person(name: \"Dave\") { name age address { country } } }");

    assertThat(result.getErrors()).isEmpty();

    var data = result.getData();
    Assertions.assertNotNull(data);
    @SuppressWarnings("unchecked")
    var person = (Map<String, Object>) data.get("person");

    Assertions.assertNotNull(person);
    assertThat(person.get("name")).isEqualTo("Dave");
    // age is nullable but our resolver sets it to 30
    assertThat(person.get("age")).isEqualTo(30);

    @SuppressWarnings("unchecked")
    var address = (Map<String, Object>) person.get("address");
    Assertions.assertNotNull(address);
    // country is nullable and our resolver sets it to "USA"
    assertThat(address.get("country")).isEqualTo("USA");
  }
}
