package net.ptah.synchronize.updater;

import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicProgressBarUI;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

public final class HelperProgressBarUI extends BasicProgressBarUI {
    @Override public void paint(Graphics graphics, JComponent component) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double width = component.getWidth();
            double height = Math.min(8, component.getHeight());
            double y = (component.getHeight() - height) / 2.0;
            RoundRectangle2D track = new RoundRectangle2D.Double(0, y, width, height, height, height);
            g.setColor(new Color(0x454D59));
            g.fill(track);
            g.clip(track);
            g.setColor(new Color(0x54CFF5));
            if (progressBar.isIndeterminate()) {
                Rectangle box = getBox(null);
                if (box != null) g.fill(new RoundRectangle2D.Double(box.x, y, box.width, height, height, height));
            } else {
                double filled = width * progressBar.getPercentComplete();
                if (filled > 0) g.fill(new RoundRectangle2D.Double(0, y, filled, height, height, height));
            }
        } finally { g.dispose(); }
    }
}
