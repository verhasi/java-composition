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
        return !isWithinConciseConstruct(element);
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
        // Cases 2 & 3: the element is part of a fragmented concise construct — a stray
        // type/ref/modifier node, or the trailing `;`/whitespace — that the parser scattered
        // between the method header and the terminating `;`. Find the element's top-level
        // sibling (child of the class body) and scan backwards over the fragment's constituent
        // nodes: if the scan crosses a concise marker (`->`/`=`) and reaches the PsiMethod, the
        // element belongs to that concise body.
        PsiElement topLevel = topLevelSiblingOf(element);
        if (topLevel != null && isConciseFragmentTail(topLevel)) {
            return true;
        }
        return false;
    }

    /**
     * Scan outward from {@code node} over the constituent nodes of a fragmented concise
     * construct. The construct spans from the method header to the terminating {@code ;}, so
     * {@code node} belongs to it if EITHER:
     * <ul>
     *   <li>scanning <b>backward</b> crosses a concise marker and reaches the {@link PsiMethod}
     *       header (covers the tail: stray names, trailing {@code ;}); or</li>
     *   <li>scanning <b>forward</b> crosses a concise marker before any real member (covers the
     *       whitespace/nodes that sit between the header and the marker).</li>
     * </ul>
     */
    private boolean isConciseFragmentTail(PsiElement node) {
        return scanReachesMarkerThenMethod(node) || scanForwardCrossesMarker(node);
    }

    /** Backward: cross a marker, reach the method header. */
    private boolean scanReachesMarkerThenMethod(PsiElement node) {
        boolean crossedMarker = false;
        PsiElement sib = skipWhitespace(node.getPrevSibling(), false);
        int guard = 0;
        while (sib != null && guard++ < 30) {
            if (sib instanceof PsiMethod) {
                return crossedMarker;
            }
            if (sib instanceof PsiErrorElement) {
                if (carriesMarker(sib.getText())) {
                    crossedMarker = true;
                }
                sib = skipWhitespace(sib.getPrevSibling(), false);
                continue;
            }
            if (isStrayFragmentNode(sib)) {
                sib = skipWhitespace(sib.getPrevSibling(), false);
                continue;
            }
            return false;
        }
        return false;
    }

    /**
     * Forward: from {@code node}, cross a concise marker before hitting a real member — but
     * only if the node is itself immediately preceded by the method header (so we only treat
     * the gap BETWEEN the header and its marker as concise, not arbitrary code).
     */
    private boolean scanForwardCrossesMarker(PsiElement node) {
        PsiElement before = skipWhitespace(node.getPrevSibling(), false);
        if (!(before instanceof PsiMethod)) {
            return false;
        }
        PsiElement sib = skipWhitespace(node.getNextSibling(), true);
        int guard = 0;
        while (sib != null && guard++ < 30) {
            if (sib instanceof PsiErrorElement && carriesMarker(sib.getText())) {
                return true;
            }
            if (sib instanceof PsiErrorElement || isStrayFragmentNode(sib) || sib instanceof PsiWhiteSpace) {
                sib = sib.getNextSibling();
                continue;
            }
            return false;
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
