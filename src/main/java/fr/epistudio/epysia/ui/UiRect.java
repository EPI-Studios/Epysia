package fr.epistudio.epysia.ui;

public record UiRect(float x, float y, float width, float height) {

    public boolean contains(float pointX, float pointY) {
        return pointX >= x && pointX <= x + width && pointY >= y && pointY <= y + height;
    }
}
