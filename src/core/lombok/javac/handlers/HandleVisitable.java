package lombok.javac.handlers;

import java.util.Arrays;

import org.mangosdk.spi.ProviderFor;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.util.List;

import lombok.ConfigurationKeys;
import lombok.core.AnnotationValues;
import lombok.core.configuration.Presence;
import lombok.experimental.Visitable;
import lombok.experimental.VisitableRoot;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import lombok.javac.JavacTreeMaker;
import lombok.javac.VisitableUtils;
import lombok.javac.VisitableUtils.HasArgument;
import lombok.javac.VisitableUtils.HasReturn;
import lombok.visitor.VisitorInvariants;

/**
 * Handles the {@link Visitable} annotation
 * 
 * @see VisitableRoot
 * @author Derek
 *
 */
@ProviderFor(JavacAnnotationHandler.class) @SuppressWarnings("restriction") public class HandleVisitable extends JavacAnnotationHandler<Visitable> {
	
	@Override public void handle(AnnotationValues<Visitable> annotation, JCAnnotation ast, JavacNode annotationNode) {
		JavacNode typeNode = annotationNode.up(); // the annotated class
		JavacTreeMaker treeMaker = typeNode.getTreeMaker();
		JCClassDecl type = (JCClassDecl) typeNode.get();

		String[] rootNames = annotation.getInstance().root();
		if (rootNames.length == 0) {
			JCTree extendsType = JavacHandlerUtil.getExtendsClause(type);
			if (extendsType != null) {
				rootNames = new String[]{extendsType.toString()};
			} else {
				List<JCTree> candidates = JavacHandlerUtil.getImplementsClause(type);
				if (candidates.size() > 0) {
					rootNames = new String[]{candidates.get(0).toString()};
				} else {
					annotationNode.addError("Cannot find a vistable hierarchy root");
					return;
				}
			}
		}
		annotationNode.addWarning("Root: " + Arrays.toString(rootNames));
		
		for (String rootName : rootNames) {
			HasArgument hasArgument = annotationNode.getAst().readConfiguration(ConfigurationKeys.VISITOR_ARGUMENT) == Presence.REQUIRED ? HasArgument.YES : HasArgument.NO;
			// in the body, call the visitor's caseThis method
			JCExpression caseMethod = JavacHandlerUtil.chainDots(typeNode, VisitorInvariants.VISITOR_ARG_NAME, VisitorInvariants.createVisitorMethodName(type.name.toString()));
			List<JCExpression> caseArgs = List.<JCExpression>of(treeMaker.Ident(typeNode.toName("this")));
			caseArgs = caseArgs.append(treeMaker.Ident(typeNode.toName("arg")));
			JCMethodInvocation caseInvocation = treeMaker.Apply(List.<JCExpression>nil(), caseMethod, caseArgs);
			JCBlock methodBody = treeMaker.Block(0, List.<JCStatement>of(treeMaker.Return(caseInvocation)));
		
			
			JCMethodDecl acceptVisitorMethod = VisitableUtils.ONLY.createAcceptVisitor(typeNode, rootName, hasArgument, HasReturn.YES, methodBody);
			
			JavacHandlerUtil.injectMethod(typeNode, acceptVisitorMethod);
		}
	}
	
}