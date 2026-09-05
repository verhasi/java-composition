package guru.mocker.composition.intellij;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.light.LightMethodBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * SPIKE — tests whether {@link PsiAugmentProvider} can inject synthetic methods into a class
 * such that IntelliJ's "must implement abstract method" check treats them as implemented
 * (Lombok's technique for making generated members visible).
 *
 * <p>To isolate the MECHANISM from signature-parsing, this is HARDCODED: for a class literally
 * named {@code ImplProbe}, it synthesizes the three methods that {@code ImplProbe} declares
 * concisely — {@code int size()}, {@code boolean isEmpty()}, {@code boolean contains(Object)}.
 * If, after this, the class-level "must implement size()/isEmpty()/contains()" errors clear
 * (only the truly-unimplemented ones like {@code iterator()}/{@code toArray()} remain), the
 * mechanism is proven and real signature extraction becomes the follow-up.
 */
public class ConciseAugmentProvider extends PsiAugmentProvider {

    private static final Logger LOG = Logger.getInstance(ConciseAugmentProvider.class);

    @Override
    protected <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement element,
                                                             @NotNull Class<Psi> type,
                                                             @Nullable String nameHint) {
        List<Psi> result = new ArrayList<>();
        if (type != PsiMethod.class || !(element instanceof PsiClass)) {
            return result;
        }
        PsiClass psiClass = (PsiClass) element;
        if (!"ImplProbe".equals(psiClass.getName())) {
            return result; // spike: only touch the probe class
        }

        LOG.warn("[spike-augment] augmenting " + psiClass.getName() + " (skipping already-declared)");
        PsiManager mgr = psiClass.getManager();
        PsiElementFactory f = JavaPsiFacade.getElementFactory(psiClass.getProject());
        PsiType booleanType = PsiType.BOOLEAN;
        PsiType intType = PsiType.INT;
        PsiType objectType = f.createTypeByFQClassName("java.lang.Object", psiClass.getResolveScope());

        // KEY: only synthesize methods the class does NOT already declare (by name), so we
        // don't collide with the user's concise `size()`/`isEmpty()`/`contains()`. This tests
        // whether the user's own concise (bodyless) methods count as implementations once we
        // stop duplicating them.
        if (!declaresMethod(psiClass, "size")) {
            result.add(cast(method(mgr, psiClass, "size", intType)));
        }
        if (!declaresMethod(psiClass, "isEmpty")) {
            result.add(cast(method(mgr, psiClass, "isEmpty", booleanType)));
        }
        if (!declaresMethod(psiClass, "contains")) {
            PsiMethod contains = method(mgr, psiClass, "contains", booleanType);
            ((LightMethodBuilder) contains).addParameter("o", objectType);
            result.add(cast(contains));
        }
        LOG.warn("[spike-augment] synthesized " + result.size() + " method(s) for " + psiClass.getName());
        return result;
    }

    /** True if the class already declares a method with this name (incl. concise headers). */
    private boolean declaresMethod(PsiClass psiClass, String name) {
        for (PsiMethod m : psiClass.getMethods()) {
            if (name.equals(m.getName())) {
                return true;
            }
        }
        return false;
    }

    private PsiMethod method(PsiManager mgr, PsiClass containing, String name, PsiType returnType) {
        LightMethodBuilder m = new LightMethodBuilder(mgr, JavaLanguage.INSTANCE, name);
        m.setMethodReturnType(returnType);
        m.addModifier(PsiModifier.PUBLIC);
        m.setContainingClass(containing);
        m.setNavigationElement(containing);
        return m;
    }

    @SuppressWarnings("unchecked")
    private <Psi extends PsiElement> Psi cast(PsiMethod m) {
        return (Psi) m;
    }
}
