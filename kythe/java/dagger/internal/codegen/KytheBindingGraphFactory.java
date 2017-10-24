package dagger.internal.codegen;

import com.google.auto.common.MoreElements;
import com.google.devtools.kythe.analyzers.java.Plugin.KytheGraph;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.model.JavacTypes;
import dagger.Component;
import dagger.producers.ProductionComponent;
import java.util.Optional;
import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

final class KytheBindingGraphFactory {
  private final ComponentDescriptor.Factory componentDescriptorFactory;
  private final BindingGraph.Factory bindingGraphFactory;

  KytheBindingGraphFactory(KytheGraph kytheGraph) {
    Types types = JavacTypes.instance(kytheGraph.getJavaContext());
    Elements elements = JavacElements.instance(kytheGraph.getJavaContext());
    DaggerElements daggerElements = new DaggerElements(elements, types);
    DaggerTypes daggerTypes = new DaggerTypes(types, elements);
    this.componentDescriptorFactory = createComponentDescriptorFactory(daggerElements, daggerTypes);
    this.bindingGraphFactory = createBindingGraphFactory(daggerTypes, daggerElements);
  }

  Optional<BindingGraph> create(TypeElement type) {
    if (MoreElements.isAnnotationPresent(type, Component.class)
        || MoreElements.isAnnotationPresent(type, ProductionComponent.class)) {
      // DO NOT SUBMIT: do we need to validate the graph?
      return Optional.of(bindingGraphFactory.create(componentDescriptorFactory.forComponent(type)));
    }
    return Optional.empty();
  }

  private static ComponentDescriptor.Factory createComponentDescriptorFactory(
      DaggerElements elements, DaggerTypes types) {
    Key.Factory keyFactory = new Key.Factory(types, elements);
    DependencyRequest.Factory dependencyRequestFactory = new DependencyRequest.Factory(keyFactory);
    MembersInjectionBinding.Factory membersInjectionBindingFactory =
        new MembersInjectionBinding.Factory(elements, types, keyFactory, dependencyRequestFactory);
    ProvisionBinding.Factory provisionBindingFactory =
        new ProvisionBinding.Factory(
            types, keyFactory, dependencyRequestFactory, membersInjectionBindingFactory);
    ProductionBinding.Factory productionBindingFactory =
        new ProductionBinding.Factory(types, keyFactory, dependencyRequestFactory);
    MultibindingDeclaration.Factory multibindingDeclarationFactory =
        new MultibindingDeclaration.Factory(elements, types, keyFactory);
    DelegateDeclaration.Factory bindingDelegateDeclarationFactory =
        new DelegateDeclaration.Factory(types, keyFactory, dependencyRequestFactory);
    SubcomponentDeclaration.Factory subcomponentDeclarationFactory =
        new SubcomponentDeclaration.Factory(keyFactory);
    OptionalBindingDeclaration.Factory optionalBindingDeclarationFactory =
        new OptionalBindingDeclaration.Factory(keyFactory);

    ModuleDescriptor.Factory moduleDescriptorFactory =
        new ModuleDescriptor.Factory(
            elements,
            provisionBindingFactory,
            productionBindingFactory,
            multibindingDeclarationFactory,
            bindingDelegateDeclarationFactory,
            subcomponentDeclarationFactory,
            optionalBindingDeclarationFactory);
    return new ComponentDescriptor.Factory(
        elements, types, dependencyRequestFactory, moduleDescriptorFactory);
  }

  private static BindingGraph.Factory createBindingGraphFactory(
      DaggerTypes types, DaggerElements elements) {
    Key.Factory keyFactory = new Key.Factory(types, elements);
    DependencyRequest.Factory dependencyRequestFactory = new DependencyRequest.Factory(keyFactory);
    Messager messager = new FakeMessager();
    CompilerOptions compilerOptions =
        CompilerOptions.builder()
            .usesProducers(true)
            .writeProducerNameInToken(true)
            .nullableValidationKind(Diagnostic.Kind.NOTE)
            .privateMemberValidationKind(Diagnostic.Kind.NOTE)
            .staticMemberValidationKind(Diagnostic.Kind.NOTE)
            .ignorePrivateAndStaticInjectionForComponent(false)
            .scopeCycleValidationType(ValidationType.NONE)
            .warnIfInjectionFactoryNotGeneratedUpstream(false)
            .experimentalAndroidMode(false)
            .build();

    MembersInjectionBinding.Factory membersInjectionBindingFactory =
        new MembersInjectionBinding.Factory(elements, types, keyFactory, dependencyRequestFactory);
    ProvisionBinding.Factory provisionBindingFactory =
        new ProvisionBinding.Factory(
            types, keyFactory, dependencyRequestFactory, membersInjectionBindingFactory);
    ProductionBinding.Factory productionBindingFactory =
        new ProductionBinding.Factory(types, keyFactory, dependencyRequestFactory);

    InjectValidator injectMethodValidator = new InjectValidator(types, elements, compilerOptions);

    InjectBindingRegistry injectBindingRegistry =
        new InjectBindingRegistry(
            elements,
            types,
            messager,
            injectMethodValidator,
            keyFactory,
            provisionBindingFactory,
            membersInjectionBindingFactory,
            compilerOptions);

    return new BindingGraph.Factory(
        elements,
        injectBindingRegistry,
        keyFactory,
        provisionBindingFactory,
        productionBindingFactory);
  }

  private static class FakeMessager implements Messager {
    @Override
    public void printMessage(Diagnostic.Kind kind, CharSequence charSequence) {}

    @Override
    public void printMessage(Diagnostic.Kind kind, CharSequence charSequence, Element element) {}

    @Override
    public void printMessage(
        Diagnostic.Kind kind,
        CharSequence charSequence,
        Element element,
        AnnotationMirror annotationMirror) {}

    @Override
    public void printMessage(
        Diagnostic.Kind kind,
        CharSequence charSequence,
        Element element,
        AnnotationMirror annotationMirror,
        AnnotationValue annotationValue) {}
  }
}
