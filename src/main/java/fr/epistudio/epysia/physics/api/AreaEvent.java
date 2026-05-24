package fr.epistudio.epysia.physics.api;

public record AreaEvent(BodyHandle area, BodyHandle other, boolean entered) {
}
