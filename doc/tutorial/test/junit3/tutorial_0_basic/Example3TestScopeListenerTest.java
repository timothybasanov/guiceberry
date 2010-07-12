package junit3.tutorial_0_basic;

import com.google.common.testing.TearDown;
import com.google.guiceberry.GuiceBerryModule;
import com.google.guiceberry.TestId;
import com.google.guiceberry.TestWrapper;
import com.google.guiceberry.junit3.ManualTearDownGuiceBerry;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Scopes;

import junit.framework.TestCase;

public class Example3TestScopeListenerTest extends TestCase {

  private TearDown toTearDown;
  
  @Override
  protected void tearDown() throws Exception {
    toTearDown.tearDown();
    super.tearDown();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    toTearDown = ManualTearDownGuiceBerry.setUp(this, Env.class);
  }

  public void testOne() throws Exception {
    System.out.println("Inside testOne");
  }

  public void testTwo() throws Exception {
    System.out.println("Inside testTwo");
  }

  public static final class Example3TestScopeListener implements TestWrapper {

    @Inject
    private Provider<TestId> testId;

    public void toRunBeforeTest() {
      System.out.println("Entering scope of: " + testId.get());
    }

    public void toRunAfterTest() {
      System.out.println("Exiting scope of: " + testId.get());
    }
  }

  public static final class Env extends GuiceBerryModule {
    
    @Override
    protected void configure() {
      // TODO Auto-generated method stub
      super.configure();
      bind(TestWrapper.class).to(Example3TestScopeListener.class).in(Scopes.SINGLETON);
    }
  }
}
