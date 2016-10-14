package lombok.eclipse.handlers;

import org.eclipse.jdt.internal.compiler.ast.ASTNode;
import org.eclipse.jdt.internal.compiler.ast.Annotation;
import org.eclipse.jdt.internal.compiler.ast.Expression;
import org.eclipse.jdt.internal.compiler.ast.MessageSend;
import org.eclipse.jdt.internal.compiler.ast.MethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.ReturnStatement;
import org.eclipse.jdt.internal.compiler.ast.SingleNameReference;
import org.eclipse.jdt.internal.compiler.ast.Statement;
import org.eclipse.jdt.internal.compiler.ast.ThisReference;
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.eclipse.jdt.internal.compiler.ast.TypeReference;
import org.mangosdk.spi.ProviderFor;

import lombok.ConfigurationKeys;
import lombok.core.AnnotationValues;
import lombok.core.configuration.Presence;
import lombok.eclipse.EclipseAnnotationHandler;
import lombok.eclipse.EclipseNode;
import lombok.eclipse.VisitableUtils;
import lombok.experimental.Visitable;
import lombok.javac.VisitableUtils.HasArgument;
import lombok.javac.VisitableUtils.HasReturn;
import lombok.visitor.VisitorInvariants;
import lombok.visitor.VisitorInvariants.ConfigReader;

@ProviderFor(EclipseAnnotationHandler.class)
public class HandleVisitable extends EclipseAnnotationHandler<Visitable> {
	
	@Override public void handle(AnnotationValues<Visitable> annotation, Annotation ast, EclipseNode annotationNode) {
		// the visited type
		EclipseNode typeNode = annotationNode.up();
		ConfigReader reader = new VisitorInvariants.ASTConfigReader(typeNode.getAst());
		ASTNode source = annotationNode.get();
		
		int pS = source.sourceStart, pE = source.sourceEnd;
		long p = (long) pS << 32 | pE;
		
		HasArgument hasArgument = annotationNode.getAst().readConfiguration(ConfigurationKeys.VISITOR_ARGUMENT) == Presence.REQUIRED ? HasArgument.YES : HasArgument.NO;
		HasReturn hasReturn = annotationNode.getAst().readConfiguration(ConfigurationKeys.VISITOR_RETURN) != Presence.ABSENT ? HasReturn.YES : HasReturn.NO;

		String[] rootNames = annotation.getInstance().root();
		if (rootNames.length == 0) {
			TypeDeclaration typeDecl = (TypeDeclaration) typeNode.get();
			TypeReference visitableRoot = typeDecl.superclass;
			if (visitableRoot == null) {
				TypeReference[] superInterfaces = typeDecl.superInterfaces;
				if (superInterfaces != null && superInterfaces.length > 0) {
					visitableRoot = superInterfaces[0];
				}
			}
			if (visitableRoot != null) {
				rootNames = new String[]{visitableRoot.toString()};
			} else {
				annotationNode.addError("A visitable class must have an abstract superclass or interface");
			}
		}
		
		for (String rootName : rootNames) {
			// method body of
			// public R accept(RootVisitor<R> visitor) {
			//   return visitor.caseX(this);
			// }
			MessageSend acceptInvocation = new MessageSend();
			Expression thisArg = new ThisReference(pS, pE);
			Expression genericArg = (hasArgument == HasArgument.YES) ? new SingleNameReference(VisitorInvariants.getArgumentVariableName(reader).toCharArray(), p) : null;
			Expression[] acceptArguments = VisitableUtils.ONLY.removeNulls(new Expression[0], thisArg, genericArg);
			acceptInvocation.receiver = new SingleNameReference(VisitorInvariants.getVisitorArgName(reader).toCharArray(), p);
			acceptInvocation.selector = (VisitorInvariants.getVisitorCasePrefix(reader) + typeNode.getName()).toCharArray();
			acceptInvocation.arguments = acceptArguments;
			acceptInvocation.sourceStart = pS;
			acceptInvocation.sourceEnd = pE;
			Statement statement = (hasReturn == HasReturn.YES) ? new ReturnStatement(acceptInvocation, pS, pE) : acceptInvocation;
			MethodDeclaration acceptVisitorMethod = VisitableUtils.ONLY.createAcceptVisitor(typeNode, annotationNode.get(), rootName, hasArgument, hasReturn, new Statement[] {statement});
			
			EclipseHandlerUtil.injectMethod(typeNode, acceptVisitorMethod);
		}
	}
	
}