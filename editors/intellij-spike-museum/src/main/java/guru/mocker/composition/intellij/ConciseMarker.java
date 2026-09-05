package guru.mocker.composition.intellij;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.Nullable;

/**
 * Detects a concise method-body marker ({@code ->} or {@code =}) and the payload range
 * within a text fragment. Used by both the injection and error-suppression spikes.
 *
 * <p>The marker is recognized only after a {@code )} (method parameter-list close), so an
 * ordinary assignment {@code =} is not mistaken for the method-reference marker.
 */
public enum ConciseMarker {
    ARROW("->"),
    METHOD_REF("=");

    private final String token;

    ConciseMarker(String token) {
        this.token = token;
    }

    /**
     * Detect a concise marker in {@code text}, requiring a preceding {@code )}.
     * @return the marker, or null if none present in method-body position
     */
    public static @Nullable ConciseMarker detect(String text) {
        int paren = text.indexOf(')');
        if (paren < 0) {
            return null;
        }
        String after = text.substring(paren + 1).stripLeading();
        if (after.startsWith("->")) {
            return ARROW;
        }
        // Method-ref '='; guard against '==' or '=>' etc.
        if (after.startsWith("=") && !after.startsWith("==")) {
            return METHOD_REF;
        }
        return null;
    }

    /**
     * The range of the payload (between the marker and the trailing {@code ;}) within
     * {@code text}. Returns null if the structure is not recognizable.
     */
    public @Nullable TextRange payloadRange(String text) {
        int paren = text.indexOf(')');
        if (paren < 0) {
            return null;
        }
        int markerStart = indexOfMarker(text, paren + 1);
        if (markerStart < 0) {
            return null;
        }
        int payloadStart = markerStart + token.length();
        // Skip whitespace after the marker.
        while (payloadStart < text.length() && Character.isWhitespace(text.charAt(payloadStart))) {
            payloadStart++;
        }
        int semi = text.indexOf(';', payloadStart);
        int payloadEnd = semi >= 0 ? semi : text.length();
        if (payloadEnd <= payloadStart) {
            return null;
        }
        return new TextRange(payloadStart, payloadEnd);
    }

    private int indexOfMarker(String text, int from) {
        for (int i = from; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            if (text.startsWith(token, i)) {
                return i;
            }
            // First non-space char is not our marker.
            return -1;
        }
        return -1;
    }
}
