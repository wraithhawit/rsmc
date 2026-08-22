package com.wraithhawit.rsmc.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.refinedmods.refinedstorage.common.support.stretching.AbstractStretchingScreen;
import com.refinedmods.refinedstorage.common.support.widget.History;
import com.refinedmods.refinedstorage.common.support.widget.SearchFieldWidget;

import com.wraithhawit.rsmc.RSMC;
import com.wraithhawit.rsmc.menu.PatternMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The structure's pattern screen, built on Refined Storage's own stretching screen.
 *
 * <p>It is the Autocrafter Manager's shape because it is the Autocrafter Manager's problem: far
 * more slots than fit on screen, gathered from more than one place, wanted by name. Using RS's
 * {@link AbstractStretchingScreen} means the window stretches to the player's screen, scrolls and
 * renders with the same chrome as every other RS screen, rather than approximating it.
 *
 * <p><strong>Coupling to RS's internals here is deliberate.</strong> Step Crafter -- the same author
 * as Cable Tiers -- extends {@code AbstractBaseScreen}, {@code AbstractBaseContainerMenu},
 * {@code AbstractBaseBlock} and mixins into {@code AbstractGridScreen}. Deep coupling is how RS
 * addons are written, and the cost of
 * breaking on an RS update is accepted in exchange for looking and behaving like part of the mod
 * you are adding to.
 *
 * <h2>Scrolling and searching are the same operation</h2>
 *
 * <p>Both are done by <strong>moving slots</strong>, not by rebuilding the menu: {@link #layout()}
 * places the slots the filter matches, offset by the scroll row, and pushes everything else
 * off-screen where it can be neither clicked nor drawn.
 *
 * <p>That is safe because a slot's position is a client-side concern. Clicks travel as slot
 * <em>indices</em>, so the server never has to agree about layout -- filtering cannot desync, and a
 * search that hides a slot cannot lose the pattern in it.
 */
public class PatternScreen extends AbstractStretchingScreen<PatternMenu> {
    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath(RSMC.MODID, "textures/gui/patterns.png");
    private static final List<String> SEARCH_HISTORY = new ArrayList<>();

    private static final int COLUMNS = 9;
    private static final int SLOT_SIZE = 18;
    /** Somewhere no click can reach, for slots the filter or the scroll has hidden. */
    private static final int OFF_SCREEN = -10000;

    private SearchFieldWidget searchField;
    private String query = "";
    private int rows;

    public PatternScreen(final PatternMenu menu, final Inventory inventory, final Component title) {
        super(menu, inventory, title);
        this.inventoryLabelY = 75;
        this.imageWidth = 176;
        this.imageHeight = 176;
    }

    /**
     * Setup happens here, not in {@code init()}.
     *
     * <p>{@code AbstractStretchingScreen.init()} works out how many rows fit, sizes the window,
     * repositions the menu's slots and builds the scrollbar -- and only then calls this with the row
     * count. Doing any of it earlier means laying out against a row count of zero and a scrollbar
     * that does not exist yet.
     */
    @Override
    protected void init(final int visibleRows) {
        super.init(visibleRows);
        this.rows = visibleRows;
        if (this.searchField == null) {
            this.searchField = new SearchFieldWidget(this.font,
                this.leftPos + 94 + 1, this.topPos + 6 + 1, 67, new History(SEARCH_HISTORY));
        } else {
            this.searchField.setX(this.leftPos + 94 + 1);
            this.searchField.setY(this.topPos + 6 + 1);
        }
        this.searchField.setResponder(value -> {
            this.query = value.toLowerCase(Locale.ROOT);
            this.layout();
        });
        this.addWidget(this.searchField);
        this.layout();
    }

    @Override
    protected void scrollbarChanged(final int visibleRows) {
        super.scrollbarChanged(visibleRows);
        this.rows = visibleRows;
        this.layout();
    }

    /**
     * Places the visible slots and hides the rest.
     *
     * <p>The only thing in the mod that moves a slot, so there is one answer to "why is this slot
     * here" rather than several interacting ones.
     */
    private void layout() {
        final List<Slot> visible = this.matchingSlots();
        final int visibleRows = Math.max(1, this.rows);
        for (final Slot slot : this.getMenu().patternSlots()) {
            slot.x = OFF_SCREEN;
            slot.y = OFF_SCREEN;
        }
        final int firstIndex = this.getScrollbarOffset() / SLOT_SIZE * COLUMNS;
        for (int i = 0; i < visibleRows * COLUMNS; i++) {
            final int index = firstIndex + i;
            if (index >= visible.size()) {
                break;
            }
            final Slot slot = visible.get(index);
            slot.x = 7 + 1 + i % COLUMNS * SLOT_SIZE;
            slot.y = TOP_HEIGHT + 1 + i / COLUMNS * SLOT_SIZE;
        }
        this.updateScrollbar((visible.size() + COLUMNS - 1) / COLUMNS);
    }

    /**
     * The slots the search matches, in menu order.
     *
     * <p>An empty query matches everything <em>including empty slots</em>, because an empty slot is
     * where you put a pattern -- a screen that only showed occupied slots would have nowhere to put
     * the first one. A non-empty query hides empty slots, since "show me slots matching iron" has no
     * reading that includes blank ones.
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

    /**
     * Where the bottom section starts in the texture. 73 is where RS puts it, and the texture is
     * theirs unmodified, so this is not a number to pick -- it is a number to match.
     */
    @Override
    protected int getBottomV() {
        return 73;
    }

    /** How tall that bottom section is. Also RS's number, for the same reason. */
    @Override
    protected int getBottomHeight() {
        return 99;
    }

    @Override
    protected ResourceLocation getTexture() {
        return TEXTURE;
    }

    @Override
    protected void renderStretchingBackground(final GuiGraphics graphics, final int x, final int y,
                                              final int rowCount) {
        // Three bands in the texture: the first row, a repeating middle, and the last. Same layout
        // as every stretching RS screen, which is why the texture can be theirs unmodified.
        for (int row = 0; row < rowCount; row++) {
            int textureY = 37;
            if (row == 0) {
                textureY = 19;
            } else if (row == rowCount - 1) {
                textureY = 55;
            }
            graphics.blit(this.getTexture(), x, y + SLOT_SIZE * row, 0, textureY, this.imageWidth,
                SLOT_SIZE);
        }
    }

    /**
     * Draws the well behind every visible pattern slot.
     *
     * <p>RS's row texture is <em>plain</em> -- the Autocrafter Manager's list is empty grey until it
     * has autocrafters to show, and it paints a well per slot as it draws each group. Inheriting
     * their texture therefore means inheriting the job of drawing slots, which is why leaving this
     * empty produced a large blank panel with items floating in it.
     *
     * <p>Drawn from the visible slots rather than as a fixed grid, so the wells are exactly where
     * the slots are: a filtered half-row shows the wells it has and no more.
     */
    @Override
    protected void renderRows(final GuiGraphics graphics, final int x, final int y,
                              final int topHeight, final int rowCount,
                              final int mouseX, final int mouseY) {
        for (final Slot slot : this.getMenu().patternSlots()) {
            if (slot.x == OFF_SCREEN) {
                continue;
            }
            final int slotX = x + slot.x;
            final int slotY = y + slot.y;
            graphics.fill(slotX - 1, slotY - 1, slotX + 17, slotY + 17, 0xFF373737);
            graphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0xFF8B8B8B);
        }
    }

    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY,
                       final float partialTicks) {
        super.render(graphics, mouseX, mouseY, partialTicks);
        if (this.searchField != null) {
            this.searchField.render(graphics, 0, 0, 0.0F);
        }
    }

    @Override
    public boolean charTyped(final char codePoint, final int modifiers) {
        return this.searchField != null && this.searchField.charTyped(codePoint, modifiers)
            || super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        // The search field gets first refusal, or typing "e" while searching closes the screen.
        return this.searchField != null && this.searchField.keyPressed(key, scanCode, modifiers)
            || super.keyPressed(key, scanCode, modifiers);
    }
}
