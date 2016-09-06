package lombok.experimental;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import lombok.visitor.VisitorProcessor;


/**
 * Makes a class a leaf of a visitor design pattern hierarchy.
 * 
 * Example:
 * <pre>
 *  &#64;Visitable(root=RootClass.class)
 * 	class ImplClass extends RootClass {
 *  }
 * </pre>
 * 
 * will become:
 * 
 * <pre>
 *  &#64;Visitable(root=RootClass.class)
 * 	class ImplClass extends RootClass {
 *    &#64;Override
 * 	  public &lt;R&gt; R accept(ClassNameVisitor&lt;R&gt; visitor) {
 *      return visitor.caseImplClass(this);
 *    }
 *  }
 * </pre>
 * 
 * Also adds the called method to the visitor interface generated by {@link VisitorProcessor}.
 * 
 * @author Derek
 * @see VisitableRoot
 * @see VisitorProcessor
 *
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface Visitable {
	String root() default "";
}
