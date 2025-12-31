package fr.nhsoul.natsbridge.processor;

import com.google.auto.service.AutoService;
import fr.nhsoul.natsbridge.common.annotation.NatsSubscribe;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.*;

@AutoService(Processor.class)
@SupportedAnnotationTypes("fr.nhsoul.natsbridge.common.annotation.NatsSubscribe")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class NatsSubscribeProcessor extends AbstractProcessor {

    private static final String GENERATED_FILE = "META-INF/natsbridge/subscriptions.list";
    private final Map<String, List<SubscriptionInfo>> subscriptions = new HashMap<>();

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (annotations.isEmpty()) {
            return false;
        }

        // Collect all @NatsSubscribe methods
        for (Element element : roundEnv.getElementsAnnotatedWith(NatsSubscribe.class)) {
            if (element.getKind() != ElementKind.METHOD) {
                processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "@NatsSubscribe can only be applied to methods",
                    element
                );
                continue;
            }

            ExecutableElement method = (ExecutableElement) element;
            TypeElement classElement = (TypeElement) method.getEnclosingElement();
            NatsSubscribe annotation = method.getAnnotation(NatsSubscribe.class);

            // Validate method signature
            if (!isValidSubscriptionMethod(method)) {
                continue;
            }

            String className = classElement.getQualifiedName().toString();
            String methodName = method.getSimpleName().toString();
            String subject = annotation.value();
            boolean async = annotation.async();

            subscriptions.computeIfAbsent(className, k -> new ArrayList<>())
                .add(new SubscriptionInfo(methodName, subject, async));
        }

        // Generate the subscriptions list file
        if (!subscriptions.isEmpty()) {
            generateSubscriptionsFile();
        }

        return true;
    }

    private boolean isValidSubscriptionMethod(ExecutableElement method) {
        // Check if method is public
        if (!method.getModifiers().contains(Modifier.PUBLIC)) {
            processingEnv.getMessager().printMessage(
                Diagnostic.Kind.ERROR,
                "@NatsSubscribe method must be public",
                method
            );
            return false;
        }

        // Check if method is not static
        if (method.getModifiers().contains(Modifier.STATIC)) {
            processingEnv.getMessager().printMessage(
                Diagnostic.Kind.ERROR,
                "@NatsSubscribe method cannot be static",
                method
            );
            return false;
        }

        // Check parameter count
        List<? extends VariableElement> parameters = method.getParameters();
        if (parameters.size() != 1) {
            processingEnv.getMessager().printMessage(
                Diagnostic.Kind.ERROR,
                "@NatsSubscribe method must have exactly one parameter",
                method
            );
            return false;
        }

        // Check parameter type
        TypeMirror paramType = parameters.get(0).asType();
        String paramTypeName = paramType.toString();
        if (!("java.lang.String".equals(paramTypeName) || "byte[]".equals(paramTypeName))) {
            processingEnv.getMessager().printMessage(
                Diagnostic.Kind.ERROR,
                "@NatsSubscribe method parameter must be String or byte[]",
                method
            );
            return false;
        }

        return true;
    }

    private void generateSubscriptionsFile() {
        try {
            FileObject file = processingEnv.getFiler().createResource(
                StandardLocation.CLASS_OUTPUT,
                "",
                GENERATED_FILE
            );

            try (Writer writer = file.openWriter()) {
                for (Map.Entry<String, List<SubscriptionInfo>> entry : subscriptions.entrySet()) {
                    String className = entry.getKey();
                    for (SubscriptionInfo info : entry.getValue()) {
                        writer.write(className + "|" + info.methodName + "|" + 
                                   info.subject + "|" + info.async + "\n");
                    }
                }
            }

        } catch (IOException e) {
            processingEnv.getMessager().printMessage(
                Diagnostic.Kind.ERROR,
                "Failed to generate subscriptions file: " + e.getMessage()
            );
        }
    }

    private static class SubscriptionInfo {
        final String methodName;
        final String subject;
        final boolean async;

        SubscriptionInfo(String methodName, String subject, boolean async) {
            this.methodName = methodName;
            this.subject = subject;
            this.async = async;
        }
    }
}