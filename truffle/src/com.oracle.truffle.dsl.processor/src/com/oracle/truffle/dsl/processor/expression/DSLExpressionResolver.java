/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.dsl.processor.expression;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Binary;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.BooleanLiteral;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Call;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.DSLExpressionVisitor;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.IntLiteral;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Negate;
import com.oracle.truffle.dsl.processor.expression.DSLExpression.Variable;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;

public class DSLExpressionResolver implements DSLExpressionVisitor {

    private static final List<String> LOGIC_OPERATORS = Arrays.asList("||");
    public static final List<String> COMPARABLE_OPERATORS = Arrays.asList("<", "<=", ">", ">=");
    public static final List<String> IDENTITY_OPERATORS = Arrays.asList("==", "!=");
    private static final String CONSTRUCTOR_KEYWORD = "new";

    private final List<VariableElement> variables = new ArrayList<>();
    private final List<ExecutableElement> methods = new ArrayList<>();
    private final ProcessorContext context;

    private DSLExpressionResolver(ProcessorContext context) {
        this.context = context;
    }

    public DSLExpressionResolver(ProcessorContext context, List<? extends Element> lookupElements) {
        this(context);
        lookup(lookupElements);
    }

    public DSLExpressionResolver copy(List<? extends Element> prefixElements) {
        DSLExpressionResolver resolver = new DSLExpressionResolver(context);
        resolver.lookup(prefixElements);
        resolver.variables.addAll(variables);
        resolver.methods.addAll(methods);
        return resolver;
    }

    private void lookup(List<? extends Element> lookupElements) {
        variablesIn(variables, lookupElements, false);
        methodsIn(lookupElements);
    }

    private void methodsIn(List<? extends Element> lookupElements) {
        for (Element variable : lookupElements) {
            ElementKind kind = variable.getKind();
            if (kind == ElementKind.METHOD || kind == ElementKind.CONSTRUCTOR) {
                methods.add((ExecutableElement) variable);
            }
        }
    }

    private static void variablesIn(List<VariableElement> variables, List<? extends Element> lookupElements, boolean publicOnly) {
        for (Element variable : lookupElements) {
            ElementKind kind = variable.getKind();
            if (kind == ElementKind.LOCAL_VARIABLE || kind == ElementKind.PARAMETER || kind == ElementKind.FIELD || kind == ElementKind.ENUM_CONSTANT) {
                VariableElement variableElement = (VariableElement) variable;
                if (!publicOnly || variableElement.getModifiers().contains(Modifier.PUBLIC)) {
                    variables.add(variableElement);
                }
            }
        }
    }

    private static String getMethodName(ExecutableElement method) {
        if (method.getKind() == ElementKind.CONSTRUCTOR) {
            return CONSTRUCTOR_KEYWORD;
        } else {
            return method.getSimpleName().toString();
        }
    }

    public void visitBinary(Binary binary) {
        String operator = binary.getOperator();
        TypeMirror leftType = binary.getLeft().getResolvedType();
        TypeMirror rightType = binary.getRight().getResolvedType();
        if (!ElementUtils.areTypesCompatible(leftType, rightType)) {
            throw new InvalidExpressionException(String.format("Incompatible operand types %s and %s.", ElementUtils.getSimpleName(leftType), ElementUtils.getSimpleName(rightType)));
        }

        TypeMirror booleanType = context.getType(boolean.class);
        boolean valid;
        if (LOGIC_OPERATORS.contains(operator)) {
            valid = ElementUtils.typeEquals(leftType, booleanType);
        } else if (COMPARABLE_OPERATORS.contains(operator)) {
            valid = ElementUtils.isPrimitive(leftType);
        } else if (IDENTITY_OPERATORS.contains(operator)) {
            valid = leftType.getKind().isPrimitive() || leftType.getKind() == TypeKind.DECLARED || leftType.getKind() == TypeKind.ARRAY;
        } else {
            throw new InvalidExpressionException(String.format("The operator %s is undefined.", operator));
        }
        binary.setResolvedType(booleanType);

        if (!valid) {
            throw new InvalidExpressionException(String.format("The operator %s is undefined for the argument type(s) %s %s.", operator, ElementUtils.getSimpleName(leftType),
                            ElementUtils.getSimpleName(rightType)));
        }
    }

    public void visitNegate(Negate negate) {
        TypeMirror booleanType = context.getType(boolean.class);
        TypeMirror resolvedType = negate.getResolvedType();
        if (!ElementUtils.typeEquals(resolvedType, booleanType)) {
            throw new InvalidExpressionException(String.format("The operator %s is undefined for the argument type %s.", "!", ElementUtils.getSimpleName(resolvedType)));
        }
    }

    public void visitCall(Call call) {
        List<ExecutableElement> lookupMethods;
        DSLExpression receiver = call.getReceiver();
        if (receiver == null) {
            lookupMethods = this.methods;
        } else {
            TypeMirror type = receiver.getResolvedType();
            if (type.getKind() == TypeKind.DECLARED) {
                type = context.reloadType(type); // ensure ECJ has the type loaded
                lookupMethods = ElementFilter.methodsIn(context.getEnvironment().getElementUtils().getAllMembers((TypeElement) ((DeclaredType) type).asElement()));
            } else {
                lookupMethods = Collections.emptyList();
            }
        }

        ExecutableElement foundWithName = null;
        outer: for (ExecutableElement method : lookupMethods) {
            if (getMethodName(method).equals(call.getName())) {
                foundWithName = method;

                List<? extends VariableElement> parameters = method.getParameters();
                if (parameters.size() != call.getParameters().size()) {
                    continue outer;
                }

                int parameterIndex = 0;
                for (DSLExpression expression : call.getParameters()) {
                    TypeMirror sourceType = expression.getResolvedType();
                    TypeMirror targetType = parameters.get(parameterIndex).asType();
                    if (!ElementUtils.isAssignable(sourceType, targetType)) {
                        continue outer;
                    }
                    expression.setResolvedTargetType(targetType);
                    parameterIndex++;
                }

                call.setResolvedMethod(method);
                break;
            }
        }
        if (call.getResolvedMethod() == null) {
            if (foundWithName == null) {
                // parameter mismatch
                throw new InvalidExpressionException(String.format("The method %s is undefined for the enclosing scope.", call.getName()));
            } else {
                StringBuilder arguments = new StringBuilder();
                String sep = "";
                for (DSLExpression expression : call.getParameters()) {
                    arguments.append(sep).append(ElementUtils.getSimpleName(expression.getResolvedType()));
                    sep = ", ";
                }
                // name mismatch
                throw new InvalidExpressionException(String.format("The method %s in the type %s is not applicable for the arguments %s.",   //
                                ElementUtils.getReadableSignature(foundWithName),   //
                                ElementUtils.getSimpleName((TypeElement) foundWithName.getEnclosingElement()), arguments.toString()));
            }
        }
    }

    public void visitVariable(Variable variable) {
        List<VariableElement> lookupVariables;
        DSLExpression receiver = variable.getReceiver();
        if (variable.getName().equals("null")) {
            variable.setResolvedVariable(new CodeVariableElement(new CodeTypeMirror(TypeKind.NULL), "null"));
        } else {
            if (receiver == null) {
                lookupVariables = this.variables;
            } else {
                TypeMirror type = receiver.getResolvedType();
                if (type.getKind() == TypeKind.DECLARED) {
                    type = context.reloadType(type); // ensure ECJ has the type loaded
                    lookupVariables = new ArrayList<>();
                    variablesIn(lookupVariables, context.getEnvironment().getElementUtils().getAllMembers((TypeElement) ((DeclaredType) type).asElement()), true);
                } else if (type.getKind() == TypeKind.ARRAY) {
                    lookupVariables = Arrays.<VariableElement> asList(new CodeVariableElement(context.getType(int.class), "length"));
                } else {
                    lookupVariables = Collections.emptyList();
                }
            }

            for (VariableElement variableElement : lookupVariables) {
                if (variableElement.getSimpleName().toString().equals(variable.getName())) {
                    variable.setResolvedVariable(variableElement);
                    break;
                }
            }
        }

        if (variable.getResolvedVariable() == null) {
            throw new InvalidExpressionException(String.format("%s cannot be resolved.", variable.getName()));
        }
    }

    public void visitBooleanLiteral(BooleanLiteral binary) {
        binary.setResolvedType(context.getType(boolean.class));
    }

    public void visitIntLiteral(IntLiteral binary) {
        try {
            binary.setResolvedType(context.getType(int.class));

            final int base;
            final String literal;

            if (binary.getLiteral().startsWith("0x")) {
                base = 16;
                literal = binary.getLiteral().substring(2);
            } else if (binary.getLiteral().startsWith("0b")) {
                base = 2;
                literal = binary.getLiteral().substring(2);
            } else if (binary.getLiteral().startsWith("0")) {
                base = 8;
                literal = binary.getLiteral();
            } else {
                base = 10;
                literal = binary.getLiteral();
            }

            binary.setResolvedValueInt(Integer.parseInt(literal, base));
        } catch (NumberFormatException e) {
            throw new InvalidExpressionException(String.format("Type mismatch: cannot convert from String '%s' to int", binary.getLiteral()));
        }
    }

}
