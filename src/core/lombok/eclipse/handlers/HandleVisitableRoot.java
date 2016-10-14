package lombok.eclipse.handlers;

import lombok.ConfigurationKeys;
import lombok.core.AnnotationValues;
import lombok.core.configuration.Presence;
import lombok.eclipse.EclipseAnnotationHandler;
import lombok.eclipse.EclipseNode;
import lombok.eclipse.VisitableUtils;
import lombok.experimental.VisitableRoot;
import lombok.javac.VisitableUtils.HasArgument;
import lombok.javac.VisitableUtils.HasReturn;

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
		
		HasArgument hasArgument = annotationNode.getAst().readConfiguration(ConfigurationKeys.VISITOR_ARGUMENT) == Presence.REQUIRED ? HasArgument.YES : HasArgument.NO;
		HasReturn hasReturn = annotationNode.getAst().readConfiguration(ConfigurationKeys.VISITOR_RETURN) != Presence.ABSENT ? HasReturn.YES : HasReturn.NO;
	
		// an abstract declaration of the accept(visitor) method
		MethodDeclaration acceptVisitorMethod = VisitableUtils.ONLY.createAcceptVisitor(typeNode, annotationNode.get(), typeNode.getName(), hasArgument, hasReturn, null);
		
		EclipseHandlerUtil.injectMethod(typeNode, acceptVisitorMethod);
		
	}
	
}