import java.util.Iterator;
import java.util.List;

/**
 * PSI / HIGHLIGHT PROBE — the case neither Sample.java nor WildcardProbe.java covered:
 * per-method concise forms in a class that ACTUALLY implements an interface.
 *
 * Open in the sandbox IDE and observe (this answers the open question: does a bodyless
 * PsiMethod produced by a concise `->` / `=` form satisfy the interface-implementation
 * requirement, or does IntelliJ still flag class-level "must implement ..." and/or
 * per-method "missing method body"?):
 *
 *   1. Is the `class ImplProbe ... {` line red with "must implement all abstract methods"?
 *   2. Are the concise methods themselves flagged "missing method body"?
 *   3. Do our current Annotator + HighlightInfoFilter already handle these, or not?
 *
 * NOTE: this deliberately implements only SOME List methods concisely, so if IntelliJ
 * requires all of them, the class-level error will fire regardless — that itself is
 * informative about whether concise bodies "count" as implementations.
 */
class ImplProbe implements List<String> {

    private final List<String> impl = null;

    // Concise (-> and =) implementations of a few List methods:
    public int size()              -> impl.size();
    public boolean isEmpty()       -> impl.isEmpty();
    public boolean contains(Object o) = impl::contains;
    @Override
    public Iterator<String> iterator()  = impl::contains;

    // A normal method to compare error behaviour:
    public void clear() {
        impl.clear();
    }
}
