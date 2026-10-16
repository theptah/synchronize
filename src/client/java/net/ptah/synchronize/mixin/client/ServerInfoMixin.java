package net.ptah.synchronize.mixin.client;

import net.minecraft.client.network.ServerInfo;
import net.minecraft.nbt.NbtCompound;
//? if <1.21.5
import net.minecraft.nbt.NbtElement;
import net.ptah.synchronize.client.handshake.HandshakeServerEntry;
import net.ptah.synchronize.util.HandshakeCrypto;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerInfo.class)
public abstract class ServerInfoMixin implements HandshakeServerEntry {
    @Unique
    private static final String SYNCHRONIZE_NBT_KEY = "synchronizeHandshake";

    @Unique
    private String synchronize$handshakeCode = "";

    @Override
    public String synchronize$getHandshakeCode() {
        return synchronize$handshakeCode == null ? "" : synchronize$handshakeCode;
    }

    @Override
    public void synchronize$setHandshakeCode(String code) {
        String normalized = HandshakeCrypto.normalizeCode(code);
        if (normalized.length() > HandshakeCrypto.MAX_CODE_LENGTH) {
            normalized = normalized.substring(0, HandshakeCrypto.MAX_CODE_LENGTH);
        }
        synchronize$handshakeCode = normalized;
    }

    @Inject(method = "toNbt", at = @At("RETURN"))
    private void synchronize$writeHandshake(CallbackInfoReturnable<NbtCompound> cir) {
        String code = synchronize$getHandshakeCode();
        if (!code.isEmpty()) {
            cir.getReturnValue().putString(SYNCHRONIZE_NBT_KEY, code);
        }
    }

    @Inject(method = "fromNbt", at = @At("RETURN"))
    private static void synchronize$readHandshake(NbtCompound root, CallbackInfoReturnable<ServerInfo> cir) {
         
        //? if >=1.21.5 {
        /*if (cir.getReturnValue() instanceof HandshakeServerEntry entry) {
            root.getString(SYNCHRONIZE_NBT_KEY).ifPresent(entry::synchronize$setHandshakeCode);
        }
        *///?} else {
        if (root.contains(SYNCHRONIZE_NBT_KEY, NbtElement.STRING_TYPE) && cir.getReturnValue() instanceof HandshakeServerEntry entry) {
            entry.synchronize$setHandshakeCode(root.getString(SYNCHRONIZE_NBT_KEY));
        }
        //?}
    }

    @Inject(method = "copyFrom", at = @At("TAIL"))
    private void synchronize$copyHandshake(ServerInfo other, CallbackInfo ci) {
        if (other instanceof HandshakeServerEntry entry) {
            synchronize$setHandshakeCode(entry.synchronize$getHandshakeCode());
        }
    }

    @Inject(method = "copyWithSettingsFrom", at = @At("TAIL"))
    private void synchronize$copyHandshakeWithSettings(ServerInfo other, CallbackInfo ci) {
        if (other instanceof HandshakeServerEntry entry) {
            synchronize$setHandshakeCode(entry.synchronize$getHandshakeCode());
        }
    }
}
