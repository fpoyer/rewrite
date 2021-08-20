/*
 * Copyright 2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java;

import lombok.EqualsAndHashCode;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.internal.FormatFirstClassPrefix;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.search.FindMethods;
import org.openrewrite.java.search.FindTypes;
import org.openrewrite.java.style.ImportLayoutStyle;
import org.openrewrite.java.style.IntelliJ;
import org.openrewrite.java.style.TabsAndIndentsStyle;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.*;

import static org.openrewrite.Tree.randomId;

/**
 * A Java refactoring visitor that can be used to add an import (or static import) to a given compilation unit.
 * This visitor can also be configured to only add the import if the imported class/method are referenced within the
 * compilation unit.
 * <P><P>
 * The {@link AddImport#type} must be supplied and represents a fully qualified class name.
 * <P><P>
 * The {@link AddImport#statik} is an optional method within the imported type. The staticMethod can be set to "*"
 * to represent a static wildcard import.
 * <P><P>
 * The {@link AddImport#onlyIfReferenced} is a flag (defaulted to true) to indicate if the import should only be added
 * if there is a reference to the imported class/method.
 */
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public class AddImport<P> extends JavaIsoVisitor<P> {
    @EqualsAndHashCode.Include
    private final String type;

    @EqualsAndHashCode.Include
    @Nullable
    private final String statik;

    @EqualsAndHashCode.Include
    private final boolean onlyIfReferenced;

    private final JavaType.Class classType;

    public AddImport(String type, @Nullable String statik, boolean onlyIfReferenced) {
        this.type = type;
        this.classType = JavaType.Class.build(type);
        this.statik = statik;
        this.onlyIfReferenced = onlyIfReferenced;
    }

    @Override
    public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, P p) {
        if (JavaType.Primitive.fromKeyword(classType.getFullyQualifiedName()) != null) {
            return cu;
        }

        // No need to add imports for classes within the same package
        int dotIndex = classType.getFullyQualifiedName().lastIndexOf('.');
        if(cu.getPackageDeclaration() != null && dotIndex >= 0) {
            String packageName = classType.getFullyQualifiedName().substring(0, dotIndex);
            if(packageName.equals(cu.getPackageDeclaration().getExpression().printTrimmed())) {
                return cu;
            }
        }

        if (onlyIfReferenced && !hasReference(cu)) {
            return cu;
        }

        if (classType.getPackageName().isEmpty()) {
            return cu;
        }

        if (cu.getImports().stream().anyMatch(i -> {
            String ending = i.getQualid().getSimpleName();
            if (statik == null) {
                return !i.isStatic() && i.getPackageName().equals(classType.getPackageName()) &&
                        (ending.equals(classType.getClassName()) || ending.equals("*"));
            }
            return i.isStatic() && i.getTypeName().equals(classType.getFullyQualifiedName()) &&
                    (ending.equals(statik) || ending.equals("*"));
        })) {
            return cu;
        }

        J.Import importToAdd = new J.Import(randomId(),
                Space.EMPTY,
                Markers.EMPTY,
                new JLeftPadded<>(statik == null ? Space.EMPTY : Space.format(" "),
                        statik != null, Markers.EMPTY),
                TypeTree.build(classType.getFullyQualifiedName() +
                        (statik == null ? "" : "." + statik)).withPrefix(Space.format(" ")));

        List<JRightPadded<J.Import>> imports = new ArrayList<>(cu.getPadding().getImports());

        if (imports.isEmpty()) {
            if (cu.getPackageDeclaration() == null && cu.getClasses().get(0).getPrefix().getComments().isEmpty()) {
                importToAdd = importToAdd.withPrefix(cu.getClasses().get(0).getPrefix());
            } else if (cu.getPackageDeclaration() == null) {
                importToAdd = importToAdd.withPrefix(cu.getClasses().get(0).getPrefix().withComments(new ArrayList<>()));
                TabsAndIndentsStyle tabsAndIndentsStyle = Optional.ofNullable(cu.getStyle(TabsAndIndentsStyle.class))
                        .orElse(IntelliJ.tabsAndIndents());
                String addNewLine = tabsAndIndentsStyle.getUseCRLFNewLines() ? "\r\n\r\n" : "\n\n";
                cu.getClasses().set(0, cu.getClasses().get(0).withPrefix(cu.getClasses().get(0).getPrefix().withWhitespace(addNewLine + cu.getClasses().get(0).getPrefix().getWhitespace())));
            } else {
                TabsAndIndentsStyle tabsAndIndentsStyle = Optional.ofNullable(cu.getStyle(TabsAndIndentsStyle.class))
                        .orElse(IntelliJ.tabsAndIndents());
                importToAdd = importToAdd.withPrefix(Space.format(tabsAndIndentsStyle.getUseCRLFNewLines() ? "\r\n\r\n" : "\n\n"));
            }
        }

        ImportLayoutStyle layoutStyle = Optional.ofNullable(cu.getStyle(ImportLayoutStyle.class))
                .orElse(IntelliJ.importLayout());

        Optional<JavaSourceSet> sourceSet = cu.getMarkers().findFirst(JavaSourceSet.class);
        Set<JavaType.FullyQualified> classpath = new HashSet<>();
        if (sourceSet.isPresent()) {
            classpath = sourceSet.get().getClasspath();
        }

        cu = cu.getPadding().withImports(layoutStyle.addImport(cu.getPadding().getImports(), importToAdd,
                cu.getPackageDeclaration(), classpath));

        doAfterVisit(new FormatFirstClassPrefix<>());

        return cu;
    }

    private boolean isTypeReference(NameTree t) {
        boolean isTypRef = true;
        if (t instanceof J.FieldAccess) {
            isTypRef = TypeUtils.isOfClassType(((J.FieldAccess)t).getTarget().getType(), type);
        }
        return isTypRef;
    }

    /**
     * Returns true if there is at least one matching references for this associated import.
     * An import is considered a match if:
     * It is non-static and has a field reference
     * It is static, the static method is a wildcard, and there is at least on method invocation on the given import type.
     * It is static, the static method is explicitly defined, and there is at least on method invocation matching the type and method.
     *
     * @param compilationUnit The compilation passed to the visitCompilationUnit
     * @return true if the import is referenced by the class either explicitly or through a method reference.
     */
    //Note that using anyMatch when a stream is empty ends up returning true, which is not the behavior needed here!
    private boolean hasReference(J.CompilationUnit compilationUnit) {
        if (statik == null) {
            //Non-static imports, we just look for field accesses.
            for (NameTree t : FindTypes.find(compilationUnit, type)) {
                if ((!(t instanceof J.FieldAccess) || !((J.FieldAccess) t).isFullyQualifiedClassReference(type)) &&
                        isTypeReference(t)) {
                    return true;
                }
            }
            return false;
        }

        //For static imports, we are either looking for a specific method or a wildcard.
        for (J invocation : FindMethods.find(compilationUnit, type + " *(..)")) {
            if(invocation instanceof J.MethodInvocation) {
                J.MethodInvocation mi = (J.MethodInvocation) invocation;
                if (mi.getSelect() == null &&
                        (statik.equals("*") || mi.getName().getSimpleName().equals(statik))) {
                    return true;
                }
            }
        }
        return false;
    }
}
