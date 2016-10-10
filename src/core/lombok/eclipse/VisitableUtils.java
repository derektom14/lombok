package lombok.eclipse;

import java.lang.reflect.Modifier;

import lombok.eclipse.handlers.EclipseHandlerUtil;
import lombok.visitor.VisitorInvariants;
import lombok.visitor.VisitorInvariants.ConfigReader;

import org.eclipse.jdt.internal.compiler.ast.ASTNode;
import org.eclipse.jdt.internal.compiler.ast.Argument;
import org.eclipse.jdt.internal.compiler.ast.MethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.SingleTypeReference;
import org.eclipse.jdt.internal.compiler.ast.Statement;
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.eclipse.jdt.internal.compiler.ast.TypeParameter;
import org.eclipse.jdt.internal.compiler.ast.TypeReference;
import org.eclipse.jdt.internal.compiler.lookup.ExtraCompilerModifiers;

/**
 * Utility class for Eclipse creation of visitor-related methods
 * 
 * @author Derek
 *
 */
public class VisitableUtils {
	private VisitableUtils() {
	}
	
	public static VisitableUtils ONLY = new VisitableUtils();
	
	
	/**
	 * Generates the accept(visitor) method for a class
	 * @param typeNode Type to add the method to
	 * @param astNode The source node
	 * @param rootName The name of the root visitable class
	 * @param statements The statements in the method (null if abstract)
	 * @return The corresponding declaration of the method
	 */
	public MethodDeclaration createAcceptVisitor(EclipseNode typeNode, ASTNode astNode, String rootName, Statement[] statements) {
		int pS = astNode.sourceStart, pE = astNode.sourceEnd;
		long p = (long) pS << 32 | pE;
	
		ConfigReader reader = new VisitorInvariants.ASTConfigReader(typeNode.getAst());
		
		TypeDeclaration typeDecl = (TypeDeclaration) typeNode.get();
		
		// the generic return type R
		TypeParameter returnTypeParameter = new TypeParameter();
		EclipseHandlerUtil.setGeneratedBy(returnTypeParameter, astNode);
		returnTypeParameter.name = VisitorInvariants.getReturnTypeVariableName(reader).toCharArray();
		
		TypeParameter[] parameters = new TypeParameter[] {returnTypeParameter};
		
		// the visitor argument (RootVisitor<R> visitor)
		TypeReference visitorType = EclipseHandlerUtil.namePlusTypeParamsToTypeReference(VisitorInvariants.createVisitorClassName(rootName).toCharArray(), parameters, p);
		Argument visitorArg = new Argument(VisitorInvariants.getVisitorArgName(reader).toCharArray(), Eclipse.pos(astNode), visitorType, 0);
		
		// the method
		// public [abstract] R accept(RootVisitor<R> visitor) {}
		MethodDeclaration method = new MethodDeclaration(typeDecl.compilationResult);
		EclipseHandlerUtil.setGeneratedBy(method, astNode);
		method.annotations = null;
		method.modifiers = Modifier.PUBLIC;
		if (statements == null) method.modifiers |= (Modifier.ABSTRACT | ExtraCompilerModifiers.AccSemicolonBody);
		method.typeParameters = parameters;
		method.returnType = new SingleTypeReference(returnTypeParameter.name, 0);
		method.selector = VisitorInvariants.getVisitorAcceptMethodName(reader).toCharArray();
		method.arguments = new Argument[] {visitorArg};
		method.binding = null;
		method.thrownExceptions = null;
		method.bits |= Eclipse.ECLIPSE_DO_NOT_TOUCH_FLAG;
		
		returnTypeParameter.sourceStart = visitorType.sourceStart = visitorArg.sourceStart = method.sourceStart = method.returnType.sourceStart = method.declarationSourceEnd = method.sourceStart = pS;
		returnTypeParameter.sourceEnd = visitorType.sourceEnd = visitorArg.sourceEnd = method.sourceStart = method.returnType.sourceEnd = method.declarationSourceEnd = method.sourceEnd = pE;
		
		if (statements != null) {
			method.statements = statements;
			method.bodyStart = astNode.sourceStart;
			method.bodyEnd = astNode.sourceEnd;
		}
		return method;
	}
}
