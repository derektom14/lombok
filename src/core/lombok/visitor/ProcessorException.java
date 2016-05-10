package lombok.visitor;

import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic.Kind;

public class ProcessorException extends RuntimeException {

	private Element e;
	private String message;

	public ProcessorException(Element e, String message) {
		this.e = e;
		this.message = message;
	}

	public boolean printWarning(Messager messager) {
		messager.printMessage(Kind.ERROR, message, e);
		return true;
	}
	
}
