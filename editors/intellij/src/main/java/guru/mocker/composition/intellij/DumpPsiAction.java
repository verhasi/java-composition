package guru.mocker.composition.intellij;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * SPIKE INSTRUMENT — dumps the PSI tree of the current file to the IDE log.
 *
 * <p>The single question of the injection spike: for a concise method body
 * ({@code -> expr;} / {@code = ref;}), which PSI element holds the payload, and does it
 * implement {@link PsiLanguageInjectionHost} (a prerequisite for language injection)?
 *
 * <p>This action walks the PSI and logs each node's class, text range, and whether it is an
 * injection host — with extra emphasis on {@code PsiErrorElement}s (where the Java parser
 * broke on the concise syntax). Read the output in idea.log or via Help | Show Log.
 */
public class DumpPsiAction extends AnAction {

    private static final Logger LOG = Logger.getInstance(DumpPsiAction.class);

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
        if (file == null) {
            notify(e, "No PSI file in context.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("=== PSI DUMP: ").append(file.getName()).append(" ===\n");
        dump(file, 0, sb);
        String dump = sb.toString();
        LOG.warn(dump); // WARN so it is easy to find in idea.log
        notify(e, "PSI dumped to idea.log (" + dump.split("\n").length + " lines). "
                + "See Help | Show Log in Finder.");
    }

    private void dump(PsiElement element, int depth, StringBuilder sb) {
        String indent = "  ".repeat(depth);
        boolean isHost = element instanceof PsiLanguageInjectionHost;
        String cls = element.getClass().getName();
        String kind = element.getNode() != null ? String.valueOf(element.getNode().getElementType()) : "?";
        String text = element.getText();
        String snippet = text.length() > 40 ? text.substring(0, 40).replace("\n", "\\n") + "…" : text.replace("\n", "\\n");

        sb.append(indent)
          .append(isHost ? "[HOST] " : "")
          .append(cls)
          .append("  type=").append(kind)
          .append("  range=").append(element.getTextRange())
          .append("  text='").append(snippet).append("'\n");

        for (PsiElement child : element.getChildren()) {
            dump(child, depth + 1, sb);
        }
    }

    private void notify(AnActionEvent e, String message) {
        try {
            NotificationGroupManager.getInstance()
                    .getNotificationGroup("Java Composition Spike")
                    .createNotification(message, NotificationType.INFORMATION)
                    .notify(e.getProject());
        } catch (Exception ignored) {
            // Notification group may be absent in the spike; the log dump is the primary output.
        }
    }
}
