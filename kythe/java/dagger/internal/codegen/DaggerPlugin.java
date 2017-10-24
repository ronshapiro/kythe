package dagger.internal.codegen;

import static com.google.devtools.kythe.analyzers.base.EdgeKind.DEFINES_BINDING;

import com.google.auto.common.MoreElements;
import com.google.auto.service.AutoService;
import com.google.devtools.kythe.analyzers.base.EntrySet;
import com.google.devtools.kythe.analyzers.base.FactEmitter;
import com.google.devtools.kythe.analyzers.base.KytheEntrySets;
import com.google.devtools.kythe.analyzers.java.Plugin;
import com.google.devtools.kythe.util.Span;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;

@AutoService(Plugin.class)
public class DaggerPlugin extends Plugin.Scanner<Void, Void> {
  private JavacTrees trees;
  private FactEmitter emitter;
  private JCCompilationUnit compilationUnit;
  private KytheBindingGraphFactory bindingGraphFactory;

  @Override
  public Void visitClassDef(JCClassDecl tree, Void p) {
    TypeElement type = MoreElements.asType(trees.getElement(trees.getPath(compilationUnit, tree)));
    bindingGraphFactory.create(type).ifPresent(this::addEdgesForGraph);
    return super.visitClassDef(tree, p);
  }

  private void addEdgesForGraph(BindingGraph graph) {
    for (ResolvedBindings resolvedBinding : graph.resolvedBindings().values()) {
      for (Binding binding : resolvedBinding.bindings()) {
        for (DependencyRequest dependency : binding.explicitDependencies()) {
          addEdgesForDependencyRequest(dependency, dependency.bindingKey(), graph);
        }
      }
    }
    for (ComponentDescriptor.ComponentMethodDescriptor componentMethod :
        graph.componentDescriptor().componentMethods()) {
      if (componentMethod.dependencyRequest().isPresent()) {
        DependencyRequest dependency = componentMethod.dependencyRequest().get();
        addEdgesForDependencyRequest(dependency, dependency.bindingKey(), graph);
      }
    }
  }

  /**
   * Adds an edge from {@code dependency} to all bindings with {@linkplain
   * BindingDeclaration#bindingElement() binding elements} that resolve it in the graph. For
   * bindings without elements, add an edge from {@code dependency} to the bindings with elements on
   * which the elementless binding transitively depends.
   *
   * <p>Skips synthetic dependencies (without elements).
   */
  private void addEdgesForDependencyRequest(
      DependencyRequest dependency, BindingKey bindingKey, BindingGraph graph) {
    if (!dependency.requestElement().isPresent()) {
      return;
    }
    for (ContributionBinding binding :
        graph.resolvedBindings().get(bindingKey).contributionBindings()) {
      if (binding.bindingElement().isPresent()) {
        EntrySet bindingAnchor =
            entrySets.newAnchorAndEmit(
                kytheGraph.getNode(compilationUnit).get().getVName(),
                bindingElementSpan(binding),
                null);

        EntrySet keyNode = keyNode(bindingKey.key());
        emitter.emitEdge(bindingAnchor.getVName(), DEFINES_BINDING.getValue(), keyNode.getVName());
        emitter.emitEdge(keyNode.getVName(), "param.0", bindingTypeNode(binding).getVName());

      } else {
        for (DependencyRequest subsequentDependency : binding.explicitDependencies()) {
          addEdgesForDependencyRequest(dependency, subsequentDependency.bindingKey(), graph);
        }
      }
    }
  }

  private Span bindingElementSpan(Binding binding) {
    Element bindingElement = binding.bindingElement().get();
    JCTree bindingTree = trees.getTree(bindingElement);
    Name name =
        bindingElement.getKind().equals(ElementKind.CONSTRUCTOR)
            ? bindingElement.getEnclosingElement().getSimpleName()
            : bindingElement.getSimpleName();
    return kytheGraph.findIdentifier(name, bindingTree.getPreferredPosition()).get();
  }

  private EntrySet keyNode(Key key) {
    // Question: How can we add key-specific information to a node? I don't see any methods on
    // NodeBuilder that allow for modification of the VName
    EntrySet keyNode = entrySets.newNode("inject/key").build();
    keyNode.emit(entrySets.getEmitter());
    return keyNode;
  }

  // DO NOT SUBMIT docs. this is the type of the key without the qualifiers
  private KytheNode bindingTypeNode(Binding binding) {
    Element element = binding.bindingElement().get();
    if (element.getKind().equals(ElementKind.CONSTRUCTOR)) {
      return kytheGraph.getNode((ClassSymbol) binding.bindingTypeElement().get()).get();
    }

    JCTree returnTypeTree = ((JCMethodDecl) trees.getTree(element)).getReturnType();
    return kytheGraph.getNode(returnTypeTree).get();
  }

  @Override
  public void run(
      JCCompilationUnit compilationUnit, KytheEntrySets entrySets, KytheGraph kytheGraph) {
    if (bindingGraphFactory == null) {
      trees = JavacTrees.instance(kytheGraph.getJavaContext());
      emitter = entrySets.getEmitter();
      bindingGraphFactory = new KytheBindingGraphFactory(kytheGraph);
    }
    this.compilationUnit = compilationUnit;
    super.run(compilationUnit, entrySets, kytheGraph);
  }
}
