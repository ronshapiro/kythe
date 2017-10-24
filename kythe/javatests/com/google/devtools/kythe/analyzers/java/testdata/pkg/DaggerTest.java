package com.google.devtools.kythe.analyzers.java.testdata.pkg;

import dagger.Component;
import dagger.Module;
import dagger.Provides;
import javax.inject.Inject;

public class DaggerTest {
  @Component(modules = Mod.class)
  interface TestComponent {
    Foo foo();
    Bar bar();
  }

  static class Foo {}

  //- @#1Bar defines/binding KeyBar
  //- @#0Bar defines/binding ClassBar
  //- KeyBar.node/kind inject/key
  static class Bar { @Inject Bar() {}}
  //NOT_WORKING- KeyBar param.0 ClassBar


  @Module
  interface Mod {
    //- @fooMethod defines/binding KeyFoo
    //- @Foo ref ClassFoo
    //- KeyFoo.node/kind inject/key
    @Provides static Foo fooMethod() {
      return new Foo();
    }
    //NOT_WORKING - KeyFoo param.0 ClassFoo
  }
}
