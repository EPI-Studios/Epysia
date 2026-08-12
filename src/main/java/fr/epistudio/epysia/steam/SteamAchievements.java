package fr.epistudio.epysia.steam;

import com.codedisaster.steamworks.SteamUserStats;
import com.codedisaster.steamworks.SteamUserStatsCallback;

import java.util.ArrayList;
import java.util.List;

public final class SteamAchievements {

    private final SteamUserStats stats;

    SteamAchievements() {
        stats = new SteamUserStats(new SteamUserStatsCallback() {
        });
    }

    public boolean unlock(String achievement) {
        return stats.setAchievement(achievement) && stats.storeStats();
    }

    public boolean clear(String achievement) {
        return stats.clearAchievement(achievement) && stats.storeStats();
    }

    public boolean unlocked(String achievement) {
        return stats.isAchieved(achievement, false);
    }

    public boolean showProgress(String achievement, int current, int target) {
        return stats.indicateAchievementProgress(achievement, current, target);
    }

    public List<String> names() {
        List<String> names = new ArrayList<>();
        for (int index = 0; index < stats.getNumAchievements(); index++) {
            names.add(stats.getAchievementName(index));
        }
        return List.copyOf(names);
    }

    public int intStat(String name, int fallback) {
        return stats.getStatI(name, fallback);
    }

    public float floatStat(String name, float fallback) {
        return stats.getStatF(name, fallback);
    }

    public boolean setIntStat(String name, int value) {
        return stats.setStatI(name, value);
    }

    public boolean setFloatStat(String name, float value) {
        return stats.setStatF(name, value);
    }

    public boolean store() {
        return stats.storeStats();
    }

    public boolean resetEverything(boolean achievementsToo) {
        return stats.resetAllStats(achievementsToo) && stats.storeStats();
    }

    void dispose() {
        stats.dispose();
    }
}
