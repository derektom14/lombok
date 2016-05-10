package lombok.launch;

import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Completion;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/**
 * Delegates to a processor loaded by the shadow class loader.
 * Originally written as {@link AnnotationProcessorHider}, modified to hide any processor
 * @author Derek
 *
 */
abstract class DelegateProcessor extends AbstractProcessor {
	private final AbstractProcessor instance = createWrappedInstance();
	
	@Override public Set<String> getSupportedOptions() {
		return instance.getSupportedOptions();
	}

	@Override public Set<String> getSupportedAnnotationTypes() {
		return instance.getSupportedAnnotationTypes();
	}
	
	@Override public SourceVersion getSupportedSourceVersion() {
		return instance.getSupportedSourceVersion();
	}
	
	@Override public void init(ProcessingEnvironment processingEnv) {
		instance.init(processingEnv);
		super.init(processingEnv);
	}
	
	@Override public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		return instance.process(annotations, roundEnv);
	}
	
	@Override public Iterable<? extends Completion> getCompletions(Element element, AnnotationMirror annotation, ExecutableElement member, String userText) {
		return instance.getCompletions(element, annotation, member, userText);
	}
	
	private AbstractProcessor createWrappedInstance() {
		ClassLoader cl = Main.createShadowClassLoader();
		try {
			Class<?> mc = cl.loadClass(getLoadedClassName());
			return (AbstractProcessor) mc.newInstance();
		} catch (Throwable t) {
			if (t instanceof Error) throw (Error) t;
			if (t instanceof RuntimeException) throw (RuntimeException) t;
			throw new RuntimeException(t);
		}
	}

	abstract String getLoadedClassName();
}
