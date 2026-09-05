package guru.mocker.composition.intellij;

import com.intellij.codeInsight.highlighting.HighlightErrorFilter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiErrorElement;
import org.jetbrains.annotations.NotNull;

/**
 * SPIKE — attempts to suppress the false {@code '{' expected} error the Java parser emits at
 * a concise method body.
 *
 * <p>Returns {@code false} (do not highlight) when the error element sits at a concise
 * method-body position — heuristically, when the error's surrounding text contains a concise
 * marker ({@code -> } / {@code = }) right after a {@code )}. This is deliberately conservative
 * for the spike; the exact matching will be refined once the PSI dump shows the real shape of
 * the error element.
 */
public class ConciseErrorFilter extends HighlightErrorFilter {

    private static final Logger LOG = Logger.getInstance(ConciseErrorFilter.class);

    @Override
    public boolean shouldHighlightErrorElement(@NotNull PsiErrorElement element) {
        String description = element.getErrorDescription();
        // Context text around the error: the parent (method) text is the most reliable signal.
        String parentText = element.getParent() != null ? element.getParent().getText() : "";

        boolean looksLikeConcise = ConciseMarker.detect(parentText) != null;
        boolean braceExpected = description != null && description.contains("'{'");

        if (looksLikeConcise && braceExpected) {
            LOG.warn("[spike] suppressing error '" + description + "' at " + element.getTextRange()
                    + " (concise body detected in parent)");
            return false; // do not paint the squiggle
        }
        // Log misses too, so the spike shows what we could NOT match.
        if (braceExpected) {
            LOG.warn("[spike] NOT suppressing '{' error at " + element.getTextRange()
                    + " — no concise marker found in parent text='"
                    + snippet(parentText) + "'");
        }
        return true; // default: show the error
    }

    private static String snippet(String t) {
        if (t == null) return "";
        t = t.replace("\n", "\\n");
        return t.length() > 60 ? t.substring(0, 60) + "…" : t;
    }
}
