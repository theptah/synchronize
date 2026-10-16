package net.ptah.synchronize.compat;

import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;

//? if >=1.21.5
/*import java.net.URI;*/

 


public final class TextCompat {
    private TextCompat() {
    }

    public static ClickEvent openUrl(String url) {
        //? if >=1.21.5 {
        /*return new ClickEvent.OpenUrl(URI.create(url));
        *///?} else
        return new ClickEvent(ClickEvent.Action.OPEN_URL, url);
    }

    public static HoverEvent showText(Text text) {
        //? if >=1.21.5 {
        /*return new HoverEvent.ShowText(text);
        *///?} else
        return new HoverEvent(HoverEvent.Action.SHOW_TEXT, text);
    }
}
