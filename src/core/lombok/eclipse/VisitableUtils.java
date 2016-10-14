package lombok.eclipse;

import static lombok.eclipse.handlers.EclipseHandlerUtil.*;

import java.lang.reflect.Modifier;
import java.util.Arrays;

import org.eclipse.jdt.internal.compiler.ast.ASTNode;
import org.eclipse.jdt.internal.compiler.ast.Annotation;
import org.eclipse.jdt.internal.compiler.ast.Argument;
import org.eclipse.jdt.internal.compiler.ast.FalseLiteral;
import org.eclipse.jdt.internal.compiler.ast.MemberValuePair;
import org.eclipse.jdt.internal.compiler.ast.MethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.NormalAnnotation;
import org.eclipse.jdt.internal.compiler.ast.SingleTypeReference;
import org.eclipse.jdt.internal.compiler.ast.Statement;
import org.eclipse.jdt.internal.compiler.ast.StringLiteral;
import org.eclipse.jdt.internal.compiler.ast.TrueLiteral;
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.eclipse.jdt.internal.compiler.ast.TypeParameter;
import org.eclipse.jdt.internal.compiler.ast.TypeReference;
import org.eclipse.jdt.internal.compiler.lookup.ExtraCompilerModifiers;
import org.eclipse.jdt.internal.compiler.lookup.TypeConstants;

import lombok.javac.VisitableUtils.HasArgument;
import lombok.javac.VisitableUtils.HasReturn;
import lombok.visitor.VisitorInvariants;
import lombok.visitor.VisitorInvariants.ConfigReader;

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
	public MethodDeclaration createAcceptVisitor(EclipseNode typeNode, ASTNode astNode, String rootName, HasArgument hasArgument, HasReturn hasReturn, Statement[] statements) {
		int pS = astNode.sourceStart, pE = astNode.sourceEnd;
		long p = (long) pS << 32 | pE;
	
		ConfigReader reader = new VisitorInvariants.ASTConfigReader(typeNode.getAst());
		
		TypeDeclaration typeDecl = (TypeDeclaration) typeNode.get();

		// the generic return type R
		TypeParameter returnTypeParameter;
		if (hasReturn == HasReturn.YES) {
			returnTypeParameter = new TypeParameter();
			setGeneratedBy(returnTypeParameter, astNode);
			returnTypeParameter.name = VisitorInvariants.getReturnTypeVariableName(reader).toCharArray();
		} else {
			returnTypeParameter = null;
		}
		
		// the generic argument type A
		TypeParameter argTypeParameter;
		if (hasArgument == HasArgument.YES) {
			argTypeParameter = new TypeParameter();
			setGeneratedBy(argTypeParameter, astNode);
			argTypeParameter.name = VisitorInvariants.getReturnTypeVariableName(reader).toCharArray();
		} else {
			argTypeParameter = null;
		}
		
		TypeParameter[] parameters = removeNulls(new TypeParameter[0], returnTypeParameter, argTypeParameter);
		
		// the visitor argument (RootVisitor<R,A> visitor)
		TypeReference visitorType = namePlusTypeParamsToTypeReference(VisitorInvariants.createVisitorClassName(rootName).toCharArray(), parameters, p);
		Argument visitorArg = new Argument(VisitorInvariants.getVisitorArgName(reader).toCharArray(), Eclipse.pos(astNode), visitorType, 0);
		
		Argument genericArg;
		if (argTypeParameter != null) {
			genericArg = new Argument(VisitorInvariants.getArgumentVariableName(reader).toCharArray(), Eclipse.pos(astNode), new SingleTypeReference(argTypeParameter.name, Eclipse.pos(astNode)), 0);
		} else {
			genericArg = null;
		}
		
		// the method
		// public [abstract] R accept(RootVisitor<R> visitor) {}
		MethodDeclaration method = new MethodDeclaration(typeDecl.compilationResult);
		setGeneratedBy(method, astNode);
		
		NormalAnnotation acceptMethodAnnotation = makeAcceptMethodAnnotation(astNode, pS, pE, reader);
		Annotation override = (statements == null) ? null : makeMarkerAnnotation(TypeConstants.JAVA_LANG_OVERRIDE, astNode);
		method.annotations = removeNulls(new Annotation[1], acceptMethodAnnotation, override);
		method.modifiers = Modifier.PUBLIC;
		if (statements == null) method.modifiers |= (Modifier.ABSTRACT | ExtraCompilerModifiers.AccSemicolonBody);
		method.typeParameters = parameters;
		method.returnType = (returnTypeParameter == null) ? null : new SingleTypeReference(returnTypeParameter.name, 0);
		method.selector = VisitorInvariants.getVisitorAcceptMethodName(reader).toCharArray();
		method.arguments = removeNulls(new Argument[1], visitorArg, genericArg);
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

	private NormalAnnotation makeAcceptMethodAnnotation(ASTNode astNode, int pS, int pE, ConfigReader reader) {
		NormalAnnotation acceptMethodAnnotation = makeNormalAnnotation(new char[][]{"lombok".toCharArray(),"experimental".toCharArray(),"VisitorAccept".toCharArray()}, astNode);
		acceptMethodAnnotation.memberValuePairs = new MemberValuePair[]{
				pair("caseMethodPrefix", pS, pE, VisitorInvariants.getVisitorCasePrefix(reader)),
				pair("constantImplEnabled", pS, pE, VisitorInvariants.getConstantImplEnabled(reader)),
				pair("lambdaImplEnabled", pS, pE, VisitorInvariants.getLambdaImplEnabled(reader)),
				pair("lambdaBuilderEnabled", pS, pE, VisitorInvariants.getLambdaBuilderEnabled(reader)),
				pair("defaultImplEnabled", pS, pE, VisitorInvariants.getDefaultImplEnabled(reader)),
				pair("defaultBuilderEnabled", pS, pE, VisitorInvariants.getDefaultBuilderEnabled(reader)),
				};
		return acceptMethodAnnotation;
	}
	private MemberValuePair pair(String key, int pS, int pE, String value) {
		return new MemberValuePair(key.toCharArray(), pS, pE, new StringLiteral(value.toCharArray(), pS, pE, 0));
	}
	
	private MemberValuePair pair(String key, int pS, int pE, boolean value) {
		return new MemberValuePair(key.toCharArray(), pS, pE, value ? new TrueLiteral(pS,pE) : new FalseLiteral(pS,pE));
	}	
	
	
	public <E> E[] removeNulls(E[] example, E... items) {
		int countNonNulls = 0;
		for (E item : items) {
			if (item != null) {
				countNonNulls++;
			}
		}
		E[] nonNulls = (example.length == countNonNulls ? example : Arrays.copyOf(example, countNonNulls));
		int index = 0;
		for (E item : items) {
			if (item != null) {
				nonNulls[index++] = item;
			}
		}
		return nonNulls;
	}
}
