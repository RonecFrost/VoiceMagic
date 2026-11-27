package com.ronec.voicemagic;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Closed vocabulary of concepts, independent of each player's chosen spoken words. */
public final class SpellConcepts {
    private SpellConcepts() {}
    private static final List<String> SPELLS=List.of("projectile", "cmd:gamemode", "cmd:effect");
    private static final List<String> TARGETS=List.of(
        "target:self", "target:nearest", "target:random", "target:all_players", "target:all_entities");
    private static final List<String> MODES=List.of(
        "gamemode:survival", "gamemode:creative", "gamemode:adventure", "gamemode:spectator");

    public static List<String> section(String section) {
        return switch(section) {
            case "Spells" -> SPELLS;
            case "Targets" -> TARGETS;
            case "Modes" -> MODES;
            case "Effects" -> BuiltInRegistries.MOB_EFFECT.keySet().stream()
                .filter(id -> "minecraft".equals(id.getNamespace()))
                .map(id -> "effect:" + id.getPath()).sorted().toList();
            default -> List.of();
        };
    }
    public static boolean isTrainable(String concept) {
        if (concept == null) return false;
        if (SPELLS.contains(concept) || TARGETS.contains(concept) || MODES.contains(concept)) return true;
        if (!concept.startsWith("effect:")) return false;
        String name = concept.substring("effect:".length());
        if (!name.matches("[a-z0-9_]+")) return false;
        return BuiltInRegistries.MOB_EFFECT.containsKey(ResourceLocation.fromNamespaceAndPath("minecraft",name));
    }
    public static String label(String concept) {
        if (concept == null) return "?";
        return switch (concept) {
            case "projectile" -> "Projectile";
            case "cmd:gamemode" -> "Gamemode";
            case "cmd:effect" -> "Effect";
            case "target:self" -> "Myself (@s)";
            case "target:nearest" -> "Nearest Player (@p)";
            case "target:random" -> "Random Player (@r)";
            case "target:all_players" -> "All Players (@a)";
            case "target:all_entities" -> "All Entities (@e)";
            default -> {
                String raw=concept.contains(":") ? concept.substring(concept.indexOf(':')+1) : concept;
                String[] words=raw.split("_");
                StringBuilder s=new StringBuilder();
                for(String w:words) if(!w.isEmpty()) {
                    if(s.length()>0) s.append(' ');
                    s.append(w.substring(0,1).toUpperCase(Locale.ROOT)).append(w.substring(1));
                }
                yield s.toString();
            }
        };
    }
    public static boolean validCast(List<String> spell) {
        if (spell.size()==1) return "projectile".equals(spell.get(0));
        if (spell.size()!=3) return false;
        String action=spell.get(0), target=spell.get(1), kind=spell.get(2);
        return TARGETS.contains(target) &&
            (("cmd:gamemode".equals(action) && MODES.contains(kind)) ||
             ("cmd:effect".equals(action) && isTrainable(kind) && kind.startsWith("effect:")));
    }
}
