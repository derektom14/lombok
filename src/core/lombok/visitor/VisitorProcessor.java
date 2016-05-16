package lombok.visitor;

import java.io.IOException;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;

import lombok.core.AnnotationProcessor;
import lombok.experimental.Visitable;
import lombok.experimental.VisitableRoot;

import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;

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
				TypeElement te = assertElement(visitableRoot, TypeElement.class, VisitableRoot.class);
				visitors.put(te.getQualifiedName().toString(), new VisitorInfo(te));
			}
			// add the visitable nodes to the information started by their
			// hierarchies
			for (final Element visitable : roundEnv.getElementsAnnotatedWith(Visitable.class)) {
				TypeElement te = assertElement(visitable, TypeElement.class, Visitable.class);
				String rootName = visitable.getAnnotation(Visitable.class).root();
				PackageElement packageElement = elementUtils.getPackageOf(te.getEnclosingElement());
				String visitorName = packageElement.isUnnamed() ? rootName : packageElement.getQualifiedName() + "." + rootName;
				if (visitors.containsKey(visitorName)) visitors.get(visitorName).addImplementation(te);
				else
					throw new ProcessorException(te, "Cannot find " + visitorName + " among " + visitors.keySet() + ", you may have forgotten a VisitableRoot annotation");
			}
			// write each visitor with full knowledge of its hierarchy
			for (VisitorInfo visitorPlan : visitors.values()) {
				visitorPlan.writeVisitor();
			}
			return false;
		} catch (ProcessorException e) {
			e.printWarning(messager);
		} catch (IOException e) {
			messager.printMessage(Kind.ERROR, "Could not write visitor: " + e);
		}
		return false;
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
		 * The leaf classes of the visitable hierarchy, have the implemented
		 * accept methods
		 */
		private final List<TypeElement> implementations;
		
		/**
		 * Creates the visitor info
		 * 
		 * @param root
		 *            The root class/interface of the hierarchy
		 */
		public VisitorInfo(TypeElement root) {
			super();
			this.root = root;
			this.implementations = new ArrayList<TypeElement>();
		}
		
		/**
		 * Adds an implementation to the hierarchy
		 * 
		 * @param e
		 *            The implementing class
		 */
		public void addImplementation(TypeElement e) {
			implementations.add(e);
		}
		
		/**
		 * @return All elements involved in the hierarchy (root plus all leaves)
		 */
		public Element[] getAllElements() {
			Element[] e = new Element[1 + implementations.size()];
			e[0] = root;
			for (int k = 0; k < implementations.size(); k++) {
				e[k + 1] = implementations.get(k);
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
			TypeVariableName returnType = TypeVariableName.get(VisitorInvariants.GENERIC_RETURN_TYPE_NAME);
			String visitorSimpleName = VisitorInvariants.createVisitorClassName(root.getSimpleName().toString());
			String visitorQualifiedName = VisitorInvariants.createVisitorClassName(root.getQualifiedName().toString());
			
			// public interface RootVisitor<R> {}
			TypeSpec.Builder visitorSpec = TypeSpec.interfaceBuilder(visitorSimpleName).addModifiers(javax.lang.model.element.Modifier.PUBLIC).addTypeVariable(returnType);
			
			for (TypeElement impl : implementations) {
				// public abstract R caseImplementation(Implementation
				// implementation);
				MethodSpec visitCaseSpec = MethodSpec.methodBuilder(VisitorInvariants.createVisitorMethodName(impl.getSimpleName().toString())).addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT).addParameter(ParameterSpec.builder(TypeName.get(impl.asType()), asVar(impl.getSimpleName().toString())).build()).returns(returnType).build();
				visitorSpec.addMethod(visitCaseSpec);
			}
			JavaFileObject jfo = filer.createSourceFile(visitorQualifiedName, getAllElements());
			PackageElement pack = elementUtils.getPackageOf(root);
			JavaFile javaFile = JavaFile.builder(pack.getQualifiedName().toString(), visitorSpec.build()).build();
			Writer writer = null;
			try {
				writer = jfo.openWriter();
				javaFile.writeTo(writer);
			} finally {
				if (writer != null) writer.close();
			}
		}
	}
	
	/**
	 * @see AnnotationProcessor
	 */
	@Override public SourceVersion getSupportedSourceVersion() {
		return SourceVersion.values()[SourceVersion.values().length - 1];
	}
}
