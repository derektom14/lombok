package lombok.javac.handlers;

import java.util.Arrays;

import org.mangosdk.spi.ProviderFor;

import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.SimpleTreeVisitor;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCTypeApply;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

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
import lombok.visitor.VisitorInvariants.ConfigReader;

/**
 * Handles the {@link Visitable} annotation
 * 
 * @see VisitableRoot
 * @author Derek
 *
 */
@ProviderFor(JavacAnnotationHandler.class) @SuppressWarnings("restriction") public class HandleVisitable extends JavacAnnotationHandler<Visitable> {
	
	@Override public void handle(AnnotationValues<Visitable> annotation, JCAnnotation ast, JavacNode annotationNode) {
		final JavacNode typeNode = annotationNode.up(); // the annotated class
		ConfigReader reader = new VisitorInvariants.ASTConfigReader(typeNode.getAst());
		
		JavacTreeMaker treeMaker = typeNode.getTreeMaker();
		JCClassDecl type = (JCClassDecl) typeNode.get();

		String[] rootNames = annotation.getInstance().root();
		
		List<Name> parameters = List.nil();
		if (rootNames.length == 0) {
			JCTree extendsType = JavacHandlerUtil.getExtendsClause(type);
			if (extendsType != null) {
				typeNode.addWarning("Extends type: " + extendsType + ", " + extendsType.getClass());
				parameters = extendsType.accept(new SimpleTreeVisitor<List<Name>,Void>(List.<Name>nil()) {

					@Override public List<Name> visitParameterizedType(ParameterizedTypeTree node, Void p) {
						List<Name> names = List.nil(); 
						for (Tree tree : node.getTypeArguments()) {
							names = names.append(((JCIdent) tree).getName());
						}
						return names;
					}
				}, null);
				if (extendsType instanceof JCTypeApply) {
					rootNames = new String[]{((JCTypeApply) extendsType).getType().toString()};
				} else {
					rootNames = new String[]{extendsType.toString()};
				}
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
			HasReturn hasReturn = annotationNode.getAst().readConfiguration(ConfigurationKeys.VISITOR_RETURN) != Presence.ABSENT ? HasReturn.YES : HasReturn.NO;
			// in the body, call the visitor's caseThis method
			JCExpression caseMethod = JavacHandlerUtil.chainDots(typeNode, VisitorInvariants.getVisitorArgName(reader), VisitorInvariants.getVisitorCasePrefix(reader) + type.name.toString());
			List<JCExpression> caseArgs = List.<JCExpression>of(treeMaker.Ident(typeNode.toName("this")));
			if (hasArgument == HasArgument.YES) {
				caseArgs = caseArgs.append(treeMaker.Ident(typeNode.toName(VisitorInvariants.getArgumentVariableName(reader))));
			}
			JCMethodInvocation caseInvocation = treeMaker.Apply(List.<JCExpression>nil(), caseMethod, caseArgs);
			JCStatement statement = (hasReturn == HasReturn.YES) ? treeMaker.Return(caseInvocation) : treeMaker.Exec(caseInvocation);
			JCBlock methodBody = treeMaker.Block(0, List.<JCStatement>of(statement));
		
			
			JCMethodDecl acceptVisitorMethod = VisitableUtils.ONLY.createAcceptVisitor(typeNode, rootName, parameters, hasArgument, hasReturn, methodBody);
			
			JavacHandlerUtil.injectMethod(typeNode, acceptVisitorMethod);
		}
	}
	
}