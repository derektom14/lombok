package lombok.experimental;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import lombok.visitor.VisitorProcessor;

/**
 * Makes a class or interface the root of a visitor design pattern hierarchy.
 * 
 * Example:
 * <pre>
 *  &#64;VisitableRoot
 * 	abstract class RootClass {
 *  }
 * </pre>
 * 
 * will become:
 * 
 * <pre>
 *  &#64;VisitableRoot
 * 	class RootClass {
 *    &#64;Override
 * 	  public abstract &lt;R&gt; R accept(ClassNameVisitor&lt;R&gt; visitor;
 *  }
 * </pre>
 * 
 * 
 * Also creates a corresponding visitor interface via the {@link VisitorProcessor}.
 * 
 * @author Derek
 * @see Visitable
 * @see VisitorProcessor
 *
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface VisitableRoot {
	Class<?>[] order() default {};
	
	enum Builder {
		NONE, IMMUTABLE, MUTABLE;
	}
}
