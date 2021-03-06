package lombok.visitor;

import java.io.IOException;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleTypeVisitor7;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import com.squareup.javapoet.WildcardTypeName;

import lombok.AllArgsConstructor;
import lombok.ConfigurationKeys;
import lombok.NonNull;
import lombok.core.AnnotationProcessor;
import lombok.core.configuration.Presence;
import lombok.experimental.Visitable;
import lombok.experimental.VisitableRoot;
import lombok.experimental.VisitorAccept;
import lombok.visitor.VisitorInvariants.ConfigReader;

/**
 * Processes the {@link lombok.experimental.Visitable} and
 * {@link lombok.experimental.VisitableRoot} annotations to produce a visitor
 * interface corresponding to each visitable class hierarchy.
 * 
 * For example, given the following annotated classes
 * 
 * <pre>
 *  &#64;VisitableRoot
 * 	abstract class RootClass {
 *  }
 * </pre>,
 * 
 * 
 * <pre>
 *  &#64;Visitable(root=RootClass.class)
 * 	class ImplClass1 extends RootClass {
 *  }
 * </pre>, and
 * 
 * 
 * <pre>
 *  &#64;Visitable(root=RootClass.class)
 * 	class ImplClass2 extends RootClass {
 *  }
 * </pre>,
 * 
 * this will generate, in the same package as the above classes:
 * 
 * <pre>
 *  public interface RootClassVisitor&lt;R&gt; {
 *    R caseImplClass1(ImplClass1 implClass1);
 *    R caseImplClass2(ImplClass2 implClass2);
 *  }
 *    
 * </pre>
 * 
 * @author Derek
 *
 * @see lombok.experimental.Visitable
 * @see lombok.experimental.VisitableRoot
 */
@SupportedAnnotationTypes({"lombok.experimental.Visitable", "lombok.experimental.VisitableRoot"}) public class VisitorProcessor extends AbstractProcessor {
	
	/**
	 * @see ProcessingEnvironment.getTypeUtils
	 */
	private Elements elementUtils;
	/**
	 * @see ProcessingEnvironment.getFiler
	 */
	private Filer filer;
	/**
	 * @see ProcessingEnvironment.getMessager
	 */
	private Messager messager;
	
	@Override public synchronized void init(ProcessingEnvironment processingEnv) {
		super.init(processingEnv);
		elementUtils = processingEnv.getElementUtils();
		filer = processingEnv.getFiler();
		messager = processingEnv.getMessager();
	}
	
	/**
	 * Asserts that the given element is the given element subclass.
	 * 
	 * @param e
	 *            The element to assert on
	 * @param klass
	 *            The class the element is expected to be, and should be cast to
	 * @param annotation
	 *            The annotation that indicates that the element should be
	 *            castable
	 * @return The element cast to the given class
	 * @throws ProcessorException
	 *             If the element is of the wrong class
	 */
	@SuppressWarnings("unchecked") private <T extends Element> T assertElement(Element e, Class<T> klass, Class<? extends Annotation> annotation) throws ProcessorException {
		if (klass.isInstance(e)) return (T) e;
		else
			throw new ProcessorException(e, e + " cannot be annotated with " + annotation);
	}
	
	/**
	 * For each visitable hierarchy, writes a new visitor interface that visits
	 * that hierarchy
	 */
	@Override public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		try {
			// build up information for each hierarchy at its root
			final Map<String, VisitorInfo> visitors = new HashMap<String, VisitorInfo>();
			for (final Element visitableRoot : roundEnv.getElementsAnnotatedWith(VisitableRoot.class)) {
				messager.printMessage(Kind.WARNING, "Found visitable root: " + visitableRoot);
				TypeElement te = assertElement(visitableRoot, TypeElement.class, VisitableRoot.class);
				visitors.put(te.getQualifiedName().toString(), new VisitorInfo(te, visitableRoot.getAnnotation(VisitableRoot.class), createVisitorConfig(te)));
			}
			// add the visitable nodes to the information started by their
			// hierarchies
			for (final Element visitable : roundEnv.getElementsAnnotatedWith(Visitable.class)) {
				messager.printMessage(Kind.WARNING, "Found visitable: " + visitable);
				final TypeElement te = assertElement(visitable, TypeElement.class, Visitable.class);
				String[] visitorNames = getVisitorNames(te);
				for (String visitorName : visitorNames) {
					if (visitors.containsKey(visitorName)) {
						visitors.get(visitorName).addImplementation(te);
					}
					else {
						throw new ProcessorException(te, "Cannot find " + visitorName + " among " + visitors.keySet() + ", you may have forgotten a VisitableRoot annotation");
					}
				}
			}
			// write each visitor with full knowledge of its hierarchy
			for (VisitorInfo visitorPlan : visitors.values()) {
				visitorPlan.writeVisitor();
			}
			return false;
		} catch (ProcessorException e) {
			e.printWarning(messager);
		} catch (Exception e) {
			messager.printMessage(Kind.ERROR, Arrays.toString(e.getStackTrace()) + ": Could not write visitor: " + e);
		}
		return false;
	}
	
	private VisitorConfiguration createVisitorConfig(TypeElement te) {
		ExecutableElement visitorAccept = getVisitorAccept(te);
		if (visitorAccept == null) {
			ConfigReader reader = new VisitorInvariants.ElementConfigReader(te, elementUtils);
			return new VisitorConfiguration(reader);
		} else {
			return new VisitorConfiguration(visitorAccept);
		}
	}
	
	private ExecutableElement getVisitorAccept(TypeElement te) {
		for (Element e : te.getEnclosedElements()) {
			if ((e instanceof ExecutableElement) && (e.getAnnotation(VisitorAccept.class) != null)) {
				return (ExecutableElement) e;
			}
		}
		return null;
	}
	
	private String[] getVisitorNames(final TypeElement te) {
		String[] rootNames = te.getAnnotation(Visitable.class).root();
		if (rootNames.length == 0) {
			TypeMirror superclass = te.getSuperclass();
			String visitorName = superclass.accept(new SimpleTypeVisitor7<String, Void>() {
				@Override public String visitDeclared(DeclaredType t, Void p) {
					if (t.toString().equals(Object.class.getCanonicalName())) {
						// ignore inheritance from Object
						return defaultAction(t, p);
					} else {
						return t.asElement().getSimpleName().toString();
					}
				}
				@Override protected String defaultAction(TypeMirror e, Void p) {
					List<? extends TypeMirror> superInterfaces = te.getInterfaces();
					if (superInterfaces.size() > 0) {
						return superInterfaces.get(0).accept(new SimpleTypeVisitor7<String,Void>() {

							@Override public String visitDeclared(DeclaredType t, Void p) {
								return t.asElement().getSimpleName().toString();
							}
							
						}, p);
					} else {
						throw new ProcessorException(te, "Cannot find a visitable root");
					}
				}
			}, null);
			return new String[]{visitorName};
		} else {
			PackageElement packageElement = elementUtils.getPackageOf(te.getEnclosingElement());
			String[] visitorNames = new String[rootNames.length];
			for (int k = 0; k < rootNames.length; k++) {
				String rootName = rootNames[k];
				String visitorName = packageElement.isUnnamed() ? rootName : packageElement.getQualifiedName() + "." + rootName;
				visitorNames[k] = visitorName;
			}
			return visitorNames;
		}
	}
	
	/**
	 * Lower-cases the first letter in the given name (class name to variable
	 * name, for example)
	 * 
	 * @param name
	 *            A CamelCase string
	 * @return The corresponding camelCase string
	 */
	private static String asVar(String name) {
		return Character.toLowerCase(name.charAt(0)) + name.substring(1);
	}
	
	/**
	 * Contains all of the information necessary to define and write a visitor
	 * 
	 * @author Derek
	 *
	 */
	private class VisitorInfo {
		/**
		 * The root class/interface of the visitable hierarchy, has the abstract
		 * accept method
		 */
		private final TypeElement root;
		
		/**
		 * An "implementation" that uses the root, whose methods can be useful for default purposes
		 */
		private final Implementation rootImplementation;
		
		/**
		 * The leaf classes of the visitable hierarchy, have the implemented
		 * accept methods
		 */
		private final List<Implementation> implementations;
		
		/**
		 * The annotation on the root visitor
		 */
		private VisitableRoot annotation;
	
		private TypeVariableName[] typeVars;
		
		/**
		 * The visitor configuration
		 */
		private VisitorConfiguration config;
		
		/**
		 * Creates the visitor info
		 * 
		 * @param root
		 *            The root class/interface of the hierarchy
		 */
		public VisitorInfo(TypeElement root, VisitableRoot annotation, final VisitorConfiguration config) {
			super();
			this.root = root;
			this.annotation = annotation;
			this.rootImplementation = new Implementation(root, config) {

				@Override public String getMethodName() {
					return config.getCaseMethodPrefix() + "Default";
				}
				
			};
			this.implementations = new ArrayList<Implementation>();
			messager.printMessage(Kind.NOTE, config.toString());
			this.config = config;
			typeVars = createTypeVars(root, config);
		}
		
		private TypeVariableName[] createTypeVars(TypeElement root, VisitorConfiguration config) {
			List<TypeVariableName> typeVars = new ArrayList<TypeVariableName>();
			for (TypeParameterElement e : root.getTypeParameters()) {
				typeVars.add(TypeVariableName.get(e));
			}
			typeVars.addAll(config.getTypeVariables());
			return typeVars.toArray(new TypeVariableName[typeVars.size()]);
		}
		
		private TypeName parameterize(ClassName className) {
			if (typeVars.length == 0) {
				return className;
			} else {
				return ParameterizedTypeName.get(className, typeVars);
			}
		}

		/**
		 * Adds an implementation to the hierarchy
		 * 
		 * @param e
		 *            The implementing class
		 */
		public void addImplementation(TypeElement e) {
			implementations.add(new Implementation(e, config));
		}
		
		/**
		 * @return All elements involved in the hierarchy (root plus all leaves)
		 */
		public Element[] getAllElements() {
			Element[] e = new Element[1 + implementations.size()];
			e[0] = root;
			for (int k = 0; k < implementations.size(); k++) {
				e[k + 1] = implementations.get(k).getElement();
			}
			return e;
		}
		
		/**
		 * Writes the visitor to an appropriate file
		 * 
		 * @throws IOException
		 *             if writing is not possible
		 */
		public void writeVisitor() throws IOException {
			List<? extends TypeMirror> types = Collections.emptyList();
			try {
				annotation.order();
			} catch (MirroredTypesException e) {
				types = e.getTypeMirrors();
			}
			Collections.sort(implementations, new ListImplComparator(types));
			
			String visitorSimpleName = VisitorInvariants.createVisitorClassName(root.getSimpleName().toString());
			String visitorQualifiedName = VisitorInvariants.createVisitorClassName(root.getQualifiedName().toString());
			
			// public interface RootVisitor<R> {}
			final TypeSpec.Builder visitorSpec = TypeSpec.interfaceBuilder(visitorSimpleName).addModifiers(javax.lang.model.element.Modifier.PUBLIC);
			visitorSpec.addTypeVariables(getTypeVariables());
			
			for (Implementation impl : implementations) {
				// public abstract R caseImplementation(Implementation implementation);
				MethodSpec visitCaseSpec = impl.createAbstractCaseMethod();
				messager.printMessage(Kind.NOTE, "Visitor method: " + visitCaseSpec);
				visitorSpec.addMethod(visitCaseSpec);
			}
			
			PackageElement pack = elementUtils.getPackageOf(root);
		
			ClassName visitorName = ClassName.get(pack.getQualifiedName().toString(), visitorSimpleName);
			
			if (config.getConstantImplEnabled() && config.getReturnType() != null) {
				TypeSpec.Builder constantImpl = createConstantImpl(visitorSimpleName);
				Iterator<Implementation> iterator = implementations.iterator();
				if (iterator.hasNext()) {
					constantImpl.addType(createImmutableBuilder(visitorName, "Constant", visitorName.nestedClass("Constant"), iterator.next(), iterator, 0, false).addModifiers(Modifier.PUBLIC, Modifier.STATIC).build());
					String fieldName = implementations.get(0).getMethodName();
					// public static <R> Builder0<R> forImpl1(Function<Impl1, R> caseImpl1) {
					//   return new Builder0<>(caseImpl1);
					// }
					constantImpl.addMethod(MethodSpec.methodBuilder(fieldName)
							.addTypeVariables(getTypeVariables())
							.addParameter(config.getReturnType(), fieldName)
							.returns(parameterize(visitorName.nestedClass("Constant").nestedClass("Builder0")))
							.addStatement("return new Builder0<>($N)", fieldName)
							.addModifiers(Modifier.PUBLIC, Modifier.STATIC)
							.build());
				}
				visitorSpec.addType(constantImpl.build());
			}
		
			if (config.isDefaultImplEnabled()) {
				TypeSpec defaultImpl = createDefaultImpl(visitorSimpleName).build();
				visitorSpec.addType(defaultImpl);
			}
			
			if (config.isDefaultBuilderEnabled() && config.isLambdaImplEnabled()) {
				TypeSpec defaultBuilder = createDefaultBuilder(visitorSimpleName).build();
				visitorSpec.addType(defaultBuilder);
				TypeName defaultBuilderType = parameterize(visitorName.nestedClass("DefaultBuilder"));
				MethodSpec defaultBuilderInit = MethodSpec.methodBuilder("defaultBuilder")
						.addModifiers(Modifier.PUBLIC, Modifier.STATIC)
						.addTypeVariables(getTypeVariables())
						.returns(defaultBuilderType)
						.addStatement("return new $T()", defaultBuilderType)
						.build();
				visitorSpec.addMethod(defaultBuilderInit);
			}
			
			if (config.isLambdaImplEnabled()) {
				String version = Runtime.class.getPackage().getImplementationVersion();
				if (version.compareTo("1.8") < 0) {
					messager.printMessage(Kind.ERROR, "Lambda implementation is not supported for Java versions prior to 1.8", root);
				} else {
					TypeSpec.Builder lambdaImplBuilder = createLambdaImpl(visitorSimpleName);
					if (config.isLambdaBuilderEnabled()) {
						Iterator<Implementation> iterator = implementations.iterator();
						if (iterator.hasNext()) {
							lambdaImplBuilder.addType(createImmutableBuilder(visitorName, "Lambda", visitorName, iterator.next(), iterator, 0, true).addModifiers(Modifier.PUBLIC, Modifier.STATIC).build());
							String fieldName = implementations.get(0).getMethodName();
							// public static <R> Builder0<R> forImpl1(Function<Impl1, R> caseImpl1) {
							//   return new Builder0<>(caseImpl1);
							// }
							TypeName builderStart = parameterize(visitorName.nestedClass("Lambda").nestedClass("Builder0"));
							lambdaImplBuilder.addMethod(MethodSpec.methodBuilder(fieldName)
									.addModifiers(Modifier.PUBLIC, Modifier.STATIC)
									.addTypeVariables(getTypeVariables())
									.addParameter(implementations.get(0).getFunctionType(), fieldName)
									.addStatement("return new $T($N)", builderStart, fieldName)
									.returns(builderStart)
									.build());
						}
//					} else if (annotation.builder() == VisitableRoot.Builder.MUTABLE) {
//						visitorSpec.addTypes(createBuilderInterfaces(visitorName));
//						TypeSpec builderClass = createMutableBuilder(visitorName, "Lambda");
//						visitorSpec.addType(builderClass);
//						String methodName = implementations.get(0).getMethodName();
//						// public static <R> BuilderImpl2<R> forImpl1(Function<Impl1, R> caseImpl1) {
//						//   return new Builder<R>(caseImpl1);
//						// }
//						visitorSpec.addMethod(MethodSpec.methodBuilder(methodName)
//								.addTypeVariables(getTypeVariables())
//								.addParameter(implementations.get(0).getFunctionType(), methodName)
//								.returns(parameterize(visitorName.nestedClass("Builder" + implementations.get(1).getSimpleName())))
//								.addStatement("return new $N<>($N)", builderClass, methodName)
//								.build());
					}
					visitorSpec.addType(lambdaImplBuilder.build());
				}
			}
			
			JavaFileObject jfo = filer.createSourceFile(visitorQualifiedName, getAllElements());
			JavaFile javaFile = JavaFile.builder(pack.getQualifiedName().toString(), visitorSpec.build()).build();
			Writer writer = null;
			try {
				writer = jfo.openWriter();
				javaFile.writeTo(writer);
			} finally {
				if (writer != null) writer.close();
			}
		}

		private Iterable<TypeVariableName> getTypeVariables() {
			return Arrays.asList(typeVars);
		}

		/**
		 * Creates a mutable builder class, which returns different itself under a different interface with
		 * each method added to ensure at compilation time that all methods are added.
		 * 
		 * @param visitorName The name of the visitor
		 * @param implName The implementation of the visitor
		 * @return The specification of the builder class
		 */
		@SuppressWarnings("unused") private TypeSpec createMutableBuilder(ClassName visitorName, String implName) {
			TypeSpec.Builder builder = TypeSpec.classBuilder("Builder")
					.addModifiers(Modifier.STATIC, Modifier.PUBLIC)
					.addTypeVariables(getTypeVariables());
			for (int k = 1; k < implementations.size(); k++) {
				ClassName name = visitorName.nestedClass(implementations.get(k).getBuilderName());
				builder.addSuperinterface(parameterize(name));
			}
			// add fields
			for (int k = 0; k < implementations.size() - 1; k++) {
				Implementation impl = implementations.get(k);
				builder.addField(impl.getFunctionType(), impl.getMethodName(), Modifier.PRIVATE);
			}
			// add constructor
			{
				Implementation first = implementations.get(0);
				MethodSpec constructor = MethodSpec.constructorBuilder()
						.addModifiers(Modifier.PRIVATE)
						.addParameter(first.getFunctionType(), first.getMethodName())
						.addStatement("this.$N = $N", first.getMethodName(), first.getMethodName())
						.build();
				builder.addMethod(constructor);
			}
			// add build methods
			for (int k = 1; k < implementations.size(); k++) {
				Implementation current = implementations.get(k);
				MethodSpec.Builder method = MethodSpec.methodBuilder(current.getMethodName())
						.addModifiers(Modifier.PUBLIC)
						.addAnnotation(Override.class)
						.addParameter(current.getFunctionType(), current.getMethodName());
				if (k == implementations.size() - 1) {
					// final method, returns full visitor
					method.returns(parameterize(visitorName));
					method.addCode("return new $T(", parameterize(visitorName.nestedClass(implName)));
					for (int j = 0; j < implementations.size(); j++) {
						if (j > 0) {
							method.addCode(",");
						}
						method.addCode("$N", implementations.get(j).getMethodName());
					}
					method.addCode(");");
				} else {
					method.returns(parameterize((visitorName.nestedClass(implementations.get(k+1).getBuilderName()))));
					method.addStatement("this.$N = $N", current.getMethodName(), current.getMethodName());
					method.addStatement("return this");
				}
				builder.addMethod(method.build());
			}
			return builder.build();
		}

		/**
		 * Creates the builder interfaces implemented by the mutable builder. Each one is used to add
		 * the next function corresponding to its name. Only the first implementation does not have a builder
		 * interface, because it is added in the builder's constructor instead.
		 * @param visitorName The name of the visitor class
		 * @return A list of all of the builder interfaces
		 */
		@SuppressWarnings("unused") private List<TypeSpec> createBuilderInterfaces(ClassName visitorName) {
			List<TypeSpec> interfaces = new ArrayList<TypeSpec>();
			TypeSpec prev = null;
			for (int k = implementations.size() - 1; k >= 1; k--) {
				Implementation impl = implementations.get(k);
				TypeSpec.Builder builderInterface = TypeSpec.interfaceBuilder(impl.getBuilderName())
						.addTypeVariables(getTypeVariables())
						.addModifiers(Modifier.PUBLIC, Modifier.STATIC);
				String methodName = impl.getMethodName();// this is the final builder interface, returns the visitor itself
				MethodSpec.Builder method = MethodSpec.methodBuilder(methodName)
						.addParameter(impl.getFunctionType(), methodName, Modifier.FINAL)
						.addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT);
				ClassName returnedClass = (prev == null) ? visitorName : visitorName.nestedClass(prev.name);
				method.returns(parameterize(returnedClass));
				builderInterface.addMethod(method.build());
				TypeSpec spec = builderInterface.build();
				prev = spec;
				interfaces.add(spec);
			}
			return interfaces;
		}

		/**
		 * Recursively creates an immutable builder, which creates a new object as each new function is
		 * added, and uses nested classes to avoid copying fields.
		 * @param visitorName The name of the visitor class
		 * @param implName The implementation of the visitor
		 * @param enclosingName The nesting location of the builder
		 * @param current The implementation that this builder's method adds
		 * @param rest The remaining implementations
		 * @param i The index of this implementation
		 * @param function True if fields are functions, false if they are constants
		 * @return A builder that adds a function for the current implementation, then returns either the next builder or the built visitor
		 */
		private TypeSpec.Builder createImmutableBuilder(ClassName visitorName, String implName, ClassName enclosingName, Implementation current, Iterator<Implementation> rest, int i, boolean function) {
			String fieldName = current.getMethodName();
			TypeSpec.Builder builder = TypeSpec.classBuilder("Builder" + i)
					.addAnnotation(AllArgsConstructor.class)
					.addModifiers(Modifier.PUBLIC)
					.addField(function ? current.getFunctionType() : config.getReturnType(), fieldName);
			if (i == 0) {
				builder.addTypeVariables(getTypeVariables());
			}
			Implementation next = rest.next();
			String nextFieldName = next.getMethodName();
			ParameterSpec param = ParameterSpec.builder(function ? next.getFunctionType() : config.getReturnType(), nextFieldName).addAnnotation(NonNull.class).build();
			MethodSpec.Builder methodSpec = MethodSpec.methodBuilder(nextFieldName)
					.addParameter(param)
					.addModifiers(Modifier.PUBLIC);
			ClassName innerName = enclosingName.nestedClass("Builder" + i);
			ClassName furtherInnerName = innerName.nestedClass("Builder" + (i + 1));
			if (rest.hasNext()) {
				TypeSpec inner = createImmutableBuilder(visitorName, implName, innerName, next, rest, i + 1, function).build();
				builder.addType(inner);
				methodSpec.returns(furtherInnerName);
				methodSpec.addStatement("return new $T($N)", furtherInnerName, param);
			} else {
				methodSpec.returns(parameterize(visitorName));
				methodSpec.addCode("return new $T(", parameterize(ClassName.bestGuess(implName)));
				Iterator<Implementation> allIter = implementations.iterator();
				while (allIter.hasNext()) {
					methodSpec.addCode("$N", allIter.next().getMethodName());
					if (allIter.hasNext()) {
						methodSpec.addCode(",");
					}
				}
				methodSpec.addCode(");\n");
			}
			builder.addMethod(methodSpec.build());
			return builder;
		}

		/**
		 * Creates the default implementation for this visitor, which delegates each case to an abstract
		 * default case.
		 * @param visitorName the name of the visitor
		 * @return The default implementation
		 */
		private TypeSpec.Builder createDefaultImpl(String visitorName) {
			TypeSpec.Builder builder = TypeSpec.classBuilder("Default")
					.addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.ABSTRACT)
					.addTypeVariables(getTypeVariables())
					.addSuperinterface(parameterize(ClassName.bestGuess(visitorName)));
			MethodSpec defaultMethod = rootImplementation.createAbstractCaseMethod();
			builder.addMethod(defaultMethod);
			for (Implementation impl : implementations) {
				MethodSpec.Builder method = impl.createConcreteCaseMethod();
				CodeBlock.Builder code = CodeBlock.builder();
				if (config.getReturnType() != null) {
					code.add("return ");
				}
				code.add("$N($N", defaultMethod, impl.getArgName());
				if (config.getArgumentType() != null) {
					code.add(", $N", config.getArgument().name);
				}
				code.add(");\n");
				method.addCode(code.build());
				builder.addMethod(method.build());
			}
			return builder;
		}
		
		/**
		 * Creates the default builder, which uses the lambda implementation for building.
		 * @param visitorName the name of the visitor
		 * @return The default implementation
		 */
		private TypeSpec.Builder createDefaultBuilder(String visitorName) {
			TypeSpec.Builder builder = TypeSpec.classBuilder("DefaultBuilder")
					.addModifiers(Modifier.PUBLIC, Modifier.STATIC)
					.addTypeVariables(getTypeVariables());
			// each implementation needs a field for its lambda and a method to set its lambda
			for (Implementation impl : implementations) {
				FieldSpec fieldSpec = FieldSpec.builder(impl.getFunctionType(), impl.getMethodName(), Modifier.PRIVATE).build();
				builder.addField(fieldSpec);
				MethodSpec setter = MethodSpec.methodBuilder(impl.getMethodName())
						.addParameter(impl.getFunctionType(), impl.getMethodName(), Modifier.FINAL)
						.addStatement("this.$N = $N", impl.getMethodName(), impl.getMethodName())
						.addStatement("return this")
						.returns(parameterize(ClassName.bestGuess("DefaultBuilder")))
						.build();
				builder.addMethod(setter);
			}
			MethodSpec.Builder setDefault = MethodSpec.methodBuilder("caseDefault")
					.addParameter(rootImplementation.getFunctionType(), "caseDefault", Modifier.FINAL)
					.returns(parameterize(ClassName.bestGuess(visitorName)));
			CodeBlock.Builder code = CodeBlock.builder();
			for (Implementation impl : implementations) {
				code.beginControlFlow("if ($N == null)", impl.getMethodName());
				code.addStatement("$N = $N", impl.getMethodName(), "caseDefault");
				code.endControlFlow();
			}
			code.add("return new $T(", parameterize(ClassName.bestGuess(visitorName).nestedClass("Lambda")));
			Iterator<Implementation> iter = implementations.iterator();
			while (iter.hasNext()) {
				code.add("$N", iter.next().getMethodName());
				if (iter.hasNext()) {
					code.add(",");
				}
			}
			code.add(");");
			setDefault.addCode(code.build());
			builder.addMethod(setDefault.build());
			return builder;
		}
		
		/**
		 * Creates the lambda implementation for the visitor, which has a delegated-to Function field
		 * for each implementation.
		 * @param visitorName The name of the visitor.
		 * @return The lambda implementation.
		 */
		private TypeSpec.Builder createLambdaImpl(String visitorName) {
			TypeSpec.Builder builder = TypeSpec.classBuilder("Lambda")
					.addTypeVariables(getTypeVariables())
					.addAnnotation(AllArgsConstructor.class)
					.addSuperinterface(parameterize(ClassName.bestGuess(visitorName)))
					.addModifiers(Modifier.PUBLIC, Modifier.STATIC);
			for (Implementation impl : implementations) {
				TypeName functionType = impl.getFunctionType();
				FieldSpec fieldSpec = FieldSpec.builder(functionType, impl.getMethodName(), Modifier.PRIVATE, Modifier.FINAL).addAnnotation(NonNull.class).build();
				builder.addField(fieldSpec);
				MethodSpec.Builder methodSpec = impl.createConcreteCaseMethod();
				CodeBlock.Builder code = CodeBlock.builder();
				if (config.getReturnType() != null) {
					code.add("return $N.apply(", fieldSpec);
				} else {
					code.add("$N.accept(", fieldSpec);
				}
				code.add("$N", impl.getArgName());
				if (config.getArgument() != null) {
					code.add(", $N", config.getArgument());
				}
				code.add(");\n");
				methodSpec.addCode(code.build());
				builder.addMethod(methodSpec.build());
			}
			return builder;
		}
		
		/**
		 * Creates the constant implementation for the visitor, which has a delegated-to constant field
		 * to return for each implementation
		 * @param visitorName The name of the visitor
		 * @return The constant implementation
		 */
		private TypeSpec.Builder createConstantImpl(String visitorName) {
			TypeSpec.Builder builder = createImpl(visitorName, "Constant");
			for (Implementation impl : implementations) {
				FieldSpec fieldSpec = FieldSpec.builder(config.getReturnType(), impl.getMethodName(), Modifier.PRIVATE, Modifier.FINAL).build();
				builder.addField(fieldSpec);
				MethodSpec.Builder methodSpec = impl.createConcreteCaseMethod();
				methodSpec.addStatement("return $N", fieldSpec);
				builder.addMethod(methodSpec.build());
			}
			return builder;
		}
		
		private TypeSpec.Builder createImpl(String visitorName, String implName) {
				return TypeSpec.classBuilder(implName)
					.addTypeVariables(getTypeVariables())
					.addAnnotation(AllArgsConstructor.class)
					.addSuperinterface(parameterize(ClassName.bestGuess(visitorName)))
					.addModifiers(Modifier.PUBLIC, Modifier.STATIC);		
		}
		
	}
	
	/**
	 * Represents an implementation class in the visitable hierarchy
	 * @author Derek
	 *
	 */
	private class Implementation implements Comparable<Implementation> {
		/**
		 * The compiled type element of the implementation
		 */
		private final TypeElement element;
		
		/**
		 * The configuration information relevant to this visitor
		 */
		private final VisitorConfiguration config;

		/**
		 * @param element The type element of the implementation
		 */
		public Implementation(final TypeElement element, final VisitorConfiguration config) {
			this.element = element;
			this.config = config;
		}

		/**
		 * @return The simple name of the class
		 */
		public String getSimpleName() {
			return element.getSimpleName().toString();
		}

		/**
		 * @return The simple name of a builder corresponding to the class
		 */
		public String getBuilderName() {
			return "Builder" + element.getSimpleName();
		}

		/**
		 * @return The function type appropriate for this implementation, accepting it and returning
		 * the generic return type, with proper wildcard bounds for a function
		 */
		public TypeName getFunctionType() {
			TypeName objType = WildcardTypeName.supertypeOf(TypeName.get(element.asType()));
			if (config.getArgument() == null) {
				if (config.getReturnType() == null) {
					return ParameterizedTypeName.get(ClassName.get(Consumer.class), objType);
				} else {
					return ParameterizedTypeName.get(ClassName.get(Function.class), objType, config.getReturnWildcard());
				}
			} else {
				if (config.getReturnType() == null) {
					return ParameterizedTypeName.get(ClassName.get(BiConsumer.class), objType, config.getArgumentWildcard());
				} else {
					return ParameterizedTypeName.get(ClassName.get(BiFunction.class), objType, config.getArgumentWildcard(), config.getReturnWildcard());
				}
			}
		}

		/**
		 * @return The TypeName corresponding to this class
		 */
		public TypeName getTypeName() {
			return TypeName.get(element.asType());
		}

		/**
		 * @return The name of a case method for this class
		 */
		public String getMethodName() {
			return config.getCaseMethodPrefix() + element.getSimpleName();
		}

		/**
		 * @return The type element for this class
		 */
		public TypeElement getElement() {
			return element;
		}

		/**
		 * @return What a parameter known to be this class ought to be called
		 */
		public String getArgName() {
			return asVar(element.getSimpleName().toString());
		}

		/**
		 * @return A partially-built case method appropriate for this implementation
		 */
		private MethodSpec.Builder createCaseMethod() {
			MethodSpec.Builder builder = MethodSpec.methodBuilder(getMethodName())
					.addModifiers(Modifier.PUBLIC)
					.addParameter(getTypeName(), getArgName());
			ParameterSpec arg = config.getArgument();
			if (arg != null) {
				builder.addParameter(arg);
			}
			if (config.getReturnType() != null) {
				builder.returns(config.getReturnType());
			}
			return builder;
		}

		/**
		 * @return An abstract case method appropriate for this implementation
		 */
		public MethodSpec createAbstractCaseMethod() {
			return createCaseMethod().addModifiers(Modifier.ABSTRACT).build();
		}

		/**
		 * @return A partially-built overriding case method appropriate for this implementation
		 */
		public MethodSpec.Builder createConcreteCaseMethod() {
			return createCaseMethod().addAnnotation(AnnotationSpec.builder(Override.class).build());
		}
	
		@Override public int compareTo(Implementation other) {
			int curWeight = element.getAnnotation(Visitable.class).weight();
			int otherWeight = other.element.getAnnotation(Visitable.class).weight();
			if (curWeight < otherWeight) {
				return -1;
			} else if (curWeight > otherWeight) {
				return 1;
			} else {
				return getSimpleName().compareTo(other.getSimpleName());
			}
		}
	}
	
	/**
	 * Compares implementations first by their mirrors' order in a list, then by natural order.
	 * An implementation that appears in the list comes before all that do not.
	 * @author derek
	 */
	private class ListImplComparator implements Comparator<Implementation> {

		private List<? extends TypeMirror> order;
		
		public ListImplComparator(List<? extends TypeMirror> order) {
			this.order = order;
		}
		
		@Override public int compare(Implementation o1, Implementation o2) {
			int index1 = order.indexOf(o1.element.asType());
			int index2 = order.indexOf(o2.element.asType());
			if (index1 >= 0) {
				if (index2 >= 0) {
					return index1 - index2;
				} else {
					return -1;
				}
			} else {
				if (index2 >= 0) {
					return 1;
				} else {
					return o1.compareTo(o2);
				}
			}
		}
		
	}
	
	/**
	 * @see AnnotationProcessor
	 */
	@Override public SourceVersion getSupportedSourceVersion() {
		return SourceVersion.values()[SourceVersion.values().length - 1];
	}
	
	class VisitorConfiguration {
		@Override public String toString() {
			return "VisitorConfiguration [returnType=" + returnType + ", argumentType=" + argumentType + ", argument=" + argument + ", caseMethodPrefix=" + caseMethodPrefix + ", constantImplEnabled=" + constantImplEnabled + ", lambdaImplEnabled=" + lambdaImplEnabled + ", lambdaBuilderEnabled=" + lambdaBuilderEnabled + ", defaultImplEnabled=" + defaultImplEnabled + ", defaultBuilderEnabled=" + defaultBuilderEnabled + "]";
		}

		private final TypeVariableName returnType;
		private final TypeVariableName argumentType;
		private final ParameterSpec argument;
		private final String caseMethodPrefix;
		private final boolean constantImplEnabled;
		private final boolean lambdaImplEnabled;
		private final boolean lambdaBuilderEnabled;
		private final boolean defaultImplEnabled;
		private final boolean defaultBuilderEnabled;
	
		public VisitorConfiguration(ConfigReader reader) {
			Presence retPresence = reader.readConfiguration(ConfigurationKeys.VISITOR_RETURN);
			returnType = (retPresence != Presence.ABSENT ? TypeVariableName.get(VisitorInvariants.getReturnTypeVariableName(reader)) : null);
			Presence argPresence = reader.readConfiguration(ConfigurationKeys.VISITOR_ARGUMENT);
			argumentType = (argPresence == Presence.REQUIRED ? TypeVariableName.get(VisitorInvariants.getArgumentTypeVariableName(reader)) : null);
			String argName = VisitorInvariants.getArgumentVariableName(reader);
			argument = createArgument(argumentType, argName);
			caseMethodPrefix = VisitorInvariants.getVisitorCasePrefix(reader);
			constantImplEnabled = VisitorInvariants.getConstantImplEnabled(reader);
			lambdaImplEnabled = VisitorInvariants.getLambdaImplEnabled(reader);
			lambdaBuilderEnabled = VisitorInvariants.getLambdaBuilderEnabled(reader);
			defaultImplEnabled = VisitorInvariants.getDefaultImplEnabled(reader);
			defaultBuilderEnabled = VisitorInvariants.getDefaultBuilderEnabled(reader);
	
		}
		
		public VisitorConfiguration(ExecutableElement acceptMethod) {
			VisitorAccept visitorAccept = acceptMethod.getAnnotation(VisitorAccept.class);
			returnType = acceptMethod.getReturnType().accept(new SimpleTypeVisitor7<TypeVariableName,Void>(null) {
				@Override public TypeVariableName visitTypeVariable(TypeVariable t, Void p) {
					return TypeVariableName.get(t);
				}
			}, null);
			if (acceptMethod.getParameters().size() > 1) {
				argumentType = TypeVariableName.get((TypeVariable) acceptMethod.getParameters().get(1).asType());
				String argumentName = acceptMethod.getParameters().get(1).getSimpleName().toString();
				argument = createArgument(argumentType, argumentName);
			} else {
				argumentType = null;
				argument = null;
			}
			caseMethodPrefix = visitorAccept.caseMethodPrefix();
			constantImplEnabled = visitorAccept.constantImplEnabled();
			lambdaImplEnabled = visitorAccept.lambdaImplEnabled();
			lambdaBuilderEnabled = visitorAccept.lambdaBuilderEnabled();
			defaultImplEnabled = visitorAccept.defaultImplEnabled();
			defaultBuilderEnabled = visitorAccept.defaultBuilderEnabled();
		}

		public boolean isLambdaImplEnabled() {
			return lambdaImplEnabled;
		}

		public boolean isLambdaBuilderEnabled() {
			return lambdaBuilderEnabled;
		}

		public boolean isDefaultImplEnabled() {
			return defaultImplEnabled;
		}

		public boolean isDefaultBuilderEnabled() {
			return defaultBuilderEnabled;
		}

		public boolean getConstantImplEnabled() {
			return constantImplEnabled;
		}

		public String getCaseMethodPrefix() {
			return caseMethodPrefix;
		}

		public List<TypeVariableName> getTypeVariables() {
			List<TypeVariableName> vars = new ArrayList<TypeVariableName>(2);
			if (returnType != null) {
				vars.add(returnType);
			}
			if (argumentType != null) {
				vars.add(argumentType);
			}
			return vars;
		}

		private ParameterSpec createArgument(TypeVariableName argumentType, String argName) {
			if (argumentType == null) {
				return null;
			} else {
				return ParameterSpec.builder(argumentType, argName, Modifier.FINAL).build();
			}
		}

		public TypeVariableName getReturnType() {
			return returnType;
		}

		public ParameterSpec getArgument() {
			return argument;
		}
		
		public TypeVariableName getArgumentType() {
			return argumentType;
		}
		
		public WildcardTypeName getArgumentWildcard() {
			return WildcardTypeName.supertypeOf(argumentType);
		}
		
		public WildcardTypeName getReturnWildcard() {
			return WildcardTypeName.subtypeOf(returnType);
		}

	}	

}
