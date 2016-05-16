package lombok.eclipse.handlers;

import lombok.core.AnnotationValues;
import lombok.eclipse.EclipseAnnotationHandler;
import lombok.eclipse.EclipseNode;
import lombok.eclipse.VisitableUtils;
import lombok.experimental.VisitableRoot;

import org.eclipse.jdt.internal.compiler.ast.Annotation;
import org.eclipse.jdt.internal.compiler.ast.MethodDeclaration;
import org.mangosdk.spi.ProviderFor;

/**
 * Handles the {@link VisitableRoot} annotation
 * @see VisitableRoot
 * @author Derek
 *
 */
@ProviderFor(EclipseAnnotationHandler.class) public class HandleVisitableRoot extends EclipseAnnotationHandler<VisitableRoot> {
	
	@Override public void handle(AnnotationValues<VisitableRoot> annotation, Annotation ast, EclipseNode annotationNode) {
		EclipseNode typeNode = annotationNode.up(); // the annotated class
		
		// an abstract declaration of the accept(visitor) method
		MethodDeclaration acceptVisitorMethod = VisitableUtils.ONLY.createAcceptVisitor(typeNode, annotationNode.get(), typeNode.getName(), null);
		
		EclipseHandlerUtil.injectMethod(typeNode, acceptVisitorMethod);
		
	}
	
}