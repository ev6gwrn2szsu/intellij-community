/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.refactoring.introduce.variable;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.Pass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.refactoring.GrRefactoringError;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceHandlerBase;
import org.jetbrains.plugins.groovy.refactoring.introduce.StringPartInfo;

/**
 * Created by Max Medvedev on 10/29/13
 */
public class GrIntroduceVariableHandler extends GrIntroduceHandlerBase<GroovyIntroduceVariableSettings, GrControlFlowOwner> {
  public static final String DUMMY_NAME = "________________xxx_________________";
  protected static final String REFACTORING_NAME = GroovyRefactoringBundle.message("introduce.variable.title");
  private RangeMarker myPosition = null;

  @NotNull
  @Override
  protected GrControlFlowOwner[] findPossibleScopes(GrExpression selectedExpr,
                                                    GrVariable variable,
                                                    StringPartInfo stringPartInfo,
                                                    Editor editor) {
    // Get container element
    final GrControlFlowOwner scope = ControlFlowUtils.findControlFlowOwner(stringPartInfo != null ? stringPartInfo.getLiteral() : selectedExpr);
    if (scope == null) {
      throw new GrRefactoringError(
        GroovyRefactoringBundle.message("refactoring.is.not.supported.in.the.current.context", REFACTORING_NAME));
    }
    if (!GroovyRefactoringUtil.isAppropriateContainerForIntroduceVariable(scope)) {
      throw new GrRefactoringError(
        GroovyRefactoringBundle.message("refactoring.is.not.supported.in.the.current.context", REFACTORING_NAME));
    }
    return new GrControlFlowOwner[]{scope};
  }

  protected void checkExpression(@NotNull GrExpression selectedExpr) {
    // Cannot perform refactoring in parameter default values

    PsiElement parent = selectedExpr.getParent();
    while (!(parent == null || parent instanceof GroovyFileBase || parent instanceof GrParameter)) {
      parent = parent.getParent();
    }

    if (checkInFieldInitializer(selectedExpr)) {
      throw new GrRefactoringError(GroovyRefactoringBundle.message("refactoring.is.not.supported.in.the.current.context"));
    }

    if (parent instanceof GrParameter) {
      throw new GrRefactoringError(GroovyRefactoringBundle.message("refactoring.is.not.supported.in.method.parameters"));
    }
  }

  @Override
  protected void checkVariable(@NotNull GrVariable variable) throws GrRefactoringError {
    throw new GrRefactoringError(null);
  }

  @Override
  protected void checkStringLiteral(@NotNull StringPartInfo info) throws GrRefactoringError {
    //todo
  }

  @Override
  protected void checkOccurrences(@NotNull PsiElement[] occurrences) {
    //nothing to do
  }

  private static boolean checkInFieldInitializer(@NotNull GrExpression expr) {
    PsiElement parent = expr.getParent();
    if (parent instanceof GrClosableBlock) {
      return false;
    }
    if (parent instanceof GrField && expr == ((GrField)parent).getInitializerGroovy()) {
      return true;
    }
    if (parent instanceof GrExpression) {
      return checkInFieldInitializer(((GrExpression)parent));
    }
    return false;
  }

  /**
   * Inserts new variable declarations and replaces occurrences
   */
  public GrVariable runRefactoring(@NotNull final GrIntroduceContext context, @NotNull final GroovyIntroduceVariableSettings settings) {
    // Generating variable declaration

    final GrVariableDeclaration varDecl = generateDeclaration(context, settings);
    GrVariable insertedVar = processExpression(context, settings, varDecl, true);

    if (context.getEditor() != null && getPositionMarker() != null) {
      context.getEditor().getCaretModel().moveToOffset(getPositionMarker().getEndOffset());
      context.getEditor().getSelectionModel().removeSelection();
    }
    return insertedVar;
  }

  @Override
  protected GrInplaceVariableIntroducer getIntroducer(@NotNull GrIntroduceContext context, OccurrencesChooser.ReplaceChoice choice) {
    return new GrInplaceVariableIntroducer(getRefactoringName(), choice, context) {
      @Override
      protected GrVariable runRefactoring(GrIntroduceContext context, GroovyIntroduceVariableSettings settings, boolean processUsages) {
        final GrVariableDeclaration varDecl = generateDeclaration(context, settings);
        return processExpression(context, settings, varDecl, processUsages);
      }
    };
  }

  @Override
  protected void showScopeChooser(GrControlFlowOwner[] scopes, Pass<GrControlFlowOwner> callback, Editor editor) {
    //todo do nothing right now
  }

  @NotNull
  private static GrVariableDeclaration generateDeclaration(@NotNull GrIntroduceContext context,
                                                           @NotNull GroovyIntroduceVariableSettings settings) {
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(context.getProject());
    final String[] modifiers = settings.isDeclareFinal() ? new String[]{PsiModifier.FINAL} : null;

    final GrVariableDeclaration declaration =
      factory.createVariableDeclaration(modifiers, "foo", settings.getSelectedType(), settings.getName());

    generateInitializer(context, declaration.getVariables()[0]);
    return declaration;
  }

  @NotNull
  private GrVariable processExpression(@NotNull GrIntroduceContext context,
                                       @NotNull GroovyIntroduceVariableSettings settings,
                                       @NotNull GrVariableDeclaration varDecl, boolean processUsages) {
    if (context.getStringPart() != null) {
      final GrExpression ref = processLiteral(DUMMY_NAME, context.getStringPart(), context.getProject());
      return doProcessExpression(context, settings, varDecl, new PsiElement[]{ref}, ref, processUsages);
    }
    else {
      final GrExpression expression = context.getExpression();
      assert expression != null;
      return doProcessExpression(context, settings, varDecl, context.getOccurrences(), expression, processUsages);
    }
  }

  private GrVariable doProcessExpression(@NotNull final GrIntroduceContext context,
                                         @NotNull GroovyIntroduceVariableSettings settings,
                                         @NotNull GrVariableDeclaration varDecl,
                                         @NotNull PsiElement[] elements,
                                         @NotNull GrExpression expression, boolean processUsages) {
    return new GrIntroduceLocalVariableProcessor(context, settings, elements, expression, processUsages) {
      @Override
      protected void refreshPositionMarker(PsiElement e) {
        GrIntroduceVariableHandler.this.refreshPositionMarker(context.getEditor().getDocument().createRangeMarker(e.getTextRange()));
      }
    }.processExpression(varDecl);
  }

  @NotNull
  private static GrExpression generateInitializer(@NotNull GrIntroduceContext context,
                                                  @NotNull GrVariable variable) {
    final GrExpression initializer = context.getStringPart() != null
                                     ? GrIntroduceHandlerBase.generateExpressionFromStringPart(context.getStringPart(), context.getProject())
                                     : context.getExpression();
    final GrExpression dummyInitializer = variable.getInitializerGroovy();
    assert dummyInitializer != null;
    return dummyInitializer.replaceWithExpression(initializer, true);
  }

  void refreshPositionMarker(RangeMarker marker) {
    myPosition = marker;
  }

  private RangeMarker getPositionMarker() {
    return myPosition;
  }

  @NotNull
  @Override
  protected String getRefactoringName() {
    return REFACTORING_NAME;
  }

  @NotNull
  @Override
  protected String getHelpID() {
    return HelpID.INTRODUCE_VARIABLE;
  }

  @NotNull
  protected GroovyIntroduceVariableDialog getDialog(@NotNull GrIntroduceContext context) {
    final GroovyVariableValidator validator = new GroovyVariableValidator(context);
    return new GroovyIntroduceVariableDialog(context, validator);
  }
}
