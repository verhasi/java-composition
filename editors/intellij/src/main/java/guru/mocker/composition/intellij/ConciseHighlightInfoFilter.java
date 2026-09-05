package guru.mocker.composition.intellij;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Suppresses the false error/warning highlights IntelliJ's Java parser produces on concise
 * method bodies ({@code -> expr;} / {@code = ref;}), which it cannot parse as standard Java.
 *
 * <p>Follows the strategy IntelliJ documents for tools that change Java syntax (and that
 * Lombok uses for its generated members): a {@link HighlightInfoFilter} decides which
 * {@link HighlightInfo}s are shown. We suppress ONLY highlights whose element sits within a
 * concise construct in method-body position, so genuine errors in normal code are untouched.
 *
 * <p>Covers, at concise constructs only:
 * <ul>
 *   <li>parse errors on the markers ({@code Unexpected token}, {@code '{' expected}, …);</li>
 *   <li>semantic "cannot resolve" on a field misparsed as a type (e.g. {@code store} in
 *       {@code = store::size});</li>
 *   <li>the trailing {@code ;}/whitespace parse errors at the end of a concise body.</li>
 * </ul>
 *
 * <p>Deliberately does NOT call {@code PsiClass.getMethods()} anywhere (that re-enters augment
 * providers and can recurse); it only inspects the local sibling structure of the element.
 */
public final class ConciseHighlightInfoFilter implements HighlightInfoFilter {

    private static final com.intellij.openapi.diagnostic.Logger LOG =
            com.intellij.openapi.diagnostic.Logger.getInstance(ConciseHighlightInfoFilter.class);

    @Override
    public boolean accept(@NotNull HighlightInfo highlightInfo, @Nullable PsiFile file) {
        if (file == null) {
            return true;
        }
        // Only consider problems (error/warning); leave INFO/silent highlights (incl. our own
        // marker colouring) alone.
        if (highlightInfo.getSeverity().compareTo(HighlightSeverity.WEAK_WARNING) < 0) {
            return true;
        }
        PsiElement element = file.findElementAt(highlightInfo.getStartOffset());
        if (element == null) {
            return true;
        }
        boolean within = isWithinConciseConstruct(element);
        // TEMP DIAGNOSTIC (A2.2 debugging) — remove before release.
        String cls = element.getClass().getSimpleName();
        String parent = element.getParent() == null ? "null" : element.getParent().getClass().getSimpleName();
        LOG.warn("[a2-filter] " + (within ? "SUPPRESS" : "KEEP   ") + " '" + highlightInfo.getDescription()
                + "' at " + highlightInfo.getStartOffset() + " elem=" + cls + "('"
                + safe(element.getText()) + "') parent=" + parent);
        return !within;
    }

    private static String safe(String t) {
        if (t == null) return "";
        t = t.replace("\n", "\\n");
        return t.length() > 20 ? t.substring(0, 20) + "…" : t;
    }

    /**
     * True if {@code element} belongs to a concise method-body construct in method-body
     * position. Recognizes three cases, all anchored to a nearby {@link PsiMethod}:
     * <ol>
     *   <li>the element is inside a {@link PsiErrorElement} carrying a concise marker;</li>
     *   <li>the element is a stray type/reference node flanked by concise-marker errors
     *       (a field misparsed as a type in the {@code =} form);</li>
     *   <li>the element is the trailing {@code ;}/whitespace immediately after such a
     *       construct.</li>
     * </ol>
     */
    private boolean isWithinConciseConstruct(PsiElement element) {
        // Case 1: inside a marker-bearing error element.
        PsiErrorElement enclosingError = PsiTreeUtil.getParentOfType(element, PsiErrorElement.class, false);
        if (enclosingError != null && carriesMarker(enclosingError.getText())
                && anchoredToMethod(enclosingError)) {
            return true;
        }
        // Cases 2 & 3: a stray node (type/ref/`;`/whitespace) adjacent to a marker error, all
        // in method-body position. Find the top-level sibling (child of the class body) that
        // this element belongs to, then check its neighbourhood.
        PsiElement topLevel = topLevelSiblingOf(element);
        if (topLevel != null && hasAdjacentMarkerError(topLevel) && anchoredToMethod(topLevel)) {
            return true;
        }
        return false;
    }

    /** The concise markers, as they appear in a fragment's text. */
    private boolean carriesMarker(@Nullable String text) {
        if (text == null) {
            return false;
        }
        return text.contains("->") || text.contains("::") || text.contains("*")
                || text.strip().equals("=") || text.contains("[") || text.contains("]");
    }

    /**
     * Walk up to the node that is a direct child of the class body (i.e. a sibling of the
     * method declarations), so we can examine its neighbours at that level.
     */
    private PsiElement topLevelSiblingOf(PsiElement element) {
        PsiElement e = element;
        while (e != null && e.getParent() != null && !isClassBody(e.getParent())) {
            e = e.getParent();
        }
        return e;
    }

    private boolean isClassBody(PsiElement element) {
        // PsiClass holds members directly; the class body braces are tokens within PsiClass.
        return element instanceof com.intellij.psi.PsiClass;
    }

    /** True if a marker-bearing PsiErrorElement is an immediate (whitespace-skipping) neighbour. */
    private boolean hasAdjacentMarkerError(PsiElement node) {
        return isMarkerError(skipWhitespace(node.getPrevSibling(), false))
                || isMarkerError(skipWhitespace(node.getNextSibling(), true));
    }

    private boolean isMarkerError(PsiElement e) {
        return e instanceof PsiErrorElement && carriesMarker(e.getText());
    }

    /**
     * True if this construct is in method-body position: scanning backwards over the concise
     * fragment's stray/error/whitespace siblings reaches a {@link PsiMethod} (its intact
     * header). This is the anchor that prevents suppressing errors in unrelated code.
     */
    private boolean anchoredToMethod(PsiElement node) {
        PsiElement sib = skipWhitespace(node.getPrevSibling(), false);
        int guard = 0;
        while (sib != null && guard++ < 12) {
            if (sib instanceof PsiMethod) {
                return true;
            }
            if (sib instanceof PsiErrorElement || isStrayFragmentNode(sib)) {
                sib = skipWhitespace(sib.getPrevSibling(), false);
                continue;
            }
            break;
        }
        return false;
    }

    private boolean isStrayFragmentNode(PsiElement e) {
        String cls = e.getClass().getSimpleName();
        return "PsiTypeElementImpl".equals(cls)
                || "PsiModifierListImpl".equals(cls)
                || "PsiJavaCodeReferenceElementImpl".equals(cls);
    }

    private PsiElement skipWhitespace(PsiElement e, boolean forward) {
        while (e instanceof PsiWhiteSpace) {
            e = forward ? e.getNextSibling() : e.getPrevSibling();
        }
        return e;
    }
}
