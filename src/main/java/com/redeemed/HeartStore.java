package com.redeemed;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Hearts (not HP) permanently taken from each player by Shattered Slam. Saved so it survives restarts and respawns. */
public final class HeartStore {
    private final RedeemedGemPlugin pl;
    private final File file;
    private final Map<UUID, Double> lost = new HashMap<>();

    public HeartStore(RedeemedGemPlugin pl) {
        this.pl = pl;
        this.file = new File(pl.getDataFolder(), "hearts.yml");
        if (file.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
            for (String k : y.getKeys(false)) {
                try { lost.put(UUID.fromString(k), y.getDouble(k)); } catch (IllegalArgumentException ignored) { }
            }
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Double> e : lost.entrySet()) y.set(e.getKey().toString(), e.getValue());
        try { y.save(file); } catch (IOException ex) { pl.getLogger().warning("Could not save hearts.yml: " + ex.getMessage()); }
    }

    public double get(UUID id) { return lost.getOrDefault(id, 0.0); }

    public void set(UUID id, double hearts) {
        if (hearts <= 0) lost.remove(id); else lost.put(id, hearts);
        save();
    }
}
