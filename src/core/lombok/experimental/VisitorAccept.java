package lombok.experimental;

public @interface VisitorAccept {
	String caseMethodPrefix();

	boolean constantImplEnabled();

	boolean lambdaImplEnabled();

	boolean lambdaBuilderEnabled();

	boolean defaultImplEnabled();

	boolean defaultBuilderEnabled();
}
