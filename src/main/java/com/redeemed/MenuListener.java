package com.redeemed;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class MenuListener implements Listener {
    /** One editable number in /rg config. */
    record Setting(int slot, String path, String name, Material icon, double step, double min, double max, String unit) { }

    static final class DisguiseHolder implements InventoryHolder {
        public Inventory getInventory() { return null; }
    }

    static final class ConfigHolder implements InventoryHolder {
        public Inventory getInventory() { return null; }
    }

    private static final List<Setting> SETTINGS = List.of(
            new Setting(1, "shackles.charges", "Shackles - Charges", Material.CHAIN, 1, 1, 10, ""),
            new Setting(2, "shackles.ground-seconds", "Shackles - Ground Time", Material.CLOCK, 0.1, 0.5, 10, "s"),
            new Setting(3, "shackles.damage-hearts", "Shackles - True Damage", Material.RED_DYE, 0.5, 0, 20, " hearts"),
            new Setting(4, "shackles.recharge-seconds", "Shackles - Recharge", Material.HOPPER, 5, 5, 600, "s"),
            new Setting(5, "shackles.launch-strength", "Shackles Launch - Strength", Material.FIREWORK_ROCKET, 0.1, 0.2, 5, "x"),
            new Setting(6, "shackles.launch-lift", "Shackles Launch - Lift", Material.FEATHER, 0.1, 0, 2, ""),
            new Setting(7, "shackles.launch-cooldown-seconds", "Shackles Launch - Cooldown", Material.HOPPER, 1, 1, 120, "s"),
            new Setting(10, "slam.radius", "Shattered Slam - Radius", Material.HEAVY_CORE, 1, 2, 30, " blocks"),
            new Setting(11, "slam.hearts-taken", "Shattered Slam - Hearts Taken (permanent)", Material.RED_DYE, 0.5, 0, 20, " hearts"),
            new Setting(12, "slam.fling-distance", "Shattered Slam - Fling Distance", Material.FEATHER, 1, 0, 40, " blocks"),
            new Setting(13, "slam.cooldown-seconds", "Shattered Slam - Cooldown", Material.HOPPER, 5, 5, 600, "s"),
            new Setting(19, "limitless.radius", "Limitless - Radius", Material.HEAVY_CORE, 1, 2, 30, " blocks"),
            new Setting(20, "limitless.hearts-taken", "Limitless - Hearts Taken", Material.RED_DYE, 0.5, 0, 20, " hearts"),
            new Setting(21, "limitless.lock-radius", "Limitless - Lock Radius", Material.IRON_BARS, 0.5, 0.5, 10, " blocks"),
            new Setting(22, "limitless.lock-seconds", "Limitless - Lock Time", Material.CLOCK, 1, 1, 60, "s"),
            new Setting(23, "limitless.duration-seconds", "Limitless - Duration", Material.CLOCK, 1, 1, 120, "s"),
            new Setting(24, "limitless.cooldown-seconds", "Limitless - Cooldown", Material.HOPPER, 5, 5, 600, "s"),
            new Setting(25, "limitless.armor-mend-per-second", "Limitless - Armor Mend / sec", Material.ANVIL, 1, 0, 50, " durability"),
            new Setting(28, "general.double-click-ms", "Double Right Click Window", Material.COMPARATOR, 50, 200, 1500, "ms"),
            new Setting(14, "slam.min-hearts", "Shattered Slam - Min Max Health", Material.GOLDEN_APPLE, 0.5, 0.5, 10, " hearts"),
            new Setting(29, "effects.particle-multiplier", "Particle Amount", Material.FIREWORK_STAR, 0.5, 0.5, 5, "x")
    );

    /** On/off options in /rg config. */
    record Toggle(int slot, String path, boolean def, String name, String on, String off) { }

    private static final List<Toggle> TOGGLES = List.of(
            new Toggle(31, "soulbound", true, "Soulbound Gem", "Cannot be dropped; kept on death.", "Droppable and lootable. Sneak + drop opens the disguise menu."),
            new Toggle(32, "disguise-blocks-powers", true, "Disguise Blocks Powers", "A disguised gem has no powers.", "A disguised gem keeps its powers.")
    );

    private final RedeemedGemPlugin pl;

    public MenuListener(RedeemedGemPlugin pl) { this.pl = pl; }

    // ------------------------------------------------------------ Disguise menu
    private static final int[] GEM_SLOTS = {10, 11, 12, 13, 14, 15, 16, 17};
    private static final int REVERT_SLOT = 22;

    public void openDisguise(Player p) {
        Inventory inv = Bukkit.createInventory(new DisguiseHolder(), 27, Component.text("Disguise your gem"));
        ItemStack pane = named(new ItemStack(Material.GRAY_STAINED_GLASS_PANE), " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);
        for (int i = 0; i < Gems.DISGUISES.length; i++) {
            ItemStack it = pl.gems.create(Gems.Stage.DORMANT, Gems.DISGUISES[i]);
            ItemMeta m = it.getItemMeta();
            m.lore(List.of(Component.text("Click to disguise as this gem.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
            it.setItemMeta(m);
            inv.setItem(GEM_SLOTS[i], it);
        }
        Gems.Stage cur = pl.gems.stageInInventory(p);
        ItemStack revert = pl.gems.create(cur == null ? Gems.Stage.DORMANT : cur, null);
        ItemMeta rm = revert.getItemMeta();
        rm.lore(List.of(Component.text("Click to turn back into the Redeemed Gem.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        revert.setItemMeta(rm);
        inv.setItem(REVERT_SLOT, revert);
        p.openInventory(inv);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (!pl.gems.isRedeemed(e.getItemDrop().getItemStack())) return;
        Player p = e.getPlayer();
        // Not soulbound: a normal drop really drops the gem. Sneak + drop (or /rg disguise) opens the disguise menu.
        if (!pl.getConfig().getBoolean("soulbound", true) && !p.isSneaking()) return;
        e.setCancelled(true);
        Bukkit.getScheduler().runTask(pl, () -> openDisguise(p));
    }

    // ------------------------------------------------------------ Config menu
    public void openConfig(Player p) {
        Inventory inv = Bukkit.createInventory(new ConfigHolder(), 36, Component.text("Redeemed Gem Config"));
        ItemStack pane = named(new ItemStack(Material.BLACK_STAINED_GLASS_PANE), " ");
        for (int i = 0; i < 36; i++) inv.setItem(i, pane);
        for (Setting s : SETTINGS) inv.setItem(s.slot(), settingItem(s));
        for (Toggle t : TOGGLES) inv.setItem(t.slot(), toggleItem(t));
        p.openInventory(inv);
    }

    private ItemStack settingItem(Setting s) {
        ItemStack it = new ItemStack(s.icon());
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text(s.name(), NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(gray("Value: " + fmt(pl.getConfig().getDouble(s.path())) + s.unit()));
        lore.add(gray("Left click: +" + fmt(s.step()) + "   Right click: -" + fmt(s.step())));
        lore.add(gray("Shift: x5"));
        m.lore(lore);
        it.setItemMeta(m);
        return it;
    }

    private ItemStack toggleItem(Toggle t) {
        boolean on = pl.getConfig().getBoolean(t.path(), t.def());
        ItemStack it = new ItemStack(on ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text(t.name() + ": " + (on ? "ON" : "OFF"), on ? NamedTextColor.GREEN : NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(gray(on ? t.on() : t.off()), gray("Click to toggle.")));
        it.setItemMeta(m);
        return it;
    }

    private static Component gray(String s) { return Component.text(s, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false); }
    private static String fmt(double v) { return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(Math.round(v * 100.0) / 100.0); }

    private static ItemStack named(ItemStack it, String name) {
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text(name));
        it.setItemMeta(m);
        return it;
    }

    // ------------------------------------------------------------ Clicks
    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        Player p = (Player) e.getWhoClicked();

        // Q / Ctrl+Q on the gem inside the inventory behaves like dropping it: open the disguise menu.
        if (!(top.getHolder() instanceof DisguiseHolder) && !(top.getHolder() instanceof ConfigHolder)) {
            if ((e.getClick() == ClickType.DROP || e.getClick() == ClickType.CONTROL_DROP) && pl.gems.isRedeemed(e.getCurrentItem())) {
                if (!pl.getConfig().getBoolean("soulbound", true) && !p.isSneaking()) return;
                e.setCancelled(true);
                Bukkit.getScheduler().runTask(pl, () -> openDisguise(p));
            }
            return;
        }
        e.setCancelled(true);
        if (e.getClickedInventory() != top) return;
        int slot = e.getSlot();

        if (top.getHolder() instanceof DisguiseHolder) {
            if (slot == REVERT_SLOT) {
                pl.gems.setDisguise(p, null);
                p.sendMessage(Component.text("Your gem is the Redeemed Gem again.", NamedTextColor.GRAY));
            } else {
                for (int i = 0; i < GEM_SLOTS.length; i++) {
                    if (GEM_SLOTS[i] == slot) {
                        pl.gems.setDisguise(p, Gems.DISGUISES[i]);
                        p.sendMessage(Component.text("Your gem now looks like the " + Gems.cap(Gems.DISGUISES[i]) + " Gem.", NamedTextColor.GRAY));
                    }
                }
            }
            if (slot == REVERT_SLOT || contains(GEM_SLOTS, slot)) {
                p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.8f);
                p.closeInventory();
            }
            return;
        }

        for (Toggle t : TOGGLES) {
            if (t.slot() != slot) continue;
            pl.getConfig().set(t.path(), !pl.getConfig().getBoolean(t.path(), t.def()));
            pl.saveConfig();
            top.setItem(slot, toggleItem(t));
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
        }
        for (Setting s : SETTINGS) {
            if (s.slot() != slot) continue;
            double delta = s.step() * (e.isShiftClick() ? 5 : 1) * (e.isRightClick() ? -1 : 1);
            double v = Math.max(s.min(), Math.min(s.max(), pl.getConfig().getDouble(s.path()) + delta));
            v = Math.round(v * 100.0) / 100.0;
            pl.getConfig().set(s.path(), v);
            pl.saveConfig();
            top.setItem(slot, settingItem(s));
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
        }
    }

    private static boolean contains(int[] a, int v) { for (int x : a) if (x == v) return true; return false; }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        InventoryHolder h = e.getView().getTopInventory().getHolder();
        if (h instanceof DisguiseHolder || h instanceof ConfigHolder) e.setCancelled(true);
    }
}
