package fr.adrienbrault.idea.symfony2plugin.config.yaml.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.StandardPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.jetbrains.php.lang.parser.PhpElementTypes;
import com.jetbrains.php.lang.psi.PhpFile;
import com.jetbrains.php.lang.psi.elements.*;
import fr.adrienbrault.idea.symfony2plugin.Symfony2ProjectComponent;
import fr.adrienbrault.idea.symfony2plugin.codeInspection.quickfix.CreateMethodQuickFix;
import fr.adrienbrault.idea.symfony2plugin.config.xml.XmlHelper;
import fr.adrienbrault.idea.symfony2plugin.config.yaml.YamlElementPatternHelper;
import fr.adrienbrault.idea.symfony2plugin.stubs.ContainerCollectionResolver;
import fr.adrienbrault.idea.symfony2plugin.util.AnnotationBackportUtil;
import fr.adrienbrault.idea.symfony2plugin.util.EventSubscriberUtil;
import fr.adrienbrault.idea.symfony2plugin.util.PhpElementsUtil;
import fr.adrienbrault.idea.symfony2plugin.util.PsiElementUtils;
import fr.adrienbrault.idea.symfony2plugin.util.dict.ServiceUtil;
import fr.adrienbrault.idea.symfony2plugin.util.yaml.YamlHelper;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLTokenTypes;
import org.jetbrains.yaml.psi.*;

public class EventMethodCallInspection extends LocalInspectionTool {

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if(!Symfony2ProjectComponent.isEnabled(holder.getProject())) {
            return super.buildVisitor(holder, isOnTheFly);
        }

        return new PsiElementVisitor() {
            @Override
            public void visitFile(PsiFile psiFile) {
                if(psiFile instanceof XmlFile) {
                    visitXmlFile(psiFile, holder, new ContainerCollectionResolver.LazyServiceCollector(holder.getProject()));
                } else if(psiFile instanceof YAMLFile) {
                    visitYamlFile(psiFile, holder, new ContainerCollectionResolver.LazyServiceCollector(holder.getProject()));
                } else if(psiFile instanceof PhpFile) {
                    visitPhpFile((PhpFile) psiFile, holder);
                }
            }
        };
    }

    private void visitPhpFile(PhpFile psiFile, final ProblemsHolder holder) {
        psiFile.acceptChildren(new PhpSubscriberRecursiveElementWalkingVisitor(holder));
    }

    private void visitYamlFile(PsiFile psiFile, final ProblemsHolder holder, @NotNull final ContainerCollectionResolver.LazyServiceCollector lazyServiceCollector) {

        psiFile.acceptChildren(new PsiRecursiveElementWalkingVisitor() {
            @Override
            public void visitElement(PsiElement element) {
                annotateCallMethod(element, holder, lazyServiceCollector);
                super.visitElement(element);
            }
        });

    }

    private void visitXmlFile(@NotNull PsiFile psiFile, @NotNull final ProblemsHolder holder, @NotNull final ContainerCollectionResolver.LazyServiceCollector lazyServiceCollector) {

        psiFile.acceptChildren(new PsiRecursiveElementWalkingVisitor() {
            @Override
            public void visitElement(PsiElement element) {

                if(XmlHelper.getTagAttributePattern("tag", "method").inside(XmlHelper.getInsideTagPattern("services")).inFile(XmlHelper.getXmlFilePattern()).accepts(element) ||
                   XmlHelper.getTagAttributePattern("call", "method").inside(XmlHelper.getInsideTagPattern("services")).inFile(XmlHelper.getXmlFilePattern()).accepts(element)
                  )
                {

                    // attach to text child only
                    PsiElement[] psiElements = element.getChildren();
                    if(psiElements.length < 2) {
                        return;
                    }

                    String serviceClassValue = XmlHelper.getServiceDefinitionClass(element);
                    if(serviceClassValue != null && StringUtils.isNotBlank(serviceClassValue)) {
                        registerMethodProblem(psiElements[1], holder, serviceClassValue, lazyServiceCollector);
                    }

                }

                super.visitElement(element);
            }
        });

    }

    @Nullable
    private String getEventName(PsiElement psiElement) {

        // xml service
        if(psiElement.getContainingFile() instanceof XmlFile) {

            XmlTag xmlTag = PsiTreeUtil.getParentOfType(psiElement, XmlTag.class);
            if(xmlTag == null) {
                return null;
            }

            XmlAttribute event = xmlTag.getAttribute("event");
            if(event == null) {
                return null;
            }

            String value = event.getValue();
            if(StringUtils.isBlank(value)) {
                return null;
            }

            return value;

        } else if(psiElement.getContainingFile() instanceof YAMLFile) {

            // yaml services
            YAMLMapping yamlHash = PsiTreeUtil.getParentOfType(psiElement, YAMLMapping.class);
            if(yamlHash != null) {
                YAMLKeyValue event = YamlHelper.getYamlKeyValue(yamlHash, "event");
                if(event != null) {
                    PsiElement value = event.getValue();
                    if(value != null ) {
                        String text = value.getText();
                        if(StringUtils.isNotBlank(text)) {
                            return text;
                        }
                    }
                }
            }

        } else if(psiElement.getContainingFile() instanceof PhpFile) {

            ArrayHashElement arrayHashElement = PsiTreeUtil.getParentOfType(psiElement, ArrayHashElement.class);
            if(arrayHashElement != null) {
                PhpPsiElement key = arrayHashElement.getKey();
                if (key != null) {
                    String stringValue = PhpElementsUtil.getStringValue(key);
                    if(StringUtils.isNotBlank(stringValue)) {
                        return stringValue;
                    }
                }
            }

        }

        return null;
    }

    private void visitYamlMethodTagKey(@NotNull final PsiElement psiElement, @NotNull ProblemsHolder holder, ContainerCollectionResolver.LazyServiceCollector collector) {

        String methodName = PsiElementUtils.trimQuote(psiElement.getText());
        if(StringUtils.isBlank(methodName)) {
            return;
        }

        String classValue = YamlHelper.getServiceDefinitionClass(psiElement);
        if(classValue == null) {
            return;
        }

        registerMethodProblem(psiElement, holder, classValue, collector);
    }

    private void annotateCallMethod(@NotNull final PsiElement psiElement, @NotNull ProblemsHolder holder, ContainerCollectionResolver.LazyServiceCollector collector) {

        if(StandardPatterns.and(
            YamlElementPatternHelper.getInsideKeyValue("tags"),
            YamlElementPatternHelper.getSingleLineScalarKey("method")
        ).accepts(psiElement)) {
            visitYamlMethodTagKey(psiElement, holder, collector);
        }

        if((PlatformPatterns.psiElement(YAMLTokenTypes.TEXT).accepts(psiElement)
            || PlatformPatterns.psiElement(YAMLTokenTypes.SCALAR_DSTRING).accepts(psiElement)))
        {
            visitYamlMethod(psiElement, holder, collector);
        }

    }

    private void visitYamlMethod(PsiElement psiElement, ProblemsHolder holder, ContainerCollectionResolver.LazyServiceCollector collector) {
        if(!YamlElementPatternHelper.getInsideKeyValue("calls").accepts(psiElement)){
            return;
        }

        PsiElement parent = psiElement.getParent();
        if(!(parent instanceof YAMLScalar)) {
            return;
        }

        PsiElement yamlSeq = parent.getContext();
        if(!(yamlSeq instanceof YAMLSequenceItem)) {
            return;
        }

        PsiElement context = yamlSeq.getContext();
        if(!(context instanceof YAMLSequence)) {
            return;
        }

        YAMLKeyValue callYamlKeyValue = PsiTreeUtil.getParentOfType(psiElement, YAMLKeyValue.class);
        if(callYamlKeyValue == null) {
            return;
        }

        YAMLKeyValue classKeyValue = YamlHelper.getYamlKeyValue(callYamlKeyValue.getContext(), "class");
        if(classKeyValue == null) {
            return;
        }

        registerMethodProblem(psiElement, holder, getServiceName(classKeyValue.getValue()), collector);

    }

    private void registerMethodProblem(final @NotNull PsiElement psiElement, @NotNull ProblemsHolder holder, @NotNull String classKeyValue, ContainerCollectionResolver.LazyServiceCollector collector) {
        registerMethodProblem(psiElement, holder, ServiceUtil.getResolvedClassDefinition(psiElement.getProject(), classKeyValue, collector));
    }

    private void registerMethodProblem(final @NotNull PsiElement psiElement, @NotNull ProblemsHolder holder, @Nullable final PhpClass phpClass) {

        if(phpClass == null) {
            return;
        }

        final String methodName = PsiElementUtils.trimQuote(psiElement.getText());
        if(phpClass.findMethodByName(methodName) != null) {
            return;
        }

        holder.registerProblem(psiElement, "Missing Method", ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new CreateMethodQuickFix(phpClass, methodName, new CreateMethodQuickFix.InsertStringInterface() {
            @NotNull
            @Override
            public StringBuilder getStringBuilder() {

                String taggedEventMethodParameter = null;
                String eventName = getEventName(psiElement);
                if(eventName != null) {
                    taggedEventMethodParameter = EventSubscriberUtil.getTaggedEventMethodParameter(psiElement.getProject(), eventName);
                    if(taggedEventMethodParameter != null) {
                        String qualifiedName = AnnotationBackportUtil.getQualifiedName(phpClass, taggedEventMethodParameter);
                        if(qualifiedName != null && !qualifiedName.equals(taggedEventMethodParameter.substring(1))) {
                            taggedEventMethodParameter = qualifiedName;
                        }
                    }

                }

                String parameter = "";
                if(taggedEventMethodParameter != null) {
                    parameter = taggedEventMethodParameter + " $event";
                }

                return new StringBuilder()
                    .append("public function ")
                    .append(methodName)
                    .append("(")
                    .append(parameter)
                    .append(")\n {\n}\n\n");
            }
        }));
    }

    private String getServiceName(PsiElement psiElement) {
        return YamlHelper.trimSpecialSyntaxServiceName(PsiElementUtils.getText(psiElement));
    }

    /**
     * getSubscribedEvents method quick fix check
     *
     * return array(
     *   ConsoleEvents::COMMAND => array('onCommanda', 255),
     *   ConsoleEvents::TERMINATE => array('onTerminate', -255),
     * );
     *
     */
    private class PhpSubscriberRecursiveElementWalkingVisitor extends PsiRecursiveElementWalkingVisitor {
        private final ProblemsHolder holder;

        public PhpSubscriberRecursiveElementWalkingVisitor(ProblemsHolder holder) {
            this.holder = holder;
        }

        @Override
        public void visitElement(PsiElement element) {
            super.visitElement(element);

            if(!(element instanceof StringLiteralExpression)) {
                return;
            }

            PsiElement arrayValue = element.getParent();
            if(arrayValue != null && arrayValue.getNode().getElementType() == PhpElementTypes.ARRAY_VALUE) {
                PhpReturn phpReturn = PsiTreeUtil.getParentOfType(arrayValue, PhpReturn.class);
                if(phpReturn != null) {
                    Method method = PsiTreeUtil.getParentOfType(arrayValue, Method.class);
                    if(method != null) {
                        String name = method.getName();
                        if("getSubscribedEvents".equals(name)) {
                            PhpClass containingClass = method.getContainingClass();
                            if(containingClass != null && PhpElementsUtil.isInstanceOf(containingClass, "\\Symfony\\Component\\EventDispatcher\\EventSubscriberInterface")) {
                                String contents = ((StringLiteralExpression) element).getContents();
                                if(StringUtils.isNotBlank(contents) && containingClass.findMethodByName(contents) == null) {
                                    registerMethodProblem(element, holder, containingClass);
                                }
                            }
                        }
                    }
                }
            }
        }

    }
}
