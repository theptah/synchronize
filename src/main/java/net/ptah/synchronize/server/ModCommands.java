package net.ptah.synchronize.server;

import net.ptah.synchronize.compat.ServerCompat;

import net.ptah.synchronize.util.Translations;
import net.ptah.synchronize.Synchronize;
import net.ptah.synchronize.config.FileRegistry;
import net.ptah.synchronize.config.FileRegistry.FileEntry;
import net.ptah.synchronize.config.ModConfig;
import net.ptah.synchronize.util.ModJar;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public class ModCommands {
    private static final String DESTINATION_FLAG = "destination";
    private static final String OPTIONAL_FLAG = "optional";
    private static final String SHALLOW_FLAG = "shallow";
    private static final String RESTART_FLAG = "restart";

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
            CommandManager.literal("synchronize")
                .requires(source -> source.getEntity() == null)
                .then(CommandManager.literal("help")
                    .requires(source -> source.getEntity() == null)
                    .executes(ModCommands::executeHelp))
                .then(CommandManager.literal("add_files")
                    .requires(source -> source.getEntity() == null)
                    .then(CommandManager.argument("args", StringArgumentType.greedyString())
                        .suggests(ModCommands::suggestAddFileArgs)
                        .executes(ModCommands::executeAddFiles)))
                .then(CommandManager.literal("remove_files")
                    .requires(source -> source.getEntity() == null)
                    .then(CommandManager.argument("path", StringArgumentType.greedyString())
                        .suggests(ModCommands::suggestTrackedFiles)
                        .executes(ModCommands::executeRemoveFiles)))
                .then(CommandManager.literal("refresh")
                    .requires(source -> source.getEntity() == null)
                    .executes(ModCommands::executeRefresh))
        ));
    }

    private static int executeRefresh(CommandContext<ServerCommandSource> context) {
        if (context.getSource().getEntity() != null) {
            context.getSource().sendError(Translations.literal("synchronize.command.console_only"));
            return 0;
        }

        FileRegistry.refreshHashes();

        ConfigHolder<ModConfig> holder = AutoConfig.getConfigHolder(ModConfig.class);
        holder.load();
        Synchronize.updateConfig(holder.getConfig());
        Synchronize.ensureHandshake();
        Translations.clearCache();
        ServerSyncHandler.reloadRateLimiterFromConfig(holder.getConfig());
        ServerCompat.feedback(context.getSource(), () -> Translations.literal("synchronize.command.refresh.done"), true);
        return 1;
    }

    private static int executeAddFiles(CommandContext<ServerCommandSource> context) {
        if (context.getSource().getEntity() != null) {
            context.getSource().sendError(Translations.literal("synchronize.command.console_only"));
            return 0;
        }

        String rawArgs = StringArgumentType.getString(context, "args");
        List<String> tokens = tokenize(rawArgs);

        if (tokens.isEmpty()) {
            context.getSource().sendError(Translations.literal("synchronize.command.add_files.usage", addFilesSyntax()));
            return 0;
        }

        String inputPath = normalizeInputPath(tokens.get(0));
        boolean optional = false;
        boolean shallow = false;
        boolean restart = false;
        String destinationOverride = null;

        for (int i = 1; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (token.equalsIgnoreCase(OPTIONAL_FLAG)) {
                optional = true;
            } else if (token.equalsIgnoreCase(SHALLOW_FLAG)) {
                shallow = true;
            } else if (token.equalsIgnoreCase(RESTART_FLAG)) {
                restart = true;
            } else if (token.equalsIgnoreCase(DESTINATION_FLAG)) {
                if (i + 1 >= tokens.size()) {
                    context.getSource().sendError(Translations.literal("synchronize.command.add_files.destination_missing"));
                    return 0;
                }
                destinationOverride = normalizeInputPath(tokens.get(++i));
            } else {
                final String finalToken = token;
                context.getSource().sendError(Translations.literal("synchronize.command.add_files.unknown_argument", finalToken));
                return 0;
            }
        }

        Path gameDir = FabricLoader.getInstance().getGameDir();
        Path targetPath = resolveWithinGameDir(context, gameDir, inputPath);
        if (targetPath == null) {
            return 0;
        }

        File targetFile = targetPath.toFile();
        if (!targetFile.exists()) {
            context.getSource().sendError(Translations.literal("synchronize.command.add_files.not_found"));
            return 0;
        }

        if (restart && destinationTargetsModsFolder(inputPath, destinationOverride)) {
            context.getSource().sendError(Translations.literal("synchronize.command.add_files.restart_invalid"));
            return 0;
        }

        int addedFiles = 0;
        try {
            if (targetFile.isFile()) {
                if (ModJar.isSynchronizeJar(targetPath)) {
                    context.getSource().sendError(Translations.literal("synchronize.command.add_files.main_mod_ignored", inputPath));
                } else if (targetFile.length() == 0) {
                    ServerCompat.feedback(context.getSource(), () -> Translations.literal("synchronize.command.add_files.skipped_empty", inputPath), false);
                } else if (addSingleFile(gameDir, targetPath, destinationOverride, optional, restart)) {
                    ++addedFiles;
                }
            } else if (targetFile.isDirectory()) {
                final String finalDestinationOverride = destinationOverride;
                final boolean finalOptional = optional;
                final boolean finalRestart = restart;
                int maxDepth = shallow ? 1 : Integer.MAX_VALUE;

                try (Stream<Path> walk = Files.walk(targetPath, maxDepth)) {
                    addedFiles = walk.filter(Files::isRegularFile).mapToInt(p -> {
                        if (p.toFile().length() == 0) {
                            return 0;
                        }
                        if (ModJar.isSynchronizeJar(p)) {
                            String skipped = gameDir.relativize(p).toString().replace('\\', '/');
                            ServerCompat.feedback(context.getSource(), () -> Translations.literal("synchronize.command.add_files.main_mod_ignored", skipped), false);
                            return 0;
                        }
                        String destForFile = null;
                        if (finalDestinationOverride != null) {
                            String subPath = targetPath.relativize(p).toString().replace('\\', '/');
                            destForFile = finalDestinationOverride.isEmpty() ? subPath : finalDestinationOverride + "/" + subPath;
                        }
                        return addSingleFile(gameDir, p, destForFile, finalOptional, finalRestart) ? 1 : 0;
                    }).sum();
                }
            }

            if (addedFiles > 0) {
                FileRegistry.save();
            }

            final int finalAddedFiles = addedFiles;
            final boolean finalOptionalFeedback = optional;
            final boolean finalRestartFeedback = restart;
            final String finalDestFeedback = destinationOverride;

            if (addedFiles > 0) {
                ServerCompat.feedback(context.getSource(), () -> Text.literal(Translations.server("synchronize.command.add_files.added", finalAddedFiles,
                    Translations.server(finalOptionalFeedback ? "synchronize.command.add_files.optional" : "synchronize.command.add_files.mandatory"))
                    + (finalDestFeedback != null ? Translations.server("synchronize.command.add_files.destination_suffix", finalDestFeedback) : "")
                    + (finalRestartFeedback ? Translations.server("synchronize.command.add_files.restart_suffix") : "") + "."), true);
            }
        } catch (Exception e) {
            Synchronize.LOGGER.error("Error reading files during command execution", e);
            context.getSource().sendError(Translations.literal("synchronize.command.add_files.read_error"));
        }
        return addedFiles;
    }

    private static boolean addSingleFile(Path gameDir, Path file, String destinationOverride, boolean optional, boolean restart) {
        try {
            if (Files.size(file) == 0) {
                return false;
            }

            String relativePath = gameDir.relativize(file).toString().replace('\\', '/');
            if (relativePath.contains("..")) {
                return false;
            }

            if (ModJar.isSynchronizeJar(file)) {
                return false;
            }
            String hash = FileRegistry.calculateHash(file);
            String destination = (destinationOverride != null && !destinationOverride.isBlank()) ? destinationOverride : relativePath;
            if (destination.contains("..")) {
                return false;
            }

            if (restart && isModsDestination(destination)) {
                return false;
            }
            FileRegistry.FILE_ENTRIES.put(relativePath, new FileEntry(hash, destination, optional, restart));
            FileRegistry.unmarkGarbage(destination);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean destinationTargetsModsFolder(String inputPath, String destinationOverride) {
        String baseDestination = destinationOverride;
        if (baseDestination == null || baseDestination.isBlank()) {
            baseDestination = inputPath;
        }
        return isModsDestination(baseDestination);
    }

    private static boolean isModsDestination(String destination) {
        String normalized = normalizeInputPath(destination);
        return normalized.equalsIgnoreCase("mods") || normalized.toLowerCase(java.util.Locale.ROOT).startsWith("mods/");
    }

    private static int executeHelp(CommandContext<ServerCommandSource> context) {
        if (context.getSource().getEntity() != null) {
            context.getSource().sendError(Translations.literal("synchronize.command.console_only"));
            return 0;
        }

        ServerCompat.feedback(context.getSource(), () -> Text.literal(buildHelpMessage()), false);
        return 1;
    }

    private static String pathArgument() {
        return "<" + Translations.server("synchronize.command.help.path") + ">";
    }

    private static String addFilesSyntax() {
        String path = pathArgument();
        return "/synchronize add_files " + path + " [optional] [shallow] [restart] [destination " + path + "]";
    }

    private static String buildHelpMessage() {
        String path = pathArgument();
        return "=== " + Translations.server("synchronize.command.help.title") + " ===\n"
            + Translations.server("synchronize.command.help.note") + "\n\n"
            + addFilesSyntax() + "\n"
            + "  -> " + Translations.server("synchronize.command.help.add_files") + "\n"
            + "  -> [optional]: " + Translations.server("synchronize.command.help.add_files.optional") + "\n"
            + "  -> [shallow]: " + Translations.server("synchronize.command.help.add_files.shallow") + "\n"
            + "  -> [restart]: " + Translations.server("synchronize.command.help.add_files.restart") + "\n"
            + "  -> [destination " + path + "]: " + Translations.server("synchronize.command.help.add_files.destination") + "\n\n"
            + "/synchronize remove_files " + path + "\n"
            + "  -> " + Translations.server("synchronize.command.help.remove_files") + "\n\n"
            + "/synchronize refresh\n"
            + "  -> " + Translations.server("synchronize.command.help.refresh") + "\n\n"
            + "/synchronize help\n"
            + "  -> " + Translations.server("synchronize.command.help.help");
    }

    private static int executeRemoveFiles(CommandContext<ServerCommandSource> context) {
        if (context.getSource().getEntity() != null) {
            context.getSource().sendError(Translations.literal("synchronize.command.console_only"));
            return 0;
        }

        String inputPath = StringArgumentType.getString(context, "path").replace('\\', '/');
        if (inputPath.startsWith("/")) {
            inputPath = inputPath.substring(1);
        }
        if (inputPath.endsWith("/")) {
            inputPath = inputPath.substring(0, inputPath.length() - 1);
        }

        int removedCount = 0;
        Iterator<String> iterator = FileRegistry.FILE_ENTRIES.keySet().iterator();
        while (iterator.hasNext()) {
            String path = iterator.next();
            if (path.equals(inputPath) || path.startsWith(inputPath + "/")) {
                iterator.remove();
                ++removedCount;
            }
        }

        if (removedCount > 0) {
            FileRegistry.save();
            final int finalRemovedCount = removedCount;
            ServerCompat.feedback(context.getSource(), () -> Translations.literal("synchronize.command.remove_files.done", finalRemovedCount), true);
        } else {
            context.getSource().sendError(Translations.literal("synchronize.command.remove_files.none"));
        }
        return removedCount;
    }

    private static String normalizeInputPath(String inputPath) {
        String normalized = inputPath.replace('\\', '/');
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static Path resolveWithinGameDir(CommandContext<ServerCommandSource> context, Path gameDir, String inputPath) {
        String normalizedInput = inputPath.replace('\\', '/');
        if (normalizedInput.contains("..")) {
            context.getSource().sendError(Translations.literal("synchronize.command.path_traversal"));
            return null;
        }
        Path targetPath = gameDir.resolve(inputPath).normalize();
        if (!targetPath.startsWith(gameDir)) {
            context.getSource().sendError(Translations.literal("synchronize.command.outside_root"));
            return null;
        }
        return targetPath;
    }

    private static List<String> tokenize(String raw) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private static CompletableFuture<Suggestions> suggestAddFileArgs(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        if (context.getSource().getEntity() != null) {
            return Suggestions.empty();
        }
        String remaining = builder.getRemaining();
        List<String> tokens = tokenize(remaining);
        boolean endsWithSpace = remaining.isEmpty() || Character.isWhitespace(remaining.charAt(remaining.length() - 1));

        if (tokens.isEmpty() || (tokens.size() == 1 && !endsWithSpace)) {
            return suggestPathCompletion(remaining, builder);
        }

        String lastToken = tokens.get(tokens.size() - 1);
        boolean typingLastToken = !endsWithSpace;

        if (typingLastToken && tokens.size() >= 2 && tokens.get(tokens.size() - 2).equalsIgnoreCase(DESTINATION_FLAG)) {
            return suggestPathCompletion(lastToken, builder);
        }

        if (!typingLastToken && lastToken.equalsIgnoreCase(DESTINATION_FLAG)) {
            return suggestPathCompletion("", builder);
        }

        String prefix = typingLastToken ? lastToken : "";
        SuggestionsBuilder offsetBuilder = builder.createOffset(builder.getStart() + remaining.length() - prefix.length());

        boolean hasOptional = tokens.stream().anyMatch(t -> t.equalsIgnoreCase(OPTIONAL_FLAG));
        boolean hasShallow = tokens.stream().anyMatch(t -> t.equalsIgnoreCase(SHALLOW_FLAG));
        boolean hasDestination = tokens.stream().anyMatch(t -> t.equalsIgnoreCase(DESTINATION_FLAG));
        boolean hasRestart = tokens.stream().anyMatch(t -> t.equalsIgnoreCase(RESTART_FLAG));

        if (!hasOptional && OPTIONAL_FLAG.startsWith(prefix.toLowerCase())) {
            offsetBuilder.suggest(OPTIONAL_FLAG);
        }
        if (!hasShallow && SHALLOW_FLAG.startsWith(prefix.toLowerCase())) {
            offsetBuilder.suggest(SHALLOW_FLAG);
        }
        if (!hasDestination && DESTINATION_FLAG.startsWith(prefix.toLowerCase())) {
            offsetBuilder.suggest(DESTINATION_FLAG);
        }
        if (!hasRestart && RESTART_FLAG.startsWith(prefix.toLowerCase())) {
            offsetBuilder.suggest(RESTART_FLAG);
        }

        return offsetBuilder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestPathCompletion(String input, SuggestionsBuilder builder) {
        String normalized = input.replace('\\', '/');
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        int lastSlash = normalized.lastIndexOf('/');
        String dirPart = lastSlash >= 0 ? normalized.substring(0, lastSlash) : "";
        String prefix = lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;

        Path gameDir = FabricLoader.getInstance().getGameDir().normalize();
        Path searchDir = gameDir.resolve(dirPart).normalize();
        if (!searchDir.startsWith(gameDir)) {
            return builder.buildFuture();
        }

        int inputStartInRemaining = builder.getRemaining().length() - input.length();
        SuggestionsBuilder offsetBuilder = builder.createOffset(builder.getStart() + inputStartInRemaining + (lastSlash >= 0 ? lastSlash + 1 : 0));

        File[] files = searchDir.toFile().listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (name.startsWith(prefix)) {
                    offsetBuilder.suggest(file.isDirectory() ? name + "/" : name);
                }
            }
        }
        return offsetBuilder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestTrackedFiles(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        if (context.getSource().getEntity() != null) {
            return Suggestions.empty();
        }
        String input = builder.getRemaining().replace('\\', '/');
        if (input.startsWith("/")) {
            input = input.substring(1);
        }
        int lastSlash = input.lastIndexOf('/');
        SuggestionsBuilder offsetBuilder = builder.createOffset(builder.getStart() + (lastSlash >= 0 ? lastSlash + 1 : 0));

        Set<String> suggestions = new HashSet<>();
        for (String trackedPath : FileRegistry.FILE_ENTRIES.keySet()) {
            if (trackedPath.startsWith(input)) {
                String remainder = trackedPath.substring(lastSlash >= 0 ? lastSlash + 1 : 0);
                int nextSlash = remainder.indexOf('/');
                if (nextSlash >= 0) {
                    suggestions.add(remainder.substring(0, nextSlash + 1));
                } else {
                    suggestions.add(remainder);
                }
            }
        }

        for (String suggestion : suggestions) {
            offsetBuilder.suggest(suggestion);
        }
        return offsetBuilder.buildFuture();
    }
}