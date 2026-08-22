package com.wraithhawit.rsmc.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.wraithhawit.rsmc.menu.PatternMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The structure's pattern screen: a search field, and every pattern slot the structure has.
 *
 * <p>Modelled on Refined Storage's Autocrafter Manager, which solves the same problem -- far more
 * slots than fit on a screen, gathered from more than one place.
 *
 * <h2>Scrolling and searching are the same operation</h2>
 *
 * <p>Both are done by <strong>moving slots</strong>, not by rebuilding the menu. {@link #layout()}
 * decides which slots are visible -- filtered by the search text, then offset by the scroll row --
 * and puts them where they go; everything else is moved off-screen so it cannot be clicked or drawn.
 *
 * <p>This works because a slot's position is a client-side concern. Clicks travel as slot
 * <em>indices</em>, so the server never has to agree about layout, which means filtering can never
 * desync and a search that hides a slot cannot lose the pattern in it.
 */
public class PatternScreen extends AbstractContainerScreen<PatternMenu> {
    private static final int COLUMNS = 9;
    private static final int SLOT_SIZE = 18;
    /** Somewhere no click can reach, for slots the filter or the scroll has hidden. */
    private static final int OFF_SCREEN = -10000;

    private EditBox searchField;
    private int scrollRow;
    private String query = "";

    public PatternScreen(final PatternMenu menu, final Inventory inventory, final Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = PatternMenu.PATTERNS_Y + PatternMenu.VISIBLE_ROWS * SLOT_SIZE + 14 + 76;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        this.searchField = new EditBox(this.font,
            this.leftPos + 8 + 1, this.topPos + 18, 158 - 2, 12, Component.empty());
        this.searchField.setBordered(false);
        this.searchField.setMaxLength(50);
        this.searchField.setTextColor(0xFFFFFF);
        this.searchField.setResponder(value -> {
            this.query = value.toLowerCase(Locale.ROOT);
            this.scrollRow = 0;
            this.layout();
        });
        this.addWidget(this.searchField);
        this.layout();
    }

    /**
     * Places the visible slots and hides the rest.
     *
     * <p>Called on every change to the query or the scroll position, and it is the only thing that
     * ever moves a slot -- so there is one answer to "why is this slot here" rather than several
     * interacting ones.
     */
    private void layout() {
        final List<Slot> visible = this.matchingSlots();
        final int totalRows = (visible.size() + COLUMNS - 1) / COLUMNS;
        this.scrollRow = Math.max(0, Math.min(this.scrollRow,
            Math.max(0, totalRows - PatternMenu.VISIBLE_ROWS)));
        for (final Slot slot : this.getMenu().patternSlots()) {
            slot.x = OFF_SCREEN;
            slot.y = OFF_SCREEN;
        }
        final int firstIndex = this.scrollRow * COLUMNS;
        for (int i = 0; i < PatternMenu.VISIBLE_ROWS * COLUMNS; i++) {
            final int index = firstIndex + i;
            if (index >= visible.size()) {
                break;
            }
            final Slot slot = visible.get(index);
            slot.x = PatternMenu.PATTERNS_X + i % COLUMNS * SLOT_SIZE;
            slot.y = PatternMenu.PATTERNS_Y + i / COLUMNS * SLOT_SIZE;
        }
    }

    /**
     * The slots the search matches, in menu order.
     *
     * <p>An empty query matches everything <em>including empty slots</em>, because an empty slot is
     * where you put a pattern -- a screen that only showed occupied slots would have nowhere to put
     * the first one. A non-empty query hides empty slots, since "show me slots matching iron" has no
     * sensible reading that includes blank ones.
     */
    private List<Slot> matchingSlots() {
        final List<Slot> matching = new ArrayList<>();
        for (final Slot slot : this.getMenu().patternSlots()) {
            if (this.query.isEmpty()) {
                matching.add(slot);
                continue;
            }
            final ItemStack stack = slot.getItem();
            if (!stack.isEmpty()
                && stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(this.query)) {
                matching.add(slot);
            }
        }
        return matching;
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY,
                                 final double scrollX, final double scrollY) {
        if (scrollY != 0) {
            this.scrollRow -= (int) Math.signum(scrollY);
            this.layout();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        // The search field takes typing first, or pressing "e" while searching closes the screen.
        if (this.searchField.isFocused() && key != 256) {
            return this.searchField.keyPressed(key, scanCode, modifiers)
                || this.searchField.canConsumeInput()
                || super.keyPressed(key, scanCode, modifiers);
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(final char codePoint, final int modifiers) {
        return this.searchField.charTyped(codePoint, modifiers)
            || super.charTyped(codePoint, modifiers);
    }

    @Override
    protected void renderBg(final GuiGraphics graphics, final float partialTick,
                            final int mouseX, final int mouseY) {
        graphics.fill(this.leftPos, this.topPos,
            this.leftPos + this.imageWidth, this.topPos + this.imageHeight, 0xFFC6C6C6);
        // The search field's well.
        graphics.fill(this.leftPos + 7, this.topPos + 16,
            this.leftPos + 169, this.topPos + 29, 0xFF373737);
        for (int row = 0; row < PatternMenu.VISIBLE_ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                this.drawSlotWell(graphics,
                    this.leftPos + PatternMenu.PATTERNS_X + column * SLOT_SIZE,
                    this.topPos + PatternMenu.PATTERNS_Y + row * SLOT_SIZE);
            }
        }
        final int inventoryY = this.topPos + PatternMenu.PATTERNS_Y
            + PatternMenu.VISIBLE_ROWS * SLOT_SIZE + 14;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                this.drawSlotWell(graphics, this.leftPos + PatternMenu.PATTERNS_X + column * SLOT_SIZE,
                    inventoryY + row * SLOT_SIZE);
            }
        }
        for (int column = 0; column < COLUMNS; column++) {
            this.drawSlotWell(graphics, this.leftPos + PatternMenu.PATTERNS_X + column * SLOT_SIZE,
                inventoryY + 58);
        }
    }

    private void drawSlotWell(final GuiGraphics graphics, final int x, final int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF373737);
        graphics.fill(x, y, x + 16, y + 16, 0xFF8B8B8B);
    }

    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY,
                       final float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.searchField.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        this.searchField.mouseClicked(mouseX, mouseY, button);
        this.setFocused(this.searchField.isMouseOver(mouseX, mouseY) ? this.searchField : null);
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
