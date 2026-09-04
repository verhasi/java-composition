package guru.mocker.composition.intellij;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * SPIKE — tests whether an {@link Annotator} can COLOR the concise marker tokens, INCLUDING
 * tokens that live inside a {@link com.intellij.psi.PsiErrorElement}.
 *
 * <p>The open question (not answered by the injection spike): annotators run per PSI element;
 * it is unverified whether they are invoked on tokens nested in error elements, and whether
 * any color we add renders or is overridden by IntelliJ's error highlighting.
 *
 * <p>This annotator targets the marker token types the PSI dumps showed at concise/wildcard
 * positions: {@code ARROW}, {@code EQ} (method-body {@code =}), {@code DOUBLE_COLON},
 * {@code ASTERISK}. It colors each as an operation sign and logs every hit, so the sandbox
 * run reveals (a) whether it fires on error-element children and (b) whether the color shows.
 */
public class ConciseMarkerAnnotator implements Annotator {

    private static final Logger LOG = Logger.getInstance(ConciseMarkerAnnotator.class);

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (element.getNode() == null) {
            return;
        }
        IElementType type = element.getNode().getElementType();
        String name = type.toString();

        // Only the marker tokens we care about (avoid noise from every element).
        boolean isMarker = "ARROW".equals(name)
                || "DOUBLE_COLON".equals(name)
                || "ASTERISK".equals(name)
                || ("EQ".equals(name) && insideErrorElement(element)); // '=' only when in an error (method-body position)

        if (!isMarker) {
            return;
        }

        boolean inError = insideErrorElement(element);
        LOG.warn("[spike-annotator] coloring marker '" + element.getText() + "' type=" + name
                + " inErrorElement=" + inError + " range=" + element.getTextRange());

        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(element.getTextRange())
                .textAttributes(DefaultLanguageHighlighterColors.OPERATION_SIGN)
                .create();
    }

    private boolean insideErrorElement(PsiElement element) {
        for (PsiElement p = element.getParent(); p != null; p = p.getParent()) {
            if (p instanceof com.intellij.psi.PsiErrorElement) {
                return true;
            }
        }
        return false;
    }
}
