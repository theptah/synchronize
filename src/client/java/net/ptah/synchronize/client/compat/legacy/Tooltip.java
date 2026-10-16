package net.ptah.synchronize.client.compat.legacy;

//? if <1.19.3 {
/*import java.util.function.Supplier;
import net.minecraft.text.Text;

 
public final class Tooltip {
    private final Supplier<Text> text;

    private Tooltip(Supplier<Text> text) {
        this.text = text;
    }

    public static Tooltip of(Text text) {
        return new Tooltip(() -> text);
    }

     
    public static Tooltip dynamic(Supplier<Text> text) {
        return new Tooltip(text);
    }

    public Text text() {
        return this.text.get();
    }
}
*///?}
