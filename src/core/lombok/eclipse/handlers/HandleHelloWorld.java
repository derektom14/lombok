package lombok.eclipse.handlers;
import lombok.HelloWorld;
import lombok.core.AnnotationValues;
import lombok.eclipse.Eclipse;
import lombok.eclipse.EclipseAnnotationHandler;
import lombok.eclipse.EclipseNode;

import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.internal.compiler.ast.ASTNode;
import org.eclipse.jdt.internal.compiler.ast.Annotation;
import org.eclipse.jdt.internal.compiler.ast.Expression;
import org.eclipse.jdt.internal.compiler.ast.MessageSend;
import org.eclipse.jdt.internal.compiler.ast.MethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.NameReference;
import org.eclipse.jdt.internal.compiler.ast.SingleTypeReference;
import org.eclipse.jdt.internal.compiler.ast.Statement;
import org.eclipse.jdt.internal.compiler.ast.StringLiteral;
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.eclipse.jdt.internal.compiler.lookup.TypeBinding;
import org.mangosdk.spi.ProviderFor;

@ProviderFor(EclipseAnnotationHandler.class)
public class HandleHelloWorld extends EclipseAnnotationHandler<HelloWorld> {

	@Override
	 public void handle(AnnotationValues<HelloWorld> annotation, Annotation ast,
	   EclipseNode annotationNode) {
	  EclipseNode typeNode = annotationNode.up();

	  MethodDeclaration helloWorldMethod = 
	   createHelloWorld(typeNode, annotationNode, annotationNode.get(), ast);
	  
	  EclipseHandlerUtil.injectMethod(typeNode, helloWorldMethod);
	  
	 }

	private MethodDeclaration createHelloWorld(EclipseNode typeNode, EclipseNode errorNode, ASTNode astNode, Annotation source) {
		  TypeDeclaration typeDecl = (TypeDeclaration) typeNode.get();

		  MethodDeclaration method = new MethodDeclaration(typeDecl.compilationResult);
		  EclipseHandlerUtil.setGeneratedBy(method, astNode);
		  method.annotations = null;
		  method.modifiers = Modifier.PUBLIC;
		  method.typeParameters = null;
		  method.returnType = new SingleTypeReference(TypeBinding.VOID.simpleName, 0);
		  method.selector = "helloWorld".toCharArray();
		  method.arguments = null;
		  method.binding = null;
		  method.thrownExceptions = null;
		  method.bits |= Eclipse.ECLIPSE_DO_NOT_TOUCH_FLAG;
		  
		  NameReference systemOutReference = EclipseHandlerUtil.createNameReference("System.out", source);
		  Expression [] printlnArguments = new Expression[] { 
		   new StringLiteral("Hello World".toCharArray(), astNode.sourceStart, astNode.sourceEnd, 0)
		  };
		  
		  MessageSend printlnInvocation = new MessageSend();
		  printlnInvocation.arguments = printlnArguments;
		  printlnInvocation.receiver = systemOutReference;
		  printlnInvocation.selector = "println".toCharArray();
		  EclipseHandlerUtil.setGeneratedBy(printlnInvocation, source);
		  
		  method.bodyStart = method.declarationSourceStart = method.sourceStart = astNode.sourceStart;
		  method.bodyEnd = method.declarationSourceEnd = method.sourceEnd = astNode.sourceEnd;
		  method.statements = new Statement[] { printlnInvocation };
		  return method;
		 }

}