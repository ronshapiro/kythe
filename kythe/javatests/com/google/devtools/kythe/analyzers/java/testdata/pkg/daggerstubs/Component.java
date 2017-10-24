package dagger;

public @interface Component {
  Class<?>[] dependencies() default {};
  Class<?>[] modules() default {};
}
