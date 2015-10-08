package cz.kamosh.friendlypackageaccessor.processor;

import cz.kamosh.friendlypackageaccessor.annotation.FriendlyAccess;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

@SupportedAnnotationTypes("cz.kamosh.friendlypackageaccessor.annotation.FriendlyAccess")
public class FriendlyAccessorAnnotationProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        for (Element e : roundEnv.getElementsAnnotatedWith(FriendlyAccess.class)) {

        }
        return true;
    }

}
