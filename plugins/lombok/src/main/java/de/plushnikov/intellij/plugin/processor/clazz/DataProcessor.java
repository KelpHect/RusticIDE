package de.plushnikov.intellij.plugin.processor.clazz;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.problem.ProblemSink;
import de.plushnikov.intellij.plugin.processor.LombokProcessorManager;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.AbstractConstructorClassProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.NoArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.RequiredArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.quickfix.PsiQuickFixFactory;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


/**
 * @author Plushnikov Michail
 */
public final class DataProcessor extends AbstractClassProcessor {

  public DataProcessor() {
    super(PsiMethod.class, LombokClassNames.DATA);
  }

  private static ToStringProcessor getToStringProcessor() {
    return LombokProcessorManager.getInstance().getToStringProcessor();
  }

  private static NoArgsConstructorProcessor getNoArgsConstructorProcessor() {
    return LombokProcessorManager.getInstance().getNoArgsConstructorProcessor();
  }

  private static GetterProcessor getGetterProcessor() {
    return LombokProcessorManager.getInstance().getGetterProcessor();
  }

  private static SetterProcessor getSetterProcessor() {
    return LombokProcessorManager.getInstance().getSetterProcessor();
  }

  private static EqualsAndHashCodeProcessor getEqualsAndHashCodeProcessor() {
    return LombokProcessorManager.getInstance().getEqualsAndHashCodeProcessor();
  }

  private static RequiredArgsConstructorProcessor getRequiredArgsConstructorProcessor() {
    return LombokProcessorManager.getInstance().getRequiredArgsConstructorProcessor();
  }

  @Override
  protected boolean possibleToGenerateElementNamed(@NotNull String nameHint,
                                                   @NotNull PsiClass psiClass,
                                                   @NotNull PsiAnnotation psiAnnotation) {
    return nameHint.equals(getStaticConstructorNameValue(psiAnnotation)) ||
           getNoArgsConstructorProcessor().possibleToGenerateElementNamed(nameHint, psiClass, psiAnnotation) ||
           getToStringProcessor().possibleToGenerateElementNamed(nameHint, psiClass, psiAnnotation) ||
           getEqualsAndHashCodeProcessor().possibleToGenerateElementNamed(nameHint, psiClass, psiAnnotation) ||
           getGetterProcessor().possibleToGenerateElementNamed(nameHint, psiClass, psiAnnotation) ||
           getSetterProcessor().possibleToGenerateElementNamed(nameHint, psiClass, psiAnnotation);
  }

  @Override
  protected Collection<String> getNamesOfPossibleGeneratedElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    Collection<String> result = new ArrayList<>();

    final String staticConstructorName = getStaticConstructorNameValue(psiAnnotation);
    if (StringUtil.isNotEmpty(staticConstructorName)) {
      result.add(staticConstructorName);
    }
    result.addAll(getNoArgsConstructorProcessor().getNamesOfPossibleGeneratedElements(psiClass, psiAnnotation));
    result.addAll(getToStringProcessor().getNamesOfPossibleGeneratedElements(psiClass, psiAnnotation));
    result.addAll(getEqualsAndHashCodeProcessor().getNamesOfPossibleGeneratedElements(psiClass, psiAnnotation));
    result.addAll(getGetterProcessor().getNamesOfPossibleGeneratedElements(psiClass, psiAnnotation));
    result.addAll(getSetterProcessor().getNamesOfPossibleGeneratedElements(psiClass, psiAnnotation));

    return result;
  }

  private static String getStaticConstructorNameValue(@NotNull PsiAnnotation psiAnnotation) {
    return PsiAnnotationUtil.getStringAnnotationValue(psiAnnotation, "staticConstructor", "");
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemSink builder) {
    validateAnnotationOnRightType(psiClass, builder);

    if (builder.deepValidation()) {
      final boolean hasNoEqualsAndHashCodeAnnotation =
        PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass, LombokClassNames.EQUALS_AND_HASHCODE);
      if (hasNoEqualsAndHashCodeAnnotation) {
        getEqualsAndHashCodeProcessor().validateCallSuperParamExtern(psiAnnotation, psiClass, builder);
      }

      if (shouldGenerateConstructor(psiClass)) {
        getRequiredArgsConstructorProcessor().validateBaseClassConstructor(psiClass, builder);
      }
    }
    return builder.success();
  }

  private static void validateAnnotationOnRightType(@NotNull PsiClass psiClass, @NotNull ProblemSink builder) {
    if (psiClass.isAnnotationType() || psiClass.isInterface() || psiClass.isEnum() || psiClass.isRecord()) {
      builder.addErrorMessage("inspection.message.data.only.supported.on.class.type")
        .withLocalQuickFixes(() -> PsiQuickFixFactory.createDeleteAnnotationFix(psiClass, LombokClassNames.DATA));
      builder.markFailed();
    }
  }

  @Override
  protected void generatePsiElements(@NotNull PsiClass psiClass,
                                     @NotNull PsiAnnotation psiAnnotation,
                                     @NotNull List<? super PsiElement> target,
                                     @Nullable String nameHint) {
    if (PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass, LombokClassNames.GETTER)) {
      target.addAll(getGetterProcessor().createFieldGetters(psiClass, PsiModifier.PUBLIC, nameHint));
    }
    if (PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass, LombokClassNames.SETTER)) {
      target.addAll(getSetterProcessor().createFieldSetters(psiClass, PsiModifier.PUBLIC, nameHint));
    }
    if (PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass, LombokClassNames.EQUALS_AND_HASHCODE) &&
        getEqualsAndHashCodeProcessor().noHintOrPossibleToGenerateElementNamed(nameHint, psiClass, psiAnnotation)) {
      target.addAll(getEqualsAndHashCodeProcessor().createEqualAndHashCode(psiClass, psiAnnotation));
    }
    if (PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass, LombokClassNames.TO_STRING) &&
        getToStringProcessor().noHintOrPossibleToGenerateElementNamed(nameHint, psiClass, psiAnnotation)) {
      target.addAll(getToStringProcessor().createToStringMethod(psiClass, psiAnnotation));
    }

    boolean hasConstructorWithoutParameters = false;
    final String staticName = getStaticConstructorNameValue(psiAnnotation);
    if (nameHint != null && !nameHint.equals(staticName) && !nameHint.equals(psiClass.getName())) return;

    if (!hasLombokConstructorAnnotations(psiClass) && PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass, LombokClassNames.SUPER_BUILDER)) {
      target.addAll(
        getRequiredArgsConstructorProcessor().createRequiredArgsConstructor(psiClass, PsiModifier.PUBLIC, psiAnnotation, staticName, true));
      // if there are no required fields, it will already have a default constructor without parameters
      hasConstructorWithoutParameters = AbstractConstructorClassProcessor.getRequiredFields(psiClass).isEmpty();
    }

    if (!hasConstructorWithoutParameters && shouldGenerateExtraNoArgsConstructor(psiClass)) {
      target.addAll(getNoArgsConstructorProcessor().createNoArgsConstructor(psiClass, PsiModifier.PRIVATE, psiAnnotation, true));
    }
  }

  @Override
  public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
    return LombokPsiElementUsage.READ_WRITE;
  }
}
