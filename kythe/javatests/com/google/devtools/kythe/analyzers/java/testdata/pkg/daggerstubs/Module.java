package dagger;

public @interface Module {
  Class<?>[] includes() default {};
  Class<?>[] subcomponents() default {};
}
