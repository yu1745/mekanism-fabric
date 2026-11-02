package mekanism.fabric.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Read-only view of Mekanism's TOML configuration files, opened from Mod Menu.
 * <p>
 * Port delta: the NeoForge build lets NeoForge's built-in configuration screen edit every {@code ModConfigSpec} value
 * in game. On Fabric there is no such screen and no equivalent of {@code ModConfigSpec} to drive one, and the port reads
 * its TOML files once at startup, so a real editor would have to be written from scratch. Rather than ship a fake
 * editor this screen shows which files exist, their size, and where they live.
 */
public class GuiMekanismConfig extends Screen {

    private static final int ROW_HEIGHT = 20;

    private final Screen parent;
    private final List<ConfigFile> files = new ArrayList<>();
    private FileList list;

    public GuiMekanismConfig(Screen parent) {
        super(Component.literal("Mekanism Configuration"));
        this.parent = parent;
        collectFiles();
    }

    private void collectFiles() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path mekanismDir = configDir.resolve("Mekanism");
        Path root = Files.isDirectory(mekanismDir) ? mekanismDir : configDir;
        try (Stream<Path> stream = Files.walk(root, 2)) {
            stream.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".toml"))
                  .sorted(Comparator.comparing(path -> root.relativize(path).toString()))
                  .forEach(path -> {
                      long size;
                      try {
                          size = Files.size(path);
                      } catch (IOException ignored) {
                          size = -1;
                      }
                      files.add(new ConfigFile(root.relativize(path).toString(), path, size));
                  });
        } catch (IOException ignored) {
            //Nothing to list; the screen explains that below.
        }
    }

    @Override
    protected void init() {
        list = new FileList(minecraft);
        addRenderableWidget(list);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                                      .bounds(width / 2 - 50, height - 27, 100, 20)
                                      .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        if (files.isEmpty()) {
            graphics.drawCenteredString(font, Component.literal("No configuration files found yet - they are written on first launch."),
                  width / 2, height / 2 - 20, 0xFF5555);
        }
        graphics.drawCenteredString(font, Component.literal("Edit these files, then restart the game for changes to apply."),
              width / 2, height - 42, 0xFFFF55);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private record ConfigFile(String name, Path path, long size) {

        private String describe() {
            return name + "  (" + (size < 0 ? "unreadable" : size + " bytes") + ")";
        }
    }

    private final class FileList extends ObjectSelectionList<FileList.Entry> {

        private FileList(net.minecraft.client.Minecraft minecraft) {
            super(minecraft, GuiMekanismConfig.this.width, GuiMekanismConfig.this.height - 70, 28, ROW_HEIGHT);
            for (ConfigFile file : files) {
                addEntry(new Entry(file));
            }
        }

        @Override
        public int getRowWidth() {
            return 300;
        }

        private final class Entry extends ObjectSelectionList.Entry<Entry> {

            private final ConfigFile file;

            private Entry(ConfigFile file) {
                this.file = file;
            }

            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX,
                  int mouseY, boolean hovered, float partialTick) {
                int textY = top + (height - GuiMekanismConfig.this.font.lineHeight) / 2;
                graphics.drawString(GuiMekanismConfig.this.font, file.describe(), left + 4, textY, 0xFFFFFF);
            }

            @Override
            public Component getNarration() {
                return Component.literal(file.describe());
            }
        }
    }
}
