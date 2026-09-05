package guru.mocker.composition.intellij;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * SPIKE — Lombok-style {@link HighlightInfoFilter} that hides false-positive highlights
 * (parse errors AND semantic "cannot resolve" errors) caused by concise / wildcard syntax.
 *
 * <p>Motivated by the JetBrains docs (controlling-highlighting.html), whose worked example is
 * almost exactly our case: a tool changing Java syntax → false "unresolved symbol" → suppress
 * via {@code HighlightInfoFilter} (as {@code LombokHighlightErrorFilter} does). Unlike
 * {@code HighlightErrorFilter} (parse errors only), this governs ALL highlighting.
 *
 * <p>Suppression rule (spike, deliberately conservative + heavily logged): hide an ERROR/
 * WARNING info if the PSI element at its offset is at/within a concise construct — i.e. it is
 * (or is adjacent to) a marker token {@code -> = :: * [ ]}, or lives inside a
 * {@link PsiErrorElement} whose text contains such a marker. Everything suppressed is logged
 * so the sandbox run can confirm we do NOT hide real errors (e.g. in the normal method).
 */
public class ConciseHighlightInfoFilter implements HighlightInfoFilter {

    private static final Logger LOG = Logger.getInstance(ConciseHighlightInfoFilter.class);

    @Override
    public boolean accept(@NotNull HighlightInfo highlightInfo, @Nullable PsiFile file) {
        if (file == null) {
            return true; // only act within a real file context
        }
        HighlightSeverity severity = highlightInfo.getSeverity();
        boolean isProblem = severity.compareTo(HighlightSeverity.WEAK_WARNING) >= 0;
        if (!isProblem) {
            return true; // leave informational/silent highlights (incl. our own coloring) alone
        }

        PsiElement element = file.findElementAt(highlightInfo.getStartOffset());
        if (element == null) {
            return true;
        }

        if (isInConciseContext(element)) {
            LOG.warn("[spike-infofilter] SUPPRESSING " + severity + " '"
                    + highlightInfo.getDescription() + "' at (" + highlightInfo.getStartOffset()
                    + "," + highlightInfo.getEndOffset() + ") on '" + snippet(element) + "'");
            return false; // hide this false-positive
        }
        return true; // real highlight — keep it
    }

    /**
     * True if this element is part of a concise/wildcard construct: it is a marker token,
     * or it sits inside a PsiErrorElement whose text carries a concise marker, or it is a
     * stray type/reference node (e.g. a field name misparsed as a type, like {@code store})
     * flanked by concise-marker error elements.
     */
    private boolean isInConciseContext(PsiElement element) {
        if (isMarkerToken(element)) {
            return true;
        }
        // Inside an error element that carries a marker?
        PsiErrorElement err = PsiTreeUtil.getParentOfType(element, PsiErrorElement.class, false);
        if (err != null && containsMarker(err.getText())) {
            return true;
        }
        // A stray name misparsed as a type/reference (e.g. the field `store` in `= store::x`).
        // The leaf identifier's enclosing PsiTypeElement / PsiJavaCodeReferenceElement is the
        // node that sits as a sibling between concise-marker error elements — check ITS
        // neighbours, not the identifier's.
        PsiElement strayTypeNode = enclosingStrayTypeNode(element);
        if (strayTypeNode != null && hasMarkerNeighbor(strayTypeNode)) {
            return true;
        }
        return false;
    }

    /**
     * Walk up from a leaf to the outermost PsiTypeElement / PsiJavaCodeReferenceElement that
     * the parser produced for a misparsed name, so we can inspect its siblings. Returns null
     * if the element is not inside such a node.
     */
    private PsiElement enclosingStrayTypeNode(PsiElement element) {
        PsiElement node = null;
        for (PsiElement p = element; p != null; p = p.getParent()) {
            String cls = p.getClass().getSimpleName();
            if (cls.equals("PsiTypeElementImpl") || cls.equals("PsiJavaCodeReferenceElementImpl")) {
                node = p; // keep climbing to the outermost such node
            } else if (node != null) {
                break; // climbed past the type/reference chain
            }
        }
        return node;
    }

    private boolean isMarkerToken(PsiElement element) {
        if (element.getNode() == null) {
            return false;
        }
        String t = element.getNode().getElementType().toString();
        return "ARROW".equals(t) || "EQ".equals(t) || "DOUBLE_COLON".equals(t)
                || "ASTERISK".equals(t) || "LBRACKET".equals(t) || "RBRACKET".equals(t);
    }

    private boolean hasMarkerNeighbor(PsiElement element) {
        return isMarkerErrorSibling(skipWhitespaceBackward(element.getPrevSibling()))
                || isMarkerErrorSibling(skipWhitespaceForward(element.getNextSibling()));
    }

    private PsiElement skipWhitespaceBackward(PsiElement e) {
        while (e != null && e.getText() != null && e.getText().isBlank()) {
            e = e.getPrevSibling();
        }
        return e;
    }

    private PsiElement skipWhitespaceForward(PsiElement e) {
        while (e != null && e.getText() != null && e.getText().isBlank()) {
            e = e.getNextSibling();
        }
        return e;
    }

    private boolean isMarkerErrorSibling(PsiElement sib) {
        return sib instanceof PsiErrorElement && containsMarker(sib.getText());
    }

    private boolean containsMarker(String text) {
        if (text == null) {
            return false;
        }
        return text.contains("->") || text.contains("::") || text.contains("*")
                || text.contains("[") || text.contains("]")
                || text.strip().equals("=");
    }

    private static String snippet(PsiElement e) {
        String t = e.getText();
        if (t == null) return "";
        t = t.replace("\n", "\\n");
        return t.length() > 30 ? t.substring(0, 30) + "…" : t;
    }
}
