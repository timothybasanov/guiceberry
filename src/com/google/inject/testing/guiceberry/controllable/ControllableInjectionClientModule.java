/*
 * Copyright (C) 2009 Google Inc.
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
package com.google.inject.testing.guiceberry.controllable;

import java.util.Map;

import com.google.common.testing.TearDown;
import com.google.common.testing.TearDownAccepter;
import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.testing.guiceberry.TestId;
import com.google.inject.testing.guiceberry.controllable.IcStrategyCouple.IcClientStrategy;

/**
 * This internal class is basically what the {@link IcMaster} uses to fullfil
 * its {@link IcMaster#buildClientModule()} method.
 * 
 * @author Luiz-Otavio Zorzella
 * @author Jesse Wilson
 */
final class ControllableInjectionClientModule extends AbstractModule {
  
  private final Map<Key<?>, IcStrategyCouple> rewriter;
  
  public ControllableInjectionClientModule(Map<Key<?>, IcStrategyCouple> rewriter) {
    this.rewriter = rewriter;
  }
  
  @SuppressWarnings("unchecked")
  @Override
  protected void configure() {
    for (Map.Entry<Key<?>, IcStrategyCouple> e : rewriter.entrySet()) {
      bind(IcStrategyCouple.wrap(IcClient.class, e.getKey()))
         .toProvider(new MyClientProvider(
             e.getKey(), 
             getProvider(TestId.class), 
             getProvider(e.getValue().clientControllerClass()),
             getProvider(TearDownAccepter.class)));
    }
  }

  private static final class MyClientProvider<T> implements Provider<IcClient<T>> {
    private final Key<T> key;
    private final Provider<IcClientStrategy> clientControllerSupportProvider;
    private final Provider<TestId> testIdProvider;
    private final Provider<TearDownAccepter> tearDownAccepterProvider;
    
    public MyClientProvider(Key<T> key,  
        Provider<TestId> testIdProvider, 
        Provider<IcClientStrategy> clientControllerSupportProvider,
        Provider<TearDownAccepter> tearDownAccepterProvider) {
      this.key = key;
      this.testIdProvider = testIdProvider;
      this.clientControllerSupportProvider = clientControllerSupportProvider;
      this.tearDownAccepterProvider = tearDownAccepterProvider;
    }

    public IcClient<T> get() {
      return new IcClient<T>() {     
        @SuppressWarnings("unchecked")
        public void setOverride(T override) {
          if (override == null) {
            throw new NullPointerException();
          }
          final IcClientStrategy icClientStrategy = 
            clientControllerSupportProvider.get();
          final ControllableId controllableId = 
            new ControllableId(testIdProvider.get(), key);
          tearDownAccepterProvider.get().addTearDown(new TearDown() {
            public void tearDown() throws Exception {
              icClientStrategy.resetOverride(controllableId);
            }
          });
          icClientStrategy.setOverride(controllableId, override);
        }
      };
    }
  }
}