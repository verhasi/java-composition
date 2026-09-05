package guru.mocker.composition.intellij;

import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * SPIKE — attempts to inject the concise-body payload as a Java expression.
 *
 * <p>Technique (per the JetBrains docs' XML→Java example): wrap the payload with a
 * prefix/suffix so it parses as valid Java, e.g. {@code class X{Object m(){return } + payload
 * + ;}}} — giving the payload real Java highlighting/completion.
 *
 * <p>The blocker this spike probes: {@link MultiHostRegistrar#addPlace} requires a
 * {@link PsiLanguageInjectionHost}. Our payload sits where the Java parser broke (likely a
 * {@link PsiErrorElement}), which is NOT a host. This injector therefore only acts when it
 * finds a host at/around the concise position; otherwise it logs that no host is available —
 * which is itself the key spike finding.
 */
public class ConcisePayloadInjector implements MultiHostInjector {

    private static final Logger LOG = Logger.getInstance(ConcisePayloadInjector.class);

    @Override
    public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
        // We register interest broadly (see elementsToInjectIn) and inspect the context.
        if (!(context instanceof PsiLanguageInjectionHost host)) {
            LOG.warn("[spike] context is NOT a PsiLanguageInjectionHost: " + context.getClass().getName()
                    + " text='" + snippet(context) + "'");
            return;
        }
        if (!host.isValidHost()) {
            LOG.warn("[spike] host present but isValidHost()==false: " + context.getClass().getName());
            return;
        }

        String text = context.getText();
        // Only attempt when this element's text looks like a concise payload region.
        ConciseMarker marker = ConciseMarker.detect(text);
        if (marker == null) {
            return;
        }

        TextRange payloadRange = marker.payloadRange(text);
        if (payloadRange == null || payloadRange.isEmpty()) {
            return;
        }

        LOG.warn("[spike] INJECTING Java expression into host " + context.getClass().getName()
                + " payloadRange=" + payloadRange + " text='" + snippet(context) + "'");

        // Wrap the payload so it parses as a Java expression.
        registrar.startInjecting(JavaLanguage.INSTANCE);
        registrar.addPlace("class X{Object m(){return ", ";}}", host, payloadRange);
        registrar.doneInjecting();
    }

    @Override
    public @NotNull List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
        // Broad on purpose for the spike: we want to see whatever the parser produces.
        // PsiErrorElement is included to confirm (negatively) that it is not a host.
        return List.of(PsiElement.class);
    }

    private static String snippet(PsiElement e) {
        String t = e.getText();
        if (t == null) return "";
        t = t.replace("\n", "\\n");
        return t.length() > 40 ? t.substring(0, 40) + "…" : t;
    }
}
