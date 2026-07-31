package fr.epistudio.epysia.render.mesh;

public record ArenaPlacement(MeshArena arena, ArenaMesh allocation) {

    public void release() {
        arena.release(allocation);
    }
}
