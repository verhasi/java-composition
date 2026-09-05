package guru.mocker.composition.intellij;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.light.LightMethodBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Makes concise methods ({@code T m() -> expr;} / {@code = ref;}) count as real method
 * implementations, so a class that {@code implements} an interface using concise bodies does
 * not show a false "must implement abstract method" error.
 *
 * <p>The parser leaves each concise method as a <em>bodyless</em> {@link PsiMethod} header
 * (its body is a sibling error node). IntelliJ treats a bodyless concrete method as
 * abstract-ish, so it does not satisfy the interface. This provider synthesizes a
 * body-bearing twin ({@link LightMethodBuilder}) copying that header's signature, which DOES
 * satisfy the check.
 *
 * <p><b>Recursion safety:</b> augment providers must not call {@link PsiClass#getMethods()}
 * (it re-enters augmentation). This provider reads only the class's <em>physical child</em>
 * PSI ({@link PsiClass#getChildren()}), which is the non-augmented AST view.
 */
public final class ConciseAugmentProvider extends PsiAugmentProvider {

    @Override
    protected <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement element,
                                                             @NotNull Class<Psi> type,
                                                             @Nullable String nameHint) {
        List<Psi> result = new ArrayList<>();
        if (type != PsiMethod.class || !(element instanceof PsiClass)) {
            return result;
        }
        PsiClass psiClass = (PsiClass) element;

        // Iterate the class's PHYSICAL children only (no getMethods() → no re-entrancy).
        for (PsiElement child : psiClass.getChildren()) {
            if (!(child instanceof PsiMethod)) {
                continue;
            }
            PsiMethod method = (PsiMethod) child;
            if (!isConciseBodyless(method)) {
                continue; // normal-bodied or abstract method — leave it
            }
            result.add(cast(synthesizeTwin(psiClass, method)));
        }
        return result;
    }

    /**
     * A concise method is a concrete (non-abstract, non-interface) method with an intact
     * header but NO body block — the parser dropped the body as a sibling error.
     */
    private boolean isConciseBodyless(PsiMethod method) {
        if (method.getBody() != null) {
            return false; // has a real { } body
        }
        PsiModifierList mods = method.getModifierList();
        if (mods.hasModifierProperty(PsiModifier.ABSTRACT)
                || mods.hasModifierProperty(PsiModifier.NATIVE)) {
            return false; // genuinely abstract/native — bodyless by design, not concise
        }
        PsiClass containing = method.getContainingClass();
        if (containing != null && containing.isInterface()) {
            return false; // interface method — bodyless by design
        }
        return true;
    }

    /** Synthesize a body-bearing twin copying the concise method's signature. */
    private PsiMethod synthesizeTwin(PsiClass containing, PsiMethod source) {
        LightMethodBuilder twin = new LightMethodBuilder(
                containing.getManager(), JavaLanguage.INSTANCE, source.getName());
        PsiType returnType = source.getReturnType();
        if (returnType != null) {
            twin.setMethodReturnType(returnType);
        }
        for (String modifier : PsiModifier.MODIFIERS) {
            if (source.getModifierList().hasExplicitModifier(modifier)) {
                twin.addModifier(modifier);
            }
        }
        for (PsiParameter p : source.getParameterList().getParameters()) {
            twin.addParameter(p.getName(), p.getType());
        }
        twin.setContainingClass(containing);
        twin.setNavigationElement(source); // navigation jumps to the user's concise method
        return twin;
    }

    @SuppressWarnings("unchecked")
    private <Psi extends PsiElement> Psi cast(PsiMethod m) {
        return (Psi) m;
    }
}
