package com.terraformersmc.modmenu.gui;

import com.google.common.base.Joiner;
import com.terraformersmc.modmenu.ModMenu;
import com.terraformersmc.modmenu.config.ModMenuConfig;
import com.terraformersmc.modmenu.config.ModMenuConfigManager;
import com.terraformersmc.modmenu.gui.widget.DescriptionListWidget;
import com.terraformersmc.modmenu.gui.widget.LegacyTexturedButtonWidget;
import com.terraformersmc.modmenu.gui.widget.ModListWidget;
import com.terraformersmc.modmenu.gui.widget.entries.ModListEntry;
import com.terraformersmc.modmenu.util.DrawingUtil;
import com.terraformersmc.modmenu.util.ModMenuScreenTexts;
import com.terraformersmc.modmenu.util.TranslationUtil;
import com.terraformersmc.modmenu.util.mod.Mod;
import com.terraformersmc.modmenu.util.mod.ModBadgeRenderer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.minecraft.SharedConstants;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import net.minecraft.util.Urls;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class ModsScreen extends Screen {
    private static final Identifier FILTERS_BUTTON_LOCATION = Identifier.of(ModMenu.MOD_ID, "textures/gui/filters_button.png");
    private static final Identifier CONFIGURE_BUTTON_LOCATION = Identifier.of(ModMenu.MOD_ID, "textures/gui/configure_button.png");
    private static final Logger LOGGER = LoggerFactory.getLogger("Mod Menu | ModsScreen");

    private final Screen previousScreen;
    private ModListEntry selected;
    private ModBadgeRenderer modBadgeRenderer;
    private boolean keepFilterOptionsShown = false;
    private boolean init = false;
    private boolean filterOptionsShown = false;
    private static final int RIGHT_PANE_Y = 48;
    private int paneWidth;
    private int rightPaneX;
    private int searchBoxX;
    private int filtersX;
    private int filtersWidth;
    private int searchRowWidth;

    public final Set<String> showModChildren = new HashSet<>();
    private TextFieldWidget searchBox;
    private @Nullable ClickableWidget filtersButton;
    private ClickableWidget sortingButton;
    private ClickableWidget librariesButton;
    private ModListWidget modList;
    private @Nullable ClickableWidget configureButton;
    private ClickableWidget websiteButton;
    private ClickableWidget issuesButton;
    private DescriptionListWidget descriptionListWidget;

    public final Map<String, Boolean> modHasConfigScreen = new HashMap<>();
    public final Map<String, Throwable> modScreenErrors = new HashMap<>();

    private static final Text SEND_FEEDBACK_TEXT = Text.translatable("menu.sendFeedback");
    private static final Text REPORT_BUGS_TEXT = Text.translatable("menu.reportBugs");

    public ModsScreen(Screen previousScreen) {
        super(ModMenuScreenTexts.TITLE);
        this.previousScreen = previousScreen;
    }

    @Override
    protected void init() {
        int paneY = ModMenuConfig.CONFIG_MODE.getValue() ? 48 : 48 + 19;
        this.paneWidth = this.width / 2 - 8;
        this.rightPaneX = this.width - this.paneWidth;

        this.modList = new ModListWidget(this.client, this.paneWidth, this.height - paneY - 36, paneY,
                ModMenuConfig.COMPACT_LIST.getValue() ? 23 : 36, this.modList, this);
        this.modList.setX(0);

        int filtersButtonSize = ModMenuConfig.CONFIG_MODE.getValue() ? 0 : 22;
        int searchWidthMax = this.paneWidth - 32 - filtersButtonSize;
        int searchBoxWidth = ModMenuConfig.CONFIG_MODE.getValue() ? Math.min(200, searchWidthMax) : searchWidthMax;
        this.searchBoxX = this.paneWidth / 2 - searchBoxWidth / 2 - filtersButtonSize / 2;

        this.searchBox = new TextFieldWidget(this.textRenderer, this.searchBoxX, 22, searchBoxWidth, 20, ModMenuScreenTexts.SEARCH);
        this.searchBox.setChangedListener(text -> this.modList.filter(text, false));

        Text sortingText = ModMenuConfig.SORTING.getButtonText();
        Text librariesText = ModMenuConfig.SHOW_LIBRARIES.getButtonText();
        int sortingWidth = textRenderer.getWidth(sortingText) + 28;
        int librariesWidth = textRenderer.getWidth(librariesText) + 20;
        this.filtersWidth = librariesWidth + sortingWidth + 2;
        this.searchRowWidth = this.searchBoxX + searchBoxWidth + 22;
        this.updateFiltersX(true);

        if (!ModMenuConfig.CONFIG_MODE.getValue()) {
            this.filtersButton = LegacyTexturedButtonWidget.legacyTexturedBuilder(
                    ModMenuScreenTexts.TOGGLE_FILTER_OPTIONS,
                    button -> this.setFilterOptionsShown(!this.filterOptionsShown))
                    .position(this.paneWidth / 2 + searchBoxWidth / 2 - 10, 22)
                    .size(20, 20)
                    .uv(0, 0, 20)
                    .texture(FILTERS_BUTTON_LOCATION, 32, 64)
                    .build();
            this.filtersButton.setTooltip(Tooltip.of(ModMenuScreenTexts.TOGGLE_FILTER_OPTIONS));
        }

        this.sortingButton = ButtonWidget.builder(sortingText, button -> {
            ModMenuConfig.SORTING.cycleValue(this.client.isShiftPressed() ? -1 : 1);
            ModMenuConfigManager.save();
            modList.reloadFilters();
            button.setMessage(ModMenuConfig.SORTING.getButtonText());
        }).position(this.filtersX, 45).size(sortingWidth, 20).build();

        this.librariesButton = ButtonWidget.builder(librariesText, button -> {
            ModMenuConfig.SHOW_LIBRARIES.toggleValue();
            ModMenuConfigManager.save();
            modList.reloadFilters();
            button.setMessage(ModMenuConfig.SHOW_LIBRARIES.getButtonText());
        }).position(this.filtersX + sortingWidth + 2, 45).size(librariesWidth, 20).build();

        if (!ModMenuConfig.HIDE_CONFIG_BUTTONS.getValue()) {
            this.configureButton = LegacyTexturedButtonWidget.legacyTexturedBuilder(ScreenTexts.EMPTY, button -> {
                if (selected != null) {
                    String id = selected.getMod().getId();
                    if (getModHasConfigScreen(id)) this.safelyOpenConfigScreen(id);
                }
            }).position(width - 24, RIGHT_PANE_Y).size(20, 20)
                    .uv(0, 0, 20).texture(CONFIGURE_BUTTON_LOCATION, 32, 64).build();
        }

        int urlButtonWidths = this.paneWidth / 2 - 2;
        int cappedButtonWidth = Math.min(urlButtonWidths, 200);

        this.websiteButton = ButtonWidget.builder(ModMenuScreenTexts.WEBSITE, button -> {
            if (selected == null) return;
            Mod mod = selected.getMod();
            boolean isMinecraft = "minecraft".equals(mod.getId());
            String url = isMinecraft 
                ? (SharedConstants.getGameVersion().stable() ? Urls.JAVA_FEEDBACK.toString() : Urls.SNAPSHOT_FEEDBACK.toString()) 
                : mod.getWebsite();
            if (url != null) ConfirmLinkScreen.open(this, url, !isMinecraft);
        }).position(this.rightPaneX + (urlButtonWidths / 2) - (cappedButtonWidth / 2), RIGHT_PANE_Y + 36)
                .size(cappedButtonWidth, 20).build();

        this.issuesButton = ButtonWidget.builder(ModMenuScreenTexts.ISSUES, button -> {
            if (selected == null) return;
            Mod mod = selected.getMod();
            boolean isMinecraft = "minecraft".equals(mod.getId());
            String url = isMinecraft ? Urls.SNAPSHOT_BUGS.toString() : mod.getIssueTracker();
            if (url != null) ConfirmLinkScreen.open(this, url, !isMinecraft);
        }).position(this.rightPaneX + urlButtonWidths + 4 + (urlButtonWidths / 2) - (cappedButtonWidth / 2), RIGHT_PANE_Y + 36)
                .size(cappedButtonWidth, 20).build();

        this.descriptionListWidget = new DescriptionListWidget(
                this.client, this.paneWidth, this.height - RIGHT_PANE_Y - 96,
                RIGHT_PANE_Y + 60, textRenderer.fontHeight + 1,
                this.descriptionListWidget, this);
        this.descriptionListWidget.setX(this.rightPaneX);

        ClickableWidget modsFolderButton = ButtonWidget.builder(ModMenuScreenTexts.MODS_FOLDER,
                button -> Util.getOperatingSystem().open(getModsFolder().toUri()))
                .position(this.width / 2 - 154, this.height - 28).size(150, 20).build();

        ClickableWidget doneButton = ButtonWidget.builder(ScreenTexts.DONE,
                button -> client.setScreen(previousScreen))
                .position(this.width / 2 + 4, this.height - 28).size(150, 20).build();

        // ====================== КРАСНАЯ ТЕСТОВАЯ КНОПКА ======================
        // Ярко-красная кнопка 30x30 в левом верхнем углу
        ButtonWidget hideButton = ButtonWidget.builder(ScreenTexts.EMPTY, button -> {
            if (selected != null) {
                String modId = selected.getMod().getId();
                Set<String> hidden = new HashSet<>(ModMenuConfig.HIDDEN_MODS.getValue());

                if (hidden.contains(modId)) {
                    hidden.remove(modId);
                } else {
                    hidden.add(modId);
                }

                ModMenuConfig.HIDDEN_MODS.setValue(hidden);
                ModMenuConfigManager.save();
                modList.reloadFilters();
            }
        }).position(2, 2).size(30, 30).build();

        // Переопределяем отрисовку, чтобы кнопка была красной
        hideButton = new ButtonWidget(hideButton.getX(), hideButton.getY(), hideButton.getWidth(), hideButton.getHeight(),
                ScreenTexts.EMPTY, hideButton.getOnPress(), ButtonWidget.DEFAULT_NARRATION_SUPPLIER) {
            @Override
            public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
                boolean hovered = this.isSelected() || (mouseX >= getX() && mouseX < getX() + getWidth() &&
                        mouseY >= getY() && mouseY < getY() + getHeight());

                int color = hovered ? 0xC0FF4444 : 0xA0FF0000;   // красный с прозрачностью
                context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), color);

                // Белая рамка для красоты
                context.drawBorder(getX(), getY(), getWidth(), getHeight(), 0xFFFFFFFF);
            }
        };

        this.addDrawableChild(hideButton);
        // =====================================================================

        modList.finalizeInit();
        this.setFilterOptionsShown(this.keepFilterOptionsShown && this.filterOptionsShown);

        this.addSelectableChild(this.searchBox);
        this.setInitialFocus(this.searchBox);
        if (this.filtersButton != null) this.addDrawableChild(this.filtersButton);
        this.addDrawableChild(this.sortingButton);
        this.addDrawableChild(this.librariesButton);
        this.addSelectableChild(this.modList);
        if (this.configureButton != null) this.addDrawableChild(this.configureButton);
        this.addDrawableChild(this.websiteButton);
        this.addDrawableChild(this.issuesButton);
        this.addSelectableChild(this.descriptionListWidget);
        this.addDrawableChild(modsFolderButton);
        this.addDrawableChild(doneButton);

        this.updateSelectedEntry(this.modList.getEntry(0));
        this.modList.select(this.selected);

        this.init = true;
        this.keepFilterOptionsShown = true;
    }

    // ==================== ОСТАЛЬНОЙ КОД БЕЗ ИЗМЕНЕНИЙ ====================
    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        super.render(drawContext, mouseX, mouseY, delta);

        this.modList.render(drawContext, mouseX, mouseY, delta);
        this.searchBox.render(drawContext, mouseX, mouseY, delta);

        drawContext.drawCenteredTextWithShadow(this.textRenderer, this.title, this.modList.getWidth() / 2, 8, 0xFFFFFFFF);

        if (!ModMenuConfig.CONFIG_MODE.getValue()) {
            Text fullModCount = this.computeModCountText(true, false);
            if (this.updateFiltersX(false)) {
                if (this.filterOptionsShown) {
                    if (!ModMenuConfig.SHOW_LIBRARIES.getValue() || this.textRenderer.getWidth(fullModCount) <= this.filtersX - 5) {
                        drawContext.drawText(this.textRenderer, fullModCount.asOrderedText(), this.searchBoxX, 52, 0xFFFFFFFF, true);
                    } else {
                        drawContext.drawText(this.textRenderer, this.computeModCountText(false, false).asOrderedText(), this.searchBoxX, 46, 0xFFFFFFFF, true);
                        drawContext.drawText(this.textRenderer, this.computeLibraryCountText(false).asOrderedText(), this.searchBoxX, 57, 0xFFFFFFFF, true);
                    }
                } else {
                    if (!ModMenuConfig.SHOW_LIBRARIES.getValue() || this.textRenderer.getWidth(fullModCount) <= this.modList.getWidth() - 5) {
                        drawContext.drawText(this.textRenderer, fullModCount.asOrderedText(), this.searchBoxX, 52, 0xFFFFFFFF, true);
                    } else {
                        drawContext.drawText(this.textRenderer, this.computeModCountText(false, false).asOrderedText(), this.searchBoxX, 46, 0xFFFFFFFF, true);
                        drawContext.drawText(this.textRenderer, this.computeLibraryCountText(false).asOrderedText(), this.searchBoxX, 57, 0xFFFFFFFF, true);
                    }
                }
            }
        }

        if (selected != null) {
            this.descriptionListWidget.render(drawContext, mouseX, mouseY, delta);

            Mod mod = selected.getMod();
            int x = this.rightPaneX;

            if ("java".equals(mod.getId())) {
                DrawingUtil.drawRandomVersionBackground(mod, drawContext, x, RIGHT_PANE_Y, 32, 32);
            }

            drawContext.drawTexture(RenderPipelines.GUI_TEXTURED, selected.getIconTexture(), x, RIGHT_PANE_Y, 0.0F, 0.0F, 32, 32, 32, 32, 0xFFFFFFFF);

            Text name = Text.literal(mod.getTranslatedName());
            StringVisitable trimmedName = name;
            int maxNameWidth = this.width - (x + 36);
            if (this.textRenderer.getWidth(name) > maxNameWidth) {
                StringVisitable ellipsis = StringVisitable.plain("...");
                trimmedName = StringVisitable.concat(this.textRenderer.trimToWidth(name, maxNameWidth - this.textRenderer.getWidth(ellipsis)), ellipsis);
            }
            drawContext.drawText(this.textRenderer, Language.getInstance().reorder(trimmedName), x + 36, RIGHT_PANE_Y + 1, 0xFFFFFFFF, true);

            if (mouseX > x + 36 && mouseY > RIGHT_PANE_Y + 1 && mouseY < RIGHT_PANE_Y + 1 + this.textRenderer.fontHeight && mouseX < x + 36 + this.textRenderer.getWidth(trimmedName)) {
                drawContext.drawTooltip(ModMenuScreenTexts.modIdTooltip(mod.getId()), mouseX, mouseY);
            }

            if (this.init || this.modBadgeRenderer == null || this.modBadgeRenderer.getMod() != mod) {
                this.modBadgeRenderer = new ModBadgeRenderer(x + 36 + this.textRenderer.getWidth(trimmedName) + 2, RIGHT_PANE_Y, this.width - 28, selected.mod, this);
                this.init = false;
            }
            if (!ModMenuConfig.HIDE_BADGES.getValue()) {
                this.modBadgeRenderer.draw(drawContext, mouseX, mouseY);
            }

            if (mod.isReal()) {
                drawContext.drawText(this.textRenderer, mod.getPrefixedVersion(), x + 36, RIGHT_PANE_Y + 2 + this.textRenderer.fontHeight, 0xFFAAAAAA, true);
            }

            List<String> names = mod.getAuthors();
            if (!names.isEmpty()) {
                String authors = names.size() > 1 ? Joiner.on(", ").join(names) : names.get(0);
                DrawingUtil.drawWrappedString(drawContext, I18n.translate("modmenu.authorPrefix", authors),
                        x + 36, RIGHT_PANE_Y + 2 + this.textRenderer.fontHeight * 2,
                        this.paneWidth - 36 - 4, 1, 0xFFAAAAAA);
            }
        }

        if (!ModMenuConfig.DISABLE_DRAG_AND_DROP.getValue()) {
            int gray = 0xFFAAAAAA;
            drawContext.drawCenteredTextWithShadow(this.textRenderer, ModMenuScreenTexts.DROP_INFO_LINE_1,
                    this.width - this.modList.getWidth() / 2, RIGHT_PANE_Y / 2 - this.textRenderer.fontHeight - 1, gray);
            drawContext.drawCenteredTextWithShadow(this.textRenderer, ModMenuScreenTexts.DROP_INFO_LINE_2,
                    this.width - this.modList.getWidth() / 2, RIGHT_PANE_Y / 2 + 1, gray);
        }
    }

    private Text computeModCountText(boolean includeLibs, boolean onInit) {
        int[] rootMods = formatModCount(ModMenu.ROOT_MODS.values().stream()
                .filter(mod -> !mod.isHidden() && !mod.getBadges().contains(Mod.Badge.LIBRARY))
                .map(Mod::getId).collect(Collectors.toSet()), onInit);
        if (includeLibs && ModMenuConfig.SHOW_LIBRARIES.getValue() && !onInit) {
            int[] rootLibs = formatModCount(ModMenu.ROOT_MODS.values().stream()
                    .filter(mod -> !mod.isHidden() && mod.getBadges().contains(Mod.Badge.LIBRARY))
                    .map(Mod::getId).collect(Collectors.toSet()), false);
            return TranslationUtil.translateNumeric("modmenu.showingModsLibraries", rootMods, rootLibs);
        }
        return TranslationUtil.translateNumeric("modmenu.showingMods", rootMods);
    }

    private Text computeLibraryCountText(boolean onInit) {
        if (ModMenuConfig.SHOW_LIBRARIES.getValue() && !onInit) {
            int[] rootLibs = formatModCount(ModMenu.ROOT_MODS.values().stream()
                    .filter(mod -> !mod.isHidden() && mod.getBadges().contains(Mod.Badge.LIBRARY))
                    .map(Mod::getId).collect(Collectors.toSet()), false);
            return TranslationUtil.translateNumeric("modmenu.showingLibraries", rootLibs);
        }
        return Text.empty();
    }

    private int[] formatModCount(Set<String> set, boolean allVisible) {
        int visible = this.modList.getDisplayedCountFor(set);
        int total = set.size();
        return (visible == total || allVisible) ? new int[]{total} : new int[]{visible, total};
    }

    private boolean updateFiltersX(boolean onInit) {
        Text countText = computeModCountText(true, onInit);
        if ((this.filtersWidth + this.textRenderer.getWidth(countText) + 20) >= this.searchRowWidth &&
                ((this.filtersWidth + this.textRenderer.getWidth(computeModCountText(false, onInit)) + 20) >= this.searchRowWidth ||
                        (this.filtersWidth + this.textRenderer.getWidth(computeLibraryCountText(onInit)) + 20) >= this.searchRowWidth)) {
            this.filtersX = this.paneWidth / 2 - this.filtersWidth / 2;
            return !filterOptionsShown;
        } else {
            this.filtersX = this.searchRowWidth - this.filtersWidth + 1;
            return true;
        }
    }

    private void setFilterOptionsShown(boolean shown) {
        this.filterOptionsShown = shown;
        if (this.sortingButton != null) this.sortingButton.visible = shown;
        if (this.librariesButton != null) this.librariesButton.visible = shown;
    }

    public void updateSelectedEntry(ModListEntry entry) {
        this.selected = entry;
        if (entry != null) {
            this.descriptionListWidget.updateSelectedMod(entry.getMod());
            String modId = entry.getMod().getId();
            if (this.configureButton != null) {
                boolean hasConfig = getModHasConfigScreen(modId);
                this.configureButton.active = hasConfig;
                this.configureButton.visible = hasConfig || modScreenErrors.containsKey(modId);
            }
            boolean isMinecraft = "minecraft".equals(modId);
            this.websiteButton.setMessage(isMinecraft ? SEND_FEEDBACK_TEXT : ModMenuScreenTexts.WEBSITE);
            this.issuesButton.setMessage(isMinecraft ? REPORT_BUGS_TEXT : ModMenuScreenTexts.ISSUES);
            this.websiteButton.active = isMinecraft || entry.getMod().getWebsite() != null;
            this.issuesButton.active = isMinecraft || entry.getMod().getIssueTracker() != null;
        }
    }

    public ModListEntry getSelectedEntry() {
        return selected;
    }

    public String getSearchInput() {
        return this.searchBox.getText();
    }

    public boolean getModHasConfigScreen(String modId) {
        if (modScreenErrors.containsKey(modId)) return false;
        return modHasConfigScreen.computeIfAbsent(modId, ModMenu::hasConfigScreen);
    }

    public void safelyOpenConfigScreen(String modId) {
        try {
            Screen screen = ModMenu.getConfigScreen(modId, this);
            if (screen != null) {
                this.client.setScreen(screen);
            }
        } catch (Throwable e) {
            LOGGER.error("Error opening config screen for {}", modId, e);
            modScreenErrors.put(modId, e);
        }
    }

    @Override
    public void close() {
        this.modList.close();
        this.client.setScreen(this.previousScreen);
    }

    @Override
    public void onFilesDropped(List<Path> paths) {
        Path modsDirectory = FabricLoader.getInstance().getGameDir().resolve("mods");
        List<Path> mods = paths.stream().filter(ModsScreen::isValidMod).toList();
        if (mods.isEmpty()) return;
        String modListStr = mods.stream().map(p -> p.getFileName().toString()).collect(Collectors.joining(", "));
        this.client.setScreen(new ConfirmScreen(value -> {
            if (value) {
                boolean ok = true;
                for (Path p : mods) {
                    try {
                        Files.copy(p, modsDirectory.resolve(p.getFileName()));
                    } catch (IOException e) {
                        SystemToast.addPackCopyFailure(client, p.toString());
                        ok = false;
                        break;
                    }
                }
                if (ok) SystemToast.add(client.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION,
                        ModMenuScreenTexts.DROP_SUCCESSFUL_LINE_1, ModMenuScreenTexts.DROP_SUCCESSFUL_LINE_2);
            }
            this.client.setScreen(this);
        }, ModMenuScreenTexts.DROP_CONFIRM, Text.literal(modListStr)));
    }

    private static boolean isValidMod(Path mod) {
        try (JarFile jar = new JarFile(mod.toFile())) {
            boolean fabric = jar.getEntry("fabric.mod.json") != null;
            return fabric || (ModMenu.RUNNING_QUILT && jar.getEntry("quilt.mod.json") != null);
        } catch (IOException e) {
            return false;
        }
    }

    private static Path getModsFolder() {
        ModContainer container = FabricLoader.getInstance().getModContainer(ModMenu.MOD_ID).orElseThrow();
        while (container.getContainingMod().isPresent()) container = container.getContainingMod().get();
        if (container.getOrigin().getKind() == ModOrigin.Kind.PATH) {
            return container.getOrigin().getPaths().get(0).getParent();
        }
        return FabricLoader.getInstance().getGameDir().resolve("mods");
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        return super.keyPressed(input) || this.searchBox.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        return this.searchBox.charTyped(input);
    }
}
