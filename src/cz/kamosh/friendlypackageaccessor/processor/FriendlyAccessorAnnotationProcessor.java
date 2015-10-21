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
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.util.SimpleElementVisitor6;
import javax.lang.model.util.TypeKindVisitor6;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

@SupportedAnnotationTypes("cz.kamosh.friendlypackageaccessor.annotation.FriendlyAccessor")
public class FriendlyAccessorAnnotationProcessor extends AbstractProcessor {

    private final static String NEW_LINE = System.getProperty("line.separator"); // NORES

    private enum TypeOfAccessedMethod {

        STATIC,
        CONSTRUCTOR,
        INSTANCE
    }

    private Filer filer;
    private Messager messager;

    @Override
    public void init(ProcessingEnvironment env) {
        filer = env.getFiler();
        messager = env.getMessager();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        //FqAccessorName->methodsToAccess
        Map<String, Collection<Element>> accessorsEntries
                = collectAccessorsAndMethods(roundEnv.getElementsAnnotatedWith(FriendlyAccessor.class));

        if (!areAnnotatedElementsValid(accessorsEntries)) {
            return false;
        }

        for (final Map.Entry<String, Collection<Element>> accessorEntry : accessorsEntries.entrySet()) {
            final ClassDescription accessorFileDescription = parseClassName(accessorEntry.getKey());

            final StringBuilder accessorImplFileText = new StringBuilder();
            ClassDescription accessedFileDescription = deriveAccessedFileDescription(accessorEntry);
            final ClassDescription accessorImplFileDescription = new ClassDescription(accessedFileDescription.packageName, accessorFileDescription.className + "Impl");
            accessorImplFileText.append(generateAccessorImplSignature(accessedFileDescription, accessorImplFileDescription, accessorFileDescription));

            final StringBuilder accessorFileText = new StringBuilder();
            accessorFileText.append(generateAccessorSignature(accessorEntry.getKey(), accessorImplFileDescription.fqName));

            for (final Element methodToAccess : accessorEntry.getValue()) {
                final TypeMirror enclosingType = findEnclosingType(methodToAccess);
                final TypeOfAccessedMethod typeOfAccessedMethod = findTypeOfAccessedMethod(methodToAccess);
                final String methodName = methodToAccess.getSimpleName().toString();
                final String accessedTypeName = findTypeNameWithTrimmedPackage(enclosingType);

                SimpleElementVisitor6 visitor = new SimpleElementVisitor6() {
                    @Override
                    public Object visitExecutable(ExecutableElement e, Object p) {
                        StringBuilder commonCode = new StringBuilder();
                        accessorFileText.append("protected abstract ");
                        accessorImplFileText.append("protected ");
                        String accessedMethodName;
                        if (typeOfAccessedMethod == TypeOfAccessedMethod.CONSTRUCTOR) {
                            commonCode.append(enclosingType.toString());
                            accessedMethodName = accessedTypeName.replaceAll("\\.", "_");
                        } else {
                            commonCode.append(e.getReturnType().toString());
                            accessedMethodName = e.getSimpleName().toString();
                        }
                        commonCode.append(" ");

                        commonCode.append(accessedMethodName);
                        commonCode.append("(");
                        String comma = "";
                        String accessedClassParamName = null;
                        
                        switch (typeOfAccessedMethod) {
                            case CONSTRUCTOR:
                            case STATIC:
                                commonCode.append("Class<");
                                break;
                        }
                        commonCode.append(enclosingType.toString());
                        switch (typeOfAccessedMethod) {
                            case CONSTRUCTOR:
                            case STATIC:
                                commonCode.append(">");
                                break;
                        }
                        commonCode.append(" ");
                        accessedClassParamName = firstLowerCase(accessedTypeName.replaceAll("\\.", ""));
                        commonCode.append(accessedClassParamName);
                        comma = ", ";
                        
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

                        switch (typeOfAccessedMethod) {
                            case CONSTRUCTOR:
                                accessorImplFileText.append("return new ");
                                accessorImplFileText.append(enclosingType.toString());
                                break;
                            case STATIC:
                                if (!"void".equals(e.getReturnType().toString())) {
                                    accessorImplFileText.append("return ");
                                }
                                accessorImplFileText.append(enclosingType.toString());
                                accessorImplFileText.append(".");
                                accessorImplFileText.append(accessedMethodName);
                                break;
                            case INSTANCE:
                                if (!"void".equals(e.getReturnType().toString())) {
                                    accessorImplFileText.append("return ");
                                }
                                accessorImplFileText.append(accessedClassParamName);
                                accessorImplFileText.append(".");
                                accessorImplFileText.append(accessedMethodName);
                                break;
                        }

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

                        return super.visitExecutable(e, p);
                    }

                };
                methodToAccess.accept(visitor, this);
            }
            accessorFileText.append("}");
            accessorImplFileText.append("}");
            
            createFile(accessorEntry.getKey(), accessorFileText,
                    accessorEntry.getValue().toArray(new Element[0]));
            createFile(accessorImplFileDescription.fqName, accessorImplFileText,
                    accessorEntry.getValue().toArray(new Element[0]));
        }
        return true;
    }

    private ClassDescription deriveAccessedFileDescription(final Map.Entry<String, Collection<Element>> accessorEntry) {
        final TypeMirror enclosingType = findEnclosingType(accessorEntry.getValue().iterator().next());
        // TODO Check that there is not already existing ...Impl file
        String fqEnclosingType = enclosingType.toString();
        final ClassDescription accessedFileDescription = parseClassName(fqEnclosingType);
        return accessedFileDescription;
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

    private StringBuilder generateAccessorSignature(String fqAccessorName, String fqAccessorImplName) {
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
        String basicAccessorCode
                = BASIC_ACCESSOR_CODE.replaceAll("XXXAccessor", fqAccessorName.substring(fqAccessorName.lastIndexOf('.') + 1));
        // TODO specify XXXImpl replace value
        basicAccessorCode
                = basicAccessorCode.replaceAll("XXXImpl", fqAccessorImplName);
        res.append(basicAccessorCode);

        return res;
    }

    private void print(Diagnostic.Kind messageKind, String message) {
        messager.printMessage(messageKind, message);
    }

    private void createFile(String fqAccessorName, StringBuilder fileText, Element... elements) {
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
            + "      XXXAccessor.setDefault(new XXXImpl());\n"
            + "    }";

    private final static String BASIC_ACCESSOR_CODE
            = "    private static volatile XXXAccessor DEFAULT;\n"
            + "    public static XXXAccessor getDefault() {\n"
            + "        APIAccessor a = DEFAULT;\n"
            + "        if (a == null) {\n"
            + "            throw new IllegalStateException(\"Something is wrong: \" + a);\n"
            + "        }\n"
            + "        return a;\n"
            + "    }\n"
            + " \n"
            + "    public static void setDefault(XXXAccessor accessor) {\n"
            + "        if (DEFAULT != null) {\n"
            + "            throw new IllegalStateException();\n"
            + "        }\n"
            + "        DEFAULT = accessor;\n"
            + "    }\n"
            + " \n"
            + "    protected XXXAccessor() {\n"
            + "    }\n"
            + "  \n"
            + "private static final Class<?> INIT_API_CLASS = loadClass(\n"
            + "    \"XXXImpl\"\n"
            + ");\n"
            + "\n"
            + "private static Class<?> loadClass(String name) {\n"
            + "  try {\n"
            + "    return Class.forName(\n"
            + "   name, true, XXXAccessor.class.getClassLoader()\n"
            + "  );\n"
            + "  } catch (Exception ex) {\n"
            + "    throw new RuntimeException(ex);\n"
            + "  }\n"
            + "}\n";

    private StringBuilder generateAccessorImplSignature(
            ClassDescription accessedFileDescription,
            ClassDescription accessorImplFileDescription,
            ClassDescription accessorFileDescription) {
        // Get package of the accessed type        
        StringBuilder sb = new StringBuilder();
        if (accessedFileDescription.packageName != null) {
            sb.append("package ");
            sb.append(accessorImplFileDescription.packageName);
            sb.append(";");
            sb.append(NEW_LINE);
        }
        // TODO Check that use has not already created file with this name
        final String accessorImplClassName = accessorImplFileDescription.className;
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
        String replacedAccessorImplName = replacedAccessorName.replaceAll("XXXImpl", accessorImplClassName);
        sb.append(replacedAccessorImplName);
        sb.append(NEW_LINE);

        return sb;
    }

    /**
     * Checks of all the annotated elements (in fact only methods are expected)
     * can be accessed using the FriendlyAccessor pattern.
     *
     * Restrictions:
     * <ul>
     * <li>All the methods accessed by the same accessor must belong to the same
     * package.
     * <li>The annotated method must not have private access modifier.
     * </ul>
     *
     * Error message is reported using messager on the first found invalid
     * element, in addition the method returns false.
     *
     * @param values The java method elements to check
     */
    private boolean areAnnotatedElementsValid(Map<String, Collection<Element>> allAnnotatedMethods) {
        for (Map.Entry<String, Collection<Element>> accessorMethods : allAnnotatedMethods.entrySet()) {
            // All the methods accessed by the same accessor must belong 
            // to the same package.
            PackageElement enclosingPackage = null;
            Element firstMethodDefiningPackage = null;
            // The annotated method must not have private access modifier.
            for (Element method : accessorMethods.getValue()) {
                PackageElement methodEnclosingPackage = fingEnclosingPackage(method);
                if (enclosingPackage == null) {
                    enclosingPackage = methodEnclosingPackage;
                    firstMethodDefiningPackage = method;
                } else if (!enclosingPackage.getQualifiedName().equals(methodEnclosingPackage.getQualifiedName())) {
                    print(Diagnostic.Kind.ERROR,
                            "Accessor " + accessorMethods.getKey() + " cannot be used to access methods from different packages. "
                            + getFqMethodName(firstMethodDefiningPackage) + ", " + getFqMethodName(method));
                    return false;
                }

                if (method.getModifiers().contains(Modifier.PRIVATE)) {
                    print(Diagnostic.Kind.ERROR,
                            "Annotation FriendlyAccessor cannot be used for private method " + getFqMethodName(method));
                    return false;
                }
                if (method.getModifiers().contains(Modifier.PUBLIC)) {
                    print(Diagnostic.Kind.WARNING,
                            "Annotation FriendlyAccessor is superfluously used for public method " + getFqMethodName(method));
                }
            }
        }
        return true;
    }

    private PackageElement fingEnclosingPackage(Element method) {
        Element enclosingElement = method;
        while (enclosingElement != null) {
            if (enclosingElement.getKind() == ElementKind.PACKAGE) {
                return (PackageElement) enclosingElement;
            }
            enclosingElement = enclosingElement.getEnclosingElement();
        }
        return null;
    }

    private String getFqMethodName(Element method) {
        TypeMirror enclosingType = findEnclosingType(method);
        String fqEnclosingType = enclosingType.toString();
        return fqEnclosingType + "." + method.getSimpleName();
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

    private TypeOfAccessedMethod findTypeOfAccessedMethod(Element methodToAccess) {
        if (methodToAccess.getKind() == ElementKind.CONSTRUCTOR) {
            return TypeOfAccessedMethod.CONSTRUCTOR;
        }
        if (methodToAccess.getModifiers().contains(Modifier.STATIC)) {
            return TypeOfAccessedMethod.STATIC;
        }
        return TypeOfAccessedMethod.INSTANCE;
    }

    private String findTypeNameWithTrimmedPackage(TypeMirror enclosingType) {
        // TODO Do not use only last name, the type can be an innertype!!!
        String fqName = enclosingType.toString();
        return fqName.substring(fqName.lastIndexOf('.') + 1);
    }
}

//class TestStringBuilder {
//    void test(String s) {
//        StringBuilder sb = new StringBuilder();
//        sb.append(generateSomeString(s));
//    }
//}
