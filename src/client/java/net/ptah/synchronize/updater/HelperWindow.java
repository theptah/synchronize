package net.ptah.synchronize.updater;

import net.ptah.synchronize.client.profile.ProfileStorage;
import javax.swing.*;
import javax.swing.event.MouseInputListener;
import java.awt.*;
import java.awt.event.MouseEvent;

public final class HelperWindow extends JFrame implements MouseInputListener {
    private static final Color BACKGROUND = new Color(0x24282F);
    private static final Color HEADER = new Color(0x2E333C);
    private static final Color TEXT = new Color(0xEDF0F5);
    private static final Color MUTED = new Color(0xB2BDCD);
    private final JLabel heading = label("Preparing changes", 19, true, TEXT);
    private final JLabel route = label("Please wait...", 13, false, MUTED);
    private final JLabel status = label("Preparing files...", 12, false, new Color(0xC4CBD5));
    private final JLabel percent = label("", 13, true, TEXT);
    private final JLabel path = label("", 12, false, MUTED);
    private final JLabel count = label("", 12, false, MUTED);
    private final JLabel footer = label("Keep this window open. Do not launch Minecraft yet.", 12, false, MUTED);
    private final JProgressBar progress = new JProgressBar(0, 100);
    private final Timer refresh;
    private volatile ProfileStorage.Progress latest = new ProfileStorage.Progress("preparing", "", 0, -1);
    private volatile String headingText = "Preparing changes";
    private volatile String routeText = "Please wait...";
    private volatile String completionText = "Update complete.";
    private volatile boolean failed;
    private Point dragOffset;

    public HelperWindow() {
        super("Synchronize Helper");
        setUndecorated(true);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setResizable(false);
        if (Toolkit.getDefaultToolkit().isAlwaysOnTopSupported()) setAlwaysOnTop(true);
        JPanel panel = new JPanel(null);
        panel.setPreferredSize(new Dimension(520, 240));
        panel.setBackground(BACKGROUND);
        panel.setBorder(BorderFactory.createLineBorder(new Color(0x545E6D)));
        JPanel header = new JPanel(null);
        header.setBackground(HEADER);
        header.setBounds(1, 1, 518, 39);
        JLabel brand = label("Synchronize", 14, true, TEXT);
        JLabel helper = label("Helper", 12, false, MUTED);
        brand.setBounds(23, 0, 300, 38);
        helper.setHorizontalAlignment(SwingConstants.RIGHT);
        helper.setBounds(397, 0, 98, 38);
        header.add(brand); header.add(helper);
        for (Component component : new Component[] {header, brand, helper}) {
            component.addMouseListener(this); component.addMouseMotionListener(this);
            component.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        }
        panel.add(header);
        divider(panel, 1, 40, 518);
        add(panel, heading, 24, 53, 472, 25);
        add(panel, route, 24, 80, 472, 20);
        add(panel, status, 24, 108, 410, 20);
        percent.setHorizontalAlignment(SwingConstants.RIGHT);
        add(panel, percent, 440, 108, 56, 20);
        progress.setUI(new HelperProgressBarUI());
        progress.setBorderPainted(false);
        progress.setIndeterminate(true);
        progress.setStringPainted(false);
        progress.setBackground(new Color(0x454D59));
        progress.setBounds(24, 133, 472, 16);
        progress.setOpaque(false);
        panel.add(progress);
        add(panel, path, 24, 166, 340, 20);
        count.setHorizontalAlignment(SwingConstants.RIGHT);
        add(panel, count, 372, 166, 124, 20);
        divider(panel, 24, 198, 472);
        footer.setHorizontalAlignment(SwingConstants.CENTER);
        add(panel, footer, 24, 208, 472, 23);
        setContentPane(panel);
        pack();
        setLocationRelativeTo(null);
        refresh = new Timer(75, event -> render());
        refresh.start();
    }

    private static JLabel label(String text, int size, boolean bold, Color color) {
        JLabel label = new JLabel(text);
        label.putClientProperty("html.disable", Boolean.TRUE);
        label.putClientProperty(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        String family = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win") ? "Segoe UI" : "SansSerif";
        label.setFont(new Font(family, bold ? Font.BOLD : Font.PLAIN, size));
        label.setForeground(color);
        return label;
    }

    private static void add(JPanel panel, JComponent component, int x, int y, int width, int height) {
        component.setBounds(x, y, width, height); panel.add(component);
    }

    private static void divider(JPanel panel, int x, int y, int width) {
        JPanel line = new JPanel(); line.setBackground(new Color(0x3B424D));
        line.setBounds(x, y, width, 1); panel.add(line);
    }

    public void context(String title, String profiles) { headingText = title; routeText = profiles; }
    public void report(ProfileStorage.Progress value) { latest = value; }
    public void complete(String text) { completionText = text; report(new ProfileStorage.Progress("complete", "", 1, 1)); }
    public void fail(String message) {
        failed = true;
        headingText = "Operation failed";
        report(new ProfileStorage.Progress("failed", message, 0, -1));
    }

    private void render() {
        ProfileStorage.Progress value = latest;
        boolean complete = value.phase().equals("complete");
        heading.setText(complete ? "Ready to play" : headingText);
        heading.setForeground(failed ? new Color(0xFF9A9A) : TEXT);
        route.setText(routeText); route.setToolTipText("Profiles: " + routeText);
        status.setText(switch (value.phase()) {
            case "scanning" -> "Scanning profile files...";
            case "saving" -> "Saving current profile...";
            case "backup" -> "Backing up current files...";
            case "preparing-files" -> "Preparing selected profile...";
            case "removing" -> "Removing previous profile files...";
            case "applying" -> "Applying profile files...";
            case "snapshot" -> "Saving selected profile...";
            case "saving-metadata", "snapshot-metadata" -> "Saving profile data...";
            case "rollback-removing" -> "Removing interrupted changes...";
            case "restoring" -> "Restoring saved files...";
            case "finishing" -> "Finishing profile switch...";
            case "updating" -> "Applying pending files...";
            case "deleting" -> "Deleting pending files...";
            case "complete" -> completionText;
            case "failed" -> "Changes could not be completed.";
            default -> "Preparing files...";
        });
        boolean unknown = value.total() < 0 && !failed;
        if (progress.isIndeterminate() != unknown) progress.setIndeterminate(unknown);
        int amount = value.total() > 0 ? (int) Math.min(100, Math.max(0, value.completed() * 100.0 / value.total())) : 0;
        progress.setValue(amount);
        percent.setText(unknown || failed || value.total() == 0 ? "" : amount + "%");
        path.setText(value.path()); path.setToolTipText("File: " + value.path());
        count.setText(value.total() > 0 && !complete ? value.completed() + " / " + value.total() + " files" : "");
        footer.setText(failed ? "Review the error before restarting Minecraft."
                : complete ? "You can launch Minecraft now."
                : "Keep this window open. Do not launch Minecraft yet.");
    }

    @Override public void dispose() { refresh.stop(); super.dispose(); }
    @Override public void mousePressed(MouseEvent event) {
        if (SwingUtilities.isLeftMouseButton(event)) dragOffset = new Point(event.getXOnScreen() - getX(), event.getYOnScreen() - getY());
    }
    @Override public void mouseDragged(MouseEvent event) {
        if (dragOffset != null) setLocation(event.getXOnScreen() - dragOffset.x, event.getYOnScreen() - dragOffset.y);
    }
    @Override public void mouseReleased(MouseEvent event) { dragOffset = null; }
    @Override public void mouseClicked(MouseEvent event) {}
    @Override public void mouseEntered(MouseEvent event) {}
    @Override public void mouseExited(MouseEvent event) {}
    @Override public void mouseMoved(MouseEvent event) {}
}
