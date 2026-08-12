package fr.epistudio.epysia.steam;

public record SteamAvatar(int width, int height, byte[] rgba) {

    public boolean isEmpty() {
        return width <= 0 || height <= 0 || rgba.length == 0;
    }
}
