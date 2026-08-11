package burp.extension;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.StandardCharsets;

public class HexViewerPanel extends JPanel {
    private final JTextArea offsetArea;
    private final JTextArea hexArea;
    private final JTextArea asciiArea;
    private int[] hexStart;
    private int[] hexEnd;
    private int[] asciiStart;
    private int[] asciiEnd;
    private int numBytes;
    private boolean syncing;

    public HexViewerPanel() {
        super(new BorderLayout());
        offsetArea = createGutterArea();
        hexArea = createArea();
        asciiArea = createArea();

        JPanel rowPanel = new JPanel();
        rowPanel.setLayout(new BoxLayout(rowPanel, BoxLayout.X_AXIS));
        rowPanel.add(offsetArea);
        rowPanel.add(hexArea);
        rowPanel.add(asciiArea);

        add(new JScrollPane(rowPanel), BorderLayout.CENTER);

        hexArea.addCaretListener(e -> onHexSelection());
        asciiArea.addCaretListener(e -> onAsciiSelection());
    }

    private JTextArea createArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setSelectionColor(new Color(0xB8CFE5));
        area.setSelectedTextColor(Color.BLACK);
        return area;
    }

    private JTextArea createGutterArea() {
        JTextArea area = createArea();
        area.setFocusable(false);
        area.setForeground(Color.GRAY);
        return area;
    }

    public void setData(byte[] data) {
        if (data == null || data.length == 0) {
            offsetArea.setText("");
            hexArea.setText("(empty)");
            asciiArea.setText("");
            numBytes = 0;
            hexStart = hexEnd = asciiStart = asciiEnd = new int[0];
            return;
        }
        int n = data.length;
        numBytes = n;
        hexStart = new int[n];
        hexEnd = new int[n];
        asciiStart = new int[n];
        asciiEnd = new int[n];

        Glyph[] glyphs = new Glyph[n];
        int[] glyphOfByte = new int[n];
        int gcount = 0;
        int i = 0;
        while (i < n) {
            int b = data[i] & 0xff;
            int len = utf8SequenceLen(b);
            boolean valid = len > 1 && isValidUtf8Seq(data, i, len);
            Glyph g;
            if (valid) {
                g = new Glyph(i, len, new String(data, i, len, StandardCharsets.UTF_8));
            } else if (b >= 0x20 && b < 0x7f) {
                g = new Glyph(i, 1, String.valueOf((char) b));
            } else {
                g = new Glyph(i, 1, ".");
            }
            for (int k = 0; k < g.len; k++) {
                glyphOfByte[i + k] = gcount;
            }
            glyphs[gcount] = g;
            gcount++;
            i += g.len;
        }

        StringBuilder offset = new StringBuilder();
        StringBuilder hex = new StringBuilder();
        StringBuilder ascii = new StringBuilder();

        for (int off = 0; off < n; off += 16) {
            offset.append(String.format("%08x  ", off));
            int end = Math.min(off + 16, n);
            for (int bIdx = off; bIdx < end; bIdx++) {
                hexStart[bIdx] = hex.length();
                hex.append(String.format("%02x", data[bIdx] & 0xff));
                hexEnd[bIdx] = hex.length();
                hex.append(' ');
                if ((bIdx % 16) == 7) hex.append(' ');
            }
            for (int bIdx = off; bIdx < end; bIdx++) {
                Glyph g = glyphs[glyphOfByte[bIdx]];
                if (g.start == bIdx) {
                    int pos = ascii.length();
                    ascii.append(g.display);
                    int epos = ascii.length();
                    for (int k = 0; k < g.len; k++) {
                        int bi = g.start + k;
                        if (bi < n) {
                            asciiStart[bi] = pos;
                            asciiEnd[bi] = epos;
                        }
                    }
                }
            }
            offset.append('\n');
            hex.append('\n');
            ascii.append('\n');
        }
        offsetArea.setText(offset.toString());
        hexArea.setText(hex.toString());
        asciiArea.setText(ascii.toString());
    }

    private static class Glyph {
        final int start;
        final int len;
        final String display;

        Glyph(int start, int len, String display) {
            this.start = start;
            this.len = len;
            this.display = display;
        }
    }

    private void onHexSelection() {
        if (syncing || hexStart == null) return;
        int s = hexArea.getSelectionStart();
        int e = hexArea.getSelectionEnd();
        int[] r = bytesForRange(s, e, hexStart, hexEnd);
        if (r == null) return;
        syncing = true;
        try {
            if (s == e) {
                asciiArea.setCaretPosition(asciiStart[r[0]]);
            } else {
                asciiArea.select(asciiStart[r[0]], asciiEnd[r[1]]);
            }
            revealSelection(asciiArea);
        } finally {
            syncing = false;
        }
    }

    private void onAsciiSelection() {
        if (syncing || asciiStart == null) return;
        int s = asciiArea.getSelectionStart();
        int e = asciiArea.getSelectionEnd();
        int[] r = bytesForRange(s, e, asciiStart, asciiEnd);
        if (r == null) return;
        syncing = true;
        try {
            if (s == e) {
                hexArea.setCaretPosition(hexStart[r[0]]);
            } else {
                hexArea.select(hexStart[r[0]], hexEnd[r[1]]);
            }
            revealSelection(hexArea);
        } finally {
            syncing = false;
        }
    }

    private void revealSelection(JTextArea area) {
        int start = area.getSelectionStart();
        int end = area.getSelectionEnd();
        try {
            Rectangle r1 = area.modelToView(start);
            Rectangle r2 = area.modelToView(end);
            if (r1 != null) {
                Rectangle r = r1;
                if (r2 != null) r = r1.union(r2);
                area.scrollRectToVisible(r);
            }
        } catch (Exception ignored) { }
        area.repaint();
    }

    private int[] bytesForRange(int selStart, int selEnd, int[] startArr, int[] endArr) {
        int min = -1;
        int max = -1;
        for (int b = 0; b < numBytes; b++) {
            if (startArr[b] < selEnd && endArr[b] > selStart) {
                if (min == -1 || b < min) min = b;
                if (b > max) max = b;
            }
        }
        if (min == -1) return null;
        return new int[]{min, max};
    }

    private int utf8SequenceLen(int b) {
        if ((b & 0x80) == 0) return 1;
        if ((b & 0xE0) == 0xC0) return 2;
        if ((b & 0xF0) == 0xE0) return 3;
        if ((b & 0xF8) == 0xF0) return 4;
        return 1;
    }

    private boolean isValidUtf8Seq(byte[] data, int start, int len) {
        if (len < 2 || start + len > data.length) return false;
        int b0 = data[start] & 0xff;
        if (len == 2 && b0 < 0xC2) return false;
        if (len == 3 && b0 == 0xE0 && (data[start + 1] & 0xff) < 0xA0) return false;
        if (len == 3 && b0 == 0xED && (data[start + 1] & 0xff) > 0x9F) return false;
        if (len == 4 && b0 == 0xF0 && (data[start + 1] & 0xff) < 0x90) return false;
        if (len == 4 && b0 == 0xF4 && (data[start + 1] & 0xff) > 0x8F) return false;
        if (len == 4 && b0 > 0xF4) return false;
        for (int k = 1; k < len; k++) {
            int c = data[start + k] & 0xff;
            if ((c & 0xC0) != 0x80) return false;
        }
        return true;
    }
}
