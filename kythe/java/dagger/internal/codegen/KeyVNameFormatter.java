package dagger.internal.codegen;

import static com.google.auto.common.AnnotationMirrors.getAnnotationValuesWithDefaults;

import com.google.auto.common.MoreTypes;
import com.squareup.javapoet.CodeBlock;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;

final class KeyVNameFormatter {
  static String format(Key key) {
    StringBuilder builder = new StringBuilder(key.type().toString());
    if (key.qualifier().isPresent()) {
      builder.append(' ');
    }
    return builder.toString();
  }

  private static void formatAnnotation(AnnotationMirror annotation, StringBuilder stringBuilder) {
    stringBuilder.append('@').append(MoreTypes.asTypeElement(annotation.getAnnotationType()));
    Map<ExecutableElement, AnnotationValue> annotationValues =
        getAnnotationValuesWithDefaults(annotation);
    if (!annotationValues.isEmpty()) {
      stringBuilder.append('(');
      Iterator<Entry<ExecutableElement, AnnotationValue>> iterator =
          annotationValues.entrySet().iterator();
      while (iterator.hasNext()) {
        Entry<ExecutableElement, AnnotationValue> entry = iterator.next();
        stringBuilder
            .append(entry.getKey().getSimpleName())
            .append(entry.getValue().accept(new AnnotationValueFormatter(), stringBuilder));

        if (iterator.hasNext()) {
          stringBuilder.append(", ");
        }
      }
      stringBuilder.append(')');
    }
  }

  private static class AnnotationValueFormatter
      extends SimpleAnnotationValueVisitor8<Void, StringBuilder> {
    @Override
    public Void visitAnnotation(AnnotationMirror innerAnnotation, StringBuilder stringBuilder) {
      formatAnnotation(innerAnnotation, stringBuilder);
      return null;
    }

    @Override
    public Void visitArray(List<? extends AnnotationValue> list, StringBuilder stringBuilder) {
      stringBuilder.append('{');
      appendList(stringBuilder, list, value -> value.accept(this, stringBuilder));
      stringBuilder.append('}');
      return null;
    }

    @Override
    public Void visitEnumConstant(VariableElement enumConstant, StringBuilder stringBuilder) {
      stringBuilder.append(enumConstant.getSimpleName());
      return null;
    }

    @Override
    public Void visitType(TypeMirror typeMirror, StringBuilder stringBuilder) {
      stringBuilder.append(typeMirror).append(".class");
      return null;
    }

    @Override
    protected Void defaultAction(Object value, StringBuilder stringBuilder) {
      stringBuilder.append(value);
      return null;
    }

    @Override
    public Void visitString(String value, StringBuilder stringBuilder) {
      stringBuilder.append(CodeBlock.of("$S", value));
      return null;
    }
  }

  private static <T> void appendList(
      StringBuilder stringBuilder, Iterable<T> iterable, Consumer<T> consumer) {
    Iterator<T> iterator = iterable.iterator();
    while (iterator.hasNext()) {
      consumer.accept(iterator.next());
      if (iterator.hasNext()) {
        stringBuilder.append(", ");
      }
    }
  }
}
