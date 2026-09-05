package guru.mocker.composition.intellij;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * Colours the concise method-body markers ({@code ->} and {@code =}) so they stand out as
 * operators/keywords rather than appearing as default text inside error elements.
 *
 * <p>Scope: only method-body position — a marker token inside a {@link PsiErrorElement} whose
 * preceding non-whitespace sibling is a {@link PsiMethod} (the concise method's header). This
 * anchoring ensures we never colour a normal lambda {@code ->} or an assignment {@code =}.
 *
 * <p>The marker tokens are coloured with {@code KEYWORD} (theme-aware, bold), consistent with
 * how IntelliJ colours the {@code ->} in lambda expressions.
 */
public final class ConciseMarkerAnnotator implements Annotator {

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (element.getNode() == null) {
            return;
        }
        IElementType type = element.getNode().getElementType();
        String typeName = type.toString();

        if (!isMarkerToken(typeName)) {
            return;
        }

        // The marker must sit inside a PsiErrorElement...
        PsiElement parent = element.getParent();
        if (!(parent instanceof PsiErrorElement)) {
            return;
        }

        // ...that is in method-body position: the error element's preceding non-whitespace
        // sibling (inside the class body) is a PsiMethod.
        if (!isInMethodBodyPosition(parent)) {
            return;
        }

        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(element.getTextRange())
                .textAttributes(DefaultLanguageHighlighterColors.KEYWORD)
                .create();
    }

    /**
     * The marker token types the parser produces at the concise-body position.
     * ARROW = {@code ->}, EQ = {@code =}, DOUBLE_COLON = {@code ::}, ASTERISK = {@code *}.
     * We colour ARROW and EQ (the primary concise markers); DOUBLE_COLON for the = form's
     * method-reference operator is optional (included since users expect :: to colour).
     */
    private boolean isMarkerToken(String typeName) {
        return "ARROW".equals(typeName)
                || "EQ".equals(typeName)
                || "DOUBLE_COLON".equals(typeName);
    }

    /**
     * True if the given error element sits in method-body position: it is a direct child of
     * the class body, and the preceding non-whitespace sibling is a {@link PsiMethod} (the
     * concise method's intact header). This is the positional anchor that distinguishes a
     * concise body marker from a normal lambda {@code ->} or assignment {@code =}.
     */
    private boolean isInMethodBodyPosition(PsiElement errorElement) {
        PsiElement sibling = errorElement.getPrevSibling();
        while (sibling instanceof PsiWhiteSpace) {
            sibling = sibling.getPrevSibling();
        }
        // For the -> form: the error element directly follows the method header.
        if (sibling instanceof PsiMethod) {
            return true;
        }
        // For the = form: the parser fragments the construct, so the error element may follow
        // another error element or a stray PsiTypeElement that itself follows the method.
        // Walk backwards past error/type siblings to find the method.
        while (sibling != null && (sibling instanceof PsiErrorElement || isStrayTypeElement(sibling))) {
            sibling = sibling.getPrevSibling();
            while (sibling instanceof PsiWhiteSpace) {
                sibling = sibling.getPrevSibling();
            }
        }
        return sibling instanceof PsiMethod;
    }

    private boolean isStrayTypeElement(PsiElement element) {
        // The stray PsiTypeElement / PsiModifierList nodes the parser produces between markers
        // in the = form. Check by class name to avoid pulling in impl types.
        String cls = element.getClass().getSimpleName();
        return "PsiTypeElementImpl".equals(cls) || "PsiModifierListImpl".equals(cls);
    }
}
