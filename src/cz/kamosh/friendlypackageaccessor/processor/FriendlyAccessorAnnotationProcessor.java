package cz.kamosh.friendlypackageaccessor.processor;

import cz.kamosh.friendlypackageaccessor.annotation.FriendlyAccessor;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.DoubleStream;
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

//    private Map<String, Collection<Element>> collectAccessorsAndMethods(Set<Element> friendlyAccessorMethods) {
//        
//    }
//  
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        final Filer filer = processingEnv.getFiler();

        Map<String, Collection<Element>> accessorsEntries
                = collectAccessorsAndMethods(roundEnv.getElementsAnnotatedWith(FriendlyAccessor.class));                
        
        for (Map.Entry<String, Collection<Element>> accessorEntry : accessorsEntries.entrySet()) {
            try {
                // TODO Think about originating elements
//                final JavaFileObject accessorSourceFile = filer.createSourceFile(accessorEntry.getKey(), null);
//                final Writer accessorSourceFileWriter = accessorSourceFile.openWriter();
                if (false) {
                    throw new IOException();
                }
                for (Element methodToAccess : accessorEntry.getValue()) {
                    TypeMirror enclosingType = findEnclosingType(methodToAccess);
                    String methodName = methodToAccess.getSimpleName().toString();
                    SimpleElementVisitor6 visitor = new SimpleElementVisitor6() {

                        @Override
                        public Object visitExecutable(ExecutableElement e, Object p) {
                            StringBuilder sb = new StringBuilder();
                            sb.append(e.getReturnType().toString());
                            sb.append(" ");
                            sb.append(e.getSimpleName().toString());
                            sb.append(" ");
                            sb.append("(");
                            String comma = "";
                            String accessedClassParamName = null;
                            if(enclosingType != null) {
                                String fqEnclosingType = enclosingType.toString();
                                sb.append(enclosingType.toString());
                                sb.append(" ");                                
                                accessedClassParamName = firstLowerCase(fqEnclosingType.substring(fqEnclosingType.lastIndexOf('.') +1) );
                                sb.append(accessedClassParamName);
                                comma = ", ";
                            }
                            for (VariableElement variableElement : e.getParameters()) {
                                sb.append(comma);
                                TypeMirror paramType = variableElement.asType();
                                sb.append(paramType.toString());
                                sb.append(" ");
                                sb.append(variableElement.getSimpleName().toString());
                                comma = ", ";
                            }
                            sb.append(")\n");
                            
                            sb.append("{\n");
                            sb.append("return " + accessedClassParamName);
                            sb.append("(");
                            comma = "";
                            for (VariableElement variableElement : e.getParameters()) {
                                sb.append(comma);                                
                                sb.append(variableElement.getSimpleName().toString());
                                comma = ", ";
                            }
                            sb.append(")");
                            sb.append("\n");
                            sb.append("}\n");

                            print(processingEnv, sb.toString());
                            return super.visitExecutable(e, p); //To change body of generated methods, choose Tools | Templates.
                        }
                    };
                    methodToAccess.accept(visitor, this);
                }
            } catch (IOException ex) {
                Logger.getLogger(FriendlyAccessorAnnotationProcessor.class.getName()).log(Level.SEVERE, null, ex);
            }

//            for (Element e : accessorEntry.getValue()) {
//                String message = e.getSimpleName().toString();
//                processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, message);
//            }
        }
        return true;
    }

    private Map<String, Collection<Element>> collectAccessorsAndMethods(Set<? extends Element> elementsAnnotatedWithFriendlyAccessor) {
        Map<String, Collection<Element>> res = new HashMap<>();
        for (Element e : elementsAnnotatedWithFriendlyAccessor) {
            final FriendlyAccessor friendlyAccessorAnot = e.getAnnotation(FriendlyAccessor.class);
            String fqAccessor = friendlyAccessorAnot.friendPackage() + friendlyAccessorAnot.accessor();
            Collection<Element> accessedMethods = res.get(fqAccessor);
            if (accessedMethods == null) {
                accessedMethods = new ArrayList<Element>();
                res.put(fqAccessor, accessedMethods);
            }
            accessedMethods.add(e);
        }
        return res;
    }

    void print(ProcessingEnvironment processingEnv, String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, message);
    }

    private TypeMirror findEnclosingType(Element methodToAccess) {
        Element enclosingElement = methodToAccess;
        while(enclosingElement != null) {
            if(enclosingElement.getKind() == ElementKind.CLASS)
            {               
              return enclosingElement.asType();
            }
            enclosingElement = enclosingElement.getEnclosingElement();
        }
        return null;
    }
    
    private static String firstLowerCase(String string) {
        return Character.toLowerCase(string.charAt(0)) + (string.length() > 1 ? string.substring(1) : "");
    }
}
