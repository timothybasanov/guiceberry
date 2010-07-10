/*
 * Copyright (C) 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.guiceberry;

import com.google.common.collect.Maps;
import com.google.guiceberry.GuiceBerryModule.ToTearDown;
import com.google.guiceberry.junit3.ManualTearDownGuiceBerry;
import com.google.guiceberry.junit3.AutoTearDownGuiceBerry;
import com.google.guiceberry.junit4.GuiceBerryRule;
import com.google.inject.ConfigurationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.testing.guiceberry.GuiceBerryEnv;
import com.google.inject.testing.guiceberry.junit3.GuiceBerryJunit3;

import java.util.Map;

/**
 * @author Luiz-Otavio "Z" Zorzella
 */
class GuiceBerryUniverse {

  static final GuiceBerryUniverse INSTANCE = new GuiceBerryUniverse();
  
  final Map<Class<? extends Module>, NonFinals> gbeClassToInjectorMap = Maps.newHashMap();
  
  public final InheritableThreadLocal<TestDescription> currentTestDescriptionThreadLocal =
    new InheritableThreadLocal<TestDescription>();
  
  /**
   * If something goes wrong trying to get an Injector instance for some 
   * GuiceBerryEnv, this instance is stored in the 
   * {@link GuiceBerryUniverse#gbeClassToInjectorMap}, to allow for graceful
   * error handling.
   */
  private static final Injector BOGUS_INJECTOR = Guice.createInjector();
  private static final NonFinals BOGUS_NON_FINALS = 
    new NonFinals(BOGUS_INJECTOR, NoOpTestScopeListener.INSTANCE);
  
  class TestCaseScaffolding {

    private final TestDescription testDescription;
    private final EnvChooser envChooser;
    private final GuiceBerryUniverse universe;

    private NonFinals nonFinals;
    
    public TestCaseScaffolding(
        TestDescription testDescription,
        EnvChooser envChooser) {
      this.testDescription = testDescription;
      this.envChooser = envChooser;
      this.universe = GuiceBerryUniverse.this;
    }

    public synchronized void goSetUp() {
      
      // If anything should go wrong, we "tag" this scaffolding as having failed
      // to acquire an injector, so that the tear down knows to skip the
      // appropriate steps.
      nonFinals = BOGUS_NON_FINALS;

      checkPreviousTestCalledTearDown(testDescription);
      
      final Class<? extends Module> gbeClass =
        envChooser.guiceBerryEnvToUse(testDescription);
      
      universe.currentTestDescriptionThreadLocal.set(testDescription);
      nonFinals = getInjector(gbeClass);

      nonFinals.testScopeListener.enteringScope();
      injectMembersIntoTest(gbeClass, nonFinals.injector); 
    }

    private void injectMembersIntoTest(
        final Class<? extends Module> gbeClass, Injector injector) {
    
      try {
        injector.injectMembers(testDescription.getTestCase());
      } catch (ConfigurationException e) {
        String msg = String.format("Binding error in the GuiceBerry Env '%s': '%s'.",
            gbeClass.getName(), e.getMessage());
        notifyTestScopeListenerOfOutScope(universe.gbeClassToInjectorMap.get(gbeClass));
        throw new RuntimeException(msg, e);
      }
    }

    private NonFinals getInjector(final Class<? extends Module> gbeClass) {
      if (!universe.gbeClassToInjectorMap.containsKey(gbeClass)) {
        return foundGbeForTheFirstTime(gbeClass);  
      } else {
        NonFinals result = 
          universe.gbeClassToInjectorMap.get(gbeClass);
        if (result == BOGUS_NON_FINALS) {
          throw new RuntimeException(String.format(
              "Skipping '%s' GuiceBerryEnv which failed previously during injector creation.",
              gbeClass.getName()));
        }
        return result; 
      }
    }

    private void checkPreviousTestCalledTearDown(TestDescription testCase) {
      TestDescription previousTestCase = universe.currentTestDescriptionThreadLocal.get();
      
      if (previousTestCase != null) {  
        String msg = String.format(
            "Error while setting up a test: GuiceBerry was asked to " +
            "set up test '%s', but the previous test '%s' did not properly " +
            "call GuiceBerry's tear down.",
            testCase.getName(),
            previousTestCase.getName());
        throw new RuntimeException(msg);
      }
    }
    
    private NonFinals foundGbeForTheFirstTime(final Class<? extends Module> gbeClass) {
      
      Injector injector = BOGUS_INJECTOR;
      TestScopeListener testScopeListener = NoOpTestScopeListener.INSTANCE;
      
      NonFinals result = BOGUS_NON_FINALS;
      
      try {
        Module gbeInstance = createGbeInstanceFromClass(gbeClass);
        injector = Guice.createInjector(gbeInstance);
        callGbeMainIfBound(injector);
        try {
          boolean hasTestScopeListenerBinding = hasTestScopeListenerBinding(injector);
          boolean hasDeprecatedTestScopeListenerBinding = hasDeprecatedTestScopeListenerBinding(injector);
          if (hasTestScopeListenerBinding && hasDeprecatedTestScopeListenerBinding) {
            throw new RuntimeException(
              "Your GuiceBerry Env has bindings for both the new TestScopeListener and the deprecated one. Please fix.");
          } else if (hasTestScopeListenerBinding) {
            testScopeListener = injector.getInstance(TestScopeListener.class);
          } else if (hasDeprecatedTestScopeListenerBinding) {
            testScopeListener = injector.getInstance(com.google.inject.testing.guiceberry.TestScopeListener.class);
          }
        } catch (ConfigurationException e) {
          String msg = String.format("Error while creating a TestScopeListener: '%s'.",
            e.getMessage());
          throw new RuntimeException(msg, e); 
        }
        result = new NonFinals(injector, testScopeListener);
        return result;
      } finally {
        // This is in the finally block to ensure that BOGUS_INJECTOR
        // is put in the map if things go bad.
        universe.gbeClassToInjectorMap.put(gbeClass, result);
      }
    }

    private boolean hasBinding(Injector injector, Class<?> clazz) {
      return injector.getBindings().get(Key.get(clazz)) != null;
    }

    private <T> T getInstanceIfHasBinding(Injector injector, Class<T> clazz) {
      if (hasBinding(injector, clazz)) {
        return injector.getInstance(clazz);
      }
      return null;
    }
    
    
    private boolean hasDeprecatedTestScopeListenerBinding(Injector injector) {
      return hasBinding(injector, com.google.inject.testing.guiceberry.TestScopeListener.class);
    }

    private boolean hasTestScopeListenerBinding(Injector injector) {
      return hasBinding(injector, TestScopeListener.class);
    }

    private void callGbeMainIfBound(Injector injector) {
      com.google.inject.testing.guiceberry.GuiceBerryEnvMain foo = 
        getInstanceIfHasBinding(injector, com.google.inject.testing.guiceberry.GuiceBerryEnvMain.class);

      GuiceBerryEnvMain bar = 
        getInstanceIfHasBinding(injector, GuiceBerryEnvMain.class);
      
      if ((foo != null) && (bar != null)) {
        throw new RuntimeException();
      }
      
      if (foo != null) {
        foo.run();
      }
      
      if (bar != null) {
        bar.run();
      }
    }

    private Module createGbeInstanceFromClass(final Class<? extends Module> gbeClass) {
      Module result; 
      try {
        result = gbeClass.getConstructor().newInstance(); 
      } catch (NoSuchMethodException e) {
        String msg = String.format(
                "@%s class '%s' must have a public zero-arguments constructor",
                GuiceBerryEnv.class.getSimpleName(),
                gbeClass.getName()); 
        throw new IllegalArgumentException(msg, e); 
      } catch (Exception e) {
            String msg = String.format(
                    "Error creating instance of @%s '%s'",
                    GuiceBerryEnv.class.getSimpleName(),
                    gbeClass.getName()); 
              throw new IllegalArgumentException(msg, e); 
      }
      return result;
    }
    
    public void goTearDown() {
      
      Injector injector = this.nonFinals.injector;
      
      if (injector == BOGUS_INJECTOR) {
        // We failed to get a valid injector for this module in the setUp method,
        // so we just gracefully return, after cleaning up the threadlocal (which
        // normally would happen in the doTearDown method).
        universe.currentTestDescriptionThreadLocal.set(null);
        return;
      }
      
      ToTearDown toTearDown = injector.getInstance(ToTearDown.class);
      toTearDown.runTearDown();
      
      notifyTestScopeListenerOfOutScope(nonFinals);
      // TODO: this line used to be before the notifyTestScopeListenerOfOutScope
      // causing a bug -- e.d. a Provider<TestId> could not be used in the 
      // exitingScope method of the TestScopeListener. I haven't yet found a
      // good way to test this change.
      doTearDown(injector);
    }
    
    private void notifyTestScopeListenerOfOutScope(NonFinals nonFinals) {
      nonFinals.testScopeListener.exitingScope();
    }

    private void doTearDown(Injector injector) {
    
      if (!universe.currentTestDescriptionThreadLocal.get().equals(testDescription)) {
        String msg = String.format(GuiceBerryJunit3.class.toString() 
            + " cannot tear down "
            + testDescription.toString()
            + " because that test never called "
            + GuiceBerryJunit3.class.getCanonicalName()
            + ".setUp()"); 
        throw new RuntimeException(msg); 
      }
      universe.currentTestDescriptionThreadLocal.set(null);
      injector.getInstance(TestScope.class).finishScope(testDescription);    
    }
  }

  private static final class NoOpTestScopeListener implements TestScopeListener {
    
    public static final TestScopeListener INSTANCE = new NoOpTestScopeListener();

    public void enteringScope() {
    }
    
    public void exitingScope() {
    }
  }
  
  static final class NonFinals {

    final Injector injector;
    final TestScopeListener testScopeListener;

    public NonFinals(Injector injector, TestScopeListener testScopeListener) {
      this.injector = injector;
      this.testScopeListener = testScopeListener;
    }
  }
}