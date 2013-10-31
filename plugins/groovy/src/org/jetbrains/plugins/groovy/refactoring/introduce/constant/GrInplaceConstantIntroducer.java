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
package org.jetbrains.plugins.groovy.refactoring.introduce.constant;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser;
import com.intellij.refactoring.introduceField.IntroduceConstantHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.refactoring.GroovyNameSuggestionUtil;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrAbstractInplaceIntroducer;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.StringPartInfo;
import org.jetbrains.plugins.groovy.refactoring.introduce.field.GroovyInplaceFieldValidator;

import javax.swing.*;

/**
 * Created by Max Medvedev on 8/29/13
 */
public class GrInplaceConstantIntroducer extends GrAbstractInplaceIntroducer<GrIntroduceConstantSettings> {
  private final GrInplaceIntroduceConstantPanel myPanel;
  private final GrIntroduceContext myContext;
  private String[] mySuggestedNames;

  public GrInplaceConstantIntroducer(GrIntroduceContext context, OccurrencesChooser.ReplaceChoice choice) {
    super(IntroduceConstantHandler.REFACTORING_NAME, choice, context);

    myContext = context;

    myPanel = new GrInplaceIntroduceConstantPanel();

    mySuggestedNames = GroovyNameSuggestionUtil.suggestVariableNames(context.getExpression(), new GroovyInplaceFieldValidator(context),
                                                                     true);
  }

  @Override
  protected String getActionName() {
    return null;
  }

  @Override
  protected String[] suggestNames(boolean replaceAll, @Nullable GrVariable variable) {
    return mySuggestedNames;
  }

  @Nullable
  @Override
  protected JComponent getComponent() {
    return myPanel.getRootPane();
  }

  @Override
  protected void saveSettings(@NotNull GrVariable variable) {

  }

  @Override
  protected GrVariable runRefactoring(GrIntroduceContext context, GrIntroduceConstantSettings settings, boolean processUsages) {
    if (processUsages) {
      return new GrIntroduceConstantProcessor(context, settings).run();
    }
    else {
      PsiElement scope = context.getScope();
      return new GrIntroduceConstantProcessor(context, settings).addDeclaration(scope instanceof GroovyFileBase ? ((GroovyFileBase)scope).getScriptClass() : (PsiClass)scope).getVariables()[0];
    }
  }

  @Nullable
  @Override
  protected GrIntroduceConstantSettings getInitialSettingsForInplace(@NotNull final GrIntroduceContext context,
                                                                     @NotNull final OccurrencesChooser.ReplaceChoice choice,
                                                                     final String[] names) {
    return new GrIntroduceConstantSettings() {
      @Override
      public String getVisibilityModifier() {
        return PsiModifier.PUBLIC;
      }

      @Nullable
      @Override
      public PsiClass getTargetClass() {
        return (PsiClass)context.getScope();
      }

      @Nullable
      @Override
      public String getName() {
        return names[0];
      }

      @Override
      public boolean replaceAllOccurrences() {
        return isReplaceAllOccurrences();
      }

      @Nullable
      @Override
      public PsiType getSelectedType() {
        GrExpression expression = context.getExpression();
        GrVariable var = context.getVar();
        StringPartInfo stringPart = context.getStringPart();
        return var != null ? var.getDeclaredType() :
               expression != null ? expression.getType() :
               stringPart != null ? stringPart.getLiteral().getType() :
               null;
      }
    };
  }

  @Override
  protected GrIntroduceConstantSettings getSettings() {
    return new GrIntroduceConstantSettings() {
      @Override
      public String getVisibilityModifier() {
        return PsiModifier.PUBLIC;
      }

      @Nullable
      @Override
      public String getName() {
        return getInputName();
      }

      @Override
      public boolean replaceAllOccurrences() {
        return isReplaceAllOccurrences();
      }

      @Nullable
      @Override
      public PsiType getSelectedType() {
        return GrInplaceConstantIntroducer.this.getSelectedType();
      }

      @Nullable
      @Override
      public PsiClass getTargetClass() {
        return (PsiClass)myContext.getScope();
      }
    };
  }

  @Nullable
  @Override
  protected PsiElement checkLocalScope() {
    return getVariable().getContainingFile();
  }

}
