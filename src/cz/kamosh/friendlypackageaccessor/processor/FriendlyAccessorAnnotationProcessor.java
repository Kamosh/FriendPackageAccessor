package cz.kamosh.friendlypackageaccessor.processor;

import cz.kamosh.friendlypackageaccessor.annotation.FriendlyAccessor;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleElementVisitor6;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

@SupportedAnnotationTypes("cz.kamosh.friendlypackageaccessor.annotation.FriendlyAccessor")
public class FriendlyAccessorAnnotationProcessor extends AbstractProcessor {

    private final static String NEW_LINE = System.getProperty("line.separator"); // NORES

//    private Map<String, Collection<Element>> collectAccessorsAndMethods(Set<Element> friendlyAccessorMethods) {
//        
//    }
//  
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        final Filer filer = processingEnv.getFiler();

        //FqAccessorName->methodsToAccess
        Map<String, Collection<Element>> accessorsEntries
                = collectAccessorsAndMethods(roundEnv.getElementsAnnotatedWith(FriendlyAccessor.class));

        for (final Map.Entry<String, Collection<Element>> accessorEntry : accessorsEntries.entrySet()) {
            final ClassDescription accessorFileDescription = parseClassName(accessorEntry.getKey());

            final StringBuilder accessorImplFileText = new StringBuilder();
            final TypeMirror enclosingType = findEnclosingType(accessorEntry.getValue().iterator().next());
            // TODO Check that there is not already existing ...Impl file
            String fqEnclosingType = enclosingType.toString();
            final ClassDescription accessedFileDescription = parseClassName(fqEnclosingType);
            accessorImplFileText.append(generateAccessorImplSignature(accessedFileDescription, accessorFileDescription));

            final StringBuilder accessorFileText = new StringBuilder();
            accessorFileText.append(generateAccessorSignature(accessorEntry.getKey()));

            for (Element methodToAccess : accessorEntry.getValue()) {

                String methodName = methodToAccess.getSimpleName().toString();

                SimpleElementVisitor6 visitor = new SimpleElementVisitor6() {
                    @Override
                    public Object visitExecutable(ExecutableElement e, Object p) {
                        StringBuilder commonCode = new StringBuilder();
                        accessorFileText.append("abstract ");
                        commonCode.append(e.getReturnType().toString());
                        commonCode.append(" ");
                        final String accessedMethodName = e.getSimpleName().toString();
                        commonCode.append(accessedMethodName);
                        commonCode.append("(");
                        String comma = "";
                        String accessedClassParamName = null;
                        if (enclosingType != null) {
                            commonCode.append(enclosingType.toString());
                            commonCode.append(" ");
                            accessedClassParamName = firstLowerCase(accessedFileDescription.className);
                            commonCode.append(accessedClassParamName);
                            comma = ", ";
                        }
                        for (VariableElement variableElement : e.getParameters()) {
                            commonCode.append(comma);
                            TypeMirror paramType = variableElement.asType();
                            commonCode.append(paramType.toString());
                            commonCode.append(" ");
                            commonCode.append(variableElement.getSimpleName().toString());
                            comma = ", ";
                        }
                        commonCode.append(")");

                        accessorFileText.append(commonCode);
                        accessorFileText.append(";");
                        accessorFileText.append(NEW_LINE);

                        accessorImplFileText.append(commonCode);
                        accessorImplFileText.append("{");
                        accessorImplFileText.append(NEW_LINE);
                        accessorImplFileText.append(NEW_LINE);

                        if (!"void".equals(e.getReturnType().toString())) {
                            accessorImplFileText.append("return ");
                        }
                        accessorImplFileText.append(accessedClassParamName);
                        accessorImplFileText.append("." + accessedMethodName);
                        accessorImplFileText.append("(");
                        comma = "";
                        for (VariableElement variableElement : e.getParameters()) {
                            accessorImplFileText.append(comma);
                            accessorImplFileText.append(variableElement.getSimpleName().toString());
                            comma = ", ";
                        }
                        accessorImplFileText.append(");");
                        accessorImplFileText.append(NEW_LINE);
                        accessorImplFileText.append("}");
                        accessorImplFileText.append(NEW_LINE);

//                        print(processingEnv, accessorFileText.toString());
                        return super.visitExecutable(e, p);
                    }

                };
                methodToAccess.accept(visitor, this);
            }
            accessorFileText.append("}");
            // TODO Think about originating elements
            createFile(processingEnv.getFiler(), accessorEntry.getKey(), accessorFileText, null);
            String fqAccessorImpl = accessedFileDescription.packageName != null ? accessedFileDescription.packageName + "." : "";
            fqAccessorImpl += accessedFileDescription.className + "Impl";
            accessorImplFileText.append("}");
            createFile(processingEnv.getFiler(), fqAccessorImpl, accessorImplFileText, null);
        }
        return true;
    }

    /**
     *
     * @param elementsAnnotatedWithFriendlyAccessor
     * @return Map FqAccessorName->methodsToAccess
     */
    private Map<String, Collection<Element>> collectAccessorsAndMethods(Set<? extends Element> elementsAnnotatedWithFriendlyAccessor) {
        Map<String, Collection<Element>> res = new HashMap<>();
        for (Element e : elementsAnnotatedWithFriendlyAccessor) {
            final FriendlyAccessor friendlyAccessorAnot = e.getAnnotation(FriendlyAccessor.class);
            String fqAccessor = friendlyAccessorAnot.friendPackage() + "." + friendlyAccessorAnot.accessor();
            Collection<Element> accessedMethods = res.get(fqAccessor);
            if (accessedMethods == null) {
                accessedMethods = new ArrayList<Element>();
                res.put(fqAccessor, accessedMethods);
            }
            accessedMethods.add(e);
        }
        return res;
    }

    private TypeMirror findEnclosingType(Element methodToAccess) {
        Element enclosingElement = methodToAccess;
        while (enclosingElement != null) {
            if (enclosingElement.getKind() == ElementKind.CLASS) {
                return enclosingElement.asType();
            }
            enclosingElement = enclosingElement.getEnclosingElement();
        }
        return null;
    }

    private static String firstLowerCase(String string) {
        return Character.toLowerCase(string.charAt(0)) + (string.length() > 1 ? string.substring(1) : "");
    }

    private StringBuilder generateAccessorSignature(String fqAccessorName) {
        StringBuilder res = new StringBuilder();

        final int classNameStartIndex = fqAccessorName.lastIndexOf('.') + 1;
        // Package
        if (classNameStartIndex != 0) {
            res.append("package ");
            res.append(fqAccessorName.substring(0, classNameStartIndex - 1));
            res.append(";"); // NORES
            res.append(NEW_LINE);
        }
        // Class name
        res.append("public abstract class "); // NORES
        res.append(fqAccessorName.substring(classNameStartIndex));
        res.append(" {");
        res.append(NEW_LINE);
        final String basicAccessorCode
                = BASIC_ACCESSOR_CODE.replaceAll("XXX", fqAccessorName.substring(fqAccessorName.lastIndexOf('.') + 1));
        res.append(basicAccessorCode);

        return res;
    }

    void print(ProcessingEnvironment processingEnv, String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, message);
    }

    private void createFile(Filer filer, String fqAccessorName, StringBuilder fileText, Element... elements) {
        try {
            final JavaFileObject createdFile = filer.createSourceFile(fqAccessorName, elements);
            final Writer fileWriter = createdFile.openWriter();
            fileWriter.append(fileText);
            fileWriter.flush();
            fileWriter.close();
        } catch (IOException ex) {
            Logger.getLogger(FriendlyAccessorAnnotationProcessor.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private final static String BASIC_ACCESSOR_IMPL_CODE
            = "    static {\n"
            + "      XXXAccessor.setDefault(new XXXAccessorImpl());\n"
            + "    }";

    private final static String BASIC_ACCESSOR_CODE
            = "    private static volatile XXX DEFAULT;\n"
            + "    public static APIAccessor getDefault() {\n"
            + "        APIAccessor a = DEFAULT;\n"
            + "        if (a == null) {\n"
            + "            throw new IllegalStateException(\"Something is wrong: \" + a);\n"
            + "        }\n"
            + "        return a;\n"
            + "    }\n"
            + " \n"
            + "    public static void setDefault(XXX accessor) {\n"
            + "        if (DEFAULT != null) {\n"
            + "            throw new IllegalStateException();\n"
            + "        }\n"
            + "        DEFAULT = accessor;\n"
            + "    }\n"
            + " \n"
            + "    protected XXX() {\n"
            + "    }\n";

    private StringBuilder generateAccessorImplSignature(ClassDescription accessedFileDescription, ClassDescription accessorFileDescription) {
        // Get package of the accessed type        
        StringBuilder sb = new StringBuilder();
        if (accessedFileDescription.packageName != null) {
            sb.append("package ");
            sb.append(accessedFileDescription.packageName);
            sb.append(";");
            sb.append(NEW_LINE);
        }
        // TODO Check that use has not already created file with this name
        final String accessorImplClassName = accessedFileDescription.className + "Impl";
        sb.append("class ");
        sb.append(accessorImplClassName);
        sb.append(" extends ");
        if (accessorFileDescription.packageName != null) {
            sb.append(accessorFileDescription.packageName);
            sb.append(".");            
        }
        sb.append(accessorFileDescription.className);                
        

        sb.append(" {");        
        sb.append(NEW_LINE);
        
        String replacedAccessorName = BASIC_ACCESSOR_IMPL_CODE.replaceAll("XXXAccessor", accessorFileDescription.fqName);
        String replacedAccessorImplName = replacedAccessorName.replaceAll("XXXAccessorImpl", accessorImplClassName);
        sb.append(replacedAccessorImplName);
        sb.append(NEW_LINE);

        return sb;
    }

    private static class ClassDescription {

        final String packageName;
        final String className;
        final String fqName;

        private ClassDescription(String packageName, String className) {
            this.packageName = packageName;
            this.className = className;
            this.fqName = (packageName == null ? "" : packageName + ".") + className;
        }
    }

    private static ClassDescription parseClassName(String fqClassName) {

        final int classNameStartIndex = fqClassName.lastIndexOf('.') + 1;
        String packageName = null;
        String className = null;
        // Package
        if (classNameStartIndex != 0) {
            packageName = fqClassName.substring(0, classNameStartIndex - 1);
        }
        // Class name
        className = fqClassName.substring(classNameStartIndex);
        return new ClassDescription(packageName, className);
    }
}

//class TestStringBuilder {
//    void test(String s) {
//        StringBuilder sb = new StringBuilder();
//        sb.append(generateSomeString(s));
//    }
//}
