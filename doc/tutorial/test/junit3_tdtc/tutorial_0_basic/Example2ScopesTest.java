package junit3_tdtc.tutorial_0_basic;

import com.google.common.testing.junit3.TearDownTestCase;
import com.google.guiceberry.GuiceBerryModule;
import com.google.guiceberry.TestScoped;
import com.google.guiceberry.junit3.AutoTearDownGuiceBerry;
import com.google.inject.BindingAnnotation;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Scopes;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public class Example2ScopesTest extends TearDownTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    AutoTearDownGuiceBerry.setup(this, Env.class);
  }

  @Inject
  @UnscopedIncrementingNumber
  private Provider<Integer> unscopedIncrementingNumber;

  @Inject
  @TestScopedIncrementingNumber
  private Provider<Integer> testScopedIncrementingNumber;

  @Inject
  @SingletonScopedIncrementingNumber
  private Provider<Integer> singletonScopedIncrementingNumber;

  public void testOne() throws Exception {
    assertEquals(300, singletonScopedIncrementingNumber.get().intValue());
    assertEquals(300, singletonScopedIncrementingNumber.get().intValue());

    assertEquals(200, testScopedIncrementingNumber.get().intValue());
    assertEquals(200, testScopedIncrementingNumber.get().intValue());

    assertEquals(100, unscopedIncrementingNumber.get().intValue());
    assertEquals(101, unscopedIncrementingNumber.get().intValue());
  }

  public void testTwo() throws Exception {
    assertEquals(300, singletonScopedIncrementingNumber.get().intValue());
    assertEquals(300, singletonScopedIncrementingNumber.get().intValue());

    assertEquals(201, testScopedIncrementingNumber.get().intValue());
    assertEquals(201, testScopedIncrementingNumber.get().intValue());

    assertEquals(102, unscopedIncrementingNumber.get().intValue());
    assertEquals(103, unscopedIncrementingNumber.get().intValue());
  }

  public static final class Env extends GuiceBerryModule {
    private static final class IncrementingProvider implements Provider<Integer> {
      private int number;

      public IncrementingProvider(int seed) {
        this.number = seed;
      }

      public Integer get() {
        return number++;
      }
    }

    @Override
    protected void configure() {
      super.configure();
      IncrementingProvider unscopedIncrementingNumberProvider = 
        new IncrementingProvider(100);
      IncrementingProvider testScopedIncrementingNumberProvider = 
        new IncrementingProvider(200);
      IncrementingProvider singletonScopedIncrementingNumberProvider = 
        new IncrementingProvider(300);
      bind(Integer.class)
        .annotatedWith(UnscopedIncrementingNumber.class)
        .toProvider(unscopedIncrementingNumberProvider);
      bind(Integer.class)
        .annotatedWith(TestScopedIncrementingNumber.class)
        .toProvider(testScopedIncrementingNumberProvider)
        .in(TestScoped.class);
      bind(Integer.class)
        .annotatedWith(SingletonScopedIncrementingNumber.class)
        .toProvider(singletonScopedIncrementingNumberProvider)
        .in(Scopes.SINGLETON);
    }
  }

  @Retention(RetentionPolicy.RUNTIME) 
  @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER}) 
  @BindingAnnotation
  private @interface UnscopedIncrementingNumber {}

  @Retention(RetentionPolicy.RUNTIME) 
  @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER}) 
  @BindingAnnotation
  private @interface TestScopedIncrementingNumber {}

  @Retention(RetentionPolicy.RUNTIME) 
  @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER}) 
  @BindingAnnotation
  private @interface SingletonScopedIncrementingNumber {}
}
