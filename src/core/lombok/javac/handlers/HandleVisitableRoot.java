package lombok.javac.handlers;

import lombok.ConfigurationKeys;
import lombok.core.AnnotationValues;
import lombok.core.configuration.Presence;
import lombok.experimental.VisitableRoot;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import lombok.javac.VisitableUtils;
import lombok.javac.VisitableUtils.HasArgument;
import lombok.javac.VisitableUtils.HasReturn;

import org.mangosdk.spi.ProviderFor;

import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;

/**
 * Handles the {@link VisitableRoot} annotation
 * @see VisitableRoot
 * @author Derek
 *
 */
@ProviderFor(JavacAnnotationHandler.class)
@SuppressWarnings("restriction")
public class HandleVisitableRoot extends JavacAnnotationHandler<VisitableRoot> {
	
	@Override
	public void handle(AnnotationValues<VisitableRoot> annotation, JCAnnotation ast, JavacNode annotationNode) {
		JavacNode typeNode = annotationNode.up(); // the annotated class
		JCClassDecl type = (JCClassDecl) typeNode.get();
		
		HasArgument hasArgument = annotationNode.getAst().readConfiguration(ConfigurationKeys.VISITOR_ARGUMENT) == Presence.REQUIRED ? HasArgument.YES : HasArgument.NO;
		// create the abstract accept method
		JCMethodDecl acceptVisitorMethod = VisitableUtils.ONLY.createAcceptVisitor(typeNode, type.name.toString(), hasArgument, HasReturn.YES, null);
		
		JavacHandlerUtil.injectMethod(typeNode, acceptVisitorMethod);
	}
	
	
}