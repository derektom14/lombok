package lombok.eclipse.handlers;

import lombok.core.AnnotationValues;
import lombok.eclipse.EclipseAnnotationHandler;
import lombok.eclipse.EclipseNode;
import lombok.eclipse.VisitableUtils;
import lombok.experimental.Visitable;
import lombok.visitor.VisitorInvariants;

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

@ProviderFor(EclipseAnnotationHandler.class)
public class HandleVisitable extends EclipseAnnotationHandler<Visitable> {
	
	@Override public void handle(AnnotationValues<Visitable> annotation, Annotation ast, EclipseNode annotationNode) {
		// the visited type
		EclipseNode typeNode = annotationNode.up();
		ASTNode source = annotationNode.get();
		
		int pS = source.sourceStart, pE = source.sourceEnd;
		long p = (long) pS << 32 | pE;
		

		String[] rootNames = annotation.getInstance().root();
		if (rootNames.length == 0) {
			TypeDeclaration typeDecl = (TypeDeclaration) typeNode.get();
			TypeReference visitableRoot = typeDecl.superclass;
			if (visitableRoot == null) {
				TypeReference[] superInterfaces = typeDecl.superInterfaces;
				if (superInterfaces.length > 0) {
					visitableRoot = superInterfaces[0];
				}
			}
			if (visitableRoot != null) {
				rootNames = new String[]{visitableRoot.toString()};
			}
		}
		
		for (String rootName : rootNames) {
			// method body of
			// public R accept(RootVisitor<R> visitor) {
			//   return visitor.caseX(this);
			// }
			MessageSend acceptInvocation = new MessageSend();
			Expression[] acceptArguments = new Expression[] {new ThisReference(pS, pE)};
			acceptInvocation.receiver = new SingleNameReference(VisitorInvariants.VISITOR_ARG_NAME.toCharArray(), p);
			acceptInvocation.selector = VisitorInvariants.createVisitorMethodName(typeNode.getName()).toCharArray();
			acceptInvocation.arguments = acceptArguments;
			acceptInvocation.sourceStart = pS;
			acceptInvocation.sourceEnd = pE;
			Statement statement = new ReturnStatement(acceptInvocation, pS, pE);
			MethodDeclaration acceptVisitorMethod = VisitableUtils.ONLY.createAcceptVisitor(typeNode, annotationNode.get(), rootName, new Statement[] {statement});
			
			EclipseHandlerUtil.injectMethod(typeNode, acceptVisitorMethod);
		}
	}
	
}