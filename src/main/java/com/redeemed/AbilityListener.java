package com.redeemed;

import com.redeemed.Gems.Stage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public final class AbilityListener implements Listener {
    private static final Particle.DustOptions BLACK = new Particle.DustOptions(Color.fromRGB(10, 10, 10), 1.4f);
    private static final Particle.DustOptions GRAY = new Particle.DustOptions(Color.fromRGB(135, 135, 135), 1.2f);

    private final RedeemedGemPlugin pl;
    private final Map<UUID, Integer> charges = new HashMap<>();
    private final Map<UUID, Long> shackleReady = new HashMap<>();
    private final Map<UUID, Long> slamReady = new HashMap<>();
    private final Map<UUID, Long> limitReady = new HashMap<>();
    private final Map<UUID, Long> limitEnd = new HashMap<>();
    private final Map<UUID, Long> grounded = new HashMap<>();
    private final Map<UUID, Long> locked = new HashMap<>();
    private final Map<UUID, Long> lastClick = new HashMap<>();
    private final Map<UUID, Long> launchReady = new HashMap<>();
    private final Map<UUID, Long> noFall = new HashMap<>();
    private final NamespacedKey lossKey;
    private boolean internalDamage;
    private int tick;

    public AbilityListener(RedeemedGemPlugin pl) {
        this.pl = pl;
        this.lossKey = new NamespacedKey(pl, "slam_heart_loss");
        Bukkit.getScheduler().runTaskTimer(pl, this::tick, 4L, 4L);
    }

    private double d(String path) { return pl.getConfig().getDouble(path); }
    private long ms(String secPath) { return (long) (d(secPath) * 1000.0); }
    private boolean isLocked(Player p, long now) { return locked.getOrDefault(p.getUniqueId(), 0L) > now; }
    private boolean isGrounded(LivingEntity e, long now) { return grounded.getOrDefault(e.getUniqueId(), 0L) > now; }

    private boolean validTarget(Player src, Player t) {
        return t != src && t.isOnline() && !t.isDead()
                && (t.getGameMode() == GameMode.SURVIVAL || t.getGameMode() == GameMode.ADVENTURE)
                && !pl.trust.isTrusted(src.getUniqueId(), t.getUniqueId());
    }

    // ------------------------------------------------------------------ Shackles
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (internalDamage) return;
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (!(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof LivingEntity t)) return;
        Stage s = pl.gems.activeStage(p.getInventory().getItemInMainHand());
        if (s == null || s.ordinal() < 1) return;
        long now = System.currentTimeMillis();
        if (isLocked(p, now) || isGrounded(t, now)) return;
        if (t instanceof Player tp && pl.trust.isTrusted(p.getUniqueId(), tp.getUniqueId())) return;

        UUID id = p.getUniqueId();
        int max = (int) d("shackles.charges");
        int c = charges.getOrDefault(id, max);
        if (c <= 0) return;
        c--;
        charges.put(id, c);
        if (c == 0) shackleReady.put(id, now + ms("shackles.recharge-seconds"));

        ground(t, ms("shackles.ground-seconds"));
        trueDamage(t, d("shackles.damage-hearts") * 2.0, p);
        p.getWorld().playSound(t.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1f, 0.6f);
    }

    private void ground(LivingEntity t, long millis) {
        grounded.put(t.getUniqueId(), System.currentTimeMillis() + millis);
        if (t instanceof Mob m) {
            boolean was = m.isAware();
            m.setAware(false);
            Bukkit.getScheduler().runTaskLater(pl, () -> m.setAware(was), Math.max(1L, millis / 50L));
        }
        groundFx(t, millis);
    }

    /** Chains wrapped around the target for the whole ground time, then a short fade (about 1s longer). */
    private void groundFx(LivingEntity t, long millis) {
        final int ticks = (int) (millis / 50L) + 20;
        Location hit = t.getLocation().add(0, 1, 0);
        dust(hit, 40, 0.5, 0.8, 0.5);
        t.getWorld().spawnParticle(Particle.SQUID_INK, hit, n(10), 0.4, 0.7, 0.4, 0.05);
        new BukkitRunnable() {
            int i = 0;
            @Override public void run() {
                if (i >= ticks || t.isDead() || !t.isValid()) { cancel(); return; }
                Location b = t.getLocation();
                boolean held = i < ticks - 20;
                for (int k = 0; k < 3; k++) ring(b.clone().add(0, 0.15 + k * 0.8, 0), held ? 0.75 : 0.75 + (i - (ticks - 20)) * 0.04, 10);
                for (int sIdx = 0; sIdx < 2; sIdx++) {
                    double a = i * 0.5 + sIdx * Math.PI;
                    double y = (i % 20) / 20.0 * 1.9;
                    dust(b.clone().add(Math.cos(a) * 0.8, y, Math.sin(a) * 0.8), 2, 0.05, 0.05, 0.05);
                }
                if (i % 2 == 0) smoke(b.clone().add(0, 0.1, 0), 2, 0.4, 0.05, 0.4, 0.01);
                if (i % 4 == 0) ash(b.clone().add(0, 1, 0), 4, 0.5, 0.9, 0.5);
                i++;
            }
        }.runTaskTimer(pl, 0L, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!isGrounded(p, System.currentTimeMillis())) return;
        Location f = e.getFrom(), to = e.getTo();
        if (to == null) return;
        if (f.getX() != to.getX() || f.getY() != to.getY() || f.getZ() != to.getZ()) {
            Location n = f.clone();
            n.setYaw(to.getYaw());
            n.setPitch(to.getPitch());
            e.setTo(n);
        }
    }

    // ------------------------------------------------------------------ Input
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        if (it == null) return;
        Player p = e.getPlayer();
        Stage s = pl.gems.stageOf(it);
        long now = System.currentTimeMillis();

        if (isLocked(p, now)) {
            if (s != null || pl.gems.isGemLike(it)) {
                e.setCancelled(true);
                p.sendActionBar(Component.text("Your abilities are locked!", NamedTextColor.GRAY));
            }
            return;
        }
        if (s == null) return;
        if (pl.gems.activeStage(it) == null) {
            p.sendActionBar(Component.text("Your gem is disguised - its powers are disabled.", NamedTextColor.GRAY));
            return;
        }

        if (e.getHand() == EquipmentSlot.HAND) {
            if (s.ordinal() >= 3) { e.setCancelled(true); limitless(p, now); }
        } else if (e.getHand() == EquipmentSlot.OFF_HAND) {
            if (s.ordinal() >= 2) {
                e.setCancelled(true);
                UUID id = p.getUniqueId();
                long last = lastClick.getOrDefault(id, 0L);
                if (now - last <= (long) d("general.double-click-ms")) { lastClick.remove(id); slam(p, now); }
                else lastClick.put(id, now);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (!isLocked(p, System.currentTimeMillis())) return;
        if (pl.gems.isGemLike(e.getMainHandItem()) || pl.gems.isGemLike(e.getOffHandItem())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDropLocked(PlayerDropItemEvent e) {
        ItemStack it = e.getItemDrop().getItemStack();
        if (isLocked(e.getPlayer(), System.currentTimeMillis()) && pl.gems.isGemLike(it)) e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        grounded.remove(id); locked.remove(id); lastClick.remove(id); limitEnd.remove(id);
    }

    // ------------------------------------------------------------------ Shackles launch (left click the ground)
    @EventHandler(priority = EventPriority.HIGH)
    public void onLeftClickGround(PlayerInteractEvent e) {
        if (e.getAction() != Action.LEFT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        ItemStack it = e.getItem();
        Stage raw = pl.gems.stageOf(it);
        if (raw == null) return;
        Player p = e.getPlayer();
        long now = System.currentTimeMillis();
        if (isLocked(p, now)) { e.setCancelled(true); return; }
        if (pl.gems.activeStage(it) == null) {
            p.sendActionBar(Component.text("Your gem is disguised - its powers are disabled.", NamedTextColor.GRAY));
            return;
        }
        if (raw.ordinal() < 1) return;
        e.setCancelled(true);
        launch(p, now);
    }

    private void launch(Player p, long now) {
        UUID id = p.getUniqueId();
        long ready = launchReady.getOrDefault(id, 0L);
        if (ready > now) { p.sendActionBar(Component.text("Launch: " + secs(ready - now) + "s", NamedTextColor.GRAY)); return; }
        launchReady.put(id, now + ms("shackles.launch-cooldown-seconds"));
        noFall.put(id, now + ms("shackles.launch-fall-protect-seconds"));

        Vector dir = p.getLocation().getDirection();
        dir.setY(0);
        if (dir.lengthSquared() < 0.01) dir = new Vector(0, 0, 1);
        dir.normalize().multiply(d("shackles.launch-strength")).setY(d("shackles.launch-lift"));
        p.setVelocity(dir);

        Location from = p.getLocation().add(0, 0.2, 0);
        p.getWorld().playSound(from, Sound.ENTITY_BREEZE_WIND_BURST, 1f, 0.8f);
        p.getWorld().playSound(from, Sound.BLOCK_CHAIN_BREAK, 1f, 0.7f);
        ring(from, 1.2, 20);
        dust(from, 50, 0.6, 0.2, 0.6);
        smoke(from, 15, 0.5, 0.1, 0.5, 0.05);
        new BukkitRunnable() {
            int i = 0;
            @Override public void run() {
                if (i++ >= 25 || !p.isOnline() || p.isDead()) { cancel(); return; }
                Location l = p.getLocation().add(0, 0.9, 0);
                dust(l, 12, 0.3, 0.5, 0.3);
                smoke(l, 2, 0.25, 0.4, 0.25, 0.01);
                ash(l, 3, 0.4, 0.5, 0.4);
                if (i % 3 == 0) ring(p.getLocation().add(0, 0.9, 0), 0.8, 12);
            }
        }.runTaskTimer(pl, 0L, 1L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL || !(e.getEntity() instanceof Player p)) return;
        if (noFall.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis()) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ Shattered Slam
    private void slam(Player p, long now) {
        UUID id = p.getUniqueId();
        long ready = slamReady.getOrDefault(id, 0L);
        if (ready > now) { p.sendActionBar(Component.text("Shattered Slam: " + secs(ready - now) + "s", NamedTextColor.GRAY)); return; }
        slamReady.put(id, now + ms("slam.cooldown-seconds"));

        double r = d("slam.radius");
        Location c = p.getLocation().add(0, 0.15, 0);
        p.getWorld().playSound(c, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 0.7f);
        p.getWorld().playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.6f);

        c.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, c, 1);
        smoke(c.clone().add(0, 0.5, 0), 30, 1.0, 0.3, 1.0, 0.05);
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                double prog = Math.min(1.0, t / 10.0);
                double rr = Math.max(0.5, r * prog);
                int pts = (int) (rr * 10) + 16;
                ring(c, rr, pts);
                ring(c.clone().add(0, 0.6, 0), rr * 0.92, pts);
                ring(c.clone().add(0, 1.3, 0), rr * 0.8, pts);
                smoke(c, 6, rr * 0.5, 0.2, rr * 0.5, 0.02);
                ash(c.clone().add(0, 1, 0), 8, rr * 0.5, 0.8, rr * 0.5);
                if (prog >= 1.0 && t % 3 == 0) pillars(c, r, 12, 3.0);
                if (t >= 36) cancel();
                t++;
            }
        }.runTaskTimer(pl, 0L, 1L);

        for (Player t : p.getWorld().getNearbyPlayers(p.getLocation(), r)) {
            if (!validTarget(p, t)) continue;
            Vector dir = t.getLocation().toVector().subtract(p.getLocation().toVector());
            dir.setY(0);
            if (dir.lengthSquared() < 0.01) dir = new Vector(Math.random() - 0.5, 0, Math.random() - 0.5);
            dir.normalize().multiply(d("slam.fling-distance") / 8.0).setY(0.7);
            t.setVelocity(dir);
            takeMaxHearts(t, d("slam.hearts-taken"));
            dust(t.getLocation().add(0, 1, 0), 40, 0.5, 0.8, 0.5);
            t.getWorld().spawnParticle(Particle.SQUID_INK, t.getLocation().add(0, 1, 0), n(10), 0.4, 0.7, 0.4, 0.05);
        }
    }

    // ------------------------------------------------------------------ Limitless
    private void limitless(Player p, long now) {
        UUID id = p.getUniqueId();
        long ready = limitReady.getOrDefault(id, 0L);
        if (ready > now) { p.sendActionBar(Component.text("Limitless: " + secs(ready - now) + "s", NamedTextColor.GRAY)); return; }
        long dur = ms("limitless.duration-seconds");
        limitEnd.put(id, now + dur);
        limitReady.put(id, now + dur + ms("limitless.cooldown-seconds"));

        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.6f);
        double take = d("limitless.hearts-taken") * 2.0, gained = 0;
        for (Player t : p.getWorld().getNearbyPlayers(p.getLocation(), d("limitless.radius"))) {
            if (!validTarget(p, t)) continue;
            double before = t.getHealth();
            trueDamage(t, take, p);
            gained += Math.min(take, before);
            drainBeam(p, t);
        }
        burstRings(p.getLocation().add(0, 0.15, 0), d("limitless.radius"));
        if (gained > 0) {
            double max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
            p.setHealth(Math.min(max, p.getHealth() + gained));
        }
    }

    private void tickLimitless(Player p, long now) {
        limitlessFx(p);
        double lr = d("limitless.lock-radius");
        for (Player t : p.getWorld().getNearbyPlayers(p.getLocation(), lr)) {
            if (!validTarget(p, t)) continue;
            locked.put(t.getUniqueId(), now + ms("limitless.lock-seconds"));
            t.getWorld().spawnParticle(Particle.DUST, t.getLocation().add(0, 1, 0), 6, 0.3, 0.6, 0.3, 0, GRAY);
        }
        if (tick % 5 == 0) {
            int amt = (int) d("limitless.armor-mend-per-second");
            ItemStack[] armor = p.getInventory().getArmorContents();
            for (ItemStack a : armor) {
                if (a != null && a.getItemMeta() instanceof Damageable dm && dm.hasDamage()) {
                    dm.setDamage(Math.max(0, dm.getDamage() - amt));
                    a.setItemMeta(dm);
                }
            }
            p.getInventory().setArmorContents(armor);
        }
    }

    // ------------------------------------------------------------------ Tick / HUD
    private void tick() {
        long now = System.currentTimeMillis();
        tick++;
        shackleReady.entrySet().removeIf(en -> {
            if (now >= en.getValue()) { charges.remove(en.getKey()); return true; }
            return false;
        });
        grounded.values().removeIf(v -> v <= now);
        noFall.values().removeIf(v -> v <= now);
        locked.values().removeIf(v -> v <= now);

        Iterator<Map.Entry<UUID, Long>> it = limitEnd.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> en = it.next();
            Player p = Bukkit.getPlayer(en.getKey());
            if (p == null || p.isDead() || now >= en.getValue()) { it.remove(); continue; }
            tickLimitless(p, now);
        }
        if (tick % 5 == 0) hud(now);
    }

    private void hud(long now) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Stage s = activeHeld(p);
            if (s == null || s == Stage.DORMANT) continue;
            UUID id = p.getUniqueId();
            List<Component> parts = new ArrayList<>();
            int max = (int) d("shackles.charges");
            long sr = shackleReady.getOrDefault(id, 0L);
            parts.add(part("Shackles", sr > now ? secs(sr - now) + "s" : charges.getOrDefault(id, max) + "/" + max));
            long lr0 = launchReady.getOrDefault(id, 0L);
            parts.add(part("Launch", lr0 > now ? secs(lr0 - now) + "s" : "READY"));
            if (s.ordinal() >= 3) {
                long end = limitEnd.getOrDefault(id, 0L), lr = limitReady.getOrDefault(id, 0L);
                parts.add(part("Limitless", end > now ? "ACTIVE " + secs(end - now) + "s" : lr > now ? secs(lr - now) + "s" : "READY"));
            }
            if (s.ordinal() >= 2) {
                long sl = slamReady.getOrDefault(id, 0L);
                parts.add(part("Shattered Slam", sl > now ? secs(sl - now) + "s" : "READY"));
            }
            p.sendActionBar(Component.join(JoinConfiguration.separator(Component.text(" | ", NamedTextColor.DARK_GRAY)), parts));
        }
    }

    private Stage activeHeld(Player p) {
        Stage a = pl.gems.activeStage(p.getInventory().getItemInMainHand());
        Stage b = pl.gems.activeStage(p.getInventory().getItemInOffHand());
        if (a == null) return b;
        if (b == null) return a;
        return a.ordinal() >= b.ordinal() ? a : b;
    }

    private Component part(String name, String state) {
        return Component.text(name + " ", NamedTextColor.GRAY).append(Component.text(state, NamedTextColor.WHITE));
    }

    private static long secs(long millis) { return (millis + 999) / 1000; }

    // ------------------------------------------------------------------ Helpers
    /** Damage that ignores armor and enchantments. Falls back to a lethal hit so totems and kill credit still work. */
    public void trueDamage(LivingEntity t, double hp, Player src) {
        if (t.isDead() || hp <= 0) return;
        internalDamage = true;
        try {
            double left = t.getHealth() - hp;
            if (left <= 0) {
                t.damage(10000.0, src);
            } else {
                t.setHealth(left);
                t.playEffect(EntityEffect.HURT);
            }
        } finally {
            internalDamage = false;
        }
    }

    // ------------------------------------------------------------------ Permanent hearts (Shattered Slam)
    /** Hearts currently missing from the player's max health because of Shattered Slam. */
    public double heartsLost(Player t) { return pl.hearts.get(t.getUniqueId()); }

    /** Permanently removes max-health hearts (never below slam.min-hearts). Saved, and re-applied on join/respawn. */
    public void takeMaxHearts(Player t, double hearts) {
        AttributeInstance a = t.getAttribute(Attribute.MAX_HEALTH);
        if (a == null || hearts <= 0) return;
        double room = Math.max(0.0, a.getValue() - d("slam.min-hearts") * 2.0);
        double take = Math.min(hearts * 2.0, room);
        if (take <= 0) return;
        pl.hearts.set(t.getUniqueId(), heartsLost(t) + take / 2.0);
        applyHearts(t);
        t.playEffect(EntityEffect.HURT);
    }

    /** Puts the saved heart loss onto the player's max health. */
    public void applyHearts(Player t) {
        AttributeInstance a = t.getAttribute(Attribute.MAX_HEALTH);
        if (a == null) return;
        for (AttributeModifier m : new ArrayList<>(a.getModifiers())) if (m.getKey().equals(lossKey)) a.removeModifier(m);
        double lost = heartsLost(t) * 2.0;
        if (lost > 0) a.addModifier(new AttributeModifier(lossKey, -lost, AttributeModifier.Operation.ADD_NUMBER));
        if (t.getHealth() > a.getValue()) t.setHealth(a.getValue());
    }

    /** Gives all taken hearts back. */
    public void restoreHearts(Player t) {
        pl.hearts.set(t.getUniqueId(), 0);
        applyHearts(t);
    }

    // ------------------------------------------------------------------ Particles
    private int n(int base) {
        return Math.max(1, (int) Math.round(base * pl.getConfig().getDouble("effects.particle-multiplier", 2.0)));
    }

    private void dust(Location l, int count, double dx, double dy, double dz) {
        World w = l.getWorld();
        w.spawnParticle(Particle.DUST, l, n(count), dx, dy, dz, 0, BLACK);
        w.spawnParticle(Particle.DUST, l, n(count), dx, dy, dz, 0, GRAY);
    }

    private void smoke(Location l, int count, double dx, double dy, double dz, double speed) {
        l.getWorld().spawnParticle(Particle.LARGE_SMOKE, l, n(count), dx, dy, dz, speed);
    }

    private void ash(Location l, int count, double dx, double dy, double dz) {
        l.getWorld().spawnParticle(Particle.ASH, l, n(count), dx, dy, dz, 0);
    }

    private void ring(Location c, double r, int pts) {
        World w = c.getWorld();
        int points = n(pts);
        for (int i = 0; i < points; i++) {
            double a = 2 * Math.PI * i / points;
            w.spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r), 1, 0, 0, 0, 0, i % 2 == 0 ? BLACK : GRAY);
        }
    }

    /** Vertical black/gray columns standing on a circle. */
    private void pillars(Location c, double r, int count, double height) {
        World w = c.getWorld();
        for (int i = 0; i < count; i++) {
            double a = 2 * Math.PI * i / count;
            Location base = c.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r);
            for (double y = 0; y <= height; y += 0.35) {
                w.spawnParticle(Particle.DUST, base.clone().add(0, y, 0), 1, 0.05, 0, 0.05, 0, ((int) (y / 0.35)) % 2 == 0 ? BLACK : GRAY);
            }
            w.spawnParticle(Particle.LARGE_SMOKE, base.clone().add(0, 0.2, 0), n(1), 0.1, 0.1, 0.1, 0.01);
        }
    }

    /** Expanding rings that rush out when Limitless starts. */
    private void burstRings(Location c, double r) {
        new BukkitRunnable() {
            int t = 1;
            @Override public void run() {
                double rr = r * t / 10.0;
                ring(c, rr, (int) (rr * 8) + 16);
                ring(c.clone().add(0, 1.0, 0), rr * 0.9, (int) (rr * 8) + 16);
                smoke(c, 4, rr * 0.5, 0.2, rr * 0.5, 0.02);
                if (++t > 10) cancel();
            }
        }.runTaskTimer(pl, 0L, 1L);
    }

    /** Runs every 4 ticks while Limitless is active: ring, rotating spokes, pillars, lock-range ring, drifting ash. */
    private void limitlessFx(Player p) {
        Location c = p.getLocation().add(0, 0.1, 0);
        double r = d("limitless.radius");
        ring(c, r, 48);
        ring(c.clone().add(0, 0.5, 0), r * 0.97, 36);
        ring(c, Math.max(0.5, d("limitless.lock-radius")), 16);
        double phase = tick * 0.25;
        World w = c.getWorld();
        for (int s = 0; s < 6; s++) {
            double a = phase + s * Math.PI / 3.0;
            for (double dist = 1.0; dist < r; dist += 1.0) {
                w.spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * dist, 0.05, Math.sin(a) * dist), 1, 0, 0, 0, 0, ((int) dist) % 2 == 0 ? BLACK : GRAY);
            }
        }
        if (tick % 2 == 0) pillars(c, r, 8, 2.5);
        smoke(p.getLocation().add(0, 1, 0), 3, 0.6, 0.9, 0.6, 0.01);
        ash(p.getLocation().add(0, 1, 0), 6, 1.2, 1.0, 1.2);
    }

    /** A drain beam from the victim to the caster that keeps pulsing for 1.5s. */
    private void drainBeam(Player src, Player target) {
        new BukkitRunnable() {
            int i = 0;
            @Override public void run() {
                if (i++ >= 15 || !src.isOnline() || !target.isOnline()) { cancel(); return; }
                Location from = target.getLocation().add(0, 1, 0), to = src.getLocation().add(0, 1, 0);
                Vector v = to.toVector().subtract(from.toVector());
                double len = v.length();
                if (len < 0.1) return;
                v.normalize();
                int idx = 0;
                for (double k = 0; k < len; k += 0.3, idx++) {
                    from.getWorld().spawnParticle(Particle.DUST, from.clone().add(v.clone().multiply(k)), 1, 0.04, 0.04, 0.04, 0, idx % 2 == 0 ? BLACK : GRAY);
                }
                dust(target.getLocation().add(0, 1, 0), 6, 0.3, 0.6, 0.3);
            }
        }.runTaskTimer(pl, 0L, 2L);
    }

    public void particleBurst(Location c) {
        dust(c, 80, 0.6, 0.9, 0.6);
        smoke(c, 20, 0.6, 0.9, 0.6, 0.05);
        ash(c, 40, 0.8, 1.0, 0.8);
        burstRings(c.clone().subtract(0, 0.85, 0), 3.0);
    }
}
