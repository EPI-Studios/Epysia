package fr.epistudio.epysia.assets.epyatlas;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SpriteAtlas {

    private final String texturePath;
    private final Optional<SpriteAtlasGrid> grid;
    private final List<SpriteAtlasRegion> explicitRegions;
    private final List<SpriteAnimation> animations;
    private final Map<String, SpriteAtlasRegion> regionsByName = new LinkedHashMap<>();
    private final Map<String, SpriteAnimation> animationsByName = new LinkedHashMap<>();

    public SpriteAtlas(String texturePath, List<SpriteAtlasRegion> explicitRegions) {
        this(texturePath, Optional.empty(), explicitRegions, List.of());
    }

    public SpriteAtlas(String texturePath, List<SpriteAtlasRegion> explicitRegions,
                       List<SpriteAnimation> animations) {
        this(texturePath, Optional.empty(), explicitRegions, animations);
    }

    private SpriteAtlas(String texturePath, Optional<SpriteAtlasGrid> grid,
                        List<SpriteAtlasRegion> explicitRegions, List<SpriteAnimation> animations) {
        this.texturePath = texturePath;
        this.grid = grid;
        this.explicitRegions = List.copyOf(explicitRegions);
        this.animations = List.copyOf(animations);
        grid.ifPresent(this::deriveGridRegions);
        for (SpriteAtlasRegion region : explicitRegions) {
            regionsByName.put(region.name(), region);
        }
        for (SpriteAnimation animation : animations) {
            animationsByName.put(animation.name(), animation);
        }
    }

    public static SpriteAtlas gridAtlas(String texturePath, SpriteAtlasGrid grid,
                                        List<SpriteAtlasRegion> explicitRegions) {
        return new SpriteAtlas(texturePath, Optional.of(grid), explicitRegions, List.of());
    }

    public static SpriteAtlas gridAtlas(String texturePath, SpriteAtlasGrid grid,
                                        List<SpriteAtlasRegion> explicitRegions,
                                        List<SpriteAnimation> animations) {
        return new SpriteAtlas(texturePath, Optional.of(grid), explicitRegions, animations);
    }

    private void deriveGridRegions(SpriteAtlasGrid layout) {
        for (int row = 0; row < layout.rows(); row++) {
            for (int column = 0; column < layout.columns(); column++) {
                int index = row * layout.columns() + column;
                regionsByName.put(Integer.toString(index), gridRegion(layout, row, column, index));
            }
        }
    }

    private static SpriteAtlasRegion gridRegion(SpriteAtlasGrid layout, int row, int column, int index) {
        float minU = (float) column / layout.columns();
        float maxU = (float) (column + 1) / layout.columns();
        float minV = (float) (layout.rows() - row - 1) / layout.rows();
        float maxV = (float) (layout.rows() - row) / layout.rows();
        return new SpriteAtlasRegion(Integer.toString(index), minU, minV, maxU, maxV);
    }

    public String texturePath() {
        return texturePath;
    }

    public SpriteAtlas withTexturePath(String value) {
        return new SpriteAtlas(value, grid, explicitRegions, animations);
    }

    public Optional<SpriteAtlasGrid> grid() {
        return grid;
    }

    public List<SpriteAtlasRegion> explicitRegions() {
        return explicitRegions;
    }

    public Optional<SpriteAtlasRegion> region(String name) {
        return Optional.ofNullable(regionsByName.get(name));
    }

    public List<String> regionNames() {
        return new ArrayList<>(regionsByName.keySet());
    }

    public int regionCount() {
        return regionsByName.size();
    }

    public List<SpriteAnimation> animations() {
        return animations;
    }

    public Optional<SpriteAnimation> animation(String name) {
        return Optional.ofNullable(animationsByName.get(name));
    }

    public List<String> animationNames() {
        return new ArrayList<>(animationsByName.keySet());
    }
}
